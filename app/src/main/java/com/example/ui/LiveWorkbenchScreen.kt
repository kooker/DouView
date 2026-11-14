package com.example.ui

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.pm.ActivityInfo
import android.os.Build
import android.util.Rational
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import java.util.Locale
import kotlinx.coroutines.launch
import com.example.R
import com.example.ui.components.AppDialogsHost
import com.example.ui.components.LivePlayerView
import com.example.ui.components.MobileWorkbenchLayout
import com.example.ui.components.TvWorkbenchLayout
import com.example.ui.theme.DouyinRed
import com.example.ui.theme.StudioBg

@Composable
fun LiveWorkbenchScreen(
    viewModel: LiveWorkbenchViewModel,
    isInPictureInPictureMode: Boolean,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context as? Activity
    val focusManager = LocalFocusManager.current
    val snackbarHostState = remember { SnackbarHostState() }

    val isFullscreen = uiState.isFullscreen
    var isPortraitVideo by remember { mutableStateOf(false) }
    var inputText by remember { mutableStateOf("") }

    // 智能识别当前运行宿主是否为 Android TV / 电视盒子
    val isTv = remember(context) { com.example.util.DeviceEnvironment.isTvDevice(context) }
    val playerStageFocusRequester = remember { FocusRequester() }

    // TV 端初始化与弹窗/全屏退出后的安全焦点恢复（防止 FocusRequester 未挂载导致异常）
    LaunchedEffect(isTv, isFullscreen, isInPictureInPictureMode, uiState.activeDialog) {
        if (isTv && !isFullscreen && !isInPictureInPictureMode && uiState.activeDialog == null) {
            kotlinx.coroutines.delay(60L)
            try {
                playerStageFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    // 本地文件选择器（TXT / CSV / JSON 文件导入，限制最大 1MB 防止 OOM）
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            try {
                context.contentResolver.openInputStream(it)?.use { stream ->
                    val content = readBoundedUtf8Text(stream, maxBytes = 1024 * 1024)
                    viewModel.importLocalSource(content, ImportMode.MERGE)
                }
            } catch (e: Exception) {
                viewModel.showToast("读取文件失败: ${e.message}")
            }
        }
    }

    // 全屏返回处理
    BackHandler(enabled = isFullscreen) {
        viewModel.setFullscreen(false)
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }

    // 提示信息监听
    LaunchedEffect(uiState.toastMessage) {
        uiState.toastMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearToast()
        }
    }

    // 竖屏与横屏全屏自适应感知：电视设备绝对不强制旋转为竖屏，避免电视系统崩溃或取消全屏
    LaunchedEffect(isFullscreen, isPortraitVideo, isTv) {
        if (isFullscreen) {
            if (isTv) {
                // 电视大屏固定采用横屏全屏，手机竖屏直播居中满幅自适应
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            } else {
                // 移动端手机依据视频长宽比自适应竖屏全屏或横屏全屏
                activity?.requestedOrientation = if (isPortraitVideo) {
                    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                } else {
                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                }
            }
        }
    }

    // 全屏时隐藏手机状态栏，退出全屏时恢复可见
    DisposableEffect(isFullscreen) {
        val window = activity?.window
        if (window != null) {
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            if (isFullscreen) {
                insetsController.hide(WindowInsetsCompat.Type.statusBars())
                insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                insetsController.show(WindowInsetsCompat.Type.statusBars())
            }
        }
        onDispose {
            val window = activity?.window
            if (window != null) {
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                insetsController.show(WindowInsetsCompat.Type.statusBars())
            }
        }
    }

    // 画中画模式：仅全屏显示无缝播放画面
    if (isInPictureInPictureMode) {
        LivePlayerView(
            playbackUrl = uiState.playbackUrl,
            playbackStatus = uiState.playbackStatus,
            posterUrl = uiState.activeChannel?.avatar,
            isMuted = uiState.isMuted,
            volume = uiState.volume,
            onRetry = { viewModel.manualRefresh() },
            onPlaybackStateChanged = { viewModel.setPlaybackStatus(it) },
            onPlayerErrorOccurred = { isDec, msg -> viewModel.handlePlayerError(isDec, msg) },
            onBlackScreenDetected = { viewModel.autoRecoverBlackScreen() },
            onVideoAspectRatioChanged = { isPortraitVideo = it },
            isPiPMode = true,
            isScreenOffAudioMode = uiState.isScreenOffAudioMode,
            modifier = Modifier.fillMaxSize()
        )
        return
    }

    // 全屏播放模式：自适应横竖屏全屏、抖音式上下滑切台、双指自由极限放大(10x)与右侧滑音量控制
    if (isFullscreen) {
        val coroutineScope = rememberCoroutineScope()
        var zoomScale by remember { mutableFloatStateOf(1f) }
        var panOffset by remember { mutableStateOf(Offset.Zero) }
        var showControls by remember { mutableStateOf(true) }
        var showVolumeHud by remember { mutableStateOf(false) }
        var lastChannelSwitchTime by remember { mutableLongStateOf(0L) }
        var animationJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

        val resetZoomSmoothly: () -> Unit = {
            animationJob?.cancel()
            animationJob = coroutineScope.launch {
                val startScale = zoomScale
                val startPan = panOffset
                val anim = Animatable(0f)
                anim.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing)
                ) {
                    val fraction = value
                    zoomScale = lerp(startScale, 1f, fraction)
                    panOffset = Offset(
                        lerp(startPan.x, 0f, fraction),
                        lerp(startPan.y, 0f, fraction)
                    )
                }
            }
        }

        val animateZoomTo: (Float, Offset) -> Unit = { targetScale, targetPan ->
            animationJob?.cancel()
            animationJob = coroutineScope.launch {
                val startScale = zoomScale
                val startPan = panOffset
                val anim = Animatable(0f)
                anim.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing)
                ) {
                    val fraction = value
                    zoomScale = lerp(startScale, targetScale, fraction)
                    panOffset = Offset(
                        lerp(startPan.x, targetPan.x, fraction),
                        lerp(startPan.y, targetPan.y, fraction)
                    )
                }
            }
        }

        // 切台时自动重置缩放和位移，并在全屏下自动浮现 3.5 秒主播信息 HUD 方便电视用户识别
        LaunchedEffect(uiState.activeChannel?.rid) {
            animationJob?.cancel()
            zoomScale = 1f
            panOffset = Offset.Zero
            if (isFullscreen && uiState.activeChannel != null) {
                showControls = true
            }
        }

        // 3.5秒无触控自动淡出全屏控制面板
        LaunchedEffect(showControls) {
            if (showControls) {
                kotlinx.coroutines.delay(3500)
                showControls = false
            }
        }

        // 1.5秒自动淡出音量 HUD
        LaunchedEffect(showVolumeHud, uiState.volume) {
            if (showVolumeHud) {
                kotlinx.coroutines.delay(1500)
                showVolumeHud = false
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clipToBounds()
                .pointerInput(uiState.activeChannel?.rid) {
                    detectTapGestures(
                        onTap = {
                            showControls = !showControls
                        },
                        onDoubleTap = { tapOffset ->
                            if (zoomScale > 1.05f) {
                                resetZoomSmoothly()
                            } else {
                                val targetScale = 2.5f
                                val maxPanX = (size.width.toFloat() * (targetScale - 1f)) / 2f
                                val maxPanY = (size.height.toFloat() * (targetScale - 1f)) / 2f
                                val center = Offset(size.width / 2f, size.height / 2f)
                                // 仿抖音：以双击触点为中心向四周放大，带边界严密吸附
                                val targetPanX = ((center.x - tapOffset.x) * (targetScale - 1f)).coerceIn(-maxPanX, maxPanX)
                                val targetPanY = ((center.y - tapOffset.y) * (targetScale - 1f)).coerceIn(-maxPanY, maxPanY)
                                animateZoomTo(targetScale, Offset(targetPanX, targetPanY))
                            }
                        }
                    )
                }
                .pointerInput(uiState.activeChannel?.rid) {
                    awaitEachGesture {
                        val firstDown = awaitFirstDown(requireUnconsumed = false)
                        animationJob?.cancel()
                        val downX = firstDown.position.x
                        // 工业级三区物理隔离：
                        // 左侧 40% (downX < 0.40f)：严格切台感应区
                        // 右侧 40% (downX > 0.60f)：严格音量调节感应区
                        // 中间 20% (0.40f ~ 0.60f)：中立隔离缓冲区，杜绝中间滑动引起的误切台或误变音量
                        val isRightSideZone = downX > (size.width * 0.60f)
                        val isLeftSideZone = downX < (size.width * 0.40f)

                        var gestureMode = 0 // 0: 未定, 1: 锁定音量调节, 2: 锁定上下切台, 3: 锁定平移或忽略
                        var accumulatedDragY = 0f
                        var totalMovementX = 0f
                        var totalMovementY = 0f

                        do {
                            val event = awaitPointerEvent()
                            val count = event.changes.size

                            if (count >= 2) {
                                gestureMode = 3
                                // 仿抖音双指中心锚点平滑极限缩放与阻尼吸附（最高 10.0x 高清显微巡视）
                                val centroid = event.calculateCentroid(useCurrent = true)
                                val zoomChange = event.calculateZoom()
                                val panChange = event.calculatePan()

                                val currentScale = zoomScale
                                val newScale = (currentScale * zoomChange).coerceIn(0.85f, 10.0f)
                                val scaleRatio = if (currentScale > 0.001f) newScale / currentScale else 1f

                                val center = Offset(size.width / 2f, size.height / 2f)
                                val focalPan = (panOffset + panChange) + (centroid - center - panOffset) * (1f - scaleRatio)

                                if (newScale > 1.0f) {
                                    val maxPanX = (size.width.toFloat() * (newScale - 1f)) / 2f
                                    val maxPanY = (size.height.toFloat() * (newScale - 1f)) / 2f
                                    panOffset = Offset(
                                        focalPan.x.coerceIn(-maxPanX * 1.15f, maxPanX * 1.15f),
                                        focalPan.y.coerceIn(-maxPanY * 1.15f, maxPanY * 1.15f)
                                    )
                                    zoomScale = newScale
                                } else {
                                    zoomScale = newScale
                                    panOffset = focalPan
                                }
                                accumulatedDragY = 0f
                                event.changes.forEach { it.consume() }
                            } else if (count == 1) {
                                val change = event.changes.first()
                                if (zoomScale > 1.05f) {
                                    // 放大状态下单指平移巡视（带视口边界严密吸附约束，绝不让画面脱出视界）
                                    val maxPanX = (size.width.toFloat() * (zoomScale - 1f)) / 2f
                                    val maxPanY = (size.height.toFloat() * (zoomScale - 1f)) / 2f
                                    val targetPan = panOffset + change.positionChange()
                                    panOffset = Offset(
                                        targetPan.x.coerceIn(-maxPanX, maxPanX),
                                        targetPan.y.coerceIn(-maxPanY, maxPanY)
                                    )
                                    change.consume()
                                } else {
                                    val posChange = change.positionChange()
                                    totalMovementX += posChange.x
                                    totalMovementY += posChange.y

                                    // 主轴与分区单手势严格互斥锁存：
                                    if (gestureMode == 0) {
                                        val absX = kotlin.math.abs(totalMovementX)
                                        val absY = kotlin.math.abs(totalMovementY)
                                        if (absY > 18f || absX > 18f) {
                                            if (absY > absX * 1.3f) {
                                                // 垂直主轴运动
                                                if (isRightSideZone) {
                                                    gestureMode = 1 // 锁定音量调节模式
                                                } else if (isLeftSideZone) {
                                                    gestureMode = 2 // 锁定上下切台模式
                                                } else {
                                                    gestureMode = 3 // 中立区，锁定忽略
                                                }
                                            } else {
                                                // 横向或斜向滑动，锁定忽略，防止非垂直滑动导致误触
                                                gestureMode = 3
                                            }
                                        }
                                    }

                                    when (gestureMode) {
                                        1 -> {
                                            // 严格锁定为音量调节：绝不累加切台位移，杜绝冲突
                                            val deltaVol = -posChange.y / 320f
                                            viewModel.adjustVolume(deltaVol)
                                            showVolumeHud = true
                                            change.consume()
                                        }
                                        2 -> {
                                            // 严格锁定为切台准备：绝不改变音量
                                            accumulatedDragY += posChange.y
                                            if (kotlin.math.abs(accumulatedDragY) > 25f) {
                                                change.consume()
                                            }
                                        }
                                        else -> {}
                                    }
                                }
                            }
                        } while (event.changes.any { it.pressed })

                        // 手指释放时：
                        if (zoomScale < 1.05f) {
                            if (zoomScale != 1f || panOffset != Offset.Zero) {
                                resetZoomSmoothly()
                            }
                        } else {
                            val clampedScale = zoomScale.coerceIn(1.0f, 10.0f)
                            val maxPanX = (size.width.toFloat() * (clampedScale - 1f)) / 2f
                            val maxPanY = (size.height.toFloat() * (clampedScale - 1f)) / 2f
                            val clampedPanX = panOffset.x.coerceIn(-maxPanX, maxPanX)
                            val clampedPanY = panOffset.y.coerceIn(-maxPanY, maxPanY)
                            if (clampedScale != zoomScale || clampedPanX != panOffset.x || clampedPanY != panOffset.y) {
                                animateZoomTo(clampedScale, Offset(clampedPanX, clampedPanY))
                            }
                        }

                        // 手指释放时：仅在严格锁定为切台模式下触发切台，并加设 500ms 冷却保护，杜绝误触与连触
                        if (zoomScale <= 1.05f && gestureMode == 2) {
                            val now = System.currentTimeMillis()
                            if (now - lastChannelSwitchTime > 500L) {
                                if (accumulatedDragY < -110f) {
                                    lastChannelSwitchTime = now
                                    viewModel.playNextChannel()
                                } else if (accumulatedDragY > 110f) {
                                    lastChannelSwitchTime = now
                                    viewModel.playPreviousChannel()
                                }
                            }
                        }
                    }
                }
        ) {
            // 视频渲染图层（应用双指放大与平移硬件变换）
            LivePlayerView(
                playbackUrl = uiState.playbackUrl,
                playbackStatus = uiState.playbackStatus,
                posterUrl = uiState.activeChannel?.avatar,
                isMuted = uiState.isMuted,
                volume = uiState.volume,
                onRetry = { viewModel.manualRefresh() },
                onPlaybackStateChanged = { viewModel.setPlaybackStatus(it) },
                onPlayerErrorOccurred = { isDec, msg -> viewModel.handlePlayerError(isDec, msg) },
                onBlackScreenDetected = { viewModel.autoRecoverBlackScreen() },
                onVideoAspectRatioChanged = { isPortraitVideo = it },
                isScreenOffAudioMode = uiState.isScreenOffAudioMode,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = zoomScale
                        scaleY = zoomScale
                        translationX = panOffset.x
                        translationY = panOffset.y
                    },
                isTv = isTv
            )

            // 全屏音量手势 HUD 浮层
            AnimatedVisibility(
                visible = showVolumeHud,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.Center)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xDD111116))
                        .border(1.dp, Color(0x33FE2C55), RoundedCornerShape(16.dp))
                        .padding(horizontal = 22.dp, vertical = 16.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = when {
                                uiState.isMuted || uiState.volume == 0f -> Icons.AutoMirrored.Filled.VolumeMute
                                uiState.volume < 0.5f -> Icons.AutoMirrored.Filled.VolumeDown
                                else -> Icons.AutoMirrored.Filled.VolumeUp
                            },
                            contentDescription = null,
                            tint = if (uiState.isMuted || uiState.volume == 0f) DouyinRed else Color(0xFF00D26A),
                            modifier = Modifier.size(34.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (uiState.isMuted) "静音" else "音量 ${(uiState.volume * 100).toInt()}%",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { if (uiState.isMuted) 0f else uiState.volume },
                            color = if (uiState.isMuted) DouyinRed else Color(0xFF00D26A),
                            trackColor = Color(0x33FFFFFF),
                            modifier = Modifier
                                .width(110.dp)
                                .height(5.dp)
                                .clip(RoundedCornerShape(3.dp))
                        )
                    }
                }
            }

            // 仿抖音全屏缩放提示与快捷复位悬浮气泡
            AnimatedVisibility(
                visible = zoomScale > 1.05f,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(
                        bottom = if (showControls) (if (isTv) 92.dp else 84.dp) else TvSafeArea.fullscreenBottomHint(isTv)
                    )
            ) {
                Surface(
                    onClick = resetZoomSmoothly,
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xD9111116),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x44FE2C55))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ZoomIn,
                            contentDescription = "还原缩放",
                            tint = DouyinRed,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${String.format(Locale.US, "%.1f", zoomScale)}x · 点击还原",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // 全屏顶部沉浸式导航浮层：当前主播信息与操作
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            androidx.compose.ui.graphics.Brush.verticalGradient(
                                colors = listOf(Color(0xCC000000), Color.Transparent)
                            )
                        )
                        .padding(
                            horizontal = TvSafeArea.fullscreenHudHorizontal(isTv),
                            vertical = TvSafeArea.fullscreenHudVertical(isTv)
                        )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        AsyncImage(
                            model = uiState.activeChannel?.avatar ?: "android.resource://com.example/drawable/app_launcher_icon_1793754710559",
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .border(1.5.dp, DouyinRed, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = uiState.activeChannel?.name ?: "",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = uiState.activeChannel?.title ?: "",
                                color = Color(0xFFCCCCCC),
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (zoomScale > 1.05f) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0x99000000))
                                    .clickable {
                                        resetZoomSmoothly()
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "${String.format(Locale.US, "%.1f", zoomScale)}x 还原",
                                    color = Color(0xFF00D26A),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                        }

                        var isExitFsFocused by remember { mutableStateOf(false) }
                        val showTvExitFocus = isTv && isExitFsFocused
                        IconButton(
                            onClick = {
                                viewModel.setFullscreen(false)
                                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                            },
                            modifier = Modifier
                                .size(if (isTv) 44.dp else 36.dp)
                                .onFocusChanged { isExitFsFocused = it.isFocused }
                                .background(if (showTvExitFocus) Color(0x4400D26A) else Color(0x66000000), CircleShape)
                                .then(
                                    if (showTvExitFocus) Modifier.border(2.dp, Color(0xFF00D26A), CircleShape) else Modifier
                                )
                                .testTag("btn_exit_fullscreen")
                        ) {
                            Icon(
                                Icons.Default.FullscreenExit,
                                contentDescription = "退出全屏",
                                tint = if (showTvExitFocus) Color(0xFF00D26A) else Color.White
                            )
                        }
                    }
                }
            }

            // 底部操作与换台提示（仅在控制栏激活且未放大时呈现，遵循 TV 过扫描安全边距）
            AnimatedVisibility(
                visible = showControls && zoomScale <= 1.05f,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = TvSafeArea.fullscreenBottomHint(isTv))
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0x66000000))
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = if (isTv) "遥控器方向键上下切台 · 左右调音 · 确认键静音 · 返回键退出" else "左侧滑切台 · 右侧滑调音 · 双指放大(最高10x)",
                        color = Color(0xCCFFFFFF),
                        fontSize = 11.5.sp
                    )
                }
            }
        }
        return
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = StudioBg,
        contentWindowInsets = WindowInsets.safeDrawing,
        modifier = modifier
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val isWideScreen = maxWidth >= 720.dp
            val onEnterPip: () -> Unit = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val rational = if (isPortraitVideo) Rational(9, 16) else Rational(16, 9)
                    val params = PictureInPictureParams.Builder()
                        .setAspectRatio(rational)
                        .build()
                    activity?.enterPictureInPictureMode(params)
                } else {
                    viewModel.showToast("当前系统版本不支持画中画模式")
                }
            }
            val onAddAction: () -> Unit = {
                viewModel.addChannel(inputText)
                inputText = ""
                focusManager.clearFocus()
            }

            if (isTv) {
                // 电视端独立大屏遥控 UI
                TvWorkbenchLayout(
                    uiState = uiState,
                    playerStageFocusRequester = playerStageFocusRequester,
                    inputText = inputText,
                    onInputChange = { inputText = it },
                    onAddChannel = onAddAction,
                    onShowGuide = { viewModel.showDialog(ActiveDialog.UserGuide) },
                    onCheckUpdate = { viewModel.checkVersionUpdate(manual = true) },
                    onRefresh = { viewModel.manualRefresh() },
                    onRetry = { viewModel.manualRefresh() },
                    onPlaybackStateChanged = { viewModel.setPlaybackStatus(it) },
                    onPlayerErrorOccurred = { isDec, msg -> viewModel.handlePlayerError(isDec, msg) },
                    onBlackScreenDetected = { viewModel.autoRecoverBlackScreen() },
                    onVideoAspectRatioChanged = { isPortraitVideo = it },
                    onEnterFullscreen = { viewModel.setFullscreen(true) },
                    onToggleMute = { viewModel.toggleMute() },
                    onVolumeChange = { viewModel.setVolume(it) },
                    onProtocolChange = { viewModel.changeProtocol(it) },
                    onQualityChange = { viewModel.changeQuality(it) },
                    onPipClick = onEnterPip,
                    onRemoteImport = { viewModel.showDialog(ActiveDialog.RemoteImport) },
                    onLocalImport = { viewModel.showDialog(ActiveDialog.LocalImport) },
                    onExport = { viewModel.showDialog(ActiveDialog.ExportFormat) },
                    onClearOffline = { viewModel.clearOfflineOnceChannels() },
                    onClearCache = { viewModel.clearAppCache() },
                    onSelectChannel = { viewModel.playChannel(it) },
                    onDeleteChannel = { viewModel.confirmDelete(it) }
                )
            } else {
                // 移动端（手机/平板）独立触控 UI：无任何绿色焦点圆圈
                MobileWorkbenchLayout(
                    uiState = uiState,
                    isWideScreen = isWideScreen,
                    inputText = inputText,
                    onInputChange = { inputText = it },
                    onAddChannel = onAddAction,
                    onShowGuide = { viewModel.showDialog(ActiveDialog.UserGuide) },
                    onCheckUpdate = { viewModel.checkVersionUpdate(manual = true) },
                    onRefresh = { viewModel.manualRefresh() },
                    onRetry = { viewModel.manualRefresh() },
                    onPlaybackStateChanged = { viewModel.setPlaybackStatus(it) },
                    onPlayerErrorOccurred = { isDec, msg -> viewModel.handlePlayerError(isDec, msg) },
                    onBlackScreenDetected = { viewModel.autoRecoverBlackScreen() },
                    onVideoAspectRatioChanged = { isPortraitVideo = it },
                    onEnterFullscreen = { viewModel.setFullscreen(true) },
                    onToggleMute = { viewModel.toggleMute() },
                    onVolumeChange = { viewModel.setVolume(it) },
                    onProtocolChange = { viewModel.changeProtocol(it) },
                    onQualityChange = { viewModel.changeQuality(it) },
                    onPipClick = onEnterPip,
                    onRemoteImport = { viewModel.showDialog(ActiveDialog.RemoteImport) },
                    onLocalImport = { viewModel.showDialog(ActiveDialog.LocalImport) },
                    onExport = { viewModel.showDialog(ActiveDialog.ExportFormat) },
                    onClearOffline = { viewModel.clearOfflineOnceChannels() },
                    onClearCache = { viewModel.clearAppCache() },
                    onSelectChannel = { viewModel.playChannel(it) },
                    onDeleteChannel = { viewModel.confirmDelete(it) }
                )
            }
        }

        // 全局弹窗挂载
        AppDialogsHost(
            activeDialog = uiState.activeDialog,
            onDismiss = { viewModel.dismissDialog() },
            onConfirmDelete = { viewModel.deleteChannel(it) },
            onRemoteImport = { url, mode -> viewModel.importRemoteSource(url, mode) },
            onLocalImport = { content, mode -> viewModel.importLocalSource(content, mode) },
            onExportSelectFormat = { viewModel.exportChannels(it) },
            onToast = { viewModel.showToast(it) }
        )
    }
}

internal object TvSafeArea {
    fun workbenchHorizontal(isTv: Boolean): Dp = if (isTv) 40.dp else 12.dp
    fun workbenchVertical(isTv: Boolean): Dp = if (isTv) 22.dp else 12.dp
    fun fullscreenHudHorizontal(isTv: Boolean): Dp = if (isTv) 48.dp else 16.dp
    fun fullscreenHudVertical(isTv: Boolean): Dp = if (isTv) 28.dp else 20.dp
    fun fullscreenBottomHint(isTv: Boolean): Dp = if (isTv) 32.dp else 24.dp
}

internal fun readBoundedUtf8Text(input: java.io.InputStream, maxBytes: Int = 1024 * 1024): String {
    val buf = java.io.ByteArrayOutputStream()
    val chunk = ByteArray(8192)
    var total = 0
    while (true) {
        val read = input.read(chunk)
        if (read <= 0) break
        total += read
        if (total > maxBytes) {
            throw IllegalStateException("所选文件超过 ${maxBytes / 1024}KB 安全上限")
        }
        buf.write(chunk, 0, read)
    }
    return String(buf.toByteArray(), java.nio.charset.StandardCharsets.UTF_8)
}
