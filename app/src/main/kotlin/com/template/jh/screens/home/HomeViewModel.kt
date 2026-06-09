@file:Suppress("UNCHECKED_CAST")

package com.template.jh.screens.home

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.template.jh.core.ai.FileOperationEvents
import com.template.jh.core.storage.FileManager
import com.template.jh.data.model.McpServer
import com.template.jh.data.model.Rule
import com.template.jh.data.model.SkillItem
import com.template.jh.data.repository.UserPreferencesRepository
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
                userPreferencesRepository.skills,
                userPreferencesRepository.mcpServers,
                _folderState,
            ) { values: Array<Any?> ->
                val fs = values[5] as? FolderState ?: FolderState()
                HomeUiState(
                    isLoading = false,
                    themeMode = values[0] as? String ?: "system",
                    language = values[1] as? String ?: "system",
                    rules = (values[2] as? List<Rule>) ?: emptyList(),
                    skills = (values[3] as? List<SkillItem>) ?: emptyList(),
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

    /** 以项目目录打开：将目录设为项目根，资源管理器聚焦到此目录 */
    fun openAsProjectDirectory(absolutePath: String): Boolean {
        val dir = File(absolutePath)
        if (!dir.isDirectory) return false
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

    /** SAF 模式 - 通过系统文件夹选择器 URI 打开文件夹 */
    fun openFolder(uri: Uri) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    fileManager.setProjectUri(uri)
                    val docFile = androidx.documentfile.provider.DocumentFile.fromTreeUri(getApplication(), uri)
                    val folderName = docFile?.name ?: "未命名文件夹"
                    _folderState.value = FolderState(folderUri = uri, folderName = folderName)
                    refreshRootFiles()
                } catch (_: Exception) {
                    _folderState.value = FolderState()
                    _files.value = emptyList()
                }
            }
        }
    }

    /** 当前是否为直接文件系统访问模式 */
    fun isDirectAccessMode(): Boolean = fileManager.isDirectAccessMode()

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

    private fun safUriToRelative(uri: Uri): String {
        val rootUri = _folderState.value.folderUri ?: return ""
        val rootDocId = try {
            android.provider.DocumentsContract.getTreeDocumentId(rootUri)
        } catch (_: Exception) { null } ?: return ""
        val fileDocId = try {
            android.provider.DocumentsContract.getDocumentId(uri)
        } catch (_: Exception) { null } ?: return ""
        return fileDocId.removePrefix(rootDocId.trimEnd('/') + "/")
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

    fun closeFolder() {
        fileManager.clearProjectUri()
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

    fun setSkills(skills: List<SkillItem>) {
        viewModelScope.launch { userPreferencesRepository.setSkills(skills) }
    }

    fun setMcpServers(servers: List<McpServer>) {
        viewModelScope.launch { userPreferencesRepository.setMcpServers(servers) }
    }

    fun renameFile(relativePath: String, newName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (isDirectAccessMode()) {
                    val base = fileManager.projectDirPath.ifEmpty { fileManager.storageRootPath }
                    val file = java.io.File(base, relativePath)
                    file.renameTo(java.io.File(file.parentFile, newName))
                } else {
                    val nodes = fileManager.listFilesAsNodes(
                        relativePath.substringBeforeLast('/')
                    )
                    val node = nodes.find { it.name == relativePath.substringAfterLast('/') }
                    if (node != null) {
                        android.provider.DocumentsContract.renameDocument(
                            getApplication<Application>().contentResolver, node.uri, newName
                        )
                    }
                }
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
