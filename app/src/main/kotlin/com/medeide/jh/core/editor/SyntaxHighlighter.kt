package com.medeide.jh.core.editor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

private val keywordColor = Color(0xFF7F52FF)
private val stringColor = Color(0xFF4CAF50)
private val commentColor = Color(0xFF8B949E)
private val numberColor = Color(0xFF79C0FF)
private val annotationColor = Color(0xFFFFA726)

private val keywords = setOf(
    "fun", "val", "var", "class", "object", "interface", "enum", "data",
    "sealed", "abstract", "open", "override", "private", "protected", "public",
    "internal", "companion", "init", "constructor", "this", "super",
    "if", "else", "when", "for", "while", "do", "return", "continue", "break",
    "try", "catch", "finally", "throw", "import", "package", "as", "is", "in", "!in",
    "typealias", "inline", "noinline", "crossinline", "reified", "suspend", "tailrec",
    "operator", "infix", "const", "lateinit", "by", "get", "set", "field", "it",
    "null", "true", "false", "Unit", "Any", "Nothing", "String", "Int", "Long",
    "Float", "Double", "Boolean", "Char", "Short", "Byte", "List", "Map", "Set",
    "public", "static", "void", "final", "extends", "implements", "new",
    "synchronized", "volatile", "transient", "native", "strictfp",
)

// 内容哈希缓存：避免同内容重复高亮
private val syntaxCache = object : LinkedHashMap<Int, AnnotatedString>(16, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, AnnotatedString>?): Boolean {
        return size > 8
    }
}

fun highlightSyntax(text: String): AnnotatedString {
    if (text.isEmpty()) return AnnotatedString("")
    val key = text.hashCode()
    synchronized(syntaxCache) { syntaxCache[key]?.let { return it } }

    val result = buildAnnotatedString {
        var i = 0
        val len = text.length

        while (i < len) {
            val ch = text[i]

            // 单行注释 //
            if (ch == '/' && i + 1 < len && text[i + 1] == '/') {
                val end = text.indexOf('\n', i).let { if (it == -1) len else it }
                withStyle(SpanStyle(color = commentColor)) { append(text.substring(i, end)) }
                i = end
                continue
            }

            // 多行注释 /* */
            if (ch == '/' && i + 1 < len && text[i + 1] == '*') {
                val end = text.indexOf("*/", i + 2).let { if (it == -1) len else it + 2 }
                withStyle(SpanStyle(color = commentColor)) { append(text.substring(i, end)) }
                i = end
                continue
            }

            // 字符串 "
            if (ch == '"') {
                val end = findStringEnd(text, i + 1)
                withStyle(SpanStyle(color = stringColor)) { append(text.substring(i, end)) }
                i = end
                continue
            }

            // 注解 @
            if (ch == '@' && i + 1 < len && text[i + 1].isLetter()) {
                val end = findWordEnd(text, i + 1)
                withStyle(SpanStyle(color = annotationColor, fontWeight = FontWeight.Normal)) {
                    append(text.substring(i, end))
                }
                i = end
                continue
            }

            // 数字
            if (ch.isDigit() && (i == 0 || !text[i - 1].isLetterOrDigit())) {
                val end = findNumberEnd(text, i)
                withStyle(SpanStyle(color = numberColor)) { append(text.substring(i, end)) }
                i = end
                continue
            }

            // 关键字 / 标识符
            if (ch.isLetter() || ch == '_') {
                val end = findWordEnd(text, i)
                val word = text.substring(i, end)
                if (word in keywords) {
                    withStyle(SpanStyle(color = keywordColor, fontWeight = FontWeight.Bold)) {
                        append(word)
                    }
                } else {
                    append(word)
                }
                i = end
                continue
            }

            append(ch)
            i++
        }
    }

    synchronized(syntaxCache) { syntaxCache[key] = result }
    return result
}

private fun findStringEnd(text: String, start: Int): Int {
    var i = start
    while (i < text.length) {
        when (text[i]) {
            '\\' -> i += 2
            '"' -> return i + 1
            else -> i++
        }
    }
    return text.length
}

private fun findWordEnd(text: String, start: Int): Int {
    var i = start
    while (i < text.length && (text[i].isLetterOrDigit() || text[i] == '_')) i++
    return i
}

private fun findNumberEnd(text: String, start: Int): Int {
    var i = start
    var hasDot = false
    while (i < text.length) {
        val c = text[i]
        if (c.isDigit()) { i++; continue }
        if (c == '.' && !hasDot && i + 1 < text.length && text[i + 1].isDigit()) {
            hasDot = true; i++; continue
        }
        if (c == 'x' || c == 'X' || c == 'b' || c == 'B' || c == 'L' || c == 'l' || c == 'f' || c == 'F') {
            i++; break
        }
        break
    }
    return i
}
