package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.LiveChannel
import com.example.ui.theme.CheckingAmber
import com.example.ui.theme.CheckingAmberBg
import com.example.ui.theme.DouyinRed
import com.example.ui.theme.DouyinRedDim
import com.example.ui.theme.OnceBlue
import com.example.ui.theme.OnceBlueBg
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioCard
import com.example.ui.theme.StudioCardVariant

@Composable
fun ChannelItem(
    channel: LiveChannel,
    isActive: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 呼吸灯动画
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val itemAlpha = if (channel.isLive || !channel.statusKnown) 1.0f else 0.65f

    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (isActive || isFocused) {
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color(0x33FE2C55),
                            Color(0x0DFE2C55)
                        )
                    )
                } else {
                    Brush.horizontalGradient(
                        colors = listOf(StudioCard, StudioCardVariant)
                    )
                }
            )
            .border(
                width = if (isActive || isFocused) 2.dp else 1.dp,
                color = if (isFocused) Color(0xFF00D26A) else if (isActive) DouyinRed else StudioBorder,
                shape = RoundedCornerShape(10.dp)
            )
            .onFocusChanged { isFocused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .alpha(itemAlpha)
            .testTag("channel_item_${channel.rid}")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            // 头像与在线呼吸灯
            Box(
                modifier = Modifier.size(42.dp)
            ) {
                AsyncImage(
                    model = channel.avatar.ifEmpty { "android.resource://com.example/drawable/app_launcher_icon_1793754710559" },
                    contentDescription = channel.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF222228))
                        .border(
                            width = 1.5.dp,
                            color = if (channel.isLive) DouyinRed else Color(0x33FFFFFF),
                            shape = CircleShape
                        )
                )

                // 右下角在线小圆点
                val dotColor = when {
                    channel.isLive -> DouyinRed
                    !channel.statusKnown -> CheckingAmber
                    else -> Color(0xFF555562)
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(11.dp)
                        .scale(if (channel.isLive) pulseScale else 1f)
                        .clip(CircleShape)
                        .background(dotColor)
                        .border(1.5.dp, StudioCard, CircleShape)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // 主播名称与标题
            Column(
                modifier = Modifier.weight(1f)
            ) {
                val displayName = if (channel.name.isNotBlank() && channel.name != channel.rid && !channel.name.matches(Regex("^(?:频道|房间)\\s*\\d{3,22}$"))) {
                    channel.name
                } else if (channel.title.isNotBlank() && !channel.title.matches(Regex("^(?:房间号?|频道)\\s*[:：]?\\s*\\d+.*$"))) {
                    channel.title
                } else {
                    channel.name.ifBlank { "抖音主播" }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = displayName,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 13.5.sp,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    if (channel.isLive) {
                        Text(
                            text = "LIVE",
                            color = DouyinRed,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .background(DouyinRedDim, RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    } else if (!channel.statusKnown) {
                        Text(
                            text = "待核验",
                            color = CheckingAmber,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .background(CheckingAmberBg, RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }

                    if (channel.isOnce) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "一次性",
                            color = OnceBlue,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .background(OnceBlueBg, RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }

                val subText = when {
                    channel.title.isNotEmpty() && !channel.title.matches(Regex("^(?:房间号?|频道)\\s*[:：]?\\s*\\d+.*$")) -> channel.title
                    channel.isLive -> "实况直播中"
                    !channel.statusKnown -> "正在核验开播状态..."
                    else -> "主播暂未开播"
                }
                Text(
                    text = subText,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            // 删除按钮（兼容遥控器绿色焦点环与触控目标）
            var isDeleteFocused by remember { mutableStateOf(false) }
            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .size(44.dp)
                    .onFocusChanged { isDeleteFocused = it.isFocused }
                    .background(if (isDeleteFocused) Color(0x3300D26A) else Color.Transparent, CircleShape)
                    .border(if (isDeleteFocused) 2.dp else 0.dp, Color(0xFF00D26A), CircleShape)
                    .testTag("btn_delete_channel_${channel.rid}")
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "删除频道",
                    tint = if (isDeleteFocused) Color(0xFF00D26A) else Color(0xFF7A7A8C),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
