package com.example.util

import android.app.ActivityManager
import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration

/**
 * 统一的双端与硬件性能嗅探工具：
 * 1. 识别 Android TV / 电视盒子 / 当贝 / 投影仪等大屏设备
 * 2. 识别低内存设备 (Low-RAM TV Box / 入门级硬件) 以启用轻量解码与低内存流控策略
 */
object DeviceEnvironment {

    fun isTvDevice(context: Context): Boolean {
        val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        val isUiModeTv = uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
            (context.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION
        val pm = context.packageManager
        val hasLeanback = pm?.hasSystemFeature(PackageManager.FEATURE_LEANBACK) == true
        @Suppress("DEPRECATION")
        val hasTv = pm?.hasSystemFeature(PackageManager.FEATURE_TELEVISION) == true
        return isUiModeTv || hasLeanback || hasTv
    }

    fun isLowRamDevice(context: Context): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        return am?.isLowRamDevice == true
    }
}
