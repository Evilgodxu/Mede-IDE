@file:Suppress("UNCHECKED_CAST")

package com.medeide.jh.screens.home

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.medeide.jh.screens.home.ai.FileOperationEvents
import com.medeide.jh.data.storage.FileManager
import com.medeide.jh.model.DEFAULT_ROLE_ID
import com.medeide.jh.model.FileItem
import com.medeide.jh.model.McpServer
import com.medeide.jh.model.Rule
import com.medeide.jh.data.repository.RecentEntry
import com.medeide.jh.data.repository.UserPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// 主屏幕 ViewModel - 文件浏览使用 FileManager（双模式）
class HomeViewModel(
    application: Application,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val fileManager: FileManager,
) : AndroidViewModel(application) {
    private val _folderState = MutableStateFlow(FolderState())
    private val _state = MutableStateFlow(HomeUiState(isLoading = true))
    val state: StateFlow<HomeUiState> = _state

    init {
        viewModelScope.launch {
            combine(
                userPreferencesRepository.themeMode,
                userPreferencesRepository.language,
                userPreferencesRepository.rules,
                userPreferencesRepository.activeRoleId,
                userPreferencesRepository.mcpServers,
                _folderState,
            ) { values: Array<Any?> ->
                val fs = values[5] as? FolderState ?: FolderState()
                HomeUiState(
                    isLoading = false,
                    themeMode = values[0] as? String ?: "system",
                    language = values[1] as? String ?: "system",
                    rules = (values[2] as? List<Rule>) ?: emptyList(),
                    activeRoleId = (values[3] as? String) ?: DEFAULT_ROLE_ID,
                    mcpServers = (values[4] as? List<McpServer>) ?: emptyList(),
                    openedFolderName = fs.folderName,
                    openedFolderUri = fs.folderUri?.toString(),
                    storageRootPath = fs.storageRootPath,
                    projectDirName = fs.projectDirName,
                    projectDirPath = fs.projectDirPath,
                )
            }.collect { _state.value = it }
        }
        viewModelScope.launch {
            FileOperationEvents.events.collect { event ->
                if (event.operation in listOf("create", "overwrite", "delete", "modify")) {
                    refreshRootFiles()
                }
            }
        }
    }

    private val _files = MutableStateFlow<List<FileItem>>(emptyList())
    val files: StateFlow<List<FileItem>> = _files

    /** 打开存储根目录（文件管理器模式） */
    fun openDirectStorage() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val root = android.os.Environment.getExternalStorageDirectory()
                    fileManager.setDirectStorageRoot(root)
                    _folderState.value = FolderState(
                        folderName = "存储根目录",
                        isDirectAccess = true,
                        storageRootPath = root.absolutePath,
                        projectDirPath = root.absolutePath,
                        projectDirName = null,
                    )
                    refreshRootFiles()
                } catch (_: Exception) {
                    _folderState.value = FolderState()
                    _files.value = emptyList()
                }
            }
        }
    }

    /** 以工作目录打开：将目录设为项目根，双列文件管理器聚焦到此目录 */
    fun openAsProjectDirectory(absolutePath: String): Boolean {
        val dir = File(absolutePath)
        if (!dir.isDirectory) return false
        recordRecentFolder(absolutePath, dir.name)
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                fileManager.setProjectDir(absolutePath)
                _folderState.value = _folderState.value.copy(
                    folderName = dir.name,
                    projectDirName = dir.name,
                    projectDirPath = dir.absolutePath,
                )
                refreshRootFiles()
            }
        }
        return true
    }

    /** 回到存储根目录（文件管理器根视图） */
    fun navigateToStorageRoot() {
        val rootPath = _folderState.value.storageRootPath
        if (rootPath.isBlank()) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                fileManager.setProjectDir(rootPath)
                _folderState.value = _folderState.value.copy(
                    folderName = "存储根目录",
                    projectDirName = null,
                    projectDirPath = rootPath,
                )
                refreshRootFiles()
            }
        }
    }

    /** 获取相对路径的完整绝对路径 */
    fun getFullPath(relativePath: String): String = fileManager.getAbsolutePath(relativePath)

    /** 获取存储根路径 */
    fun getStorageRootPath(): String = fileManager.storageRootPath

    /** 获取项目目录路径 */
    fun getProjectDirPath(): String = fileManager.projectDirPath

    /** 设置项目根目录（使用直接文件系统路径） */
    fun setProjectRootPath(path: String, displayName: String) {
        fileManager.setProjectDir(path)
        _folderState.value = FolderState(folderName = displayName)
        refreshRootFiles()
    }

    /** 相对路径是否在项目目录范围内 */
    fun isProjectFile(relativePath: String): Boolean {
        val base = fileManager.projectDirPath.ifEmpty { fileManager.storageRootPath }
        if (base.isEmpty()) return false
        val full = File(base, relativePath.trim('/')).absolutePath
        return full.startsWith(base)
    }

    fun listChildren(parentRelativePath: String, onResult: (List<FileItem>) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val nodes = fileManager.listFilesAsNodes(parentRelativePath)
                val items = nodes.map { toFileItem(it) }
                withContext(Dispatchers.Main) { onResult(items) }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) { onResult(emptyList()) }
            }
        }
    }

    val lastOpenedFolderUri: StateFlow<String?> = userPreferencesRepository.lastOpenedFolderUri
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val openedFileTabs: StateFlow<List<String>> = userPreferencesRepository.openedFileTabs
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun saveLastOpenedFolder(uri: String) {
        viewModelScope.launch { userPreferencesRepository.setLastOpenedFolderUri(uri) }
    }

    fun saveOpenedTabs(paths: List<String>) {
        viewModelScope.launch { userPreferencesRepository.setOpenedFileTabs(paths) }
    }

    val recentFiles: StateFlow<List<RecentEntry>> = userPreferencesRepository.recentFiles
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val recentFolders: StateFlow<List<RecentEntry>> = userPreferencesRepository.recentFolders
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // 合并最近打开的文件和目录，按时间倒序，最多 20 条
    val allRecent: StateFlow<List<RecentEntry>> = combine(
        userPreferencesRepository.recentFiles,
        userPreferencesRepository.recentFolders,
    ) { files, folders ->
        (files + folders)
            .sortedByDescending { it.timestamp }
            .take(20)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun recordRecentFile(path: String, name: String) {
        viewModelScope.launch { userPreferencesRepository.addRecentFile(path, name) }
    }

    fun recordRecentFolder(path: String, name: String) {
        viewModelScope.launch { userPreferencesRepository.addRecentFolder(path, name) }
    }

    /** 在项目中搜索文件内容 */
    fun searchInFiles(
        pattern: String,
        extension: String = "",
        ignoreCase: Boolean = true,
        maxResults: Int = 100,
    ): List<FileManager.SearchMatch> {
        return fileManager.grepStructured(pattern, extension, ignoreCase = ignoreCase, maxResults = maxResults)
    }

    /** 在项目中搜索并替换 */
    fun replaceInFiles(pattern: String, replacement: String, extension: String = "", ignoreCase: Boolean = true): Int {
        return fileManager.replaceInFiles(pattern, replacement, extension, ignoreCase)
    }

    fun getAbsolutePath(relativePath: String): String = fileManager.getAbsolutePath(relativePath)

    fun closeFolder() {
        _folderState.value = FolderState()
        _files.value = emptyList()
    }

    fun setThemeMode(mode: String) {
        viewModelScope.launch { userPreferencesRepository.setThemeMode(mode) }
    }

    fun setLanguage(language: String) {
        viewModelScope.launch { userPreferencesRepository.setLanguage(language) }
    }

    fun setRules(rules: List<Rule>) {
        viewModelScope.launch { userPreferencesRepository.setRules(rules) }
    }

    fun setActiveRoleId(id: String) {
        viewModelScope.launch { userPreferencesRepository.setActiveRoleId(id) }
    }

    fun setMcpServers(servers: List<McpServer>) {
        viewModelScope.launch { userPreferencesRepository.setMcpServers(servers) }
    }

    fun renameFile(relativePath: String, newName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val base = fileManager.projectDirPath.ifEmpty { fileManager.storageRootPath }
                val file = java.io.File(base, relativePath)
                file.renameTo(java.io.File(file.parentFile, newName))
                refreshRootFiles()
            } catch (_: Exception) {}
        }
    }

    fun deleteFile(relativePath: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = fileManager.deleteFile(relativePath)
                if (!result.startsWith("Failed")) {
                    FileOperationEvents.notify(relativePath, "delete")
                }
                refreshRootFiles()
            } catch (_: Exception) {}
        }
    }

    fun createFile(parentRelativePath: String, name: String, isDirectory: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val targetPath = if (parentRelativePath.isBlank()) name else "$parentRelativePath/$name"
                val result = if (isDirectory) {
                    fileManager.createDirectory(targetPath)
                } else {
                    fileManager.writeFile(targetPath, "")
                }
                if (!result.startsWith("Failed")) {
                    FileOperationEvents.notify(targetPath, if (isDirectory) "create" else "create")
                }
                refreshRootFiles()
            } catch (_: Exception) {}
        }
    }

    fun copyFile(srcPath: String, dstDirPath: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val base = fileManager.projectDirPath.ifEmpty { fileManager.storageRootPath }
                val srcFile = java.io.File(base, srcPath)
                val dstDir = java.io.File(base, dstDirPath)
                val dstFile = java.io.File(dstDir, srcFile.name)
                if (!dstDir.exists()) dstDir.mkdirs()
                srcFile.copyRecursively(dstFile, overwrite = true)
                FileOperationEvents.notify(dstFile.path.removePrefix(base).trimStart('/'), "create")
                refreshRootFiles()
            } catch (_: Exception) {}
        }
    }

    fun moveFile(srcPath: String, dstDirPath: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val base = fileManager.projectDirPath.ifEmpty { fileManager.storageRootPath }
                val srcFile = java.io.File(base, srcPath)
                val dstDir = java.io.File(base, dstDirPath)
                val dstFile = java.io.File(dstDir, srcFile.name)
                if (!dstDir.exists()) dstDir.mkdirs()
                srcFile.renameTo(dstFile)
                FileOperationEvents.notify(srcPath, "delete")
                FileOperationEvents.notify(dstFile.path.removePrefix(base).trimStart('/'), "create")
                refreshRootFiles()
            } catch (_: Exception) {}
        }
    }

    fun compressFiles(
        paths: List<String>,
        archiveName: String,
        format: String,
        level: Int,
        password: String?,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val base = fileManager.projectDirPath.ifEmpty { fileManager.storageRootPath }
                val targetFile = java.io.File(base, archiveName)
                if (format == "zip") {
                    val zipFile = net.lingala.zip4j.ZipFile(targetFile)
                    val parameters = net.lingala.zip4j.model.ZipParameters()
                    parameters.compressionLevel = net.lingala.zip4j.model.enums.CompressionLevel.values()[level.coerceIn(0, 9)]
                    if (!password.isNullOrBlank()) {
                        zipFile.setPassword(password.toCharArray())
                        parameters.encryptionMethod = net.lingala.zip4j.model.enums.EncryptionMethod.ZIP_STANDARD
                    }
                    for (path in paths) {
                        val file = java.io.File(base, path)
                        if (file.isDirectory) {
                            zipFile.addFolder(file, parameters)
                        } else {
                            zipFile.addFile(file, parameters)
                        }
                    }
                    FileOperationEvents.notify(archiveName, "create")
                }
                refreshRootFiles()
            } catch (_: Exception) {}
        }
    }

    private fun refreshRootFiles() {
        val nodes = fileManager.listFilesAsNodes("")
        _files.value = nodes.map { toFileItem(it) }
    }

    private fun toFileItem(node: FileManager.FileNode): FileItem {
        return FileItem(
            name = node.name,
            uri = node.uri,
            isDirectory = node.isDirectory,
            relativePath = node.path,
            size = node.size,
            filePath = node.filePath,
        )
    }
}

private data class FolderState(
    val folderUri: Uri? = null,
    val folderName: String? = null,
    val isDirectAccess: Boolean = false,
    val storageRootPath: String = "",
    val projectDirPath: String = "",
    val projectDirName: String? = null,
)
