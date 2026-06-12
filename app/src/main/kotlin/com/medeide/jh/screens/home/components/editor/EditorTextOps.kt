package com.medeide.jh.screens.home.components.editor

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/**
 * 触屏文本操作工具：
 * 替代 PC 物理按键（Tab/Ctrl+/ 等）的纯触屏实现
 */

/** 缩进：选中每行前插入 4 空格。无选中则光标处插入 4 空格 */
fun TextFieldValue.indent(): TextFieldValue {
    val sel = selection
    val text = text
    if (sel.start == sel.end) {
        // 无选中 → 光标处插入 4 空格
        val newText = text.substring(0, sel.start) + "    " + text.substring(sel.end)
        return TextFieldValue(newText, TextRange(sel.start + 4))
    }
    // 有选中 → 每行前加 4 空格
    val lines = text.lines()
    val startLine = text.substring(0, sel.start).count { it == '\n' }
    val endLine = startLine + text.substring(sel.start, sel.end).count { it == '\n' }
    val result = lines.toMutableList()
    var offsetDelta = 0
    for (i in startLine..endLine.coerceAtMost(lines.size - 1)) {
        result[i] = "    ${result[i]}"
        offsetDelta += 4
    }
    return TextFieldValue(result.joinToString("\n"), TextRange(sel.start + 4, sel.end + offsetDelta))
}

/** 取消缩进：选中每行前移除至多 4 空格。无选中则光标所在行移除 4 空格 */
fun TextFieldValue.dedent(): TextFieldValue {
    val sel = selection
    val text = text
    val lines = text.lines()
    val startLine = text.substring(0, sel.start).count { it == '\n' }
    val endLine = if (sel.start == sel.end) {
        startLine
    } else {
        startLine + text.substring(sel.start, sel.end).count { it == '\n' }
    }

    val result = lines.toMutableList()
    var removedStart = 0
    var removedEnd = 0
    for (i in startLine..endLine.coerceAtMost(lines.size - 1)) {
        val line = result[i]
        val toRemove = line.takeWhile { it == ' ' }.let { spaces ->
            if (spaces.length > 4) 4 else spaces.length
        }
        result[i] = line.substring(toRemove)
        if (i == startLine) removedStart = toRemove
        if (i == endLine) removedEnd = toRemove
    }
    val newStart = if (sel.start == sel.end) sel.start - removedStart else sel.start - removedStart
    val newEnd = sel.end - removedEnd
    return TextFieldValue(
        result.joinToString("\n"),
        TextRange(newStart.coerceAtLeast(0), newEnd.coerceAtLeast(newStart))
    )
}

/** 切换行注释（//） */
fun TextFieldValue.toggleComment(): TextFieldValue {
    val sel = selection
    val text = text
    val lines = text.lines()
    val startLine = text.substring(0, sel.start).count { it == '\n' }
    val endLine = if (sel.start == sel.end) {
        startLine
    } else {
        startLine + text.substring(sel.start, sel.end).count { it == '\n' }
    }

    val result = lines.toMutableList()
    var allCommented = true
    for (i in startLine..endLine.coerceAtMost(lines.size - 1)) {
        if (!result[i].trimStart().startsWith("//")) {
            allCommented = false
            break
        }
    }

    var offsetDelta = 0
    for (i in startLine..endLine.coerceAtMost(lines.size - 1)) {
        if (allCommented) {
            // 取消注释
            val idx = result[i].indexOf("//")
            if (idx >= 0) {
                result[i] = result[i].substring(0, idx) + result[i].substring(idx + 2)
                offsetDelta -= 2
            }
        } else {
            // 添加注释
            val indent = result[i].takeWhile { it == ' ' }
            val rest = result[i].trimStart()
            result[i] = "${indent}// $rest"
            offsetDelta += 2
        }
    }

    val newSel = if (allCommented) {
        TextRange(sel.start, sel.end + offsetDelta)
    } else {
        TextRange(sel.start + 2, sel.end + offsetDelta)
    }
    return TextFieldValue(
        result.joinToString("\n"),
        TextRange(newSel.start.coerceAtLeast(0), newSel.end.coerceAtLeast(newSel.start))
    )
}

/** 选中光标所在整行 */
fun TextFieldValue.selectCurrentLine(): TextFieldValue {
    val text = text
    val cursor = selection.start
    val lineStart = text.substring(0, cursor).let { pre ->
        pre.lastIndexOf('\n').let { if (it == -1) 0 else it + 1 }
    }
    val lineEnd = text.indexOf('\n', cursor).let { if (it == -1) text.length else it }
    return copy(selection = TextRange(lineStart, lineEnd))
}
