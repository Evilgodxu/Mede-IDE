package com.template.jh.screens.home.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.provider.MediaStore
import androidx.compose.foundation.Image
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.derivedStateOf
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.DisposableEffect
import android.os.Handler
import android.os.Looper
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.template.jh.R
import com.template.jh.core.ai.ChatMessage
import com.template.jh.core.ai.ChatRole
import com.template.jh.core.ai.ModelActivity
import com.template.jh.core.ai.AttachedFile
import com.template.jh.core.ai.ConversationEntry
import com.template.jh.core.ai.EngineStatus
import com.template.jh.core.analytics.UsageStats

// AI 协作面板
@Composable
fun AIChatPanel(
    onSettingsClick: () -> Unit = {},
    onNewTaskClick: () -> Unit = {},
    onHistoryClick: () -> Unit = {},
    viewModel: com.template.jh.core.ai.ChatViewModel,
) {
    val state by viewModel.state.collectAsState()
    val listState = rememberLazyListState()

    // 计算最后一条消息的内容长度，用于触发流式滚动
    val lastMessageContentLength by remember(state.messages.size) {
        derivedStateOf {
            state.messages.lastOrNull()?.content?.length ?: 0
        }
    }

    // 自动滚动到最新消息（消息数量变化时触发）
    LaunchedEffect(state.messages.size) {
        if (state.messages.isEmpty()) return@LaunchedEffect
        val lastIndex = state.messages.size - 1
        delay(50)
        if (!isActive) return@LaunchedEffect
        val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
        // 如果用户正在靠近底部（最后可见项在倒数第3以内），自动滚到底
        if (lastVisible >= lastIndex - 2) {
            listState.animateScrollToItem(lastIndex)
        }
    }

    // 流式生成时的平滑滚动（监听内容长度变化）
    LaunchedEffect(lastMessageContentLength, state.isLoading) {
        if (state.messages.isEmpty() || !state.isLoading) return@LaunchedEffect
        val lastIndex = state.messages.size - 1
        delay(100) // 防抖 100ms
        if (!isActive) return@LaunchedEffect // 组合已离开则不滚动
        val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
        // 当用户靠近底部时自动滚动
        if (lastVisible >= lastIndex - 1) {
            listState.animateScrollToItem(lastIndex)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ChatTopBar(
            engineStatus = state.engineStatus,
            conversations = state.conversations,
            isHistoryOpen = state.isHistoryOpen,
            onNewTaskClick = { viewModel.newConversation(); onNewTaskClick() },
            onHistoryClick = { viewModel.toggleHistory() },
            onSettingsClick = onSettingsClick,
            onSwitchConversation = { viewModel.switchConversation(it) },
            onDeleteConversation = { viewModel.deleteConversation(it) },
            onDismissHistory = { viewModel.closeHistory() },
        )

        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        // 模型活动状态指示
        ModelActivityIndicator(
            activity = state.modelActivity,
            detail = state.activityDetail,
            visible = state.isLoading && state.modelActivity != ModelActivity.Idle,
        )

        if (state.messages.isEmpty() && state.engineStatus != EngineStatus.Loading) {
            Box(modifier = Modifier.weight(1f)) { EmptyChatState(state.engineStatus) }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp),
                state = listState, verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.messages, key = { it.id }) { msg ->
                    if (msg.isToolMessage) {
                        ToolCallBubble(message = msg)
                    } else {
                        ChatBubble(
                            message = msg,
                            isActiveStreaming = msg.isStreaming,
                        )
                    }
                }
            }
        }

        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        val contextUsedTokens = remember(state.messages.size, state.messages.lastOrNull()?.content) {
            state.messages.sumOf { it.content.length / 2 }
        }
        val contextMaxTokens = 128000
        var showContextInfoDialog by remember { mutableStateOf(false) }
        val usageStats by viewModel.usageStats.collectAsState()
        var showUsageStatsDialog by remember { mutableStateOf(false) }

        // 图片选择启动器
        val imagePickerLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri: android.net.Uri? ->
            uri?.let { viewModel.attachImage(it) }
        }

        ChatInputBar(
            inputText = state.inputText,
            onInputChange = { viewModel.setInputText(it) },
            onSend = { viewModel.sendMessage() },
            isLoading = state.isLoading,
            isOptimizing = state.isOptimizing,
            engineStatus = state.engineStatus,
            onCancel = { viewModel.cancelGeneration() },
            onOptimize = { viewModel.optimizeInput() },
            attachedImageUris = state.attachedImageUris,
            onDetachImage = { viewModel.detachImage(it) },
            attachedFileRefs = state.attachedFileRefs,
            onDetachFile = { viewModel.detachFile(it) },
            contextUsedTokens = contextUsedTokens,
            contextMaxTokens = contextMaxTokens,
            isContextCompressed = state.isContextCompressed,
            contextCompressedTokens = state.contextCompressedTokens,
            contextCompressedCount = state.contextCompressedCount,
            onContextInfoClick = { showContextInfoDialog = true },
            onImagePick = { imagePickerLauncher.launch("image/*") },
            usageStats = usageStats,
            onUsageStatsClick = { showUsageStatsDialog = true },
        )

        if (showContextInfoDialog) {
            ContextInfoDialog(
                usedTokens = contextUsedTokens,
                maxTokens = contextMaxTokens,
                messagesCount = state.messages.size,
                openedFilePaths = state.openedFilePaths,
                isContextCompressed = state.isContextCompressed,
                contextCompressedTokens = state.contextCompressedTokens,
                contextCompressedCount = state.contextCompressedCount,
                onDismiss = { showContextInfoDialog = false },
            )
        }
        if (showUsageStatsDialog) {
            UsageStatsDialog(
                stats = usageStats,
                onDismiss = { showUsageStatsDialog = false },
                onReset = { viewModel.resetUsageStats() },
            )
        }
    }
}

private fun ModelActivity.displayLabel(): String = when (this) {
    ModelActivity.Idle -> ""
    ModelActivity.Thinking -> "思考中…"
    ModelActivity.ListingFiles -> "正在列出目录"
    ModelActivity.ReadingFile -> "正在读取文件"
    ModelActivity.WritingFile -> "正在写入文件"
    ModelActivity.EditingFile -> "正在修改文件"
    ModelActivity.DeletingFile -> "正在删除文件"
    ModelActivity.CreatingDirectory -> "正在创建目录"
    ModelActivity.SearchingCode -> "正在搜索代码"
    ModelActivity.SearchingWeb -> "正在搜索网络"
    ModelActivity.RunningCommand -> "正在执行命令"
    ModelActivity.GitOperation -> "正在执行 Git 操作"
    ModelActivity.ReadingLints -> "正在检查编译错误"
    ModelActivity.ExecutingTool -> "正在执行操作"
    ModelActivity.ProcessingResult -> "正在处理结果"
}

@Composable
private fun ModelActivityIndicator(
    activity: ModelActivity,
    detail: String,
    visible: Boolean,
) {
    if (!visible) return
    val label = activity.displayLabel()
    if (label.isEmpty()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f))
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(12.dp),
            strokeWidth = 1.5.dp,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = if (detail.isNotBlank()) "$label: $detail" else label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ChatTopBar(
    engineStatus: EngineStatus,
    conversations: List<ConversationEntry>, isHistoryOpen: Boolean,
    onNewTaskClick: () -> Unit, onHistoryClick: () -> Unit, onSettingsClick: () -> Unit,
    onSwitchConversation: (ConversationEntry) -> Unit, onDeleteConversation: (String) -> Unit,
    onDismissHistory: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp).height(36.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        // 左侧标题区域（已移除模型状态圆点，MainTopBar 已包含完整状态）
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Text(
                text = "AI 协作",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            IconButton(onClick = onNewTaskClick, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Add, stringResource(R.string.ai_new_task), Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            // 历史按钮 + 下拉
            Box {
                IconButton(onClick = onHistoryClick, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.History, stringResource(R.string.ai_history), Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                DropdownMenu(expanded = isHistoryOpen, onDismissRequest = onDismissHistory, modifier = Modifier.widthIn(min = 200.dp)) {
                    if (conversations.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text("暂无历史对话", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                            onClick = onDismissHistory, enabled = false,
                        )
                    } else {
                        conversations.forEach { conv ->
                            DropdownMenuItem(
                                text = {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(conv.title, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text("${conv.messages.size} 条消息", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        IconButton(onClick = { onDeleteConversation(conv.id) }, modifier = Modifier.size(24.dp)) {
                                            Icon(Icons.Default.Delete, "删除", Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                                        }
                                    }
                                },
                                onClick = { onSwitchConversation(conv) },
                            )
                        }
                    }
                }
            }
            IconButton(onClick = onSettingsClick, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Settings, stringResource(R.string.ai_ide_settings), Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun EmptyChatState(engineStatus: EngineStatus) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
            Text(stringResource(R.string.ai_collaboration_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(8.dp))
            Text(
                if (engineStatus == EngineStatus.Idle) "请在 IDE 设置 → 模型中加载模型"
                else stringResource(R.string.ai_collaboration_subtitle),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ChatBubble(
    message: ChatMessage,
    isActiveStreaming: Boolean = false,
) {
    val isUser = message.role == ChatRole.User
    val bgColor = if (isUser) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    val shape = RoundedCornerShape(topStart = if (isUser) 12.dp else 4.dp, topEnd = 12.dp, bottomStart = 12.dp, bottomEnd = if (isUser) 4.dp else 12.dp)
    val context = LocalContext.current

    // 提取 [think]...[/think] 块
    val thinkRegex = remember { Regex("""\[think\](.*?)\[/think]""", RegexOption.DOT_MATCHES_ALL) }
    val thinks = remember(message.content) { thinkRegex.findAll(message.content).map { it.groupValues[1].trim() }.toList() }
    val displayContent = remember(message.content) { message.content.replace(thinkRegex, "").trim() }

    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalAlignment = if (isUser) Alignment.End else Alignment.Start) {
        Text(if (isUser) "You" else "AI", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))

        // 可折叠思考块
        var anyThinkExpanded by remember { mutableStateOf(false) }
        if (thinks.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .widthIn(max = 360.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    .clickable { anyThinkExpanded = !anyThinkExpanded }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("思考", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(4.dp))
                        Text(
                            if (anyThinkExpanded) "▼" else "▶",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (anyThinkExpanded) {
                        Spacer(Modifier.height(4.dp))
                        thinks.forEachIndexed { i, thinkContent ->
                            if (i > 0) Spacer(Modifier.height(6.dp))
                            Text(thinkContent, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                        }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }

        // 内容气泡
        if (displayContent.isNotEmpty()) {
            Box(
                Modifier
                    .widthIn(max = 360.dp)
                    .clip(shape)
                    .background(bgColor)
                    .combinedClickable(
                        onClick = {},
                        onLongClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("消息内容", message.content))
                        },
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(displayContent, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f, fill = false))
                    // 流式输出闪烁光标
                    if (isActiveStreaming) {
                        StreamingCursor()
                    }
                }
            }
        }
    }
}

/**
 * 工具调用气泡：显示模型推理文本 + 工具名 + 可折叠的工具结果
 */
@Composable
private fun ToolCallBubble(message: ChatMessage) {
    val context = LocalContext.current
    val (prefixText, toolName, toolResult) = remember(message.content) { parseToolMessage(message.content) }
    var showResult by remember { mutableStateOf(false) }

    val shape = RoundedCornerShape(topStart = 4.dp, topEnd = 12.dp, bottomStart = 12.dp, bottomEnd = 12.dp)
    val bgColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)

    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalAlignment = Alignment.Start) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)) {
            Text("AI", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(4.dp))
            Box(
                Modifier
                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 1.dp)
            ) {
                Text("🛠 工具调用", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }

        if (prefixText.isNotBlank()) {
            Box(
                Modifier
                    .widthIn(max = 360.dp)
                    .clip(shape)
                    .background(bgColor)
                    .combinedClickable(
                        onClick = {},
                        onLongClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("消息内容", message.content))
                        },
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(prefixText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
            }
        }

        if (toolName.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Box(
                Modifier
                    .widthIn(max = 360.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f))
                    .clickable { showResult = !showResult }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("⚡ $toolName", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                    Spacer(Modifier.width(4.dp))
                    Text(if (showResult) "▼" else "▶", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                    if (!showResult) {
                        Text(" 结果", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.6f))
                    }
                }
            }

            if (showResult && toolResult.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Box(
                    Modifier
                        .widthIn(max = 360.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    val truncated = if (toolResult.length > 500) toolResult.take(500) + "\n..." else toolResult
                    Text(truncated, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/**
 * 解析工具消息内容，分离前缀文本、工具名、工具结果。
 */
private fun parseToolMessage(content: String): Triple<String, String, String> {
    val jsonStart = content.indexOf("""{"tool_name":""")
    if (jsonStart < 0) {
        val markerIdx = content.indexOf("[工具调用:")
        if (markerIdx >= 0) {
            val afterMarker = content.substring(markerIdx + "[工具调用:".length).trimStart()
            val endIdx = afterMarker.indexOf(']')
            val name = if (endIdx >= 0) afterMarker.substring(0, endIdx).trim() else ""
            val resultPart = if (endIdx >= 0) afterMarker.substring(endIdx + 1).trim() else content
            return Triple("", name, resultPart.removePrefix("\n").removePrefix("\n"))
        }
        return Triple("", "", "")
    }

    val prefix = content.substring(0, jsonStart).trim()
    val afterJsonStart = content.substring(jsonStart)

    var depth = 0
    var jsonEnd = -1
    for (i in afterJsonStart.indices) {
        when (afterJsonStart[i]) {
            '{' -> depth++
            '}' -> { depth--; if (depth == 0) { jsonEnd = jsonStart + i; break } }
        }
    }
    if (jsonEnd < 0) return Triple(prefix, "", "")

    val jsonPart = content.substring(jsonStart, jsonEnd + 1)
    val name = try {
        org.json.JSONObject(jsonPart).optString("tool_name", "")
    } catch (_: Exception) { "" }

    val resultPart = content.substring(jsonEnd + 1).trim().removePrefix("\n").removePrefix("\n")

    return Triple(prefix, name, resultPart)
}

@Composable
private fun StreamingCursor() {
    val infiniteTransition = rememberInfiniteTransition(label = "streaming")
    val visible by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(500),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "cursorBlink",
    )
    Text(
        text = "▍",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.primary.copy(alpha = visible),
    )
}

@Composable
private fun ChatInputBar(
    inputText: String, onInputChange: (String) -> Unit, onSend: () -> Unit,
    isLoading: Boolean, isOptimizing: Boolean, engineStatus: EngineStatus,
    onCancel: () -> Unit, onOptimize: () -> Unit,
    contextUsedTokens: Int = 0, contextMaxTokens: Int = 128000,
    isContextCompressed: Boolean = false,
    contextCompressedTokens: Int = 0,
    contextCompressedCount: Int = 0,
    onContextInfoClick: () -> Unit = {},
    onImagePick: () -> Unit = {},
    attachedImageUris: List<android.net.Uri> = emptyList(),
    onDetachImage: (android.net.Uri) -> Unit = {},
    attachedFileRefs: List<AttachedFile> = emptyList(),
    onDetachFile: (Int) -> Unit = {},
    usageStats: UsageStats = UsageStats(),
    onUsageStatsClick: () -> Unit = {},
) {
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        OutlinedTextField(
            value = inputText, onValueChange = onInputChange,
            placeholder = { Text(stringResource(R.string.chat_with_agent), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline) },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, focusedBorderColor = MaterialTheme.colorScheme.outlineVariant, unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            textStyle = MaterialTheme.typography.bodySmall, maxLines = 3, enabled = engineStatus == EngineStatus.Ready,
        )

        // 文件附件芯片
        if (attachedFileRefs.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                attachedFileRefs.forEachIndexed { index, file ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 8.dp, end = 2.dp, top = 2.dp, bottom = 2.dp)
                        ) {
                            Text("📄 ${file.name}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 160.dp))
                            IconButton(onClick = { onDetachFile(index) }, Modifier.size(20.dp)) {
                                Icon(Icons.Default.Close, "移除", Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                        }
                    }
                }
            }
        }

        // 图片缩略图预览区
        if (attachedImageUris.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                attachedImageUris.forEach { uri ->
                    Box(modifier = Modifier.size(56.dp)) {
                        // 加载缩略图
                        val thumbnail = remember(uri) { loadThumbnail(context, uri) }
                        thumbnail?.let { bmp ->
                            Image(bitmap = bmp.asImageBitmap(), contentDescription = null, modifier = Modifier.size(56.dp).clip(RoundedCornerShape(6.dp)),
                                contentScale = ContentScale.Crop)
                        } ?: Box(Modifier.size(56.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp)))
                        // 删除按钮
                        IconButton(
                            onClick = { onDetachImage(uri) },
                            modifier = Modifier.align(Alignment.TopEnd).size(18.dp).background(Color.Black.copy(alpha = 0.5f), CircleShape),
                        ) {
                            Icon(Icons.Default.Close, "移除", Modifier.size(12.dp), tint = Color.White)
                        }
                    }
                }
            }
        }

        // 工具栏：居中显示所有按钮
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            // 优化按钮
            if (engineStatus == EngineStatus.Ready) {
                if (isOptimizing) {
                    IconButton(onClick = {}, Modifier.size(28.dp), enabled = false) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                    }
                } else {
                    IconButton(
                        onClick = onOptimize,
                        Modifier.size(28.dp),
                        enabled = inputText.isNotBlank() && !isLoading
                    ) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            stringResource(R.string.ai_optimize_input),
                            Modifier.size(16.dp),
                            tint = if (inputText.isNotBlank() && !isLoading) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                            }
                        )
                    }
                }
            }

            // 添加图片按钮
            if (engineStatus == EngineStatus.Ready) {
                IconButton(onClick = onImagePick, Modifier.size(28.dp)) {
                    Icon(Icons.Default.Image, "添加图片", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }

            Spacer(Modifier.width(4.dp))

            // 语音输入按钮
            VoiceInputButton(
                onTextRecognized = { recognizedText ->
                    onInputChange(inputText + recognizedText)
                },
                enabled = engineStatus == EngineStatus.Ready && !isLoading
            )

            // 上下文窗口进度按钮
            val ratio = if (contextMaxTokens > 0) (contextUsedTokens.toFloat() / contextMaxTokens).coerceIn(0f, 1f) else 0f
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .clickable { onContextInfoClick() },
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    progress = { ratio },
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 1.5.dp,
                    color = if (isContextCompressed) Color(0xFF7B1FA2) // 紫色表示已压缩
                        else if (ratio < 0.5f) Color(0xFF4CAF50)
                        else if (ratio < 0.8f) Color(0xFFFFA000)
                        else Color(0xFFE53935),
                    trackColor = Color(0xFFE0E0E0),
                )
                // 已压缩标记
                if (isContextCompressed) {
                    Text("C", style = androidx.compose.material3.MaterialTheme.typography.labelSmall.copy(fontSize = 7.sp),
                        color = Color(0xFF7B1FA2))
                }
            }

            // 用量统计按钮
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onUsageStatsClick() },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "${usageStats.todayCalls}",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (isLoading) {
                IconButton(onClick = onCancel, Modifier.size(36.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.5.dp, color = MaterialTheme.colorScheme.primary)
                        Icon(Icons.Default.Pause, stringResource(R.string.chat_cancel), Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                }
            } else {
                val canSend = (inputText.isNotBlank() || attachedImageUris.isNotEmpty() || attachedFileRefs.isNotEmpty()) && engineStatus == EngineStatus.Ready
                IconButton(onClick = onSend, Modifier.size(32.dp), enabled = canSend) {
                    Icon(Icons.AutoMirrored.Filled.Send, stringResource(R.string.ai_send_message), Modifier.size(20.dp), tint = if (canSend) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                }
            }
        }
    }
}

// 从 content URI 加载缩略图（Compose 中缓存）
private fun loadThumbnail(context: Context, uri: android.net.Uri): Bitmap? = try {
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
        val size = android.util.Size(200, 200)
        context.contentResolver.loadThumbnail(uri, size, null)
    } else {
        @Suppress("DEPRECATION")
        MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
    }
} catch (_: Exception) { null }

// 语音输入按钮组件 - 使用 SpeechRecognizer，支持连续识别
@Composable
private fun VoiceInputButton(
    onTextRecognized: (String) -> Unit,
    enabled: Boolean = true,
) {
    val context = LocalContext.current
    var isListening by remember { mutableStateOf(false) }
    var hasPermission by remember { mutableStateOf(false) }
    val recognizerManager = remember { VoiceRecognizerManager(context) }

    LaunchedEffect(Unit) {
        hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.RECORD_AUDIO
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    // 清理
    DisposableEffect(Unit) {
        onDispose { recognizerManager.destroy() }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        hasPermission = isGranted
        if (isGranted) {
            recognizerManager.start(isListeningState = { isListening = it }, onTextRecognized = onTextRecognized)
        }
    }

    IconButton(
        onClick = {
            if (isListening) {
                recognizerManager.stop()
                isListening = false
            } else if (!hasPermission) {
                permissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
            } else {
                recognizerManager.start(isListeningState = { isListening = it }, onTextRecognized = onTextRecognized)
            }
        },
        modifier = Modifier.size(28.dp),
        enabled = enabled,
    ) {
        if (isListening) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = Color(0xFFE53935),
            )
        } else {
            Icon(
                imageVector = if (hasPermission) Icons.Default.Mic else Icons.Default.MicOff,
                contentDescription = "语音输入",
                modifier = Modifier.size(18.dp),
                tint = if (enabled) {
                    if (hasPermission) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                },
            )
        }
    }
}

/**
 * 语音识别管理器 - 支持连续识别、自动重连、静音超时控制
 *
 * 修复问题：
 * 1. 设置静音超时延长参数（完整3000ms / 可能完成2000ms）
 * 2. API 34+ 启用分段会话（SEGMENTED_SESSION）
 * 3. ERROR_SPEECH_TIMEOUT / ERROR_NO_MATCH 自动重启
 * 4. onPartialResults 实时显示中间结果
 */
private class VoiceRecognizerManager(private val context: Context) {
    private var recognizer: android.speech.SpeechRecognizer? = null
    private var isActive = false
    private var accumulatedText = ""
    private var onResultCallback: ((String) -> Unit)? = null
    private var onListeningCallback: ((Boolean) -> Unit)? = null
    private val handler = Handler(Looper.getMainLooper())

    fun start(
        isListeningState: (Boolean) -> Unit,
        onTextRecognized: (String) -> Unit,
    ) {
        destroy()
        isActive = true
        accumulatedText = ""
        onResultCallback = onTextRecognized
        onListeningCallback = isListeningState
        startRecognizer()
    }

    fun stop() {
        isActive = false
        recognizer?.stopListening()
        recognizer?.destroy()
        recognizer = null
        onListeningCallback?.invoke(false)
        if (accumulatedText.isNotBlank()) {
            onResultCallback?.invoke(accumulatedText)
            accumulatedText = ""
        }
    }

    fun destroy() {
        isActive = false
        handler.removeCallbacksAndMessages(null)
        recognizer?.destroy()
        recognizer = null
    }

    private fun startRecognizer() {
        if (!isActive) return
        try {
            val r = android.speech.SpeechRecognizer.createSpeechRecognizer(context)
            recognizer = r
            r.setRecognitionListener(object : android.speech.RecognitionListener {
                override fun onReadyForSpeech(params: android.os.Bundle?) {
                    onListeningCallback?.invoke(true)
                }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    onListeningCallback?.invoke(false)
                }
                override fun onError(error: Int) {
                    android.util.Log.d("VoiceRecognizer", "onError: $error")
                    onListeningCallback?.invoke(false)
                    // ERROR_SPEECH_TIMEOUT (6) / ERROR_NO_MATCH (7): 静默重启
                    if (isActive && (error == android.speech.SpeechRecognizer.ERROR_SPEECH_TIMEOUT || error == android.speech.SpeechRecognizer.ERROR_NO_MATCH)) {
                        handler.postDelayed({ startRecognizer() }, 300)
                    } else {
                        r.destroy()
                        if (recognizer == r) recognizer = null
                    }
                }
                override fun onResults(results: android.os.Bundle?) {
                    val matches = results?.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull()
                    if (text != null && text.isNotBlank()) {
                        accumulatedText = if (accumulatedText.isBlank()) text else "$accumulatedText$text"
                        onResultCallback?.invoke(accumulatedText)
                        accumulatedText = ""
                    }
                    // 连续识别模式：不销毁，等待分段结果
                    if (isActive) {
                        onListeningCallback?.invoke(true)
                    } else {
                        r.destroy()
                        if (recognizer == r) recognizer = null
                    }
                }
                override fun onPartialResults(partialResults: android.os.Bundle?) {
                    val matches = partialResults?.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull()
                    if (text != null && text.isNotBlank()) {
                        onResultCallback?.invoke(text)
                    }
                }
                override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
            })
            val intent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, java.util.Locale.getDefault())
                putExtra(android.speech.RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(android.speech.RecognizerIntent.EXTRA_MAX_RESULTS, 5)
                // 延长静音超时（毫秒）
                putExtra(android.speech.RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
                putExtra(android.speech.RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
                // API 34+ 分段会话
                if (android.os.Build.VERSION.SDK_INT >= 34) {
                    putExtra("android.speech.extra.SEGMENTED_SESSION", true)
                }
            }
            r.startListening(intent)
        } catch (e: Exception) {
            onListeningCallback?.invoke(false)
            android.widget.Toast.makeText(context, "语音识别不可用: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
}


// 用量统计对话框
@Composable
private fun UsageStatsDialog(
    stats: UsageStats,
    onDismiss: () -> Unit,
    onReset: () -> Unit,
) {
    val avgDuration = if (stats.totalCalls > 0) stats.totalDurationMs / stats.totalCalls else 0L
    var showResetConfirm by remember { mutableStateOf(false) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("用量统计") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // 汇总
                Text("今日调用: ${stats.todayCalls}", style = MaterialTheme.typography.bodySmall)
                Text("今日 Token: ${stats.todayTokens}", style = MaterialTheme.typography.bodySmall)
                HorizontalDivider()
                Text("累计调用: ${stats.totalCalls}", style = MaterialTheme.typography.bodySmall)
                Text("累计输入 Token: ${stats.totalPromptTokens}", style = MaterialTheme.typography.bodySmall)
                Text("累计输出 Token: ${stats.totalCompletionTokens}", style = MaterialTheme.typography.bodySmall)
                Text("总计 Token: ${stats.totalPromptTokens + stats.totalCompletionTokens}", style = MaterialTheme.typography.bodySmall)
                Text("平均耗时: ${avgDuration}ms", style = MaterialTheme.typography.bodySmall)
                HorizontalDivider()
                // 按模型统计
                if (stats.byModel.isNotEmpty()) {
                    Text("按模型:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    stats.byModel.forEach { (key, mStats) ->
                        val (provider, modelName) = key.split("/", limit = 2).let { it[0] to it.getOrElse(1) { "" } }
                        Column(modifier = Modifier.padding(start = 8.dp)) {
                            Text("$modelName", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                            Text("  来源: $provider | 调用 ${mStats.calls} 次, Token ${mStats.promptTokens + mStats.completionTokens}, ${mStats.durationMs / 1000}s", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = {
                if (showResetConfirm) {
                    onReset()
                    showResetConfirm = false
                } else {
                    showResetConfirm = true
                }
            }) {
                Text(if (showResetConfirm) "确认重置" else "重置统计")
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = {
                showResetConfirm = false
                onDismiss()
            }) {
                Text(if (showResetConfirm) "取消" else "关闭")
            }
        },
    )
}

// 上下文窗口详情对话框
@Composable
private fun ContextInfoDialog(
    usedTokens: Int,
    maxTokens: Int,
    messagesCount: Int,
    openedFilePaths: List<String>,
    isContextCompressed: Boolean = false,
    contextCompressedTokens: Int = 0,
    contextCompressedCount: Int = 0,
    onDismiss: () -> Unit,
) {
    val ratio = if (maxTokens > 0) (usedTokens.toFloat() / maxTokens * 100).coerceIn(0f, 100f) else 0f
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .widthIn(min = 280.dp, max = 360.dp)
                .heightIn(max = 400.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("上下文窗口", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            progress = { ratio / 100f },
                            modifier = Modifier.size(60.dp),
                            strokeWidth = 4.dp,
                            color = when {
                                ratio < 50f -> Color(0xFF4CAF50)
                                ratio < 80f -> Color(0xFFFFA000)
                                else -> Color(0xFFE53935)
                            },
                            trackColor = Color(0xFFE0E0E0),
                        )
                        Text(
                            text = "${ratio.toInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                HorizontalDivider()
                Text("Token 用量", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)
                Text(
                    text = "$usedTokens / $maxTokens",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "对话框消息: $messagesCount 条",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (isContextCompressed) {
                    Spacer(Modifier.height(4.dp))
                    Text("上下文压缩", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFF7B1FA2))
                    Text(
                        text = "已压缩 $contextCompressedCount 次，累计移除 ${contextCompressedTokens / 1000}k+ token",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (openedFilePaths.isNotEmpty()) {
                    HorizontalDivider()
                    Text("已打开文件", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)
                    openedFilePaths.take(5).forEach { path ->
                        Text(
                            text = path,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (openedFilePaths.size > 5) Text(
                        text = "... 还有 ${openedFilePaths.size - 5} 个文件",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text("关闭")
                    }
                }
            }
        }
    }
}


