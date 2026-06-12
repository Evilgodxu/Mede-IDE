package com.medeide.jh.screens.home.components.editor

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun NormalEditMode(
    text: TextFieldValue,
    onTextChange: (TextFieldValue) -> Unit,
    readOnly: Boolean = false,
) {
    val scrollState = rememberScrollState()
    val hScrollState = rememberScrollState()
    val fontSizeState = remember { mutableFloatStateOf(12f) }
    val fontSize = fontSizeState.floatValue

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
                        val count = pressed.size

                        if (count >= 2) {
                            multiTouchActive = true
                            val p1 = pressed[0].position
                            val p2 = pressed[1].position
                            val span = (p1 - p2).getDistance()

                            if (lastSpan > 0f) {
                                val ratio = span / lastSpan
                                fontSizeState.floatValue = (fontSizeState.floatValue * ratio)
                                    .coerceIn(8f, 32f)
                            }
                            lastSpan = span
                            pressed.forEach { it.consume() }
                        } else if (multiTouchActive && count < 2) {
                            break
                        } else {
                            lastSpan = 0f
                        }
                    } while (true)
                }
            }
            .verticalScroll(scrollState)
            .horizontalScroll(hScrollState)
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
            visualTransformation = SyntaxHighlightTransformation(),
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp),
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
