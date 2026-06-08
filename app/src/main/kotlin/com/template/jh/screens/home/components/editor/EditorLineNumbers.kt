package com.template.jh.screens.home.components.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.template.jh.core.editor.LineChangeType

// 行号列 - 触屏优化：点击行号可选中整行
@Composable
fun EditorLineNumbers(
    lineCount: Int,
    lineDiffs: Map<Int, LineChangeType>,
    fontSizeSp: Float,
    lineHeightSp: Float,
    lineHeightDp: Dp,
    lineNumWidth: Dp,
    scrollState: androidx.compose.foundation.ScrollState,
    onLineTap: ((lineIndex: Int) -> Unit)? = null,
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
            androidx.compose.material3.Text(
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
                    .clip(RoundedCornerShape(2.dp))
                    .background(rowBg)
                    .then(
                        if (onLineTap != null) {
                            Modifier.clickable(enabled = true) { onLineTap(i - 1) }
                        } else Modifier
                    ),
                textAlign = TextAlign.End,
            )
        }
    }
}
