package com.template.jh.screens.home.components.resourcepanel

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

// 重命名对话框
@Composable
fun RenameDialog(
    initialName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember(initialName) { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("重命名") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                label = { Text("新名称") },
            )
        },
        confirmButton = {
            TextButton(onClick = {
                val newName = text.trim()
                if (newName.isNotEmpty()) onConfirm(newName)
            }) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

// 新建文件/目录对话框
@Composable
fun CreateDialog(
    isDirectory: Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf("") }
    val title = if (isDirectory) "新建目录" else "新建文件"
    val label = if (isDirectory) "目录名称" else "文件名称"
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                label = { Text(label) },
            )
        },
        confirmButton = {
            TextButton(onClick = {
                val name = text.trim()
                if (name.isNotEmpty()) onConfirm(name)
            }) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
