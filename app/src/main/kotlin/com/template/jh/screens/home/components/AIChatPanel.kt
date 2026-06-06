package com.template.jh.screens.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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

    LaunchedEffect(state.messages.size, state.messages.lastOrNull()?.content) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.size - 1)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ChatTopBar(
            engineStatus = state.engineStatus,
            modelName = state.modelName,
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

        if (state.messages.isEmpty() && state.engineStatus != EngineStatus.Loading) {
            Box(modifier = Modifier.weight(1f)) { EmptyChatState(state.engineStatus) }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp),
                state = listState, verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.messages, key = { it.id }) { ChatBubble(it) }
                if (state.isLoading && state.messages.lastOrNull()?.isStreaming == true) {
                    item {
                        Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 1.5.dp, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.chat_streaming), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        ChatInputBar(
            inputText = state.inputText,
            onInputChange = { viewModel.setInputText(it) },
            onSend = { viewModel.sendMessage() },
            isLoading = state.isLoading,
            engineStatus = state.engineStatus,
            onCancel = { viewModel.cancelGeneration() },
            onClear = { viewModel.clearMessages() },
        )
    }
}

@Composable
private fun ChatTopBar(
    engineStatus: EngineStatus, modelName: String,
    conversations: List<ConversationEntry>, isHistoryOpen: Boolean,
    onNewTaskClick: () -> Unit, onHistoryClick: () -> Unit, onSettingsClick: () -> Unit,
    onSwitchConversation: (ConversationEntry) -> Unit, onDeleteConversation: (String) -> Unit,
    onDismissHistory: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp).height(36.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            when (engineStatus) {
                EngineStatus.Ready -> {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(Color(0xFF4CAF50)))
                    Spacer(Modifier.width(6.dp))
                    Text(modelName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                EngineStatus.Loading -> {
                    CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 1.5.dp)
                    Spacer(Modifier.width(6.dp))
                    Text("加载中…", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                EngineStatus.Error -> {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.error))
                    Spacer(Modifier.width(6.dp))
                    Text("模型错误", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.error, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                EngineStatus.Idle -> {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurfaceVariant))
                    Spacer(Modifier.width(6.dp))
                    Text("未加载模型", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
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
private fun ChatBubble(message: ChatMessage) {
    val isUser = message.role == ChatRole.User
    val bgColor = if (isUser) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    val shape = RoundedCornerShape(topStart = if (isUser) 12.dp else 4.dp, topEnd = 12.dp, bottomStart = 12.dp, bottomEnd = if (isUser) 4.dp else 12.dp)
    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalAlignment = if (isUser) Alignment.End else Alignment.Start) {
        Text(if (isUser) "You" else "AI", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
        Box(Modifier.widthIn(max = 200.dp).clip(shape).background(bgColor).padding(horizontal = 10.dp, vertical = 6.dp)) {
            Text(message.content, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun ChatInputBar(
    inputText: String, onInputChange: (String) -> Unit, onSend: () -> Unit,
    isLoading: Boolean, engineStatus: EngineStatus,
    onCancel: () -> Unit, onClear: () -> Unit,
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
                if (engineStatus == EngineStatus.Ready) {
                    IconButton(onClick = onCancel, Modifier.size(28.dp), enabled = isLoading) { Icon(Icons.Default.Stop, stringResource(R.string.chat_cancel), Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (engineStatus == EngineStatus.Ready) {
                    IconButton(onClick = onClear, Modifier.size(28.dp)) { Icon(Icons.Default.Close, stringResource(R.string.chat_clear), Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) }
                }
                IconButton(onClick = onSend, Modifier.size(32.dp), enabled = inputText.isNotBlank() && engineStatus == EngineStatus.Ready && !isLoading) {
                    Icon(Icons.AutoMirrored.Filled.Send, stringResource(R.string.ai_send_message), Modifier.size(20.dp), tint = if (inputText.isNotBlank() && engineStatus == EngineStatus.Ready) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                }
            }
        }
    }
}
