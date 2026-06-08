package com.template.jh.screens.home.components.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.template.jh.core.editor.DiffHighlightTransformation
import com.template.jh.core.editor.LineChangeType


// ===== 普通编辑模式 =====
@Composable
fun NormalEditMode(
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
    isEditMode: Boolean,
    onZoom: (Float) -> Unit,
    onDoubleTap: () -> Unit,
    onLongPress: (String, Offset) -> Unit,
    onExitEditMode: () -> Unit,
    onLineTap: ((Int) -> Unit)? = null,
) {
    val lineCount = text.text.lines().size.coerceAtLeast(1)
    val lines = text.text.lines()

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = verticalPadding)
    ) {
        EditorLineNumbers(
            lineCount = lineCount,
            lineDiffs = lineDiffs,
            fontSizeSp = fontSizeSp,
            lineHeightSp = lineHeightSp,
            lineHeightDp = lineHeightDp,
            lineNumWidth = lineNumWidth,
            scrollState = scrollState,
            onLineTap = onLineTap,
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(scrollState)
                .horizontalScroll(hScrollState)
                .pointerInput(Unit) {
                    detectTransformGestures { _, _, gestureZoom, _ ->
                        onZoom(gestureZoom)
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = { onDoubleTap() },
                        onLongPress = { offset ->
                            val lineIndex = (offset.y / lineHeightDp.toPx()).toInt()
                            if (lineIndex in lines.indices) onLongPress(lines[lineIndex], offset)
                        }
                    )
                }
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
                            "// 开始输入代码…",
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

// ===== 审查预览模式 =====
@Composable
fun ReviewPreviewMode(
    reviewState: com.template.jh.core.editor.CodeReviewState?,
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
    onLineTap: ((Int) -> Unit)? = null,
) {
    val lines = newContent.lines()
    val lineCount = lines.size.coerceAtLeast(1)
    val newDiffs = reviewState?.let { computeNewLineDiffs(it) } ?: lineDiffs

    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 4.dp)
        ) {
            EditorLineNumbers(
                lineCount = lineCount,
                lineDiffs = newDiffs,
                fontSizeSp = fontSizeSp,
                lineHeightSp = lineHeightSp,
                lineHeightDp = lineHeightDp,
                lineNumWidth = lineNumWidth,
                scrollState = scrollState,
                onLineTap = onLineTap,
            )

            SelectionContainer {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(scrollState)
                        .horizontalScroll(hScrollState)
                        .pointerInput(Unit) {
                            detectTransformGestures { _, _, gestureZoom, _ ->
                                onZoom(gestureZoom)
                            }
                        }
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onDoubleTap = { onDoubleTap() },
                                onLongPress = { offset ->
                                    val lineIndex = (offset.y / lineHeightDp.toPx()).toInt()
                                    if (lineIndex in lines.indices) onLongPress(lines[lineIndex], offset)
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
                            overflow = androidx.compose.ui.text.style.TextOverflow.Visible,
                        )
                    }
                }
            }
        }
    }
}

// ===== 审查编辑模式 =====
@Composable
fun ReviewEditMode(
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
    onZoom: (Float) -> Unit,
    onDoubleTap: () -> Unit,
    onLongPress: (String, Offset) -> Unit,
    onLineTap: ((Int) -> Unit)? = null,
) {
    val lineCount = text.text.lines().size.coerceAtLeast(1)
    val lines = text.text.lines()

    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = verticalPadding)
        ) {
            EditorLineNumbers(
                lineCount = lineCount,
                lineDiffs = lineDiffs,
                fontSizeSp = fontSizeSp,
                lineHeightSp = lineHeightSp,
                lineHeightDp = lineHeightDp,
                lineNumWidth = lineNumWidth,
                scrollState = scrollState,
                onLineTap = onLineTap,
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(scrollState)
                    .horizontalScroll(hScrollState)
                    .pointerInput(Unit) {
                        detectTransformGestures { _, _, gestureZoom, _ ->
                            onZoom(gestureZoom)
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = { onDoubleTap() },
                            onLongPress = { offset ->
                                val lineIndex = (offset.y / lineHeightDp.toPx()).toInt()
                                if (lineIndex in lines.indices) onLongPress(lines[lineIndex], offset)
                            }
                        )
                    }
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
}
