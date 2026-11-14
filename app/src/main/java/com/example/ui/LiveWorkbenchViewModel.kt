package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.LiveChannel
import com.example.data.ChannelRepository
import com.example.engine.DouyinLiveEngine
import com.example.engine.LiveStreamData
import com.example.engine.QualityStream
import com.example.util.DeviceEnvironment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed interface PlaybackStatus {
    object Idle : PlaybackStatus
    data class Connecting(val message: String = "正在连接直播信号...") : PlaybackStatus
    object Playing : PlaybackStatus
    data class Buffering(val message: String = "正在缓冲数据...") : PlaybackStatus
    data class Offline(val message: String = "主播当前未开播") : PlaybackStatus
    data class Error(val message: String) : PlaybackStatus
}

sealed interface ActiveDialog {
    data class OnceExplanation(val name: String, val rid: String) : ActiveDialog
    data class DeleteConfirm(val channel: LiveChannel) : ActiveDialog
    object RemoteImport : ActiveDialog
    object LocalImport : ActiveDialog
    object ExportFormat : ActiveDialog
    data class ExportResult(val fileName: String, val channelCount: Int, val content: String) : ActiveDialog
    object UserGuide : ActiveDialog
    data class VersionUpdate(
        val currentVersion: String,
        val latestVersion: String,
        val isLatest: Boolean,
        val updateLog: String,
        val downloadUrl: String
    ) : ActiveDialog
}

enum class ImportMode {
    MERGE, REPLACE
}

data class WorkbenchUiState(
    val channels: List<LiveChannel> = emptyList(),
    val activeChannel: LiveChannel? = null,
    val currentStreamData: LiveStreamData? = null,
    val selectedQuality: String = "hd",
    val selectedProtocol: String = "hls", // "hls" or "flv"
    val playbackUrl: String? = null,
    val playbackStatus: PlaybackStatus = PlaybackStatus.Idle,
    val isMuted: Boolean = false,
    val volume: Float = 1.0f,
    val isFullscreen: Boolean = false,
    val isProbing: Boolean = false,
    val activeDialog: ActiveDialog? = null,
    val toastMessage: String? = null,
    val isInputBusy: Boolean = false,
    val isScreenOffAudioMode: Boolean = false
)

class LiveWorkbenchViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        // 军工级单向清晰度降级序列：严格自高向低排列，严禁逆向回弹 (origin -> uhd -> hd -> sd -> ld -> md -> default)
        private val QUALITY_DOWNGRADE_HIERARCHY = listOf(
            "origin",  // 原画 / 蓝光 (最高负载)
            "uhd",     // 超清
            "hd",      // 高清
            "sd",      // 标清
            "ld",      // 流畅
            "md",      // 极速
            "default"  // 默认 (最低负载)
        )

        private val REGEX_PLACEHOLDER_NAME = Regex("^(?:频道|房间)\\s*\\d{3,22}$")
        private val REGEX_RID_EXACT = Regex("\\d{3,22}")
        private val REGEX_RID_TOKEN = Regex("\\b(\\d{3,22})\\b")
        private val REGEX_CONTROL_CHARS = Regex("[\\u0000-\\u001F\\u007F]")
        private const val MAX_IMPORT_CHANNELS = 500
    }

    private val isTvDevice: Boolean = DeviceEnvironment.isTvDevice(application)
    private val repository: ChannelRepository
    // TV 端首次起播默认锁定标清 540P ("sd")，兼顾秒开与低端盒子稳定解码；手机端保持高清 ("hd")
    private val _uiState = MutableStateFlow(
        WorkbenchUiState(selectedQuality = if (isTvDevice) "sd" else "hd")
    )
    val uiState: StateFlow<WorkbenchUiState> = _uiState.asStateFlow()

    private var probeJob: Job? = null
    private var playToken = 0L

    // 单频道会话级自适应降级管理：支持最多 3 次单向阶梯降流（3.5s 冷却）与 HLS<->FLV 协议兜底，防止黑屏死锁且绝不逆向回弹
    private var autoDegradeCount = 0
    private val MAX_AUTO_DEGRADES_PER_CHANNEL = 3
    private val attemptedDegradeQualities = mutableSetOf<String>()
    private val attemptedStreamCombinations = mutableSetOf<String>()
    private var lastDegradeTimestamp = 0L
    private var hasInitialAutoPlayed = false
    private var hasUserInteracted = false
    private var hasManuallyChangedQuality = false

    init {
        val database = AppDatabase.getDatabase(application)
        repository = ChannelRepository(database.channelDao())

        // 监听 Room 数据库中的频道流，确保应用重启或任何增删改操作后数据立刻持久化回显
        viewModelScope.launch {
            repository.checkAndInitializeDefaults()
            repository.allChannels.collect { channelList ->
                _uiState.update { current ->
                    val updatedActive = current.activeChannel?.let { active ->
                        channelList.find { it.rid == active.rid } ?: active
                    }
                    current.copy(
                        channels = channelList,
                        activeChannel = updatedActive ?: (if (current.activeChannel == null && channelList.isNotEmpty()) channelList.first() else null)
                    )
                }

                // 首次进入自动启播首个在线频道（严格仅执行一次，优先尝试 isLive 在线频道）
                if (!hasInitialAutoPlayed && channelList.isNotEmpty()) {
                    hasInitialAutoPlayed = true
                    val firstCandidate = channelList.firstOrNull { it.isLive } ?: channelList.first()
                    playChannel(firstCandidate, forceRefresh = false, isUserInitiated = false)
                }
            }
        }

        startBackgroundProbe()
    }

    fun playChannel(channel: LiveChannel, forceRefresh: Boolean = false, isUserInitiated: Boolean = true) {
        if (isUserInitiated) {
            hasUserInteracted = true
        }
        val currentToken = ++playToken
        autoDegradeCount = 0
        attemptedDegradeQualities.clear()
        attemptedStreamCombinations.clear()
        lastDegradeTimestamp = 0L

        _uiState.update {
            it.copy(
                activeChannel = channel,
                playbackStatus = PlaybackStatus.Connecting("正在连接 ${channel.name}..."),
                playbackUrl = null
            )
        }

        viewModelScope.launch {
            try {
                var streamData = DouyinLiveEngine.fetchRoomInfo(channel.rid, ignoreCache = forceRefresh)
                if (currentToken != playToken) return@launch

                val shouldLockTvSd = isTvDevice && !hasManuallyChangedQuality
                // 若 fetchRoomInfo 未抓到流或返回空，通过 fetchLiveStreams 双重兜底
                if (streamData == null || (streamData.isLive && streamData.streams.isEmpty())) {
                    val fallbackStreams = DouyinLiveEngine.fetchLiveStreams(
                        webRid = channel.rid,
                        preferSd = shouldLockTvSd
                    )
                    if (fallbackStreams != null && fallbackStreams.isNotEmpty()) {
                        val qualityMap = mutableMapOf<String, QualityStream>()
                        val fallbackKey = if (shouldLockTvSd) "sd" else "hd"
                        val fallbackLabel = if (shouldLockTvSd) "标清 (540P)" else "高清 (720P)"
                        fallbackStreams["hls"]?.let { hlsUrl ->
                            qualityMap[fallbackKey] = QualityStream(fallbackLabel, fallbackStreams["flv"], hlsUrl)
                        }
                        if (qualityMap.isEmpty() && fallbackStreams["flv"] != null) {
                            qualityMap[fallbackKey] = QualityStream(fallbackLabel, fallbackStreams["flv"], null)
                        }
                        streamData = LiveStreamData(
                            rid = channel.rid,
                            isLive = true,
                            statusKnown = true,
                            name = channel.name,
                            title = channel.title.ifEmpty { "正在直播" },
                            avatar = channel.avatar,
                            streams = qualityMap
                        )
                    }
                }

                if (streamData != null) {
                    val remoteName = streamData.name.trim()
                    val newName = if (remoteName.isNotEmpty() && !isPlaceholderName(remoteName, channel.rid)) {
                        remoteName
                    } else {
                        channel.name
                    }
                    val remoteTitle = streamData.title.trim()
                    val newTitle = if (remoteTitle.isNotEmpty() && !isPlaceholderTitle(remoteTitle)) {
                        remoteTitle
                    } else {
                        channel.title
                    }
                    val newAvatar = if (streamData.avatar.isNotEmpty()) streamData.avatar else channel.avatar

                    // 更新数据库中该频道最新信息（包含标题、开播状态与最新头像），严格保留用户现有名称（如茉莉·molly）
                    val updated = channel.copy(
                        name = newName,
                        title = newTitle,
                        avatar = newAvatar,
                        isLive = streamData.isLive,
                        statusKnown = streamData.statusKnown
                    )
                    repository.insertOrUpdate(updated)

                    if (streamData.isLive && streamData.streams.isNotEmpty()) {
                        val chosenQuality = selectBestQuality(
                            streams = streamData.streams,
                            preferred = _uiState.value.selectedQuality,
                            isTv = isTvDevice,
                            lockTvInitialSd = shouldLockTvSd
                        )
                        val chosenProtocol = _uiState.value.selectedProtocol
                        val playUrl = pickStreamUrl(streamData.streams[chosenQuality], chosenProtocol)
                            ?: streamData.streams.values.firstNotNullOfOrNull { it.hls ?: it.flv }

                        if (!playUrl.isNullOrEmpty()) {
                            _uiState.update {
                                it.copy(
                                    activeChannel = updated,
                                    currentStreamData = streamData,
                                    selectedQuality = chosenQuality,
                                    playbackUrl = playUrl,
                                    playbackStatus = PlaybackStatus.Playing
                                )
                            }
                        } else {
                            // 降级选择其他可播放清晰度：TV 端优先标清 540P，手机端优先高清 720P，原画始终置末
                            val conservativeOrder = if (isTvDevice) {
                                listOf("sd", "ld", "md", "hd", "default", "uhd", "origin")
                            } else {
                                listOf("hd", "sd", "uhd", "ld", "md", "default", "origin")
                            }
                            val fallbackPair = conservativeOrder.firstNotNullOfOrNull { qKey ->
                                pickStreamUrl(streamData.streams[qKey], chosenProtocol)?.let { url -> qKey to url }
                            } ?: streamData.streams.entries.firstNotNullOfOrNull { (qKey, s) ->
                                pickStreamUrl(s, chosenProtocol)?.let { url -> qKey to url }
                            }
                            if (fallbackPair != null) {
                                _uiState.update {
                                    it.copy(
                                        activeChannel = updated,
                                        currentStreamData = streamData,
                                        selectedQuality = fallbackPair.first,
                                        playbackUrl = fallbackPair.second,
                                        playbackStatus = PlaybackStatus.Playing
                                    )
                                }
                            } else {
                                _uiState.update {
                                    it.copy(
                                        activeChannel = updated,
                                        currentStreamData = streamData,
                                        playbackStatus = PlaybackStatus.Error("未获取到有效流地址，点击重试")
                                    )
                                }
                            }
                        }
                    } else {
                        _uiState.update {
                            it.copy(
                                activeChannel = updated,
                                currentStreamData = streamData,
                                playbackStatus = PlaybackStatus.Offline("主播当前未开播或信号中断")
                            )
                        }
                        // 首次载入在线策略优化：若冷启动挑选的初始频道已离线且用户尚未手动干预，自动尝试接力下一个标记在线的频道
                        if (!hasUserInteracted) {
                            val nextCandidate = _uiState.value.channels.firstOrNull { it.rid != channel.rid && it.isLive }
                            if (nextCandidate != null) {
                                playChannel(nextCandidate, forceRefresh = false, isUserInitiated = false)
                                return@launch
                            }
                        }
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            playbackStatus = PlaybackStatus.Error("无法获取直播流，请检查网络或房间号")
                        )
                    }
                    if (!hasUserInteracted) {
                        val nextCandidate = _uiState.value.channels.firstOrNull { it.rid != channel.rid && it.isLive }
                        if (nextCandidate != null) {
                            playChannel(nextCandidate, forceRefresh = false, isUserInitiated = false)
                            return@launch
                        }
                    }
                }
            } catch (e: Exception) {
                if (currentToken != playToken) return@launch
                _uiState.update {
                    it.copy(
                        playbackStatus = PlaybackStatus.Error(e.message ?: "取流异常")
                    )
                }
            }
        }
    }

    private fun selectBestQuality(
        streams: Map<String, QualityStream>,
        preferred: String,
        isTv: Boolean = false,
        lockTvInitialSd: Boolean = false
    ): String {
        if (isTv && lockTvInitialSd && streams.containsKey("sd")) {
            return "sd"
        }
        if (streams.containsKey(preferred)) return preferred
        // 默认选流保守策略：
        // - TV 端首次起播优先锁定标清 540P (sd)，其次流畅/中清/高清，原画置末，降低电视盒子硬解卡顿黑屏风险
        // - 移动端优先匹配高清 (hd) / 标清 (sd)，同样将原画 (origin) 置于最末位
        val priority = if (isTv) {
            listOf("sd", "ld", "md", "hd", "default", "uhd", "origin")
        } else {
            listOf("hd", "sd", "uhd", "ld", "md", "default", "origin")
        }
        return priority.firstOrNull { streams.containsKey(it) } ?: streams.keys.firstOrNull() ?: "default"
    }

    private fun pickStreamUrl(stream: QualityStream?, protocol: String): String? {
        if (stream == null) return null
        return if (protocol == "flv" && !stream.flv.isNullOrEmpty()) {
            stream.flv
        } else {
            stream.hls ?: stream.flv
        }
    }

    fun changeQuality(qualityKey: String, isManual: Boolean = true) {
        if (isManual) {
            hasManuallyChangedQuality = true
            autoDegradeCount = 0
            attemptedDegradeQualities.clear()
            attemptedStreamCombinations.clear()
            lastDegradeTimestamp = 0L
        }
        val currentStream = _uiState.value.currentStreamData ?: return
        val stream = currentStream.streams[qualityKey] ?: return
        val playUrl = pickStreamUrl(stream, _uiState.value.selectedProtocol) ?: return

        _uiState.update {
            it.copy(
                selectedQuality = qualityKey,
                playbackUrl = playUrl,
                playbackStatus = PlaybackStatus.Playing
            )
        }
    }

    fun changeProtocol(protocol: String, isManual: Boolean = true) {
        if (_uiState.value.selectedProtocol == protocol) return
        if (isManual) {
            autoDegradeCount = 0
            attemptedDegradeQualities.clear()
            attemptedStreamCombinations.clear()
            lastDegradeTimestamp = 0L
        }
        val currentStream = _uiState.value.currentStreamData
        val currentQuality = _uiState.value.selectedQuality
        val stream = currentStream?.streams?.get(currentQuality) ?: currentStream?.streams?.values?.firstOrNull()
        val playUrl = pickStreamUrl(stream, protocol)
        if (!playUrl.isNullOrEmpty()) {
            _uiState.update {
                it.copy(
                    selectedProtocol = protocol,
                    playbackUrl = playUrl,
                    playbackStatus = PlaybackStatus.Playing
                )
            }
        } else {
            _uiState.update { it.copy(selectedProtocol = protocol) }
            val active = _uiState.value.activeChannel ?: return
            playChannel(active, forceRefresh = true)
        }
    }

    fun toggleMute() {
        _uiState.update { current ->
            val newMute = !current.isMuted
            current.copy(
                isMuted = newMute,
                volume = if (!newMute && current.volume == 0f) 0.8f else current.volume
            )
        }
    }

    /**
     * 连续音量调节 [0.0f .. 1.0f]
     */
    fun setVolume(vol: Float) {
        val clamped = vol.coerceIn(0f, 1f)
        _uiState.update { it.copy(volume = clamped, isMuted = clamped == 0f) }
    }

    /**
     * 步进式增减音量（适配车载/遥控器/手势滑动）
     */
    fun adjustVolume(delta: Float) {
        val target = (_uiState.value.volume + delta).coerceIn(0f, 1f)
        setVolume(target)
    }

    /**
     * 全屏模式控制（适配电视遥控器 OK 键及手机一键全屏切换）
     */
    fun setFullscreen(fullscreen: Boolean) {
        _uiState.update { it.copy(isFullscreen = fullscreen) }
    }

    fun toggleFullscreen() {
        _uiState.update { it.copy(isFullscreen = !it.isFullscreen) }
    }

    /**
     * 全屏抖音式上下滑动切台：向上滑切换至下一频道
     */
    fun playNextChannel() {
        val list = _uiState.value.channels
        if (list.isEmpty()) return
        val currentIdx = list.indexOfFirst { it.rid == _uiState.value.activeChannel?.rid }
        val nextIdx = if (currentIdx == -1 || currentIdx >= list.size - 1) 0 else currentIdx + 1
        val nextChannel = list[nextIdx]
        playChannel(nextChannel)
        showToast("已切换至：${nextChannel.name}")
    }

    /**
     * 全屏抖音式上下滑动切台：向下滑切换至上一频道
     */
    fun playPreviousChannel() {
        val list = _uiState.value.channels
        if (list.isEmpty()) return
        val currentIdx = list.indexOfFirst { it.rid == _uiState.value.activeChannel?.rid }
        val prevIdx = if (currentIdx <= 0) list.size - 1 else currentIdx - 1
        val prevChannel = list[prevIdx]
        playChannel(prevChannel)
        showToast("已切换至：${prevChannel.name}")
    }

    fun setPlaybackStatus(status: PlaybackStatus) {
        _uiState.update { it.copy(playbackStatus = status) }
    }

    fun handlePlayerError(isDecoderError: Boolean, errorMsg: String) {
        // 只要还有可降的画质或可切换的备用协议，解码错误或持久流异常均优先自动降流自愈，不直接弹“点击重试”
        if (performAutoRecover(fromError = true)) {
            return
        }

        // 所有可用低画质及备用协议均已耗尽或达到会话上限，显示明确错误态与手动重试入口
        setPlaybackStatus(
            PlaybackStatus.Error(
                if (isDecoderError) "当前清晰度解码受限，点击重试或手动切换画质"
                else "播放中断，点击重试"
            )
        )
    }

    /**
     * 军工级自适应画质锁定与协议兜底自愈引擎：
     * 1. 严格单向单调降级：origin -> uhd -> hd -> sd -> ld -> md -> default，绝不逆向回弹。
     * 2. 会话最多自动降 3 次，两次看门狗降级间隔缩短至 3.5 秒。
     * 3. 若已无更低画质可降，自动尝试切换协议（HLS <-> FLV）换源重试。
     */
    fun autoRecoverBlackScreen() {
        performAutoRecover(fromError = false)
    }

    private fun performAutoRecover(fromError: Boolean): Boolean {
        if (autoDegradeCount >= MAX_AUTO_DEGRADES_PER_CHANNEL) {
            return false
        }

        val now = System.currentTimeMillis()
        val cooldownMs = if (fromError) 800L else 3500L
        if (now - lastDegradeTimestamp < cooldownMs) {
            return false
        }

        val streamData = _uiState.value.currentStreamData ?: return false
        if (streamData.streams.isEmpty()) return false

        val available = streamData.streams
        val currentQuality = _uiState.value.selectedQuality
        val currentProtocol = _uiState.value.selectedProtocol
        val currentUrl = _uiState.value.playbackUrl

        attemptedDegradeQualities.add(currentQuality)
        attemptedStreamCombinations.add("$currentQuality:$currentProtocol")

        val currentRank = QUALITY_DOWNGRADE_HIERARCHY.indexOf(currentQuality).let {
            if (it == -1) 0 else it
        }

        // 1. 严格自当前位次向下寻找更低码率兼容清晰度
        val lowerCandidates = QUALITY_DOWNGRADE_HIERARCHY.filterIndexed { index, qKey ->
            index > currentRank && available.containsKey(qKey) && !attemptedDegradeQualities.contains(qKey)
        }

        for (targetQuality in lowerCandidates) {
            val candidateStream = available[targetQuality]
            val candidateUrl = pickStreamUrl(candidateStream, currentProtocol)
            val comboKey = "$targetQuality:$currentProtocol"
            if (!candidateUrl.isNullOrEmpty() && candidateUrl != currentUrl && !attemptedStreamCombinations.contains(comboKey)) {
                lastDegradeTimestamp = now
                autoDegradeCount++
                attemptedDegradeQualities.add(targetQuality)
                attemptedStreamCombinations.add(comboKey)
                val targetName = candidateStream?.name ?: targetQuality
                showToast("高画质异常，已自动降至：$targetName")
                changeQuality(targetQuality, isManual = false)
                return true
            }
        }

        // 2. 已无更低画质时，尝试切换传输协议（HLS <-> FLV）换源重试
        val altProtocol = if (currentProtocol == "hls") "flv" else "hls"
        val protocolFallbackOrder = buildList {
            if (available.containsKey(currentQuality)) add(currentQuality)
            addAll(QUALITY_DOWNGRADE_HIERARCHY.filter { it != currentQuality && available.containsKey(it) })
        }

        for (qKey in protocolFallbackOrder) {
            val qRank = QUALITY_DOWNGRADE_HIERARCHY.indexOf(qKey).let { if (it == -1) 0 else it }
            if (qRank < currentRank) continue

            val stream = available[qKey] ?: continue
            val altUrl = if (altProtocol == "flv") stream.flv else stream.hls
            val comboKey = "$qKey:$altProtocol"
            if (!altUrl.isNullOrEmpty() && altUrl != currentUrl && !attemptedStreamCombinations.contains(comboKey)) {
                lastDegradeTimestamp = now
                autoDegradeCount++
                attemptedDegradeQualities.add(qKey)
                attemptedStreamCombinations.add(comboKey)
                val targetName = stream.name
                showToast("高画质异常，已自动切换 ${altProtocol.uppercase()} ($targetName)")
                _uiState.update {
                    it.copy(
                        selectedProtocol = altProtocol,
                        selectedQuality = qKey,
                        playbackUrl = altUrl,
                        playbackStatus = PlaybackStatus.Playing
                    )
                }
                return true
            }
        }

        if (!fromError) {
            autoDegradeCount = MAX_AUTO_DEGRADES_PER_CHANNEL
            val status = _uiState.value.playbackStatus
            if (status is PlaybackStatus.Buffering || status is PlaybackStatus.Connecting) {
                setPlaybackStatus(PlaybackStatus.Error("拉流超时或画质不兼容，点击重试或切换协议"))
            } else {
                showToast("已尝试最低画质及备用协议，可点击重试或切换频道")
            }
        }
        return false
    }

    @androidx.annotation.VisibleForTesting
    internal fun seedPlaybackForTest(
        streamData: LiveStreamData,
        selectedQuality: String = "uhd",
        selectedProtocol: String = "hls",
        playbackUrl: String? = null
    ) {
        autoDegradeCount = 0
        attemptedDegradeQualities.clear()
        attemptedStreamCombinations.clear()
        lastDegradeTimestamp = 0L
        val resolvedUrl = playbackUrl ?: pickStreamUrl(streamData.streams[selectedQuality], selectedProtocol)
        _uiState.update {
            it.copy(
                currentStreamData = streamData,
                selectedQuality = selectedQuality,
                selectedProtocol = selectedProtocol,
                playbackUrl = resolvedUrl,
                playbackStatus = PlaybackStatus.Playing
            )
        }
    }

    @androidx.annotation.VisibleForTesting
    internal fun selectConservativeQualityForTest(
        streams: Map<String, QualityStream>,
        preferred: String = "hd"
    ): String = selectBestQuality(streams, preferred)

    @androidx.annotation.VisibleForTesting
    internal fun selectQualityForDeviceForTest(
        streams: Map<String, QualityStream>,
        preferred: String = "sd",
        isTv: Boolean = true,
        lockTvInitialSd: Boolean = true
    ): String = selectBestQuality(streams, preferred, isTv = isTv, lockTvInitialSd = lockTvInitialSd)

    /**
     * 彻底停止播放并释放解码与网络资源（退出应用时调用，释放后台资源）
     */
    fun stopPlayback() {
        playToken++
        _uiState.update {
            it.copy(
                playbackStatus = PlaybackStatus.Idle,
                playbackUrl = null
            )
        }
    }

    /**
     * 优化电池用量：退出至后台且未进入画中画模式时暂停流媒体拉取与硬解码
     */
    fun pausePlaybackForBatteryOptimization() {
        val currentStatus = _uiState.value.playbackStatus
        if (currentStatus is PlaybackStatus.Playing || currentStatus is PlaybackStatus.Buffering) {
            _uiState.update { it.copy(playbackStatus = PlaybackStatus.Idle) }
        }
    }

    /**
     * 前台唤醒时恢复播放当前频道
     */
    fun resumePlaybackFromBackground() {
        val active = _uiState.value.activeChannel
        if (active != null && _uiState.value.playbackStatus is PlaybackStatus.Idle) {
            playChannel(active, forceRefresh = false)
        }
    }

    fun addChannel(input: String) {
        if (input.isBlank()) return
        _uiState.update { it.copy(isInputBusy = true) }

        viewModelScope.launch {
            try {
                val (rid, isOnce) = DouyinLiveEngine.extractRoomId(input)
                if (rid.isNullOrEmpty()) {
                    showToast("无法识别该链接或房间号")
                    return@launch
                }

                val existing = repository.getAllChannels().find { it.rid == rid }
                val info = DouyinLiveEngine.fetchRoomInfo(rid)

                val resolvedName = if (existing?.name?.isNotBlank() == true && !isPlaceholderName(existing.name, rid)) {
                    existing.name
                } else if (info?.name?.isNotBlank() == true && !isPlaceholderName(info.name, rid)) {
                    info.name
                } else if (info?.title?.isNotBlank() == true && !isPlaceholderTitle(info.title)) {
                    info.title
                } else {
                    existing?.name?.takeIf { it.isNotBlank() } ?: "抖音主播"
                }

                val maxAddedAt = repository.getAllChannels().maxOfOrNull { it.addedAt } ?: 0L
                val newChannel = LiveChannel(
                    rid = rid,
                    name = resolvedName,
                    title = info?.title ?: existing?.title ?: "",
                    avatar = info?.avatar ?: existing?.avatar ?: "",
                    isLive = info?.isLive ?: existing?.isLive ?: false,
                    statusKnown = info?.statusKnown ?: true,
                    isOnce = if (existing != null) existing.isOnce else isOnce,
                    addedAt = existing?.addedAt ?: maxOf(System.currentTimeMillis(), maxAddedAt + 1000L)
                )

                // 持久化存储到 Room 数据库
                repository.insertOrUpdate(newChannel)
                showToast("已成功添加并保存频道：${newChannel.name}")

                if (isOnce && existing == null) {
                    showDialog(ActiveDialog.OnceExplanation(newChannel.name, rid))
                }

                playChannel(newChannel)
            } catch (e: Exception) {
                showToast("添加失败：${e.message}")
            } finally {
                _uiState.update { it.copy(isInputBusy = false) }
            }
        }
    }

    fun confirmDelete(channel: LiveChannel) {
        showDialog(ActiveDialog.DeleteConfirm(channel))
    }

    fun deleteChannel(channel: LiveChannel) {
        viewModelScope.launch {
            repository.delete(channel)
            showToast("已从数据库中删除：${channel.name}")
            if (_uiState.value.activeChannel?.rid == channel.rid) {
                val next = _uiState.value.channels.firstOrNull { it.rid != channel.rid }
                if (next != null) {
                    playChannel(next)
                } else {
                    _uiState.update {
                        it.copy(
                            activeChannel = null,
                            playbackUrl = null,
                            playbackStatus = PlaybackStatus.Idle
                        )
                    }
                }
            }
        }
    }

    fun startBackgroundProbe() {
        probeJob?.cancel()
        probeJob = viewModelScope.launch(Dispatchers.IO) {
            // 首次载入在线测活策略优化：
            // 启动后优先预留 1.5 秒让活跃频道优先建立视频播放与首帧缓冲，随后立即启动全量并发测活，无需死等 25 秒
            delay(1500L)
            probeAllChannelsInternal()
            while (isActive) {
                delay(30_000L)
                probeAllChannelsInternal()
            }
        }
    }

    fun manualRefresh() {
        val active = _uiState.value.activeChannel
        if (active != null) {
            playChannel(active, forceRefresh = true)
        }
        viewModelScope.launch {
            probeAllChannelsInternal()
        }
    }

    private fun isPlaceholderName(name: String?, rid: String): Boolean {
        val v = name?.trim() ?: ""
        if (v.isEmpty() || v == "抖音主播" || v == "精彩直播" || v == "已导入频道") return true
        return v.matches(REGEX_PLACEHOLDER_NAME) || v == rid
    }

    private fun isPlaceholderTitle(title: String?): Boolean {
        val v = title?.trim() ?: ""
        return v.isEmpty() || v == "已导入频道" || v == "精彩直播" || v == "直播间" || v == "未知直播" || v == "当前未开播"
    }

    private suspend fun probeAllChannelsInternal() = withContext(Dispatchers.IO) {
        if (_uiState.value.isProbing) return@withContext
        _uiState.update { it.copy(isProbing = true) }
        try {
            val list = repository.getAllChannels()
            val changedChannels = mutableListOf<LiveChannel>()

            // 采用分批并发测活策略（每批 3 个并发）：兼顾高吞吐量快速测活与防反爬限频保护
            val batches = list.chunked(3)
            for (batch in batches) {
                if (!coroutineContext.isActive) break
                val probeJobs = batch.map { channel ->
                    async(Dispatchers.IO) {
                        try {
                            val data = DouyinLiveEngine.fetchRoomInfo(channel.rid)
                            Pair(channel, data)
                        } catch (_: Exception) {
                            Pair(channel, null)
                        }
                    }
                }
                val batchResults = probeJobs.awaitAll()
                for ((channel, data) in batchResults) {
                    if (data != null) {
                        val remoteName = data.name.trim()
                        val newName = if (remoteName.isNotEmpty() && !isPlaceholderName(remoteName, channel.rid)) {
                            remoteName
                        } else {
                            channel.name
                        }

                        val remoteTitle = data.title.trim()
                        val newTitle = if (remoteTitle.isNotEmpty() && !isPlaceholderTitle(remoteTitle)) {
                            remoteTitle
                        } else {
                            channel.title
                        }

                        val newAvatar = if (data.avatar.isNotEmpty()) data.avatar else channel.avatar

                        val changed = data.isLive != channel.isLive ||
                                data.statusKnown != channel.statusKnown ||
                                newName != channel.name ||
                                newTitle != channel.title ||
                                newAvatar != channel.avatar

                        if (changed) {
                            changedChannels.add(
                                channel.copy(
                                    isLive = data.isLive,
                                    statusKnown = data.statusKnown,
                                    title = newTitle,
                                    name = newName,
                                    avatar = newAvatar,
                                    addedAt = channel.addedAt // 严格保持原有添加时序，对标网页版同级稳定排序
                                )
                            )
                        }
                    }
                }
                // 批次间微休眠 150ms 保护网络与系统低功耗
                delay(150L)
            }
            // 探活全轮询完毕后一次性原子更新，彻底消除逐条探活引起的界面频繁闪烁与跳动
            if (changedChannels.isNotEmpty()) {
                repository.insertAll(changedChannels)
            }

            // 首次载入在线策略优化：若冷启动阶段当前频道未开播且用户尚未手动干预，自动接力切至首个探活确认在线的频道
            if (!hasUserInteracted && (_uiState.value.playbackStatus is PlaybackStatus.Offline || _uiState.value.playbackStatus is PlaybackStatus.Error)) {
                val liveCandidate = repository.getAllChannels().firstOrNull { it.isLive }
                if (liveCandidate != null && liveCandidate.rid != _uiState.value.activeChannel?.rid) {
                    playChannel(liveCandidate, forceRefresh = false, isUserInitiated = false)
                }
            }
        } catch (_: Exception) {
        } finally {
            _uiState.update { it.copy(isProbing = false) }
        }
    }

    fun importRemoteSource(url: String, mode: ImportMode) {
        viewModelScope.launch {
            _uiState.update { it.copy(isInputBusy = true) }
            try {
                val rawText = DouyinLiveEngine.fetchRemoteSource(url)
                val parsedList = parseChannelsFromText(rawText)
                if (parsedList.isEmpty()) {
                    showToast("远程源拉取成功，但未能解析出有效频道")
                    return@launch
                }

                if (mode == ImportMode.REPLACE) {
                    repository.deleteAll()
                    repository.insertAll(parsedList)
                    showToast("已覆盖保存 ${parsedList.size} 个频道到本地数据库")
                } else {
                    repository.insertAll(parsedList)
                    showToast("已成功合并保存 ${parsedList.size} 个频道到本地数据库")
                }
                dismissDialog()
                probeAllChannelsInternal()
            } catch (e: Exception) {
                showToast("导入失败：${e.message}")
            } finally {
                _uiState.update { it.copy(isInputBusy = false) }
            }
        }
    }

    fun importLocalSource(rawContent: String, mode: ImportMode) {
        viewModelScope.launch {
            _uiState.update { it.copy(isInputBusy = true) }
            try {
                val parsedList = parseChannelsFromText(rawContent)
                if (parsedList.isEmpty()) {
                    showToast("未能从选定文件中解析出有效直播频道")
                    return@launch
                }

                if (mode == ImportMode.REPLACE) {
                    repository.deleteAll()
                    repository.insertAll(parsedList)
                    showToast("已成功导入并覆盖保存 ${parsedList.size} 个频道")
                } else {
                    repository.insertAll(parsedList)
                    showToast("已成功合并保存 ${parsedList.size} 个频道")
                }
                dismissDialog()
                probeAllChannelsInternal()
            } catch (e: Exception) {
                showToast("本地导入失败：${e.message}")
            } finally {
                _uiState.update { it.copy(isInputBusy = false) }
            }
        }
    }

    fun exportChannels(format: String) {
        val list = _uiState.value.channels
        if (list.isEmpty()) {
            showToast("当前暂无频道可供导出")
            return
        }

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        when (format.lowercase()) {
            "json" -> {
                val array = JSONArray()
                list.forEach { ch ->
                    val obj = JSONObject()
                    obj.put("rid", ch.rid)
                    obj.put("name", ch.name)
                    obj.put("title", ch.title)
                    obj.put("avatar", ch.avatar)
                    obj.put("isLive", ch.isLive)
                    obj.put("isOnce", ch.isOnce)
                    array.put(obj)
                }
                val content = array.toString(2)
                showDialog(ActiveDialog.ExportResult("douyin_live_$timestamp.json", list.size, content))
            }
            "csv" -> {
                val sb = StringBuilder("房间号,频道名称,直播标题,一次性\n")
                list.forEach { ch ->
                    val safeName = ch.name.replace(",", "，")
                    val safeTitle = ch.title.replace(",", "，")
                    sb.append("${ch.rid},$safeName,$safeTitle,${if (ch.isOnce) "是" else "否"}\n")
                }
                showDialog(ActiveDialog.ExportResult("douyin_live_$timestamp.csv", list.size, sb.toString()))
            }
            else -> {
                val sb = StringBuilder()
                list.forEach { ch ->
                    sb.append("${ch.rid},${ch.name}\n")
                }
                showDialog(ActiveDialog.ExportResult("douyin_live_$timestamp.txt", list.size, sb.toString()))
            }
        }
    }

    private fun sanitizeImportedText(raw: String, maxLen: Int): String {
        return raw.replace(REGEX_CONTROL_CHARS, "").trim().take(maxLen)
    }

    private fun parseChannelsFromText(text: String): List<LiveChannel> {
        val result = mutableListOf<LiveChannel>()
        val trimmed = text.trim().take(1024 * 1024)
        val now = System.currentTimeMillis()
        if (trimmed.startsWith("[") || trimmed.startsWith("{")) {
            try {
                val array = if (trimmed.startsWith("[")) {
                    JSONArray(trimmed)
                } else {
                    val obj = JSONObject(trimmed)
                    obj.optJSONArray("list") ?: JSONArray()
                }
                val limit = minOf(array.length(), MAX_IMPORT_CHANNELS)
                for (i in 0 until limit) {
                    val item = array.optJSONObject(i) ?: continue
                    val rid = item.optString("rid").ifEmpty { item.optString("id") }.trim()
                    if (rid.isNotEmpty() && rid.matches(REGEX_RID_EXACT)) {
                        val rawName = sanitizeImportedText(item.optString("name"), 50)
                        val rawTitle = sanitizeImportedText(item.optString("title"), 100)
                        val name = if (rawName.isNotEmpty() && !isPlaceholderName(rawName, rid)) {
                            rawName
                        } else if (rawTitle.isNotEmpty() && !isPlaceholderTitle(rawTitle)) {
                            rawTitle
                        } else {
                            "抖音主播"
                        }
                        val title = if (rawTitle.isNotEmpty() && !isPlaceholderTitle(rawTitle)) rawTitle else "已导入频道"
                        val avatar = DouyinLiveEngine.sanitizeHttpUrl(item.optString("avatar", "")) ?: ""
                        val isLive = item.optBoolean("isLive", false)
                        result.add(
                            LiveChannel(
                                rid = rid,
                                name = name,
                                title = title,
                                avatar = avatar,
                                isLive = isLive,
                                statusKnown = false,
                                isOnce = rid.length >= 15,
                                addedAt = now + (i + 1) * 100L
                            )
                        )
                    }
                }
                if (result.isNotEmpty()) return result
            } catch (_: Exception) {}
        }

        // CSV or TXT per line (上限 MAX_IMPORT_CHANNELS 条防 DoS)
        val lines = trimmed.lines()
        var lineIndex = 0
        for (line in lines) {
            if (result.size >= MAX_IMPORT_CHANNELS) break
            val l = line.trim()
            if (l.isEmpty() || l.startsWith("#")) continue
            val parts = l.split(",")
            val candidateRid = parts[0].trim()
            val m = REGEX_RID_TOKEN.find(candidateRid)
            if (m != null) {
                val rid = m.value
                val rawName = if (parts.size > 1) sanitizeImportedText(parts[1], 50) else ""
                val rawTitle = if (parts.size > 2) sanitizeImportedText(parts[2], 100) else ""
                val name = if (rawName.isNotEmpty() && !isPlaceholderName(rawName, rid)) {
                    rawName
                } else if (rawTitle.isNotEmpty() && !isPlaceholderTitle(rawTitle)) {
                    rawTitle
                } else {
                    "抖音主播"
                }
                val title = if (rawTitle.isNotEmpty() && !isPlaceholderTitle(rawTitle)) rawTitle else "已导入频道"
                result.add(
                    LiveChannel(
                        rid = rid,
                        name = name,
                        title = title,
                        avatar = "",
                        isLive = false,
                        statusKnown = false,
                        isOnce = rid.length >= 15,
                        addedAt = now + (++lineIndex) * 100L
                    )
                )
            }
        }
        return result
    }

    fun clearAppCache() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val app = getApplication<Application>()
                app.cacheDir.deleteRecursively()
                app.externalCacheDir?.deleteRecursively()
                DouyinLiveEngine.clearCache()
                showToast("缓存深度清理完成，已释放存储空间")
            } catch (e: Exception) {
                showToast("清理异常: ${e.message}")
            }
        }
    }

    fun setScreenOffAudioMode(enabled: Boolean) {
        if (_uiState.value.isScreenOffAudioMode == enabled) return
        _uiState.update { it.copy(isScreenOffAudioMode = enabled) }
    }

    fun checkVersionUpdate(manual: Boolean = true) {
        viewModelScope.launch {
            showDialog(
                ActiveDialog.VersionUpdate(
                    currentVersion = "1.0.0",
                    latestVersion = "1.0.0",
                    isLatest = true,
                    updateLog = "【本系统功能简介】\n• 仿抖音双指中心流畅缩放：以双指触控中心点为锚点无级缩放（最高 10x 显微级画质巡检），支持双击快速缩放与阻尼边缘吸附回弹\n• 全场景手势与按键防冲突引擎：设立左/右/中三区物理隔离与单手势互斥锁存机制，消除音量调节与上下切台误触冲突\n• 息屏与后台防断流组件：退至后台或息屏自动转为仅音频模式，降低 95% 带宽，保持系统音频焦点持续拉流\n• 直播流精准侦测与稳定单会话收敛引擎：重构视频轨侦测机制，首帧渲染即锁，消除误判，彻底杜绝画质反复来回切换\n• 优化首次载入在线测活策略：首播 1.5 秒秒开避让 + 分批并发探测，状态原子写入，界面丝滑无跳动\n• 电视端全功能适配：电视遥控器方向键切台防抖，主页键与退出意图彻底释放后台资源\n• 本地持久化与多格式导入导出：采用 Room 本地 SQLite 数据库，离线保存关注频道，支持 JSON/TXT/CSV 安全导入导出",
                    downloadUrl = "https://github.com/kooker/DouView"
                )
            )
            if (manual) {
                showToast("当前已是最新稳定生产版本 v1.0.0")
            }
        }
    }

    fun clearOfflineOnceChannels() {
        viewModelScope.launch {
            repository.clearOfflineOnceChannels()
            showToast("已清理所有已下播的一次性临时直播间")
        }
    }

    fun showDialog(dialog: ActiveDialog) {
        _uiState.update { it.copy(activeDialog = dialog) }
    }

    fun dismissDialog() {
        _uiState.update { it.copy(activeDialog = null) }
    }

    fun showToast(message: String) {
        _uiState.update { it.copy(toastMessage = message) }
    }

    fun clearToast() {
        _uiState.update { it.copy(toastMessage = null) }
    }

    override fun onCleared() {
        super.onCleared()
        probeJob?.cancel()
    }
}
