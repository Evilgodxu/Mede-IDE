@file:Suppress("UNCHECKED_CAST")

package com.template.jh.screens.home

import android.app.Application
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.template.jh.data.model.McpServer
import com.template.jh.data.model.NotificationSettings
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

// 主屏幕 ViewModel
class HomeViewModel(
    application: Application,
    private val userPreferencesRepository: UserPreferencesRepository,
) : AndroidViewModel(application) {
    // 文件夹状态
    private val _folderState = MutableStateFlow(FolderState())

    private val _state = MutableStateFlow(HomeUiState(isLoading = true))
    val state: StateFlow<HomeUiState> = _state

    init {
        viewModelScope.launch {
            combine(
                userPreferencesRepository.themeMode,
                userPreferencesRepository.language,
                userPreferencesRepository.modelName,
                userPreferencesRepository.userName,
                userPreferencesRepository.rules,
                userPreferencesRepository.skills,
                userPreferencesRepository.mcpServers,
                userPreferencesRepository.notificationSettings,
                _folderState,
            ) { values: Array<Any?> ->
                HomeUiState(
                    isLoading = false,
                    themeMode = values[0] as? String ?: "system",
                    language = values[1] as? String ?: "system",
                    modelName = values[2] as? String ?: "",
                    userName = values[3] as? String ?: "",
                    rules = (values[4] as? List<Rule>) ?: emptyList(),
                    skills = (values[5] as? List<SkillItem>) ?: emptyList(),
                    mcpServers = (values[6] as? List<McpServer>) ?: emptyList(),
                    notificationSettings = (values[7] as? NotificationSettings) ?: NotificationSettings(),
                    openedFolderName = (values[8] as? FolderState)?.folderName,
                    openedFolderUri = (values[8] as? FolderState)?.folderUri?.toString(),
                )
            }.collect { _state.value = it }
        }
    }

    // 资源管理器文件列表
    private val _files = MutableStateFlow<List<FileItem>>(emptyList())
    val files: StateFlow<List<FileItem>> = _files

    // 从 SAF URI 打开文件夹并列出文件
    fun openFolder(uri: Uri) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    // 持久化读写权限
                    val takeFlags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    getApplication<Application>().contentResolver.takePersistableUriPermission(uri, takeFlags)

                    val docFile = DocumentFile.fromTreeUri(getApplication(), uri)
                        ?: return@withContext

                    val folderName = docFile.name ?: "未命名文件夹"
                    _folderState.value = FolderState(
                        folderUri = uri,
                        folderName = folderName,
                    )

                    // 通过 DocumentsContract 查询（兼容性优于 DocumentFile.listFiles）
                    val items = queryChildren(uri)
                    _files.value = items
                } catch (e: Exception) {
                    _folderState.value = FolderState()
                    _files.value = emptyList()
                }
            }
        }
    }

    // 懒加载子目录（通过 DocumentsContract 查询，兼容各厂商 ROM）
    fun listChildren(parentUri: Uri, onResult: (List<FileItem>) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val items = queryChildren(parentUri)
                withContext(Dispatchers.Main) { onResult(items) }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) { onResult(emptyList()) }
            }
        }
    }

    // 通过 DocumentFile 查询子文件（参照 AmazeFileManager: fromTreeUri → findFile → listFiles）
    private fun queryChildren(parentUri: Uri): List<FileItem> {
        val treeUri = _folderState.value.folderUri ?: return emptyList()
        val rootDocFile = DocumentFile.fromTreeUri(getApplication(), treeUri)
            ?: return emptyList()

        // 根目录直接 listFiles
        if (parentUri == treeUri) {
            val children = rootDocFile.listFiles() ?: return emptyList()
            return sortDocFiles(children).map { toFileItem(it) }
        }

        // 子目录：获取相对路径，从根逐级 findFile
        val parentDocId = try { DocumentsContract.getDocumentId(parentUri) }
            catch (_: Exception) { null } ?: return emptyList()
        val rootDocId = try { DocumentsContract.getTreeDocumentId(treeUri) }
            catch (_: Exception) { null } ?: return emptyList()

        val relativePath = parentDocId.removePrefix(rootDocId).trimStart('/')
        if (relativePath.isEmpty()) {
            val children = rootDocFile.listFiles() ?: return emptyList()
            return sortDocFiles(children).map { toFileItem(it) }
        }

        var current = rootDocFile
        for (segment in relativePath.split('/')) {
            if (segment.isEmpty()) continue
            current = current.findFile(segment) ?: return emptyList()
        }

        val children = current.listFiles() ?: return emptyList()
        return sortDocFiles(children).map { toFileItem(it) }
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

    // 关闭文件夹
    fun closeFolder() {
        _folderState.value = FolderState()
        _files.value = emptyList()
    }

    // 重命名文件/目录
    fun renameFile(uri: Uri, newName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                DocumentsContract.renameDocument(
                    getApplication<Application>().contentResolver, uri, newName
                )
                refreshRootFiles()
            } catch (_: Exception) {}
        }
    }

    // 删除文件/目录
    fun deleteFile(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                DocumentsContract.deleteDocument(
                    getApplication<Application>().contentResolver, uri
                )
                refreshRootFiles()
            } catch (_: Exception) {}
        }
    }

    // 在指定目录下创建文件或目录
    fun createFile(parentUri: Uri, name: String, isDirectory: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val mimeType = if (isDirectory) DocumentsContract.Document.MIME_TYPE_DIR
                    else "application/octet-stream"
                DocumentsContract.createDocument(
                    getApplication<Application>().contentResolver, parentUri, mimeType, name
                )
                refreshRootFiles()
            } catch (_: Exception) {}
        }
    }

    private fun refreshRootFiles() {
        val treeUri = _folderState.value.folderUri ?: return
        try {
            val items = queryChildren(treeUri)
            _files.value = items
        } catch (_: Exception) {}
    }

    fun setThemeMode(mode: String) {
        viewModelScope.launch {
            userPreferencesRepository.setThemeMode(mode)
        }
    }

    fun setLanguage(language: String) {
        viewModelScope.launch {
            userPreferencesRepository.setLanguage(language)
        }
    }

    fun setModelName(name: String) {
        viewModelScope.launch {
            userPreferencesRepository.setModelName(name)
        }
    }

    fun setUserName(name: String) {
        viewModelScope.launch {
            userPreferencesRepository.setUserName(name)
        }
    }

    fun setRules(rules: List<Rule>) {
        viewModelScope.launch {
            userPreferencesRepository.setRules(rules)
        }
    }

    fun setSkills(skills: List<SkillItem>) {
        viewModelScope.launch {
            userPreferencesRepository.setSkills(skills)
        }
    }

    fun setMcpServers(servers: List<McpServer>) {
        viewModelScope.launch {
            userPreferencesRepository.setMcpServers(servers)
        }
    }

    fun setNotificationSettings(settings: NotificationSettings) {
        viewModelScope.launch {
            userPreferencesRepository.setNotificationSettings(settings)
        }
    }

    private fun sortDocFiles(files: Array<DocumentFile>): Array<DocumentFile> {
        try {
            files.sortWith(Comparator { a, b ->
                val aDir = a.isDirectory
                val bDir = b.isDirectory
                if (aDir != bDir) {
                    if (aDir) -1 else 1
                } else {
                    (a.name ?: "").lowercase().compareTo((b.name ?: "").lowercase())
                }
            })
        } catch (_: Exception) {}
        return files
    }

    private fun toFileItem(doc: DocumentFile): FileItem {
        return FileItem(
            name = doc.name ?: "未知",
            uri = doc.uri,
            isDirectory = doc.isDirectory,
            size = doc.length(),
            lastModified = doc.lastModified(),
        )
    }
}

private data class FolderState(
    val folderUri: Uri? = null,
    val folderName: String? = null,
)
