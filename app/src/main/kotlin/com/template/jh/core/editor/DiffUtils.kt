package com.template.jh.core.editor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

// 行变更类型
enum class LineChangeType { Added, Removed, Modified, Unchanged }

/**
 * 从 diff 结果生成修改块列表
 * 将连续的相同类型变更行分组为一个修改块
 */
fun computeChangeBlocks(oldText: String, newText: String): List<ChangeBlock> {
    val (oldDiffs, newDiffs) = computeLineDiff(oldText, newText)
    val oldLines = oldText.lines()
    val newLines = newText.lines()
    val blocks = mutableListOf<ChangeBlock>()

    // 收集所有变更行的索引
    val oldChangedLines = oldDiffs.filter { it.value != LineChangeType.Unchanged }.keys.sorted()
    val newChangedLines = newDiffs.filter { it.value != LineChangeType.Unchanged }.keys.sorted()

    if (oldChangedLines.isEmpty() && newChangedLines.isEmpty()) {
        return emptyList()
    }

    // 将连续的变更行分组
    var blockId = 0
    var i = 0
    var j = 0

    while (i < oldChangedLines.size || j < newChangedLines.size) {
        val oldStart = if (i < oldChangedLines.size) oldChangedLines[i] else -1
        val newStart = if (j < newChangedLines.size) newChangedLines[j] else -1

        // 确定当前块的范围
        val oldBlockLines = mutableListOf<Int>()
        val newBlockLines = mutableListOf<Int>()

        // 收集连续的旧文件变更行
        while (i < oldChangedLines.size) {
            val line = oldChangedLines[i]
            if (oldBlockLines.isEmpty() || line == oldBlockLines.last() + 1) {
                oldBlockLines.add(line)
                i++
            } else break
        }

        // 收集连续的新文件变更行
        while (j < newChangedLines.size) {
            val line = newChangedLines[j]
            if (newBlockLines.isEmpty() || line == newBlockLines.last() + 1) {
                newBlockLines.add(line)
                j++
            } else break
        }

        // 确定变更类型
        val hasRemoved = oldBlockLines.isNotEmpty()
        val hasAdded = newBlockLines.isNotEmpty()

        val type = when {
            hasRemoved && hasAdded -> LineChangeType.Modified
            hasRemoved -> LineChangeType.Removed
            hasAdded -> LineChangeType.Added
            else -> LineChangeType.Unchanged
        }

        // 获取内容
        val oldContent = oldBlockLines.map { oldLines.getOrNull(it) ?: "" }
        val newContent = newBlockLines.map { newLines.getOrNull(it) ?: "" }

        // 创建修改块
        val block = ChangeBlock(
            id = blockId++,
            status = ChangeBlockStatus.PENDING,
            oldStartLine = oldBlockLines.firstOrNull()?.plus(1) ?: -1,
            oldEndLine = oldBlockLines.lastOrNull()?.plus(2) ?: -1, // exclusive
            newStartLine = newBlockLines.firstOrNull()?.plus(1) ?: -1,
            newEndLine = newBlockLines.lastOrNull()?.plus(2) ?: -1, // exclusive
            type = type,
            oldContent = oldContent,
            newContent = newContent
        )

        blocks.add(block)
    }

    return blocks
}

/**
 * 从 diff 结果生成 CodeReviewState
 */
fun createCodeReviewState(filePath: String, oldText: String, newText: String): CodeReviewState {
    val blocks = computeChangeBlocks(oldText, newText)
    return CodeReviewState(
        filePath = filePath,
        oldContent = oldText,
        newContent = newText,
        changeBlocks = blocks
    )
}

// 行差异
data class LineDiff(val lineIndex: Int, val type: LineChangeType)

// Patch 指令
data class PatchOp(
    val type: String,      // replace / insert / delete
    val startLine: Int,    // 1-based
    val endLine: Int = 0, // exclusive (replace/delete)
    val content: String = "",
)

/**
 * 计算两个文本的逐行 diff
 * 返回 Pair<旧文件行变更映射, 新文件行变更映射>
 * 键为行索引（0-based），值为变更类型
 */
fun computeLineDiff(oldText: String, newText: String): Pair<Map<Int, LineChangeType>, Map<Int, LineChangeType>> {
    val oldLines = oldText.lines()
    val newLines = newText.lines()
    val oldDiffs = mutableMapOf<Int, LineChangeType>()
    val newDiffs = mutableMapOf<Int, LineChangeType>()

    // 使用 Myers 差分算法的简化版本
    val oldUsed = BooleanArray(oldLines.size) { false }
    val newUsed = BooleanArray(newLines.size) { false }

    // 建立旧行到新行的映射（用于检测移动和修改）
    val oldLinePositions = mutableMapOf<String, MutableList<Int>>()
    oldLines.forEachIndexed { idx, line ->
        oldLinePositions.getOrPut(line) { mutableListOf() }.add(idx)
    }

    // 第一步：标记完全相同的行
    var oi = 0
    var ni = 0
    while (oi < oldLines.size && ni < newLines.size) {
        if (oldLines[oi] == newLines[ni]) {
            oldDiffs[oi] = LineChangeType.Unchanged
            newDiffs[ni] = LineChangeType.Unchanged
            oldUsed[oi] = true
            newUsed[ni] = true
            oi++
            ni++
        } else {
            // 查找最佳匹配
            val oldPosInNew = findLineInRange(newLines, ni + 1, oldLines[oi])
            val newPosInOld = findLineInRange(oldLines, oi + 1, newLines[ni])

            when {
                oldPosInNew != -1 && (newPosInOld == -1 || oldPosInNew - ni <= newPosInOld - oi) -> {
                    // 旧行在新文件后面找到，中间的新行是 Added
                    for (k in ni until oldPosInNew) {
                        if (!newUsed[k]) {
                            newDiffs[k] = LineChangeType.Added
                            newUsed[k] = true
                        }
                    }
                    oldDiffs[oi] = LineChangeType.Unchanged
                    newDiffs[oldPosInNew] = LineChangeType.Unchanged
                    oldUsed[oi] = true
                    newUsed[oldPosInNew] = true
                    ni = oldPosInNew + 1
                    oi++
                }
                newPosInOld != -1 -> {
                    // 新行在旧文件后面找到，中间的旧行是 Removed
                    for (k in oi until newPosInOld) {
                        if (!oldUsed[k]) {
                            oldDiffs[k] = LineChangeType.Removed
                            oldUsed[k] = true
                        }
                    }
                    oldDiffs[newPosInOld] = LineChangeType.Unchanged
                    newDiffs[ni] = LineChangeType.Unchanged
                    oldUsed[newPosInOld] = true
                    newUsed[ni] = true
                    oi = newPosInOld + 1
                    ni++
                }
                else -> {
                    // 都没找到，标记为 Modified
                    oldDiffs[oi] = LineChangeType.Modified
                    newDiffs[ni] = LineChangeType.Modified
                    oldUsed[oi] = true
                    newUsed[ni] = true
                    oi++
                    ni++
                }
            }
        }
    }

    // 处理剩余的旧行（Removed）
    for (i in oi until oldLines.size) {
        if (!oldUsed[i]) {
            oldDiffs[i] = LineChangeType.Removed
        }
    }

    // 处理剩余的新行（Added）
    for (j in ni until newLines.size) {
        if (!newUsed[j]) {
            newDiffs[j] = LineChangeType.Added
        }
    }

    return Pair(oldDiffs, newDiffs)
}

private fun findLineInRange(lines: List<String>, start: Int, target: String): Int {
    for (i in start until lines.size) {
        if (lines[i] == target) return i
    }
    return -1
}

// 兼容性包装：返回合并的映射（旧文件行使用原索引，新文件行使用原索引+1000000作为偏移）
fun computeLineDiffLegacy(oldText: String, newText: String): Map<Int, LineChangeType> {
    val (oldDiffs, newDiffs) = computeLineDiff(oldText, newText)
    val result = mutableMapOf<Int, LineChangeType>()
    result.putAll(oldDiffs)
    // 新文件行使用偏移避免冲突
    newDiffs.forEach { (idx, type) ->
        result[idx + 1000000] = type
    }
    return result
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
                // 只有当最后一行是空字符串且结果有多行时才移除
                if (newLines.size > 1 && newLines.lastOrNull()?.isEmpty() == true) {
                    newLines.removeLast()
                }
                lines.subList(start, end).clear()
                lines.addAll(start, newLines)
            }
            "insert" -> {
                val idx = (p.startLine - 1).coerceIn(0, lines.size)
                lines.addAll(idx, p.content.lines())
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

// 代码块替换指令 - 基于唯一标识字符串的精确替换
data class BlockReplaceOp(
    val oldString: String,  // 要替换的旧代码块（必须唯一存在）
    val newString: String,  // 新代码块
)

/**
 * 基于唯一标识字符串的代码块替换
 * 优势：
 * 1. 大模型只需提供代码块内容，无需计算行号
 * 2. 精确替换，不会破坏其他行
 * 3. 自动验证唯一性，防止误替换
 * 
 * @return Pair<替换后的内容, 成功/失败信息>
 */
fun replaceInFile(originalText: String, operations: List<BlockReplaceOp>): Pair<String, String> {
    var result = originalText
    val messages = mutableListOf<String>()
    
    for ((index, op) in operations.withIndex()) {
        val trimmedOld = op.oldString
        val trimmedNew = op.newString
        
        // 验证旧字符串是否存在
        if (!result.contains(trimmedOld)) {
            // 尝试规范化空白字符后匹配
            val normalizedResult = normalizeWhitespace(result)
            val normalizedOld = normalizeWhitespace(trimmedOld)
            
            if (!normalizedResult.contains(normalizedOld)) {
                messages.add("替换 #${index + 1} 失败: 找不到指定的代码块")
                continue
            }
        }
        
        // 统计出现次数（确保唯一性）
        val occurrenceCount = countOccurrences(result, trimmedOld)
        if (occurrenceCount > 1) {
            // 尝试扩展上下文以获得唯一匹配
            val expandedResult = tryExpandAndReplace(result, trimmedOld, trimmedNew)
            if (expandedResult != null) {
                result = expandedResult
                messages.add("替换 #${index + 1} 成功")
                continue
            }
            messages.add("替换 #${index + 1} 失败: 找到 $occurrenceCount 处匹配，请提供更长的唯一代码块")
            continue
        }
        
        // 执行替换
        result = result.replaceFirst(trimmedOld, trimmedNew)
        messages.add("替换 #${index + 1} 成功")
    }
    
    return result to messages.joinToString("\n")
}

/**
 * 尝试通过扩展上下文来获得唯一匹配
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

private fun countOccurrences(text: String, pattern: String): Int {
    var count = 0
    var idx = text.indexOf(pattern)
    while (idx != -1) {
        count++
        idx = text.indexOf(pattern, idx + 1)
    }
    return count
}

private fun normalizeWhitespace(text: String): String {
    return text.replace(Regex("\\s+"), " ").trim()
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
                    LineChangeType.Unchanged -> null
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
