package com.template.jh.core.editor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

private val keywordColor = Color(0xFF7F52FF)       // 关键字
private val stringColor = Color(0xFF4CAF50)         // 字符串
private val commentColor = Color(0xFF8B949E)        // 注释
private val numberColor = Color(0xFF79C0FF)         // 数字
private val annotationColor = Color(0xFFFFA726)     // 注解

private val keywords = setOf(
    // Kotlin
    "fun", "val", "var", "class", "object", "interface", "enum", "data",
    "sealed", "abstract", "open", "override", "private", "protected", "public",
    "internal", "companion", "init", "constructor", "this", "super",
    "if", "else", "when", "for", "while", "do", "return", "continue", "break",
    "try", "catch", "finally", "throw", "import", "package", "as", "is", "in", "!in",
    "typealias", "inline", "noinline", "crossinline", "reified", "suspend", "tailrec",
    "operator", "infix", "const", "lateinit", "by", "get", "set", "field", "it",
    "null", "true", "false", "Unit", "Any", "Nothing", "String", "Int", "Long",
    "Float", "Double", "Boolean", "Char", "Short", "Byte", "List", "Map", "Set",
    // Java
    "public", "static", "void", "final", "extends", "implements", "new",
    "synchronized", "volatile", "transient", "native", "strictfp",
    // XML
)

fun highlightSyntax(text: String): AnnotatedString {
    if (text.isEmpty()) return AnnotatedString("")

    return buildAnnotatedString {
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

            // 其他字符
            append(ch)
            i++
        }
    }
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
