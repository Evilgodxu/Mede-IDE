package com.template.jh.screens.home.components

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.template.jh.R
import com.template.jh.screens.home.FileItem

// 资源管理器面板
@Composable
fun ResourcePanel(
    openedFolderName: String?,
    files: List<FileItem>,
    onCloseFolder: () -> Unit = {},
    onListChildren: (Uri, (List<FileItem>) -> Unit) -> Unit = { _, _ -> },
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // 标题栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (openedFolderName != null)
                    stringResource(R.string.resource_manager_title) + ": " + openedFolderName
                else stringResource(R.string.resource_manager_title),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }

        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        if (openedFolderName == null) {
            // 未打开文件夹
            Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.no_folder_opened),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else if (files.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                Text("空文件夹", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(files, key = { it.uri.toString() }) { item ->
                    FileTreeItem(
                        item = item,
                        depth = 0,
                        onListChildren = onListChildren,
                    )
                }
            }
        }
    }
}

@Composable
private fun FileTreeItem(
    item: FileItem,
    depth: Int,
    onListChildren: (Uri, (List<FileItem>) -> Unit) -> Unit,
) {
    // 每个展开目录的子文件列表
    val childrenCache = remember { mutableStateMapOf<String, List<FileItem>>() }
    var isExpanded by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    if (item.isDirectory) {
                        if (!isExpanded) {
                            // 展开
                            val cached = childrenCache[item.uri.toString()]
                            if (cached != null) {
                                isExpanded = true
                            } else {
                                isLoading = true
                                isExpanded = true
                                onListChildren(item.uri) { children ->
                                    childrenCache[item.uri.toString()] = children
                                    isLoading = false
                                }
                            }
                        } else {
                            isExpanded = false
                        }
                    }
                }
                .padding(start = (8 + depth * 16).dp, end = 8.dp, top = 2.dp, bottom = 2.dp)
                .heightIn(min = 28.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 展开/折叠箭头
            if (item.isDirectory) {
                if (isLoading) {
                    CircularProgressIndicator(
                        Modifier.size(12.dp).padding(end = 4.dp),
                        strokeWidth = 1.5.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = if (isExpanded) "折叠" else "展开",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Spacer(Modifier.width(14.dp))
            }

            Spacer(Modifier.width(4.dp))

            // 图标
            Icon(
                imageVector = when {
                    item.isDirectory && isExpanded -> Icons.Default.FolderOpen
                    item.isDirectory -> Icons.Default.Folder
                    item.name.let {
                        it.endsWith(".kt") || it.endsWith(".java") || it.endsWith(".xml") || it.endsWith(".gradle")
                    } -> Icons.Default.Description
                    item.name.let {
                        it.endsWith(".png") || it.endsWith(".jpg") || it.endsWith(".webp") || it.endsWith(".gif")
                    } -> Icons.Default.Image
                    else -> Icons.Default.InsertDriveFile
                },
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = when {
                    item.isDirectory -> MaterialTheme.colorScheme.primary
                    item.name.endsWith(".kt") -> Color(0xFF7F52FF)
                    item.name.endsWith(".xml") -> Color(0xFF2196F3)
                    item.name.endsWith(".gradle") || item.name.endsWith(".kts") -> Color(0xFF00BCD4)
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )

            Spacer(Modifier.width(6.dp))

            Text(
                text = item.name,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }

        // 展开的子文件
        AnimatedVisibility(visible = isExpanded && item.isDirectory) {
            Column {
                childrenCache[item.uri.toString()]?.forEach { child ->
                    FileTreeItem(
                        item = child,
                        depth = depth + 1,
                        onListChildren = onListChildren,
                    )
                }
            }
        }
    }
}

