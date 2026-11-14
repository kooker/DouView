package com.example.ui.components

import android.graphics.Bitmap
import android.os.Build
import android.view.LayoutInflater
import android.view.TextureView
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.annotation.VisibleForTesting
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.example.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Surface
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.engine.DouyinLiveEngine
import com.example.ui.PlaybackStatus
import com.example.ui.theme.DouyinRed
import com.example.util.DeviceEnvironment

import androidx.media3.exoplayer.LoadControl

/**
 * 定制直播流控策略：
 * - 保持无参 create() 向下兼容
 * - TV / 低内存盒子采用轻量紧凑缓冲（backBuffer=0，上限20s），降低内存抖动与 GC 停顿并加速起播切台
 */
@UnstableApi
object WorkbenchLiveLoadControlFactory {
    fun create(): LoadControl = create(isTv = false, isLowRam = false)

    fun create(isTv: Boolean, isLowRam: Boolean): LoadControl {
        val compactMode = isTv || isLowRam
        return DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                if (compactMode) 8000 else 15000,   // minBufferMs
                if (compactMode) 20000 else 35000,  // maxBufferMs
                if (compactMode) 600 else 700,      // bufferForPlaybackMs: 秒开起播
                if (compactMode) 2500 else 3000     // bufferForPlaybackAfterRebufferMs
            )
            .setBackBuffer(if (compactMode) 0 else 5000, false)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
    }
}

/**
 * 军工级直播动态背景采样与柔光引擎（跨 Android 7~15 全平台一致渲染）：
 * 1. 先裁切掉硬件解码器最外圈 8% 的 YUV Stride 绿边/黑边像素（彻底根治 RK3528/Mali/Amlogic 盒子绿边条纹）。
 * 2. 在 64px 级小图上执行纯整型两遍可分离盒式模糊 + 氛围暗角增艳，不依赖 Android 12+ RenderEffect，低版本电视盒子同样如丝般柔和。
 * 3. 输出全新不可变小尺寸 ImageBitmap (~24KB)，彻底杜绝 mutable Bitmap 复用擦除与 Compose 渲染线程竞争撕裂或 recycle 崩溃。
 */
internal object AmbientFrameSampler {

    fun buildAmbientImageBitmap(scratch: Bitmap): ImageBitmap? {
        val w = scratch.width
        val h = scratch.height
        if (w < 8 || h < 8 || scratch.isRecycled) return null
        val srcPixels = IntArray(w * h)
        scratch.getPixels(srcPixels, 0, w, 0, 0, w, h)
        val (outPixels, outW, outH) = cropAndBlurPixels(srcPixels, w, h)
        if (outW <= 0 || outH <= 0) return null
        return Bitmap.createBitmap(outPixels, outW, outH, Bitmap.Config.ARGB_8888).asImageBitmap()
    }

    @VisibleForTesting
    internal fun cropAndBlurPixels(
        srcPixels: IntArray,
        srcW: Int,
        srcH: Int,
        cropFraction: Float = 0.06f,
        radius: Int = 2
    ): Triple<IntArray, Int, Int> {
        if (srcW < 6 || srcH < 6 || srcPixels.size < srcW * srcH) {
            return Triple(IntArray(0), 0, 0)
        }
        val marginX = (srcW * cropFraction).toInt().coerceIn(1, (srcW - 4) / 2)
        val marginY = (srcH * cropFraction).toInt().coerceIn(1, (srcH - 4) / 2)
        val cropW = (srcW - marginX * 2).coerceAtLeast(4)
        val cropH = (srcH - marginY * 2).coerceAtLeast(4)

        val cropped = IntArray(cropW * cropH)
        for (y in 0 until cropH) {
            val srcRowOffset = (y + marginY) * srcW + marginX
            val dstRowOffset = y * cropW
            System.arraycopy(srcPixels, srcRowOffset, cropped, dstRowOffset, cropW)
        }

        val temp = IntArray(cropW * cropH)
        val blurred = IntArray(cropW * cropH)
        val r = radius.coerceIn(1, 8)
        val kernelSize = r * 2 + 1

        // 水平方向均值模糊
        for (y in 0 until cropH) {
            val rowOff = y * cropW
            for (x in 0 until cropW) {
                var sumR = 0
                var sumG = 0
                var sumB = 0
                for (dx in -r..r) {
                    val sx = (x + dx).coerceIn(0, cropW - 1)
                    val px = cropped[rowOff + sx]
                    sumR += (px ushr 16) and 0xFF
                    sumG += (px ushr 8) and 0xFF
                    sumB += px and 0xFF
                }
                temp[rowOff + x] = (0xFF shl 24) or
                    ((sumR / kernelSize) shl 16) or
                    ((sumG / kernelSize) shl 8) or
                    (sumB / kernelSize)
            }
        }

        // 垂直方向均值模糊 + 氛围暗角调色（柔和压暗 12% 突出前景主体直播画面）
        for (x in 0 until cropW) {
            for (y in 0 until cropH) {
                var sumR = 0
                var sumG = 0
                var sumB = 0
                for (dy in -r..r) {
                    val sy = (y + dy).coerceIn(0, cropH - 1)
                    val px = temp[sy * cropW + x]
                    sumR += (px ushr 16) and 0xFF
                    sumG += (px ushr 8) and 0xFF
                    sumB += px and 0xFF
                }
                val avgR = ((sumR / kernelSize) * 88 / 100).coerceIn(0, 255)
                val avgG = ((sumG / kernelSize) * 88 / 100).coerceIn(0, 255)
                val avgB = ((sumB / kernelSize) * 88 / 100).coerceIn(0, 255)
                blurred[y * cropW + x] = (0xFF shl 24) or (avgR shl 16) or (avgG shl 8) or avgB
            }
        }

        return Triple(blurred, cropW, cropH)
    }
}

@OptIn(UnstableApi::class)
@Composable
fun LivePlayerView(
    playbackUrl: String?,
    playbackStatus: PlaybackStatus,
    posterUrl: String?,
    isMuted: Boolean,
    onRetry: () -> Unit,
    onPlaybackStateChanged: (PlaybackStatus) -> Unit,
    onPlayerErrorOccurred: (isDecoderError: Boolean, message: String) -> Unit = { _, _ -> },
    onBlackScreenDetected: () -> Unit = {},
    onVideoAspectRatioChanged: (isPortrait: Boolean) -> Unit = {},
    isPiPMode: Boolean = false,
    volume: Float = 1.0f,
    isScreenOffAudioMode: Boolean = false,
    modifier: Modifier = Modifier,
    isTv: Boolean = false
) {
    val context = LocalContext.current
    val isLowRamDevice = remember(context) { DeviceEnvironment.isLowRamDevice(context) }
    var autoRetryCount by remember { mutableIntStateOf(0) }
    var isPortraitStream by remember { mutableStateOf(false) }
    var ambientBackgroundImage by remember { mutableStateOf<ImageBitmap?>(null) }
    var hasRenderedFirstFrame by remember { mutableStateOf(false) }
    var hasVideoSize by remember { mutableStateOf(false) }
    var isExoActuallyPlaying by remember { mutableStateOf(false) }
    var isAudioOnly by remember { mutableStateOf(false) }
    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }
    val isPlaying = playbackStatus is PlaybackStatus.Playing

    // 使用 rememberUpdatedState 防止 ExoPlayer / Lifecycle 长寿命监听器捕获过时闭包
    val currentIsScreenOffAudio by rememberUpdatedState(isScreenOffAudioMode)
    val currentOnPlaybackStateChanged by rememberUpdatedState(onPlaybackStateChanged)
    val currentOnPlayerError by rememberUpdatedState(onPlayerErrorOccurred)
    val currentOnBlackScreen by rememberUpdatedState(onBlackScreenDetected)
    val currentOnAspectRatio by rememberUpdatedState(onVideoAspectRatioChanged)

    // 创建配置解码器自动降级、低延时快速缓冲与严格 HTTPS 传输安全策略的 ExoPlayer 实例
    val exoPlayer = remember(isTv, isLowRamDevice) {
        val renderersFactory = DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true) // 开启解码器自动降级（硬件不支持时自动回落软解）
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            .setAllowedVideoJoiningTimeMs(5000)

        val loadControl = WorkbenchLiveLoadControlFactory.create(isTv = isTv, isLowRam = isLowRamDevice)

        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
            .setConnectTimeoutMs(8000)
            .setReadTimeoutMs(20000)
            .setAllowCrossProtocolRedirects(false) // 禁止 HTTPS -> HTTP 跨协议降级重定向
            .setDefaultRequestProperties(
                mapOf(
                    "Referer" to "https://live.douyin.com/"
                )
            )

        val mediaSourceFactory = DefaultMediaSourceFactory(context)
            .setDataSourceFactory(httpDataSourceFactory)

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .setUsage(C.USAGE_MEDIA)
            .build()

        ExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .setAudioAttributes(audioAttributes, true) // 关键：自动管理并保持系统音频焦点，杜绝后台切音被打断
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build().apply {
                repeatMode = Player.REPEAT_MODE_OFF
                playWhenReady = true
            }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    var isLifecycleBackground by remember { mutableStateOf(false) }

    // 播放器生命周期感知组件：当检测到应用退至后台或屏幕关闭时，强制切换 ExoPlayer 播放模式为仅音频流，并保持音频焦点
    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    isLifecycleBackground = true
                    try {
                        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                            .buildUpon()
                            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true)
                            .build()
                    } catch (_: Exception) {}
                }
                Lifecycle.Event.ON_START -> {
                    isLifecycleBackground = false
                    try {
                        if (!currentIsScreenOffAudio) {
                            exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                                .buildUpon()
                                .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, false)
                                .build()
                        }
                    } catch (_: Exception) {}
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // 监听播放器自身生命周期与状态事件
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                if (events.contains(Player.EVENT_RENDERED_FIRST_FRAME) ||
                    events.contains(Player.EVENT_VIDEO_SIZE_CHANGED) ||
                    events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED)
                ) {
                    if (player.videoSize.width > 0 && player.videoSize.height > 0) {
                        hasVideoSize = true
                        hasRenderedFirstFrame = true
                        isAudioOnly = false // 侦测到视频尺寸，坚决消除语音直播误判
                    }
                }
            }

            override fun onTracksChanged(tracks: Tracks) {
                var hasVideoTrackInStream = false
                var hasAudioTrackInStream = false
                for (group in tracks.groups) {
                    if (group.type == C.TRACK_TYPE_VIDEO) hasVideoTrackInStream = true
                    if (group.type == C.TRACK_TYPE_AUDIO) hasAudioTrackInStream = true
                }
                // 关键军工级防误判：
                // 仅当媒体流中「完全不存在任何视频轨道」且「存在音频轨道」且「非人工息屏/后台纯音频模式」时，
                // 才是真正的纯音频电台直播间！若流中本就存在视频轨，绝不误标记为语音电台！
                if (!hasVideoTrackInStream && hasAudioTrackInStream && !currentIsScreenOffAudio && !isLifecycleBackground) {
                    isAudioOnly = true
                    hasRenderedFirstFrame = true
                } else if (hasVideoTrackInStream) {
                    isAudioOnly = false
                }
            }

            override fun onRenderedFirstFrame() {
                hasRenderedFirstFrame = true
                isAudioOnly = false
            }

            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_BUFFERING -> {
                        currentOnPlaybackStateChanged(PlaybackStatus.Buffering("正在加速缓冲流媒体..."))
                    }
                    Player.STATE_READY -> {
                        autoRetryCount = 0
                        currentOnPlaybackStateChanged(PlaybackStatus.Playing)
                        if (exoPlayer.videoSize.width > 0 && exoPlayer.videoSize.height > 0) {
                            hasVideoSize = true
                            hasRenderedFirstFrame = true
                        }
                    }
                    Player.STATE_ENDED -> {
                        isExoActuallyPlaying = false
                        currentOnPlaybackStateChanged(PlaybackStatus.Offline("直播信号已结束"))
                    }
                    Player.STATE_IDLE -> {
                        isExoActuallyPlaying = false
                    }
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                isExoActuallyPlaying = isPlaying
                if (isPlaying) {
                    currentOnPlaybackStateChanged(PlaybackStatus.Playing)
                    if (exoPlayer.videoSize.width > 0 && exoPlayer.videoSize.height > 0) {
                        hasVideoSize = true
                        hasRenderedFirstFrame = true
                    }
                }
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    hasVideoSize = true
                    val effectiveWidth = videoSize.width * videoSize.pixelWidthHeightRatio
                    val isPortrait = videoSize.height > effectiveWidth
                    isPortraitStream = isPortrait
                    currentOnAspectRatio(isPortrait)
                    hasRenderedFirstFrame = true // 视频轨道尺寸已成功解析，解码正常
                    isAudioOnly = false
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                // 1. 直播时间窗滑出 (BehindLiveWindowException) 专线自愈机制：重新对齐直播头继续拉取
                if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                    exoPlayer.seekToDefaultPosition()
                    exoPlayer.prepare()
                    exoPlayer.play()
                    return
                }

                // 2. 扩大解码器 / MediaCodec 异常识别范围（涵盖低端盒子的硬解能力超限、10bit/HEVC 回落异常等）
                val causeChainText = generateSequence<Throwable>(error) { it.cause }
                    .take(5)
                    .joinToString(" | ") { "${it.javaClass.simpleName}: ${it.message ?: ""}" }
                    .lowercase()
                val isDecoderError = error.errorCode in 4000..4999 ||
                        error.errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ||
                        error.errorCode == PlaybackException.ERROR_CODE_DECODING_FAILED ||
                        error.errorCode == PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED ||
                        error.errorCode == PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES ||
                        error.errorCode == PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED ||
                        error.errorCode == PlaybackException.ERROR_CODE_VIDEO_FRAME_PROCESSOR_INIT_FAILED ||
                        error.errorCode == PlaybackException.ERROR_CODE_VIDEO_FRAME_PROCESSING_FAILED ||
                        error.errorCodeName.contains("DECODER", ignoreCase = true) ||
                        error.errorCodeName.contains("DECODING", ignoreCase = true) ||
                        listOf(
                            "decoder", "mediacodec", "codec", "decode",
                            "configure failed", "exceeds_capabilities", "format_unsupported",
                            "hevc", "h265", "video/hevc", "hdr", "color_format",
                            "omx.", "c2.", "dequeue"
                        ).any { causeChainText.contains(it) }

                // 3. 识别到解码故障时，坚决不再在同一清晰度上死磕重试，直接上抛触发自动降画质；
                //    仅非解码类的网络抖动执行同档短暂自愈，息屏/后台纯音频模式下持续守护重连
                val isInBackgroundAudio = currentIsScreenOffAudio || isLifecycleBackground
                if (!isDecoderError && (autoRetryCount < 2 || isInBackgroundAudio)) {
                    if (!isInBackgroundAudio) {
                        autoRetryCount++
                        currentOnPlaybackStateChanged(PlaybackStatus.Buffering("信号波动，自愈重试 ($autoRetryCount/2)..."))
                    } else {
                        // 息屏/后台模式持续守护保持拉流，杜绝因基站切换或短暂掉包发生永久断流
                        currentOnPlaybackStateChanged(PlaybackStatus.Buffering("后台流媒体自愈保持中..."))
                    }
                    exoPlayer.seekToDefaultPosition()
                    exoPlayer.prepare()
                    exoPlayer.play()
                    return
                }

                currentOnPlayerError(isDecoderError, error.message ?: "解码异常")
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    // 音量与静音综合控制
    LaunchedEffect(isMuted, volume) {
        exoPlayer.volume = if (isMuted) 0f else volume.coerceIn(0f, 1f)
    }

    // 息屏与退至后台自适应低功耗纯音频传输模式：关闭视频轨，仅保留音频拉取与解码，降低 95% 码率、优化网络传输性能并消除断流
    LaunchedEffect(isScreenOffAudioMode, isLifecycleBackground) {
        val shouldDisableVideo = isScreenOffAudioMode || isLifecycleBackground
        try {
            exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, shouldDisableVideo)
                .build()
        } catch (_: Exception) {}
    }

    // 低开销出帧兜底守护（主路径已由 Player.Listener 零延迟响应）
    LaunchedEffect(playbackUrl, isPlaying) {
        if (playbackUrl.isNullOrEmpty()) return@LaunchedEffect
        while (isActive && !hasRenderedFirstFrame) {
            val isPlaybackActive = exoPlayer.isPlaying || exoPlayer.playbackState == Player.STATE_READY
            val hasVideoOutput = (exoPlayer.videoSize.width > 0 && exoPlayer.videoSize.height > 0) || isAudioOnly
            if (isPlaybackActive && hasVideoOutput) {
                hasVideoSize = exoPlayer.videoSize.width > 0
                isExoActuallyPlaying = exoPlayer.isPlaying
                hasRenderedFirstFrame = true
                break
            }
            delay(250L)
        }
    }

    // 极端断流自愈守护进程：若长时间卡在缓冲或连接状态，主动微调向直播最新头追赶
    LaunchedEffect(playbackUrl, playbackStatus) {
        if (!playbackUrl.isNullOrEmpty() && (playbackStatus is PlaybackStatus.Buffering || playbackStatus is PlaybackStatus.Connecting)) {
            delay(7500L)
            if (playbackStatus is PlaybackStatus.Buffering || playbackStatus is PlaybackStatus.Connecting) {
                exoPlayer.seekToDefaultPosition()
                exoPlayer.prepare()
                exoPlayer.play()
            }
        }
    }

    // 流地址变化时载入（仅放行校验通过的 HTTPS 安全流地址）
    LaunchedEffect(playbackUrl) {
        ambientBackgroundImage = null
        hasRenderedFirstFrame = false
        hasVideoSize = false
        isExoActuallyPlaying = false
        isAudioOnly = false
        autoRetryCount = 0
        val safeStreamUrl = DouyinLiveEngine.sanitizeHttpUrl(playbackUrl)
        if (!safeStreamUrl.isNullOrEmpty()) {
            val mediaItem = MediaItem.fromUri(safeStreamUrl)
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            exoPlayer.play()
        } else {
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
        }
    }

    // 监听播放状态：若处于 Idle 或停止状态，立即停止 ExoPlayer 释放编解码器与音频焦点
    LaunchedEffect(playbackStatus) {
        if (playbackStatus is PlaybackStatus.Idle) {
            exoPlayer.stop()
        }
    }

    // 黑屏但有声音 (Black Screen with Audio) 快速自愈看门狗：
    // 1. 播放器已 READY / 在播，但连续 4 秒（8 个 500ms 周期）仍无视频尺寸 -> 立即触发自动降流
    // 2. 长时间卡在缓冲约 8 秒（16 个 500ms 周期）未出画面 -> 触发自动降流
    // 3. 息屏/后台纯音频模式下暂停计数，防止后台切流误伤
    LaunchedEffect(playbackUrl) {
        if (playbackUrl.isNullOrEmpty()) return@LaunchedEffect
        var totalElapsedHalfSec = 0
        var readyWithoutVideoHalfSec = 0

        while (isActive) {
            delay(500L)
            if (currentIsScreenOffAudio || isLifecycleBackground) {
                continue
            }

            // 严格校验 videoSize 宽高，杜绝“有声无画却因 READY/currentPosition>0 误判为已出帧”
            val hasVideoDecoded = exoPlayer.videoSize.width > 0 && exoPlayer.videoSize.height > 0
            val isPlaybackActive = exoPlayer.isPlaying || exoPlayer.playbackState == Player.STATE_READY
            if (hasVideoDecoded && isPlaybackActive) {
                hasRenderedFirstFrame = true
            }

            // 视频首帧渲染成功或确认为纯音频电台直播，看门狗功成身退
            if (hasRenderedFirstFrame || isAudioOnly) {
                break
            }

            totalElapsedHalfSec++
            if (isPlaybackActive) {
                readyWithoutVideoHalfSec++
                if (readyWithoutVideoHalfSec >= 8) {
                    // 已 READY / 在播连续约 4 秒仍无视频尺寸，立即自动降流
                    currentOnBlackScreen()
                    break
                }
                if (totalElapsedHalfSec >= 24) {
                    currentOnBlackScreen()
                    break
                }
            } else {
                readyWithoutVideoHalfSec = 0
                if (totalElapsedHalfSec >= 16) {
                    // 长时间卡在缓冲约 8 秒未出帧，触发自动降流
                    currentOnBlackScreen()
                    break
                }
            }
        }
    }

    // TV 端与移动端统一的实况动态氛围背景采样进程：
    // - 对竖屏流启用 224p 轻量纹理采样 + 6% 内缩抗绿边裁切 + 两遍柔光模糊，TV 盒子 (~8fps) 与手机 (~10fps) 均清晰可辨且丝滑运行
    // - 绝不向 Compose 层暴露可变/复用 Bitmap，消除 GPU Stride 绿边与位图回收竞争崩溃
    LaunchedEffect(isPlaying, isPortraitStream, hasRenderedFirstFrame, isScreenOffAudioMode, isLifecycleBackground, isTv, isLowRamDevice) {
        if (isPlaying && isPortraitStream && hasRenderedFirstFrame && !isScreenOffAudioMode && !isLifecycleBackground) {
            var scratchW = 0
            var scratchH = 0
            var scratchBmp: Bitmap? = null
            val intervalMs = if (isTv || isLowRamDevice) 125L else 100L
            try {
                while (isActive) {
                    try {
                        val textureView = playerViewRef?.videoSurfaceView as? TextureView
                        if (textureView != null && textureView.isAvailable) {
                            val tvW = textureView.width
                            val tvH = textureView.height
                            if (tvW > 8 && tvH > 8) {
                                val targetH = 224
                                val targetW = ((targetH.toFloat() * tvW) / tvH).toInt().coerceIn(96, 360)
                                val bmp = if (scratchBmp != null && !scratchBmp!!.isRecycled && scratchW == targetW && scratchH == targetH) {
                                    scratchBmp!!
                                } else {
                                    scratchBmp?.let { if (!it.isRecycled) it.recycle() }
                                    scratchW = targetW
                                    scratchH = targetH
                                    Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888).also {
                                        scratchBmp = it
                                    }
                                }
                                bmp.eraseColor(android.graphics.Color.BLACK)
                                textureView.getBitmap(bmp)
                                val ambient = withContext(Dispatchers.Default) {
                                    AmbientFrameSampler.buildAmbientImageBitmap(bmp)
                                }
                                if (ambient != null && isActive) {
                                    ambientBackgroundImage = ambient
                                }
                            }
                        }
                    } catch (_: Exception) {}
                    delay(intervalMs)
                }
            } finally {
                ambientBackgroundImage = null
                scratchBmp?.let { if (!it.isRecycled) it.recycle() }
            }
        } else {
            ambientBackgroundImage = null
        }
    }

    val pulseTransition = rememberInfiniteTransition(label = "pulse_loader")
    val radarPulse by pulseTransition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "radar_pulse"
    )
    val telemetryAlpha by pulseTransition.animateFloat(
        initialValue = 0.65f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "telemetry_alpha"
    )

    Box(
        modifier = modifier
            .background(Color(0xFF09090C))
            .clip(RoundedCornerShape(if (isPiPMode) 0.dp else 12.dp))
    ) {
        // 1. 还没载入直播前（或切台连接缓冲中），以主播头像配合系统自适应暗纱作为兜底背景
        if (!posterUrl.isNullOrEmpty()) {
            AsyncImage(
                model = posterUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Modifier.blur(26.dp) else Modifier)
                    .alpha(if (ambientBackgroundImage != null) 0f else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 0.78f else 0.35f)
            )
        }

        // 2. 实况视频动态背景：竖屏流首帧渲染后无缝淡入抗绿边柔光氛围背景（API 24+ 与 TV 盒子全适配）
        AnimatedVisibility(
            visible = ambientBackgroundImage != null && hasRenderedFirstFrame,
            enter = fadeIn(tween(320)),
            exit = fadeOut(tween(280)),
            modifier = Modifier.fillMaxSize()
        ) {
            ambientBackgroundImage?.let { img ->
                Image(
                    bitmap = img,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    filterQuality = FilterQuality.High,
                    modifier = Modifier
                        .fillMaxSize()
                        .scale(1.12f)
                        .alpha(0.92f)
                )
            }
        }

        // 3. 沉浸式暗色渐变暗角遮罩（确保主体居中视频轮廓分明，避免背景过亮抢眼）
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0x18000000),
                            Color(0x88000000)
                        )
                    )
                )
        )

        // 4. 原生 Media3 PlayerView (居中 Fit 渲染竖屏主体，背景透明透出动态光影)
        AndroidView(
            factory = { ctx ->
                (LayoutInflater.from(ctx).inflate(R.layout.view_live_player, null) as PlayerView).apply {
                    player = exoPlayer
                    useController = false
                    isFocusable = false
                    isFocusableInTouchMode = false
                    descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                    setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    resizeMode = if (isPiPMode) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    playerViewRef = this
                }
            },
            update = { playerView ->
                if (playerViewRef !== playerView) {
                    playerViewRef = playerView
                }
                val targetResize = if (isPiPMode) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT
                if (playerView.resizeMode != targetResize) {
                    playerView.resizeMode = targetResize
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .testTag("video_player_surface")
        )

        // 纯音频 / 语音电台实况播放徽标（仅在非息屏/非后台模式且确实完全无视频轨时展示，彻底绝缘误判）
        val showAudioOnlyBadge = isAudioOnly && isPlaying && !isScreenOffAudioMode && !isLifecycleBackground && !hasVideoSize
        if (showAudioOnlyBadge) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.BottomStart
            ) {
                Surface(
                    color = Color(0xDD111218),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, Color(0xFF00F2FE).copy(alpha = 0.5f))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "🎙️ 语音电台实况播放中",
                            color = Color(0xFF00F2FE),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // 浮层遮罩：仅在未开播、明确错误、连接中或显式缓冲时呈现清晰优雅的加载状态（播放中且已出帧/已实际播放时不遮挡）
        val shouldShowLoadingOverlay = (playbackStatus !is PlaybackStatus.Playing || (!hasRenderedFirstFrame && !isExoActuallyPlaying)) && !isAudioOnly
        AnimatedVisibility(
            visible = shouldShowLoadingOverlay,
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(250)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        if (playbackStatus is PlaybackStatus.Playing && !hasRenderedFirstFrame)
                            Color(0x6609090C)
                        else if (playbackStatus is PlaybackStatus.Buffering)
                            Color(0x8809090C)
                        else
                            Color(0xCC09090C)
                    )
                    .padding(16.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (playbackStatus is PlaybackStatus.Playing && !hasRenderedFirstFrame) {
                        CircularProgressIndicator(
                            color = DouyinRed,
                            strokeWidth = 3.dp,
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("spinner_first_frame")
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "正在接入视频流...",
                            color = MaterialTheme.colorScheme.onBackground,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    } else when (playbackStatus) {
                        is PlaybackStatus.Connecting -> {
                            CircularProgressIndicator(
                                color = DouyinRed,
                                strokeWidth = 3.dp,
                                modifier = Modifier
                                    .size(38.dp)
                                    .testTag("spinner_connecting")
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = playbackStatus.message,
                                color = MaterialTheme.colorScheme.onBackground,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        is PlaybackStatus.Buffering -> {
                            CircularProgressIndicator(
                                color = DouyinRed,
                                strokeWidth = 3.dp,
                                modifier = Modifier
                                    .size(38.dp)
                                    .testTag("spinner_buffering")
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = playbackStatus.message,
                                color = MaterialTheme.colorScheme.onBackground,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        is PlaybackStatus.Offline -> {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0x26FE2C55))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "🔴 " + playbackStatus.message,
                                    color = Color(0xFFFE2C55),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "主播当前未开播，您可以切换下方其他频道观看",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.5.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                        is PlaybackStatus.Error -> {
                            Text(
                                text = playbackStatus.message,
                                color = Color(0xFFFF5252),
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "网络拥塞或解码器回落，建议切换协议 (HLS/FLV) 或重试",
                                color = Color(0xFF8E8E9E),
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            var isRetryFocused by remember { mutableStateOf(false) }
                            Button(
                                onClick = onRetry,
                                colors = ButtonDefaults.buttonColors(containerColor = DouyinRed),
                                border = if (isTv && isRetryFocused) BorderStroke(2.dp, Color(0xFF00D26A)) else null,
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier
                                    .onFocusChanged { isRetryFocused = it.isFocused }
                                    .testTag("btn_player_retry")
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.size(6.dp))
                                Text("立即重试", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        PlaybackStatus.Idle -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(Color(0x22FFFFFF))
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.Wifi, contentDescription = null, tint = Color(0xFF8E8E9E), modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "请在下方列表中选择直播频道",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 12.5.sp
                                )
                            }
                        }
                        PlaybackStatus.Playing -> {}
                    }
                }
            }
        }
    }
}
