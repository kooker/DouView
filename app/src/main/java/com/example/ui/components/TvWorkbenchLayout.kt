package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
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
import com.example.ui.TvSafeArea
import com.example.ui.WorkbenchUiState
import com.example.ui.theme.DouyinRed
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioCard
import com.example.ui.theme.StudioCardVariant
import com.example.ui.theme.StudioSub
import com.example.ui.theme.StudioText

/**
 * Android TV / 电视盒子专属独立大屏 UI：
 * - 遵循 5% Overscan 安全边距防止屏幕边缘裁切
 * - 左侧大屏播放与遥控控制区 + 右侧直播管理区双栏并行，focusGroup 分区精确导航
 * - 仅当前焦点控件显示高对比绿色高亮聚焦环，控件间距充裕绝不重叠
 */
@Composable
fun TvWorkbenchLayout(
    uiState: WorkbenchUiState,
    playerStageFocusRequester: FocusRequester,
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
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                horizontal = TvSafeArea.workbenchHorizontal(true),
                vertical = TvSafeArea.workbenchVertical(true)
            )
    ) {
        // 左侧：电视大屏播放器与遥控控制区
        Column(
            modifier = Modifier
                .weight(1.3f)
                .fillMaxHeight()
                .padding(end = 14.dp)
                .focusGroup()
        ) {
            TvBannerHeader(
                activeChannel = uiState.activeChannel,
                onShowGuide = onShowGuide,
                onCheckUpdate = onCheckUpdate,
                onRefresh = onRefresh,
                isProbing = uiState.isProbing
            )
            Spacer(modifier = Modifier.height(10.dp))

            var isPlayerFocused by remember { mutableStateOf(false) }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(12.dp))
                    .border(
                        width = if (isPlayerFocused) 2.5.dp else 1.dp,
                        color = if (isPlayerFocused) TvFocusGreen else StudioBorder,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .focusRequester(playerStageFocusRequester)
                    .onFocusChanged { isPlayerFocused = it.isFocused }
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
                    isTv = true
                )
                if (isPlayerFocused) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(10.dp)
                            .background(Color(0xCC000000), RoundedCornerShape(6.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "按OK键全屏",
                            color = TvFocusGreen,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            TvControlsRow(
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

        // 右侧：电视端频道管理列表
        Column(
            modifier = Modifier
                .weight(1.0f)
                .fillMaxHeight()
                .background(StudioCard, RoundedCornerShape(12.dp))
                .border(1.dp, StudioBorder, RoundedCornerShape(12.dp))
                .padding(14.dp)
                .focusGroup()
        ) {
            TvPlaylistHeader(
                channelCount = uiState.channels.size,
                liveCount = uiState.channels.count { it.isLive },
                onRemoteImport = onRemoteImport,
                onLocalImport = onLocalImport,
                onExport = onExport,
                onClearOffline = onClearOffline,
                onClearCache = onClearCache
            )
            Spacer(modifier = Modifier.height(10.dp))
            SharedAddChannelBar(
                inputText = inputText,
                onInputChange = onInputChange,
                onAdd = onAddChannel,
                isBusy = uiState.isInputBusy,
                isTv = true
            )
            Spacer(modifier = Modifier.height(10.dp))
            SharedChannelListView(
                channels = uiState.channels,
                activeChannel = uiState.activeChannel,
                onSelect = onSelectChannel,
                onDelete = onDeleteChannel,
                isTv = true
            )
        }
    }
}

@Composable
private fun TvBannerHeader(
    activeChannel: LiveChannel?,
    onShowGuide: () -> Unit,
    onCheckUpdate: () -> Unit,
    onRefresh: () -> Unit,
    isProbing: Boolean
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
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

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.focusGroup()
            ) {
                AdaptiveActionIcon(
                    icon = Icons.Default.SystemUpdate,
                    contentDescription = "检查更新",
                    onClick = onCheckUpdate,
                    testTagName = "btn_check_update",
                    isTv = true,
                    size = 38.dp,
                    iconSize = 18.dp
                )
                AdaptiveActionIcon(
                    icon = Icons.AutoMirrored.Filled.HelpOutline,
                    contentDescription = "使用说明",
                    onClick = onShowGuide,
                    testTagName = "btn_user_guide",
                    isTv = true,
                    size = 38.dp,
                    iconSize = 19.dp
                )
                AdaptiveActionIcon(
                    icon = Icons.Default.Refresh,
                    contentDescription = "刷新",
                    onClick = onRefresh,
                    testTagName = "btn_refresh_stream",
                    isTv = true,
                    size = 38.dp,
                    iconSize = 19.dp,
                    isLoading = isProbing
                )
            }
        }
    }
}

@Composable
private fun TvControlsRow(
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
    val greenBg = Color(0x2600D26A)

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = StudioCard,
        border = BorderStroke(1.dp, StudioBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .padding(horizontal = 10.dp, vertical = 4.dp)
                .focusGroup()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.focusGroup()
            ) {
                var isHlsFocused by remember { mutableStateOf(false) }
                var isFlvFocused by remember { mutableStateOf(false) }

                FilterChip(
                    selected = selectedProtocol == "hls",
                    onClick = { onProtocolChange("hls") },
                    label = { Text("HLS", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = greenBg,
                        selectedLabelColor = TvFocusGreen,
                        containerColor = StudioCardVariant,
                        labelColor = StudioSub
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = selectedProtocol == "hls",
                        borderColor = if (isHlsFocused || selectedProtocol == "hls") TvFocusGreen else StudioBorder,
                        borderWidth = if (isHlsFocused) 2.dp else 1.dp
                    ),
                    modifier = Modifier
                        .onFocusChanged { isHlsFocused = it.isFocused }
                        .testTag("chip_proto_hls")
                )
                Spacer(modifier = Modifier.width(6.dp))
                FilterChip(
                    selected = selectedProtocol == "flv",
                    onClick = { onProtocolChange("flv") },
                    label = { Text("FLV", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = greenBg,
                        selectedLabelColor = TvFocusGreen,
                        containerColor = StudioCardVariant,
                        labelColor = StudioSub
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = selectedProtocol == "flv",
                        borderColor = if (isFlvFocused || selectedProtocol == "flv") TvFocusGreen else StudioBorder,
                        borderWidth = if (isFlvFocused) 2.dp else 1.dp
                    ),
                    modifier = Modifier
                        .onFocusChanged { isFlvFocused = it.isFocused }
                        .testTag("chip_proto_flv")
                )
            }

            Box {
                val currentQualityName = availableQualities[selectedQuality]?.name
                    ?: (if (availableQualities.isNotEmpty()) availableQualities.values.first().name else "清晰度自适应")

                var isQualityFocused by remember { mutableStateOf(false) }
                OutlinedButton(
                    onClick = { isQualityDropdownOpen = true },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    border = BorderStroke(
                        if (isQualityFocused) 2.dp else 1.dp,
                        if (isQualityFocused) TvFocusGreen else StudioBorder
                    ),
                    modifier = Modifier
                        .height(34.dp)
                        .onFocusChanged { isQualityFocused = it.isFocused }
                        .testTag("btn_quality_select")
                ) {
                    Text(
                        currentQualityName,
                        fontSize = 11.5.sp,
                        color = if (isQualityFocused) TvFocusGreen else MaterialTheme.colorScheme.onSurface
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

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.focusGroup()
            ) {
                Box {
                    var isVolumeSliderTriggerFocused by remember { mutableStateOf(false) }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(StudioCardVariant, RoundedCornerShape(16.dp))
                            .then(
                                if (isVolumeSliderTriggerFocused) {
                                    Modifier.border(2.dp, TvFocusGreen, RoundedCornerShape(16.dp))
                                } else {
                                    Modifier.border(1.dp, StudioBorder, RoundedCornerShape(16.dp))
                                }
                            )
                            .onFocusChanged { isVolumeSliderTriggerFocused = it.isFocused }
                            .clickable { isVolumeDropdownOpen = true }
                            .padding(start = 2.dp, end = 8.dp, top = 2.dp, bottom = 2.dp)
                    ) {
                        AdaptiveActionIcon(
                            icon = when {
                                isMuted || volume == 0f -> Icons.AutoMirrored.Filled.VolumeMute
                                volume < 0.5f -> Icons.AutoMirrored.Filled.VolumeDown
                                else -> Icons.AutoMirrored.Filled.VolumeUp
                            },
                            contentDescription = if (isMuted) "开启声音" else "静音",
                            onClick = onToggleMute,
                            testTagName = "btn_volume_mute",
                            isTv = true,
                            tint = if (isMuted || volume == 0f) DouyinRed else MaterialTheme.colorScheme.onSurface,
                            size = 32.dp,
                            iconSize = 18.dp
                        )
                        Text(
                            text = if (isMuted) "静音" else "${(volume * 100).toInt()}%",
                            fontSize = 10.5.sp,
                            color = if (isMuted) DouyinRed else StudioSub,
                            fontWeight = FontWeight.Medium
                        )
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
                    isTv = true,
                    size = 36.dp,
                    iconSize = 18.dp
                )

                AdaptiveActionIcon(
                    icon = Icons.Default.Fullscreen,
                    contentDescription = "全屏播放",
                    onClick = onFullscreenClick,
                    testTagName = "btn_fullscreen",
                    isTv = true,
                    size = 36.dp,
                    iconSize = 20.dp
                )
            }
        }
    }
}

@Composable
private fun TvPlaylistHeader(
    channelCount: Int,
    liveCount: Int,
    onRemoteImport: () -> Unit,
    onLocalImport: () -> Unit,
    onExport: () -> Unit,
    onClearOffline: () -> Unit,
    onClearCache: () -> Unit
) {
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
                    color = TvFocusGreen,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.focusGroup()
        ) {
            AdaptiveActionIcon(
                icon = Icons.Default.CloudDownload,
                contentDescription = "远程订阅",
                onClick = onRemoteImport,
                testTagName = "btn_header_remote_import",
                isTv = true,
                tint = StudioSub,
                size = 34.dp,
                iconSize = 16.dp
            )
            AdaptiveActionIcon(
                icon = Icons.Default.FileOpen,
                contentDescription = "本地导入",
                onClick = onLocalImport,
                testTagName = "btn_header_local_import",
                isTv = true,
                tint = StudioSub,
                size = 34.dp,
                iconSize = 16.dp
            )
            AdaptiveActionIcon(
                icon = Icons.Default.FileDownload,
                contentDescription = "导出列表",
                onClick = onExport,
                testTagName = "btn_header_export",
                isTv = true,
                tint = StudioSub,
                size = 34.dp,
                iconSize = 16.dp
            )
            AdaptiveActionIcon(
                icon = Icons.Default.CleaningServices,
                contentDescription = "清理已下播",
                onClick = onClearOffline,
                testTagName = "btn_header_clear_offline",
                isTv = true,
                tint = StudioSub,
                size = 34.dp,
                iconSize = 16.dp
            )
            AdaptiveActionIcon(
                icon = Icons.Default.DeleteSweep,
                contentDescription = "清理缓存",
                onClick = onClearCache,
                testTagName = "btn_header_clear_cache",
                isTv = true,
                tint = StudioSub,
                size = 34.dp,
                iconSize = 16.dp
            )
        }
    }
}
