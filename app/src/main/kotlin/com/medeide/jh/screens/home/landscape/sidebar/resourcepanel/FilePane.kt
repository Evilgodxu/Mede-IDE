package com.medeide.jh.screens.home.landscape.sidebar.resourcepanel

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.medeide.jh.R
import com.medeide.jh.model.FileItem
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// 面板数据
data class PaneState(
    val path: String,
    val displayName: String,
    val files: List<FileItem>,
    val archivePath: String? = null,
)

// 单列文件管理器组件
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FilePane(
    // 当前面板状态
    paneState: PaneState?,
    // 是否显示返回上级
    showNavigateUp: Boolean = true,
    // 根目录路径（用于判断是否在根目录）
    rootPath: String = "",
    // 选中项列表（外部管理，用于双列联动）
    selectedItems: List<String> = emptyList(),
    // 是否处于选择模式
    isSelectionMode: Boolean = false,
    // 本面板是否活跃（最后交互的面板）
    isActive: Boolean = false,
    // 当面板被点击时回调，用于更新活跃状态
    onActive: () -> Unit = {},
    // 回调
    onNavigateUp: () -> Unit = {},
    onNavigateInto: (FileItem) -> Unit = {},
    onFileClick: (FileItem) -> Unit = {},
    onFileLongClick: (FileItem) -> Unit = {},
    onAddToConversation: (FileItem) -> Unit = {},
    onRename: (ResourceNode) -> Unit = {},
    onDelete: (String) -> Unit = {},
    onCreateFile: (ResourceNode) -> Unit = {},
    onCreateDirectory: (ResourceNode) -> Unit = {},
    onCopyToOther: ((String) -> Unit)? = null,
    onMoveToOther: ((String) -> Unit)? = null,
    onExtractToOther: ((List<String>) -> Unit)? = null,
    onCopyName: (String) -> Unit = {},
    onCopyPath: (String) -> Unit = {},
    onViewInfo: (FileItem) -> Unit = {},
    onCompress: (List<String>) -> Unit = {},
    onOpenAsProject: ((String) -> Unit)? = null,
    onExitProjectMode: (() -> Unit)? = null,
    isProjectModeActive: Boolean = false,
    // 菜单类型：左列或右列
    isLeftPane: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // 判断是否显示返回上级：需要开启且不在根目录
    val showUp = showNavigateUp && paneState?.path?.isNotEmpty() == true && paneState.path != rootPath

    Column(modifier = modifier.fillMaxHeight()) {
        if (paneState == null || (paneState.files.isEmpty() && !showUp)) {
            Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                Text(
                    if (paneState == null) "加载中…" else "空文件夹",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                if (showUp) {
                    item(key = "..") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(onClick = onNavigateUp)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "..",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    }
                }
                if (paneState.files.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            Text(
                                "空文件夹",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    items(paneState.files, key = { it.relativePath.ifEmpty { it.name } }) { file ->
                        val path = file.relativePath.ifEmpty { file.filePath }
                        val isSelected = selectedItems.contains(path)
                        val node = ResourceNode(
                            uri = file.uri,
                            name = file.name,
                            relativePath = path,
                            isDirectory = file.isDirectory,
                            depth = 0,
                            filePath = file.filePath,
                        )
                        var showMenu by remember { mutableStateOf(false) }
                        var offsetX by remember { mutableFloatStateOf(0f) }

                        val bgColor by animateColorAsState(
                            targetValue = when {
                                isSelected -> MaterialTheme.colorScheme.primaryContainer
                                else -> MaterialTheme.colorScheme.surface
                            },
                            label = "selectionBg",
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .offset { IntOffset(offsetX.toInt(), 0) }
                                .pointerInput(path) {
                                    detectHorizontalDragGestures(
                                        onDragStart = {
                                            onActive()
                                        },
                                        onDragEnd = {
                                            if (kotlin.math.abs(offsetX) > 60f) {
                                                onFileLongClick(file)
                                            }
                                            offsetX = 0f
                                        },
                                        onHorizontalDrag = { _, dragAmount ->
                                            offsetX += dragAmount
                                        },
                                    )
                                }
                                .combinedClickable(
                                    onClick = {
                                        onActive()
                                        onFileClick(file)
                                    },
                                    onLongClick = {
                                        onActive()
                                        showMenu = true
                                    },
                                )
                                .background(bgColor)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (isSelectionMode) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = if (isSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Icon(
                                imageVector = FileTreeIcon.icon(node, false),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = FileTreeIcon.tint(
                                    node,
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = file.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (file.lastModified > 0) {
                                    Text(
                                        text = formatDate(file.lastModified),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }

                        // 上下文菜单
                        val allSelectedPaths = selectedItems
                        val isMultiSelect = allSelectedPaths.size > 1

                        if (isLeftPane) {
                            LeftTreeContextMenu(
                                expanded = showMenu,
                                node = node,
                                selectedCount = paneState.files.count { allSelectedPaths.contains(it.relativePath.ifEmpty { it.filePath }) },
                                selectedPaths = allSelectedPaths,
                                archivePath = paneState.archivePath,
                                onDismiss = { showMenu = false },
                                onAddToConversation = { onAddToConversation(FileItem(node.name, node.uri, node.isDirectory, relativePath = node.relativePath, filePath = node.filePath)) },
                                onCreateFile = { onCreateFile(node) },
                                onCreateDirectory = { onCreateDirectory(node) },
                                onRename = { onRename(node) },
                                onDelete = {
                                    if (isMultiSelect) {
                                        allSelectedPaths.forEach { onDelete(it) }
                                    } else {
                                        onDelete(node.relativePath)
                                    }
                                },
                                onCopyToRight = onCopyToOther?.let { cb -> { cb(path) } },
                                onMoveToRight = onMoveToOther?.let { cb -> { cb(path) } },
                                onExtractToRight = onExtractToOther?.let { { paths -> it(paths) } },
                                onCopyName = {
                                    try {
                                        val clip = android.content.ClipData.newPlainText("fileName", node.name)
                                        (context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager)
                                            .setPrimaryClip(clip)
                                    } catch (_: Exception) {}
                                },
                                onCopyPath = {
                                    try {
                                        val fullPath = node.filePath.ifEmpty { node.relativePath }
                                        val clip = android.content.ClipData.newPlainText("path", fullPath)
                                        (context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager)
                                            .setPrimaryClip(clip)
                                    } catch (_: Exception) {}
                                },
                                onViewInfo = { onViewInfo(file) },
                                onCompress = {
                                if (isMultiSelect) {
                                    onCompress(allSelectedPaths)
                                } else {
                                    onCompress(listOf(path))
                                }
                            },
                            onOpenAsProject = if (node.filePath.isNotEmpty()) {
                                { onOpenAsProject?.invoke(node.filePath) }
                            } else {
                                { onOpenAsProject?.invoke(node.relativePath) }
                            },
                            onExitProjectMode = onExitProjectMode,
                            isProjectModeActive = isProjectModeActive,
                        )
                        } else {
                            RightTreeContextMenu(
                                expanded = showMenu,
                                node = node,
                                selectedCount = paneState.files.count { allSelectedPaths.contains(it.relativePath.ifEmpty { it.filePath }) },
                                selectedPaths = allSelectedPaths,
                                archivePath = paneState.archivePath,
                                onDismiss = { showMenu = false },
                                onAddToConversation = { onAddToConversation(FileItem(node.name, node.uri, node.isDirectory, relativePath = node.relativePath, filePath = node.filePath)) },
                                onCreateFile = { onCreateFile(node) },
                                onCreateDirectory = { onCreateDirectory(node) },
                                onRename = { onRename(node) },
                                onDelete = {
                                    if (isMultiSelect) {
                                        allSelectedPaths.forEach { onDelete(it) }
                                    } else {
                                        onDelete(node.relativePath)
                                    }
                                },
                                onCopyToLeft = onCopyToOther?.let { cb -> { cb(path) } },
                                onMoveToLeft = onMoveToOther?.let { cb -> { cb(path) } },
                                onExtractToLeft = onExtractToOther?.let { { paths -> it(paths) } },
                                onCopyName = {
                                    try {
                                        val clip = android.content.ClipData.newPlainText("fileName", node.name)
                                        (context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager)
                                            .setPrimaryClip(clip)
                                    } catch (_: Exception) {}
                                },
                                onCopyPath = {
                                    try {
                                        val fullPath = node.filePath.ifEmpty { node.relativePath }
                                        val clip = android.content.ClipData.newPlainText("path", fullPath)
                                        (context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager)
                                            .setPrimaryClip(clip)
                                    } catch (_: Exception) {}
                                },
                                onViewInfo = { onViewInfo(file) },
                                onCompress = {
                                    if (isMultiSelect) {
                                        onCompress(allSelectedPaths)
                                    } else {
                                        onCompress(listOf(path))
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> "${"%.1f".format(bytes.toDouble() / (1024 * 1024))} MB"
}

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
