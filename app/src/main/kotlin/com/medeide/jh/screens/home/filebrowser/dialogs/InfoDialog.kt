package com.medeide.jh.screens.home.filebrowser.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.unit.dp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.medeide.jh.screens.home.filebrowser.logic.FileEntry
import com.medeide.jh.screens.home.filebrowser.logic.formatFileSize
import java.io.File

@Composable
fun InfoDialog(
    file: FileEntry,
    onDismiss: () -> Unit,
) {
    val (type, size, lastModified) = remember(file) {
        when (file) {
            is FileEntry.Real -> {
                val f = file.file
                val sz = if (f.isFile) f.length()
                else f.walkTopDown().filter { it.isFile }.map { it.length() }.sum()
                Triple(if (f.isDirectory) "文件夹" else "文件", sz, f.lastModified())
            }
            is FileEntry.Archive -> Triple(
                if (file.isDirectory) "文件夹" else "文件",
                0L, 0L
            )
        }
    }
    val sdf = remember {
        java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("属性") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("名称: ${file.name}", style = MaterialTheme.typography.bodySmall)
                Text("路径: ${file.absolutePath}", style = MaterialTheme.typography.bodySmall)
                Text("类型: $type", style = MaterialTheme.typography.bodySmall)
                if (size > 0) {
                    Text("大小: ${formatFileSize(size)}", style = MaterialTheme.typography.bodySmall)
                }
                if (lastModified > 0) {
                    Text("修改时间: ${sdf.format(java.util.Date(lastModified))}", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("确定") } },
    )
}
