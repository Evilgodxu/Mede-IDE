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
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowBack
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

// 目录树右键/长按菜单
@Composable
fun TreeContextMenu(
    expanded: Boolean,
    node: ResourceNode,
    selectedCount: Int = 1,
    selectedPaths: List<String> = emptyList(),
    archivePath: String? = null,
    onDismiss: () -> Unit,
    onOpenFile: () -> Unit,
    onAddToConversation: () -> Unit,
    onCreateFile: () -> Unit,
    onCreateDirectory: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onCopyToLeft: (() -> Unit)? = null,
    onCopyToRight: (() -> Unit)? = null,
    onMoveToLeft: (() -> Unit)? = null,
    onMoveToRight: (() -> Unit)? = null,
    onExtractToLeft: ((List<String>) -> Unit)? = null,
    onExtractToRight: ((List<String>) -> Unit)? = null,
    onCopyName: () -> Unit,
    onCopyPath: () -> Unit,
    onViewInfo: (() -> Unit)? = null,
    onCompress: (() -> Unit)? = null,
    onOpenAsProject: (() -> Unit)? = null,
    currentProjectPath: String = "",
) {
    val context = LocalContext.current
    val hasProjectSet = currentProjectPath.isNotBlank()
    val showOpenAsProject = onOpenAsProject != null && node.isDirectory && !hasProjectSet
    val isMultiSelect = selectedCount > 1

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
    ) {
        if (!node.isDirectory && !isMultiSelect) {
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
        HorizontalDivider()

        // 复制 / 移动（仅单选时显示）
        if (!isMultiSelect) {
            if (onCopyToLeft != null) {
                DropdownMenuItem(
                    text = { Text("复制到左侧目录") },
                    onClick = { onDismiss(); onCopyToLeft() },
                    leadingIcon = { Icon(Icons.Default.ArrowBack, null, Modifier.size(16.dp)) },
                )
            }
            if (onCopyToRight != null) {
                DropdownMenuItem(
                    text = { Text("复制到右侧目录") },
                    onClick = { onDismiss(); onCopyToRight() },
                    leadingIcon = { Icon(Icons.Default.ArrowForward, null, Modifier.size(16.dp)) },
                )
            }
            if (onMoveToLeft != null) {
                DropdownMenuItem(
                    text = { Text("移动到左侧目录") },
                    onClick = { onDismiss(); onMoveToLeft() },
                    leadingIcon = { Icon(Icons.Default.ArrowBack, null, Modifier.size(16.dp)) },
                )
            }
            if (onMoveToRight != null) {
                DropdownMenuItem(
                    text = { Text("移动到右侧目录") },
                    onClick = { onDismiss(); onMoveToRight() },
                    leadingIcon = { Icon(Icons.Default.ArrowForward, null, Modifier.size(16.dp)) },
                )
            }
            if (archivePath != null) {
                if (onExtractToLeft != null) {
                    DropdownMenuItem(
                        text = { Text(if (isMultiSelect) "批量解压到左侧 $selectedCount 项" else "解压到左侧目录") },
                        onClick = { onDismiss(); onExtractToLeft(selectedPaths) },
                        leadingIcon = { Icon(Icons.Default.ArrowBack, null, Modifier.size(16.dp)) },
                    )
                }
                if (onExtractToRight != null) {
                    DropdownMenuItem(
                        text = { Text(if (isMultiSelect) "批量解压到右侧 $selectedCount 项" else "解压到右侧目录") },
                        onClick = { onDismiss(); onExtractToRight(selectedPaths) },
                        leadingIcon = { Icon(Icons.Default.ArrowForward, null, Modifier.size(16.dp)) },
                    )
                }
            }
            if (onCopyToLeft != null || onCopyToRight != null || onMoveToLeft != null || onMoveToRight != null || archivePath != null) {
                HorizontalDivider()
            }
        }

        if (node.isDirectory && !isMultiSelect) {
            if (showOpenAsProject) {
                DropdownMenuItem(
                    text = { Text("以项目目录打开") },
                    onClick = { onDismiss(); onOpenAsProject() },
                    leadingIcon = { Icon(Icons.Default.FolderOpen, null, Modifier.size(16.dp)) },
                )
            }
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
            HorizontalDivider()
        }

        if (!isMultiSelect) {
            DropdownMenuItem(
                text = { Text("重命名") },
                onClick = { onDismiss(); onRename() },
                leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, null, Modifier.size(16.dp)) },
            )
        }
        DropdownMenuItem(
            text = { Text("复制名称") },
            onClick = { onDismiss(); onCopyName() },
            leadingIcon = { Icon(Icons.Default.ContentCopy, null, Modifier.size(16.dp)) },
        )
        DropdownMenuItem(
            text = { Text("复制路径") },
            onClick = { onDismiss(); onCopyPath() },
            leadingIcon = { Icon(Icons.Default.ContentCopy, null, Modifier.size(16.dp)) },
        )

        if (onViewInfo != null && !isMultiSelect) {
            DropdownMenuItem(
                text = { Text("查看信息") },
                onClick = { onDismiss(); onViewInfo() },
                leadingIcon = { Icon(Icons.Default.Info, null, Modifier.size(16.dp)) },
            )
        }
        if (onCompress != null) {
            DropdownMenuItem(
                text = { Text(if (isMultiSelect) "批量压缩 $selectedCount 项" else "压缩") },
                onClick = { onDismiss(); onCompress() },
                leadingIcon = { Icon(Icons.Default.Archive, null, Modifier.size(16.dp)) },
            )
        }

        HorizontalDivider()
        DropdownMenuItem(
            text = { Text(if (isMultiSelect) "批量删除 $selectedCount 项" else "删除") },
            onClick = { onDismiss(); onDelete() },
            leadingIcon = { Icon(Icons.Default.Delete, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error) },
        )
    }
}
