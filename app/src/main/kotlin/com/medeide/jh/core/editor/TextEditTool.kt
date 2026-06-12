package com.medeide.jh.core.editor

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
            // 检查去空白后的唯一性，再决定是否直接替换
            val count = countOccurrences(originalText, trimmedOld)
            if (count == 1) {
                return originalText.replaceFirst(trimmedOld, newString) to true
            }
            // 多次匹配时尝试行级扩展
            val expanded = tryExpandAndReplace(originalText, trimmedOld, newString)
            if (expanded != null) return expanded to true
            return originalText to false
        }

        // 统计出现次数（确保唯一性）
        val count = countOccurrences(originalText, oldString)
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

    private fun countOccurrences(text: String, pattern: String): Int {
        var count = 0
        var idx = text.indexOf(pattern)
        while (idx != -1) {
            count++
            idx = text.indexOf(pattern, idx + 1)
        }
        return count
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

        if (matches.isEmpty()) return null
        val start = if (matches.size == 1) matches[0]
            else disambiguateByContext(lines, matches, oldLines.size) ?: return null

        // 执行替换
        val result = lines.toMutableList()
        val endIdx = start + oldLines.size
        result.subList(start, endIdx).clear()
        if (newStr.isNotEmpty()) {
            result.addAll(start, newStr.lines())
        }
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
