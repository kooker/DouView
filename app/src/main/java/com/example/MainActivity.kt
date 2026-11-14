package com.example

import android.app.PictureInPictureParams
import android.app.UiModeManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.util.Rational
import android.view.KeyEvent
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.example.ui.LiveWorkbenchScreen
import com.example.ui.LiveWorkbenchViewModel
import com.example.ui.PlaybackStatus
import com.example.ui.theme.DoumouLiveTheme
import com.example.util.DeviceEnvironment

class MainActivity : ComponentActivity() {

    private val viewModel: LiveWorkbenchViewModel by viewModels()
    private var isInPictureInPictureModeState by mutableStateOf(false)
    private var lastBackPressTime = 0L
    private var lastChannelSwitchTime = 0L
    private var isScreenOff = false

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    isScreenOff = true
                    if (!isTvDevice()) {
                        viewModel.setScreenOffAudioMode(true)
                    }
                }
                Intent.ACTION_SCREEN_ON -> {
                    isScreenOff = false
                    if (!isTvDevice()) {
                        viewModel.setScreenOffAudioMode(false)
                        viewModel.resumePlaybackFromBackground()
                    }
                }
            }
        }
    }

    /**
     * 毫秒级双重设备指纹嗅探：判定当前宿主是否为 Android TV / 当贝 / 投影仪 / 电视盒子
     */
    private fun isTvDevice(): Boolean = DeviceEnvironment.isTvDevice(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 观看直播保持屏幕常亮
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // 电视端自适应兜底：显式强制充满屏幕，消除窗口初始状态挤压
        window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

        // 确保手机顶部状态栏始终可见且清晰（浅色时间与电量图标，适配暗黑中控台）
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        val screenFilter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        ContextCompat.registerReceiver(
            this,
            screenStateReceiver,
            screenFilter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        setContent {
            DoumouLiveTheme {
                LiveWorkbenchScreen(
                    viewModel = viewModel,
                    isInPictureInPictureMode = isInPictureInPictureModeState
                )
            }
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPictureInPictureModeState = isInPictureInPictureMode
    }

    /**
     * 解决电视端打开缩成微型窗口的致命根因：
     * Android TV / 当贝桌面启动初期的焦点切换会被电视 Window Manager 误触发为用户离开（Leave Hint），
     * 电视设备坚决禁用自动进入画中画，永远保持大屏全屏呈现；手机端依然保留丝滑画中画体验。
     */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (isTvDevice()) {
            // 电视端用户按下主页键 (Home Key)：彻底停止播放并退出应用任务，绝不滞留后台
            viewModel.stopPlayback()
            finishAndRemoveTask()
            finishAffinity()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val isPlaying = viewModel.uiState.value.playbackStatus is PlaybackStatus.Playing
            if (isPlaying) {
                try {
                    val params = PictureInPictureParams.Builder()
                        .setAspectRatio(Rational(16, 9))
                        .apply {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                setAutoEnterEnabled(false) // 禁用自动捕获，彻底杜绝手势与桌面焦点切换误触发
                            }
                        }
                        .build()
                    enterPictureInPictureMode(params)
                } catch (_: Exception) {}
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (isTvDevice() && !isInPictureInPictureModeState) {
            viewModel.stopPlayback()
        }
    }

    /**
     * 全屏自适应兜底恢复：从电视桌面再次点击应用时，若检测到误处于画中画，立即调度瞬间展成全屏
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode) {
            val reorderIntent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(reorderIntent)
        }
    }

    override fun onStop() {
        super.onStop()
        if (isTvDevice()) {
            // 电视端进入后台（按主页键或切换应用）：彻底停止播放并退出任务，绝不滞留后台
            viewModel.stopPlayback()
            finishAndRemoveTask()
            finishAffinity()
        } else if (isFinishing) {
            viewModel.stopPlayback()
        } else {
            val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
            val isScreenActuallyOff = isScreenOff || !(powerManager?.isInteractive ?: true)
            if (isScreenActuallyOff) {
                // 手机端息屏：自动转换为音频播放模式，保持连续流媒体音频输出，降低断流并优化网络传输性能
                viewModel.setScreenOffAudioMode(true)
            } else if (!isInPictureInPictureModeState) {
                // 桌面或切换其他应用且未开画中画：保持后台仅音频流播放，确保流媒体传输连续性与音频焦点不丢失
                viewModel.setScreenOffAudioMode(true)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(screenStateReceiver)
        } catch (_: Exception) {}
        viewModel.stopPlayback()
    }

    override fun onStart() {
        super.onStart()
        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        if (powerManager?.isInteractive == true) {
            isScreenOff = false
            viewModel.setScreenOffAudioMode(false)
            viewModel.resumePlaybackFromBackground()
        }
    }

    /**
     * 深度适配电视端遥控器按键：
     * 全屏模式：上/下方向键无缝切台，返回键退出全屏，确认键静音/恢复音量，左右键调节音量
     * 工作台模式：双击返回键安全退出应用并清理后台任务；方向键与确认键保留给界面焦点导航；数字键 1-9 直达频道
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val state = viewModel.uiState.value
        val isFullscreen = state.isFullscreen
        val hasOpenDialog = state.activeDialog != null
        val repeatCount = event?.repeatCount ?: 0

        if (hasOpenDialog) {
            return super.onKeyDown(keyCode, event)
        }

        if (TvKeyEventPolicy.shouldIgnoreRepeat(keyCode, repeatCount)) {
            return true
        }

        when (keyCode) {
            KeyEvent.KEYCODE_BACK -> {
                if (isFullscreen) {
                    viewModel.setFullscreen(false)
                    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    return true
                }
                // 在工作台模式下按返回键：连续双击安全退出应用，彻底释放解码与后台任务，防止滞留后台
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastBackPressTime < 2000L) {
                    viewModel.stopPlayback()
                    finishAndRemoveTask()
                    finishAffinity()
                    return true
                } else {
                    lastBackPressTime = currentTime
                    viewModel.showToast("再按一次退出抖眸流控")
                    return true
                }
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                if (isFullscreen) {
                    val now = System.currentTimeMillis()
                    if (now - lastChannelSwitchTime > 400L) {
                        lastChannelSwitchTime = now
                        viewModel.playPreviousChannel()
                    }
                    return true
                }
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (isFullscreen) {
                    val now = System.currentTimeMillis()
                    if (now - lastChannelSwitchTime > 400L) {
                        lastChannelSwitchTime = now
                        viewModel.playNextChannel()
                    }
                    return true
                }
            }
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_VOLUME_DOWN -> {
                if (isFullscreen) {
                    viewModel.adjustVolume(-0.05f)
                    return true
                }
            }
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_VOLUME_UP -> {
                if (isFullscreen) {
                    viewModel.adjustVolume(0.05f)
                    return true
                }
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                if (isFullscreen) {
                    viewModel.toggleMute()
                    return true
                }
                // 工作台模式下不拦截 OK 键，让 Compose 焦点系统处理当前焦点控件的点击与选中
            }
            in KeyEvent.KEYCODE_1..KeyEvent.KEYCODE_9 -> {
                val targetIndex = keyCode - KeyEvent.KEYCODE_1
                val channels = state.channels
                if (targetIndex in channels.indices) {
                    val channel = channels[targetIndex]
                    viewModel.playChannel(channel)
                    viewModel.showToast("快捷键切换至频道 ${targetIndex + 1}：${channel.name}")
                    return true
                }
            }
            KeyEvent.KEYCODE_MEDIA_NEXT -> {
                viewModel.playNextChannel()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                viewModel.playPreviousChannel()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                viewModel.toggleMute()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY -> {
                viewModel.resumePlaybackFromBackground()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PAUSE, KeyEvent.KEYCODE_MEDIA_STOP -> {
                viewModel.pausePlaybackForBatteryOptimization()
                return true
            }
            KeyEvent.KEYCODE_INFO, KeyEvent.KEYCODE_WINDOW -> {
                viewModel.toggleFullscreen()
                return true
            }
            KeyEvent.KEYCODE_PROG_RED -> {
                viewModel.manualRefresh()
                return true
            }
            KeyEvent.KEYCODE_PROG_GREEN -> {
                val currentProtocol = state.selectedProtocol
                viewModel.changeProtocol(if (currentProtocol == "flv") "hls" else "flv")
                return true
            }
            KeyEvent.KEYCODE_PROG_YELLOW -> {
                viewModel.autoRecoverBlackScreen()
                return true
            }
            KeyEvent.KEYCODE_PROG_BLUE -> {
                viewModel.checkVersionUpdate(manual = true)
                return true
            }
            KeyEvent.KEYCODE_VOLUME_UP -> {
                viewModel.adjustVolume(0.05f)
                return super.onKeyDown(keyCode, event)
            }
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                viewModel.adjustVolume(-0.05f)
                return super.onKeyDown(keyCode, event)
            }
            KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_PAGE_UP -> {
                val now = System.currentTimeMillis()
                if (now - lastChannelSwitchTime > 400L) {
                    lastChannelSwitchTime = now
                    viewModel.playNextChannel()
                }
                return true
            }
            KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_PAGE_DOWN -> {
                val now = System.currentTimeMillis()
                if (now - lastChannelSwitchTime > 400L) {
                    lastChannelSwitchTime = now
                    viewModel.playPreviousChannel()
                }
                return true
            }
            KeyEvent.KEYCODE_MUTE, KeyEvent.KEYCODE_VOLUME_MUTE -> {
                viewModel.toggleMute()
                return true
            }
            KeyEvent.KEYCODE_MENU -> {
                if (isFullscreen) {
                    viewModel.manualRefresh()
                } else {
                    viewModel.checkVersionUpdate(manual = true)
                }
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }
}

internal object TvKeyEventPolicy {
    fun shouldIgnoreRepeat(keyCode: Int, repeatCount: Int): Boolean {
        if (repeatCount <= 0) return false
        return when (keyCode) {
            KeyEvent.KEYCODE_BACK,
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER,
            KeyEvent.KEYCODE_MUTE,
            KeyEvent.KEYCODE_VOLUME_MUTE,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_MEDIA_PLAY,
            KeyEvent.KEYCODE_MEDIA_PAUSE,
            KeyEvent.KEYCODE_MEDIA_STOP,
            KeyEvent.KEYCODE_INFO,
            KeyEvent.KEYCODE_WINDOW,
            KeyEvent.KEYCODE_MENU,
            KeyEvent.KEYCODE_PROG_RED,
            KeyEvent.KEYCODE_PROG_GREEN,
            KeyEvent.KEYCODE_PROG_YELLOW,
            KeyEvent.KEYCODE_PROG_BLUE,
            in KeyEvent.KEYCODE_1..KeyEvent.KEYCODE_9 -> true
            else -> false
        }
    }
}
