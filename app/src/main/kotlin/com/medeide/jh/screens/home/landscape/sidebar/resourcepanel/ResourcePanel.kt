package com.medeide.jh.screens.home.landscape.sidebar.resourcepanel

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.SyncAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.medeide.jh.R
import com.medeide.jh.model.FileItem
import com.medeide.jh.screens.home.logic.FileOperationEvents
import java.io.File

/**
 * 使用绝对路径列出子文件（保持右列独立）
 */
private fun listChildrenAbsolute(absolutePath: String, displayName: String, onResult: (List<FileItem>) -> Unit) {
    val dir = File(absolutePath)
    if (!dir.isDirectory) {
        onResult(emptyList())
        return
    }
    try {
        val files = dir.listFiles()?.map { file ->
            FileItem(
                name = file.name,
                uri = android.net.Uri.fromFile(file),
                isDirectory = file.isDirectory,
                relativePath = file.absolutePath, // 使用绝对路径作为 relativePath
                filePath = file.absolutePath,
                size = if (file.isFile) file.length() else 0L,
                lastModified = file.lastModified(),
            )
        }?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() })) ?: emptyList()
        onResult(files)
    } catch (_: Exception) {
        onResult(emptyList())
    }
}

/**
 * 双列文件管理器 - 使用两个独立的 FilePane
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ResourcePanel(
    openedFolderName: String?,
    files: List<FileItem>,
    storageRootName: String? = null,
    onListChildren: (String, (List<FileItem>) -> Unit) -> Unit = { _, _ -> },
    onFileClick: (FileItem) -> Unit = {},
    onAddToConversation: (FileItem) -> Unit = {},
    onRename: (String, String) -> Unit = { _, _ -> },
    onDelete: (String) -> Unit = {},
    onCreate: (String, String, Boolean) -> Unit = { _, _, _ -> },
    onCopy: (srcPath: String, dstDirPath: String) -> Unit = { _, _ -> },
    onMove: (srcPath: String, dstDirPath: String) -> Unit = { _, _ -> },
    onCompress: (paths: List<String>, archiveName: String, format: String, level: Int, password: String?, volumeSize: Int?, deleteSource: Boolean) -> Unit = { _, _, _, _, _, _, _ -> },
    onDiff: (oldPath: String, newPath: String) -> Unit = { _, _ -> },
    onMerge: (oldPath: String, newPath: String) -> Unit = { _, _ -> },
    onOpenAsProject: ((String) -> Unit)? = null,
    onExitProjectMode: (() -> Unit)? = null,
    projectDirPath: String = "",
    onLeftPanePathChanged: ((String) -> Unit)? = null,
    storageRootPath: String = "",
) {
    // 左右两个独立面板状态
    var leftPane by remember { mutableStateOf<PaneState?>(null) }
    var rightPane by remember { mutableStateOf<PaneState?>(null) }

    // 导航历史（左右各自独立）
    val leftHistory = remember { mutableStateListOf<PaneState>() }
    var leftHistoryIdx by remember { mutableStateOf(-1) }
    val rightHistory = remember { mutableStateListOf<PaneState>() }
    var rightHistoryIdx by remember { mutableStateOf(-1) }

    fun pushLeftHistory(pane: PaneState) {
        while (leftHistory.size > leftHistoryIdx + 1) leftHistory.removeLast()
        leftHistory.add(pane)
        leftHistoryIdx = leftHistory.size - 1
    }

    fun pushRightHistory(pane: PaneState) {
        while (rightHistory.size > rightHistoryIdx + 1) rightHistory.removeLast()
        rightHistory.add(pane)
        rightHistoryIdx = rightHistory.size - 1
    }

    // 左列选择状态（完全独立）
    val leftSelectedItems = remember { mutableStateListOf<String>() }
    var leftSelectionAnchor by remember { mutableStateOf<String?>(null) }
    var leftIsSelectionMode by remember { mutableStateOf(false) }
    var leftLastSelectWasSwipe by remember { mutableStateOf(false) }

    // 右列选择状态（完全独立）
    val rightSelectedItems = remember { mutableStateListOf<String>() }
    var rightSelectionAnchor by remember { mutableStateOf<String?>(null) }
    var rightIsSelectionMode by remember { mutableStateOf(false) }
    var rightLastSelectWasSwipe by remember { mutableStateOf(false) }

    // 上一个交互的列（0=左，1=右）
    var lastInteractedPane by remember { mutableStateOf(0) }

    // 对话框状态
    var renameTarget by remember { mutableStateOf<ResourceNode?>(null) }
    var createTarget by remember { mutableStateOf<ResourceNode?>(null) }
    var createIsDir by remember { mutableStateOf(false) }
    var compressTargetPaths by remember { mutableStateOf<List<String>?>(null) }
    var infoTarget by remember { mutableStateOf<FileItem?>(null) }

    // 复制/移动覆盖确认
    data class PendingOverwrite(
        val srcPath: String,
        val dstDirPath: String,
        val fileName: String,
        val isMove: Boolean,
    )
    var pendingOverwrite by remember { mutableStateOf<PendingOverwrite?>(null) }

    fun getLeftSelectedCount(): Int = leftSelectedItems.size
    fun getRightSelectedCount(): Int = rightSelectedItems.size

    fun isArchiveFile(name: String): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in setOf("zip", "tar", "gz", "bz2", "7z", "rar")
    }

    fun readArchiveEntries(archivePath: String): List<FileItem> {
        return try {
            val zipFile = net.lingala.zip4j.ZipFile(archivePath)
            zipFile.fileHeaders
                .filter { header ->
                    // 过滤掉空名称和Mac系统文件
                    val name = header.fileName.substringAfterLast('/')
                    name.isNotBlank() && !name.startsWith(".") && !header.fileName.contains("__MACOSX")
                }
                .map { header ->
                    val name = header.fileName.substringAfterLast('/')
                    FileItem(
                        name = name,
                        uri = android.net.Uri.EMPTY,
                        isDirectory = header.isDirectory,
                        relativePath = header.fileName,
                        size = header.uncompressedSize,
                        lastModified = header.lastModifiedTime,
                        filePath = header.fileName,
                    )
                }
                .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun extractFiles(archivePath: String, entryPaths: List<String>, destDir: String) {
        try {
            val zipFile = net.lingala.zip4j.ZipFile(archivePath)
            // 如果 entryPaths 只包含一个空字符串，表示解压整个压缩包
            val pathsToExtract = if (entryPaths.size == 1 && entryPaths[0].isEmpty()) {
                zipFile.fileHeaders.map { it.fileName }
            } else {
                entryPaths
            }
            for (entryPath in pathsToExtract) {
                val header = zipFile.getFileHeader(entryPath)
                if (header != null) zipFile.extractFile(header, destDir)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // 左列初始化/更新
    LaunchedEffect(files, openedFolderName) {
        if (leftPane == null) {
            leftPane = PaneState("", openedFolderName ?: "", files)
            leftHistory.clear()
            leftHistory.add(leftPane!!)
            leftHistoryIdx = 0
        } else {
            leftPane = leftPane?.copy(files = files)
        }
    }

    // 右列初始化（仅一次，到存储根目录）- 使用绝对路径保持独立
    LaunchedEffect(storageRootName, storageRootPath) {
        if (rightPane == null && storageRootName != null && storageRootPath.isNotEmpty()) {
            // 右列使用绝对路径，基于 storageRootPath 列出根目录内容
            listChildrenAbsolute(storageRootPath, storageRootName) { children ->
                rightPane = PaneState(storageRootPath, storageRootName, children)
                pushRightHistory(rightPane!!)
            }
        }
    }

    // 文件操作事件完成后即时刷新双栏当前目录
    LaunchedEffect(Unit) {
        FileOperationEvents.events.collect {
            leftPane?.let { lp ->
                onListChildren(lp.path) { children ->
                    leftPane = lp.copy(files = children)
                }
            }
            rightPane?.let { rp ->
                // 右列使用绝对路径刷新，保持独立
                listChildrenAbsolute(rp.path, rp.displayName) { children ->
                    rightPane = rp.copy(files = children)
                }
            }
        }
    }

    // 左侧面板当前浏览目录变化时通知外部
    LaunchedEffect(leftPane?.path) {
        leftPane?.path?.let { onLeftPanePathChanged?.invoke(it) }
    }

    // 切换选中状态（列级完全隔离，容器仅做跨列总数限制）
    fun toggleSelect(path: String, paneIndex: Int, paneFiles: List<FileItem>, isSwipe: Boolean) {
        val currentSelected = if (paneIndex == 0) leftSelectedItems else rightSelectedItems
        val currentAnchor = if (paneIndex == 0) leftSelectionAnchor else rightSelectionAnchor
        val currentIsSelectionMode = if (paneIndex == 0) leftIsSelectionMode else rightIsSelectionMode
        val currentLastSwipe = if (paneIndex == 0) leftLastSelectWasSwipe else rightLastSelectWasSwipe
        val otherSelected = if (paneIndex == 0) rightSelectedItems else leftSelectedItems
        val totalSelected = currentSelected.size + otherSelected.size

        // 跨列限制：另一列已有2个时，禁止本列新建选中；但总大小=2且本列为空+另一列为1时允许（跨列对比）
        if (totalSelected >= 2 && currentSelected.isEmpty() && !(totalSelected == 2 && otherSelected.size == 1)) return

        if (!currentIsSelectionMode) {
            if (paneIndex == 0) {
                leftIsSelectionMode = true
                leftSelectionAnchor = path
                leftSelectedItems.clear()
                leftSelectedItems.add(path)
                leftLastSelectWasSwipe = isSwipe
            } else {
                rightIsSelectionMode = true
                rightSelectionAnchor = path
                rightSelectedItems.clear()
                rightSelectedItems.add(path)
                rightLastSelectWasSwipe = isSwipe
            }
            return
        }

        if (currentSelected.contains(path)) {
            currentSelected.remove(path)
            if (currentSelected.isEmpty()) {
                if (paneIndex == 0) {
                    leftIsSelectionMode = false
                    leftSelectionAnchor = null
                    leftLastSelectWasSwipe = false
                } else {
                    rightIsSelectionMode = false
                    rightSelectionAnchor = null
                    rightLastSelectWasSwipe = false
                }
            } else {
                if (paneIndex == 0) {
                    leftSelectionAnchor = path
                    leftLastSelectWasSwipe = isSwipe
                } else {
                    rightSelectionAnchor = path
                    rightLastSelectWasSwipe = isSwipe
                }
            }
            return
        }

        // 添加选中
        if (isSwipe && currentLastSwipe) {
            val anchor = currentAnchor
            if (anchor != null && anchor != path) {
                val anchorIdx = paneFiles.indexOfFirst { it.relativePath == anchor || it.filePath == anchor }
                val curIdx = paneFiles.indexOfFirst { it.relativePath == path || it.filePath == path }
                if (anchorIdx >= 0 && curIdx >= 0) {
                    val range = if (anchorIdx < curIdx) anchorIdx..curIdx else curIdx..anchorIdx
                    for (i in range) {
                        val p = paneFiles[i].relativePath.ifEmpty { paneFiles[i].filePath }
                        if (p.isNotBlank()) currentSelected.add(p)
                    }
                } else {
                    currentSelected.add(path)
                }
            } else {
                currentSelected.add(path)
            }
            if (paneIndex == 0) {
                leftSelectionAnchor = path
                leftLastSelectWasSwipe = true
            } else {
                rightSelectionAnchor = path
                rightLastSelectWasSwipe = true
            }
        } else {
            currentSelected.add(path)
            if (paneIndex == 0) {
                leftSelectionAnchor = path
                leftLastSelectWasSwipe = isSwipe
            } else {
                rightSelectionAnchor = path
                rightLastSelectWasSwipe = isSwipe
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 顶部工具栏
        if (openedFolderName != null) {
            val activePane = if (lastInteractedPane == 0) leftPane else rightPane
            val activeSelected = if (lastInteractedPane == 0) leftSelectedItems else rightSelectedItems
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = activePane?.displayName?.ifEmpty { openedFolderName } ?: openedFolderName,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                val activeIsSelectionMode = if (lastInteractedPane == 0) leftIsSelectionMode else rightIsSelectionMode
                if (activeIsSelectionMode && activeSelected.isNotEmpty()) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "${activeSelected.size} 已选",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "取消",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.combinedClickable(
                            onClick = {
                                if (lastInteractedPane == 0) {
                                    leftSelectedItems.clear()
                                    leftSelectionAnchor = null
                                    leftIsSelectionMode = false
                                } else {
                                    rightSelectedItems.clear()
                                    rightSelectionAnchor = null
                                    rightIsSelectionMode = false
                                }
                            },
                            onLongClick = {},
                        ),
                    )
                }
            }
            HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        }

        Box(modifier = Modifier.weight(1f)) {
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
                leftPane == null -> {
                    Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                        Text("加载中…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                else -> {
                    Row(modifier = Modifier.fillMaxSize()) {
                        // 左列
                        FilePane(
                            paneState = leftPane!!,
                            showNavigateUp = true,
                            selectedItems = leftSelectedItems.toList(),
                            isSelectionMode = leftIsSelectionMode,
                            isActive = lastInteractedPane == 0,
                            onActive = { lastInteractedPane = 0 },
                            onNavigateUp = {
                                val parent = File(leftPane!!.path).parent ?: ""
                                val parentName = File(parent).name
                                onListChildren(parent) { children ->
                                    leftPane = PaneState(parent, parentName, children)
                                    pushLeftHistory(leftPane!!)
                                }
                            },
                            onNavigateInto = { file ->
                                val relPath = file.relativePath.ifEmpty { file.filePath }
                                if (file.isDirectory) {
                                    onListChildren(relPath) { children ->
                                        leftPane = PaneState(relPath, file.name, children)
                                        pushLeftHistory(leftPane!!)
                                    }
                                } else if (isArchiveFile(file.name)) {
                                    // 左列使用相对路径，需要转换为绝对路径读取压缩包
                                    val absPath = "$storageRootPath/$relPath"
                                    val entries = readArchiveEntries(absPath)
                                    leftPane = PaneState(relPath, file.name, entries, archivePath = absPath)
                                    pushLeftHistory(leftPane!!)
                                } else {
                                    onFileClick(file)
                                }
                            },
                            onFileClick = { file ->
                                if (leftIsSelectionMode) {
                                    val path = file.relativePath.ifEmpty { file.filePath }
                                    toggleSelect(path, 0, leftPane!!.files, false)
                                } else {
                                    val relPath = file.relativePath.ifEmpty { file.filePath }
                                    if (file.isDirectory) {
                                        onListChildren(relPath) { children ->
                                            leftPane = PaneState(relPath, file.name, children)
                                            pushLeftHistory(leftPane!!)
                                        }
                                    } else if (isArchiveFile(file.name)) {
                                        // 左列使用相对路径，需要转换为绝对路径读取压缩包
                                        val absPath = "$storageRootPath/$relPath"
                                        val entries = readArchiveEntries(absPath)
                                        leftPane = PaneState(relPath, file.name, entries, archivePath = absPath)
                                        pushLeftHistory(leftPane!!)
                                    } else {
                                        onFileClick(file)
                                    }
                                }
                            },
                            onFileLongClick = { file ->
                                val path = file.relativePath.ifEmpty { file.filePath }
                                toggleSelect(path, 0, leftPane!!.files, true)
                            },
                            onAddToConversation = onAddToConversation,
                            onRename = { renameTarget = it },
                            onDelete = onDelete,
                            onCreateFile = { createTarget = it; createIsDir = false },
                            onCreateDirectory = { createTarget = it; createIsDir = true },
                            onCopyToOther = rightPane?.let { rp -> { path ->
                                // rp.path 是绝对路径，需要转换为相对路径
                                val dstRelPath = rp.path.removePrefix(storageRootPath).trimStart('/')
                                val name = path.substringAfterLast('/')
                                if (rp.files.any { it.name == name }) {
                                    pendingOverwrite = PendingOverwrite(path, dstRelPath, name, false)
                                } else {
                                    onCopy(path, dstRelPath)
                                }
                            } },
                            onMoveToOther = rightPane?.let { rp -> { path ->
                                // rp.path 是绝对路径，需要转换为相对路径
                                val dstRelPath = rp.path.removePrefix(storageRootPath).trimStart('/')
                                val name = path.substringAfterLast('/')
                                if (rp.files.any { it.name == name }) {
                                    pendingOverwrite = PendingOverwrite(path, dstRelPath, name, true)
                                } else {
                                    onMove(path, dstRelPath)
                                }
                            } },
                            onExtractToOther = if (leftPane!!.archivePath != null && rightPane != null) {
                                // 解压目标路径是绝对路径，直接使用
                                { paths -> extractFiles(leftPane!!.archivePath!!, paths, rightPane!!.path) }
                            } else null,
                            onViewInfo = { infoTarget = it },
                            onCompress = { paths ->
                                // 转换绝对路径为相对路径
                                compressTargetPaths = paths.map { it.removePrefix(storageRootPath).trimStart('/') }
                            },
                            onOpenAsProject = onOpenAsProject,
                            onExitProjectMode = {
                                onExitProjectMode?.invoke()
                                onListChildren("") { children ->
                                    leftPane = PaneState("", openedFolderName ?: "", children)
                                    pushLeftHistory(leftPane!!)
                                }
                            },
                            isProjectModeActive = projectDirPath.isNotEmpty() && projectDirPath != storageRootPath,
                            isLeftPane = true,
                            modifier = Modifier.weight(1f),
                        )

                        // 右列
                        if (rightPane != null) {
                            VerticalDivider(
                                modifier = Modifier.fillMaxHeight(),
                                thickness = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            )
                            FilePane(
                                paneState = rightPane!!,
                                showNavigateUp = true,
                                rootPath = storageRootPath,
                                selectedItems = rightSelectedItems.toList(),
                                isSelectionMode = rightIsSelectionMode,
                                isActive = lastInteractedPane == 1,
                                onActive = { lastInteractedPane = 1 },
                                onNavigateUp = {
                                    val parent = File(rightPane!!.path).parent
                                    if (parent != null && parent.startsWith(storageRootPath)) {
                                        val parentName = File(parent).name.takeIf { it.isNotEmpty() } ?: storageRootName ?: "存储"
                                        listChildrenAbsolute(parent, parentName) { children ->
                                            rightPane = PaneState(parent, parentName, children)
                                            pushRightHistory(rightPane!!)
                                        }
                                    }
                                },
                                onFileClick = { file ->
                                    if (rightIsSelectionMode) {
                                        val path = file.relativePath.ifEmpty { file.filePath }
                                        toggleSelect(path, 1, rightPane!!.files, false)
                                    } else {
                                        val absPath = file.filePath.ifEmpty { file.relativePath }
                                        if (file.isDirectory) {
                                            // 右列使用绝对路径导航，保持独立
                                            listChildrenAbsolute(absPath, file.name) { children ->
                                                rightPane = PaneState(absPath, file.name, children)
                                                pushRightHistory(rightPane!!)
                                            }
                                        } else if (isArchiveFile(file.name)) {
                                            val entries = readArchiveEntries(absPath)
                                            rightPane = PaneState(absPath, file.name, entries, archivePath = absPath)
                                            pushRightHistory(rightPane!!)
                                        } else {
                                            onFileClick(file)
                                        }
                                    }
                                },
                                onFileLongClick = { file ->
                                    val path = file.relativePath.ifEmpty { file.filePath }
                                    toggleSelect(path, 1, rightPane!!.files, true)
                                },
                                onAddToConversation = onAddToConversation,
                                onRename = { renameTarget = it },
                                onDelete = { path ->
                                    // 右列使用绝对路径，需要转换为相对路径
                                    val relPath = path.removePrefix(storageRootPath).trimStart('/')
                                    onDelete(relPath)
                                },
                                onCreateFile = { createTarget = it; createIsDir = false },
                                onCreateDirectory = { createTarget = it; createIsDir = true },
                                onCopyToOther = { path ->
                                    // path 是绝对路径，需要转换为相对路径给回调
                                    val relPath = path.removePrefix(storageRootPath).trimStart('/')
                                    val dstRelPath = leftPane!!.path.removePrefix(storageRootPath).trimStart('/')
                                    val name = path.substringAfterLast('/')
                                    if (leftPane!!.files.any { it.name == name }) {
                                        pendingOverwrite = PendingOverwrite(relPath, dstRelPath, name, false)
                                    } else {
                                        onCopy(relPath, dstRelPath)
                                    }
                                },
                                onMoveToOther = { path ->
                                    // path 是绝对路径，需要转换为相对路径给回调
                                    val relPath = path.removePrefix(storageRootPath).trimStart('/')
                                    val dstRelPath = leftPane!!.path.removePrefix(storageRootPath).trimStart('/')
                                    val name = path.substringAfterLast('/')
                                    if (leftPane!!.files.any { it.name == name }) {
                                        pendingOverwrite = PendingOverwrite(relPath, dstRelPath, name, true)
                                    } else {
                                        onMove(relPath, dstRelPath)
                                    }
                                },
                                onExtractToOther = if (rightPane!!.archivePath != null) {
                                    // 解压目标路径需要转换为绝对路径
                                    { paths -> extractFiles(rightPane!!.archivePath!!, paths, "$storageRootPath/${leftPane!!.path}") }
                                } else null,
                                onViewInfo = { infoTarget = it },
                                onCompress = { paths ->
                                    // 转换绝对路径为相对路径
                                    compressTargetPaths = paths.map { it.removePrefix(storageRootPath).trimStart('/') }
                                },
                                isLeftPane = false,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }

        // 跨列对比/合并条件计算（容器层聚合）
        val allSelectedPaths = leftSelectedItems.toList() + rightSelectedItems.toList()
        val allPaneFiles = (leftPane?.files ?: emptyList()) + (rightPane?.files ?: emptyList())
        val selectedFiles = allPaneFiles.filter { it.relativePath.ifEmpty { it.filePath } in allSelectedPaths }
        val totalSelectedSize = selectedFiles.sumOf { it.size }
        val canDiff = allSelectedPaths.size == 2 && selectedFiles.size == 2 &&
            selectedFiles.all { !it.isDirectory && isTextFile(it.name) && it.size < 5 * 1024 * 1024 }
        val canMerge = allSelectedPaths.size == 2 && selectedFiles.size == 2 &&
            selectedFiles.all { !it.isDirectory && isTextFile(it.name) } && totalSelectedSize < 5 * 1024 * 1024

        // 左列底部工具栏（完全独立）
        if (lastInteractedPane == 0) {
            BottomToolbar(
                openedFolderName = openedFolderName,
                pane = leftPane,
                historyIdx = leftHistoryIdx,
                history = leftHistory,
                selectedItems = leftSelectedItems.toList(),
                canDiff = canDiff,
                canMerge = canMerge,
                onNavigateUp = {
                    leftPane?.let { lp ->
                        val parentFullPath = File(lp.path).parent
                        if (parentFullPath != null) {
                            val parent = parentFullPath.removePrefix(storageRootPath).trimStart('/')
                            val parentName = File(parentFullPath).name.takeIf { it.isNotEmpty() } ?: openedFolderName ?: ""
                            onListChildren(parent) { children ->
                                leftPane = PaneState(parent, parentName, children)
                                pushLeftHistory(leftPane!!)
                            }
                        }
                    }
                },
                onNavigateForward = {
                    val idx = leftHistoryIdx + 1
                    if (idx < leftHistory.size) {
                        leftPane = leftHistory[idx]
                        leftHistoryIdx = idx
                    }
                },
                onNavigateToRoot = {
                    onListChildren("") { children ->
                        leftPane = PaneState("", openedFolderName ?: "", children)
                        pushLeftHistory(leftPane!!)
                    }
                },
                onSyncPath = {
                    leftPane?.let { lp ->
                        val targetPath = if (lp.archivePath != null) {
                            java.io.File(lp.archivePath).parent ?: storageRootPath
                        } else if (lp.path.isEmpty()) {
                            storageRootPath
                        } else {
                            if (lp.path.startsWith("/")) lp.path else "$storageRootPath/${lp.path}"
                        }
                        val targetName = java.io.File(targetPath).name.takeIf { it.isNotEmpty() } ?: storageRootName ?: "存储"
                        listChildrenAbsolute(targetPath, targetName) { children ->
                            rightPane = PaneState(targetPath, targetName, children)
                            pushRightHistory(rightPane!!)
                        }
                    }
                },
                onCreateFile = {
                    createTarget = leftPane?.let {
                        val uri = android.net.Uri.parse("file://$storageRootPath/${it.path}")
                        ResourceNode(uri, it.displayName, it.path, true, 0, "$storageRootPath/${it.path}")
                    }
                    createIsDir = false
                },
                onCreateDirectory = {
                    createTarget = leftPane?.let {
                        val uri = android.net.Uri.parse("file://$storageRootPath/${it.path}")
                        ResourceNode(uri, it.displayName, it.path, true, 0, "$storageRootPath/${it.path}")
                    }
                    createIsDir = true
                },
                onSelectAll = {
                    leftPane?.files?.forEach { file ->
                        val path = file.relativePath.ifEmpty { file.filePath }
                        if (path.isNotBlank()) leftSelectedItems.add(path)
                    }
                },
                onInvertSelection = {
                    leftPane?.files?.forEach { file ->
                        val path = file.relativePath.ifEmpty { file.filePath }
                        if (path in leftSelectedItems) leftSelectedItems.remove(path)
                        else if (path.isNotBlank()) leftSelectedItems.add(path)
                    }
                    if (leftSelectedItems.isEmpty()) {
                        leftSelectionAnchor = null
                        leftIsSelectionMode = false
                    }
                },
                onClearSelection = {
                    leftSelectedItems.clear()
                    leftSelectionAnchor = null
                    leftIsSelectionMode = false
                },
                onDiff = {
                    val allSelected = leftSelectedItems.toList() + rightSelectedItems.toList()
                    if (allSelected.size == 2) onDiff(allSelected[0], allSelected[1])
                },
                onMerge = {
                    val allSelected = leftSelectedItems.toList() + rightSelectedItems.toList()
                    if (allSelected.size == 2) onMerge(allSelected[0], allSelected[1])
                },
                storageRootPath = storageRootPath,
            )
        } else {
            // 右列底部工具栏（完全独立）
            BottomToolbar(
                openedFolderName = openedFolderName,
                pane = rightPane,
                historyIdx = rightHistoryIdx,
                history = rightHistory,
                selectedItems = rightSelectedItems.toList(),
                canDiff = canDiff,
                canMerge = canMerge,
                onNavigateUp = {
                    rightPane?.let { rp ->
                        val parent = File(rp.path).parent
                        if (parent != null && parent.startsWith(storageRootPath)) {
                            val parentName = File(parent).name.takeIf { it.isNotEmpty() } ?: storageRootName ?: "存储"
                            listChildrenAbsolute(parent, parentName) { children ->
                                rightPane = PaneState(parent, parentName, children)
                                pushRightHistory(rightPane!!)
                            }
                        }
                    }
                },
                onNavigateForward = {
                    val idx = rightHistoryIdx + 1
                    if (idx < rightHistory.size) {
                        rightPane = rightHistory[idx]
                        rightHistoryIdx = idx
                    }
                },
                onNavigateToRoot = {
                    listChildrenAbsolute(storageRootPath, storageRootName ?: "存储") { children ->
                        rightPane = PaneState(storageRootPath, storageRootName ?: "存储", children)
                        pushRightHistory(rightPane!!)
                    }
                },
                onSyncPath = {
                    rightPane?.let { rp ->
                        val targetRelPath = if (rp.archivePath != null) {
                            java.io.File(rp.archivePath).parent?.removePrefix(storageRootPath)?.trimStart('/') ?: ""
                        } else {
                            rp.path.removePrefix(storageRootPath).trimStart('/')
                        }
                        val targetName = if (rp.archivePath != null) {
                            java.io.File(rp.archivePath).parentFile?.name?.takeIf { it.isNotEmpty() } ?: openedFolderName ?: ""
                        } else {
                            rp.displayName
                        }
                        onListChildren(targetRelPath) { children ->
                            leftPane = PaneState(targetRelPath, targetName, children)
                            pushLeftHistory(leftPane!!)
                        }
                    }
                },
                onCreateFile = {
                    createTarget = rightPane?.let {
                        val uri = android.net.Uri.parse("file://${it.path}")
                        ResourceNode(uri, it.displayName, it.path, true, 0, it.path)
                    }
                    createIsDir = false
                },
                onCreateDirectory = {
                    createTarget = rightPane?.let {
                        val uri = android.net.Uri.parse("file://${it.path}")
                        ResourceNode(uri, it.displayName, it.path, true, 0, it.path)
                    }
                    createIsDir = true
                },
                onSelectAll = {
                    rightPane?.files?.forEach { file ->
                        val path = file.relativePath.ifEmpty { file.filePath }
                        if (path.isNotBlank()) rightSelectedItems.add(path)
                    }
                },
                onInvertSelection = {
                    rightPane?.files?.forEach { file ->
                        val path = file.relativePath.ifEmpty { file.filePath }
                        if (path in rightSelectedItems) rightSelectedItems.remove(path)
                        else if (path.isNotBlank()) rightSelectedItems.add(path)
                    }
                    if (rightSelectedItems.isEmpty()) {
                        rightSelectionAnchor = null
                        rightIsSelectionMode = false
                    }
                },
                onClearSelection = {
                    rightSelectedItems.clear()
                    rightSelectionAnchor = null
                    rightIsSelectionMode = false
                },
                onDiff = {
                    val allSelected = leftSelectedItems.toList() + rightSelectedItems.toList()
                    if (allSelected.size == 2) onDiff(allSelected[0], allSelected[1])
                },
                onMerge = {
                    val allSelected = leftSelectedItems.toList() + rightSelectedItems.toList()
                    if (allSelected.size == 2) onMerge(allSelected[0], allSelected[1])
                },
                storageRootPath = storageRootPath,
            )
        }
    }

    // 对话框
    renameTarget?.let { node ->
        RenameDialog(
            initialName = node.name,
            onConfirm = { newName ->
                // 如果是绝对路径（右列），转换为相对路径
                val relPath = node.relativePath.removePrefix(storageRootPath).trimStart('/')
                onRename(relPath, newName)
                renameTarget = null
            },
            onDismiss = { renameTarget = null },
        )
    }

    createTarget?.let {
        CreateDialog(
            isDirectory = createIsDir,
            onConfirm = { name ->
                // 如果是绝对路径（右列），转换为相对路径
                val relPath = createTarget!!.relativePath.removePrefix(storageRootPath).trimStart('/')
                onCreate(relPath, name, createIsDir)
                createTarget = null
            },
            onDismiss = { createTarget = null },
        )
    }

    compressTargetPaths?.let { paths ->
        val defaultName = if (paths.size == 1) {
            paths[0].substringAfterLast('/').substringBeforeLast('.')
        } else "archive"
        CompressDialog(
            defaultName = defaultName,
            onDismiss = { compressTargetPaths = null },
            onConfirm = { archiveName, format, level, password, volumeSize, deleteSource ->
                onCompress(paths, archiveName, format, level, password, volumeSize, deleteSource)
                compressTargetPaths = null
            },
        )
    }

    infoTarget?.let { file ->
        FileInfoDialog(
            file = file,
            onDismiss = { infoTarget = null },
        )
    }

    pendingOverwrite?.let { po ->
        OverwriteConfirmDialog(
            fileName = po.fileName,
            onConfirm = {
                if (po.isMove) onMove(po.srcPath, po.dstDirPath)
                else onCopy(po.srcPath, po.dstDirPath)
                pendingOverwrite = null
            },
            onDismiss = { pendingOverwrite = null },
        )
    }
}

@Composable
private fun BottomToolbar(
    openedFolderName: String?,
    pane: PaneState?,
    historyIdx: Int,
    history: List<PaneState>,
    selectedItems: List<String>,
    canDiff: Boolean,
    canMerge: Boolean,
    onNavigateUp: () -> Unit,
    onNavigateForward: () -> Unit,
    onNavigateToRoot: () -> Unit,
    onSyncPath: () -> Unit,
    onCreateFile: () -> Unit,
    onCreateDirectory: () -> Unit,
    onSelectAll: () -> Unit,
    onInvertSelection: () -> Unit,
    onClearSelection: () -> Unit,
    onDiff: () -> Unit,
    onMerge: () -> Unit,
    storageRootPath: String,
) {
    if (openedFolderName == null) return

    HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

    val hasSelection = selectedItems.isNotEmpty()

    if (hasSelection) {
        // 选择模式：全选、反选、取消、对比、合并
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            TextButton(onClick = onSelectAll) {
                Text("全选", style = MaterialTheme.typography.labelSmall)
            }
            TextButton(onClick = onInvertSelection) {
                Text("反选", style = MaterialTheme.typography.labelSmall)
            }
            TextButton(onClick = onClearSelection) {
                Text("取消", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }
            TextButton(
                onClick = onDiff,
                enabled = canDiff,
            ) {
                Text(
                    "对比",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (canDiff) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                )
            }
            TextButton(
                onClick = onMerge,
                enabled = canMerge,
            ) {
                Text(
                    "合并",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (canMerge) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                )
            }
        }
    } else {
        // 普通模式：后退、前进、新建、同步、回到根目录
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            val isAtRoot = pane?.path?.isEmpty() == true || pane?.path == storageRootPath
            IconButton(
                onClick = onNavigateUp,
                enabled = !isAtRoot && pane != null,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "后退",
                    tint = if (!isAtRoot && pane != null) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                )
            }
            IconButton(
                onClick = onNavigateForward,
                enabled = historyIdx < history.size - 1,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "前进",
                    tint = if (historyIdx < history.size - 1) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                )
            }

            var showCreateMenu by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { showCreateMenu = true }) {
                    Icon(Icons.Default.Add, contentDescription = "新建")
                }
                DropdownMenu(
                    expanded = showCreateMenu,
                    onDismissRequest = { showCreateMenu = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("新建文件") },
                        onClick = {
                            showCreateMenu = false
                            onCreateFile()
                        },
                        leadingIcon = {
                            Icon(Icons.Default.InsertDriveFile, contentDescription = null)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("新建文件夹") },
                        onClick = {
                            showCreateMenu = false
                            onCreateDirectory()
                        },
                        leadingIcon = {
                            Icon(Icons.Default.CreateNewFolder, contentDescription = null)
                        },
                    )
                }
            }

            IconButton(onClick = onSyncPath) {
                Icon(Icons.Default.SyncAlt, contentDescription = "同步")
            }

            val canNavigateToRoot = pane?.path?.isNotEmpty() == true && pane.path != storageRootPath
            IconButton(
                onClick = onNavigateToRoot,
                enabled = canNavigateToRoot,
            ) {
                Icon(
                    Icons.Default.Home,
                    contentDescription = "根目录",
                    tint = if (canNavigateToRoot) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                )
            }
        }
    }
}

private fun isTextFile(name: String): Boolean {
    val textExts = setOf("txt", "md", "json", "xml", "kt", "java", "py", "js", "ts", "html", "css", "c", "cpp", "h", "hpp", "rs", "go", "rb", "php", "swift", "m", "mm")
    return name.substringAfterLast('.', "").lowercase() in textExts
}
