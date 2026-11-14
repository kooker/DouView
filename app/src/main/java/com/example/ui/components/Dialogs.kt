package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.res.stringResource
import com.example.R
import com.example.data.LiveChannel
import com.example.ui.ActiveDialog
import com.example.ui.ImportMode
import com.example.ui.theme.DouyinRed
import com.example.ui.theme.OnceBlue
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioCard
import com.example.ui.theme.StudioCardVariant
import com.example.ui.theme.StudioText

@Composable
fun AppDialogsHost(
    activeDialog: ActiveDialog?,
    onDismiss: () -> Unit,
    onConfirmDelete: (LiveChannel) -> Unit,
    onRemoteImport: (String, ImportMode) -> Unit,
    onLocalImport: (String, ImportMode) -> Unit = { _, _ -> },
    onExportSelectFormat: (String) -> Unit,
    onToast: (String) -> Unit
) {
    val context = LocalContext.current

    when (activeDialog) {
        is ActiveDialog.OnceExplanation -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = OnceBlue, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("这是一次性临时直播间", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                },
                text = {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        Text(
                            text = "「${activeDialog.name}」已添加并标注为「一次性」。",
                            color = DouyinRed,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "通过【短链接】、【口令】或【15位以上纯数字房间号（如19位）】添加的是本场直播的临时房间。\n\n• 主播下播后该房间即失效，无法再次观看，也不可复用。\n• 下播后清理已结束房间即可保持列表清爽。\n• 如需长期收藏，请改用主播的 PC 网页端 live.douyin.com/房间号 或 15 位以下的长期房间号 (web_rid)。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.5.sp,
                            lineHeight = 18.sp
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = DouyinRed),
                        modifier = Modifier.testTag("dialog_once_ok")
                    ) {
                        Text("我知道了", fontSize = 13.sp)
                    }
                },
                containerColor = StudioCard,
                textContentColor = StudioText,
                shape = RoundedCornerShape(14.dp)
            )
        }

        is ActiveDialog.DeleteConfirm -> {
            val ch = activeDialog.channel
            AlertDialog(
                onDismissRequest = onDismiss,
                title = {
                    Text("确认删除直播频道？", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                },
                text = {
                    Column {
                        Text(
                            text = "即将从本机列表中删除「${ch.name}」（${ch.rid}）。",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (ch.isLive) "当前状态：正在直播中。" else "当前状态：已下播/离线。",
                            color = if (ch.isLive) DouyinRed else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "删除仅影响本机播放列表，不会影响直播间本身。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            onDismiss()
                            onConfirmDelete(ch)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE33150)),
                        modifier = Modifier.testTag("dialog_delete_confirm")
                    ) {
                        Text("确认删除", fontSize = 13.sp)
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("dialog_delete_cancel")
                    ) {
                        Text("取消", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                containerColor = StudioCard,
                textContentColor = StudioText,
                shape = RoundedCornerShape(14.dp)
            )
        }

        is ActiveDialog.RemoteImport -> {
            var url by remember { mutableStateOf("") }
            var importMode by remember { mutableStateOf(ImportMode.MERGE) }

            AlertDialog(
                onDismissRequest = onDismiss,
                title = {
                    Text("远程直播源安全导入", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                },
                text = {
                    Column {
                        Text(
                            text = "输入公开订阅源 URL（支持 JSON、TXT 或 CSV 格式），系统将自动进行合规性核验：",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = url,
                            onValueChange = { url = it },
                            placeholder = { Text("https://example.com/live_list.json", fontSize = 12.sp) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = DouyinRed,
                                unfocusedBorderColor = StudioBorder,
                                focusedContainerColor = StudioCardVariant,
                                unfocusedContainerColor = StudioCardVariant
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_remote_url")
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("导入模式：", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = importMode == ImportMode.MERGE,
                                onClick = { importMode = ImportMode.MERGE },
                                colors = RadioButtonDefaults.colors(selectedColor = DouyinRed)
                            )
                            Text("合并到现有列表", fontSize = 12.sp)
                            Spacer(modifier = Modifier.width(16.dp))
                            RadioButton(
                                selected = importMode == ImportMode.REPLACE,
                                onClick = { importMode = ImportMode.REPLACE },
                                colors = RadioButtonDefaults.colors(selectedColor = DouyinRed)
                            )
                            Text("覆盖替换现有列表", fontSize = 12.sp)
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (url.isNotBlank()) {
                                onRemoteImport(url, importMode)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DouyinRed),
                        modifier = Modifier.testTag("dialog_remote_submit")
                    ) {
                        Text("拉取并导入", fontSize = 13.sp)
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = onDismiss) {
                        Text("取消", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                containerColor = StudioCard,
                shape = RoundedCornerShape(14.dp)
            )
        }

        is ActiveDialog.LocalImport -> {
            var rawText by remember { mutableStateOf("") }
            var importMode by remember { mutableStateOf(ImportMode.MERGE) }

            val fileLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.GetContent()
            ) { uri ->
                uri?.let {
                    try {
                        context.contentResolver.openInputStream(it)?.use { stream ->
                            val buf = java.io.ByteArrayOutputStream()
                            val chunk = ByteArray(8192)
                            var total = 0
                            val maxBytes = 1024 * 1024
                            while (true) {
                                val read = stream.read(chunk)
                                if (read <= 0) break
                                total += read
                                if (total > maxBytes) {
                                    throw IllegalStateException("所选文件超过 1MB 安全上限")
                                }
                                buf.write(chunk, 0, read)
                            }
                            rawText = String(buf.toByteArray(), java.nio.charset.StandardCharsets.UTF_8)
                        }
                    } catch (e: Exception) {
                        onToast("读取文件失败: ${e.message}")
                    }
                }
            }

            AlertDialog(
                onDismissRequest = onDismiss,
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("本地直播源订阅导入", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        OutlinedButton(
                            onClick = {
                                try {
                                    (context as? com.example.MainActivity)?.suppressNextTvBackgroundExit()
                                    fileLauncher.launch("*/*")
                                } catch (_: Exception) {
                                    onToast("当前设备未提供文件选择器，请直接粘贴文本或使用远程订阅")
                                }
                            },
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("浏览文件", fontSize = 11.sp)
                        }
                    }
                },
                text = {
                    Column {
                        Text(
                            text = "选择设备本地文件或直接粘贴 JSON、CSV、TXT 频道列表文本：",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = rawText,
                            onValueChange = { rawText = it },
                            placeholder = { Text("粘贴 JSON 列表，或每行一个房间号 / 房间号,名称", fontSize = 12.sp) },
                            maxLines = 6,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = DouyinRed,
                                unfocusedBorderColor = StudioBorder,
                                focusedContainerColor = StudioCardVariant,
                                unfocusedContainerColor = StudioCardVariant
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 100.dp, max = 150.dp)
                                .testTag("input_local_text")
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("导入模式：", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = importMode == ImportMode.MERGE,
                                onClick = { importMode = ImportMode.MERGE },
                                colors = RadioButtonDefaults.colors(selectedColor = DouyinRed)
                            )
                            Text("合并到现有列表", fontSize = 12.sp)
                            Spacer(modifier = Modifier.width(16.dp))
                            RadioButton(
                                selected = importMode == ImportMode.REPLACE,
                                onClick = { importMode = ImportMode.REPLACE },
                                colors = RadioButtonDefaults.colors(selectedColor = DouyinRed)
                            )
                            Text("覆盖替换现有列表", fontSize = 12.sp)
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (rawText.isNotBlank()) {
                                onLocalImport(rawText, importMode)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DouyinRed),
                        modifier = Modifier.testTag("dialog_local_submit")
                    ) {
                        Text("解析并保存", fontSize = 13.sp)
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = onDismiss) {
                        Text("取消", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                containerColor = StudioCard,
                shape = RoundedCornerShape(14.dp)
            )
        }

        is ActiveDialog.ExportFormat -> {
            var selectedFormat by remember { mutableStateOf("json") }

            AlertDialog(
                onDismissRequest = onDismiss,
                title = {
                    Text("导出直播频道列表", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                },
                text = {
                    Column {
                        Text(
                            text = "请选择导出的格式规范：",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = selectedFormat == "json",
                                onClick = { selectedFormat = "json" },
                                colors = RadioButtonDefaults.colors(selectedColor = DouyinRed)
                            )
                            Text("JSON (完整信息包含头像与名称)", fontSize = 12.5.sp)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = selectedFormat == "txt",
                                onClick = { selectedFormat = "txt" },
                                colors = RadioButtonDefaults.colors(selectedColor = DouyinRed)
                            )
                            Text("TXT (纯房间号，每行一个)", fontSize = 12.5.sp)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = selectedFormat == "csv",
                                onClick = { selectedFormat = "csv" },
                                colors = RadioButtonDefaults.colors(selectedColor = DouyinRed)
                            )
                            Text("CSV (表格格式：房间号,频道名,标题)", fontSize = 12.5.sp)
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            onDismiss()
                            onExportSelectFormat(selectedFormat)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DouyinRed),
                        modifier = Modifier.testTag("dialog_export_format_confirm")
                    ) {
                        Text("生成导出内容", fontSize = 13.sp)
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = onDismiss) {
                        Text("取消", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                containerColor = StudioCard,
                shape = RoundedCornerShape(14.dp)
            )
        }

        is ActiveDialog.ExportResult -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = {
                    Text("导出结果：${activeDialog.fileName}", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                },
                text = {
                    Column {
                        Text(
                            text = "共包含 ${activeDialog.channelCount} 个频道数据。可直接复制到剪贴板，或通过系统分享发送：",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 160.dp)
                                .background(StudioCardVariant, RoundedCornerShape(8.dp))
                                .border(1.dp, StudioBorder, RoundedCornerShape(8.dp))
                                .padding(10.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = activeDialog.content,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                },
                confirmButton = {
                    Row {
                        OutlinedButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("DouyinLiveChannels", activeDialog.content)
                                clipboard.setPrimaryClip(clip)
                                onToast("已复制到剪贴板！")
                            },
                            modifier = Modifier.testTag("btn_copy_export")
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("复制内容", fontSize = 12.sp)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                try {
                                    (context as? com.example.MainActivity)?.suppressNextTvBackgroundExit()
                                    val sendIntent: Intent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        putExtra(Intent.EXTRA_TEXT, activeDialog.content)
                                        type = "text/plain"
                                    }
                                    val shareIntent = Intent.createChooser(sendIntent, "分享导出文件: ${activeDialog.fileName}")
                                    context.startActivity(shareIntent)
                                } catch (_: Exception) {
                                    onToast("当前设备不支持系统分享，请点击「复制内容」")
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = DouyinRed),
                            modifier = Modifier.testTag("btn_share_export")
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("分享", fontSize = 12.sp)
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) {
                        Text("关闭", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                containerColor = StudioCard,
                shape = RoundedCornerShape(14.dp)
            )
        }

        is ActiveDialog.VersionUpdate -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("系统功能要点与版本", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .background(Color(0x2600D26A), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "v${activeDialog.currentVersion} 稳定正式版",
                                fontSize = 10.sp,
                                color = Color(0xFF00D26A),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                },
                text = {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        Text(
                            text = if (activeDialog.isLatest) "您当前使用的已是最新稳定版本。" else "发现新版本：v${activeDialog.latestVersion}",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "核心功能要点：\n${activeDialog.updateLog}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "GitHub 源码与发布地址：\n${activeDialog.downloadUrl}",
                            color = DouyinRed,
                            fontSize = 11.5.sp,
                            lineHeight = 16.sp
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            try {
                                (context as? com.example.MainActivity)?.suppressNextTvBackgroundExit()
                                val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(activeDialog.downloadUrl))
                                context.startActivity(intent)
                            } catch (_: Exception) {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                clipboard?.setPrimaryClip(ClipData.newPlainText("DouViewGitHub", activeDialog.downloadUrl))
                                onToast("未检测到浏览器，已将 GitHub 仓库地址复制到剪贴板")
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DouyinRed),
                        modifier = Modifier.testTag("btn_version_download")
                    ) {
                        Text("前往 GitHub 仓库", fontSize = 13.sp)
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = onDismiss) {
                        Text("关闭", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                containerColor = StudioCard,
                shape = RoundedCornerShape(14.dp)
            )
        }

        is ActiveDialog.UserGuide -> {
            Dialog(onDismissRequest = onDismiss) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = StudioCard,
                    border = androidx.compose.foundation.BorderStroke(1.dp, StudioBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 560.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.user_guide_title), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            }
                            IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp).testTag("btn_guide_close_x")) {
                                Icon(Icons.Default.Close, contentDescription = "关闭", tint = Color.Gray)
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState())
                        ) {
                            GuideSection(
                                title = "✨ 系统核心功能要点",
                                items = listOf(
                                    "息屏与后台防断流：锁屏或退至后台自动切换仅音频模式，降低 95% 带宽，保持音频焦点连续播放不中断。",
                                    "流媒体类型精准识别：重构视频轨侦测引擎，首帧渲染即锁，彻底消除普通视频直播误判为语音直播的问题。",
                                    "仿抖音双指自由缩放：支持以双指中心点丝滑缩放（最高 10x 显微级画质巡检）、双击快捷缩放复位与智能边界回弹吸附。",
                                    "手势与按键防冲突：全屏严格区分左侧切台区、右侧音量区与中央中立区，彻底杜绝误触冲突。",
                                    "多端自适应适配：手机、平板与电视 TV 深度适配，遥控器方向键无缝换台，电视退出彻底释放后台资源。"
                                )
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            GuideSection(
                                title = "📡 频道添加与智能提纯",
                                items = listOf(
                                    "整段手机分享口令：例如 2.33 复制打开抖音... https://v.douyin.com/xxxx/，引擎自动提纯。",
                                    "官方短链接：如 https://v.douyin.com/iABCxyz/。",
                                    "PC 网页版链接：如 https://live.douyin.com/10443341948。",
                                    "纯数字房间号：直接输入主播房间号，如 10443341948、232551178364。"
                                )
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            GuideSection(
                                title = "⚠️ 一次性临时主播机制",
                                items = listOf(
                                    "通过短链接/口令/15位以上纯数字房间号添加的，属于本场直播的临时房间。",
                                    "主播下播后该房间不可复用，列表中会标注「一次性」。",
                                    "点击顶部操作栏的「清理下播」可一键自动清理所有已下播的临时房间。"
                                )
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            GuideSection(
                                title = "⚙️ 播放内核与清晰度自适应",
                                items = listOf(
                                    "HLS（稳定）：基于切片技术，适合各种跨网及弱网环境，原生硬件解码秒开。",
                                    "FLV（低延迟）：延迟更低，适合实时互动观看。",
                                    "自适应全屏：支持竖屏全屏与横屏全屏双模自适应，视频竖屏时全屏自动适配竖屏。",
                                    "优先适配：默认智能匹配设备兼容的最佳清晰度，避免高帧率硬解异常。"
                                )
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            GuideSection(
                                title = "💾 本地存储与多源订阅",
                                items = listOf(
                                    "持久化：采用 Room 本地 SQLite 数据库，离线保存所有关注频道。",
                                    "自动探活：后台自动轮询开播状态与标题，按在线优先、待核验、离线三段平滑重排，不发生跳动。",
                                    "导入与导出：支持导出为 JSON/TXT/CSV 格式，支持本地文件及远程订阅安全导入。"
                                )
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            GuideSection(
                                title = "🌐 开源项目主页",
                                items = listOf(
                                    "GitHub 仓库：https://github.com/kooker/DouView",
                                    "欢迎前往 GitHub 仓库体验最新发行版、提交 Issue 反馈或参与开源共建。"
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = onDismiss,
                            colors = ButtonDefaults.buttonColors(containerColor = DouyinRed),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("btn_close_user_guide")
                        ) {
                            Text("我知道了", fontSize = 13.sp)
                        }
                    }
                }
            }
        }

        null -> {}
    }
}

@Composable
private fun GuideSection(title: String, items: List<String>) {
    Column {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(6.dp))
        items.forEach { item ->
            Row(modifier = Modifier.padding(vertical = 2.dp)) {
                Text("• ", color = DouyinRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = item,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
            }
        }
    }
}
