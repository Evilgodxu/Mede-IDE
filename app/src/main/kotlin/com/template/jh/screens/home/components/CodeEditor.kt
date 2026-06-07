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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.template.jh.core.editor.DiffHighlightTransformation
import com.template.jh.core.editor.LineChangeType
import com.template.jh.core.editor.highlightSyntax
import kotlin.math.abs

// Diff行数据
data class DiffLine(
    val type: LineChangeType,
    val oldLineNum: Int?, // 旧文件行号（1-based）
    val newLineNum: Int?, // 新文件行号（1-based）
    val content: String,
)

// 代码编辑器：行号 + 可编辑 + 语法高亮 + 双指缩放 + 行 diff 高亮 + 悬浮审阅
@Composable
fun CodeEditor(
    text: TextFieldValue,
    onTextChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
    // 行级 diff 高亮
    lineDiffs: Map<Int, LineChangeType> = emptyMap(),
    // 原始内容（用于diff审阅模式显示旧代码）
    originalContent: String? = null,
    // 待审阅修改
    pendingFilePath: String? = null,
    onAcceptChanges: (() -> Unit)? = null,
    onRejectChanges: (() -> Unit)? = null,
    onJumpToPrevChange: (() -> Unit)? = null,
    onJumpToNextChange: (() -> Unit)? = null,
    // 当前修改索引和总修改数（用于显示 1/13）
    currentChangeIndex: Int = 0,
    totalChanges: Int = 0,
    // 光标位置回调：返回行号（1-based）
    onCursorChange: ((Int) -> Unit)? = null,
) {
    val clipboardManager = LocalClipboardManager.current
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
    val hasPending = pendingFilePath != null
    val isReviewMode = hasPending && originalContent != null

    // 光标位置跟踪：从 selection 计算行号回调
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
                    clipboardManager.setText(AnnotatedString(
                        "CodeEditor zoom: ${e::class.simpleName}: ${e.message}\n${e.stackTraceToString()}"
                    ))
                }
            }
    ) {
        if (isReviewMode) {
            // 审阅模式：并排显示旧代码（左）和新代码（右）
            CodeReviewView(
                originalContent = originalContent!!,
                newContent = text.text,
                lineDiffs = lineDiffs,
                fontSizeSp = fontSizeSp,
                lineHeightSp = lineHeightSp,
                lineHeightDp = lineHeightDp,
                scrollState = scrollState,
            )
        } else {
            // 普通编辑模式
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = verticalPadding)
            ) {
                // 行号栏
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

        // 右上角悬浮审阅按钮（待确认修改时显示）
        if (hasPending) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 4.dp, end = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = Alignment.End,
            ) {
                // 审阅条
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
                    // 上箭头 - 上一处修改
                    IconButton(
                        onClick = { onJumpToPrevChange?.invoke() },
                        modifier = Modifier.size(24.dp),
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                        ),
                    ) {
                        Icon(Icons.Default.KeyboardArrowUp, "上一处", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                    // 计数显示（如 1/13）
                    Text(
                        text = "${currentChangeIndex + 1}/$totalChanges",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                    // 下箭头 - 下一处修改
                    IconButton(
                        onClick = { onJumpToNextChange?.invoke() },
                        modifier = Modifier.size(24.dp),
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                        ),
                    ) {
                        Icon(Icons.Default.KeyboardArrowDown, "下一处", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
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
                        onClick = { onAcceptChanges?.invoke() },
                        modifier = Modifier.size(28.dp),
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = Color(0xFF22CC22).copy(alpha = 0.15f)
                        ),
                    ) {
                        Icon(Icons.Default.Check, "确认", Modifier.size(16.dp), tint = Color(0xFF22CC22))
                    }
                    // 取消按钮
                    IconButton(
                        onClick = { onRejectChanges?.invoke() },
                        modifier = Modifier.size(28.dp),
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = Color(0xFFCC2222).copy(alpha = 0.15f)
                        ),
                    ) {
                        Icon(Icons.Default.Close, "取消", Modifier.size(16.dp), tint = Color(0xFFCC2222))
                    }
                }
            }
        }
    }
}

// 语法高亮 VisualTransformation
private class SyntaxHighlightTransformation : VisualTransformation {
    override fun filter(text: androidx.compose.ui.text.AnnotatedString): TransformedText {
        val highlighted = highlightSyntax(text.text)
        return TransformedText(highlighted, OffsetMapping.Identity)
    }
}

// 代码审阅视图：并排显示旧代码（红色）和新代码（绿色）
@Composable
private fun CodeReviewView(
    originalContent: String,
    newContent: String,
    lineDiffs: Map<Int, LineChangeType>,
    fontSizeSp: Float,
    lineHeightSp: Float,
    lineHeightDp: Dp,
    scrollState: androidx.compose.foundation.ScrollState,
) {
    val oldLines = originalContent.lines()
    val newLines = newContent.lines()
    val maxLines = maxOf(oldLines.size, newLines.size)

    // 计算每行的变更类型
    fun getOldLineType(lineNum: Int): LineChangeType? {
        if (lineNum > oldLines.size) return null
        // 如果旧行在新文件中不存在或被修改，标记为Removed
        val newLineType = lineDiffs[lineNum - 1]
        return when (newLineType) {
            LineChangeType.Removed -> LineChangeType.Removed
            LineChangeType.Modified -> LineChangeType.Removed
            else -> LineChangeType.Unchanged
        }
    }

    fun getNewLineType(lineNum: Int): LineChangeType? {
        if (lineNum > newLines.size) return null
        return lineDiffs[lineNum - 1]
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 4.dp)
            .verticalScroll(scrollState)
    ) {
        // 表头
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                "旧代码",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
            Text(
                "新代码",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF22CC22),
            )
        }

        // 逐行对比
        for (i in 0 until maxLines) {
            val oldLine = oldLines.getOrNull(i) ?: ""
            val newLine = newLines.getOrNull(i) ?: ""
            val oldType = getOldLineType(i + 1)
            val newType = getNewLineType(i + 1)

            // 确定背景色
            val oldBg = when (oldType) {
                LineChangeType.Removed -> Color(0x55CC2222) // 红色，删除
                else -> Color.Transparent
            }
            val newBg = when (newType) {
                LineChangeType.Added -> Color(0x5522CC22)   // 绿色，新增
                LineChangeType.Modified -> Color(0x55CCAA00) // 黄色，修改
                else -> Color.Transparent
            }

            // 只显示有变更的行以及上下文（简化视图）
            val hasChange = oldType == LineChangeType.Removed ||
                    newType == LineChangeType.Added ||
                    newType == LineChangeType.Modified

            if (hasChange || i < oldLines.size && i < newLines.size) {
                Row(
                    modifier = Modifier.fillMaxWidth()
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
        }

        Spacer(modifier = Modifier.height(60.dp)) // 底部留白给悬浮按钮
    }
}
