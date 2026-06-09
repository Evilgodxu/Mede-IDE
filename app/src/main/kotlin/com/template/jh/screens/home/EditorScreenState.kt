package com.template.jh.screens.home

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.TextFieldValue
import com.template.jh.core.ai.ChatViewModel
import com.template.jh.core.storage.FileManager

class EditorScreenState(
    val chatViewModel: ChatViewModel,
    val fileManager: FileManager,
) {
    var onSaveTabs: () -> Unit = {}
    val tabs = mutableStateListOf<TabItem>()
    var activeTabIndex by mutableIntStateOf(-1)
    val settingsTabId = "__settings__"

    val editorContent = mutableStateMapOf<String, TextFieldValue>()
    val originalContents = mutableStateMapOf<String, String>()

    /** 将路径转为相对路径（若为绝对路径则转换） */
    private fun toStoragePath(path: String): String {
        if (path.startsWith("/storage/") || path.startsWith("/data/")) {
            return fileManager.toRelativePath(path)
        }
        return path
    }

    fun openTab(tab: TabItem) {
        val idx = tabs.indexOfFirst { it.id == tab.id }
        if (idx >= 0) {
            activeTabIndex = idx
        } else {
            tabs.add(tab)
            activeTabIndex = tabs.size - 1
        }
        saveFileTabs()
    }

    fun openFileTab(path: String, displayName: String? = null) {
        openTab(TabItem(path, displayName ?: displayNameFromPath(path), TabType.File))
    }

    fun openSettingsTab(settingsTabTitle: String) {
        openTab(TabItem(settingsTabId, settingsTabTitle, TabType.Settings))
    }

    fun closeSettingsTab() {
        val idx = tabs.indexOfFirst { it.id == settingsTabId }
        if (idx >= 0) {
            tabs.removeAt(idx)
            activeTabIndex = when {
                tabs.isEmpty() -> -1
                activeTabIndex >= tabs.size -> tabs.size - 1
                else -> activeTabIndex.coerceIn(0, tabs.size - 1)
            }
        }
    }

    fun closeTab(idx: Int): Boolean {
        val tab = tabs.getOrNull(idx) ?: return false
        if (tab.type == TabType.File && isFileModified(tab.id)) return false
        doRemoveTab(idx)
        return true
    }

    fun forceCloseTab(idx: Int) {
        val tab = tabs.getOrNull(idx) ?: return
        doRemoveTab(idx)
        editorContent.remove(tab.id)
        originalContents.remove(tab.id)
    }

    private fun doRemoveTab(idx: Int) {
        tabs.removeAt(idx)
        activeTabIndex = when {
            tabs.isEmpty() -> -1
            activeTabIndex >= tabs.size -> tabs.size - 1
            else -> activeTabIndex.coerceIn(0, tabs.size - 1)
        }
        saveFileTabs()
    }

    fun closeAllTabs() {
        tabs.clear()
        activeTabIndex = -1
    }

    fun getTabIdxById(id: String): Int = tabs.indexOfFirst { it.id == id }

    fun readFileFromSource(path: String): String {
        if (path.startsWith("content://")) {
            return runCatching {
                fileManager.contentResolver.openInputStream(Uri.parse(path))?.bufferedReader()?.readText()
            }.getOrDefault("") ?: ""
        }
        return fileManager.readFileRaw(toStoragePath(path)) ?: ""
    }

    fun isFileModified(path: String): Boolean {
        val current = editorContent[path]?.text ?: return false
        return current != readFileFromSource(path)
    }

    fun saveFile(path: String) {
        val content = editorContent[path]?.text ?: return
        if (path.startsWith("content://")) {
            runCatching {
                fileManager.contentResolver.openOutputStream(Uri.parse(path), "wt")?.use {
                    it.write(content.toByteArray(Charsets.UTF_8))
                }
            }
        } else {
            fileManager.writeFile(toStoragePath(path), content)
        }
        originalContents[path] = content
    }

    fun saveFileTabs() { onSaveTabs() }

    fun displayNameFromPath(path: String): String = com.template.jh.screens.home.displayNameFromPath(path)

    fun handleTextChange(path: String, newTextFieldValue: TextFieldValue) {
        editorContent[path] = newTextFieldValue
    }
}

@Composable
fun rememberEditorScreenState(
    chatViewModel: ChatViewModel,
    fileManager: FileManager,
): EditorScreenState {
    return remember {
        EditorScreenState(chatViewModel = chatViewModel, fileManager = fileManager)
    }
}
