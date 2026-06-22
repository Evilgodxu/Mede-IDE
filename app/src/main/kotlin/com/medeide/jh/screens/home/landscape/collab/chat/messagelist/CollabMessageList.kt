package com.medeide.jh.screens.home.landscape.collab.chat.messagelist

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.medeide.jh.R
import com.medeide.jh.model.chat.DisplayItem
import com.medeide.jh.model.chat.DisplayRole
import com.medeide.jh.model.chat.EngineStatus
import com.medeide.jh.model.chat.FileOperation
import com.medeide.jh.model.chat.FileOpStatus
import com.medeide.jh.model.chat.FileOpType
import com.medeide.jh.screens.home.logic.FileOperationEvents
import com.medeide.jh.screens.home.landscape.collab.chat.inputbar.attachment.loadThumbnail
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.core.MarkwonTheme
import io.noties.markwon.ext.tables.TablePlugin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import androidx.compose.foundation.Image as ComposeImage

// 协作区聊天框
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

        // 该消息关联的文件操作状态卡片（工具操作绑定到触发它的模型消息下方）
        if (item.fileOperations.isNotEmpty()) {
            FileOperationsBar(
                operations = item.fileOperations,
                modifier = Modifier.padding(top = 4.dp),
            )
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
 * 将 Markdown 渲染到 Android TextView，使用 Markwon 库实现原生 Spannable 渲染。
 * 支持标题/粗体/列表/代码块/引用/表格等所有 CommonMark + GFM 语法。
 */
@Composable
private fun BufferedFadingText(
    text: String,
    inProgress: Boolean,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodySmall,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    val context = LocalContext.current
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    // 根据深色/浅色模式配置 Markwon 主题（代码块/内联代码/引用条颜色）
    val markwon = remember(isDark) {
        val onSurfaceArgb = color.toArgb()
        val codeText = if (isDark) 0xFFcdd6f4.toInt() else 0xFF383838.toInt()
        val codeBg = if (isDark) 0xFF181825.toInt() else 0xFFf0f0f0.toInt()
        val quoteBarColor = if (isDark) 0x55cdd6f4.toInt() else 0xFF4a6fa5.toInt()
        Markwon.builder(context)
            .usePlugin(object : AbstractMarkwonPlugin() {
                override fun configureTheme(builder: MarkwonTheme.Builder) {
                    builder.codeTextColor(codeText)
                        .codeBackgroundColor(codeBg)
                        .codeBlockTextColor(codeText)
                        .codeBlockBackgroundColor(codeBg)
                        .blockQuoteColor(quoteBarColor)
                        .linkColor(onSurfaceArgb)
                }
            })
            .usePlugin(TablePlugin.create(context))
            .build()
    }

    val textColorArgb = color.toArgb()

    AndroidView<android.widget.TextView>(
        factory = { ctx ->
            android.widget.TextView(ctx).apply {
                textSize = style.fontSize?.value ?: 14f
                setTextColor(textColorArgb)
                setLinkTextColor(textColorArgb)
                setTextIsSelectable(true)
                style.lineHeight?.let { lh ->
                    val fs = style.fontSize
                    if (fs != null && fs.value > 0f) {
                        setLineSpacing(0f, lh.value / fs.value)
                    }
                }
            }
        },
        update = { tv ->
            tv.setTextColor(textColorArgb)
            tv.setLinkTextColor(textColorArgb)
            markwon.setMarkdown(tv, text)
            // Markwon 渲染后强制所有可点击 Span 使用正文颜色
            val spannable = tv.text
            if (spannable is android.text.Spannable) {
                val spans = spannable.getSpans(
                    0, spannable.length,
                    android.text.style.ClickableSpan::class.java
                ).toList()
                for (span in spans) {
                    val start = spannable.getSpanStart(span)
                    val end = spannable.getSpanEnd(span)
                    val flags = spannable.getSpanFlags(span)
                    spannable.removeSpan(span)
                    spannable.setSpan(
                        object : android.text.style.ClickableSpan() {
                            override fun onClick(widget: android.view.View) = span.onClick(widget)
                            override fun updateDrawState(ds: android.text.TextPaint) {
                                ds.color = textColorArgb
                                ds.isUnderlineText = true
                            }
                        },
                        start, end, flags,
                    )
                }
            }
        },
        modifier = modifier,
    )
}

/** 文件操作状态卡片组 */
@Composable
private fun FileOperationsBar(
    operations: List<FileOperation>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        operations.forEach { op ->
            FileOperationCard(operation = op)
        }
    }
}

/** 单个文件操作卡片：图标 + 操作描述 + 文件路径 + 状态指示 */
@Composable
private fun FileOperationCard(
    operation: FileOperation,
    modifier: Modifier = Modifier,
) {
    val bgColor = when (operation.status) {
        FileOpStatus.InProgress -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        FileOpStatus.Success -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        FileOpStatus.Error -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
    }
    val label = when (operation.type) {
        FileOpType.WriteFile -> "写入"
        FileOpType.CreateDirectory -> "创建目录"
        FileOpType.DeleteFile -> "删除"
        FileOpType.MoveFile -> "移动"
        FileOpType.CopyFile -> "复制"
    }
    // 仅显示文件名，隐藏长路径前缀
    val displayPath = operation.filePath.substringAfterLast('/').let {
        if (it.length > 40) it.take(37) + "..." else it
    }
    // 写入/创建/移动/复制 支持点击打开（复用已有 FileOperationEvents 机制）
    val canOpen = operation.type != FileOpType.DeleteFile &&
        operation.status == FileOpStatus.Success

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .then(if (canOpen) Modifier.clickable {
                FileOperationEvents.notify(operation.filePath, "create")
            } else Modifier)
            .background(bgColor)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 状态指示
        StatusIndicator(operation.status)
        Spacer(Modifier.width(6.dp))
        // 操作标签
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(4.dp))
        // 文件路径
        Text(
            displayPath,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

/** 状态圆点指示器（执行中为旋转转圈动画，成功为绿色实心，失败为红色实心） */
@Composable
private fun StatusIndicator(status: FileOpStatus) {
    val successColor = Color(0xFF4CAF50) // 绿色
    val errorColor = MaterialTheme.colorScheme.error

    when (status) {
        FileOpStatus.InProgress -> SpinningDot(size = 8.dp)
        FileOpStatus.Success -> Box(
            Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(successColor)
        )
        FileOpStatus.Error -> Box(
            Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(errorColor)
        )
    }
}

/** 旋转转圈动画的圆点（类似加载中 spinner 效果） */
@Composable
private fun SpinningDot(size: androidx.compose.ui.unit.Dp) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val infiniteTransition = rememberInfiniteTransition(label = "spinningDot")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(800, easing = androidx.compose.animation.core.LinearEasing)),
        label = "rotation",
    )
    val sweep by infiniteTransition.animateFloat(
        initialValue = 30f,
        targetValue = 330f,
        animationSpec = infiniteRepeatable(tween(800, easing = androidx.compose.animation.core.LinearEasing), RepeatMode.Reverse),
        label = "sweep",
    )
    Canvas(modifier = Modifier.size(size)) {
        val strokeWidth = (size.toPx() * 0.22f).coerceAtLeast(1.5f)
        drawArc(
            color = primaryColor,
            startAngle = rotation,
            sweepAngle = sweep,
            useCenter = false,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth, cap = androidx.compose.ui.graphics.StrokeCap.Round),
        )
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
