package com.medeide.jh.screens.home.landscape.workspace.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun NormalEditMode(
    text: TextFieldValue,
    onTextChange: (TextFieldValue) -> Unit,
    readOnly: Boolean = false,
    visualTransformation: VisualTransformation? = null,
    searchScrollVersion: Int = 0,
) {
    val scrollState = rememberScrollState()
    val hScrollState = rememberScrollState()
    val fontSizeState = remember { mutableFloatStateOf(13f) }
    val fontSize = fontSizeState.floatValue

    // 通过搜索导航点击时自动将光标行滚动到中间位置
    val density = LocalDensity.current
    LaunchedEffect(searchScrollVersion) {
        if (searchScrollVersion == 0) return@LaunchedEffect
        val cursorLine = text.text.substring(0, text.selection.start.coerceIn(0, text.text.length))
            .count { it == '\n' }
        val lineHeightPx = with(density) { (fontSize * 1.5f).sp.toPx() }
        val viewportHeightPx = scrollState.viewportSize
        val targetScrollPx = if (viewportHeightPx > 0) {
            ((cursorLine + 0.5f) * lineHeightPx - viewportHeightPx / 2f).toInt()
                .coerceIn(0, scrollState.maxValue)
        } else {
            (cursorLine * lineHeightPx).toInt().coerceAtMost(scrollState.maxValue)
        }
        scrollState.animateScrollTo(targetScrollPx)
    }

    val lineCount = remember(text.text) { text.text.lines().size }
    val lineNumbersText = remember(lineCount) { (1..lineCount).joinToString("\n") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitEachGesture {
                    var lastSpan = 0f
                    var multiTouchActive = false
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.size >= 2) {
                            multiTouchActive = true
                            val p1 = pressed[0].position
                            val p2 = pressed[1].position
                            val span = (p1 - p2).getDistance()
                            if (lastSpan > 0f) {
                                fontSizeState.floatValue = (fontSizeState.floatValue * span / lastSpan)
                                    .coerceIn(8f, 32f)
                            }
                            lastSpan = span
                            pressed.forEach { it.consume() }
                        } else if (multiTouchActive && pressed.size < 2) break
                        else lastSpan = 0f
                    } while (true)
                }
            }
            .verticalScroll(scrollState)
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            // 行号列
            Text(
                text = lineNumbersText,
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = fontSize.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    lineHeight = (fontSize * 1.5f).sp,
                ),
                modifier = Modifier
                    .widthIn(min = 40.dp)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
            )

            // 行号分隔线
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .padding(vertical = 8.dp)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)),
            )

            // 编辑区域
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .horizontalScroll(hScrollState)
                    .padding(end = 12.dp)
                    .padding(vertical = 8.dp),
            ) {
                BasicTextField(
                    value = text,
                    onValueChange = onTextChange,
                    readOnly = readOnly,
                    textStyle = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = fontSize.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = (fontSize * 1.5f).sp,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    visualTransformation = visualTransformation ?: SyntaxHighlightTransformation(),
                    decorationBox = { innerTextField ->
                        if (text.text.isEmpty()) {
                            Text(
                                "// 开始输入代码…",
                                style = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = fontSize.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                                    lineHeight = (fontSize * 1.5f).sp,
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
