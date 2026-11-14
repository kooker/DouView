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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.LiveChannel
import com.example.ui.theme.DouyinRed
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioCard
import com.example.ui.theme.StudioCardVariant
import com.example.ui.theme.StudioSub
import com.example.ui.theme.StudioText

internal val TvFocusGreen = Color(0xFF00D26A)

internal object ChannelDisplayFormatter {
    fun resolveBannerName(activeChannel: LiveChannel?): String {
        if (activeChannel == null) return "请选择直播间"
        return if (
            activeChannel.name.isNotBlank() &&
            activeChannel.name != activeChannel.rid &&
            !activeChannel.name.matches(Regex("^(?:频道|房间)\\s*\\d{3,22}$"))
        ) {
            activeChannel.name
        } else if (
            activeChannel.title.isNotBlank() &&
            !activeChannel.title.matches(Regex("^(?:房间号?|频道)\\s*[:：]?\\s*\\d+.*$"))
        ) {
            activeChannel.title
        } else {
            activeChannel.name.ifBlank { "请选择直播间" }
        }
    }

    fun resolveBannerSubText(activeChannel: LiveChannel?): String {
        return when {
            activeChannel == null -> "从下方列表中点击频道开始播放"
            activeChannel.title.isNotEmpty() &&
                !activeChannel.title.matches(Regex("^(?:房间号?|频道)\\s*[:：]?\\s*\\d+.*$")) -> activeChannel.title
            activeChannel.isLive -> "实况直播中"
            !activeChannel.statusKnown -> "正在核验开播状态..."
            else -> "主播暂未开播"
        }
    }
}

/**
 * 统一的双端图标操作按钮：
 * - 移动端 (isTv = false)：纯净无边框触控圆按钮，点击水波纹反馈，绝不绘制 0.dp 细线绿圈
 * - TV 端 (isTv = true)：仅当焦点真正在当前按钮时绘制 2.dp 绿色聚焦环，不重叠相邻控件
 */
@Composable
internal fun AdaptiveActionIcon(
    icon: ImageVector?,
    contentDescription: String,
    onClick: () -> Unit,
    testTagName: String,
    isTv: Boolean,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    size: Dp = 36.dp,
    iconSize: Dp = 18.dp,
    isLoading: Boolean = false
) {
    var isFocused by remember { mutableStateOf(false) }
    val showTvFocus = isTv && isFocused

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .then(
                if (showTvFocus) {
                    Modifier
                        .background(Color(0x3300D26A), CircleShape)
                        .border(2.dp, TvFocusGreen, CircleShape)
                } else {
                    Modifier
                }
            )
            .onFocusChanged { isFocused = it.isFocused }
            .clickable(onClick = onClick)
            .testTag(testTagName)
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                strokeWidth = 2.dp,
                color = DouyinRed,
                modifier = Modifier.size(16.dp)
            )
        } else if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (showTvFocus) TvFocusGreen else tint,
                modifier = Modifier.size(iconSize)
            )
        }
    }
}

@Composable
internal fun SharedVolumeDropdown(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    isMuted: Boolean,
    volume: Float,
    onToggleMute: () -> Unit,
    onVolumeChange: (Float) -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = Modifier
            .background(StudioCard)
            .padding(12.dp)
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

@Composable
internal fun SharedAddChannelBar(
    inputText: String,
    onInputChange: (String) -> Unit,
    onAdd: () -> Unit,
    isBusy: Boolean,
    isTv: Boolean
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (isTv) Modifier.focusGroup() else Modifier)
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

        var isAddBtnFocused by remember { mutableStateOf(false) }
        Button(
            onClick = onAdd,
            enabled = inputText.isNotBlank() && !isBusy,
            colors = ButtonDefaults.buttonColors(containerColor = DouyinRed),
            border = if (isTv && isAddBtnFocused) BorderStroke(2.dp, TvFocusGreen) else null,
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 14.dp),
            modifier = Modifier
                .height(46.dp)
                .onFocusChanged { isAddBtnFocused = it.isFocused }
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
internal fun SharedChannelListView(
    channels: List<LiveChannel>,
    activeChannel: LiveChannel?,
    onSelect: (LiveChannel) -> Unit,
    onDelete: (LiveChannel) -> Unit,
    isTv: Boolean
) {
    if (channels.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "暂无直播频道\n粘贴分享口令或房间号快速添加",
                color = StudioSub,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                textAlign = TextAlign.Center
            )
        }
    } else {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxSize()
                .then(if (isTv) Modifier.focusGroup() else Modifier)
                .testTag("channels_lazy_column")
        ) {
            items(channels, key = { it.rid }) { channel ->
                ChannelItem(
                    channel = channel,
                    isActive = channel.rid == activeChannel?.rid,
                    onClick = { onSelect(channel) },
                    onDelete = { onDelete(channel) },
                    isTv = isTv
                )
            }
        }
    }
}
