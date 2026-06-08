package com.template.jh.screens.home.components.editor

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import com.template.jh.core.editor.highlightSyntax

// 语法高亮 VisualTransformation - 单一职责：语法着色
class SyntaxHighlightTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val highlighted = highlightSyntax(text.text)
        return TransformedText(highlighted, OffsetMapping.Identity)
    }
}
