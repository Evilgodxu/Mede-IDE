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
        if (matches.size != 1) return null
        val result = lines.toMutableList()
        val start = matches[0]
        result.subList(start, start + oldLines.size).clear()
        result.addAll(start, newStr.lines())
        return ReplaceResult.Success(result.joinToString("\n"), "替换成功（上下文扩展定位）")
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
        if (matches.size != 1) return null
        val result = textLines.toMutableList()
        val start = matches[0]
        result.subList(start, start + oldLines.size).clear()
        result.addAll(start, newStr.lines())
        return ReplaceResult.Success(result.joinToString("\n"), "替换成功（忽略行尾空白）")
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

    sealed class ReplaceResult {
        data class Success(val newText: String, val message: String) : ReplaceResult()
        data class Error(val message: String) : ReplaceResult()
    }
}
