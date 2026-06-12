package com.medeide.jh.screens.home.components.resourcepanel

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.SyncAlt
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import com.medeide.jh.R
import com.medeide.jh.model.FileItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 资源管理器面板 - 双列文件管理器（始终显示左右两列）
 *
 * 点击目录在右侧新列显示，支持左右滑动选中文件及范围多选。
 */
@OptIn(ExperimentalFoundationApi::class)
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
    onCopy: (srcPath: String, dstDirPath: String) -> Unit = { _, _ -> },
    onMove: (srcPath: String, dstDirPath: String) -> Unit = { _, _ -> },
    onCompress: (paths: List<String>, archiveName: String, format: String, level: Int, password: String?) -> Unit = { _, _, _, _, _ -> },
    onDiff: (oldPath: String, newPath: String) -> Unit = { _, _ -> },
    onMerge: (oldPath: String, newPath: String) -> Unit = { _, _ -> },
    onOpenAsProject: ((String) -> Unit)? = null,
    projectDirPath: String = "",
) {
    data class Pane(
        val path: String,
        val displayName: String,
        val files: List<FileItem>,
        val archivePath: String? = null,
    )

    // 左右两个独立面板
    val leftPane = remember { mutableStateOf<Pane?>(null) }
    val rightPane = remember { mutableStateOf<Pane?>(null) }

    // 导航历史（左右各自独立）
    val leftHistory = remember { mutableStateListOf<Pane>() }
    var leftHistoryIdx by remember { mutableStateOf(-1) }
    val rightHistory = remember { mutableStateListOf<Pane>() }
    var rightHistoryIdx by remember { mutableStateOf(-1) }

    // 选择状态
    val selectedItems = remember { mutableStateListOf<String>() }
    var selectionAnchor by remember { mutableStateOf<String?>(null) }
    var isSelectionMode by remember { mutableStateOf(false) }
    var activePaneIndex by remember { mutableStateOf(0) }
    var lastSelectWasSwipe by remember { mutableStateOf(false) }

    fun getPaneSelectedCount(paneFiles: List<FileItem>): Int =
        paneFiles.count { selectedItems.contains(it.relativePath.ifEmpty { it.filePath }) }

    // 上一个交互的列（0=左，1=右）—— 决定底部工具栏作用于哪一列
    var lastInteractedPane by remember { mutableStateOf(0) }

    // 对话框状态
    var renameTarget by remember { mutableStateOf<ResourceNode?>(null) }
    var createTarget by remember { mutableStateOf<ResourceNode?>(null) }
    var createIsDir by remember { mutableStateOf(false) }
    var compressTargetPaths by remember { mutableStateOf<List<String>?>(null) }
    var infoTarget by remember { mutableStateOf<FileItem?>(null) }

    fun pushLeftHistory(pane: Pane) {
        while (leftHistory.size > leftHistoryIdx + 1) leftHistory.removeLast()
        leftHistory.add(pane)
        leftHistoryIdx = leftHistory.size - 1
    }

    fun pushRightHistory(pane: Pane) {
        while (rightHistory.size > rightHistoryIdx + 1) rightHistory.removeLast()
        rightHistory.add(pane)
        rightHistoryIdx = rightHistory.size - 1
    }

    fun isArchiveFile(name: String): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in setOf("zip", "tar", "gz", "bz2", "7z", "rar")
    }

    fun readArchiveEntries(archivePath: String): List<FileItem> {
        return try {
            val zipFile = net.lingala.zip4j.ZipFile(archivePath)
            zipFile.fileHeaders.map { header ->
                FileItem(
                    name = header.fileName.substringAfterLast('/'),
                    uri = android.net.Uri.EMPTY,
                    isDirectory = header.isDirectory,
                    relativePath = header.fileName,
                    size = header.uncompressedSize,
                    lastModified = header.lastModifiedTime,
                    filePath = header.fileName,
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun navigateLeft(file: FileItem) {
        val relPath = file.relativePath.ifEmpty { file.filePath }
        if (file.isDirectory) {
            onListChildren(relPath) { children ->
                leftPane.value = Pane(relPath, file.name, children)
                pushLeftHistory(leftPane.value!!)
            }
            return
        }
        if (isArchiveFile(file.name)) {
            val entries = readArchiveEntries(relPath)
            leftPane.value = Pane(relPath, file.name, entries, archivePath = relPath)
            pushLeftHistory(leftPane.value!!)
            return
        }
        onFileClick(file)
    }

    fun navigateRight(file: FileItem) {
        val relPath = file.relativePath.ifEmpty { file.filePath }
        if (file.isDirectory) {
            onListChildren(relPath) { children ->
                rightPane.value = Pane(relPath, file.name, children)
                pushRightHistory(rightPane.value!!)
            }
            return
        }
        if (isArchiveFile(file.name)) {
            val entries = readArchiveEntries(relPath)
            rightPane.value = Pane(relPath, file.name, entries, archivePath = relPath)
            pushRightHistory(rightPane.value!!)
            return
        }
        onFileClick(file)
    }

    fun extractFiles(archivePath: String, entryPaths: List<String>, destDir: String) {
        try {
            val zipFile = net.lingala.zip4j.ZipFile(archivePath)
            for (entryPath in entryPaths) {
                val header = zipFile.getFileHeader(entryPath)
                if (header != null) zipFile.extractFile(header, destDir)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    LaunchedEffect(files, openedFolderName) {
        leftPane.value = Pane("", openedFolderName ?: "", files)
        leftHistory.clear()
        rightHistory.clear()
        leftHistory.add(leftPane.value!!)
        leftHistoryIdx = 0
        // 始终初始化右列为左列当前目录的内容
        if (rightPane.value == null) {
            onListChildren("") { children ->
                rightPane.value = Pane("", openedFolderName ?: "", children)
                pushRightHistory(rightPane.value!!)
            }
        }
    }

    LaunchedEffect(files) {
        leftPane.value = leftPane.value?.copy(files = files)
    }

    // 切换选中状态
    // isSwipe=true 表示由滑动触发；false 表示由点击触发
    // 仅连续两次滑动才触发范围多选
    fun toggleSelect(path: String, paneIndex: Int, paneFiles: List<FileItem>, isSwipe: Boolean) {
        val otherPaneFiles = if (paneIndex == 0) rightPane.value?.files ?: emptyList() else leftPane.value?.files ?: emptyList()
        val otherSelectedCount = getPaneSelectedCount(otherPaneFiles)
        val currentSelectedCount = getPaneSelectedCount(paneFiles)

        // 锁定状态：双列各选1个，不能再选
        if (selectedItems.size == 2 && currentSelectedCount == 0 && otherSelectedCount == 1) return

        if (!isSelectionMode) {
            isSelectionMode = true
            activePaneIndex = paneIndex
            selectedItems.clear()
            selectionAnchor = path
            selectedItems.add(path)
            lastSelectWasSwipe = isSwipe
            return
        }

        if (activePaneIndex != paneIndex) {
            // 跨列
            if (otherSelectedCount >= 2) {
                // 另一列有多选（>=2），清空另一列，当前列单选
                selectedItems.clear()
                selectedItems.add(path)
                activePaneIndex = paneIndex
                selectionAnchor = path
                lastSelectWasSwipe = isSwipe
            } else if (otherSelectedCount == 1 && selectedItems.size < 2) {
                // 另一列只有1个，当前列追加1个（用于对比）
                selectedItems.add(path)
                activePaneIndex = paneIndex
                selectionAnchor = path
                lastSelectWasSwipe = isSwipe
            } else if (otherSelectedCount == 1 && selectedItems.size >= 2) {
                // 已满2个，不能再加（锁定）
                return
            } else {
                // 另一列没有选中，正常切换
                selectedItems.clear()
                selectedItems.add(path)
                activePaneIndex = paneIndex
                selectionAnchor = path
                lastSelectWasSwipe = isSwipe
            }
            return
        }

        // 同列
        if (selectedItems.contains(path)) {
            selectedItems.remove(path)
            if (selectedItems.isEmpty()) {
                isSelectionMode = false
                selectionAnchor = null
                lastSelectWasSwipe = false
            } else {
                selectionAnchor = path
                lastSelectWasSwipe = isSwipe
            }
            return
        }

        if (isSwipe && lastSelectWasSwipe) {
            val anchor = selectionAnchor
            if (anchor != null && anchor != path) {
                val anchorIdx = paneFiles.indexOfFirst { it.relativePath == anchor || it.filePath == anchor }
                val currentIdx = paneFiles.indexOfFirst { it.relativePath == path || it.filePath == path }
                if (anchorIdx >= 0 && currentIdx >= 0) {
                    val range = if (anchorIdx < currentIdx)
                        anchorIdx..currentIdx else currentIdx..anchorIdx
                    for (i in range) {
                        val p = paneFiles[i].relativePath.ifEmpty { paneFiles[i].filePath }
                        if (p.isNotBlank()) selectedItems.add(p)
                    }
                } else {
                    selectedItems.add(path)
                }
            } else {
                selectedItems.add(path)
            }
            selectionAnchor = path
            lastSelectWasSwipe = true
        } else {
            selectedItems.add(path)
            selectionAnchor = path
            lastSelectWasSwipe = isSwipe
        }
    }

    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize()) {
        // 资源管理器顶部工具栏（面包屑 + 选择计数）
        if (openedFolderName != null) {
            val activePane = if (lastInteractedPane == 0) leftPane.value else rightPane.value
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
                if (isSelectionMode && selectedItems.isNotEmpty()) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "${selectedItems.size} 已选",
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
                                selectedItems.clear()
                                selectionAnchor = null
                                isSelectionMode = false
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
                leftPane.value == null -> {
                    Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                        Text("加载中…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                else -> {
                    Row(modifier = Modifier.fillMaxSize()) {
                        // 左列（始终显示）
                        val lp = leftPane.value!!
                        FileListPaneContent(
                            files = lp.files,
                            paneIndex = 0,
                            selectedItems = selectedItems,
                            isSelectionMode = isSelectionMode,
                            activePaneIndex = activePaneIndex,
                            currentPath = lp.path,
                            archivePath = lp.archivePath,
                            onNavigateUp = {
                                val parent = java.io.File(lp.path).parent ?: ""
                                val parentName = java.io.File(parent).name
                                onListChildren(parent) { children ->
                                    leftPane.value = Pane(parent, parentName, children)
                                    pushLeftHistory(leftPane.value!!)
                                }
                            },
                            onInteracted = { lastInteractedPane = 0 },
                            onFileClick = { file ->
                                if (isSelectionMode) {
                                    val path = file.relativePath.ifEmpty { file.filePath }
                                    toggleSelect(path, 0, lp.files, false)
                                } else {
                                    navigateLeft(file)
                                }
                            },
                            onFileLongClick = { file ->
                                val path = file.relativePath.ifEmpty { file.filePath }
                                toggleSelect(path, 0, lp.files, true)
                            },
                            onAddToConversation = onAddToConversation,
                            onRename = { renameTarget = it },
                            onDelete = onDelete,
                            onCreateFile = { createTarget = it; createIsDir = false },
                            onCreateDirectory = { createTarget = it; createIsDir = true },
                            onCopyToLeft = null,
                            onCopyToRight = rightPane.value?.let { rp -> { path -> onCopy(path, rp.path) } },
                            onMoveToLeft = null,
                            onMoveToRight = rightPane.value?.let { rp -> { path -> onMove(path, rp.path) } },
                            onExtractToLeft = null,
                            onExtractToRight = if (lp.archivePath != null && rightPane.value != null) {
                                { paths -> extractFiles(lp.archivePath, paths, rightPane.value!!.path) }
                            } else null,
                            onViewInfo = { infoTarget = it },
                            onCompress = { paths -> compressTargetPaths = paths },
                            onOpenAsProject = onOpenAsProject,
                            projectDirPath = projectDirPath,
                            modifier = Modifier.weight(1f),
                        )
                        // 右列（始终显示）
                        if (rightPane.value != null) {
                            VerticalDivider(
                                modifier = Modifier.fillMaxHeight(),
                                thickness = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            )
                            val rp = rightPane.value!!
                            FileListPaneContent(
                                files = rp.files,
                                paneIndex = 1,
                                selectedItems = selectedItems,
                                isSelectionMode = isSelectionMode,
                                activePaneIndex = activePaneIndex,
                                currentPath = rp.path,
                                archivePath = rp.archivePath,
                                onNavigateUp = {
                                    val parent = java.io.File(rp.path).parent ?: ""
                                    val parentName = java.io.File(parent).name
                                    onListChildren(parent) { children ->
                                        rightPane.value = Pane(parent, parentName, children)
                                        pushRightHistory(rightPane.value!!)
                                    }
                                },
                                onInteracted = { lastInteractedPane = 1 },
                                onFileClick = { file ->
                                    if (isSelectionMode) {
                                        val path = file.relativePath.ifEmpty { file.filePath }
                                        toggleSelect(path, 1, rp.files, false)
                                    } else {
                                        navigateRight(file)
                                    }
                                },
                                onFileLongClick = { file ->
                                    val path = file.relativePath.ifEmpty { file.filePath }
                                    toggleSelect(path, 1, rp.files, true)
                                },
                                onAddToConversation = onAddToConversation,
                                onRename = { renameTarget = it },
                                onDelete = onDelete,
                                onCreateFile = { createTarget = it; createIsDir = false },
                                onCreateDirectory = { createTarget = it; createIsDir = true },
                                onCopyToLeft = { path -> onCopy(path, lp.path) },
                                onCopyToRight = null,
                                onMoveToLeft = { path -> onMove(path, lp.path) },
                                onMoveToRight = null,
                                onExtractToLeft = if (rp.archivePath != null) {
                                    { paths -> extractFiles(rp.archivePath, paths, lp.path) }
                                } else null,
                                onExtractToRight = null,
                                onViewInfo = { infoTarget = it },
                                onCompress = { paths -> compressTargetPaths = paths },
                                onOpenAsProject = onOpenAsProject,
                                projectDirPath = projectDirPath,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }

        // === 底部工具栏 ===
        if (openedFolderName != null && leftPane.value != null) {
            HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // 活跃列的数据
            val activePane = if (lastInteractedPane == 0) leftPane.value else rightPane.value
            val activePaneFiles = activePane?.files ?: emptyList()
            val activePanePaths = activePaneFiles.map { it.relativePath.ifEmpty { it.filePath } }
            val activeHistoryIdx = if (lastInteractedPane == 0) leftHistoryIdx else rightHistoryIdx
            val activeHistory = if (lastInteractedPane == 0) leftHistory else rightHistory

            val allPaneFiles = (leftPane.value?.files ?: emptyList()) + (rightPane.value?.files ?: emptyList())
            val selectedFiles = allPaneFiles.filter { it.relativePath.ifEmpty { it.filePath } in selectedItems }
            val hasDirectory = selectedFiles.any { it.isDirectory }
            val canDiff = selectedItems.size == 2 && selectedFiles.size == 2 &&
                !hasDirectory && selectedFiles.all { it.size < 5 * 1024 * 1024 && isTextFile(it.name) }
            val totalSelectedSize = selectedFiles.sumOf { it.size }
            val canMerge = selectedItems.size == 2 && selectedFiles.size == 2 &&
                !hasDirectory && totalSelectedSize < 5 * 1024 * 1024 && selectedFiles.all { isTextFile(it.name) }

            if (isSelectionMode && selectedItems.isNotEmpty()) {
                // === 批量操作模式 ===
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 全选
                    TextButton(onClick = {
                        activePanePaths.forEach { if (it.isNotBlank()) selectedItems.add(it) }
                    }) {
                        Text("全选", style = MaterialTheme.typography.labelSmall)
                    }
                    Spacer(Modifier.width(4.dp))
                    // 反选
                    TextButton(onClick = {
                        activePanePaths.forEach { path ->
                            if (path in selectedItems) selectedItems.remove(path)
                            else if (path.isNotBlank()) selectedItems.add(path)
                        }
                        if (selectedItems.isEmpty()) {
                            isSelectionMode = false
                            selectionAnchor = null
                        }
                    }) {
                        Text("反选", style = MaterialTheme.typography.labelSmall)
                    }
                    Spacer(Modifier.width(4.dp))
                    // 取消
                    TextButton(onClick = {
                        selectedItems.clear()
                        selectionAnchor = null
                        isSelectionMode = false
                    }) {
                        Text("取消", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                    }

                    Spacer(Modifier.weight(1f))

                    // Diff
                    TextButton(
                        onClick = {
                            val paths = selectedItems.toList()
                            if (paths.size == 2) {
                                onDiff(paths[0], paths[1])
                            }
                        },
                        enabled = canDiff,
                    ) {
                        Text(
                            "对比",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (canDiff) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    // Merge
                    TextButton(
                        onClick = {
                            val paths = selectedItems.toList()
                            if (paths.size == 2) {
                                onMerge(paths[0], paths[1])
                            }
                        },
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
                // === 普通导航模式 ===
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 1. 返回上级目录
                    IconButton(
                        onClick = {
                            val pane = activePane ?: return@IconButton
                            if (pane.path.isNotEmpty()) {
                                val parent = java.io.File(pane.path).parent ?: ""
                                val parentName = java.io.File(parent).name
                                onListChildren(parent) { children ->
                                    val newPane = Pane(parent, parentName, children)
                                    if (lastInteractedPane == 0) {
                                        leftPane.value = newPane
                                        pushLeftHistory(newPane)
                                    } else {
                                        rightPane.value = newPane
                                        pushRightHistory(newPane)
                                    }
                                }
                            }
                        },
                        enabled = activePane?.path?.isNotEmpty() == true,
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回上级",
                            tint = if (activePane?.path?.isNotEmpty() == true) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                        )
                    }

                    Spacer(Modifier.weight(1f))

                    // 2. 回到上次目录（前进）
                    IconButton(
                        onClick = {
                            if (activeHistoryIdx < activeHistory.size - 1) {
                                val nextIdx = activeHistoryIdx + 1
                                val entry = activeHistory[nextIdx]
                                onListChildren(entry.path) { children ->
                                    val newPane = Pane(entry.path, entry.displayName, children)
                                    if (lastInteractedPane == 0) {
                                        leftPane.value = newPane
                                        leftHistoryIdx = nextIdx
                                    } else {
                                        rightPane.value = newPane
                                        rightHistoryIdx = nextIdx
                                    }
                                }
                            }
                        },
                        enabled = activeHistoryIdx < activeHistory.size - 1,
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "前进",
                            tint = if (activeHistoryIdx < activeHistory.size - 1) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                        )
                    }

                    Spacer(Modifier.weight(1f))

                    // 3. 创建文件/文件夹
                    IconButton(
                        onClick = {
                            val pane = activePane!!
                            val node = ResourceNode(
                                uri = android.net.Uri.EMPTY,
                                name = pane.displayName,
                                relativePath = pane.path,
                                isDirectory = true,
                                depth = 0,
                                filePath = pane.path,
                            )
                            createTarget = node
                            createIsDir = false
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "新建",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }

                    Spacer(Modifier.weight(1f))

                    // 4. 同步双列目录（将非活跃列同步为活跃列路径）
                    IconButton(
                        onClick = {
                            val src = activePane!!
                            onListChildren(src.path) { children ->
                                val newPane = Pane(src.path, src.displayName, children)
                                if (lastInteractedPane == 0) {
                                    rightPane.value = newPane
                                } else {
                                    leftPane.value = newPane
                                    pushLeftHistory(newPane)
                                }
                            }
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Default.SyncAlt,
                            contentDescription = "同步双列",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Spacer(Modifier.weight(1f))

                    // 5. 回到根目录
                    IconButton(
                        onClick = {
                            onListChildren("") { children ->
                                val newPane = Pane("", openedFolderName, children)
                                if (lastInteractedPane == 0) {
                                    leftPane.value = newPane
                                    pushLeftHistory(newPane)
                                } else {
                                    rightPane.value = newPane
                                    pushRightHistory(newPane)
                                }
                            }
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowUpward,
                            contentDescription = "回到根目录",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }

    // 对话框
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

    compressTargetPaths?.let { paths ->
        CompressDialog(
            defaultName = "archive",
            onConfirm = { archiveName, format, level, password ->
                onCompress(paths, archiveName, format, level, password)
                compressTargetPaths = null
            },
            onDismiss = { compressTargetPaths = null },
        )
    }

    infoTarget?.let { file ->
        FileInfoDialog(file = file, onDismiss = { infoTarget = null })
    }
}

// ================================================================
// 单列文件列表
// ================================================================

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileListPaneContent(
    files: List<FileItem>,
    paneIndex: Int,
    selectedItems: List<String>,
    isSelectionMode: Boolean,
    activePaneIndex: Int,
    currentPath: String = "",
    archivePath: String? = null,
    onNavigateUp: (() -> Unit)? = null,
    onInteracted: () -> Unit,
    onFileClick: (FileItem) -> Unit,
    onFileLongClick: (FileItem) -> Unit,
    onAddToConversation: (FileItem) -> Unit,
    onRename: (ResourceNode) -> Unit,
    onDelete: (String) -> Unit,
    onCreateFile: (ResourceNode) -> Unit,
    onCreateDirectory: (ResourceNode) -> Unit,
    onCopyToLeft: ((String) -> Unit)?,
    onCopyToRight: ((String) -> Unit)?,
    onMoveToLeft: ((String) -> Unit)?,
    onMoveToRight: ((String) -> Unit)?,
    onExtractToLeft: ((List<String>) -> Unit)? = null,
    onExtractToRight: ((List<String>) -> Unit)? = null,
    onViewInfo: (FileItem) -> Unit,
    onCompress: (List<String>) -> Unit,
    onOpenAsProject: ((String) -> Unit)?,
    projectDirPath: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val showUp = onNavigateUp != null && currentPath.isNotEmpty()
    Column(
        modifier = modifier
            .fillMaxHeight(),
    ) {
        if (files.isEmpty() && !showUp) {
            Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                Text(
                    "空文件夹",
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
                                .combinedClickable(onClick = { onInteracted(); onNavigateUp() })
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
                if (files.isEmpty()) {
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
                    items(files, key = { it.relativePath.ifEmpty { it.name } }) { file ->
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
                                    onDragEnd = {
                                        if (kotlin.math.abs(offsetX) > 60f) {
                                            onInteracted()
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
                                onClick = { onInteracted(); onFileClick(file); Unit },
                                onLongClick = { onInteracted(); showMenu = true; Unit },
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
                        if (!file.isDirectory && file.size > 0) {
                            Text(
                                text = formatFileSize(file.size),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    val allSelectedPaths = selectedItems.toList()
                    val paneSelectedCount = files.count { selectedItems.contains(it.relativePath.ifEmpty { it.filePath }) }
                    val isMultiSelect = allSelectedPaths.size > 1

                    TreeContextMenu(
                        expanded = showMenu,
                        node = node,
                        selectedCount = paneSelectedCount,
                        selectedPaths = allSelectedPaths,
                        archivePath = archivePath,
                        onDismiss = { showMenu = false },
                        onAddToConversation = { onInteracted(); onAddToConversation(FileItem(node.name, node.uri, node.isDirectory, relativePath = node.relativePath, filePath = node.filePath)) },
                        onCreateFile = { onInteracted(); onCreateFile(node) },
                        onCreateDirectory = { onInteracted(); onCreateDirectory(node) },
                        onRename = { onInteracted(); onRename(node) },
                        onDelete = {
                            if (isMultiSelect) {
                                allSelectedPaths.forEach { onDelete(it) }
                            } else {
                                onDelete(node.relativePath)
                            }
                        },
                        onCopyToLeft = onCopyToLeft?.let { cb -> { onInteracted(); cb(path) } },
                        onCopyToRight = onCopyToRight?.let { cb -> { onInteracted(); cb(path) } },
                        onMoveToLeft = onMoveToLeft?.let { cb -> { onInteracted(); cb(path) } },
                        onMoveToRight = onMoveToRight?.let { cb -> { onInteracted(); cb(path) } },
                        onExtractToLeft = onExtractToLeft?.let { { paths -> onInteracted(); it(paths) } },
                        onExtractToRight = onExtractToRight?.let { { paths -> onInteracted(); it(paths) } },
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
                        onViewInfo = { onInteracted(); onViewInfo(file) },
                        onCompress = {
                            if (isMultiSelect) {
                                onCompress(allSelectedPaths)
                            } else {
                                onCompress(listOf(path))
                            }
                        },
                        onOpenAsProject = if (node.filePath.isNotEmpty()) {
                            { onInteracted(); onOpenAsProject?.invoke(node.filePath) }
                        } else {
                            { onInteracted(); onOpenAsProject?.invoke(node.relativePath) }
                        },
                    )
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
    val sdf = SimpleDateFormat("yy-MM-dd HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

private fun isTextFile(name: String): Boolean {
    val textExtensions = setOf(
        "kt", "kts", "java", "xml", "json", "yml", "yaml", "properties",
        "txt", "md", "gradle", "toml", "cfg", "conf", "ini",
        "html", "css", "js", "ts", "sql", "sh", "bat", "py", "c", "cpp", "h", "hpp", "rs", "go",
    )
    return textExtensions.any { name.endsWith(".$it", ignoreCase = true) }
}
