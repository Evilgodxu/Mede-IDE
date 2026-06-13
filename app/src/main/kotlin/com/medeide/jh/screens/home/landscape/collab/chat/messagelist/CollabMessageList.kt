package com.medeide.jh.screens.home.landscape.collab.chat.messagelist

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medeide.jh.R
import com.medeide.jh.model.chat.DisplayItem
import com.medeide.jh.model.chat.DisplayRole
import com.medeide.jh.model.chat.EngineStatus
import com.medeide.jh.screens.home.landscape.collab.chat.inputbar.attachment.loadThumbnail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch
import androidx.compose.foundation.Image as ComposeImage

// 协作区聊天消息列表
@Composable
fun CollabMessageList(
    displayItems: List<DisplayItem>,
    isLoading: Boolean,
    engineStatus: EngineStatus,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    var isAtBottom by remember { mutableStateOf(true) }

    // 检测是否在底部，500ms 防抖
    LaunchedEffect(Unit) {
        snapshotFlow {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            val totalItems = info.totalItemsCount
            lastVisible to totalItems
        }.collectLatest { (lastVisible, totalItems) ->
            val rawAtBottom = totalItems == 0 || lastVisible >= totalItems - 1
            if (!rawAtBottom) delay(500)
            isAtBottom = rawAtBottom
        }
    }

    // 流式输出时自动滚动到底部
    LaunchedEffect(displayItems.size, isLoading) {
        if (isAtBottom) {
            val idx = displayItems.size - 1
            if (idx >= 0) try { listState.scrollToItem(idx) } catch (_: Exception) {}
        }
    }

    // 流式结束后自动滚动到底部
    LaunchedEffect(isLoading) {
        if (!isLoading && isAtBottom) {
            val idx = displayItems.size - 1
            if (idx >= 0) try { listState.scrollToItem(idx) } catch (_: Exception) {}
        }
    }

    if (displayItems.isEmpty() && engineStatus != EngineStatus.Loading) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyChatState(engineStatus)
        }
    } else {
        Box(modifier = modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(displayItems, key = { it.id }) { item ->
                    ConversationItemView(
                        item = item,
                        isActiveStreaming = item.isStreaming && item.role == DisplayRole.Model,
                    )
                }
                item { Spacer(Modifier.height(1.dp)) }
            }
            // Gallery 风格: 滚动到底部按钮
            ScrollToBottomButton(
                isAtBottom = isAtBottom,
                onClick = {
                    val idx = displayItems.size - 1
                    if (idx >= 0) {
                        GlobalScope.launch(Dispatchers.Main) {
                            listState.scrollToItem(idx)
                            isAtBottom = true
                        }
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 4.dp, bottom = 4.dp),
            )
        }
    }
}

// 空聊天状态
@Composable
private fun EmptyChatState(engineStatus: EngineStatus) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(16.dp)
    ) {
        Text(
            stringResource(R.string.ai_collaboration_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(8.dp))
        Text(
            if (engineStatus == EngineStatus.Idle) "请在 IDE 设置 → 模型中加载模型"
            else stringResource(R.string.ai_collaboration_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// 单个对话项（根据角色分发）
@Composable
private fun ConversationItemView(
    item: DisplayItem,
    isActiveStreaming: Boolean = false,
) {
    when (item.role) {
        DisplayRole.User -> UserItemView(item)
        DisplayRole.Model -> ModelItemView(item, isActiveStreaming)
    }
}

// 用户消息
@Composable
private fun UserItemView(item: DisplayItem) {
    val context = LocalContext.current
    Column(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalAlignment = Alignment.End
    ) {
        Text(
            "You",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
        )
        // 图片缩略图
        if (item.imageUris.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .widthIn(max = 360.dp)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item.imageUris.forEach { uri ->
                    val thumbnail = remember(uri) { loadThumbnail(context, uri) }
                    thumbnail?.let { bmp ->
                        ComposeImage(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier
                                .size(80.dp)
                                .clip(RoundedCornerShape(6.dp)),
                            contentScale = ContentScale.Crop
                        )
                    } ?: Box(
                        Modifier
                            .size(80.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant,
                                RoundedCornerShape(6.dp)
                            )
                    )
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

// 模型回复消息
@Composable
private fun ModelItemView(
    item: DisplayItem,
    isActiveStreaming: Boolean,
) {
    val context = LocalContext.current

    Column(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            "Mede",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
        )

        // 思考内容（可展开+虚线分隔）
        if (item.channelContent != null) {
            var thinkExpanded by remember { mutableStateOf(false) }
            val dividerColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
            Column(modifier = Modifier.widthIn(max = 360.dp)) {
                Row(
                    modifier = Modifier
                        .clickable { thinkExpanded = !thinkExpanded }
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (thinkExpanded) "▼" else "▶",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(2.dp))
                    Text(
                        "思考",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (thinkExpanded) {
                    Text(
                        item.channelContent,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
                // 虚线分隔
                Canvas(modifier = Modifier.fillMaxWidth().height(1.dp).padding(horizontal = 8.dp)) {
                    drawLine(
                        color = dividerColor,
                        start = androidx.compose.ui.geometry.Offset(0f, 0f),
                        end = androidx.compose.ui.geometry.Offset(size.width, 0f),
                        strokeWidth = 1f,
                        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                            floatArrayOf(8f, 8f), 0f
                        )
                    )
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
                            val clipboard =
                                context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(
                                ClipData.newPlainText("Mede", item.content)
                            )
                            android.widget.Toast
                                .makeText(
                                    context,
                                    "已复制到剪贴板",
                                    android.widget.Toast.LENGTH_SHORT
                                )
                                .show()
                        },
                    ),
                verticalAlignment = Alignment.Bottom,
            ) {
                BufferedFadingText(
                    text = item.content,
                    inProgress = isActiveStreaming,
                    modifier = Modifier.weight(1f),
                )
                if (isActiveStreaming) {
                    Spacer(Modifier.width(2.dp))
                    StreamingCursor()
                }
            }
        }
    }
}

// 流式光标
@Composable
private fun StreamingCursor() {
    val infiniteTransition = rememberInfiniteTransition(label = "streaming")
    val visible by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
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

/**
 * 参考 Google AI Edge Gallery BufferedFadingMarkdownText 实现。
 * 流式文本输出时使用交叉淡入淡出动画 + Markdown 渲染。
 */
@Composable
private fun BufferedFadingText(
    text: String,
    inProgress: Boolean,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodySmall,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    val mdParser = remember { org.commonmark.parser.Parser.builder().build() }
    val mdRenderer = remember { org.commonmark.renderer.html.HtmlRenderer.builder().build() }

    fun markdownToAnnotated(md: String): AnnotatedString {
        if (md.isBlank()) return AnnotatedString("")
        val html = mdRenderer.render(mdParser.parse(md))
        val spanned = if (android.os.Build.VERSION.SDK_INT >= 24) {
            android.text.Html.fromHtml(html, android.text.Html.FROM_HTML_MODE_LEGACY)
        } else {
            @Suppress("DEPRECATION")
            android.text.Html.fromHtml(html)
        }
        return AnnotatedString(spanned.toString())
    }

    var text1 by remember { mutableStateOf(markdownToAnnotated(text)) }
    var text2 by remember { mutableStateOf(AnnotatedString("")) }
    val alpha2 = remember { Animatable(0f) }
    val currentText by rememberUpdatedState(text)
    var showOverlay by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        snapshotFlow { currentText }
            .conflate()
            .collect { newText: String ->
                val newAnno = markdownToAnnotated(newText)
                if (newAnno.toString() == text1.toString()) return@collect
                text2 = newAnno
                alpha2.snapTo(0f)
                alpha2.animateTo(1f, animationSpec = tween(120, easing = LinearOutSlowInEasing))
                text1 = newAnno
                awaitFrame()
                alpha2.snapTo(0f)
            }
    }

    val previousInProgress by rememberUpdatedState(inProgress)
    LaunchedEffect(inProgress) {
        if (previousInProgress && !inProgress) {
            delay(240)
            showOverlay = false
        }
    }

    Box(modifier = modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
        androidx.compose.foundation.text.selection.SelectionContainer {
            Text(
                text = text1,
                style = style,
                color = color,
                modifier = Modifier.graphicsLayer { alpha = 1f - alpha2.value },
            )
        }
        if (showOverlay) {
            Text(
                text = text2,
                style = style,
                color = color,
                modifier = Modifier
                    .clearAndSetSemantics {}
                    .graphicsLayer {
                        alpha = alpha2.value
                        blendMode = BlendMode.Plus
                    },
            )
        }
    }
}

/** Gallery 风格: 浮动 "滚动到底部" 按钮 */
@Composable
private fun ScrollToBottomButton(
    isAtBottom: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    androidx.compose.animation.AnimatedVisibility(
        visible = !isAtBottom,
        enter = androidx.compose.animation.fadeIn(animationSpec = tween(300)) +
            androidx.compose.animation.scaleIn(
                animationSpec = spring(
                    dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                    stiffness = androidx.compose.animation.core.Spring.StiffnessMedium,
                )
            ),
        exit = androidx.compose.animation.fadeOut(animationSpec = tween(200)),
        modifier = modifier,
    ) {
        androidx.compose.material3.IconButton(
            onClick = onClick,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            ),
        ) {
            Text(
                text = "↓",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}
