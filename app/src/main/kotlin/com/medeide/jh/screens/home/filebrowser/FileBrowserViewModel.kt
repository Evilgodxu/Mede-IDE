package com.medeide.jh.screens.home.filebrowser

import android.os.FileObserver
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.medeide.jh.core.data.logging.FileLogger
import com.medeide.jh.screens.home.filebrowser.logic.FileEntry
import com.medeide.jh.screens.home.filebrowser.logic.computeDisplayPath
import com.medeide.jh.screens.home.filebrowser.logic.listArchiveEntries
import com.medeide.jh.screens.home.filebrowser.logic.loadFiles
import com.medeide.jh.screens.home.landscape.workspace.model.isArchiveFile
import java.io.File

class FileBrowserStateHolder(
    private val rootPath: String,
) {
    var currentPath by mutableStateOf(rootPath)
        private set
    private var fileObserver: FileObserver? = null

    // ── 压缩包内浏览状态 ──
    var mountedArchive by mutableStateOf<File?>(null)
        private set
    var archiveInnerPath by mutableStateOf("")
        private set

    val isInArchiveMode: Boolean get() = mountedArchive != null

    /**
     * 开始监听当前目录的文件变更（创建/删除/移动/修改）
     */
    fun startWatching() {
        stopWatching()
        val path = currentPath
        if (path.isBlank() || !File(path).exists()) return
        FileLogger.d("FileBrowser", "startWatching $path")
        fileObserver = object : FileObserver(File(path), CREATE or DELETE or MOVED_FROM or MOVED_TO or CLOSE_WRITE) {
            override fun onEvent(event: Int, path: String?) {
                Handler(Looper.getMainLooper()).post { refresh() }
            }
        }.also { it.startWatching() }
    }

    fun stopWatching() {
        if (fileObserver != null) FileLogger.d("FileBrowser", "stopWatching")
        fileObserver?.stopWatching()
        fileObserver = null
    }

    // 前进历史栈
    private val backStack = mutableStateListOf<String>()
    var forwardPath by mutableStateOf<String?>(null)
        private set

    val canGoForward: Boolean get() = forwardPath != null
    val canGoUp: Boolean get() = currentPath != rootPath || isInArchiveMode

    // 选择状态
    var isSelectionMode by mutableStateOf(false)
        private set
    var selectedItems by mutableStateOf<Set<String>>(emptySet())
        private set
    private var selectionAnchor by mutableStateOf<String?>(null)
    private var lastSelectWasSwipe by mutableStateOf(false)

    // 菜单/对话框
    var menuTarget by mutableStateOf<FileEntry?>(null)
        private set
    var showCreateDialog by mutableStateOf(false)
        private set
    var showRenameDialog by mutableStateOf<FileEntry?>(null)
        private set
    var showInfoDialog by mutableStateOf<FileEntry?>(null)
        private set
    var showDeleteDialog by mutableStateOf<List<FileEntry>?>(null)
        private set

    // 计算属性
    val files: List<FileEntry> get() {
        val archive = mountedArchive
        if (archive != null) {
            return listArchiveEntries(archive, archiveInnerPath)
        }
        return loadFiles(currentPath)
    }
    val displayPath: String get() {
        val archive = mountedArchive
        if (archive != null) {
            val name = archive.name
            return if (archiveInnerPath.isEmpty()) "/$name/" else "/$name/$archiveInnerPath/"
        }
        return computeDisplayPath(currentPath, rootPath)
    }
    fun navigateTo(path: String) {
        if (path == currentPath) return
        val targetFile = File(path)
        FileLogger.i("FileBrowser", "navigateTo $path")
        if (targetFile.exists() && !targetFile.isDirectory && isArchiveFile(targetFile.name)) {
            enterArchive(targetFile)
            return
        }
        backStack.add(currentPath)
        currentPath = path
        forwardPath = null
        refresh()
    }

    private fun enterArchive(archiveFile: File) {
        FileLogger.i("FileBrowser", "enterArchive ${archiveFile.name}")
        backStack.add(currentPath)
        mountedArchive = archiveFile
        archiveInnerPath = ""
        currentPath = archiveFile.absolutePath
        forwardPath = null
        stopWatching()
    }

    fun exitArchive() {
        FileLogger.i("FileBrowser", "exitArchive")
        mountedArchive = null
        archiveInnerPath = ""
        startWatching()
    }

    fun navigateArchiveTo(dirName: String) {
        val archive = mountedArchive ?: return
        val newInnerPath = if (archiveInnerPath.isEmpty()) dirName else "$archiveInnerPath/$dirName"
        forwardPath = currentPath
        archiveInnerPath = newInnerPath
        refresh()
    }

    fun navigateUp() {
        FileLogger.d("FileBrowser", "navigateUp from $currentPath")
        val archive = mountedArchive
        if (archive != null) {
            if (archiveInnerPath.isNotEmpty()) {
                forwardPath = currentPath
                val parts = archiveInnerPath.split("/")
                archiveInnerPath = parts.dropLast(1).joinToString("/")
            } else {
                forwardPath = currentPath
                exitArchive()
                currentPath = archive.parentFile?.absolutePath ?: rootPath
            }
            startWatching()
            return
        }
        val parent = File(currentPath).parentFile?.absolutePath ?: return
        forwardPath = currentPath
        currentPath = parent
        startWatching()
    }

    fun navigateForward() {
        val fwd = forwardPath ?: return
        FileLogger.d("FileBrowser", "navigateForward $fwd")
        forwardPath = null
        mountedArchive = null
        archiveInnerPath = ""
        currentPath = fwd
        startWatching()
    }

    fun refresh() {
        val p = currentPath
        currentPath = ""
        currentPath = p
    }

    // ── 选择操作（含范围选择，与旧版 ResourcePanel 一致）──
    fun enterSelectionMode(file: FileEntry) {
        isSelectionMode = true
        selectionAnchor = file.absolutePath
        selectedItems = setOf(file.absolutePath)
        lastSelectWasSwipe = false
    }

    fun toggleSelect(file: FileEntry, isSwipe: Boolean = false) {
        val path = file.absolutePath

        if (!isSelectionMode) {
            isSelectionMode = true
            selectionAnchor = path
            selectedItems = setOf(path)
            lastSelectWasSwipe = isSwipe
            return
        }

        if (path in selectedItems) {
            selectedItems = selectedItems - path
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

        // 添加选中
        if (isSwipe && lastSelectWasSwipe) {
            val anchor = selectionAnchor
            if (anchor != null && anchor != path) {
                val anchorIdx = files.indexOfFirst { it.absolutePath == anchor }
                val curIdx = files.indexOfFirst { it.absolutePath == path }
                if (anchorIdx >= 0 && curIdx >= 0) {
                    val range = if (anchorIdx < curIdx) anchorIdx..curIdx else curIdx..anchorIdx
                    val newSet = mutableSetOf<String>()
                    for (i in range) newSet.add(files[i].absolutePath)
                    selectedItems = newSet
                } else {
                    selectedItems = selectedItems + path
                }
            } else {
                selectedItems = selectedItems + path
            }
            selectionAnchor = path
            lastSelectWasSwipe = true
        } else {
            selectedItems = selectedItems + path
            selectionAnchor = path
            lastSelectWasSwipe = isSwipe
        }
    }

    fun selectAll(allPaths: List<String>) {
        selectedItems = allPaths.toSet()
    }

    fun invertSelection(allPaths: List<String>) {
        selectedItems = allPaths.filter { it !in selectedItems }.toSet()
    }

    fun cancelSelection() {
        isSelectionMode = false
        selectedItems = emptySet()
        selectionAnchor = null
        lastSelectWasSwipe = false
    }

    // 对话框操作
    fun showMenu(file: FileEntry) { menuTarget = file }
    fun dismissMenu() { menuTarget = null }
    fun requestCreate() { showCreateDialog = true }
    fun requestRename(file: FileEntry) { showRenameDialog = file }
    fun requestInfo(file: FileEntry) { showInfoDialog = file }
    fun requestDelete(files: List<FileEntry>) { showDeleteDialog = files }
    fun dismissCreate() { showCreateDialog = false }
    fun dismissRename() { showRenameDialog = null }
    fun dismissInfo() { showInfoDialog = null }
    fun dismissDelete() { showDeleteDialog = null }

    fun snapshot(): FileBrowserState = FileBrowserState(
        currentPath = currentPath,
        files = files,
        displayPath = displayPath,
        canGoForward = canGoForward,
        canGoUp = canGoUp,
        isSelectionMode = isSelectionMode,
        selectedItems = selectedItems,
        menuTarget = menuTarget,
        showCreateDialog = showCreateDialog,
        showRenameDialog = showRenameDialog,
        showInfoDialog = showInfoDialog,
        showDeleteDialog = showDeleteDialog,
    )
}

@Composable
fun rememberFileBrowserStateHolder(rootPath: String): FileBrowserStateHolder {
    return remember(rootPath) { FileBrowserStateHolder(rootPath) }
}
