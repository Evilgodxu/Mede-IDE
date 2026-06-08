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

// 主屏幕 ViewModel - 文件浏览使用 FileManager（相对路径）
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
                HomeUiState(
                    isLoading = false,
                    themeMode = values[0] as? String ?: "system",
                    language = values[1] as? String ?: "system",
                    rules = (values[2] as? List<Rule>) ?: emptyList(),
                    skills = (values[3] as? List<SkillItem>) ?: emptyList(),
                    mcpServers = (values[4] as? List<McpServer>) ?: emptyList(),
                    openedFolderName = (values[5] as? FolderState)?.folderName,
                    openedFolderUri = (values[5] as? FolderState)?.folderUri?.toString(),
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

    fun renameFile(uri: Uri, newName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                android.provider.DocumentsContract.renameDocument(
                    getApplication<Application>().contentResolver, uri, newName
                )
                refreshRootFiles()
            } catch (_: Exception) {}
        }
    }

    fun deleteFile(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val relativePath = safUriToRelative(uri)
                if (relativePath.isNotEmpty()) {
                    val result = fileManager.deleteFile(relativePath)
                    if (!result.startsWith("Failed")) {
                        FileOperationEvents.notify(relativePath, "delete")
                    }
                } else {
                    android.provider.DocumentsContract.deleteDocument(
                        getApplication<Application>().contentResolver, uri
                    )
                }
                refreshRootFiles()
            } catch (_: Exception) {}
        }
    }

    fun createFile(parentUri: Uri, name: String, isDirectory: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val mimeType = if (isDirectory) android.provider.DocumentsContract.Document.MIME_TYPE_DIR
                    else "application/octet-stream"
                val created = android.provider.DocumentsContract.createDocument(
                    getApplication<Application>().contentResolver, parentUri, mimeType, name
                )
                if (created != null) {
                    val path = safUriToRelative(created)
                    if (path.isNotEmpty()) {
                        FileOperationEvents.notify(path, if (isDirectory) "create" else "create")
                    }
                }
                refreshRootFiles()
            } catch (_: Exception) {}
        }
    }

    private fun refreshRootFiles() {
        val nodes = fileManager.listFilesAsNodes("")
        _files.value = nodes.map { toFileItem(it) }
    }

    private fun toFileItem(node: com.template.jh.core.storage.FileNode): FileItem {
        return FileItem(
            name = node.name,
            uri = node.uri,
            isDirectory = node.isDirectory,
            relativePath = node.path,
            size = node.size,
        )
    }
}

private data class FolderState(
    val folderUri: Uri? = null,
    val folderName: String? = null,
)
