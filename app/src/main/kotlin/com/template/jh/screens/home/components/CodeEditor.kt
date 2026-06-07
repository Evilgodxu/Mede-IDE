package com.template.jh.screens.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalDensity
import android.content.ClipData
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.template.jh.core.editor.ChangeBlock
import com.template.jh.core.editor.ChangeBlockStatus
import com.template.jh.core.editor.CodeReviewState
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.input.pointer.positionChanged
import com.template.jh.core.editor.DiffHighlightTransformation
import com.template.jh.core.editor.LineChangeType
import com.template.jh.core.editor.backgroundColor
import com.template.jh.core.editor.color
import com.template.jh.core.editor.highlightSyntax
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.delay
import kotlin.math.abs

/**
 * 代码编辑器：支持预览模式和编辑模式
 *
 * 交互方式：
 * 1. 单指滑动 - 预览滚动
 * 2. 双指缩放 - 调整字体大小
 * 3. 双击 - 进入编辑模式
 * 4. 长按 - 选中内容并显示操作菜单
 */
@Composable
fun CodeEditor(
    text: TextFieldValue,
    onTextChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
    // 行级 diff 高亮
    lineDiffs: Map<Int, LineChangeType> = emptyMap(),
    // 原始内容（用于diff审阅模式）
    originalContent: String? = null,
    // 代码审查状态
    reviewState: CodeReviewState? = null,
    // 待审阅修改
    pendingFilePath: String? = null,
    onAcceptChanges: (() -> Unit)? = null,
    onRejectChanges: (() -> Unit)? = null,
    onJumpToPrevChange: (() -> Unit)? = null,
    onJumpToNextChange: (() -> Unit)? = null,
    // 修改块级别的回调
    onAcceptBlock: ((Int) -> Unit)? = null,
    onRejectBlock: ((Int) -> Unit)? = null,
    onNavigateToBlock: ((Int) -> Unit)? = null,
    // 是否显示审查面板
    showReviewPanel: Boolean = false,
    onToggleReviewPanel: (() -> Unit)? = null,
    // 当前修改索引和总修改数
    currentChangeIndex: Int = 0,
    totalChanges: Int = 0,
    // 光标位置回调
    onCursorChange: ((Int) -> Unit)? = null,
    // 添加到对话回调
    onAddToChat: ((String) -> Unit)? = null,
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val scrollState = rememberScrollState()
    val hScrollState = rememberScrollState()
    val lineCount = remember(text.text) { text.text.lines().size.coerceAtLeast(1) }

    var fontSizeSp by remember { mutableFloatStateOf(12f) }
    var isEditMode by remember { mutableStateOf(false) }
    var showContextMenu by remember { mutableStateOf(false) }
    var selectedText by remember { mutableStateOf("") }
    var menuOffset by remember { mutableStateOf(Offset.Zero) }

    val digitCount = lineCount.toString().length
    val lineNumWidth: Dp = remember(digitCount) { (digitCount * 10 + 20).dp.coerceIn(30.dp, 80.dp) }
    val lineHeightSp = (fontSizeSp * 1.5f).coerceAtLeast(14f)
    val lineHeightDp = with(density) { lineHeightSp.sp.toDp() }
    val verticalPadding = (fontSizeSp * 0.6f).dp

    // 判断当前模式
    val hasReviewState = reviewState != null && reviewState.changeBlocks.isNotEmpty()
    val hasLegacyPending = pendingFilePath != null && originalContent != null
    val isReviewMode = hasReviewState || hasLegacyPending

    // 光标位置跟踪
    val cursorLine = remember(text.selection) {
        text.text.substring(0, text.selection.start).count { it == '\n' } + 1
    }
    LaunchedEffect(cursorLine) {
        onCursorChange?.invoke(cursorLine)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // 主内容区域
        if (isReviewMode && !isEditMode) {
            // 审查预览模式 - 单栏显示新代码，带diff高亮
            ReviewPreviewMode(
                reviewState = reviewState,
                originalContent = originalContent,
                newContent = text.text,
                lineDiffs = lineDiffs,
                fontSizeSp = fontSizeSp,
                lineHeightSp = lineHeightSp,
                lineHeightDp = lineHeightDp,
                lineNumWidth = lineNumWidth,
                scrollState = scrollState,
                hScrollState = hScrollState,
                onDoubleTap = { isEditMode = true },
                onLongPress = { text, offset ->
                    selectedText = text
                    menuOffset = offset
                    showContextMenu = true
                },
                onZoom = { zoom ->
                    fontSizeSp = (fontSizeSp * zoom).coerceIn(8f, 32f)
                }
            )
        } else if (isReviewMode && isEditMode) {
            // 审查编辑模式
            ReviewEditMode(
                text = text,
                onTextChange = onTextChange,
                lineDiffs = lineDiffs,
                fontSizeSp = fontSizeSp,
                lineHeightSp = lineHeightSp,
                lineHeightDp = lineHeightDp,
                lineNumWidth = lineNumWidth,
                scrollState = scrollState,
                hScrollState = hScrollState,
                verticalPadding = verticalPadding,
                onExitEditMode = { isEditMode = false }
            )
        } else {
            // 普通编辑模式
            NormalEditMode(
                text = text,
                onTextChange = onTextChange,
                lineDiffs = lineDiffs,
                fontSizeSp = fontSizeSp,
                lineHeightSp = lineHeightSp,
                lineHeightDp = lineHeightDp,
                lineNumWidth = lineNumWidth,
                scrollState = scrollState,
                hScrollState = hScrollState,
                verticalPadding = verticalPadding,
                readOnly = readOnly
            )
        }

        // 右上角悬浮导航器（审查模式）
        if (isReviewMode) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 4.dp, end = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = Alignment.End,
            ) {
                // 编辑模式指示器
                if (isEditMode) {
                    Row(
                        modifier = Modifier
                            .background(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                                RoundedCornerShape(12.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .clickable { isEditMode = false },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "编辑中",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }

                // 审查导航器
                val navReviewState = reviewState
                if (hasReviewState && navReviewState != null) {
                    ReviewNavigator(
                        reviewState = navReviewState,
                        onPrev = { onJumpToPrevChange?.invoke() },
                        onNext = { onJumpToNextChange?.invoke() },
                        onAcceptCurrent = {
                            reviewState.currentBlockIndex.let { idx ->
                                if (idx in reviewState.changeBlocks.indices) {
                                    onAcceptBlock?.invoke(idx)
                                }
                            }
                        },
                        onRejectCurrent = {
                            reviewState.currentBlockIndex.let { idx ->
                                if (idx in reviewState.changeBlocks.indices) {
                                    onRejectBlock?.invoke(idx)
                                }
                            }
                        },
                        onOpenPanel = { onToggleReviewPanel?.invoke() }
                    )
                }
            }
        }

        // 长按菜单
        DropdownMenu(
            expanded = showContextMenu,
            onDismissRequest = { showContextMenu = false },
            offset = androidx.compose.ui.unit.DpOffset(menuOffset.x.dp, menuOffset.y.dp)
        ) {
            DropdownMenuItem(
                text = { Text("添加到对话") },
                onClick = {
                    onAddToChat?.invoke(selectedText)
                    showContextMenu = false
                }
            )
            DropdownMenuItem(
                text = { Text("复制") },
                onClick = {
                    scope.launch {
                        clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("", selectedText)))
                    }
                    showContextMenu = false
                }
            )
        }
    }
}

/**
 * 审查预览模式 - 单栏显示，支持手势
 */
@Composable
private fun ReviewPreviewMode(
    reviewState: CodeReviewState?,
    originalContent: String?,
    newContent: String,
    lineDiffs: Map<Int, LineChangeType>,
    fontSizeSp: Float,
    lineHeightSp: Float,
    lineHeightDp: Dp,
    lineNumWidth: Dp,
    scrollState: androidx.compose.foundation.ScrollState,
    hScrollState: androidx.compose.foundation.ScrollState,
    onDoubleTap: () -> Unit,
    onLongPress: (String, Offset) -> Unit,
    onZoom: (Float) -> Unit,
) {
    val lines = newContent.lines()
    val lineCount = lines.size.coerceAtLeast(1)

    // 计算每行的diff类型
    val newDiffs = reviewState?.let { computeNewLineDiffs(it) } ?: lineDiffs

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                coroutineScope {
                    var zoom = 1f
                    detectTransformGestures { _, _, gestureZoom, _ ->
                        zoom = gestureZoom
                        onZoom(zoom)
                    }
                }
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 4.dp)
        ) {
            // 行号列
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(lineNumWidth)
                    .verticalScroll(scrollState)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    .padding(horizontal = 4.dp),
            ) {
                for (i in 1..lineCount) {
                    val changeType = newDiffs[i - 1]
                    val rowBg = when (changeType) {
                        LineChangeType.Added -> Color(0x3322CC22)
                        LineChangeType.Modified -> Color(0x33CCAA00)
                        else -> Color.Transparent
                    }
                    Text(
                        text = i.toString(),
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = fontSizeSp.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            lineHeight = lineHeightSp.sp,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Visible,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(lineHeightDp)
                            .background(rowBg),
                        textAlign = TextAlign.End,
                    )
                }
            }

            // 代码内容 - 可选择和长按
            SelectionContainer {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(scrollState)
                        .horizontalScroll(hScrollState)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onDoubleTap = { onDoubleTap() },
                                onLongPress = { offset ->
                                    // 简化处理：长按整行
                                    val lineIndex = (offset.y / lineHeightDp.toPx()).toInt()
                                    if (lineIndex in lines.indices) {
                                        onLongPress(lines[lineIndex], offset)
                                    }
                                }
                            )
                        }
                ) {
                    lines.forEachIndexed { index, line ->
                        val changeType = newDiffs[index]
                        val bgColor = when (changeType) {
                            LineChangeType.Added -> Color(0x5522CC22)
                            LineChangeType.Modified -> Color(0x55CCAA00)
                            else -> Color.Transparent
                        }
                        val textColor = when (changeType) {
                            LineChangeType.Added -> Color(0xFF22CC22)
                            LineChangeType.Modified -> Color(0xFFCCAA00)
                            else -> MaterialTheme.colorScheme.onSurface
                        }

                        Text(
                            text = line.takeIf { it.isNotEmpty() } ?: " ",
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = fontSizeSp.sp,
                                color = textColor,
                                lineHeight = lineHeightSp.sp,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(lineHeightDp)
                                .background(bgColor)
                                .padding(horizontal = 8.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Visible,
                        )
                    }
                }
            }
        }
    }
}

/**
 * 审查编辑模式
 */
@Composable
private fun ReviewEditMode(
    text: TextFieldValue,
    onTextChange: (TextFieldValue) -> Unit,
    lineDiffs: Map<Int, LineChangeType>,
    fontSizeSp: Float,
    lineHeightSp: Float,
    lineHeightDp: Dp,
    lineNumWidth: Dp,
    scrollState: androidx.compose.foundation.ScrollState,
    hScrollState: androidx.compose.foundation.ScrollState,
    verticalPadding: Dp,
    onExitEditMode: () -> Unit,
) {
    val lineCount = text.text.lines().size.coerceAtLeast(1)

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = verticalPadding)
    ) {
        // 行号列
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .width(lineNumWidth)
                .verticalScroll(scrollState)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                .padding(horizontal = 4.dp),
        ) {
            for (i in 1..lineCount) {
                val changeType = lineDiffs[i - 1]
                val rowBg = when (changeType) {
                    LineChangeType.Added -> Color(0x3322CC22)
                    LineChangeType.Removed -> Color(0x33CC2222)
                    LineChangeType.Modified -> Color(0x33CCAA00)
                    else -> Color.Transparent
                }
                Text(
                    text = i.toString(),
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = fontSizeSp.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        lineHeight = lineHeightSp.sp,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Visible,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(lineHeightDp)
                        .background(rowBg),
                    textAlign = TextAlign.End,
                )
            }
        }

        // 编辑器
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(scrollState)
                .horizontalScroll(hScrollState)
        ) {
            BasicTextField(
                value = text,
                onValueChange = onTextChange,
                readOnly = false,
                textStyle = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = fontSizeSp.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = lineHeightSp.sp,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                visualTransformation = if (lineDiffs.isNotEmpty()) {
                    DiffHighlightTransformation(lineDiffs)
                } else {
                    SyntaxHighlightTransformation()
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
            )
        }
    }
}

/**
 * 普通编辑模式
 */
@Composable
private fun NormalEditMode(
    text: TextFieldValue,
    onTextChange: (TextFieldValue) -> Unit,
    lineDiffs: Map<Int, LineChangeType>,
    fontSizeSp: Float,
    lineHeightSp: Float,
    lineHeightDp: Dp,
    lineNumWidth: Dp,
    scrollState: androidx.compose.foundation.ScrollState,
    hScrollState: androidx.compose.foundation.ScrollState,
    verticalPadding: Dp,
    readOnly: Boolean,
) {
    val lineCount = text.text.lines().size.coerceAtLeast(1)

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = verticalPadding)
    ) {
        // 行号列
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .width(lineNumWidth)
                .verticalScroll(scrollState)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                .padding(horizontal = 4.dp),
        ) {
            for (i in 1..lineCount) {
                val changeType = lineDiffs[i - 1]
                val rowBg = when (changeType) {
                    LineChangeType.Added -> Color(0x3322CC22)
                    LineChangeType.Removed -> Color(0x33CC2222)
                    LineChangeType.Modified -> Color(0x33CCAA00)
                    else -> Color.Transparent
                }
                Text(
                    text = i.toString(),
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = fontSizeSp.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        lineHeight = lineHeightSp.sp,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Visible,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(lineHeightDp)
                        .background(rowBg),
                    textAlign = TextAlign.End,
                )
            }
        }

        // 编辑器
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(scrollState)
                .horizontalScroll(hScrollState)
        ) {
            BasicTextField(
                value = text,
                onValueChange = onTextChange,
                readOnly = readOnly,
                textStyle = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = fontSizeSp.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = lineHeightSp.sp,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                visualTransformation = SyntaxHighlightTransformation(),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                decorationBox = { innerTextField ->
                    if (text.text.isEmpty()) {
                        Text(
                            text = "// 开始输入代码…",
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = fontSizeSp.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                                lineHeight = lineHeightSp.sp,
                            ),
                        )
                    }
                    innerTextField()
                },
            )
        }
    }
}

// 语法高亮 VisualTransformation
private class SyntaxHighlightTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val highlighted = highlightSyntax(text.text)
        return TransformedText(highlighted, OffsetMapping.Identity)
    }
}

// 辅助函数：从 CodeReviewState 计算新文件的行 diff
private fun computeNewLineDiffs(state: CodeReviewState): Map<Int, LineChangeType> {
    val result = mutableMapOf<Int, LineChangeType>()
    state.changeBlocks.forEach { block ->
        if (block.newStartLine > 0) {
            for (i in block.newStartLine - 1 until block.newEndLine - 1) {
                result[i] = if (block.type == LineChangeType.Removed) {
                    LineChangeType.Unchanged
                } else {
                    block.type
                }
            }
        }
    }
    return result
}
