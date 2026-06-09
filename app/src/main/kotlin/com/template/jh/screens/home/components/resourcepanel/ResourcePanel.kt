package com.template.jh.screens.home.components.resourcepanel

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.template.jh.R
import com.template.jh.model.FileItem

/**
 * 资源管理器面板 - 使用 FileManager 相对路径
 *
 * onListChildren 参数为 relativePath，与 FileManager 一致
 */
@Composable
fun ResourcePanel(
    openedFolderName: String?,
    files: List<FileItem>,
    onListChildren: (String, (List<FileItem>) -> Unit) -> Unit = { _, _ -> },
    onFileClick: (FileItem) -> Unit = {},
    onAddToConversation: (FileItem) -> Unit = {},
    onRename: (String, String) -> Unit = { _, _ -> },
    onDelete: (String) -> Unit = {},
    onCreate: (String, String, Boolean) -> Unit = { _, _, _ -> },
    onOpenAsProject: ((String) -> Unit)? = null,  // 接收目录的 filePath（绝对路径）
    projectDirPath: String = "",                  // 当前已打开项目目录，匹配目录时隐藏"以项目目录打开"
) {
    val treeState = rememberFlatTreeState()

    remember(files) { treeState.setRoot(files) }

    var renameTarget by remember { mutableStateOf<ResourceNode?>(null) }
    var createTarget by remember { mutableStateOf<ResourceNode?>(null) }
    var createIsDir by remember { mutableStateOf(false) }

    renameTarget?.let { target ->
        RenameDialog(
            initialName = target.name,
            onConfirm = { newName ->
                onRename(target.relativePath, newName)
                renameTarget = null
            },
            onDismiss = { renameTarget = null },
        )
    }

    createTarget?.let { target ->
        CreateDialog(
            isDirectory = createIsDir,
            onConfirm = { name ->
                onCreate(target.relativePath, name, createIsDir)
                createTarget = null
            },
            onDismiss = { createTarget = null },
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (openedFolderName != null) {
            Text(
                text = openedFolderName,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
            HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        }

        when {
            openedFolderName == null -> {
                Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.no_folder_opened),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            files.isEmpty() -> {
                Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                    Text("空文件夹", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            else -> {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(treeState.visibleNodes, key = { it.relativePath }) { node ->
                        FlatTreeItem(
                            node = node,
                            isExpanded = treeState.isExpanded(node),
                            isLoading = treeState.isLoading(node.relativePath),
                            onClick = {
                                if (node.isDirectory) {
                                    treeState.toggle(node, onListChildren)
                                } else {
                                    onFileClick(FileItem(node.name, node.uri, false, relativePath = node.relativePath, filePath = node.filePath))
                                }
                            },
                            onLongClick = {},
                            contextMenu = { expanded, onDismiss ->
                                TreeContextMenu(
                                    expanded = expanded,
                                    node = node,
                                    onDismiss = onDismiss,
                                    onOpenFile = { onFileClick(FileItem(node.name, node.uri, false, relativePath = node.relativePath, filePath = node.filePath)) },
                                    onAddToConversation = { onAddToConversation(FileItem(node.name, node.uri, node.isDirectory, relativePath = node.relativePath, filePath = node.filePath)) },
                                    onCreateFile = { createTarget = node; createIsDir = false },
                                    onCreateDirectory = { createTarget = node; createIsDir = true },
                                    onRename = { renameTarget = node },
                                    onDelete = { onDelete(node.relativePath) },
                                    onOpenAsProject = if (node.filePath.isNotEmpty()) {
                                        { onOpenAsProject?.invoke(node.filePath) }
                                    } else {
                                        { onOpenAsProject?.invoke(node.relativePath) }
                                    },
                                    currentProjectPath = projectDirPath,
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}
