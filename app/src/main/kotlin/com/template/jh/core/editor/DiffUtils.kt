package com.template.jh.core.editor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

enum class LineChangeType { Added, Removed, Modified, Unchanged }

data class LineDiff(val lineIndex: Int, val type: LineChangeType)
data class PatchOp(
    val type: String,
    val startLine: Int,
    val endLine: Int = 0,
    val content: String = "",
)

/** 逐行 diff 算法（简化 Myers） */
fun computeLineDiff(oldText: String, newText: String): Pair<Map<Int, LineChangeType>, Map<Int, LineChangeType>> {
    val oldLines = oldText.lines()
    val newLines = newText.lines()
    val oldDiffs = mutableMapOf<Int, LineChangeType>()
    val newDiffs = mutableMapOf<Int, LineChangeType>()
    val oldUsed = BooleanArray(oldLines.size) { false }
    val newUsed = BooleanArray(newLines.size) { false }

    var oi = 0
    var ni = 0
    while (oi < oldLines.size && ni < newLines.size) {
        if (oldLines[oi] == newLines[ni]) {
            oldDiffs[oi] = LineChangeType.Unchanged
            newDiffs[ni] = LineChangeType.Unchanged
            oldUsed[oi] = true; newUsed[ni] = true
            oi++; ni++
        } else {
            val oldPosInNew = findLineInRange(newLines, ni + 1, oldLines[oi])
            val newPosInOld = findLineInRange(oldLines, oi + 1, newLines[ni])
            when {
                oldPosInNew != -1 && (newPosInOld == -1 || oldPosInNew - ni <= newPosInOld - oi) -> {
                    for (k in ni until oldPosInNew) { if (!newUsed[k]) { newDiffs[k] = LineChangeType.Added; newUsed[k] = true } }
                    oldDiffs[oi] = LineChangeType.Unchanged; newDiffs[oldPosInNew] = LineChangeType.Unchanged
                    oldUsed[oi] = true; newUsed[oldPosInNew] = true
                    ni = oldPosInNew + 1; oi++
                }
                newPosInOld != -1 -> {
                    for (k in oi until newPosInOld) { if (!oldUsed[k]) { oldDiffs[k] = LineChangeType.Removed; oldUsed[k] = true } }
                    oldDiffs[newPosInOld] = LineChangeType.Unchanged; newDiffs[ni] = LineChangeType.Unchanged
                    oldUsed[newPosInOld] = true; newUsed[ni] = true
                    oi = newPosInOld + 1; ni++
                }
                else -> {
                    oldDiffs[oi] = LineChangeType.Modified; newDiffs[ni] = LineChangeType.Modified
                    oldUsed[oi] = true; newUsed[ni] = true
                    oi++; ni++
                }
            }
        }
    }
    for (i in oi until oldLines.size) { if (!oldUsed[i]) oldDiffs[i] = LineChangeType.Removed }
    for (j in ni until newLines.size) { if (!newUsed[j]) newDiffs[j] = LineChangeType.Added }
    return Pair(oldDiffs, newDiffs)
}

private fun findLineInRange(lines: List<String>, start: Int, target: String): Int {
    for (i in start until lines.size) { if (lines[i] == target) return i }
    return -1
}

fun applyPatches(originalText: String, patches: List<PatchOp>): String {
    val lines = originalText.lines().toMutableList()
    val sorted = patches.sortedByDescending { it.startLine }
    for (p in sorted) {
        when (p.type) {
            "replace" -> {
                val start = (p.startLine - 1).coerceIn(0, lines.size)
                val end = p.endLine.coerceIn(start, lines.size)
                val newLines = p.content.lines().toMutableList()
                if (newLines.size > 1 && newLines.lastOrNull()?.isEmpty() == true) newLines.removeLast()
                lines.subList(start, end).clear()
                lines.addAll(start, newLines)
            }
            "insert" -> { lines.addAll((p.startLine - 1).coerceIn(0, lines.size), p.content.lines()) }
            "delete" -> {
                val start = (p.startLine - 1).coerceIn(0, lines.size)
                val end = p.endLine.coerceIn(start, lines.size)
                lines.subList(start, end).clear()
            }
        }
    }
    return lines.joinToString("\n")
}

data class BlockReplaceOp(
    val oldString: String,
    val newString: String,
)

fun replaceInFile(originalText: String, operations: List<BlockReplaceOp>): Pair<String, String> {
    var result = originalText
    val messages = mutableListOf<String>()
    var allSuccess = true
    for ((index, op) in operations.withIndex()) {
        val oldStr = op.oldString; val newStr = op.newString
        if (result.contains(oldStr)) {
            val count = countOccurrences(result, oldStr)
            if (count == 1) { result = result.replaceFirst(oldStr, newStr); messages.add("✓ 替换 #${index + 1} 成功"); continue }
            val expanded = tryExpandAndReplace(result, oldStr, newStr)
            if (expanded != null) { result = expanded; messages.add("✓ 替换 #${index + 1} 成功（通过上下文扩展）"); continue }
            messages.add("✗ 替换 #${index + 1} 失败: 找到 $count 处匹配"); allSuccess = false; continue
        }
        val trimmed = oldStr.trim()
        if (result.contains(trimmed)) {
            val count = countOccurrences(result, trimmed)
            if (count == 1) { result = result.replaceFirst(trimmed, newStr); messages.add("✓ 替换 #${index + 1} 成功（自动去首尾空白）"); continue }
        }
        val lineMatch = tryLineBasedMatch(result, oldStr, newStr)
        if (lineMatch != null) { result = lineMatch; messages.add("✓ 替换 #${index + 1} 成功（忽略行尾空白）"); continue }
        messages.add(buildMatchErrorMessage(result, oldStr, index + 1)); allSuccess = false
    }
    val summary = if (allSuccess) "[成功] 所有替换已完成" else "[部分失败] 部分替换未能完成"
    return result to "$summary\n${messages.joinToString("\n")}"
}

private fun tryLineBasedMatch(text: String, oldStr: String, newStr: String): String? {
    val textLines = text.lines(); val oldLines = oldStr.lines()
    if (oldLines.isEmpty()) return null
    val matches = mutableListOf<Int>()
    for (i in 0..textLines.size - oldLines.size) { if (linesMatchLoose(textLines, i, oldLines)) matches.add(i) }
    if (matches.size != 1) return null
    val result = textLines.toMutableList()
    val start = matches[0]
    result.subList(start, start + oldLines.size).clear()
    result.addAll(start, newStr.lines())
    return result.joinToString("\n")
}

private fun linesMatchLoose(lines: List<String>, startIdx: Int, oldLines: List<String>): Boolean {
    if (startIdx + oldLines.size > lines.size) return false
    for (i in oldLines.indices) { if (lines[startIdx + i].trimEnd() != oldLines[i].trimEnd()) return false }
    return true
}

private fun buildMatchErrorMessage(text: String, oldStr: String, index: Int): String {
    val oldLines = oldStr.lines()
    val first = oldLines.firstOrNull()?.trim()?.take(30) ?: ""
    val last = oldLines.lastOrNull()?.trim()?.take(30) ?: ""
    val suggestions = findSimilarContent(text, oldStr)
    val sb = StringBuilder()
    sb.appendLine("✗ 替换 #$index 失败: 找不到指定的代码块")
    sb.appendLine("  查找: \"${first}...${last}\"")
    sb.appendLine("  行数: ${oldLines.size}")
    if (suggestions.isNotEmpty()) {
        sb.appendLine("  可能的匹配:")
        suggestions.take(3).forEach { (line, content) -> sb.appendLine("    第 $line 行: \"${content.take(40)}...\"") }
    }
    return sb.toString().trimEnd()
}

private fun findSimilarContent(text: String, pattern: String): List<Pair<Int, String>> {
    val patternLines = pattern.lines()
    val textLines = text.lines()
    val results = mutableListOf<Pair<Int, String>>()
    if (patternLines.isEmpty()) return results
    val first = patternLines[0].trim().take(20)
    for (i in textLines.indices) { if (textLines[i].contains(first, ignoreCase = true)) results.add(i + 1 to textLines[i].trim()) }
    return results
}

private fun tryExpandAndReplace(text: String, oldStr: String, newStr: String): String? {
    val lines = text.lines(); val oldLines = oldStr.lines()
    val matches = mutableListOf<Int>()
    for (i in 0..lines.size - oldLines.size) { if (linesMatch(lines, i, oldLines)) matches.add(i) }
    if (matches.size != 1) return null
    val result = lines.toMutableList()
    val start = matches[0]
    result.subList(start, start + oldLines.size).clear()
    result.addAll(start, newStr.lines())
    return result.joinToString("\n")
}

private fun linesMatch(lines: List<String>, startIdx: Int, oldLines: List<String>): Boolean {
    if (startIdx + oldLines.size > lines.size) return false
    for (i in oldLines.indices) { if (lines[startIdx + i].trimEnd() != oldLines[i].trimEnd()) return false }
    return true
}

private fun countOccurrences(text: String, pattern: String): Int {
    var count = 0; var idx = text.indexOf(pattern)
    while (idx != -1) { count++; idx = text.indexOf(pattern, idx + 1) }
    return count
}

class DiffHighlightTransformation(
    private val lineChanges: Map<Int, LineChangeType>,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val syntaxHighlighted = highlightSyntax(text.text)
        val builder = AnnotatedString.Builder(syntaxHighlighted)
        val t = builder.toString()
        val lines = t.lines()
        var offset = 0
        for ((lineIdx, line) in lines.withIndex()) {
            val change = lineChanges[lineIdx]
            if (change != null && change != LineChangeType.Unchanged) {
                val bg = when (change) {
                    LineChangeType.Added -> Color(0x3322CC22)
                    LineChangeType.Removed -> Color(0x33CC2222)
                    LineChangeType.Modified -> Color(0x33CCAA00)
                    LineChangeType.Unchanged -> null
                }
                if (bg != null) builder.addStyle(SpanStyle(background = bg), offset, offset + line.length)
            }
            offset += line.length + 1
        }
        return TransformedText(builder.toAnnotatedString(), OffsetMapping.Identity)
    }
}
