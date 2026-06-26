package com.medeide.jh.screens.home.landscape.workspace.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue

// 代码编辑器 — 带语法高亮
@Composable
fun CodeEditor(
    text: TextFieldValue,
    onTextChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
    searchScrollVersion: Int = 0,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
        ) {
            NormalEditMode(
                text = text,
                onTextChange = onTextChange,
                readOnly = readOnly,
                visualTransformation = SyntaxHighlightTransformation(),
                searchScrollVersion = searchScrollVersion,
            )
        }
    }
}
