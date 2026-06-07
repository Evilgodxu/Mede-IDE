package com.template.jh.screens.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import com.template.jh.core.editor.highlightSyntax
import kotlin.math.abs

// 代码编辑器：行号 + 可编辑 + 语法高亮 + 双指缩放（panZoomLock=false 避免滚动锁定）
@Composable
fun CodeEditor(
    text: TextFieldValue,
    onTextChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
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

    // 外层 Box 负责双指缩放（脱离内层 scroll 事件竞争）
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .pointerInput(Unit) {
                try {
                    detectTransformGestures(panZoomLock = false) { _, _, zoom, _ ->
                        // 阈值降至 0.03，panZoomLock=false 防止滚动锁定缩放
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
            // 行号栏（固定行高 = lineHeightDp，与编辑器行高完全一致）
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(lineNumWidth)
                    .verticalScroll(scrollState)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    .padding(horizontal = 4.dp),
            ) {
                for (i in 1..lineCount) {
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
                            .height(lineHeightDp),
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
}

// 语法高亮 VisualTransformation
private class SyntaxHighlightTransformation : VisualTransformation {
    override fun filter(text: androidx.compose.ui.text.AnnotatedString): TransformedText {
        val highlighted = highlightSyntax(text.text)
        return TransformedText(highlighted, OffsetMapping.Identity)
    }
}
