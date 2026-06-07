package com.template.jh.screens.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalDensity
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
import com.template.jh.core.editor.DiffHighlightTransformation
import com.template.jh.core.editor.LineChangeType
import com.template.jh.core.editor.backgroundColor
import com.template.jh.core.editor.color
import com.template.jh.core.editor.highlightSyntax
import kotlin.math.abs

// Diff行数据
data class DiffLine(
    val type: LineChangeType,
    val oldLineNum: Int?, // 旧文件行号（1-based）
    val newLineNum: Int?, // 新文件行号（1-based）
    val content: String,
)

/**
 * 代码编辑器：支持普通编辑模式和代码审查模式
 *
 * 审查模式特性：
 * 1. 旧代码作为备份（左侧），新代码作为当前文件（右侧）
 * 2. 未审查的修改高亮显示
 * 3. 支持逐项确认/拒绝
 * 4. 确认后锁定，无法单独回退
 */
@Composable
fun CodeEditor(
    text: TextFieldValue,
    onTextChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
    // 行级 diff 高亮（新文件的变更）- 兼容旧接口
    lineDiffs: Map<Int, LineChangeType> = emptyMap(),
    // 旧文件的行级 diff 高亮 - 兼容旧接口
    oldLineDiffs: Map<Int, LineChangeType> = emptyMap(),
    // 原始内容（用于diff审阅模式显示旧代码）- 兼容旧接口
    originalContent: String? = null,
    // 代码审查状态（新的审查系统）
    reviewState: CodeReviewState? = null,
    // 待审阅修改（兼容旧接口）
    pendingFilePath: String? = null,
    onAcceptChanges: (() -> Unit)? = null,
    onRejectChanges: (() -> Unit)? = null,
    onJumpToPrevChange: (() -> Unit)? = null,
    onJumpToNextChange: (() -> Unit)? = null,
    // 修改块级别的回调（新的审查系统）
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
) {
    val clipboard = LocalClipboard.current
    val density = LocalDensity.current
    val scrollState = rememberScrollState()
    val hScrollState = rememberScrollState()
    val lineCount = remember(text.text) { text.text.lines().size.coerceAtLeast(1) }

    var fontSizeSp by remember { mutableFloatStateOf(12f) }

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
            .pointerInput(Unit) {
                try {
                    detectTransformGestures(panZoomLock = false) { _, _, zoom, _ ->
                        if (abs(zoom - 1f) > 0.03f) {
                            fontSizeSp = (fontSizeSp * zoom).coerceIn(8f, 32f)
                        }
                    }
                } catch (e: Exception) {
                    // 忽略手势异常
                }
            }
    ) {
        // 审查模式：显示审查面板或并排视图
        if (isReviewMode) {
            val currentReviewState = reviewState
            if (hasReviewState && showReviewPanel && currentReviewState != null) {
                // 新审查系统：显示审查面板
                CodeReviewPanel(
                    reviewState = currentReviewState,
                    onAcceptBlock = { onAcceptBlock?.invoke(it) ?: onAcceptChanges?.invoke() },
                    onRejectBlock = { onRejectBlock?.invoke(it) ?: onRejectChanges?.invoke() },
                    onNavigateToBlock = { onNavigateToBlock?.invoke(it) },
                    fontSizeSp = fontSizeSp,
                    lineHeightSp = lineHeightSp,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // 并排显示新旧代码
                val oldContent = currentReviewState?.oldContent ?: originalContent!!
                val newContent = currentReviewState?.newContent ?: text.text
                val oldDiffs = currentReviewState?.let { computeOldLineDiffs(it) } ?: oldLineDiffs
                val newDiffs = currentReviewState?.let { computeNewLineDiffs(it) } ?: lineDiffs

                SideBySideDiffView(
                    oldContent = oldContent,
                    newContent = newContent,
                    oldLineDiffs = oldDiffs,
                    newLineDiffs = newDiffs,
                    reviewState = reviewState,
                    currentBlockIndex = reviewState?.currentBlockIndex ?: currentChangeIndex,
                    fontSizeSp = fontSizeSp,
                    lineHeightSp = lineHeightSp,
                    lineHeightDp = lineHeightDp,
                    scrollState = scrollState,
                    onAcceptBlock = onAcceptBlock,
                    onRejectBlock = onRejectBlock,
                    onNavigateToBlock = onNavigateToBlock,
                )
            }
        } else {
            // 普通编辑模式
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = verticalPadding)
            ) {
                // 行号栏
                LineNumberColumn(
                    lineCount = lineCount,
                    lineDiffs = lineDiffs,
                    lineNumWidth = lineNumWidth,
                    fontSizeSp = fontSizeSp,
                    lineHeightSp = lineHeightSp,
                    lineHeightDp = lineHeightDp,
                    scrollState = scrollState
                )

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
                        visualTransformation = if (lineDiffs.isNotEmpty()) {
                            DiffHighlightTransformation(lineDiffs)
                        } else {
                            SyntaxHighlightTransformation()
                        },
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

        // 右上角悬浮导航器（审查模式）
        if (isReviewMode) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 4.dp, end = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = Alignment.End,
            ) {
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
                } else {
                    // 兼容旧版导航器
                    LegacyReviewNavigator(
                        currentIndex = currentChangeIndex,
                        totalCount = totalChanges,
                        onPrev = { onJumpToPrevChange?.invoke() },
                        onNext = { onJumpToNextChange?.invoke() },
                        onAccept = { onAcceptChanges?.invoke() },
                        onReject = { onRejectChanges?.invoke() }
                    )
                }
            }
        }
    }
}

/**
 * 行号列
 */
@Composable
private fun LineNumberColumn(
    lineCount: Int,
    lineDiffs: Map<Int, LineChangeType>,
    lineNumWidth: Dp,
    fontSizeSp: Float,
    lineHeightSp: Float,
    lineHeightDp: Dp,
    scrollState: androidx.compose.foundation.ScrollState,
) {
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
}

/**
 * 并排 Diff 视图：左侧旧代码，右侧新代码
 */
@Composable
private fun SideBySideDiffView(
    oldContent: String,
    newContent: String,
    oldLineDiffs: Map<Int, LineChangeType>,
    newLineDiffs: Map<Int, LineChangeType>,
    reviewState: CodeReviewState?,
    currentBlockIndex: Int,
    fontSizeSp: Float,
    lineHeightSp: Float,
    lineHeightDp: Dp,
    scrollState: androidx.compose.foundation.ScrollState,
    onAcceptBlock: ((Int) -> Unit)?,
    onRejectBlock: ((Int) -> Unit)?,
    onNavigateToBlock: ((Int) -> Unit)?,
) {
    val oldLines = oldContent.lines()
    val newLines = newContent.lines()
    val maxLines = maxOf(oldLines.size, newLines.size)

    // 获取当前修改块的行范围（用于高亮当前块）
    val currentBlock = reviewState?.changeBlocks?.getOrNull(currentBlockIndex)
    val currentOldRange = currentBlock?.let { it.oldStartLine..<it.oldEndLine } ?: IntRange.EMPTY
    val currentNewRange = currentBlock?.let { it.newStartLine..<it.newEndLine } ?: IntRange.EMPTY

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 4.dp)
            .verticalScroll(scrollState)
    ) {
        // 表头
        DiffHeader()

        // 逐行对比
        for (i in 0 until maxLines) {
            val lineNum = i + 1
            val oldLine = oldLines.getOrNull(i) ?: ""
            val newLine = newLines.getOrNull(i) ?: ""
            val oldType = oldLineDiffs[i] ?: LineChangeType.Unchanged
            val newType = newLineDiffs[i] ?: LineChangeType.Unchanged

            // 确定是否是当前修改块的行
            val isCurrentBlockLine = lineNum in currentOldRange || lineNum in currentNewRange

            // 确定背景色
            val oldBg = when {
                isCurrentBlockLine -> oldType.getColor().copy(alpha = 0.7f)
                oldType == LineChangeType.Removed -> Color(0x55CC2222)
                else -> Color.Transparent
            }
            val newBg = when {
                isCurrentBlockLine -> newType.getColor().copy(alpha = 0.7f)
                newType == LineChangeType.Added -> Color(0x5522CC22)
                newType == LineChangeType.Modified -> Color(0x55CCAA00)
                else -> Color.Transparent
            }

            // 只显示有变更的行以及上下文
            val hasChange = oldType == LineChangeType.Removed ||
                    newType == LineChangeType.Added ||
                    newType == LineChangeType.Modified

            if (hasChange || (i < oldLines.size && i < newLines.size)) {
                DiffRow(
                    lineNum = lineNum,
                    oldLine = oldLine,
                    newLine = newLine,
                    oldType = oldType,
                    newType = newType,
                    oldBg = oldBg,
                    newBg = newBg,
                    fontSizeSp = fontSizeSp,
                    lineHeightSp = lineHeightSp,
                    lineHeightDp = lineHeightDp,
                    isCurrentBlock = isCurrentBlockLine
                )
            }
        }

        Spacer(modifier = Modifier.height(60.dp))
    }
}

@Composable
private fun DiffHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            "旧代码（备份）",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error,
        )
        Text(
            "新代码（当前）",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF22CC22),
        )
    }
}

@Composable
private fun DiffRow(
    lineNum: Int,
    oldLine: String,
    newLine: String,
    oldType: LineChangeType,
    newType: LineChangeType,
    oldBg: Color,
    newBg: Color,
    fontSizeSp: Float,
    lineHeightSp: Float,
    lineHeightDp: Dp,
    isCurrentBlock: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isCurrentBlock) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent)
    ) {
        // 旧代码列
        Box(
            modifier = Modifier
                .weight(1f)
                .background(oldBg)
                .padding(horizontal = 8.dp)
        ) {
            Text(
                text = if (oldType == LineChangeType.Removed) oldLine else oldLine.takeIf { it.isNotEmpty() } ?: "",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = fontSizeSp.sp,
                    color = if (oldType == LineChangeType.Removed)
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    lineHeight = lineHeightSp.sp,
                ),
                modifier = Modifier.height(lineHeightDp),
            )
        }

        // 分隔线
        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.outlineVariant)
        )

        // 新代码列
        Box(
            modifier = Modifier
                .weight(1f)
                .background(newBg)
                .padding(horizontal = 8.dp)
        ) {
            Text(
                text = newLine,
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = fontSizeSp.sp,
                    color = when (newType) {
                        LineChangeType.Added -> Color(0xFF22CC22)
                        LineChangeType.Modified -> Color(0xFFCCAA00)
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                    lineHeight = lineHeightSp.sp,
                ),
                modifier = Modifier.height(lineHeightDp),
            )
        }
    }
}

/**
 * 旧版审查导航器（兼容）
 */
@Composable
private fun LegacyReviewNavigator(
    currentIndex: Int,
    totalCount: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onAccept: () -> Unit,
    onReject: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
                RoundedCornerShape(20.dp)
            )
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .border(
                1.dp,
                MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                RoundedCornerShape(20.dp)
            )
    ) {
        // 上箭头
        IconButton(
            onClick = onPrev,
            modifier = Modifier.size(24.dp),
            colors = androidx.compose.material3.IconButtonDefaults.iconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
            ),
        ) {
            Icon(
                androidx.compose.material.icons.Icons.Default.KeyboardArrowUp,
                "上一处",
                Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }

        // 计数显示
        Text(
            text = "${currentIndex + 1}/$totalCount",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 4.dp),
        )

        // 下箭头
        IconButton(
            onClick = onNext,
            modifier = Modifier.size(24.dp),
            colors = androidx.compose.material3.IconButtonDefaults.iconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
            ),
        ) {
            Icon(
                androidx.compose.material.icons.Icons.Default.KeyboardArrowDown,
                "下一处",
                Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }

        // 分隔线
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(16.dp)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        )

        // 确认按钮
        IconButton(
            onClick = onAccept,
            modifier = Modifier.size(28.dp),
            colors = androidx.compose.material3.IconButtonDefaults.iconButtonColors(
                containerColor = Color(0xFF22CC22).copy(alpha = 0.15f)
            ),
        ) {
            Icon(
                androidx.compose.material.icons.Icons.Default.Check,
                "确认",
                Modifier.size(16.dp),
                tint = Color(0xFF22CC22)
            )
        }

        // 取消按钮
        IconButton(
            onClick = onReject,
            modifier = Modifier.size(28.dp),
            colors = androidx.compose.material3.IconButtonDefaults.iconButtonColors(
                containerColor = Color(0xFFCC2222).copy(alpha = 0.15f)
            ),
        ) {
            Icon(
                androidx.compose.material.icons.Icons.Default.Close,
                "取消",
                Modifier.size(16.dp),
                tint = Color(0xFFCC2222)
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

// 辅助函数：从 CodeReviewState 计算旧文件的行 diff
private fun computeOldLineDiffs(state: CodeReviewState): Map<Int, LineChangeType> {
    val result = mutableMapOf<Int, LineChangeType>()
    state.changeBlocks.forEach { block ->
        if (block.oldStartLine > 0) {
            for (i in block.oldStartLine - 1 until block.oldEndLine - 1) {
                result[i] = if (block.type == LineChangeType.Added) {
                    LineChangeType.Unchanged
                } else {
                    LineChangeType.Removed
                }
            }
        }
    }
    return result
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

// 扩展函数：获取变更类型的颜色
private fun LineChangeType.getColor(): Color = when (this) {
    LineChangeType.Added -> Color(0xFF22CC22)
    LineChangeType.Removed -> Color(0xFFCC2222)
    LineChangeType.Modified -> Color(0xFFCCAA00)
    LineChangeType.Unchanged -> Color.Transparent
}
