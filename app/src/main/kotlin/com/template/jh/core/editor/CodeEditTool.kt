package com.template.jh.core.editor

// 代码编辑工具 - 精确代码块级替换，支持多级容错匹配
object CodeEditTool {

    fun replace(
        originalText: String,
        oldString: String,
        newString: String
    ): ReplaceResult {
        // 1. 精确匹配
        val exact = tryExactReplace(originalText, oldString, newString)
        if (exact != null) return exact

        // 2. 去首尾空白
        val trimmed = oldString.trim()
        if (trimmed != oldString) {
            val count = countOccurrences(originalText, trimmed)
            if (count == 1) {
                return ReplaceResult.Success(
                    originalText.replaceFirst(trimmed, newString),
                    "替换成功（自动去首尾空白）"
                )
            }
        }

        // 3. 行级匹配（忽略行尾空白差异）
        val lineMatch = tryLineMatch(originalText, oldString, newString)
        if (lineMatch != null) return lineMatch

        // 4. 全部失败，提供详细的错误信息
        return ReplaceResult.Error(buildErrorMessage(originalText, oldString))
    }

    /**
     * 批量编辑 — 一次调用替换多处内容。
     * 所有 edits 基于同一原始文件匹配（非顺序叠加），
     * 从后往前应用以避免位置偏移。
     * edits 之间不允许重叠。
     */
    fun batchReplace(originalText: String, edits: List<Edit>): ReplaceResult {
        if (edits.isEmpty()) return ReplaceResult.Error("未提供编辑操作")

        data class Match(val start: Int, val end: Int, val newText: String)
        val matches = mutableListOf<Match>()

        for ((i, edit) in edits.withIndex()) {
            val label = "编辑 #${i + 1}"
            val idx = originalText.indexOf(edit.oldText)
            if (idx < 0) return ReplaceResult.Error("$label 失败: 找不到指定的代码块")
            val count = countOccurrences(originalText, edit.oldText)
            if (count > 1) return ReplaceResult.Error("$label 失败: 找到 $count 处匹配，请提供更长的唯一代码块")
            matches.add(Match(idx, idx + edit.oldText.length, edit.newText))
        }

        // 检查重叠
        matches.sortBy { it.start }
        for (i in 0 until matches.size - 1) {
            if (matches[i].end > matches[i + 1].start) {
                return ReplaceResult.Error(
                    "编辑 #${i + 1} 与 #${i + 2} 的代码块区间重叠（不相邻或嵌套），请分别调用"
                )
            }
        }

        // 从后往前应用，避免位置偏移
        var result = originalText
        for (m in matches.reversed()) {
            result = result.substring(0, m.start) + m.newText + result.substring(m.end)
        }

        val msg = "${edits.size} 处编辑成功（基于原始文件匹配，按位置倒序应用）"
        return ReplaceResult.Success(result, msg)
    }

    private fun tryExactReplace(text: String, oldStr: String, newStr: String): ReplaceResult? {
        if (!text.contains(oldStr)) return null
        val count = countOccurrences(text, oldStr)
        if (count == 1) {
            return ReplaceResult.Success(text.replaceFirst(oldStr, newStr), "替换成功")
        }
        // 多次匹配时尝试扩展上下文（按行匹配定位唯一位置）
        val expanded = tryExpandMatch(text, oldStr, newStr)
        if (expanded != null) return expanded
        return ReplaceResult.Error(
            "找到 $count 处匹配，请提供更长的唯一代码块（建议包含函数签名或更多上下文）"
        )
    }

    // 基于行级匹配定位唯一位置，然后按旧字符串长度替换
    private fun tryExpandMatch(text: String, oldStr: String, newStr: String): ReplaceResult? {
        val lines = text.lines()
        val oldLines = oldStr.lines()
        val matches = mutableListOf<Int>()
        for (i in 0..lines.size - oldLines.size) {
            if (linesMatchExact(lines, i, oldLines)) matches.add(i)
        }
        if (matches.isEmpty()) return null
        val start = if (matches.size == 1) matches[0]
            else disambiguateByContext(lines, matches, oldLines.size) ?: return null
        return doLineReplace(lines, start, oldLines.size, newStr, "替换成功（上下文扩展定位）")
    }

    // 行级匹配（忽略每行末尾空白）
    private fun tryLineMatch(text: String, oldStr: String, newStr: String): ReplaceResult? {
        val textLines = text.lines()
        val oldLines = oldStr.lines()
        if (oldLines.isEmpty()) return null
        val matches = mutableListOf<Int>()
        for (i in 0..textLines.size - oldLines.size) {
            if (linesMatchLoose(textLines, i, oldLines)) matches.add(i)
        }
        if (matches.isEmpty()) return null
        val start = if (matches.size == 1) matches[0]
            else disambiguateByContext(textLines, matches, oldLines.size) ?: return null
        return doLineReplace(textLines, start, oldLines.size, newStr, "替换成功（忽略行尾空白）")
    }

    private fun doLineReplace(
        lines: List<String>, start: Int, blockSize: Int, newStr: String, msg: String
    ): ReplaceResult.Success {
        val result = lines.toMutableList()
        result.subList(start, start + blockSize).clear()
        if (newStr.isNotEmpty()) {
            result.addAll(start, newStr.lines())
        }
        return ReplaceResult.Success(result.joinToString("\n"), msg)
    }

    /** 当块级匹配出现多个相同位置时，用前后文行消除歧义 */
    private fun disambiguateByContext(
        lines: List<String>, positions: List<Int>, blockSize: Int
    ): Int? {
        for (ctxLines in 1..2) {
            val keyToPos = positions.groupBy { pos ->
                buildContextKey(lines, pos, blockSize, ctxLines)
            }
            keyToPos.entries.firstOrNull { it.value.size == 1 }?.let {
                return it.value[0]
            }
        }
        return null
    }

    private fun buildContextKey(lines: List<String>, pos: Int, blockSize: Int, ctxLines: Int): String {
        val before = ((pos - ctxLines).coerceAtLeast(0) until pos)
            .joinToString("|") { lines[it].trimEnd() }
        val after = (pos + blockSize until (pos + blockSize + ctxLines).coerceAtMost(lines.size))
            .joinToString("|") { lines[it].trimEnd() }
        return "$before ⏎ $after"
    }

    private fun linesMatchExact(lines: List<String>, startIdx: Int, oldLines: List<String>): Boolean {
        if (startIdx + oldLines.size > lines.size) return false
        for (i in oldLines.indices) {
            if (lines[startIdx + i] != oldLines[i]) return false
        }
        return true
    }

    private fun linesMatchLoose(lines: List<String>, startIdx: Int, oldLines: List<String>): Boolean {
        if (startIdx + oldLines.size > lines.size) return false
        for (i in oldLines.indices) {
            if (lines[startIdx + i].trimEnd() != oldLines[i].trimEnd()) return false
        }
        return true
    }

    private fun countOccurrences(text: String, pattern: String): Int {
        var count = 0
        var idx = text.indexOf(pattern)
        while (idx != -1) {
            count++
            idx = text.indexOf(pattern, idx + 1)
        }
        return count
    }

    private fun buildErrorMessage(text: String, oldString: String): String {
        val lines = oldString.lines()
        val firstLine = lines.firstOrNull()?.trim()?.take(40) ?: ""
        val lastLine = lines.lastOrNull()?.trim()?.take(40) ?: ""

        val sb = StringBuilder()
        sb.appendLine("替换失败: 找不到指定的代码块")
        sb.appendLine()
        sb.appendLine("查找内容:")
        sb.appendLine("  首行: \"$firstLine\"")
        sb.appendLine("  末行: \"$lastLine\"")
        sb.appendLine("  共 ${lines.size} 行")
        sb.appendLine()
        sb.appendLine("建议:")
        sb.appendLine("  1. 确保 old_string 是从文件中直接复制的")
        sb.appendLine("  2. 提供更多上下文（如包含函数签名或类名）")

        // 输出相似内容提示
        val suggestions = findSimilar(text, oldString)
        if (suggestions.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("文件中类似的内容:")
            suggestions.take(3).forEach { (lineNum, content) ->
                sb.appendLine("  第 $lineNum 行: \"${content.take(50)}...\"")
            }
        }

        return sb.toString()
    }

    private fun findSimilar(text: String, pattern: String): List<Pair<Int, String>> {
        val patternLines = pattern.lines()
        val textLines = text.lines()
        if (patternLines.isEmpty()) return emptyList()
        val firstSig = patternLines[0].trim().take(20)
        if (firstSig.isEmpty()) return emptyList()
        return textLines.mapIndexedNotNull { i, line ->
            if (line.contains(firstSig, ignoreCase = true)) (i + 1) to line.trim()
            else null
        }
    }

    data class Edit(val oldText: String, val newText: String)

sealed class ReplaceResult {
        data class Success(val newText: String, val message: String) : ReplaceResult()
        data class Error(val message: String) : ReplaceResult()
    }
}
