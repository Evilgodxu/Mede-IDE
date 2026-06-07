package com.template.jh.core.editor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

// 行变更类型
enum class LineChangeType { Added, Removed, Modified, Unchanged }

// 行差异
data class LineDiff(val lineIndex: Int, val type: LineChangeType)

// Patch 指令
data class PatchOp(
    val type: String,      // replace / insert / delete
    val startLine: Int,    // 1-based
    val endLine: Int = 0, // exclusive (replace/delete)
    val content: String = "",
)

// 计算两个文本的逐行 diff，返回 LineDiff 列表
fun computeLineDiff(oldText: String, newText: String): List<LineDiff> {
    val oldLines = oldText.lines()
    val newLines = newText.lines()
    val diffs = mutableListOf<LineDiff>()

    // 简单 LCS 匹配：逐行比较，标记 Added/Removed/Modified
    var oi = 0; var ni = 0
    val oldUsed = BooleanArray(oldLines.size) { false }
    val newUsed = BooleanArray(newLines.size) { false }

    // 第一遍：匹配相同的行
    while (oi < oldLines.size && ni < newLines.size) {
        if (oldLines[oi] == newLines[ni]) {
            diffs.add(LineDiff(oi, LineChangeType.Unchanged))
            oldUsed[oi] = true; newUsed[ni] = true
            oi++; ni++
        } else {
            // 尝试在 newLines 中向前找匹配
            var found = false
            for (j in ni + 1 until newLines.size) {
                if (oldLines[oi] == newLines[j]) {
                    // oldLines[oi] 匹配到 newLines[j]，中间的都是新增
                    for (k in ni until j) {
                        diffs.add(LineDiff(k, LineChangeType.Added))
                        newUsed[k] = true
                    }
                    diffs.add(LineDiff(oi, LineChangeType.Unchanged))
                    oldUsed[oi] = true; newUsed[j] = true
                    ni = j + 1; oi++; found = true; break
                }
            }
            if (!found) {
                // 在 oldLines 中向前找匹配
                for (i in oi + 1 until oldLines.size) {
                    if (oldLines[i] == newLines[ni]) {
                        for (k in oi until i) {
                            diffs.add(LineDiff(k, LineChangeType.Removed))
                            oldUsed[k] = true
                        }
                        diffs.add(LineDiff(i, LineChangeType.Unchanged))
                        oldUsed[i] = true; newUsed[ni] = true
                        oi = i + 1; ni++; found = true; break
                    }
                }
            }
            if (!found) {
                // 不匹配 → Modified
                diffs.add(LineDiff(oi, LineChangeType.Modified))
                oldUsed[oi] = true; newUsed[ni] = true
                oi++; ni++
            }
        }
    }

    // 剩余旧行 = Removed
    for (i in oi until oldLines.size) {
        if (!oldUsed[i]) diffs.add(LineDiff(i, LineChangeType.Removed))
    }
    // 剩余新行 = Added（用新行索引标记）
    for (j in ni until newLines.size) {
        if (!newUsed[j]) diffs.add(LineDiff(j, LineChangeType.Added))
    }

    return diffs
}

// 应用 PatchOp 列表到文本
fun applyPatches(originalText: String, patches: List<PatchOp>): String {
    val lines = originalText.lines().toMutableList()
    // 从后往前应用，避免行号偏移
    val sorted = patches.sortedByDescending { it.startLine }
    for (p in sorted) {
        when (p.type) {
            "replace" -> {
                val start = (p.startLine - 1).coerceIn(0, lines.size)
                val end = p.endLine.coerceIn(start, lines.size)
                val newLines = p.content.lines().toMutableList()
                if (newLines.lastOrNull() == "") newLines.removeLast()
                lines.subList(start, end).clear()
                lines.addAll(start, newLines)
            }
            "insert" -> {
                val idx = (p.startLine - 1).coerceIn(0, lines.size)
                lines.add(idx, p.content)
            }
            "delete" -> {
                val start = (p.startLine - 1).coerceIn(0, lines.size)
                val end = p.endLine.coerceIn(start, lines.size)
                lines.subList(start, end).clear()
            }
        }
    }
    return lines.joinToString("\n")
}

// 行级高亮 VisualTransformation：在语法高亮基础上添加 diff 背景色
class DiffHighlightTransformation(
    private val lineChanges: Map<Int, LineChangeType>,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val syntaxHighlighted = highlightSyntax(text.text)
        val builder = AnnotatedString.Builder(syntaxHighlighted)
        val text = builder.toString()
        val lines = text.lines()
        var offset = 0
        for ((lineIdx, line) in lines.withIndex()) {
            val change = lineChanges[lineIdx]
            if (change != null && change != LineChangeType.Unchanged) {
                val bg = when (change) {
                    LineChangeType.Added -> Color(0x3322CC22)
                    LineChangeType.Removed -> Color(0x33CC2222)
                    LineChangeType.Modified -> Color(0x33CCAA00)
                    else -> null
                }
                if (bg != null) {
                    val start = offset
                    val end = offset + line.length
                    builder.addStyle(SpanStyle(background = bg), start, end)
                }
            }
            offset += line.length + 1
        }
        return TransformedText(builder.toAnnotatedString(), OffsetMapping.Identity)
    }
}
