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
import androidx.compose.material.icons.filled.Refresh
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

// 代码编辑器：行号 + 可编辑 + 语法高亮 + 双指缩放 + 行 diff 高亮 + 悬浮审阅
@Composable
fun CodeEditor(
    text: TextFieldValue,
    onTextChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
    // 行级 diff 高亮
    lineDiffs: Map<Int, LineChangeType> = emptyMap(),
    // 待审阅修改
    pendingFilePath: String? = null,
    onAcceptChanges: (() -> Unit)? = null,
    onRejectChanges: (() -> Unit)? = null,
    onJumpToNextChange: (() -> Unit)? = null,
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
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
                            RoundedCornerShape(20.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                            RoundedCornerShape(20.dp)
                        )
                ) {
                    Text(
                        "待审阅",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                    IconButton(
                        onClick = { onJumpToNextChange?.invoke() },
                        modifier = Modifier.size(28.dp),
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                        ),
                    ) {
                        Icon(Icons.Default.Refresh, "定位修改", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(
                        onClick = { onAcceptChanges?.invoke() },
                        modifier = Modifier.size(28.dp),
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = Color(0xFF22CC22).copy(alpha = 0.15f)
                        ),
                    ) {
                        Icon(Icons.Default.Check, "确认", Modifier.size(16.dp), tint = Color(0xFF22CC22))
                    }
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
