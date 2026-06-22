package com.medeide.jh.screens.home.landscape.collab.chat.topbar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.medeide.jh.R
import com.medeide.jh.model.chat.ConversationEntry

// 协作区顶部栏
@Composable
fun CollabTopBar(
    conversations: List<ConversationEntry>, isHistoryOpen: Boolean,
    onNewTaskClick: () -> Unit, onHistoryClick: () -> Unit, onSettingsClick: () -> Unit,
    onSwitchConversation: (ConversationEntry) -> Unit, onDeleteConversation: (String) -> Unit,
    onDismissHistory: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp).height(36.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            IconButton(onClick = onNewTaskClick, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.Add,
                    stringResource(R.string.ai_new_task),
                    Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // 历史按钮 + 下拉
            Box {
                IconButton(onClick = onHistoryClick, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.History,
                        stringResource(R.string.ai_history),
                        Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                DropdownMenu(
                    expanded = isHistoryOpen,
                    onDismissRequest = onDismissHistory,
                    modifier = Modifier.widthIn(min = 200.dp)
                        .heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.75f).dp)
                ) {
                    if (conversations.isEmpty()) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    "暂无历史对话",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            onClick = onDismissHistory,
                            enabled = false,
                        )
                    } else {
                        conversations.forEach { conv ->
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                conv.title,
                                                style = MaterialTheme.typography.labelMedium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                "${conv.messages.size} 条消息",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        IconButton(
                                            onClick = { onDeleteConversation(conv.id) },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Delete,
                                                "删除",
                                                Modifier.size(14.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                            )
                                        }
                                    }
                                },
                                onClick = { onSwitchConversation(conv) },
                            )
                        }
                    }
                }
            }
            IconButton(onClick = onSettingsClick, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.Settings,
                    stringResource(R.string.ai_ide_settings),
                    Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
