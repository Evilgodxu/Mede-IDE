package com.template.jh.screens.home

import android.app.Application
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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

    val state: StateFlow<HomeUiState> = combine(
        userPreferencesRepository.themeMode,
        userPreferencesRepository.language,
        _folderState,
    ) { themeMode, language, folder ->
        HomeUiState(
            isLoading = false,
            themeMode = themeMode,
            language = language,
            openedFolderName = folder.folderName,
            openedFolderUri = folder.folderUri?.toString(),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState(isLoading = true),
    )

    // 资源管理器文件列表
    private val _files = MutableStateFlow<List<FileItem>>(emptyList())
    val files: StateFlow<List<FileItem>> = _files

    // 从 SAF URI 打开文件夹并列出文件
    fun openFolder(uri: Uri) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    // 持久化访问权限
                    getApplication<Application>().contentResolver.takePersistableUriPermission(
                        uri,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )

                    val docFile = DocumentFile.fromTreeUri(getApplication(), uri)
                        ?: return@withContext

                    val folderName = docFile.name ?: "未命名文件夹"
                    _folderState.value = FolderState(
                        folderUri = uri,
                        folderName = folderName,
                    )

                    val children = docFile.listFiles()
                    val items = sortDocFiles(children).map { child -> toFileItem(child) }
                    _files.value = items
                } catch (e: Exception) {
                    _folderState.value = FolderState()
                    _files.value = emptyList()
                }
            }
        }
    }

    // 懒加载子目录
    fun listChildren(parentUri: Uri, onResult: (List<FileItem>) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val docFile = DocumentFile.fromSingleUri(getApplication(), parentUri)
                    ?: return@launch

                val children = docFile.listFiles()
                val items = sortDocFiles(children).map { child -> toFileItem(child) }
                withContext(Dispatchers.Main) { onResult(items) }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) { onResult(emptyList()) }
            }
        }
    }

    // 关闭文件夹
    fun closeFolder() {
        _folderState.value = FolderState()
        _files.value = emptyList()
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

    private fun sortDocFiles(files: Array<DocumentFile>): Array<DocumentFile> {
        files.sortWith(Comparator { a, b ->
            val aDir = a.isDirectory
            val bDir = b.isDirectory
            if (aDir != bDir) {
                if (aDir) -1 else 1
            } else {
                (a.name ?: "").lowercase().compareTo((b.name ?: "").lowercase())
            }
        })
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
