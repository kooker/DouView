package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.LiveChannel
import com.example.ui.TvSafeArea
import com.example.ui.theme.DouyinRed
import com.example.ui.theme.DouyinRedDim
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioCard
import com.example.ui.theme.StudioCardVariant
import com.example.ui.theme.StudioSub
import kotlinx.coroutines.delay

/**
 * 电视遥控器菜单键（KEYCODE_MENU）左侧弹出在线直播换台抽屉：
 * - 仅筛选展示当前在线 (isLive == true) 的直播频道，避免换台遇到未开播冷场
 * - 全 D-Pad 遥控焦点导航支持（绿色聚焦环 + 自动滚动定位当前频道 + 空列表焦点落刷新按钮）
 * - 支持全屏播放与大屏工作台双模式随时呼出/收起
 */
@Composable
fun TvOnlineChannelDrawer(
    visible: Boolean,
    channels: List<LiveChannel>,
    activeChannel: LiveChannel?,
    isProbing: Boolean,
    isTv: Boolean,
    onSelectChannel: (LiveChannel) -> Unit,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val liveChannels = remember(channels) { channels.filter { it.isLive } }
    val listState = rememberLazyListState()
    val targetFocusRequester = remember { FocusRequester() }
    val emptyRefreshFocusRequester = remember { FocusRequester() }

    LaunchedEffect(visible, liveChannels.size) {
        if (visible) {
            if (liveChannels.isNotEmpty()) {
                val targetIndex = liveChannels.indexOfFirst { it.rid == activeChannel?.rid }
                    .let { if (it >= 0) it else 0 }
                try {
                    listState.scrollToItem(targetIndex)
                } catch (_: Exception) {}
                delay(70L)
                try {
                    targetFocusRequester.requestFocus()
                } catch (_: Exception) {}
            } else {
                delay(70L)
                try {
                    emptyRefreshFocusRequester.requestFocus()
                } catch (_: Exception) {}
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // 半透明遮罩：点击空白区域收起左侧抽屉
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(180))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x88000000))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss
                    )
            )
        }

        // 左侧滑出在线频道面板
        AnimatedVisibility(
            visible = visible,
            enter = slideInHorizontally(
                initialOffsetX = { -it },
                animationSpec = tween(240, easing = FastOutSlowInEasing)
            ) + fadeIn(tween(200)),
            exit = slideOutHorizontally(
                targetOffsetX = { -it },
                animationSpec = tween(200, easing = FastOutSlowInEasing)
            ) + fadeOut(tween(160)),
            modifier = Modifier.align(Alignment.CenterStart)
        ) {
            val safeVertical = TvSafeArea.workbenchVertical(isTv)
            val safeStart = if (isTv) 28.dp else 12.dp

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xF20D0F14),
                border = BorderStroke(1.5.dp, Color(0x33FFFFFF)),
                shadowElevation = 18.dp,
                modifier = Modifier
                    .padding(start = safeStart, top = safeVertical, bottom = safeVertical)
                    .width(if (isTv) 356.dp else 300.dp)
                    .fillMaxHeight()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {} // 拦截穿透点击背景
                    )
                    .testTag("tv_online_channel_drawer")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp)
                        .focusGroup()
                ) {
                    // 顶部标题栏 + 在线数量 + 刷新/关闭按钮
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.LiveTv,
                                contentDescription = null,
                                tint = DouyinRed,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "在线直播换台",
                                    color = Color.White,
                                    fontSize = 15.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${liveChannels.size} 个主播正在直播",
                                    color = TvFocusGreen,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            AdaptiveActionIcon(
                                icon = Icons.Default.Refresh,
                                contentDescription = "刷新在线列表",
                                onClick = onRefresh,
                                testTagName = "tv_drawer_refresh_btn",
                                isTv = true,
                                tint = Color.White,
                                size = 38.dp,
                                iconSize = 18.dp,
                                isLoading = isProbing
                            )
                            AdaptiveActionIcon(
                                icon = Icons.Default.Close,
                                contentDescription = "关闭在线列表",
                                onClick = onDismiss,
                                testTagName = "tv_drawer_close_btn",
                                isTv = true,
                                tint = StudioSub,
                                size = 38.dp,
                                iconSize = 18.dp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Color(0x22FFFFFF))
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // 在线直播列表体
                    if (liveChannels.isEmpty()) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.LiveTv,
                                contentDescription = null,
                                tint = StudioSub,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "当前暂无确认在线的直播",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "请点击下方按钮立即测活全网直播间状态",
                                color = StudioSub,
                                fontSize = 11.5.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            var isBtnFocused by remember { mutableStateOf(false) }
                            Button(
                                onClick = onRefresh,
                                colors = ButtonDefaults.buttonColors(containerColor = DouyinRed),
                                border = if (isBtnFocused) BorderStroke(2.dp, TvFocusGreen) else null,
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier
                                    .focusRequester(emptyRefreshFocusRequester)
                                    .onFocusChanged { isBtnFocused = it.isFocused }
                                    .testTag("tv_drawer_empty_refresh_btn")
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("立即刷新测活", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        val targetIndex = liveChannels.indexOfFirst { it.rid == activeChannel?.rid }
                            .let { if (it >= 0) it else 0 }

                        LazyColumn(
                            state = listState,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(bottom = 8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .focusGroup()
                                .testTag("tv_drawer_live_list")
                        ) {
                            itemsIndexed(liveChannels, key = { _, ch -> ch.rid }) { index, channel ->
                                val isActive = channel.rid == activeChannel?.rid
                                val itemModifier = if (index == targetIndex) {
                                    Modifier.focusRequester(targetFocusRequester)
                                } else {
                                    Modifier
                                }
                                DrawerLiveChannelRow(
                                    index = index,
                                    channel = channel,
                                    isActive = isActive,
                                    onClick = { onSelectChannel(channel) },
                                    modifier = itemModifier
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 底部遥控操作指南
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0x18FFFFFF))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "上下键选台 · OK 键播放 · 右键/菜单/返回关闭",
                            color = Color(0xFFB8B8C8),
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DrawerLiveChannelRow(
    index: Int,
    channel: LiveChannel,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    val borderWidth = when {
        isFocused -> 2.dp
        isActive -> 1.5.dp
        else -> 1.dp
    }
    val borderColor = when {
        isFocused -> TvFocusGreen
        isActive -> DouyinRed
        else -> StudioBorder
    }
    val bgBrush = when {
        isFocused -> Brush.horizontalGradient(listOf(Color(0x3300D26A), Color(0x1100D26A)))
        isActive -> Brush.horizontalGradient(listOf(Color(0x33FE2C55), Color(0x11FE2C55)))
        else -> Brush.horizontalGradient(listOf(StudioCard, StudioCardVariant))
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(bgBrush)
            .border(borderWidth, borderColor, RoundedCornerShape(10.dp))
            .onFocusChanged { isFocused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp)
            .testTag("tv_drawer_item_${channel.rid}")
    ) {
        Box(modifier = Modifier.size(38.dp)) {
            AsyncImage(
                model = channel.avatar.ifEmpty { "android.resource://com.example/drawable/app_launcher_icon_1793754710559" },
                contentDescription = ChannelDisplayFormatter.resolveChannelName(channel),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1E1E26))
                    .border(1.5.dp, if (isFocused) TvFocusGreen else DouyinRed, CircleShape)
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(DouyinRed)
                    .border(1.5.dp, StudioCard, CircleShape)
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = ChannelDisplayFormatter.resolveChannelName(channel),
                    color = if (isFocused) Color.White else MaterialTheme.colorScheme.onSurface,
                    fontSize = 13.5.sp,
                    fontWeight = if (isActive || isFocused) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "LIVE",
                    color = DouyinRed,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .background(DouyinRedDim, RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }
            Text(
                text = ChannelDisplayFormatter.resolveBannerSubText(channel),
                color = if (isFocused) Color(0xFFDADAE6) else StudioSub,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(
                    when {
                        isActive -> DouyinRedDim
                        isFocused -> Color(0x2E00D26A)
                        else -> Color(0x1AFFFFFF)
                    }
                )
                .padding(horizontal = 7.dp, vertical = 3.dp)
        ) {
            Text(
                text = if (isActive) "播放中" else "#${index + 1}",
                color = when {
                    isActive -> DouyinRed
                    isFocused -> TvFocusGreen
                    else -> StudioSub
                },
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
