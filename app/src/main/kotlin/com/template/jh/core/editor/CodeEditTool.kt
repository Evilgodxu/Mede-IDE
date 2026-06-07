package com.template.jh.core.editor

/**
 * 代码编辑工具 - 类似 Claude Desktop 的 SearchReplace
 * 核心原则：只修改指定内容，不生成整个文件
 */
object CodeEditTool {

    /**
     * 执行单次替换操作
     * 
     * @param originalText 原始文件内容
     * @param oldString 要查找的代码块（必须足够唯一，通常包含函数签名或类定义）
     * @param newString 新代码块
     * @return 替换结果
     */
    fun replace(
        originalText: String,
        oldString: String,
        newString: String
    ): ReplaceResult {
        // 1. 精确匹配
        if (originalText.contains(oldString)) {
            val count = countOccurrences(originalText, oldString)
            if (count == 1) {
                return ReplaceResult.Success(
                    originalText.replaceFirst(oldString, newString),
                    "替换成功"
                )
            }
            return ReplaceResult.Error(
                "找到 $count 处匹配，请提供更长的唯一代码块（建议包含函数签名或更多上下文）"
            )
        }

        // 2. 尝试忽略首尾空白匹配
        val trimmedOld = oldString.trim()
        if (originalText.contains(trimmedOld)) {
            val count = countOccurrences(originalText, trimmedOld)
            if (count == 1) {
                return ReplaceResult.Success(
                    originalText.replaceFirst(trimmedOld, newString),
                    "替换成功（自动去除首尾空白）"
                )
            }
            return ReplaceResult.Error(
                "找到 $count 处匹配（去除空白后），请提供更长的唯一代码块"
            )
        }

        // 3. 失败，提供详细的错误信息
        return ReplaceResult.Error(buildErrorMessage(originalText, oldString))
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

        // 查找相似内容
        val suggestions = findSimilarLines(text, oldString)
        if (suggestions.isNotEmpty()) {
            sb.appendLine("文件中可能的匹配位置:")
            suggestions.take(3).forEach { (lineNum, content) ->
                sb.appendLine("  第 ${lineNum} 行: ${content.take(50)}")
            }
            sb.appendLine()
        }

        sb.appendLine("建议:")
        sb.appendLine("  1. 确保 old_string 是从文件中直接复制的")
        sb.appendLine("  2. 提供更多上下文（如包含函数签名或类名）")
        sb.appendLine("  3. 检查是否有额外的空格或缩进差异")

        return sb.toString()
    }

    private fun findSimilarLines(text: String, pattern: String): List<Pair<Int, String>> {
        val patternLines = pattern.lines()
        val textLines = text.lines()
        val results = mutableListOf<Pair<Int, String>>()

        if (patternLines.isEmpty()) return results

        val firstPattern = patternLines[0].trim().take(30)

        for (i in textLines.indices) {
            if (textLines[i].contains(firstPattern, ignoreCase = true)) {
                results.add(i + 1 to textLines[i].trim())
            }
        }

        return results
    }

    sealed class ReplaceResult {
        data class Success(val newText: String, val message: String) : ReplaceResult()
        data class Error(val message: String) : ReplaceResult()
    }
}
