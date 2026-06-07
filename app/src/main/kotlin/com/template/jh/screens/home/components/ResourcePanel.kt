package com.template.jh.screens.home.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
    onFileClick: (FileItem) -> Unit = {},
    onAddToConversation: (FileItem) -> Unit = {},
    onRename: (Uri, String) -> Unit = { _, _ -> },
    onDelete: (Uri) -> Unit = {},
    onCreate: (Uri, String, Boolean) -> Unit = { _, _, _ -> },
) {
    var renameTarget by remember { mutableStateOf<FileItem?>(null) }
    var renameText by remember { mutableStateOf("") }

    // 创建对话框状态
    var createTarget by remember { mutableStateOf<FileItem?>(null) }
    var createIsDir by remember { mutableStateOf(false) }
    var createText by remember { mutableStateOf("") }

    // 重命名对话框
    renameTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("重命名") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    label = { Text("新名称") },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val newName = renameText.trim()
                    if (newName.isNotEmpty() && newName != target.name) {
                        onRename(target.uri, newName)
                    }
                    renameTarget = null
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("取消") }
            },
        )
    }

    // 创建对话框
    createTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { createTarget = null },
            title = { Text(if (createIsDir) "新建目录" else "新建文件") },
            text = {
                OutlinedTextField(
                    value = createText,
                    onValueChange = { createText = it },
                    singleLine = true,
                    label = { Text(if (createIsDir) "目录名称" else "文件名称") },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val name = createText.trim()
                    if (name.isNotEmpty()) {
                        onCreate(target.uri, name, createIsDir)
                    }
                    createTarget = null
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { createTarget = null }) { Text("取消") }
            },
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 文件夹名称
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
                        onFileClick = onFileClick,
                        onAddToConversation = onAddToConversation,
                        onRenameRequest = { renameTarget = it; renameText = it.name },
                        onDelete = onDelete,
                        onCreateRequest = { target, isDir ->
                            createTarget = target; createIsDir = isDir; createText = ""
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileTreeItem(
    item: FileItem,
    depth: Int,
    autoExpandedUris: Set<String> = emptySet(),
    onListChildren: (Uri, (List<FileItem>) -> Unit) -> Unit,
    onFileClick: (FileItem) -> Unit = {},
    onAddToConversation: (FileItem) -> Unit = {},
    onRenameRequest: (FileItem) -> Unit = {},
    onDelete: (Uri) -> Unit = {},
    onCreateRequest: (FileItem, Boolean) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    val childrenCache = remember { mutableStateMapOf<String, List<FileItem>>() }
    var isExpanded by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var showContextMenu by remember { mutableStateOf(false) }
    var drillDownUris by remember { mutableStateOf<Set<String>>(emptySet()) }

    // 自动展开：当上级目录钻取到此节点时触发
    val needsAutoExpand = item.uri.toString() in autoExpandedUris
    LaunchedEffect(needsAutoExpand) {
        if (needsAutoExpand && item.isDirectory && !isExpanded) {
            isLoading = true
            onListChildren(item.uri) { children ->
                childrenCache[item.uri.toString()] = children
                isExpanded = true
                isLoading = false
                if (children.isNotEmpty() && children.all { it.isDirectory }) {
                    startDrill(children[0].uri, childrenCache, onListChildren) { drillUris ->
                        drillDownUris = drillUris
                    }
                }
            }
        }
    }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = {
                        if (item.isDirectory) {
                            if (isLoading) return@combinedClickable
                            if (isExpanded) {
                                isExpanded = false
                                drillDownUris = emptySet()
                            } else {
                                val cached = childrenCache[item.uri.toString()]
                                if (cached != null) {
                                    isExpanded = true
                                    if (cached.isNotEmpty() && cached.all { it.isDirectory }) {
                                        startDrill(cached[0].uri, childrenCache, onListChildren) { drillUris ->
                                            drillDownUris = drillUris
                                        }
                                    }
                                } else {
                                    isLoading = true
                                    onListChildren(item.uri) { children ->
                                        childrenCache[item.uri.toString()] = children
                                        isExpanded = true
                                        isLoading = false
                                        if (children.isNotEmpty() && children.all { it.isDirectory }) {
                                            startDrill(children[0].uri, childrenCache, onListChildren) { drillUris ->
                                                drillDownUris = drillUris
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            onFileClick(item)
                        }
                    },
                    onLongClick = { showContextMenu = true },
                )
                .padding(vertical = 2.dp)
                .heightIn(min = 28.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val indent = depth
            if (indent > 0) Spacer(Modifier.width((indent * 16).dp))

            if (item.isDirectory) {
                if (isLoading) {
                    CircularProgressIndicator(Modifier.size(12.dp).padding(end = 4.dp), strokeWidth = 1.5.dp, color = MaterialTheme.colorScheme.primary)
                } else {
                    Icon(Icons.Default.ChevronRight, if (isExpanded) "折叠" else "展开", Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Spacer(Modifier.width(14.dp))
            }

            Spacer(Modifier.width(4.dp))

            Icon(
                imageVector = when {
                    item.isDirectory && isExpanded -> Icons.Default.FolderOpen
                    item.isDirectory -> Icons.Default.Folder
                    item.name.let { it.endsWith(".kt") || it.endsWith(".java") || it.endsWith(".xml") || it.endsWith(".gradle") } -> Icons.Default.Description
                    item.name.let { it.endsWith(".png") || it.endsWith(".jpg") || it.endsWith(".webp") || it.endsWith(".gif") } -> Icons.Default.Image
                    else -> Icons.Default.InsertDriveFile
                },
                contentDescription = null, modifier = Modifier.size(14.dp),
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
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )

            // 右键菜单
            Box {
                DropdownMenu(
                    expanded = showContextMenu,
                    onDismissRequest = { showContextMenu = false },
                ) {
                    if (!item.isDirectory) {
                        DropdownMenuItem(
                            text = { Text("打开文件") },
                            onClick = { showContextMenu = false; onFileClick(item) },
                            leadingIcon = { Icon(Icons.Default.OpenInNew, null, Modifier.size(16.dp)) },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.add_to_conversation)) },
                        onClick = { showContextMenu = false; onAddToConversation(item) },
                        leadingIcon = { Icon(Icons.Default.Add, null, Modifier.size(16.dp)) },
                    )
                    if (item.isDirectory) {
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("新建文件") },
                            onClick = { showContextMenu = false; onCreateRequest(item, false) },
                            leadingIcon = { Icon(Icons.Default.Add, null, Modifier.size(16.dp)) },
                        )
                        DropdownMenuItem(
                            text = { Text("新建目录") },
                            onClick = { showContextMenu = false; onCreateRequest(item, true) },
                            leadingIcon = { Icon(Icons.Default.Folder, null, Modifier.size(16.dp)) },
                        )
                    }
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("重命名") },
                        onClick = { showContextMenu = false; onRenameRequest(item) },
                        leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, null, Modifier.size(16.dp)) },
                    )
                    DropdownMenuItem(
                        text = { Text("复制路径") },
                        onClick = {
                            showContextMenu = false
                            val clip = ClipData.newPlainText("path", item.uri.toString())
                            (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
                        },
                        leadingIcon = { Icon(Icons.Default.ContentCopy, null, Modifier.size(16.dp)) },
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("删除") },
                        onClick = { showContextMenu = false; onDelete(item.uri) },
                        leadingIcon = { Icon(Icons.Default.Delete, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error) },
                    )
                }
            }
        }

        // 展开的子文件
        AnimatedVisibility(visible = isExpanded && item.isDirectory) {
            Column {
                val cached = childrenCache[item.uri.toString()]
                if (cached != null) {
                    if (cached.isNotEmpty() && cached.all { it.isDirectory }) {
                        // ===== 紧凑模式：所有子项都是目录，压缩为一条路径 =====
                        val pathText = cached.joinToString("/") { it.name }
                        val lastDir = cached.last()
                        var compactOpen by remember(lastDir.uri) { mutableStateOf(false) }
                        var compactLoading by remember(lastDir.uri) { mutableStateOf(false) }
                        var compactChildren by remember { mutableStateOf<List<FileItem>?>(null) }

                        // 展开时加载最后一个目录的子项
                        LaunchedEffect(compactOpen) {
                            if (compactOpen && compactChildren == null) {
                                compactLoading = true
                                onListChildren(lastDir.uri) { children ->
                                    compactChildren = children
                                    compactLoading = false
                                }
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 28.dp)
                                .combinedClickable(
                                    onClick = {
                                        if (!compactLoading) compactOpen = !compactOpen
                                    },
                                    onLongClick = { showContextMenu = true },
                                )
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Spacer(Modifier.width(((depth + 1) * 16).dp))

                            if (compactLoading) {
                                CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 1.5.dp, color = MaterialTheme.colorScheme.primary)
                            } else {
                                Icon(
                                    Icons.Default.ChevronRight,
                                    if (compactOpen) "折叠" else "展开",
                                    Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }

                            Spacer(Modifier.width(4.dp))
                            Icon(
                                Icons.Default.Folder,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = pathText,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                        }

                        // 紧凑路径展开后的实际内容
                        AnimatedVisibility(visible = compactOpen) {
                            Column {
                                if (compactChildren != null) {
                                    compactChildren!!.forEach { child ->
                                        FileTreeItem(
                                            item = child,
                                            depth = depth + 2,
                                            onListChildren = onListChildren,
                                            onFileClick = onFileClick,
                                            onAddToConversation = onAddToConversation,
                                            onRenameRequest = onRenameRequest,
                                            onDelete = onDelete,
                                            onCreateRequest = onCreateRequest,
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        // ===== 正常模式：逐个渲染子项 =====
                        cached.forEach { child ->
                            FileTreeItem(
                                item = child,
                                depth = depth + 1,
                                autoExpandedUris = drillDownUris,
                                onListChildren = onListChildren,
                                onFileClick = onFileClick,
                                onAddToConversation = onAddToConversation,
                                onRenameRequest = onRenameRequest,
                                onDelete = onDelete,
                                onCreateRequest = onCreateRequest,
                            )
                        }
                    }
                }
            }
        }
    }
}

// 递归钻取：沿着单子目录链条加载，返回需要自动展开的 URI 集合
private fun startDrill(
    uri: Uri,
    cache: MutableMap<String, List<FileItem>>,
    onListChildren: (Uri, (List<FileItem>) -> Unit) -> Unit,
    onResult: (Set<String>) -> Unit,
) {
    val drillUris = mutableSetOf<String>()
    fun step(curUri: Uri) {
        val cached = cache[curUri.toString()]
        if (cached != null) {
            if (cached.isNotEmpty() && cached.all { it.isDirectory }) {
                val next = cached[0]
                drillUris.add(next.uri.toString())
                step(next.uri)
            }
        }
    }
    step(uri)
    if (drillUris.isEmpty()) {
        // 尚未缓存，加载一次
        onListChildren(uri) { children ->
            cache[uri.toString()] = children
            if (children.isNotEmpty() && children.all { it.isDirectory }) {
                val next = children[0]
                drillUris.add(next.uri.toString())
                step(next.uri)
            }
            onResult(drillUris)
        }
    } else {
        onResult(drillUris)
    }
}

