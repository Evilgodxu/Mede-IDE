package com.medeide.jh.screens.home.landscape.collab.topbar

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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.medeide.jh.model.chat.ConversationEntry

@Composable
fun CollabTopBar(
    cloudModelEnabled: Boolean,
    conversations: List<ConversationEntry>,
    isHistoryOpen: Boolean,
    onNewConversation: () -> Unit,
    onHistoryClick: () -> Unit,
    onSettings: () -> Unit,
    onSwitchConversation: (String) -> Unit,
    onDeleteConversation: (String) -> Unit,
    onDismissHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Mede",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            IconButton(onClick = onNewConversation, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Add, "新建对话", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Box {
                IconButton(onClick = onHistoryClick, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.History, "历史", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                DropdownMenu(
                    expanded = isHistoryOpen,
                    onDismissRequest = onDismissHistory,
                    modifier = Modifier.widthIn(min = 200.dp)
                        .heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.75f).dp),
                ) {
                    if (conversations.isEmpty()) {
                        DropdownMenuItem(
                            text = {
                                Text("暂无历史对话",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            },
                            onClick = onDismissHistory,
                            enabled = false,
                        )
                    } else {
                        conversations.sortedByDescending { it.timestamp }.forEach { conv ->
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                conv.title,
                                                style = MaterialTheme.typography.labelMedium,
                                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                            )
                                            Text(
                                                "${conv.messages.size} 条消息",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        IconButton(
                                            onClick = { onDeleteConversation(conv.id) },
                                            modifier = Modifier.size(24.dp),
                                        ) {
                                            Icon(
                                                Icons.Default.Delete, "删除", Modifier.size(14.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                            )
                                        }
                                    }
                                },
                                onClick = { onSwitchConversation(conv.id) },
                            )
                        }
                    }
                }
            }

            IconButton(onClick = onSettings, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Settings, "设置", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
