package com.medeide.jh.screens.home.landscape.workspace.editor

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

// 纯文本编辑器 — 无语法高亮，复用 NormalEditMode 的行号/缩放/滚动
@Composable
fun TextEditor(
    text: TextFieldValue,
    onTextChange: (TextFieldValue) -> Unit,
    readOnly: Boolean = false,
    searchScrollVersion: Int = 0,
) {
    NormalEditMode(
        text = text,
        onTextChange = onTextChange,
        readOnly = readOnly,
        searchScrollVersion = searchScrollVersion,
        visualTransformation = IdentityTransformation,
    )
}

private object IdentityTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        return TransformedText(text, OffsetMapping.Identity)
    }
}
