package com.template.jh.screens.home.components.resourcepanel

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.template.jh.R

// 目录树右键菜单 - 单一职责：上下文菜单
@Composable
fun TreeContextMenu(
    expanded: Boolean,
    node: ResourceNode,
    onDismiss: () -> Unit,
    onOpenFile: () -> Unit,
    onAddToConversation: () -> Unit,
    onCreateFile: () -> Unit,
    onCreateDirectory: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
    ) {
        if (!node.isDirectory) {
            DropdownMenuItem(
                text = { Text("打开文件") },
                onClick = { onDismiss(); onOpenFile() },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(16.dp)) },
            )
        }
        DropdownMenuItem(
            text = { Text(stringResource(R.string.add_to_conversation)) },
            onClick = { onDismiss(); onAddToConversation() },
            leadingIcon = { Icon(Icons.Default.Add, null, Modifier.size(16.dp)) },
        )
        if (node.isDirectory) {
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("新建文件") },
                onClick = { onDismiss(); onCreateFile() },
                leadingIcon = { Icon(Icons.Default.Add, null, Modifier.size(16.dp)) },
            )
            DropdownMenuItem(
                text = { Text("新建目录") },
                onClick = { onDismiss(); onCreateDirectory() },
                leadingIcon = { Icon(Icons.Default.Folder, null, Modifier.size(16.dp)) },
            )
        }
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text("重命名") },
            onClick = { onDismiss(); onRename() },
            leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, null, Modifier.size(16.dp)) },
        )
        DropdownMenuItem(
            text = { Text("复制名称") },
            onClick = {
                onDismiss()
                val clip = ClipData.newPlainText("fileName", node.name)
                (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
            },
            leadingIcon = { Icon(Icons.Default.ContentCopy, null, Modifier.size(16.dp)) },
        )
        DropdownMenuItem(
            text = { Text("复制路径") },
            onClick = {
                onDismiss()
                val clip = ClipData.newPlainText("path", node.relativePath)
                (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
            },
            leadingIcon = { Icon(Icons.Default.ContentCopy, null, Modifier.size(16.dp)) },
        )
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text("删除") },
            onClick = { onDismiss(); onDelete() },
            leadingIcon = { Icon(Icons.Default.Delete, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error) },
        )
    }
}
