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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.template.jh.core.editor.CodeReviewState
import com.template.jh.core.editor.LineChangeType
import kotlinx.coroutines.launch

/**
 * 代码编辑器编排层 - 单一职责：模式选择、状态协调、手势分发
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
                onZoom = { zoom -> fontSizeSp = (fontSizeSp * zoom).coerceIn(8f, 32f) },
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
                onZoom = { zoom -> fontSizeSp = (fontSizeSp * zoom).coerceIn(8f, 32f) },
                onDoubleTap = { isEditMode = true },
                onLongPress = { sel, offset ->
                    selectedText = sel
                    menuOffset = offset
                    showContextMenu = true
                },
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
                onZoom = { zoom -> fontSizeSp = (fontSizeSp * zoom).coerceIn(8f, 32f) },
                onDoubleTap = { if (!readOnly) isEditMode = true },
                onLongPress = { sel, offset ->
                    selectedText = sel
                    menuOffset = offset
                    showContextMenu = true
                },
                onExitEditMode = { isEditMode = false },
            )
        }

        // 右上角导航器（审查模式）
        if (isReviewMode) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 4.dp, end = 4.dp),
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
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .clickable { isEditMode = false },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "编辑中",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }

                val navReviewState = reviewState
                if (hasReviewState && navReviewState != null) {
                    ReviewNavigator(
                        totalChanges = navReviewState.totalCount,
                        currentIndex = navReviewState.currentBlockIndex,
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

        // 长按菜单
        DropdownMenu(
            expanded = showContextMenu,
            onDismissRequest = { showContextMenu = false },
            offset = androidx.compose.ui.unit.DpOffset(menuOffset.x.dp, menuOffset.y.dp),
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
                        clipboard.setClipEntry(androidx.compose.ui.platform.ClipEntry(ClipData.newPlainText("", selectedText)))
                    }
                    showContextMenu = false
                }
            )
        }
    }
}
