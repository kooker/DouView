package com.example.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

data class QualityStream(
    val name: String,
    val flv: String?,
    val hls: String?
)

data class LiveStreamData(
    val rid: String,
    val isLive: Boolean,
    val statusKnown: Boolean,
    val name: String,
    val title: String,
    val avatar: String,
    val streams: Map<String, QualityStream>
)

object DouyinLiveEngine {
    private const val UA_PC =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    private const val UA_MOBILE =
        "Mozilla/5.0 (iPhone; CPU iPhone OS 17_4 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Mobile/15E148"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val noRedirectClient = httpClient.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    @Volatile
    private var cachedTtwid: String = ""
    @Volatile
    private var ttwidFetchedAt: Long = 0L

    // 内存短期流缓存（对标网页版 live.php 的 TTL_LIVE 20s 机制）
    private val streamCache = mutableMapOf<String, Pair<LiveStreamData, Long>>()
    private const val STREAM_CACHE_TTL = 20_000L

    fun clearCache() {
        streamCache.clear()
        cachedTtwid = ""
        ttwidFetchedAt = 0L
    }

    // 优先匹配高清 (hd 720P) 或超清 (uhd 1080P)，避免原画 (1080P60) 导致的设备硬解异常
    val QUALITY_ORDER = listOf(
        "hd" to "高清 (720P)",
        "uhd" to "超清 (1080P)",
        "origin" to "原画 (1080P60)",
        "sd" to "标清 (540P)",
        "md" to "中清",
        "ld" to "流畅",
        "default" to "默认画质"
    )

    private val QUALITY_MAP = QUALITY_ORDER.toMap()

    /**
     * 1. 提取输入源：支持整段手机分享口令、官方短链、PC 网页链接、纯数字房间号
     */
    suspend fun extractRoomId(rawInput: String): Pair<String?, Boolean> = withContext(Dispatchers.IO) {
        val text = rawInput.trim()
        if (text.isEmpty()) return@withContext Pair(null, false)

        // 匹配短链接例如 https://v.douyin.com/iABCxyz/
        val shortMatcher = Pattern.compile("https?://v\\.douyin\\.com/[A-Za-z0-9_-]+/?").matcher(text)
        if (shortMatcher.find()) {
            val shortUrl = shortMatcher.group(0) ?: ""
            val resolved = resolveShortUrl(shortUrl)
            if (resolved != null) {
                return@withContext Pair(resolved, true)
            }
        }

        // 匹配 PC 网页链接 live.douyin.com/xxx 或 follow/live/xxx
        val webMatcher = Pattern.compile("(?:live\\.douyin\\.com/|follow/live/)(\\d{3,22})").matcher(text)
        if (webMatcher.find()) {
            val rid = webMatcher.group(1)
            val isOnce = (rid?.length ?: 0) >= 15
            return@withContext Pair(rid, isOnce)
        }

        // 匹配纯房间数字
        val numMatcher = Pattern.compile("\\b(\\d{3,22})\\b").matcher(text)
        if (numMatcher.find()) {
            val rid = numMatcher.group(1)
            val isOnce = (rid?.length ?: 0) >= 15
            return@withContext Pair(rid, isOnce)
        }

        Pair(null, false)
    }

    /**
     * 2. 获取并自动缓存 ttwid Token (字节防爬通行证)
     * 双重保障：优先通过 live.douyin.com 直连获取官方根域 Cookie，失败则通过 union 注册接口兜底
     */
    suspend fun getTtwid(): String = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (cachedTtwid.isNotEmpty() && (now - ttwidFetchedAt) < 3600_000L) {
            return@withContext cachedTtwid
        }

        // 方式 A：优先通过官方 ttwid union 注册接口高速获取（对齐 live.php 毫秒级返回）
        try {
            val postJson = JSONObject().apply {
                put("region", "cn")
                put("aid", 1768)
                put("needFid", false)
                put("service", "www.ixigua.com")
                put("migrate_info", JSONObject().put("ticket", "").put("source", "node"))
                put("cbUrlProtocol", "https")
                put("union", true)
            }.toString()

            val req = Request.Builder()
                .url("https://ttwid.bytedance.com/ttwid/union/register/")
                .header("User-Agent", UA_PC)
                .post(postJson.toRequestBody("application/json".toMediaType()))
                .build()

            val res = httpClient.newCall(req).execute()
            val cookieHeaders = res.headers("Set-Cookie")
            for (cookie in cookieHeaders) {
                val m = Pattern.compile("ttwid=([^;\\s]+)").matcher(cookie)
                if (m.find()) {
                    val token = m.group(1) ?: ""
                    if (token.isNotEmpty()) {
                        cachedTtwid = token
                        ttwidFetchedAt = now
                        return@withContext cachedTtwid
                    }
                }
            }
        } catch (_: Exception) {}

        // 方式 B：访问 live.douyin.com 获取原生 Set-Cookie 兜底
        try {
            val req = Request.Builder()
                .url("https://live.douyin.com/")
                .header("User-Agent", UA_PC)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                .header("Accept-Language", "zh-CN,zh;q=0.9")
                .build()

            val res = httpClient.newCall(req).execute()
            val cookieHeaders = res.headers("Set-Cookie")
            for (cookie in cookieHeaders) {
                val m = Pattern.compile("ttwid=([^;\\s]+)").matcher(cookie)
                if (m.find()) {
                    val token = m.group(1) ?: ""
                    if (token.isNotEmpty()) {
                        cachedTtwid = token
                        ttwidFetchedAt = now
                        return@withContext cachedTtwid
                    }
                }
            }
        } catch (_: Exception) {}

        cachedTtwid
    }

    /**
     * 获取 ttwid Token（供外部或内部调用的公开方法）
     */
    suspend fun fetchTtwid(): String = getTtwid()

    /**
     * 根据 web_rid 与可选 sec_user_id 获取直播间 HLS/FLV 播放地址
     * 包含 sec_user_id、正确的 User-Agent 与 Cookie，
     * 解析 stream_data 中的 flv 或 hls 播放流地址，优先匹配高清 (hd) 或超清 (uhd) 质量，
     * 并处理 hls_pull_url_map 与 flv_pull_url 的多画质回落。
     */
    suspend fun fetchLiveStreams(webRid: String, secUserId: String? = null): Map<String, String>? = withContext(Dispatchers.IO) {
        try {
            val ttwid = fetchTtwid()
            val secParam = if (!secUserId.isNullOrEmpty()) "&sec_user_id=${URLEncoder.encode(secUserId, "UTF-8")}" else ""
            val url = "https://live.douyin.com/webcast/room/web/enter/?aid=6383&app_name=douyin_web&live_id=1&device_platform=web&language=zh-CN&enter_from=web_live&cookie_enabled=true&screen_width=1920&screen_height=1080&browser_language=zh-CN&browser_platform=Win32&browser_name=Chrome&browser_version=124.0.0.0&web_rid=$webRid$secParam"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", UA_PC)
                .header("Referer", "https://live.douyin.com/$webRid")
                .header("Accept", "application/json, text/plain, */*")
                .header("Accept-Language", "zh-CN,zh;q=0.9")
                .apply {
                    if (ttwid.isNotEmpty()) {
                        header("Cookie", "ttwid=$ttwid")
                    }
                }
                .build()

            val response = httpClient.newCall(request).execute()
            val jsonStr = response.body?.string() ?: return@withContext null
            val root = JSONObject(jsonStr)
            val roomCandidates = collectRoomCandidates(root)
            val roomData = roomCandidates.firstOrNull() ?: return@withContext null

            val status = roomData.optInt("status")
            if (status != 2) {
                // 主播未开播
                return@withContext null
            }

            val streamUrlObj = roomData.optJSONObject("stream_url")
            val streamDataRaw = streamUrlObj?.optJSONObject("live_core_sdk_data")
                ?.optJSONObject("pull_data")
                ?.optString("stream_data")

            val streams = mutableMapOf<String, String>()
            if (!streamDataRaw.isNullOrEmpty()) {
                val streamTree = JSONObject(streamDataRaw).optJSONObject("data")

                // 优先取高清 (hd) 或超清 (uhd)，回退标清 (sd) 或原画 (origin)
                val hdHls = streamTree?.optJSONObject("hd")?.optJSONObject("main")?.optString("hls")
                val uhdHls = streamTree?.optJSONObject("uhd")?.optJSONObject("main")?.optString("hls")
                val sdHls = streamTree?.optJSONObject("sd")?.optJSONObject("main")?.optString("hls")
                val originHls = streamTree?.optJSONObject("origin")?.optJSONObject("main")?.optString("hls")
                val mdHls = streamTree?.optJSONObject("md")?.optJSONObject("main")?.optString("hls")

                val bestHls = listOf(hdHls, uhdHls, sdHls, originHls, mdHls).firstOrNull { !it.isNullOrEmpty() }
                if (!bestHls.isNullOrEmpty()) {
                    streams["hls"] = bestHls.replace("http://", "https://")
                }

                // 同理优先取高清 (hd) 或超清 (uhd) 的 FLV 地址
                val hdFlv = streamTree?.optJSONObject("hd")?.optJSONObject("main")?.optString("flv")
                val uhdFlv = streamTree?.optJSONObject("uhd")?.optJSONObject("main")?.optString("flv")
                val sdFlv = streamTree?.optJSONObject("sd")?.optJSONObject("main")?.optString("flv")
                val originFlv = streamTree?.optJSONObject("origin")?.optJSONObject("main")?.optString("flv")
                val mdFlv = streamTree?.optJSONObject("md")?.optJSONObject("main")?.optString("flv")

                val bestFlv = listOf(hdFlv, uhdFlv, sdFlv, originFlv, mdFlv).firstOrNull { !it.isNullOrEmpty() }
                if (!bestFlv.isNullOrEmpty()) {
                    streams["flv"] = bestFlv.replace("http://", "https://")
                }
            }

            // 尝试从 hls_pull_url_map 回退 (HD1, FULL_HD1, SD1 等)
            if (!streams.containsKey("hls") && streamUrlObj != null) {
                val hlsMap = streamUrlObj.optJSONObject("hls_pull_url_map")
                val hdMapHls = hlsMap?.optString("HD1")?.ifEmpty { null }
                    ?: hlsMap?.optString("FULL_HD1")?.ifEmpty { null }
                    ?: extractFirstUrlFromObj(hlsMap)
                    ?: streamUrlObj.optString("hls_pull_url").ifEmpty { null }
                if (!hdMapHls.isNullOrEmpty()) {
                    streams["hls"] = hdMapHls.replace("http://", "https://")
                }
            }

            if (!streams.containsKey("flv") && streamUrlObj != null) {
                val flvMap = streamUrlObj.optJSONObject("flv_pull_url")
                val hdMapFlv = flvMap?.optString("HD1")?.ifEmpty { null }
                    ?: flvMap?.optString("FULL_HD1")?.ifEmpty { null }
                    ?: extractFirstUrlFromObj(flvMap)
                    ?: streamUrlObj.optString("rtmp_pull_url").ifEmpty { null }
                if (!hdMapFlv.isNullOrEmpty()) {
                    streams["flv"] = hdMapFlv.replace("http://", "https://")
                }
            }

            return@withContext if (streams.isNotEmpty()) streams else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 3. 追踪短链 302 重定向
     */
    suspend fun resolveShortUrl(url: String): String? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", UA_MOBILE)
                .build()
            val res = noRedirectClient.newCall(req).execute()
            val loc = res.header("Location") ?: ""
            val m = Pattern.compile("(?:reflow/|room_id=|live\\.douyin\\.com/)(\\d{15,22}|\\d{3,14})").matcher(loc)
            if (m.find()) {
                return@withContext m.group(1)
            }
        } catch (_: Exception) {}
        null
    }

    private data class MutableRoomInfo(
        var isLive: Boolean = false,
        var statusKnown: Boolean = false,
        var anchorName: String = "",
        var title: String = "",
        var avatar: String = "",
        val streams: MutableMap<String, QualityStream> = mutableMapOf()
    )

    /**
     * 4. 抓取直播间详情与播放流信息（对齐网页版 live.php 完整规则）
     */
    suspend fun fetchRoomInfo(rid: String, ignoreCache: Boolean = false): LiveStreamData? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!ignoreCache) {
            val cached = streamCache[rid]
            if (cached != null && (now - cached.second) < STREAM_CACHE_TTL && cached.first.isLive && cached.first.streams.isNotEmpty()) {
                return@withContext cached.first
            }
        }

        val info = MutableRoomInfo()

        // 步骤 4.1：长房间号优先查询 room_id 信息 (reflow/info 接口)
        if (rid.length >= 15) {
            try {
                val reflowApi = "https://webcast.amemv.com/webcast/room/reflow/info/?room_id=${URLEncoder.encode(rid, "UTF-8")}&live_id=1&app_id=1128"
                val req = Request.Builder()
                    .url(reflowApi)
                    .header("Accept", "application/json")
                    .header("User-Agent", UA_MOBILE)
                    .build()
                val res = httpClient.newCall(req).execute()
                val raw = res.body?.string() ?: ""
                if (raw.isNotEmpty()) {
                    val root = JSONObject(raw)
                    for (candidate in collectRoomCandidates(root)) {
                        mergeRoomSnapshot(candidate, info)
                    }
                }
            } catch (_: Exception) {}
        }

        // 步骤 4.2：若资料不完整、状态未验证或在线但缺少流地址，再走 web_rid 入口 (web/enter 接口)
        val needWebEnter = (!info.statusKnown || info.anchorName.isEmpty() || info.avatar.isEmpty() || (info.isLive && info.streams.isEmpty()))
        if (needWebEnter) {
            try {
                val ttwid = getTtwid()
                val webUrl = "https://live.douyin.com/webcast/room/web/enter/?aid=6383&app_name=douyin_web&live_id=1&device_platform=web&language=zh-CN&enter_from=web_live&cookie_enabled=true&screen_width=1920&screen_height=1080&browser_language=zh-CN&browser_platform=Win32&browser_name=Chrome&browser_version=124.0.0.0&web_rid=$rid"
                val reqBuilder = Request.Builder()
                    .url(webUrl)
                    .header("Accept", "application/json, text/plain, */*")
                    .header("Accept-Language", "zh-CN,zh;q=0.9")
                    .header("Referer", "https://live.douyin.com/$rid")
                    .header("User-Agent", UA_PC)
                if (ttwid.isNotEmpty()) {
                    reqBuilder.header("Cookie", "ttwid=$ttwid")
                }

                val res = httpClient.newCall(reqBuilder.build()).execute()
                val raw = res.body?.string() ?: ""
                if (raw.isNotEmpty()) {
                    val root = JSONObject(raw)
                    for (candidate in collectRoomCandidates(root)) {
                        mergeRoomSnapshot(candidate, info)
                    }
                }
            } catch (_: Exception) {}
        }

        if (info.anchorName.isEmpty() && !info.isLive && !info.statusKnown) {
            return@withContext null
        }

        val result = LiveStreamData(
            rid = rid,
            isLive = info.isLive,
            statusKnown = info.statusKnown,
            name = info.anchorName.trim(),
            title = if (info.title.isNotEmpty()) info.title else if (info.isLive) "正在直播" else "当前未开播",
            avatar = info.avatar,
            streams = info.streams
        )

        // 写入短期缓存
        streamCache[rid] = Pair(result, now)
        result
    }

    /**
     * 遍历多种响应结构中可能存在 room 对象的候选路径（对齐网页版 live.php collectRoomCandidates）
     * 关键修复：支持 JSONObject 与 JSONArray 混合层级（如 data.data[0]）的正确解析。
     */
    private fun collectRoomCandidates(root: JSONObject): List<JSONObject> {
        val candidates = mutableListOf<JSONObject>()
        val paths = listOf(
            listOf("data", "data", "0"),
            listOf("data", "room"),
            listOf("data", "data", "0", "room"),
            listOf("data", "data", "0", "room_info"),
            listOf("data", "room_info"),
            listOf("data", "data", "room"),
            listOf("room"),
            listOf("room_info")
        )

        for (path in paths) {
            var curr: Any? = root
            for (part in path) {
                curr = when (curr) {
                    is JSONObject -> curr.opt(part)
                    is JSONArray -> {
                        val idx = part.toIntOrNull()
                        if (idx != null && idx >= 0 && idx < curr.length()) {
                            curr.opt(idx)
                        } else null
                    }
                    else -> null
                }
                if (curr == null) break
            }
            if (curr is JSONObject && !candidates.contains(curr)) {
                candidates.add(curr)
            }
        }
        return candidates
    }

    /**
     * 合并房间快照资料（对齐网页版 live.php mergeRoomSnapshot）
     */
    private fun mergeRoomSnapshot(room: JSONObject, info: MutableRoomInfo): Boolean {
        var owner = room.optJSONObject("owner")
        if (owner == null) owner = room.optJSONObject("user")
        if (owner == null) owner = room.optJSONObject("anchor")
        if (owner == null) owner = room.optJSONObject("owner_info")
        if (owner == null) owner = room.optJSONObject("user_info")

        val nickname = firstNonEmpty(
            owner?.optString("nickname"),
            owner?.optString("nick_name"),
            owner?.optString("display_id"),
            room.optString("nickname"),
            room.optString("anchor_name")
        )
        if (nickname.isNotEmpty()) {
            info.anchorName = nickname.take(50)
        }

        val roomTitle = firstNonEmpty(
            room.optString("title"),
            room.optString("room_title"),
            room.optString("live_title")
        )
        if (roomTitle.isNotEmpty()) {
            info.title = roomTitle.take(100)
        }

        val avatar = extractAvatarUrl(owner)
        if (avatar.isNotEmpty()) {
            info.avatar = avatar
        }

        if (room.has("status")) {
            info.statusKnown = true
            val status = room.optInt("status", -1)
            if (status == 2) {
                info.isLive = true
            } else if (!info.isLive) {
                info.isLive = false
            }
        }

        if (info.isLive) {
            val streams = extractStreamSet(room)
            if (streams.isNotEmpty()) {
                info.streams.putAll(streams)
            }
        }

        return nickname.isNotEmpty() || roomTitle.isNotEmpty() || avatar.isNotEmpty() || room.has("status")
    }

    /**
     * 提取全套播放流地址（对齐网页版 live.php extractStreamSet）
     */
    private fun extractStreamSet(room: JSONObject): Map<String, QualityStream> {
        val streams = mutableMapOf<String, QualityStream>()
        val su = room.optJSONObject("stream_url") ?: return streams

        // 1. 从 live_core_sdk_data.pull_data.stream_data 提取多画质树
        val streamDataRaw = su.optJSONObject("live_core_sdk_data")
            ?.optJSONObject("pull_data")
            ?.optString("stream_data")

        if (!streamDataRaw.isNullOrEmpty()) {
            try {
                val tree = JSONObject(streamDataRaw).optJSONObject("data")
                if (tree != null) {
                    // 按规范优先级提取
                    for ((key, label) in QUALITY_ORDER) {
                        val qualityObj = tree.optJSONObject(key) ?: continue
                        val main = qualityObj.optJSONObject("main") ?: qualityObj.optJSONObject("backup")
                        if (main != null) {
                            val flv = fixUrl(main.optString("flv"))
                            val hls = fixUrl(main.optString("hls"))
                            if (!flv.isNullOrEmpty() || !hls.isNullOrEmpty()) {
                                streams[key] = QualityStream(label, flv, hls)
                            }
                        }
                    }

                    // 补全 tree 中未在 QUALITY_ORDER 内的任意其它画质
                    val it = tree.keys()
                    while (it.hasNext()) {
                        val key = it.next()
                        if (!streams.containsKey(key)) {
                            val qualityObj = tree.optJSONObject(key) ?: continue
                            val main = qualityObj.optJSONObject("main") ?: qualityObj.optJSONObject("backup")
                            if (main != null) {
                                val flv = fixUrl(main.optString("flv"))
                                val hls = fixUrl(main.optString("hls"))
                                if (!flv.isNullOrEmpty() || !hls.isNullOrEmpty()) {
                                    streams[key] = QualityStream(key.uppercase(), flv, hls)
                                }
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        // 2. 如果 stream_data 为空或部分缺失，从 stream_url 的顶层属性 (hls_pull_url_map, flv_pull_url) 补全
        val hlsMap = su.optJSONObject("hls_pull_url_map")
        val flvMap = su.optJSONObject("flv_pull_url")

        if (streams.isEmpty()) {
            if (hlsMap != null || flvMap != null) {
                val qualityAliases = listOf(
                    Triple("hd", "HD1", "高清 (720P)"),
                    Triple("uhd", "FULL_HD1", "超清 (1080P)"),
                    Triple("sd", "SD1", "标清 (540P)"),
                    Triple("sd", "SD2", "标清")
                )
                for ((qKey, mapKey, label) in qualityAliases) {
                    val hls = fixUrl(hlsMap?.optString(mapKey))
                    val flv = fixUrl(flvMap?.optString(mapKey))
                    if (!hls.isNullOrEmpty() || !flv.isNullOrEmpty()) {
                        if (!streams.containsKey(qKey)) {
                            streams[qKey] = QualityStream(label, flv, hls)
                        }
                    }
                }
            }

            val defaultHls = fixUrl(su.optString("hls_pull_url")) ?: extractFirstUrlFromObj(hlsMap)
            val defaultFlv = fixUrl(su.optString("rtmp_pull_url")) ?: extractFirstUrlFromObj(flvMap)

            if ((!defaultHls.isNullOrEmpty() || !defaultFlv.isNullOrEmpty()) && streams.isEmpty()) {
                streams["default"] = QualityStream("高清 (推荐)", defaultFlv, defaultHls)
            }
        }

        return streams
    }

    private fun extractAvatarUrl(owner: JSONObject?): String {
        if (owner == null) return ""
        val avatarObj = owner.optJSONObject("avatar_thumb")
            ?: owner.optJSONObject("avatar_medium")
            ?: owner.optJSONObject("avatar")
        if (avatarObj != null) {
            val urlList = avatarObj.optJSONArray("url_list")
            if (urlList != null && urlList.length() > 0) {
                val u = urlList.optString(0, "")
                if (u.isNotEmpty()) return fixUrl(u) ?: ""
            }
            val u = avatarObj.optString("url", "")
            if (u.isNotEmpty()) return fixUrl(u) ?: ""
        }
        return ""
    }

    private fun extractFirstUrlFromObj(obj: JSONObject?): String? {
        if (obj == null) return null
        val it = obj.keys()
        while (it.hasNext()) {
            val key = it.next()
            val u = obj.optString(key)
            if (!u.isNullOrEmpty()) return fixUrl(u)
        }
        return null
    }

    private fun fixUrl(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val trimmed = url.trim()
        return if (trimmed.startsWith("http://", ignoreCase = true)) {
            trimmed.replaceFirst("http://", "https://", ignoreCase = true)
        } else trimmed
    }

    private fun firstNonEmpty(vararg values: String?): String {
        for (v in values) {
            if (!v.isNullOrBlank()) return v.trim()
        }
        return ""
    }

    /**
     * 5. 远程源拉取与合规校验（支持 JSON、TXT、CSV 格式）
     */
    suspend fun fetchRemoteSource(url: String): String = withContext(Dispatchers.IO) {
        val trimmed = url.trim()
        if (!trimmed.startsWith("http://", ignoreCase = true) && !trimmed.startsWith("https://", ignoreCase = true)) {
            throw IllegalArgumentException("仅支持 HTTP / HTTPS 协议的远程链接")
        }

        val req = Request.Builder()
            .url(trimmed)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) DoumouLive/1.0")
            .build()

        val res = httpClient.newCall(req).execute()
        if (!res.isSuccessful) {
            throw IllegalStateException("远程服务器响应错误，HTTP ${res.code}")
        }

        val body = res.body?.string() ?: ""
        if (body.length > 1024 * 1024) {
            throw IllegalStateException("远程源文件体积过大（上限 1MB）")
        }
        body
    }
}
