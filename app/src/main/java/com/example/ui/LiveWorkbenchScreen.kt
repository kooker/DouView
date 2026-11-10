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
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.util.lerp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import java.util.Locale
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.stringResource
import coil.compose.AsyncImage
import com.example.R
import com.example.ui.components.AppDialogsHost
import com.example.ui.components.ChannelItem
import com.example.ui.components.LivePlayerView
import com.example.ui.theme.CheckingAmber
import com.example.ui.theme.DouyinRed
import com.example.ui.theme.DouyinRedDim
import com.example.ui.theme.StudioBg
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioCard
import com.example.ui.theme.StudioCardVariant
import com.example.ui.theme.StudioSub
import com.example.ui.theme.StudioText

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
    val isTv = remember {
        val uiModeManager = context.getSystemService(android.content.Context.UI_MODE_SERVICE) as? android.app.UiModeManager
        val isUiModeTv = uiModeManager?.currentModeType == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
        val hasLeanback = context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_LEANBACK)
        @Suppress("DEPRECATION")
        val hasTv = context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_TELEVISION)
        isUiModeTv || hasLeanback || hasTv
    }

    // 本地文件选择器（TXT / CSV / JSON 文件导入）
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            try {
                context.contentResolver.openInputStream(it)?.use { stream ->
                    val content = stream.bufferedReader().use { reader -> reader.readText() }
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
                    }
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
                    .padding(bottom = if (showControls) 84.dp else 24.dp)
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
                        .padding(horizontal = 16.dp, vertical = 20.dp)
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

                        IconButton(
                            onClick = {
                                viewModel.setFullscreen(false)
                                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                            },
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color(0x66000000), CircleShape)
                                .testTag("btn_exit_fullscreen")
                        ) {
                            Icon(Icons.Default.FullscreenExit, contentDescription = "退出全屏", tint = Color.White)
                        }
                    }
                }
            }

            // 底部操作与换台提示（仅在控制栏激活且未放大时呈现）
            AnimatedVisibility(
                visible = showControls && zoomScale <= 1.05f,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp)
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

            if (isWideScreen) {
                // 平板 / 电视 / 横屏宽屏自适应双栏布局
                Row(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                    // 左侧：播放器与控制区
                    Column(
                        modifier = Modifier
                            .weight(1.3f)
                            .fillMaxHeight()
                            .padding(end = 12.dp)
                    ) {
                        BannerHeader(
                            activeChannel = uiState.activeChannel,
                            onShowGuide = { viewModel.showDialog(ActiveDialog.UserGuide) },
                            onCheckUpdate = { viewModel.checkVersionUpdate(manual = true) },
                            onRefresh = { viewModel.manualRefresh() },
                            isProbing = uiState.isProbing
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        // 视频播放舞台 (宽屏 / 电视大屏) - 支持遥控器焦点感知与按OK/点击直接全屏
                        var isPlayerFocused by remember { mutableStateOf(false) }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                                .clip(RoundedCornerShape(12.dp))
                                .onFocusChanged { isPlayerFocused = it.isFocused }
                                .border(
                                    if (isPlayerFocused) 2.dp else 1.dp,
                                    if (isPlayerFocused) Color(0xFF00D26A) else StudioBorder,
                                    RoundedCornerShape(12.dp)
                                )
                                .focusable()
                                .clickable { viewModel.setFullscreen(true) }
                        ) {
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
                                modifier = Modifier.fillMaxSize()
                            )
                            if (isTv && isPlayerFocused) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(8.dp)
                                        .background(Color(0xCC000000), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text("按OK键全屏", color = Color(0xFF00D26A), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        ControlsRow(
                            selectedProtocol = uiState.selectedProtocol,
                            selectedQuality = uiState.selectedQuality,
                            availableQualities = uiState.currentStreamData?.streams ?: emptyMap(),
                            isMuted = uiState.isMuted,
                            volume = uiState.volume,
                            onToggleMute = { viewModel.toggleMute() },
                            onVolumeChange = { viewModel.setVolume(it) },
                            onProtocolChange = { viewModel.changeProtocol(it) },
                            onQualityChange = { viewModel.changeQuality(it) },
                            onPipClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    val rational = if (isPortraitVideo) Rational(9, 16) else Rational(16, 9)
                                    val params = PictureInPictureParams.Builder()
                                        .setAspectRatio(rational)
                                        .build()
                                    activity?.enterPictureInPictureMode(params)
                                } else {
                                    viewModel.showToast("当前系统版本不支持画中画模式")
                                }
                            },
                            onFullscreenClick = {
                                viewModel.setFullscreen(true)
                            }
                        )
                    }

                    // 右侧：播放列表与管理
                    Column(
                        modifier = Modifier
                            .weight(1.0f)
                            .fillMaxHeight()
                            .background(StudioCard, RoundedCornerShape(12.dp))
                            .border(1.dp, StudioBorder, RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        PlaylistHeader(
                            channelCount = uiState.channels.size,
                            liveCount = uiState.channels.count { it.isLive },
                            onRemoteImport = { viewModel.showDialog(ActiveDialog.RemoteImport) },
                            onLocalImport = { viewModel.showDialog(ActiveDialog.LocalImport) },
                            onExport = { viewModel.showDialog(ActiveDialog.ExportFormat) },
                            onClearOffline = { viewModel.clearOfflineOnceChannels() },
                            onClearCache = { viewModel.clearAppCache() }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        AddChannelBar(
                            inputText = inputText,
                            onInputChange = { inputText = it },
                            onAdd = {
                                viewModel.addChannel(inputText)
                                inputText = ""
                                focusManager.clearFocus()
                            },
                            isBusy = uiState.isInputBusy
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        ChannelListView(
                            channels = uiState.channels,
                            activeChannel = uiState.activeChannel,
                            onSelect = { viewModel.playChannel(it) },
                            onDelete = { viewModel.confirmDelete(it) }
                        )
                    }
                }
            } else {
                // 手机竖屏紧凑单栏流式布局
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    // 顶部状态条
                    BannerHeader(
                        activeChannel = uiState.activeChannel,
                        onShowGuide = { viewModel.showDialog(ActiveDialog.UserGuide) },
                        onCheckUpdate = { viewModel.checkVersionUpdate(manual = true) },
                        onRefresh = { viewModel.manualRefresh() },
                        isProbing = uiState.isProbing
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // 视频播放舞台 (固定 16:9) - 支持点击直接全屏
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { viewModel.setFullscreen(true) }
                    ) {
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
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // 播放控制器行（协议 / 清晰度 / 静音 / 画中画 / 全屏）
                    ControlsRow(
                        selectedProtocol = uiState.selectedProtocol,
                        selectedQuality = uiState.selectedQuality,
                        availableQualities = uiState.currentStreamData?.streams ?: emptyMap(),
                        isMuted = uiState.isMuted,
                        volume = uiState.volume,
                        onToggleMute = { viewModel.toggleMute() },
                        onVolumeChange = { viewModel.setVolume(it) },
                        onProtocolChange = { viewModel.changeProtocol(it) },
                        onQualityChange = { viewModel.changeQuality(it) },
                        onPipClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                val rational = if (isPortraitVideo) Rational(9, 16) else Rational(16, 9)
                                val params = PictureInPictureParams.Builder()
                                    .setAspectRatio(rational)
                                    .build()
                                activity?.enterPictureInPictureMode(params)
                            } else {
                                viewModel.showToast("当前系统版本不支持画中画模式")
                            }
                        },
                        onFullscreenClick = {
                            viewModel.setFullscreen(true)
                        }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // 直播频道管理面板
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = StudioCard,
                        border = androidx.compose.foundation.BorderStroke(1.dp, StudioBorder),
                        modifier = Modifier.weight(1f).fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            PlaylistHeader(
                                channelCount = uiState.channels.size,
                                liveCount = uiState.channels.count { it.isLive },
                                onRemoteImport = { viewModel.showDialog(ActiveDialog.RemoteImport) },
                                onLocalImport = { viewModel.showDialog(ActiveDialog.LocalImport) },
                                onExport = { viewModel.showDialog(ActiveDialog.ExportFormat) },
                                onClearOffline = { viewModel.clearOfflineOnceChannels() },
                                onClearCache = { viewModel.clearAppCache() }
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            AddChannelBar(
                                inputText = inputText,
                                onInputChange = { inputText = it },
                                onAdd = {
                                    viewModel.addChannel(inputText)
                                    inputText = ""
                                    focusManager.clearFocus()
                                },
                                isBusy = uiState.isInputBusy
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            ChannelListView(
                                channels = uiState.channels,
                                activeChannel = uiState.activeChannel,
                                onSelect = { viewModel.playChannel(it) },
                                onDelete = { viewModel.confirmDelete(it) }
                            )
                        }
                    }
                }
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

@Composable
private fun BannerHeader(
    activeChannel: com.example.data.LiveChannel?,
    onShowGuide: () -> Unit,
    onCheckUpdate: () -> Unit,
    onRefresh: () -> Unit,
    isProbing: Boolean
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = StudioCard,
        border = androidx.compose.foundation.BorderStroke(1.dp, StudioBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            // 当前激活主播头像
            AsyncImage(
                model = activeChannel?.avatar ?: "android.resource://com.example/drawable/app_launcher_icon_1793754710559",
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1C1C24))
                    .border(1.5.dp, DouyinRed, CircleShape)
            )

            Spacer(modifier = Modifier.width(10.dp))

            // 名称与标题
            val bannerDisplayName = activeChannel?.name?.let {
                if (it.isBlank() || it.matches(Regex("^(?:频道|房间)\\s*\\d{3,22}$")) || it == activeChannel.rid) "抖音主播" else it
            } ?: "请选择直播间"
            val bannerSubText = when {
                activeChannel == null -> "从下方列表中点击频道开始播放"
                activeChannel.title.isNotEmpty() && !activeChannel.title.matches(Regex("^(?:房间号?|频道)\\s*[:：]?\\s*\\d+.*$")) -> activeChannel.title
                activeChannel.isLive -> "实况直播中"
                !activeChannel.statusKnown -> "正在核验开播状态..."
                else -> "主播暂未开播"
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = bannerDisplayName,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = bannerSubText,
                    color = StudioSub,
                    fontSize = 11.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // 检查版本更新
            IconButton(
                onClick = onCheckUpdate,
                modifier = Modifier.size(36.dp).testTag("btn_check_update")
            ) {
                Icon(
                    imageVector = Icons.Default.SystemUpdate,
                    contentDescription = "检查更新",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(18.dp)
                )
            }

            // 说明按钮
            IconButton(
                onClick = onShowGuide,
                modifier = Modifier.size(36.dp).testTag("btn_user_guide")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                    contentDescription = "使用说明",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(19.dp)
                )
            }

            // 探活与刷新按钮
            IconButton(
                onClick = onRefresh,
                modifier = Modifier.size(36.dp).testTag("btn_refresh_stream")
            ) {
                if (isProbing) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        color = DouyinRed,
                        modifier = Modifier.size(16.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "刷新",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ControlsRow(
    selectedProtocol: String,
    selectedQuality: String,
    availableQualities: Map<String, com.example.engine.QualityStream>,
    isMuted: Boolean,
    volume: Float = 1.0f,
    onToggleMute: () -> Unit,
    onVolumeChange: (Float) -> Unit = {},
    onProtocolChange: (String) -> Unit,
    onQualityChange: (String) -> Unit,
    onPipClick: () -> Unit,
    onFullscreenClick: () -> Unit
) {
    var isQualityDropdownOpen by remember { mutableStateOf(false) }
    var isVolumeDropdownOpen by remember { mutableStateOf(false) }
    val greenAccent = Color(0xFF00D26A)
    val greenBg = Color(0x2600D26A)

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = StudioCard,
        border = androidx.compose.foundation.BorderStroke(1.dp, StudioBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            // 协议切换 (HLS / FLV) - 选中与焦点状态采用绿色高亮 (适配电视遥控器)
            Row(verticalAlignment = Alignment.CenterVertically) {
                var isHlsFocused by remember { mutableStateOf(false) }
                var isFlvFocused by remember { mutableStateOf(false) }

                FilterChip(
                    selected = selectedProtocol == "hls",
                    onClick = { onProtocolChange("hls") },
                    label = { Text("HLS", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = greenBg,
                        selectedLabelColor = greenAccent,
                        containerColor = StudioCardVariant,
                        labelColor = StudioSub
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = selectedProtocol == "hls",
                        borderColor = if (isHlsFocused || selectedProtocol == "hls") greenAccent else StudioBorder,
                        borderWidth = if (isHlsFocused) 2.dp else 1.dp
                    ),
                    modifier = Modifier
                        .onFocusChanged { isHlsFocused = it.isFocused }
                        .testTag("chip_proto_hls")
                )
                Spacer(modifier = Modifier.width(4.dp))
                FilterChip(
                    selected = selectedProtocol == "flv",
                    onClick = { onProtocolChange("flv") },
                    label = { Text("FLV", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = greenBg,
                        selectedLabelColor = greenAccent,
                        containerColor = StudioCardVariant,
                        labelColor = StudioSub
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = selectedProtocol == "flv",
                        borderColor = if (isFlvFocused || selectedProtocol == "flv") greenAccent else StudioBorder,
                        borderWidth = if (isFlvFocused) 2.dp else 1.dp
                    ),
                    modifier = Modifier
                        .onFocusChanged { isFlvFocused = it.isFocused }
                        .testTag("chip_proto_flv")
                )
            }

            // 清晰度选择下拉框
            Box {
                val currentQualityName = availableQualities[selectedQuality]?.name
                    ?: (if (availableQualities.isNotEmpty()) availableQualities.values.first().name else "清晰度自适应")

                var isQualityFocused by remember { mutableStateOf(false) }
                OutlinedButton(
                    onClick = { isQualityDropdownOpen = true },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        if (isQualityFocused) 2.dp else 1.dp,
                        if (isQualityFocused) greenAccent else StudioBorder
                    ),
                    modifier = Modifier
                        .height(34.dp)
                        .onFocusChanged { isQualityFocused = it.isFocused }
                        .testTag("btn_quality_select")
                ) {
                    Text(currentQualityName, fontSize = 11.5.sp, color = if (isQualityFocused) greenAccent else MaterialTheme.colorScheme.onSurface)
                }

                DropdownMenu(
                    expanded = isQualityDropdownOpen,
                    onDismissRequest = { isQualityDropdownOpen = false },
                    modifier = Modifier.background(StudioCard)
                ) {
                    if (availableQualities.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text("默认画质", fontSize = 12.sp) },
                            onClick = { isQualityDropdownOpen = false }
                        )
                    } else {
                        availableQualities.forEach { (key, stream) ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = stream.name,
                                        fontSize = 12.sp,
                                        color = if (key == selectedQuality) DouyinRed else StudioText,
                                        fontWeight = if (key == selectedQuality) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                onClick = {
                                    isQualityDropdownOpen = false
                                    onQualityChange(key)
                                }
                            )
                        }
                    }
                }
            }

            // 操作集合：连续音量调节/静音、画中画、全屏
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { isVolumeDropdownOpen = true }
                            .padding(end = 4.dp)
                    ) {
                        var isMuteFocused by remember { mutableStateOf(false) }
                        IconButton(
                            onClick = onToggleMute,
                            modifier = Modifier
                                .size(34.dp)
                                .onFocusChanged { isMuteFocused = it.isFocused }
                                .background(if (isMuteFocused) Color(0x3300D26A) else Color.Transparent, CircleShape)
                                .border(if (isMuteFocused) 2.dp else 0.dp, greenAccent, CircleShape)
                                .testTag("btn_volume_mute")
                        ) {
                            Icon(
                                imageVector = when {
                                    isMuted || volume == 0f -> Icons.AutoMirrored.Filled.VolumeMute
                                    volume < 0.5f -> Icons.AutoMirrored.Filled.VolumeDown
                                    else -> Icons.AutoMirrored.Filled.VolumeUp
                                },
                                contentDescription = if (isMuted) "开启声音" else "静音",
                                tint = if (isMuted || volume == 0f) DouyinRed else if (isMuteFocused) greenAccent else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                        Text(
                            text = if (isMuted) "静音" else "${(volume * 100).toInt()}%",
                            fontSize = 10.5.sp,
                            color = if (isMuted) DouyinRed else StudioSub,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // 音量精准滑块与预设快捷调节浮层
                    DropdownMenu(
                        expanded = isVolumeDropdownOpen,
                        onDismissRequest = { isVolumeDropdownOpen = false },
                        modifier = Modifier.background(StudioCard).padding(12.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.width(180.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = if (isMuted) "已静音" else "音量 ${(volume * 100).toInt()}%",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (isMuted) "点击恢复" else "点击静音",
                                    fontSize = 11.sp,
                                    color = DouyinRed,
                                    modifier = Modifier.clickable { onToggleMute() }
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Slider(
                                value = if (isMuted) 0f else volume,
                                onValueChange = { onVolumeChange(it) },
                                valueRange = 0f..1f,
                                colors = SliderDefaults.colors(
                                    thumbColor = DouyinRed,
                                    activeTrackColor = DouyinRed,
                                    inactiveTrackColor = StudioBorder
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                listOf(0.2f, 0.5f, 0.8f, 1.0f).forEach { preset ->
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(StudioCardVariant)
                                            .clickable { onVolumeChange(preset) }
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "${(preset * 100).toInt()}%",
                                            fontSize = 9.5.sp,
                                            color = StudioSub
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                IconButton(
                    onClick = onPipClick,
                    modifier = Modifier.size(34.dp).testTag("btn_pip")
                ) {
                    Icon(
                        imageVector = Icons.Default.PictureInPicture,
                        contentDescription = "画中画",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(18.dp)
                    )
                }
                var isFullscreenBtnFocused by remember { mutableStateOf(false) }
                IconButton(
                    onClick = onFullscreenClick,
                    modifier = Modifier
                        .size(34.dp)
                        .onFocusChanged { isFullscreenBtnFocused = it.isFocused }
                        .background(if (isFullscreenBtnFocused) Color(0x3300D26A) else Color.Transparent, CircleShape)
                        .border(if (isFullscreenBtnFocused) 2.dp else 0.dp, greenAccent, CircleShape)
                        .testTag("btn_fullscreen")
                ) {
                    Icon(
                        imageVector = Icons.Default.Fullscreen,
                        contentDescription = "全屏播放",
                        tint = if (isFullscreenBtnFocused) greenAccent else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaylistHeader(
    channelCount: Int,
    liveCount: Int,
    onRemoteImport: () -> Unit,
    onLocalImport: () -> Unit,
    onExport: () -> Unit,
    onClearOffline: () -> Unit,
    onClearCache: () -> Unit
) {
    val greenAccent = Color(0xFF00D26A)
    val greenBg = Color(0x2600D26A)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "直播列表",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(6.dp))
            // 在线状态标记改为绿色
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(greenBg)
                    .padding(horizontal = 7.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "在线 $liveCount / $channelCount",
                    color = greenAccent,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onRemoteImport,
                modifier = Modifier.size(32.dp).testTag("btn_header_remote_import")
            ) {
                Icon(Icons.Default.CloudDownload, contentDescription = "远程订阅", tint = StudioSub, modifier = Modifier.size(16.dp))
            }
            IconButton(
                onClick = onLocalImport,
                modifier = Modifier.size(32.dp).testTag("btn_header_local_import")
            ) {
                Icon(Icons.Default.FileOpen, contentDescription = "本地导入", tint = StudioSub, modifier = Modifier.size(16.dp))
            }
            IconButton(
                onClick = onExport,
                modifier = Modifier.size(32.dp).testTag("btn_header_export")
            ) {
                Icon(Icons.Default.FileDownload, contentDescription = "导出列表", tint = StudioSub, modifier = Modifier.size(16.dp))
            }
            IconButton(
                onClick = onClearOffline,
                modifier = Modifier.size(32.dp).testTag("btn_header_clear_offline")
            ) {
                Icon(Icons.Default.CleaningServices, contentDescription = "清理已下播", tint = StudioSub, modifier = Modifier.size(16.dp))
            }
            IconButton(
                onClick = onClearCache,
                modifier = Modifier.size(32.dp).testTag("btn_header_clear_cache")
            ) {
                Icon(Icons.Default.DeleteSweep, contentDescription = "清理缓存", tint = StudioSub, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun AddChannelBar(
    inputText: String,
    onInputChange: (String) -> Unit,
    onAdd: () -> Unit,
    isBusy: Boolean
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = inputText,
            onValueChange = onInputChange,
            placeholder = {
                Text(
                    text = stringResource(R.string.input_channel_placeholder),
                    fontSize = 12.sp,
                    color = Color(0xFF8E8E9E),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            textStyle = TextStyle(
                fontSize = 12.5.sp,
                color = StudioText,
                fontWeight = FontWeight.Normal
            ),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { if (inputText.isNotBlank() && !isBusy) onAdd() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = DouyinRed,
                unfocusedBorderColor = StudioBorder,
                focusedContainerColor = StudioCardVariant,
                focusedTextColor = StudioText,
                unfocusedTextColor = StudioText
            ),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 46.dp)
                .testTag("input_channel_query")
        )

        Spacer(modifier = Modifier.width(8.dp))

        Button(
            onClick = onAdd,
            enabled = inputText.isNotBlank() && !isBusy,
            colors = ButtonDefaults.buttonColors(containerColor = DouyinRed),
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 14.dp),
            modifier = Modifier
                .height(46.dp)
                .testTag("btn_submit_add_channel")
        ) {
            if (isBusy) {
                CircularProgressIndicator(strokeWidth = 2.dp, color = Color.White, modifier = Modifier.size(16.dp))
            } else {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(2.dp))
                Text("添加", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ChannelListView(
    channels: List<com.example.data.LiveChannel>,
    activeChannel: com.example.data.LiveChannel?,
    onSelect: (com.example.data.LiveChannel) -> Unit,
    onDelete: (com.example.data.LiveChannel) -> Unit
) {
    if (channels.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "暂无直播频道\n粘贴分享口令或房间号快速添加",
                color = StudioSub,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    } else {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxSize().testTag("channels_lazy_column")
        ) {
            items(channels, key = { it.rid }) { channel ->
                ChannelItem(
                    channel = channel,
                    isActive = channel.rid == activeChannel?.rid,
                    onClick = { onSelect(channel) },
                    onDelete = { onDelete(channel) }
                )
            }
        }
    }
}
