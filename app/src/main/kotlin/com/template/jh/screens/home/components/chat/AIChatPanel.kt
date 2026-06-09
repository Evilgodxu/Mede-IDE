package com.template.jh.screens.home.components.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap




import androidx.compose.foundation.Image
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
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
import com.template.jh.model.chat.AttachedFile
import com.template.jh.model.chat.ChatMessage
import com.template.jh.model.chat.ChatRole
import com.template.jh.model.chat.ConversationEntry
import com.template.jh.model.chat.DisplayItem
import com.template.jh.model.chat.DisplayRole
import com.template.jh.model.chat.EngineStatus
import com.template.jh.model.chat.ModelActivity
import com.template.jh.core.analytics.UsageStats

// AI 协作面板
@Composable
fun AIChatPanel(
    onSettingsClick: () -> Unit = {},
    onNewTaskClick: () -> Unit = {},
    onHistoryClick: () -> Unit = {},
    viewModel: com.template.jh.screens.home.ChatViewModel,
) {
    val state by viewModel.state.collectAsState()
    val displayItems by viewModel.displayItems.collectAsState()
    val currentToolActivity by viewModel.currentToolActivity.collectAsState()
    val listState = rememberLazyListState()
    var userScrolledUp by remember { mutableStateOf(false) }

    // 检测用户手动向上滚动（基于布局信息，比 firstVisibleItemIndex 更准确）
    LaunchedEffect(Unit) {
        snapshotFlow {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            val totalItems = info.totalItemsCount
            lastVisible to totalItems
        }.collect { (lastVisible, totalItems) ->
            if (state.isLoading && totalItems > 0) {
                userScrolledUp = lastVisible < totalItems - 2
            } else {
                userScrolledUp = false
            }
        }
    }

    // 流式输出时仅当有新内容时滚动到底部（替代 delay 轮询）
    LaunchedEffect(state.isLoading) {
        if (!state.isLoading) {
            val idx = displayItems.size + (if (currentToolActivity != null) 1 else 0) - 1
            if (idx >= 0) try { listState.scrollToItem(idx) } catch (_: Exception) {}
            return@LaunchedEffect
        }
        snapshotFlow { displayItems.size }.collect { size ->
            if (!userScrolledUp && size > 0) {
                val idx = size + (if (currentToolActivity != null) 1 else 0) - 1
                try { listState.scrollToItem(idx) } catch (_: Exception) {}
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

        if (state.messages.isEmpty() && state.engineStatus != EngineStatus.Loading) {
            Box(modifier = Modifier.weight(1f)) { EmptyChatState(state.engineStatus) }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp),
                state = listState, verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(displayItems, key = { it.id }) { item ->
                    ConversationItemView(
                        item = item,
                        isActiveStreaming = item.isStreaming && item.role == DisplayRole.Model,
                    )
                }
                // 工具操作指示器（固定在列表底部）
                item(key = "tool_activity") {
                    if (currentToolActivity != null) {
                        ToolActivityView(item = currentToolActivity!!)
                    } else {
                        Spacer(Modifier.height(0.dp))
                    }
                }
                item { Spacer(Modifier.height(1.dp)) }
            }
        }

        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        val contextUsedTokens by viewModel.contextTokenCount.collectAsState()
        val contextMaxTokens = state.contextMaxTokens
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
                contextSummary = state.contextSummary,
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

@Composable
private fun ToolActivityView(item: DisplayItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(14.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = item.content,
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
private fun ConversationItemView(
    item: DisplayItem,
    isActiveStreaming: Boolean = false,
) {
    when (item.role) {
        DisplayRole.User -> UserItemView(item)
        DisplayRole.Model -> ModelItemView(item, isActiveStreaming)
        DisplayRole.ToolActivity -> { /* 由 ToolActivityView 单独渲染 */ }
    }
}

@Composable
private fun UserItemView(item: DisplayItem) {
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalAlignment = Alignment.End) {
        Text("You", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
        // 图片缩略图
        if (item.imageUris.isNotEmpty()) {
            Row(
                modifier = Modifier.widthIn(max = 360.dp).padding(horizontal = 10.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item.imageUris.forEach { uri ->
                    val thumbnail = remember(uri) { loadThumbnail(context, uri) }
                    thumbnail?.let { bmp ->
                        Image(bitmap = bmp.asImageBitmap(), contentDescription = null,
                            modifier = Modifier.size(80.dp).clip(RoundedCornerShape(6.dp)),
                            contentScale = ContentScale.Crop)
                    } ?: Box(Modifier.size(80.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp)))
                }
            }
        }
        // 文本内容
        if (item.content.isNotEmpty()) {
            Text(
                text = item.content,
                modifier = Modifier
                    .widthIn(max = 360.dp)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun ModelItemView(
    item: DisplayItem,
    isActiveStreaming: Boolean,
) {
    val context = LocalContext.current
    val thinkRegex = remember { Regex("""\[think\](.*?)\[/think]""", RegexOption.DOT_MATCHES_ALL) }

    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalAlignment = Alignment.Start) {
        Text("AI", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))

        // 思考块：可折叠展示
        if (item.thinkBlocks.isNotEmpty()) {
            var thinkExpanded by remember { mutableStateOf(false) }
            Box(
                modifier = Modifier
                    .widthIn(max = 360.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    .clickable { thinkExpanded = !thinkExpanded }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("思考", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(4.dp))
                        Text(if (thinkExpanded) "▼" else "▶", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (thinkExpanded) {
                        Spacer(Modifier.height(4.dp))
                        item.thinkBlocks.forEachIndexed { i, thinkContent ->
                            if (i > 0) Spacer(Modifier.height(6.dp))
                            Text(thinkContent, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                        }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }

        // 回复内容
        if (item.content.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .widthIn(max = 360.dp)
                    .combinedClickable(
                        onClick = {},
                        onLongClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("消息内容", item.content))
                        },
                    )
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(item.content, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f, fill = false))
                if (isActiveStreaming) StreamingCursor()
            }
        }
    }
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

        // 文件附件芯片（支持水平滚动）
        if (attachedFileRefs.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                    .horizontalScroll(rememberScrollState()),
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
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(16.dp)
                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                .clickable { onDetachImage(uri) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Default.Close, "移除", Modifier.size(10.dp), tint = Color.White)
                        }
                    }
                }
            }
        }

        // 工具栏：按面板宽度平均间距排列按钮
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
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
                currentInput = inputText,
                onInputChange = onInputChange,
                enabled = engineStatus == EngineStatus.Ready && !isLoading
            )

            // 上下文窗口进度按钮
            val ratio = if (contextMaxTokens > 0) (contextUsedTokens.toFloat() / contextMaxTokens).coerceIn(0f, 1f) else 0f
            Box(
                modifier = Modifier
                    .size(22.dp)
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
    val size = android.util.Size(200, 200)
    context.contentResolver.loadThumbnail(uri, size, null)
} catch (_: Exception) { null }

// 语音输入按钮组件 - 使用 SpeechRecognizer，支持连续识别
@Composable
private fun VoiceInputButton(
    currentInput: String,
    onInputChange: (String) -> Unit,
    enabled: Boolean = true,
) {
    var isListening by remember { mutableStateOf(false) }
    var hasPermission by remember { mutableStateOf(false) }
    var partialText by remember { mutableStateOf("") }
    val recognizerManager = remember { VoiceRecognizerManager() }

    val permissionContext = LocalContext.current
    LaunchedEffect(Unit) {
        hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
            permissionContext, android.Manifest.permission.RECORD_AUDIO
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    DisposableEffect(Unit) {
        onDispose { recognizerManager.destroy() }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        hasPermission = isGranted
        if (isGranted) {
            recognizerManager.start(
                isListeningState = { isListening = it },
                onFinalResult = { text ->
                    partialText = ""
                    onInputChange(if (currentInput.isBlank()) text else "$currentInput$text")
                },
                onPartialResult = { text -> partialText = text },
            )
        }
    }

    IconButton(
        onClick = {
            if (isListening) {
                recognizerManager.stop()
                partialText = ""
            } else if (!hasPermission) {
                permissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
            } else {
                partialText = ""
                recognizerManager.start(
                    isListeningState = { isListening = it },
                    onFinalResult = { text ->
                        partialText = ""
                        onInputChange(if (currentInput.isBlank()) text else "$currentInput$text")
                    },
                    onPartialResult = { text -> partialText = text },
                )
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
    // 语音输入识别中显示预览文本
    if (partialText.isNotBlank()) {
        Text(
            text = partialText,
            style = MaterialTheme.typography.bodySmall.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 160.dp).padding(start = 4.dp),
        )
    }
}

/**
 * 语音识别管理器 - 支持连续识别、自动重连、静音超时控制
 *
 * 使用 MyApplication.instance 获取 Application Context，绕过 Compose LocalContext 包装。
 * 不依赖 isRecognitionAvailable() 预检（部分设备该 API 返回异常），
 * 直接 createSpeechRecognizer 并判空，兼容更多设备。
 */
private class VoiceRecognizerManager {
    private val appContext: android.content.Context = com.template.jh.MyApplication.instance
    private var recognizer: android.speech.SpeechRecognizer? = null
    private var isActive = false
    private var onFinalCallback: ((String) -> Unit)? = null
    private var onPartialCallback: ((String) -> Unit)? = null
    private var onListeningCallback: ((Boolean) -> Unit)? = null
    private val handler = Handler(Looper.getMainLooper())
    private var retryCount = 0
    private var maxRetries = 3
    private var useSegmentedSession = false

    fun start(
        isListeningState: (Boolean) -> Unit,
        onFinalResult: (String) -> Unit,
        onPartialResult: (String) -> Unit,
    ) {
        destroy()
        retryCount = 0
        isActive = true
        onFinalCallback = onFinalResult
        onPartialCallback = onPartialResult
        onListeningCallback = isListeningState
        useSegmentedSession = android.os.Build.VERSION.SDK_INT >= 33
        startRecognizer()
    }

    fun stop() {
        isActive = false
        recognizer?.stopListening()
        recognizer?.cancel()
        recognizer?.destroy()
        recognizer = null
        handler.removeCallbacksAndMessages(null)
        onListeningCallback?.invoke(false)
    }

    fun destroy() {
        isActive = false
        retryCount = 0
        handler.removeCallbacksAndMessages(null)
        recognizer?.cancel()
        recognizer?.destroy()
        recognizer = null
    }

    private fun createAndStartRecognizer() {
        val r = android.speech.SpeechRecognizer.createSpeechRecognizer(appContext)
        if (r == null) {
            onListeningCallback?.invoke(false)
            android.widget.Toast.makeText(appContext, "未找到语音识别服务", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        recognizer = r
        var hasActiveResult = false
        r.setRecognitionListener(object : android.speech.RecognitionListener {
            override fun onReadyForSpeech(params: android.os.Bundle?) {
                retryCount = 0
            }
            override fun onBeginningOfSpeech() {
                onListeningCallback?.invoke(true)
            }
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                if (!useSegmentedSession) {
                    onListeningCallback?.invoke(false)
                }
            }
            override fun onError(error: Int) {
                android.util.Log.d("VoiceRecognizer", "onError: $error")
                onListeningCallback?.invoke(false)
                r.destroy()
                if (recognizer == r) recognizer = null
                if (!isActive) return
                when (error) {
                    android.speech.SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
                    android.speech.SpeechRecognizer.ERROR_NO_MATCH -> {
                        handler.postDelayed({ startRecognizer() }, 200)
                    }
                    android.speech.SpeechRecognizer.ERROR_NETWORK,
                    android.speech.SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> {
                        if (retryCount < maxRetries) {
                            retryCount++
                            handler.postDelayed({ startRecognizer() }, (retryCount * 1000L).coerceAtMost(3000L))
                        } else {
                            onFinalCallback?.invoke("语音识别网络错误")
                        }
                    }
                    android.speech.SpeechRecognizer.ERROR_AUDIO,
                    android.speech.SpeechRecognizer.ERROR_CLIENT -> {}
                    else -> {
                        if (retryCount < maxRetries) {
                            retryCount++
                            handler.postDelayed({ startRecognizer() }, 1000)
                        }
                    }
                }
            }
            override fun onResults(results: android.os.Bundle?) {
                val matches = results?.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull()?.trim()
                if (text != null && text.isNotBlank()) {
                    hasActiveResult = true
                    onFinalCallback?.invoke(text)
                }
                if (useSegmentedSession) return
                r.destroy()
                if (recognizer == r) recognizer = null
                if (isActive) startRecognizer()
            }
            override fun onPartialResults(partialResults: android.os.Bundle?) {
                val matches = partialResults?.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull()?.trim()
                if (text != null && text.isNotBlank()) {
                    onPartialCallback?.invoke(text)
                }
            }
            override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
            override fun onSegmentResults(segmentResults: android.os.Bundle) {
                val matches = segmentResults.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull()?.trim()
                if (text != null && text.isNotBlank()) {
                    hasActiveResult = true
                    onFinalCallback?.invoke(text)
                }
            }
        })
        val intent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, java.util.Locale.getDefault())
            putExtra(android.speech.RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(android.speech.RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(android.speech.RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000)
            putExtra(android.speech.RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000)
            if (useSegmentedSession) {
                putExtra(android.speech.RecognizerIntent.EXTRA_SEGMENTED_SESSION, true)
            }
        }
        r.startListening(intent)
        onListeningCallback?.invoke(true)
    }

    private fun startRecognizer() {
        if (!isActive) return
        try {
            createAndStartRecognizer()
        } catch (e: Exception) {
            onListeningCallback?.invoke(false)
            android.widget.Toast.makeText(appContext, "语音识别启动失败", android.widget.Toast.LENGTH_SHORT).show()
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
    contextSummary: String = "",
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
                val summary = contextSummary
                if (summary.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text("上下文摘要", fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.labelMedium, color = Color(0xFF7B1FA2))
                    Text(
                        text = summary.take(200),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis,
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


