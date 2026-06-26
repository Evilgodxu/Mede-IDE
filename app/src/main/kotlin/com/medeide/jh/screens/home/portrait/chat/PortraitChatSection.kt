package com.medeide.jh.screens.home.portrait.chat

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medeide.jh.model.chat.DisplayItem
import com.medeide.jh.model.chat.DisplayRole
import com.medeide.jh.screens.home.cloudchat.CloudChatViewModel
import com.medeide.jh.screens.home.cloudchat.FileOperationsBar
import com.medeide.jh.screens.home.portrait.chat.components.WelcomeHeader
import com.medeide.jh.ui.components.AvatarImage

@Composable
fun PortraitChatSection(
    viewModel: CloudChatViewModel,
    userName: String,
    agentName: String = "AI",
    userAvatarUri: String = "",
    agentAvatarUri: String = "",
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(state.displayItems.size) {
        if (state.displayItems.isNotEmpty()) {
            listState.animateScrollToItem(state.displayItems.size - 1)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        if (state.displayItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(top = 32.dp),
                contentAlignment = Alignment.TopStart,
            ) {
                WelcomeHeader(userName = userName)
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.displayItems, key = { it.id }) { item ->
                    MessageBubble(
                        item = item, userName = userName, agentName = agentName,
                        userAvatarUri = userAvatarUri, agentAvatarUri = agentAvatarUri,
                    )
                }
                item { Spacer(Modifier.height(8.dp)) }
            }
        }
    }
}

@Composable
private fun MessageBubble(
    item: DisplayItem,
    userName: String = "",
    agentName: String = "AI",
    userAvatarUri: String = "",
    agentAvatarUri: String = "",
) {
    val isUser = item.role == DisplayRole.User
    val displayName = if (isUser) (userName.ifEmpty { "你" }) else agentName
    val avatarUri = if (isUser) userAvatarUri else agentAvatarUri
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top,
    ) {
        if (!isUser) {
            AvatarImage(
                initial = displayName.first().toString(),
                avatarUri = avatarUri.ifEmpty { null },
                size = 36.dp,
                modifier = Modifier.padding(end = 6.dp),
            )
        } else {
            Spacer(Modifier.widthIn(min = 42.dp))
        }
        Column(
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
        ) {
            Text(
                text = displayName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            )
            Column(
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isUser) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                // 推理内容
                if (item.isThinking && item.isStreaming) {
                    Text(
                        text = "正在思考…",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontStyle = FontStyle.Italic,
                    )
                } else if (item.reasoningContent.isNotEmpty()) {
                    ReasoningBlock(content = item.reasoningContent)
                }
                // 主内容
                if (item.isThinking && item.isStreaming) {
                    Text(
                        text = "",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                        color = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurface,
                    )
                } else {
                    val displayText = if (item.isStreaming) item.content + "▎" else item.content
                    val textColor = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onSurface
                    Text(
                        text = displayText,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                        color = textColor,
                    )
                }
                // 文件操作状态卡片
                if (item.fileOperations.isNotEmpty()) {
                    FileOperationsBar(
                        operations = item.fileOperations,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
        if (isUser) {
            AvatarImage(
                initial = displayName.first().toString(),
                avatarUri = avatarUri.ifEmpty { null },
                size = 36.dp,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
    }
}

@Composable
private fun ReasoningBlock(content: String) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .clickable { expanded = !expanded }
                .padding(vertical = 2.dp, horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.size(4.dp))
            Text(
                text = "思考过程",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontStyle = FontStyle.Italic,
            )
        }
        if (expanded) {
            Text(
                text = content,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 14.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 14.dp),
            )
        }
    }
}
