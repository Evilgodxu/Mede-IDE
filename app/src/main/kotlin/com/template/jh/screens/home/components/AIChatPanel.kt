package com.template.jh.screens.home.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
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
import kotlinx.coroutines.delay
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.template.jh.core.ai.ConversationEntry
import com.template.jh.core.ai.EngineStatus

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
        val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
        // 如果用户正在靠近底部（最后可见项在倒数第3以内），自动滚到底
        if (lastVisible >= lastIndex - 2) {
            try {
                listState.animateScrollToItem(lastIndex)
            } catch (e: Exception) {
                Log.e("AIChatPanel", "Scroll error: ${e.message}")
                // 复制崩溃信息到剪贴板用于调试
                val clipboard = (viewModel.getApplication<android.app.Application>().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                clipboard.setPrimaryClip(ClipData.newPlainText("Scroll Error", "AIChatPanel scroll error: ${e.message}\n${e.stackTraceToString()}"))
            }
        }
    }

    // 流式生成时的平滑滚动（监听内容长度变化）
    LaunchedEffect(lastMessageContentLength, state.isLoading) {
        if (state.messages.isEmpty() || !state.isLoading) return@LaunchedEffect
        val lastIndex = state.messages.size - 1
        delay(100) // 防抖 100ms
        val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
        // 当用户靠近底部时自动滚动
        if (lastVisible >= lastIndex - 1) {
            try {
                listState.animateScrollToItem(lastIndex)
            } catch (e: Exception) {
                Log.e("AIChatPanel", "Streaming scroll error: ${e.message}")
                val clipboard = (viewModel.getApplication<android.app.Application>().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                clipboard.setPrimaryClip(ClipData.newPlainText("Streaming Scroll Error", "AIChatPanel streaming scroll error: ${e.message}\n${e.stackTraceToString()}"))
            }
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

        // 任务清单面板（始终可打开）
        if (state.isTaskListOpen) {
            TaskListPanel(
                tasks = state.taskList,
                fileChanges = state.fileChanges,
                onClearCompleted = { viewModel.clearCompletedTasks() },
                onDismiss = { viewModel.toggleTaskList() },
            )
        }

        if (state.messages.isEmpty() && state.engineStatus != EngineStatus.Loading) {
            Box(modifier = Modifier.weight(1f)) { EmptyChatState(state.engineStatus) }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp),
                state = listState, verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.messages, key = { it.id }) { msg ->
                    ChatBubble(
                        message = msg,
                        onFileCardClick = { path -> viewModel.requestOpenFile(path) },
                        onAcceptAllChanges = { path -> viewModel.acceptAllChanges(path) },
                        onRejectAllChanges = { path -> viewModel.rejectAllChanges(path) },
                    )
                }
            }
        }

        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        val contextUsedTokens = remember(state.messages.size, state.messages.lastOrNull()?.content) {
            state.messages.sumOf { it.content.length / 2 }
        }
        val contextMaxTokens = 128000
        var showContextInfoDialog by remember { mutableStateOf(false) }

        ChatInputBar(
            inputText = state.inputText,
            onInputChange = { viewModel.setInputText(it) },
            onSend = { viewModel.sendMessage() },
            isLoading = state.isLoading,
            isOptimizing = state.isOptimizing,
            engineStatus = state.engineStatus,
            onCancel = { viewModel.cancelGeneration() },
            onOptimize = { viewModel.optimizeInput() },
            contextUsedTokens = contextUsedTokens,
            contextMaxTokens = contextMaxTokens,
            onContextInfoClick = { showContextInfoDialog = true },
        )

        if (showContextInfoDialog) {
            ContextInfoDialog(
                usedTokens = contextUsedTokens,
                maxTokens = contextMaxTokens,
                messagesCount = state.messages.size,
                openedFilePaths = state.openedFilePaths,
                onDismiss = { showContextInfoDialog = false },
            )
        }
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
    onFileCardClick: ((String) -> Unit)? = null,
    onAcceptAllChanges: ((String) -> Unit)? = null,
    onRejectAllChanges: ((String) -> Unit)? = null,
) {
    // 文件操作卡片
    message.fileOp?.let { op ->
        FileOperationCard(
            op = op,
            onFileCardClick = onFileCardClick,
            onAcceptAll = { onAcceptAllChanges?.invoke(op.filePath) },
            onRejectAll = { onRejectAllChanges?.invoke(op.filePath) }
        )
        return
    }

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
        thinks.forEach { thinkContent ->
            var expanded by remember { mutableStateOf(false) }
            Column(
                modifier = Modifier
                    .widthIn(max = 200.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("思考", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        if (expanded) "▼" else "▶",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (expanded) {
                    Spacer(Modifier.height(4.dp))
                    Text(thinkContent, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                }
            }
            Spacer(Modifier.height(4.dp))
        }

        // 内容气泡
        if (displayContent.isNotEmpty()) {
            Box(
                Modifier
                    .widthIn(max = 200.dp)
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
                Text(displayContent, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

// 文件操作卡片 - 新样式：左文件名、中路径、右新增/删除行数 + 确认/拒绝按钮
@Composable
private fun FileOperationCard(
    op: com.template.jh.core.ai.FileOperationMeta,
    onFileCardClick: ((String) -> Unit)?,
    onAcceptAll: (() -> Unit)? = null,
    onRejectAll: (() -> Unit)? = null,
) {
    // 提取文件名和路径
    val fileName = op.filePath.substringAfterLast("/")
    val dirPath = op.filePath.substringBeforeLast("/", "")

    // 解析新增/删除行数
    val addedLines = if (op.lineChanges > 0) op.lineChanges else 0
    val deletedLines = if (op.lineChanges < 0) -op.lineChanges else 0

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp, horizontal = 4.dp)
            .clickable { onFileCardClick?.invoke(op.filePath) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // 左侧：文件名
            Text(
                text = fileName,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            // 中间：文件路径（自动省略）
            if (dirPath.isNotEmpty()) {
                Text(
                    text = dirPath,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }

            // 右侧：新增/删除行数
            if (addedLines > 0) {
                Text(
                    text = "+$addedLines",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                    color = Color(0xFF22CC22),
                )
            }
            if (deletedLines > 0) {
                Text(
                    text = "-$deletedLines",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                    color = Color(0xFFCC2222),
                )
            }

            // 确认按钮（√）
            IconButton(
                onClick = { onAcceptAll?.invoke() },
                modifier = Modifier.size(24.dp),
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = Color(0xFF22CC22).copy(alpha = 0.15f)
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "接受所有修改",
                    modifier = Modifier.size(14.dp),
                    tint = Color(0xFF22CC22)
                )
            }

            // 拒绝按钮（×）
            IconButton(
                onClick = { onRejectAll?.invoke() },
                modifier = Modifier.size(24.dp),
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = Color(0xFFCC2222).copy(alpha = 0.15f)
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "拒绝所有修改",
                    modifier = Modifier.size(14.dp),
                    tint = Color(0xFFCC2222)
                )
            }
        }
    }
}

@Composable
private fun ChatInputBar(
    inputText: String, onInputChange: (String) -> Unit, onSend: () -> Unit,
    isLoading: Boolean, isOptimizing: Boolean, engineStatus: EngineStatus,
    onCancel: () -> Unit, onOptimize: () -> Unit,
    contextUsedTokens: Int = 0, contextMaxTokens: Int = 128000,
    onContextInfoClick: () -> Unit = {},
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        OutlinedTextField(
            value = inputText, onValueChange = onInputChange,
            placeholder = { Text(stringResource(R.string.chat_with_agent), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline) },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, focusedBorderColor = MaterialTheme.colorScheme.outlineVariant, unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            textStyle = MaterialTheme.typography.bodySmall, maxLines = 3, enabled = engineStatus == EngineStatus.Ready,
        )

        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                // 优化按钮 - 常驻显示，无内容时禁用
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
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
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
                        color = if (ratio < 0.5f) Color(0xFF4CAF50)
                            else if (ratio < 0.8f) Color(0xFFFFA000)
                            else Color(0xFFE53935),
                        trackColor = Color(0xFFE0E0E0),
                    )
                }
                if (isLoading) {
                    // 生成中：暂停按钮 + 环绕加载动画（不依赖 engineStatus，兼容云端模型）
                    IconButton(onClick = onCancel, Modifier.size(36.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.5.dp, color = MaterialTheme.colorScheme.primary)
                            Icon(Icons.Default.Pause, stringResource(R.string.chat_cancel), Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                } else {
                    IconButton(onClick = onSend, Modifier.size(32.dp), enabled = inputText.isNotBlank() && engineStatus == EngineStatus.Ready) {
                        Icon(Icons.AutoMirrored.Filled.Send, stringResource(R.string.ai_send_message), Modifier.size(20.dp), tint = if (inputText.isNotBlank() && engineStatus == EngineStatus.Ready) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                    }
                }
            }
        }
    }
}

// 任务清单面板（双Tab：任务列表 + 文件列表）
@Composable
private fun TaskListPanel(
    tasks: List<com.template.jh.data.model.TaskItem>,
    fileChanges: List<com.template.jh.core.ai.FileChangeItem>,
    onClearCompleted: () -> Unit,
    onDismiss: () -> Unit,
) {
    var activeTab by remember { mutableStateOf(0) } // 0=任务, 1=文件

    Card(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
    ) {
        Column(Modifier.padding(8.dp)) {
            // 标题栏 + Tab切换
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "任务",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (activeTab == 0) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (activeTab == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clickable { activeTab = 0 }.padding(horizontal = 4.dp),
                    )
                    Text(
                        "|",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                    Text(
                        "文件 (${fileChanges.size})",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (activeTab == 1) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (activeTab == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clickable { activeTab = 1 }.padding(horizontal = 4.dp),
                    )
                }
                Row {
                    if (activeTab == 0) {
                        IconButton(onClick = onClearCompleted, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Delete, "清除已完成", Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, "关闭", Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

            // 内容区
            if (activeTab == 0) {
                // 任务列表
                if (tasks.isEmpty()) {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        Text("暂无任务", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    tasks.takeLast(15).reversed().forEach { task ->
                        TaskRow(task)
                    }
                }
            } else {
                // 文件列表
                if (fileChanges.isEmpty()) {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        Text("暂无文件变更", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    fileChanges.reversed().forEach { change ->
                        FileChangeRow(change)
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskRow(task: com.template.jh.data.model.TaskItem) {
    val statusColor = when (task.status) {
        com.template.jh.data.model.TaskStatus.Completed -> Color(0xFF4CAF50)
        com.template.jh.data.model.TaskStatus.Failed -> MaterialTheme.colorScheme.error
        com.template.jh.data.model.TaskStatus.Running -> MaterialTheme.colorScheme.primary
        com.template.jh.data.model.TaskStatus.WaitingAuth -> Color(0xFFFFA726)
        com.template.jh.data.model.TaskStatus.Interrupted -> Color(0xFFFF5722)
        com.template.jh.data.model.TaskStatus.Pending -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val statusText = when (task.status) {
        com.template.jh.data.model.TaskStatus.Completed -> "✓"
        com.template.jh.data.model.TaskStatus.Failed -> "✗"
        com.template.jh.data.model.TaskStatus.Running -> "⟳"
        com.template.jh.data.model.TaskStatus.WaitingAuth -> "!"
        com.template.jh.data.model.TaskStatus.Interrupted -> "⊘"
        com.template.jh.data.model.TaskStatus.Pending -> "○"
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(statusText, color = statusColor, style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.width(6.dp))
        Text(
            task.title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

// 上下文窗口详情对话框
@Composable
private fun ContextInfoDialog(
    usedTokens: Int,
    maxTokens: Int,
    messagesCount: Int,
    openedFilePaths: List<String>,
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

@Composable
private fun FileChangeRow(change: com.template.jh.core.ai.FileChangeItem) {
    val icon = when (change.opType) {
        com.template.jh.core.ai.FileOpType.Create -> Icons.Default.Add
        com.template.jh.core.ai.FileOpType.Modify -> Icons.Default.Refresh
        com.template.jh.core.ai.FileOpType.Overwrite -> Icons.Default.Refresh
        com.template.jh.core.ai.FileOpType.Delete -> Icons.Default.Delete
    }
    val opLabel = when (change.opType) {
        com.template.jh.core.ai.FileOpType.Create -> "创建"
        com.template.jh.core.ai.FileOpType.Modify -> "修改"
        com.template.jh.core.ai.FileOpType.Overwrite -> "覆盖"
        com.template.jh.core.ai.FileOpType.Delete -> "删除"
    }
    val tint = when (change.opType) {
        com.template.jh.core.ai.FileOpType.Create -> Color(0xFF4CAF50)
        com.template.jh.core.ai.FileOpType.Modify -> MaterialTheme.colorScheme.primary
        com.template.jh.core.ai.FileOpType.Overwrite -> Color(0xFFFF9800)
        com.template.jh.core.ai.FileOpType.Delete -> MaterialTheme.colorScheme.error
    }
    val lineInfo = if (change.lineChanges != 0) {
        val diff = if (change.lineChanges > 0) "+${change.lineChanges}" else "${change.lineChanges}"
        " (${diff}\u884C)"
    } else ""

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(12.dp), tint = tint)
        Spacer(Modifier.width(6.dp))
        Text(opLabel, style = MaterialTheme.typography.labelSmall, color = tint)
        Spacer(Modifier.width(4.dp))
        Text(
            change.filePath.substringAfterLast('/') + lineInfo,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}
