package com.template.jh.screens.home.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
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
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

    // 自动滚动到最新消息（仅当用户未手动上滚时）
    LaunchedEffect(state.messages.size, state.isLoading) {
        if (state.messages.isEmpty()) return@LaunchedEffect
        // 获取当前可见的最后一项索引
        val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
        val lastIndex = state.messages.size - 1
        // 如果用户正在靠近底部（最后可见项在倒数第3以内），自动滚到底
        if (lastVisible >= lastIndex - 2 || state.isLoading) {
            try { listState.scrollToItem(lastIndex) } catch (_: Exception) {}
        }
    }

    // 通知事件弹出提示
    LaunchedEffect(state.lastNotification) {
        state.lastNotification?.let { notif ->
            kotlinx.coroutines.delay(4000)
            viewModel.clearNotification()
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

        // 通知横幅
        state.lastNotification?.let { notif ->
            NotificationBanner(notif)
        }

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
                items(state.messages, key = { it.id }) {
                    ChatBubble(
                        message = it,
                        onFileCardClick = { path -> viewModel.requestOpenFile(path) },
                        deleteCardEnabled = state.deleteCardEnabled,
                        showContextBadge = it.role == ChatRole.Model && !it.isStreaming,
                        activeRulesCount = state.activeRulesCount,
                        activeSkillsCount = state.activeSkillsCount,
                    )
                }

            }
        }

        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        ChatInputBar(
            inputText = state.inputText,
            onInputChange = { viewModel.setInputText(it) },
            onSend = { viewModel.sendMessage() },
            isLoading = state.isLoading,
            isOptimizing = state.isOptimizing,
            engineStatus = state.engineStatus,
            onCancel = { viewModel.cancelGeneration() },
            onOptimize = { viewModel.optimizeInput() },
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
    onFileCardClick: ((String) -> Unit)? = null,
    deleteCardEnabled: Boolean = false,
    showContextBadge: Boolean = false,
    activeRulesCount: Int = 0,
    activeSkillsCount: Int = 0,
) {
    // 文件操作卡片
    message.fileOp?.let { op ->
        if (op.opType == com.template.jh.core.ai.FileOpType.Delete && deleteCardEnabled) return
        FileOperationCard(op, onFileCardClick)
        return
    }

    val isUser = message.role == ChatRole.User
    val bgColor = if (isUser) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    val shape = RoundedCornerShape(topStart = if (isUser) 12.dp else 4.dp, topEnd = 12.dp, bottomStart = 12.dp, bottomEnd = if (isUser) 4.dp else 12.dp)
    val context = LocalContext.current

    // 提取 [think]...[/think] 块
    val thinkRegex = remember { Regex("""\[think\](.*?)\[/think\]""", RegexOption.DOT_MATCHES_ALL) }
    val thinks = remember(message.content) { thinkRegex.findAll(message.content).map { it.groupValues[1].trim() }.toList() }
    val displayContent = remember(message.content) { message.content.replace(thinkRegex, "").trim() }

    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalAlignment = if (isUser) Alignment.End else Alignment.Start) {
        if (showContextBadge) {
            val parts = mutableListOf<String>()
            if (activeRulesCount > 0) parts.add("参考${activeRulesCount}条规则")
            if (activeSkillsCount > 0) parts.add("${activeSkillsCount}项技能")
            if (parts.isNotEmpty()) {
                ContextBadge(parts.joinToString(" · "))
            }
        }
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

@Composable
private fun ContextBadge(text: String) {
    Row(
        modifier = Modifier
            .background(
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                RoundedCornerShape(6.dp),
            )
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.CheckCircle,
            null,
            Modifier.size(10.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

// 文件操作卡片（精简单行 git-diff 风格）
@Composable
private fun FileOperationCard(
    op: com.template.jh.core.ai.FileOperationMeta,
    onFileCardClick: ((String) -> Unit)?,
) {
    val isDelete = op.opType == com.template.jh.core.ai.FileOpType.Delete
    val prefix = when (op.opType) {
        com.template.jh.core.ai.FileOpType.Create -> "+"
        com.template.jh.core.ai.FileOpType.Modify -> "~"
        com.template.jh.core.ai.FileOpType.Delete -> "-"
    }
    val prefixColor = when (op.opType) {
        com.template.jh.core.ai.FileOpType.Create -> Color(0xFF4CAF50)
        com.template.jh.core.ai.FileOpType.Modify -> MaterialTheme.colorScheme.primary
        com.template.jh.core.ai.FileOpType.Delete -> MaterialTheme.colorScheme.error
    }
    val lineInfo = if (!isDelete && op.lineChanges != 0) {
        val diff = if (op.lineChanges > 0) "+${op.lineChanges}" else "${op.lineChanges}"
        " ($diff)"
    } else ""

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp, horizontal = 4.dp)
            .clickable { onFileCardClick?.invoke(op.filePath) },
        colors = CardDefaults.cardColors(
            containerColor = if (isDelete)
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
            else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
        ),
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                prefix,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                color = prefixColor,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                op.filePath + lineInfo,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ChatInputBar(
    inputText: String, onInputChange: (String) -> Unit, onSend: () -> Unit,
    isLoading: Boolean, isOptimizing: Boolean, engineStatus: EngineStatus,
    onCancel: () -> Unit, onOptimize: () -> Unit,
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
                if (engineStatus == EngineStatus.Ready && inputText.isNotBlank()) {
                    if (isOptimizing) {
                        IconButton(onClick = {}, Modifier.size(28.dp), enabled = false) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                        }
                    } else {
                        IconButton(onClick = onOptimize, Modifier.size(28.dp), enabled = !isLoading) {
                            Icon(Icons.Default.AutoAwesome, stringResource(R.string.ai_optimize_input), Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (engineStatus == EngineStatus.Ready && isLoading) {
                    // 生成中：暂停图标 + 加载动画
                    IconButton(onClick = onCancel, Modifier.size(32.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                            Icon(Icons.Default.Pause, stringResource(R.string.chat_cancel), Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
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

// 通知横幅
@Composable
private fun NotificationBanner(notif: com.template.jh.core.ai.NotificationEvent) {
    val bgColor = when (notif.type) {
        com.template.jh.data.model.NotificationEventType.TaskCompleted -> Color(0xFF4CAF50).copy(alpha = 0.15f)
        com.template.jh.data.model.NotificationEventType.TaskFailed -> MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
        com.template.jh.data.model.NotificationEventType.WaitingUserAction -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
    }
    val textColor = when (notif.type) {
        com.template.jh.data.model.NotificationEventType.TaskCompleted -> Color(0xFF4CAF50)
        com.template.jh.data.model.NotificationEventType.TaskFailed -> MaterialTheme.colorScheme.error
        com.template.jh.data.model.NotificationEventType.WaitingUserAction -> MaterialTheme.colorScheme.primary
    }
    val icon = when (notif.type) {
        com.template.jh.data.model.NotificationEventType.TaskCompleted -> Icons.Default.CheckCircle
        com.template.jh.data.model.NotificationEventType.TaskFailed -> Icons.Default.Error
        com.template.jh.data.model.NotificationEventType.WaitingUserAction -> Icons.Default.Refresh
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(14.dp), tint = textColor)
        Spacer(Modifier.width(8.dp))
        Text(
            notif.message,
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            modifier = Modifier.weight(1f),
        )
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

@Composable
private fun FileChangeRow(change: com.template.jh.core.ai.FileChangeItem) {
    val icon = when (change.opType) {
        com.template.jh.core.ai.FileOpType.Create -> Icons.Default.Add
        com.template.jh.core.ai.FileOpType.Modify -> Icons.Default.Refresh
        com.template.jh.core.ai.FileOpType.Delete -> Icons.Default.Delete
    }
    val opLabel = when (change.opType) {
        com.template.jh.core.ai.FileOpType.Create -> "创建"
        com.template.jh.core.ai.FileOpType.Modify -> "修改"
        com.template.jh.core.ai.FileOpType.Delete -> "删除"
    }
    val tint = when (change.opType) {
        com.template.jh.core.ai.FileOpType.Create -> Color(0xFF4CAF50)
        com.template.jh.core.ai.FileOpType.Modify -> MaterialTheme.colorScheme.primary
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
