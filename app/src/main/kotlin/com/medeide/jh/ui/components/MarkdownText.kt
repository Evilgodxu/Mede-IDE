package com.medeide.jh.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 轻量 Markdown 文本渲染组件
 * 支持：**加粗** *斜体* `行内代码` ```代码块``` -列表 1.编号 #标题
 */
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        val blocks = parseBlocks(text)
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Code -> {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = block.content,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                            .horizontalScroll(rememberScrollState())
                            .padding(10.dp),
                    )
                    Spacer(Modifier.height(4.dp))
                }
                is MdBlock.Text -> {
                    if (block.content.isBlank()) {
                        Spacer(Modifier.height(6.dp))
                    } else {
                        renderInline(block.content, block.isHeader, block.headerLevel)
                    }
                }
                is MdBlock.Bullet -> {
                    Row(modifier = Modifier.padding(start = 8.dp)) {
                        Text("•  ", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                        renderInline(block.content, bold = false)
                    }
                }
                is MdBlock.Ordered -> {
                    Row(modifier = Modifier.padding(start = 8.dp)) {
                        Text("${block.number}.  ", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                        renderInline(block.content, bold = false)
                    }
                }
            }
        }
    }
}

// ── 块级解析 ──

private sealed class MdBlock {
    data class Code(val content: String) : MdBlock()
    data class Text(val content: String, val isHeader: Boolean = false, val headerLevel: Int = 0) : MdBlock()
    data class Bullet(val content: String) : MdBlock()
    data class Ordered(val content: String, val number: Int) : MdBlock()
}

private fun parseBlocks(text: String): List<MdBlock> {
    val blocks = mutableListOf<MdBlock>()
    val lines = text.split("\n")
    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        when {
            // 代码块
            line.trimStart().startsWith("```") -> {
                val sb = StringBuilder()
                i++
                while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                    if (sb.isNotEmpty()) sb.append("\n")
                    sb.append(lines[i])
                    i++
                }
                blocks.add(MdBlock.Code(sb.toString()))
                i++ // skip closing ```
            }
            // 标题
            line.startsWith("#") -> {
                val level = line.takeWhile { it == '#' }.length
                val content = line.drop(level).trim()
                blocks.add(MdBlock.Text(content, isHeader = true, headerLevel = level.coerceIn(1, 3)))
                i++
            }
            // 无序列表
            line.trimStart().startsWith("- ") || line.trimStart().startsWith("* ") -> {
                val content = line.trimStart().removePrefix("- ").removePrefix("* ")
                blocks.add(MdBlock.Bullet(content))
                i++
            }
            // 有序列表
            line.matches(Regex("^\\s*\\d+\\.\\s.*")) -> {
                val num = line.trimStart().takeWhile { it.isDigit() }.toIntOrNull() ?: 1
                val content = line.trimStart().replace(Regex("^\\d+\\.\\s*"), "")
                blocks.add(MdBlock.Ordered(content, num))
                i++
            }
            else -> {
                blocks.add(MdBlock.Text(line))
                i++
            }
        }
    }
    return blocks
}

// ── 行内渲染 ──

@Composable
private fun renderInline(
    text: String,
    isHeader: Boolean = false,
    headerLevel: Int = 0,
    bold: Boolean = false,
) {
    val annotated = buildAnnotatedString {
        var remaining = text
        while (remaining.isNotEmpty()) {
            val codeMatch = Regex("""`([^`]+)`""").find(remaining)
            val boldMatch = if (codeMatch == null) Regex("""\*\*(.+?)\*\*""").find(remaining) else null
            val italicMatch = if (codeMatch == null && boldMatch == null) Regex("""\*(.+?)\*""").find(remaining) else null

            val firstMatch = listOfNotNull(codeMatch, boldMatch, italicMatch).minByOrNull { it.range.first }

            if (firstMatch != null) {
                // 前面的普通文本
                if (firstMatch.range.first > 0) {
                    append(remaining.substring(0, firstMatch.range.first))
                }
                // 匹配内容
                val inner = firstMatch.groupValues[1]
                when {
                    firstMatch.value.startsWith("```") -> { /* handled in block */ }
                    firstMatch.value.startsWith("``") -> { /* handled in block */ }
                    firstMatch.value.startsWith("`") -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, background = Color(0x22000000))) { append(inner) }
                    firstMatch.value.startsWith("**") -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(inner) }
                    firstMatch.value.startsWith("*") && !firstMatch.value.startsWith("**") -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(inner) }
                }
                remaining = remaining.substring(firstMatch.range.last + 1)
            } else {
                append(remaining)
                remaining = ""
            }
        }
    }

    Text(
        text = annotated,
        style = when {
            isHeader && headerLevel == 1 -> MaterialTheme.typography.titleMedium
            isHeader && headerLevel == 2 -> MaterialTheme.typography.titleSmall
            isHeader -> MaterialTheme.typography.labelLarge
            else -> MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp)
        },
        fontWeight = if (isHeader) FontWeight.Bold else if (bold) FontWeight.SemiBold else FontWeight.Normal,
        color = MaterialTheme.colorScheme.onSurface,
    )
}
