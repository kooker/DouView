package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.LiveChannel
import com.example.engine.QualityStream
import com.example.ui.PlaybackStatus
import com.example.ui.WorkbenchUiState
import com.example.ui.theme.DouyinRed
import com.example.ui.theme.DouyinRedDim
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioCard
import com.example.ui.theme.StudioCardVariant
import com.example.ui.theme.StudioSub
import com.example.ui.theme.StudioText

/**
 * 移动端（手机/平板触控端）专属独立 UI：
 * - 纯净暗黑触控美学，彻底去除 TV 端绿色 D-Pad 焦点环与残留细线圆圈
 * - 控件按触控人体工学间距排布，音量百分比独立成胶囊，图标互不重叠
 * - 支持手机竖屏单栏流式与平板横屏双栏触控布局
 */
@Composable
fun MobileWorkbenchLayout(
    uiState: WorkbenchUiState,
    isWideScreen: Boolean,
    inputText: String,
    onInputChange: (String) -> Unit,
    onAddChannel: () -> Unit,
    onShowGuide: () -> Unit,
    onCheckUpdate: () -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onPlaybackStateChanged: (PlaybackStatus) -> Unit,
    onPlayerErrorOccurred: (Boolean, String) -> Unit,
    onBlackScreenDetected: () -> Unit,
    onVideoAspectRatioChanged: (Boolean) -> Unit,
    onEnterFullscreen: () -> Unit,
    onToggleMute: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onProtocolChange: (String) -> Unit,
    onQualityChange: (String) -> Unit,
    onPipClick: () -> Unit,
    onRemoteImport: () -> Unit,
    onLocalImport: () -> Unit,
    onExport: () -> Unit,
    onClearOffline: () -> Unit,
    onClearCache: () -> Unit,
    onSelectChannel: (LiveChannel) -> Unit,
    onDeleteChannel: (LiveChannel) -> Unit
) {
    if (isWideScreen) {
        // 平板横屏触控双栏独立 UI
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Column(
                modifier = Modifier
                    .weight(1.25f)
                    .fillMaxHeight()
                    .padding(end = 12.dp)
            ) {
                MobileBannerHeader(
                    activeChannel = uiState.activeChannel,
                    onShowGuide = onShowGuide,
                    onCheckUpdate = onCheckUpdate,
                    onRefresh = onRefresh,
                    isProbing = uiState.isProbing
                )
                Spacer(modifier = Modifier.height(8.dp))
                MobilePlayerStage(
                    uiState = uiState,
                    onRetry = onRetry,
                    onPlaybackStateChanged = onPlaybackStateChanged,
                    onPlayerErrorOccurred = onPlayerErrorOccurred,
                    onBlackScreenDetected = onBlackScreenDetected,
                    onVideoAspectRatioChanged = onVideoAspectRatioChanged,
                    onEnterFullscreen = onEnterFullscreen
                )
                Spacer(modifier = Modifier.height(8.dp))
                MobileControlsBar(
                    selectedProtocol = uiState.selectedProtocol,
                    selectedQuality = uiState.selectedQuality,
                    availableQualities = uiState.currentStreamData?.streams ?: emptyMap(),
                    isMuted = uiState.isMuted,
                    volume = uiState.volume,
                    onToggleMute = onToggleMute,
                    onVolumeChange = onVolumeChange,
                    onProtocolChange = onProtocolChange,
                    onQualityChange = onQualityChange,
                    onPipClick = onPipClick,
                    onFullscreenClick = onEnterFullscreen
                )
            }

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = StudioCard,
                border = BorderStroke(1.dp, StudioBorder),
                modifier = Modifier
                    .weight(1.0f)
                    .fillMaxHeight()
            ) {
                MobileChannelPanelContent(
                    uiState = uiState,
                    inputText = inputText,
                    onInputChange = onInputChange,
                    onAddChannel = onAddChannel,
                    onRemoteImport = onRemoteImport,
                    onLocalImport = onLocalImport,
                    onExport = onExport,
                    onClearOffline = onClearOffline,
                    onClearCache = onClearCache,
                    onSelectChannel = onSelectChannel,
                    onDeleteChannel = onDeleteChannel
                )
            }
        }
    } else {
        // 手机竖屏单栏触控独立 UI
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            MobileBannerHeader(
                activeChannel = uiState.activeChannel,
                onShowGuide = onShowGuide,
                onCheckUpdate = onCheckUpdate,
                onRefresh = onRefresh,
                isProbing = uiState.isProbing
            )

            Spacer(modifier = Modifier.height(8.dp))

            MobilePlayerStage(
                uiState = uiState,
                onRetry = onRetry,
                onPlaybackStateChanged = onPlaybackStateChanged,
                onPlayerErrorOccurred = onPlayerErrorOccurred,
                onBlackScreenDetected = onBlackScreenDetected,
                onVideoAspectRatioChanged = onVideoAspectRatioChanged,
                onEnterFullscreen = onEnterFullscreen
            )

            Spacer(modifier = Modifier.height(8.dp))

            MobileControlsBar(
                selectedProtocol = uiState.selectedProtocol,
                selectedQuality = uiState.selectedQuality,
                availableQualities = uiState.currentStreamData?.streams ?: emptyMap(),
                isMuted = uiState.isMuted,
                volume = uiState.volume,
                onToggleMute = onToggleMute,
                onVolumeChange = onVolumeChange,
                onProtocolChange = onProtocolChange,
                onQualityChange = onQualityChange,
                onPipClick = onPipClick,
                onFullscreenClick = onEnterFullscreen
            )

            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = StudioCard,
                border = BorderStroke(1.dp, StudioBorder),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                MobileChannelPanelContent(
                    uiState = uiState,
                    inputText = inputText,
                    onInputChange = onInputChange,
                    onAddChannel = onAddChannel,
                    onRemoteImport = onRemoteImport,
                    onLocalImport = onLocalImport,
                    onExport = onExport,
                    onClearOffline = onClearOffline,
                    onClearCache = onClearCache,
                    onSelectChannel = onSelectChannel,
                    onDeleteChannel = onDeleteChannel
                )
            }
        }
    }
}

@Composable
private fun MobilePlayerStage(
    uiState: WorkbenchUiState,
    onRetry: () -> Unit,
    onPlaybackStateChanged: (PlaybackStatus) -> Unit,
    onPlayerErrorOccurred: (Boolean, String) -> Unit,
    onBlackScreenDetected: () -> Unit,
    onVideoAspectRatioChanged: (Boolean) -> Unit,
    onEnterFullscreen: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, StudioBorder, RoundedCornerShape(12.dp))
            .clickable(onClick = onEnterFullscreen)
    ) {
        LivePlayerView(
            playbackUrl = uiState.playbackUrl,
            playbackStatus = uiState.playbackStatus,
            posterUrl = uiState.activeChannel?.avatar,
            isMuted = uiState.isMuted,
            volume = uiState.volume,
            onRetry = onRetry,
            onPlaybackStateChanged = onPlaybackStateChanged,
            onPlayerErrorOccurred = onPlayerErrorOccurred,
            onBlackScreenDetected = onBlackScreenDetected,
            onVideoAspectRatioChanged = onVideoAspectRatioChanged,
            isScreenOffAudioMode = uiState.isScreenOffAudioMode,
            modifier = Modifier.fillMaxSize(),
            isTv = false
        )
    }
}

@Composable
private fun MobileBannerHeader(
    activeChannel: LiveChannel?,
    onShowGuide: () -> Unit,
    onCheckUpdate: () -> Unit,
    onRefresh: () -> Unit,
    isProbing: Boolean
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = StudioCard,
        border = BorderStroke(1.dp, StudioBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
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

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = ChannelDisplayFormatter.resolveBannerName(activeChannel),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = ChannelDisplayFormatter.resolveBannerSubText(activeChannel),
                    color = StudioSub,
                    fontSize = 11.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                AdaptiveActionIcon(
                    icon = Icons.Default.SystemUpdate,
                    contentDescription = "检查更新",
                    onClick = onCheckUpdate,
                    testTagName = "btn_check_update",
                    isTv = false,
                    size = 36.dp,
                    iconSize = 18.dp
                )
                AdaptiveActionIcon(
                    icon = Icons.AutoMirrored.Filled.HelpOutline,
                    contentDescription = "使用说明",
                    onClick = onShowGuide,
                    testTagName = "btn_user_guide",
                    isTv = false,
                    size = 36.dp,
                    iconSize = 19.dp
                )
                AdaptiveActionIcon(
                    icon = Icons.Default.Refresh,
                    contentDescription = "刷新",
                    onClick = onRefresh,
                    testTagName = "btn_refresh_stream",
                    isTv = false,
                    size = 36.dp,
                    iconSize = 19.dp,
                    isLoading = isProbing
                )
            }
        }
    }
}

@Composable
private fun MobileControlsBar(
    selectedProtocol: String,
    selectedQuality: String,
    availableQualities: Map<String, QualityStream>,
    isMuted: Boolean,
    volume: Float,
    onToggleMute: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onProtocolChange: (String) -> Unit,
    onQualityChange: (String) -> Unit,
    onPipClick: () -> Unit,
    onFullscreenClick: () -> Unit
) {
    var isQualityDropdownOpen by remember { mutableStateOf(false) }
    var isVolumeDropdownOpen by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = StudioCard,
        border = BorderStroke(1.dp, StudioBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            // 移动端协议切换分段胶囊（清爽红白风格，无 TV 绿框）
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                MobileProtocolPill(
                    label = "HLS",
                    selected = selectedProtocol == "hls",
                    onClick = { onProtocolChange("hls") },
                    testTagName = "chip_proto_hls"
                )
                MobileProtocolPill(
                    label = "FLV",
                    selected = selectedProtocol == "flv",
                    onClick = { onProtocolChange("flv") },
                    testTagName = "chip_proto_flv"
                )
            }

            // 清晰度选择器
            Box {
                val currentQualityName = availableQualities[selectedQuality]?.name
                    ?: (if (availableQualities.isNotEmpty()) availableQualities.values.first().name else "清晰度")

                Surface(
                    onClick = { isQualityDropdownOpen = true },
                    shape = RoundedCornerShape(16.dp),
                    color = StudioCardVariant,
                    border = BorderStroke(1.dp, StudioBorder),
                    modifier = Modifier.testTag("btn_quality_select")
                ) {
                    Text(
                        text = currentQualityName,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = StudioText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
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

            // 右侧音量胶囊 + 画中画 + 全屏（间距清晰不重叠）
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Box {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = StudioCardVariant,
                        border = BorderStroke(1.dp, StudioBorder)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clickable { isVolumeDropdownOpen = true }
                                .padding(start = 4.dp, end = 8.dp, top = 2.dp, bottom = 2.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .clickable(onClick = onToggleMute)
                                    .testTag("btn_volume_mute")
                            ) {
                                Icon(
                                    imageVector = when {
                                        isMuted || volume == 0f -> Icons.AutoMirrored.Filled.VolumeMute
                                        volume < 0.5f -> Icons.AutoMirrored.Filled.VolumeDown
                                        else -> Icons.AutoMirrored.Filled.VolumeUp
                                    },
                                    contentDescription = if (isMuted) "开启声音" else "静音",
                                    tint = if (isMuted || volume == 0f) DouyinRed else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                            Text(
                                text = if (isMuted) "静音" else "${(volume * 100).toInt()}%",
                                fontSize = 10.5.sp,
                                color = if (isMuted) DouyinRed else StudioSub,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    SharedVolumeDropdown(
                        expanded = isVolumeDropdownOpen,
                        onDismissRequest = { isVolumeDropdownOpen = false },
                        isMuted = isMuted,
                        volume = volume,
                        onToggleMute = onToggleMute,
                        onVolumeChange = onVolumeChange
                    )
                }

                AdaptiveActionIcon(
                    icon = Icons.Default.PictureInPicture,
                    contentDescription = "画中画",
                    onClick = onPipClick,
                    testTagName = "btn_pip",
                    isTv = false,
                    size = 34.dp,
                    iconSize = 18.dp
                )

                AdaptiveActionIcon(
                    icon = Icons.Default.Fullscreen,
                    contentDescription = "全屏播放",
                    onClick = onFullscreenClick,
                    testTagName = "btn_fullscreen",
                    isTv = false,
                    size = 34.dp,
                    iconSize = 20.dp
                )
            }
        }
    }
}

@Composable
private fun MobileProtocolPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTagName: String
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (selected) DouyinRedDim else StudioCardVariant,
        border = BorderStroke(1.dp, if (selected) DouyinRed else StudioBorder),
        modifier = Modifier.testTag(testTagName)
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (selected) DouyinRed else StudioSub,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun MobileChannelPanelContent(
    uiState: WorkbenchUiState,
    inputText: String,
    onInputChange: (String) -> Unit,
    onAddChannel: () -> Unit,
    onRemoteImport: () -> Unit,
    onLocalImport: () -> Unit,
    onExport: () -> Unit,
    onClearOffline: () -> Unit,
    onClearCache: () -> Unit,
    onSelectChannel: (LiveChannel) -> Unit,
    onDeleteChannel: (LiveChannel) -> Unit
) {
    val channelCount = uiState.channels.size
    val liveCount = uiState.channels.count { it.isLive }

    Column(modifier = Modifier.padding(10.dp)) {
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
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0x2600D26A))
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "在线 $liveCount / $channelCount",
                        color = Color(0xFF00D26A),
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                AdaptiveActionIcon(
                    icon = Icons.Default.CloudDownload,
                    contentDescription = "远程订阅",
                    onClick = onRemoteImport,
                    testTagName = "btn_header_remote_import",
                    isTv = false,
                    tint = StudioSub,
                    size = 32.dp,
                    iconSize = 16.dp
                )
                AdaptiveActionIcon(
                    icon = Icons.Default.FileOpen,
                    contentDescription = "本地导入",
                    onClick = onLocalImport,
                    testTagName = "btn_header_local_import",
                    isTv = false,
                    tint = StudioSub,
                    size = 32.dp,
                    iconSize = 16.dp
                )
                AdaptiveActionIcon(
                    icon = Icons.Default.FileDownload,
                    contentDescription = "导出列表",
                    onClick = onExport,
                    testTagName = "btn_header_export",
                    isTv = false,
                    tint = StudioSub,
                    size = 32.dp,
                    iconSize = 16.dp
                )
                AdaptiveActionIcon(
                    icon = Icons.Default.CleaningServices,
                    contentDescription = "清理已下播",
                    onClick = onClearOffline,
                    testTagName = "btn_header_clear_offline",
                    isTv = false,
                    tint = StudioSub,
                    size = 32.dp,
                    iconSize = 16.dp
                )
                AdaptiveActionIcon(
                    icon = Icons.Default.DeleteSweep,
                    contentDescription = "清理缓存",
                    onClick = onClearCache,
                    testTagName = "btn_header_clear_cache",
                    isTv = false,
                    tint = StudioSub,
                    size = 32.dp,
                    iconSize = 16.dp
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        SharedAddChannelBar(
            inputText = inputText,
            onInputChange = onInputChange,
            onAdd = onAddChannel,
            isBusy = uiState.isInputBusy,
            isTv = false
        )

        Spacer(modifier = Modifier.height(8.dp))

        SharedChannelListView(
            channels = uiState.channels,
            activeChannel = uiState.activeChannel,
            onSelect = onSelectChannel,
            onDelete = onDeleteChannel,
            isTv = false
        )
    }
}
