package com.example.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
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

    private const val MAX_API_BODY_BYTES = 2 * 1024 * 1024
    private const val MAX_REMOTE_SOURCE_BYTES = 1024 * 1024
    private const val MAX_CACHE_ENTRIES = 128
    private const val STREAM_CACHE_TTL = 20_000L

    private val PATTERN_SHORT_URL = Pattern.compile("https?://v\\.douyin\\.com/[A-Za-z0-9_-]+/?")
    private val PATTERN_WEB_URL = Pattern.compile("(?:live\\.douyin\\.com/|follow/live/)(\\d{3,22})")
    private val PATTERN_DIGITS = Pattern.compile("\\b(\\d{3,22})\\b")
    private val PATTERN_TTWID = Pattern.compile("ttwid=([^;\\s]+)")
    private val PATTERN_REDIRECT_RID = Pattern.compile("(?:reflow/|room_id=|live\\.douyin\\.com/)(\\d{15,22}|\\d{3,14})")

    private val sharedDispatcher = Dispatcher().apply {
        maxRequests = 16
        maxRequestsPerHost = 6
    }

    private val httpClient = OkHttpClient.Builder()
        .dispatcher(sharedDispatcher)
        .connectionPool(ConnectionPool(8, 5, TimeUnit.MINUTES))
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.SECONDS)
        .build()

    private val noRedirectClient = httpClient.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    @Volatile
    private var cachedTtwid: String = ""
    @Volatile
    private var ttwidFetchedAt: Long = 0L

    // 线程安全的内存短期流缓存（ConcurrentHashMap 防止多协程并发探活竞争）
    private val streamCache = ConcurrentHashMap<String, Pair<LiveStreamData, Long>>()

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

    fun qualityLabel(qualityKey: String): String = QUALITY_MAP[qualityKey] ?: "画质自适应"

    /**
     * 1. 提取输入源：支持整段手机分享口令、官方短链、PC 网页链接、纯数字房间号
     */
    suspend fun extractRoomId(rawInput: String): Pair<String?, Boolean> = withContext(Dispatchers.IO) {
        val text = rawInput.trim().take(4096)
        if (text.isEmpty()) return@withContext Pair(null, false)

        // 匹配短链接例如 https://v.douyin.com/iABCxyz/
        val shortMatcher = PATTERN_SHORT_URL.matcher(text)
        if (shortMatcher.find()) {
            val shortUrl = shortMatcher.group(0) ?: ""
            val resolved = resolveShortUrl(shortUrl)
            if (resolved != null) {
                return@withContext Pair(resolved, true)
            }
        }

        // 匹配 PC 网页链接 live.douyin.com/xxx 或 follow/live/xxx
        val webMatcher = PATTERN_WEB_URL.matcher(text)
        if (webMatcher.find()) {
            val rid = webMatcher.group(1)
            val isOnce = (rid?.length ?: 0) >= 15
            return@withContext Pair(rid, isOnce)
        }

        // 匹配纯房间数字
        val numMatcher = PATTERN_DIGITS.matcher(text)
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

            httpClient.newCall(req).execute().use { res ->
                val cookieHeaders = res.headers("Set-Cookie")
                for (cookie in cookieHeaders) {
                    val m = PATTERN_TTWID.matcher(cookie)
                    if (m.find()) {
                        val token = m.group(1) ?: ""
                        if (token.isNotEmpty()) {
                            cachedTtwid = token
                            ttwidFetchedAt = now
                            return@withContext cachedTtwid
                        }
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

            httpClient.newCall(req).execute().use { res ->
                val cookieHeaders = res.headers("Set-Cookie")
                for (cookie in cookieHeaders) {
                    val m = PATTERN_TTWID.matcher(cookie)
                    if (m.find()) {
                        val token = m.group(1) ?: ""
                        if (token.isNotEmpty()) {
                            cachedTtwid = token
                            ttwidFetchedAt = now
                            return@withContext cachedTtwid
                        }
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
     * 解析 stream_data 中的 flv 或 hls 播放流地址；TV 端默认 preferSd = true 优先锁定标清 540P (sd)。
     */
    @JvmOverloads
    suspend fun fetchLiveStreams(
        webRid: String,
        secUserId: String? = null,
        preferSd: Boolean = false
    ): Map<String, String>? = withContext(Dispatchers.IO) {
        try {
            val ttwid = fetchTtwid()
            val secParam = if (!secUserId.isNullOrEmpty()) "&sec_user_id=${URLEncoder.encode(secUserId, "UTF-8")}" else ""
            val url = "https://live.douyin.com/webcast/room/web/enter/?aid=6383&app_name=douyin_web&live_id=1&device_platform=web&language=zh-CN&enter_from=web_live&cookie_enabled=true&screen_width=1920&screen_height=1080&browser_language=zh-CN&browser_platform=Win32&browser_name=Chrome&browser_version=124.0.0.0&web_rid=${URLEncoder.encode(webRid, "UTF-8")}$secParam"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", UA_PC)
                .header("Referer", "https://live.douyin.com/${URLEncoder.encode(webRid, "UTF-8")}")
                .header("Accept", "application/json, text/plain, */*")
                .header("Accept-Language", "zh-CN,zh;q=0.9")
                .apply {
                    if (ttwid.isNotEmpty()) {
                        header("Cookie", "ttwid=$ttwid")
                    }
                }
                .build()

            val jsonStr = httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                readBoundedBody(response.body, MAX_API_BODY_BYTES)
            }
            if (jsonStr.isEmpty()) return@withContext null

            val root = JSONObject(jsonStr)
            val roomCandidates = collectRoomCandidates(root)
            val roomData = roomCandidates.firstOrNull() ?: return@withContext null

            val status = roomData.optInt("status")
            if (status != 2) {
                return@withContext null
            }

            val streamUrlObj = roomData.optJSONObject("stream_url")
            val streamDataRaw = streamUrlObj?.optJSONObject("live_core_sdk_data")
                ?.optJSONObject("pull_data")
                ?.optString("stream_data")

            val streams = mutableMapOf<String, String>()
            val qualityOrder = if (preferSd) {
                listOf("sd", "ld", "md", "hd", "uhd", "origin")
            } else {
                listOf("hd", "uhd", "sd", "origin", "md")
            }

            if (!streamDataRaw.isNullOrEmpty()) {
                val streamTree = JSONObject(streamDataRaw).optJSONObject("data")

                val bestHls = qualityOrder.firstNotNullOfOrNull { q ->
                    fixUrl(streamTree?.optJSONObject(q)?.optJSONObject("main")?.optString("hls"))
                }
                if (!bestHls.isNullOrEmpty()) {
                    streams["hls"] = bestHls
                }

                val bestFlv = qualityOrder.firstNotNullOfOrNull { q ->
                    fixUrl(streamTree?.optJSONObject(q)?.optJSONObject("main")?.optString("flv"))
                }
                if (!bestFlv.isNullOrEmpty()) {
                    streams["flv"] = bestFlv
                }
            }

            val mapPriority = if (preferSd) listOf("SD1", "SD2", "HD1", "FULL_HD1") else listOf("HD1", "FULL_HD1", "SD1", "SD2")

            if (!streams.containsKey("hls") && streamUrlObj != null) {
                val hlsMap = streamUrlObj.optJSONObject("hls_pull_url_map")
                val fallbackHls = mapPriority.firstNotNullOfOrNull { k -> fixUrl(hlsMap?.optString(k)) }
                    ?: extractFirstUrlFromObj(hlsMap)
                    ?: fixUrl(streamUrlObj.optString("hls_pull_url"))
                if (!fallbackHls.isNullOrEmpty()) {
                    streams["hls"] = fallbackHls
                }
            }

            if (!streams.containsKey("flv") && streamUrlObj != null) {
                val flvMap = streamUrlObj.optJSONObject("flv_pull_url")
                val fallbackFlv = mapPriority.firstNotNullOfOrNull { k -> fixUrl(flvMap?.optString(k)) }
                    ?: extractFirstUrlFromObj(flvMap)
                    ?: fixUrl(streamUrlObj.optString("rtmp_pull_url"))
                if (!fallbackFlv.isNullOrEmpty()) {
                    streams["flv"] = fallbackFlv
                }
            }

            return@withContext if (streams.isNotEmpty()) streams else null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 3. 追踪短链 302 重定向
     */
    suspend fun resolveShortUrl(url: String): String? = withContext(Dispatchers.IO) {
        try {
            val safeUrl = sanitizeHttpUrl(url) ?: return@withContext null
            val req = Request.Builder()
                .url(safeUrl)
                .header("User-Agent", UA_MOBILE)
                .build()
            noRedirectClient.newCall(req).execute().use { res ->
                val loc = res.header("Location") ?: ""
                val m = PATTERN_REDIRECT_RID.matcher(loc)
                if (m.find()) {
                    return@withContext m.group(1)
                }
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
                val raw = httpClient.newCall(req).execute().use { res ->
                    if (res.isSuccessful) readBoundedBody(res.body, MAX_API_BODY_BYTES) else ""
                }
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
                val encodedRid = URLEncoder.encode(rid, "UTF-8")
                val webUrl = "https://live.douyin.com/webcast/room/web/enter/?aid=6383&app_name=douyin_web&live_id=1&device_platform=web&language=zh-CN&enter_from=web_live&cookie_enabled=true&screen_width=1920&screen_height=1080&browser_language=zh-CN&browser_platform=Win32&browser_name=Chrome&browser_version=124.0.0.0&web_rid=$encodedRid"
                val reqBuilder = Request.Builder()
                    .url(webUrl)
                    .header("Accept", "application/json, text/plain, */*")
                    .header("Accept-Language", "zh-CN,zh;q=0.9")
                    .header("Referer", "https://live.douyin.com/$encodedRid")
                    .header("User-Agent", UA_PC)
                if (ttwid.isNotEmpty()) {
                    reqBuilder.header("Cookie", "ttwid=$ttwid")
                }

                val raw = httpClient.newCall(reqBuilder.build()).execute().use { res ->
                    if (res.isSuccessful) readBoundedBody(res.body, MAX_API_BODY_BYTES) else ""
                }
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

        // 写入短期缓存（控制最大容量并清理过期条目）
        if (streamCache.size >= MAX_CACHE_ENTRIES) {
            streamCache.entries.removeIf { (now - it.value.second) >= STREAM_CACHE_TTL }
            if (streamCache.size >= MAX_CACHE_ENTRIES) {
                streamCache.clear()
            }
        }
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

        // 2. 从 stream_url 的顶层属性 (hls_pull_url_map, flv_pull_url) 补全缺失画质（确保标清 SD1/540P 不被漏掉）
        val hlsMap = su.optJSONObject("hls_pull_url_map")
        val flvMap = su.optJSONObject("flv_pull_url")

        if (hlsMap != null || flvMap != null) {
            val qualityAliases = listOf(
                Triple("hd", "HD1", "高清 (720P)"),
                Triple("uhd", "FULL_HD1", "超清 (1080P)"),
                Triple("sd", "SD1", "标清 (540P)"),
                Triple("sd", "SD2", "标清 (540P)")
            )
            for ((qKey, mapKey, label) in qualityAliases) {
                if (!streams.containsKey(qKey)) {
                    val hls = fixUrl(hlsMap?.optString(mapKey))
                    val flv = fixUrl(flvMap?.optString(mapKey))
                    if (!hls.isNullOrEmpty() || !flv.isNullOrEmpty()) {
                        streams[qKey] = QualityStream(label, flv, hls)
                    }
                }
            }
        }

        if (streams.isEmpty()) {
            val defaultHls = fixUrl(su.optString("hls_pull_url")) ?: extractFirstUrlFromObj(hlsMap)
            val defaultFlv = fixUrl(su.optString("rtmp_pull_url")) ?: extractFirstUrlFromObj(flvMap)

            if (!defaultHls.isNullOrEmpty() || !defaultFlv.isNullOrEmpty()) {
                streams["default"] = QualityStream("默认画质", defaultFlv, defaultHls)
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
        return sanitizeHttpUrl(url)
    }

    /**
     * 规范并校验流媒体与头像 URL：仅放行 http/https 协议并强制升级为 https，拦截 javascript:/file:/data: 等危险协议
     */
    fun sanitizeHttpUrl(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val trimmed = url.trim()
        if (trimmed.length > 4096) return null
        val normalized = when {
            trimmed.startsWith("https://", ignoreCase = true) -> "https://" + trimmed.substring(8)
            trimmed.startsWith("http://", ignoreCase = true) -> "https://" + trimmed.substring(7)
            else -> return null
        }
        return try {
            val uri = URI(normalized)
            val host = uri.host
            if (host.isNullOrBlank()) null else normalized
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 校验远程订阅源 URL：强制 HTTPS，并阻断本地环回/内网保留网段 SSRF 攻击面
     */
    internal fun validateRemoteImportUrl(url: String): String {
        val trimmed = url.trim()
        if (trimmed.length > 2048) {
            throw IllegalArgumentException("订阅链接长度超出安全限制")
        }
        if (!trimmed.startsWith("https://", ignoreCase = true)) {
            throw IllegalArgumentException("出于传输安全保护，远程订阅仅支持 HTTPS 链接")
        }
        val uri = try {
            URI(trimmed)
        } catch (_: Exception) {
            throw IllegalArgumentException("无效的远程链接格式")
        }
        if (!uri.userInfo.isNullOrEmpty()) {
            throw IllegalArgumentException("禁止在订阅链接中携带身份凭据")
        }
        val rawHost = uri.host?.lowercase()?.trimEnd('.')?.removePrefix("[")?.removeSuffix("]")
            ?: throw IllegalArgumentException("远程链接缺少有效域名")

        if (isBlockedSsrfHost(rawHost)) {
            throw IllegalArgumentException("出于安全限制，禁止访问本地或内网保留地址")
        }
        return trimmed
    }

    internal fun isBlockedSsrfHost(host: String): Boolean {
        val h = host.lowercase().trimEnd('.').removePrefix("[").removeSuffix("]")
        if (h.isEmpty() || h == "localhost" || h.endsWith(".localhost") || h.endsWith(".local") || h.endsWith(".internal")) {
            return true
        }
        if (h == "::1" || h == "0:0:0:0:0:0:0:1" || h == "0.0.0.0" || h.startsWith("fe80:") || h.startsWith("fc") || h.startsWith("fd")) {
            return true
        }
        // 字面量 IPv4 检测（覆盖离线单测及 DNS 前置拦截）
        val ipv4Parts = h.split('.')
        if (ipv4Parts.size == 4 && ipv4Parts.all { p -> p.toIntOrNull()?.let { it in 0..255 } == true }) {
            val a = ipv4Parts[0].toInt()
            val b = ipv4Parts[1].toInt()
            if (a == 127 || a == 10 || a == 0) return true
            if (a == 169 && b == 254) return true
            if (a == 172 && b in 16..31) return true
            if (a == 192 && b == 168) return true
            if (a == 100 && b in 64..127) return true
        }
        // 若主机为已解析 IP（避免直接触发不必要的长时间 DNS），复核 InetAddress 属性
        return try {
            val isIpLiteral = h.all { it.isDigit() || it == '.' || it == ':' || it in 'a'..'f' }
            if (isIpLiteral) {
                val addr = InetAddress.getByName(h)
                addr.isAnyLocalAddress || addr.isLoopbackAddress || addr.isLinkLocalAddress ||
                    addr.isSiteLocalAddress || addr.isMulticastAddress ||
                    (addr is Inet6Address && ((addr.address[0].toInt() and 0xFE) == 0xFC))
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun readBoundedBody(body: ResponseBody?, maxBytes: Int): String {
        if (body == null) return ""
        val contentLen = body.contentLength()
        if (contentLen > maxBytes) {
            throw IllegalStateException("远程响应体积超出安全上限 (${maxBytes / 1024}KB)")
        }
        body.byteStream().use { input ->
            val buffer = ByteArrayOutputStream()
            val chunk = ByteArray(8192)
            var total = 0
            while (true) {
                val read = input.read(chunk)
                if (read <= 0) break
                total += read
                if (total > maxBytes) {
                    throw IllegalStateException("远程响应体积超出安全上限 (${maxBytes / 1024}KB)")
                }
                buffer.write(chunk, 0, read)
            }
            return String(buffer.toByteArray(), StandardCharsets.UTF_8)
        }
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
        val safeUrl = validateRemoteImportUrl(url)

        val req = Request.Builder()
            .url(safeUrl)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) DoumouLive/1.0")
            .build()

        noRedirectClient.newCall(req).execute().use { res ->
            if (res.isRedirect) {
                throw IllegalStateException("出于安全防护，远程订阅不支持自动跳转重定向")
            }
            if (!res.isSuccessful) {
                throw IllegalStateException("远程服务器响应错误，HTTP ${res.code}")
            }
            readBoundedBody(res.body, MAX_REMOTE_SOURCE_BYTES)
        }
    }
}
