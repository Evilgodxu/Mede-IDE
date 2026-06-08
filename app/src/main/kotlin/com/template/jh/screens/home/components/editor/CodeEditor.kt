package com.template.jh.screens.home.components.editor

import android.content.ClipData
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.template.jh.core.editor.CodeReviewState
import com.template.jh.core.editor.LineChangeType
import com.template.jh.screens.home.components.ReviewNavigator
import kotlinx.coroutines.launch

/**
 * 代码编辑器编排层 - 触屏优先设计
 *
 * 触屏优化：
 * 1. 底部浮动操作栏替代 PC 物理按键（Tab/Shift+Tab/Ctrl+/）
 * 2. 行号点击选中整行
 * 3. 长按菜单增强（全选/剪切/粘贴）
 * 4. 内联手势（双击编辑、捏合缩放、长按菜单）限于内容区域不干扰覆盖层
 *
 * 内部分为三种渲染模式：
 * 1. ReviewPreviewMode - 审查预览
 * 2. ReviewEditMode - 审查编辑
 * 3. NormalEditMode - 普通编辑
 */
@Composable
fun CodeEditor(
    text: TextFieldValue,
    onTextChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
    lineDiffs: Map<Int, LineChangeType> = emptyMap(),
    originalContent: String? = null,
    reviewState: CodeReviewState? = null,
    pendingFilePath: String? = null,
    onAcceptChanges: (() -> Unit)? = null,
    onRejectChanges: (() -> Unit)? = null,
    onJumpToPrevChange: (() -> Unit)? = null,
    onJumpToNextChange: (() -> Unit)? = null,
    onAcceptBlock: ((Int) -> Unit)? = null,
    onRejectBlock: ((Int) -> Unit)? = null,
    onNavigateToBlock: ((Int) -> Unit)? = null,
    showReviewPanel: Boolean = false,
    onToggleReviewPanel: (() -> Unit)? = null,
    currentChangeIndex: Int = 0,
    totalChanges: Int = 0,
    onCursorChange: ((Int) -> Unit)? = null,
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

    val hasReviewState = reviewState != null && reviewState.changeBlocks.isNotEmpty()
    val hasLegacyPending = pendingFilePath != null && originalContent != null
    val isReviewMode = hasReviewState || hasLegacyPending
    val isNormalEditable = !readOnly && !isReviewMode

    // 触屏工具状态
    var showEditActions by remember { mutableStateOf(false) }
    var hasSelection by remember { mutableStateOf(false) }

    // 光标位置跟踪
    val cursorLine = remember(text.selection) {
        text.text.substring(0, text.selection.start).count { it == '\n' } + 1
    }
    LaunchedEffect(cursorLine) {
        onCursorChange?.invoke(cursorLine)
    }
    // 监测是否有选中文本
    LaunchedEffect(text.selection) {
        hasSelection = text.selection.start != text.selection.end
    }

    Column(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
        ) {
        // 行号点击 → 选中整行
        val onLineTap: (Int) -> Unit = { lineIndex ->
            val lines = text.text.lines()
            val start = if (lineIndex == 0) 0 else text.text.indexOf('\n', lines.take(lineIndex).sumOf { it.length + 1 } - 1) + 1
            val end = if (lineIndex >= lines.size - 1) text.text.length else text.text.indexOf('\n', start)
            onTextChange(text.copy(selection = TextRange(start, end)))
        }

        // 主内容区域 - 选择三种模式之一
        when {
            isReviewMode && !isEditMode -> ReviewPreviewMode(
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
                onLongPress = { sel, offset ->
                    selectedText = sel
                    menuOffset = offset
                    showContextMenu = true
                },
                onZoom = { zoom -> fontSizeSp = (fontSizeSp * zoom).coerceIn(8f, 40f) },
                onLineTap = onLineTap,
            )
            isReviewMode && isEditMode -> ReviewEditMode(
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
                onExitEditMode = { isEditMode = false },
                onZoom = { zoom -> fontSizeSp = (fontSizeSp * zoom).coerceIn(8f, 40f) },
                onDoubleTap = { isEditMode = true },
                onLongPress = { sel, offset ->
                    selectedText = sel
                    menuOffset = offset
                    showContextMenu = true
                },
                onLineTap = onLineTap,
            )
            else -> NormalEditMode(
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
                readOnly = readOnly,
                isEditMode = isEditMode,
                onZoom = { zoom -> fontSizeSp = (fontSizeSp * zoom).coerceIn(8f, 40f) },
                onDoubleTap = { if (!readOnly) isEditMode = true },
                onLongPress = { sel, offset ->
                    selectedText = sel
                    menuOffset = offset
                    showContextMenu = true
                },
                onExitEditMode = { isEditMode = false },
                onLineTap = onLineTap,
            )
        }

        // 右上角导航器（审查模式）- 使用大触摸目标的新版
        if (isReviewMode) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 8.dp, end = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = Alignment.End,
            ) {
                if (isEditMode) {
                    Row(
                        modifier = Modifier
                            .background(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                                RoundedCornerShape(12.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .clickable { isEditMode = false },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "编辑中",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }

                val navReviewState = reviewState
                if (hasReviewState && navReviewState != null) {
                    ReviewNavigator(
                        reviewState = navReviewState,
                        onPrev = { onJumpToPrevChange?.invoke() },
                        onNext = { onJumpToNextChange?.invoke() },
                        onAcceptCurrent = {
                            navReviewState.currentBlockIndex.let { idx ->
                                if (idx in navReviewState.changeBlocks.indices) {
                                    onAcceptBlock?.invoke(idx)
                                }
                            }
                        },
                        onRejectCurrent = {
                            navReviewState.currentBlockIndex.let { idx ->
                                if (idx in navReviewState.changeBlocks.indices) {
                                    onRejectBlock?.invoke(idx)
                                }
                            }
                        },
                        onOpenPanel = { onToggleReviewPanel?.invoke() },
                    )
                }
            }
        }

        // 长按菜单 - 触屏增强版
        DropdownMenu(
            expanded = showContextMenu,
            onDismissRequest = { showContextMenu = false },
            offset = androidx.compose.ui.unit.DpOffset(menuOffset.x.dp, menuOffset.y.dp),
        ) {
            DropdownMenuItem(
                text = { Text("剪切") },
                onClick = {
                    scope.launch {
                        clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("", selectedText)))
                        val newText = text.text.replaceRange(text.selection.start, text.selection.end, "")
                        onTextChange(TextFieldValue(newText, TextRange(text.selection.start)))
                    }
                    showContextMenu = false
                },
                enabled = hasSelection && !readOnly,
            )
            DropdownMenuItem(
                text = { Text("复制") },
                onClick = {
                    scope.launch {
                        clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("", selectedText)))
                    }
                    showContextMenu = false
                },
                enabled = hasSelection,
            )
            DropdownMenuItem(
                text = { Text("粘贴") },
                onClick = {
                    scope.launch {
                        val clip = clipboard.getClipEntry()?.clipData?.getItemAt(0)?.text ?: return@launch
                        val cur = text.selection
                        val newText = text.text.replaceRange(cur.start, cur.end, clip)
                        val newCursor = cur.start + clip.length
                        onTextChange(TextFieldValue(newText, TextRange(newCursor)))
                    }
                    showContextMenu = false
                },
                enabled = !readOnly,
            )
            DropdownMenuItem(
                text = { Text("全选") },
                onClick = {
                    onTextChange(text.copy(selection = TextRange(0, text.text.length)))
                    showContextMenu = false
                },
            )
            DropdownMenuItem(
                text = { Text("添加到对话") },
                onClick = {
                    onAddToChat?.invoke(selectedText)
                    showContextMenu = false
                },
                enabled = hasSelection,
            )
        }
    } // end of inner Box

    // === 底部浮动操作栏（触屏专有，替代 PC 物理按键） ===
    if (isNormalEditable || isReviewMode && text.text.isNotEmpty()) {
        EditorFloatingToolbar(
            onIndent = {
                onTextChange(text.indent())
            },
            onDedent = {
                onTextChange(text.dedent())
            },
            onToggleComment = {
                onTextChange(text.toggleComment())
            },
            onZoomIn = {
                fontSizeSp = (fontSizeSp * 1.2f).coerceAtMost(40f)
            },
            onZoomOut = {
                fontSizeSp = (fontSizeSp / 1.2f).coerceAtLeast(8f)
            },
        )
    } // end of if (toolbar condition)
} // end of Column
} // end of CodeEditor
