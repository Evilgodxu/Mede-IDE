package com.template.jh.core.editor

/**
 * 文本编辑工具 - 提供精确的代码块级替换功能
 * 优势：大模型只需提供代码块内容，无需计算行号
 */
object TextEditTool {

    /**
     * 基于唯一标识字符串的代码块替换
     * 
     * @param originalText 原始文件内容
     * @param oldString 要替换的旧代码块（需唯一或足够长以确定位置）
     * @param newString 新代码块
     * @return Pair<替换后的内容, 是否成功>
     */
    fun replaceInFile(
        originalText: String,
        oldString: String,
        newString: String
    ): Pair<String, Boolean> {
        // 验证旧字符串是否存在
        if (!originalText.contains(oldString)) {
            // 尝试去除首尾空白后匹配
            val trimmedOld = oldString.trim()
            if (!originalText.contains(trimmedOld)) {
                return originalText to false
            }
            return originalText.replaceFirst(trimmedOld, newString) to true
        }

        // 统计出现次数（确保唯一性）
        val count = originalText.split(oldString).size - 1
        if (count > 1) {
            // 尝试扩展上下文以获得唯一匹配
            val result = tryExpandAndReplace(originalText, oldString, newString)
            if (result != null) {
                return result to true
            }
            return originalText to false
        }

        // 执行替换
        return originalText.replaceFirst(oldString, newString) to true
    }

    /**
     * 多段替换 - 一次性执行多个替换操作
     */
    fun replaceMultiple(
        originalText: String,
        replacements: List<Pair<String, String>>
    ): Pair<String, List<Boolean>> {
        var result = originalText
        val results = mutableListOf<Boolean>()

        for ((oldStr, newStr) in replacements) {
            val (newResult, success) = replaceInFile(result, oldStr, newStr)
            result = newResult
            results.add(success)
        }

        return result to results
    }

    /**
     * 尝试通过扩展上下文（添加前后行）来获得唯一匹配
     */
    private fun tryExpandAndReplace(text: String, oldStr: String, newStr: String): String? {
        val lines = text.lines()
        val oldLines = oldStr.lines()

        // 找到所有可能的匹配位置
        val matches = mutableListOf<Int>()
        for (i in 0..lines.size - oldLines.size) {
            if (linesMatch(lines, i, oldLines)) {
                matches.add(i)
            }
        }

        // 必须有且只有一个匹配
        if (matches.size != 1) return null

        // 执行替换
        val result = lines.toMutableList()
        val startIdx = matches[0]
        val endIdx = startIdx + oldLines.size

        result.subList(startIdx, endIdx).clear()
        result.addAll(startIdx, newStr.lines())

        return result.joinToString("\n")
    }

    private fun linesMatch(lines: List<String>, startIdx: Int, oldLines: List<String>): Boolean {
        if (startIdx + oldLines.size > lines.size) return false
        for (i in oldLines.indices) {
            if (lines[startIdx + i].trimEnd() != oldLines[i].trimEnd()) {
                return false
            }
        }
        return true
    }

    /**
     * 行级补丁 - 基于行号的精确替换（保留作备选）
     */
    data class LinePatch(
        val type: PatchType,
        val startLine: Int,  // 1-based
        val endLine: Int = 0,  // exclusive for replace/delete
        val content: String = ""
    )

    enum class PatchType { REPLACE, INSERT, DELETE }

    fun applyLinePatches(originalText: String, patches: List<LinePatch>): String {
        val lines = originalText.lines().toMutableList()
        // 从后往前应用，避免行号偏移
        val sorted = patches.sortedByDescending { it.startLine }

        for (p in sorted) {
            when (p.type) {
                PatchType.REPLACE -> {
                    val start = (p.startLine - 1).coerceIn(0, lines.size)
                    val end = p.endLine.coerceIn(start, lines.size)
                    val newLines = p.content.lines().toMutableList()
                    // 只有当最后一行是空字符串且结果有多行时才移除
                    if (newLines.size > 1 && newLines.lastOrNull()?.isEmpty() == true) {
                        newLines.removeLast()
                    }
                    lines.subList(start, end).clear()
                    lines.addAll(start, newLines)
                }
                PatchType.INSERT -> {
                    val idx = (p.startLine - 1).coerceIn(0, lines.size)
                    lines.addAll(idx, p.content.lines())
                }
                PatchType.DELETE -> {
                    val start = (p.startLine - 1).coerceIn(0, lines.size)
                    val end = p.endLine.coerceIn(start, lines.size)
                    lines.subList(start, end).clear()
                }
            }
        }
        return lines.joinToString("\n")
    }
}

/**
 * 使用示例：
 *
 * // 简单替换
 * val (newContent, success) = TextEditTool.replaceInFile(
 *     originalText = fileContent,
 *     oldString = """
 *         fun oldFunction() {
 *             return 1
 *         }
 *     """.trimIndent(),
 *     newString = """
 *         fun oldFunction() {
 *             return 2
 *         }
 *     """.trimIndent()
 * )
 *
 * // 多段替换
 * val (result, results) = TextEditTool.replaceMultiple(
 *     fileContent,
 *     listOf(
 *         "old1" to "new1",
 *         "old2" to "new2"
 *     )
 * )
 */
