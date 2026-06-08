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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import com.template.jh.core.ai.ChatViewModel
import com.template.jh.core.editor.CodeReviewState
import com.template.jh.core.editor.createCodeReviewState
import com.template.jh.core.storage.FileManager

// 编辑器屏幕状态管理器 - 通过 FileManager 读写文件
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
    val reviewStates = mutableStateMapOf<String, CodeReviewState>()
    val showReviewPanels = mutableStateMapOf<String, Boolean>()

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
        tabs.removeAt(idx)
        activeTabIndex = when {
            tabs.isEmpty() -> -1
            activeTabIndex >= tabs.size -> tabs.size - 1
            else -> activeTabIndex.coerceIn(0, tabs.size - 1)
        }
        saveFileTabs()
        return true
    }

    fun closeAllTabs() {
        tabs.clear()
        activeTabIndex = -1
    }

    fun getTabIdxById(id: String): Int = tabs.indexOfFirst { it.id == id }

    // ===== 文件操作 - 委托给 FileManager =====
    fun readFileFromSource(path: String): String {
        if (path.startsWith("content://")) {
            return runCatching {
                val context = fileManager.javaClass.getDeclaredField("context").apply { isAccessible = true }
                    ?.let { field -> field.get(fileManager) as? android.content.Context }
                if (context != null) {
                    context.contentResolver.openInputStream(Uri.parse(path))?.bufferedReader()?.readText()
                } else null
            }.getOrDefault("无法读取文件") ?: "无法读取文件"
        }
        return fileManager.readFileRaw(path) ?: "无法读取文件"
    }

    fun isFileModified(path: String): Boolean {
        val current = editorContent[path]?.text ?: return false
        return current != readFileFromSource(path)
    }

    fun saveFile(path: String) {
        val content = editorContent[path]?.text ?: return
        if (!path.startsWith("content://")) {
            fileManager.writeFile(path, content)
        } else {
            runCatching {
                val context = fileManager.javaClass.getDeclaredField("context").apply { isAccessible = true }
                    ?.let { field -> field.get(fileManager) as? android.content.Context }
                if (context != null) {
                    context.contentResolver.openOutputStream(Uri.parse(path), "wt")?.use {
                        it.write(content.toByteArray(Charsets.UTF_8))
                    }
                }
            }
        }
        originalContents[path] = content
    }

    fun saveFileTabs() {
        onSaveTabs()
    }

    fun displayNameFromPath(path: String): String = com.template.jh.screens.home.displayNameFromPath(path)

    // ===== 审查操作 =====
    fun createReviewForEvent(event: com.template.jh.core.ai.FileEvent) {
        if ((event.operation == "pending" || event.operation == "modify") &&
            event.originalContent.isNotEmpty() && event.newContent.isNotEmpty()
        ) {
            val reviewState = createCodeReviewState(event.path, event.originalContent, event.newContent)
            reviewStates[event.path] = reviewState
            showReviewPanels[event.path] = false
        }
    }

    fun saveReviewResult(path: String, content: String) {
        if (!path.startsWith("content://")) {
            fileManager.writeFile(path, content)
        }
        originalContents[path] = content
        editorContent.remove(path)
        reviewStates.remove(path)
        showReviewPanels.remove(path)
    }

    fun acceptChangeBlock(path: String, blockIndex: Int) {
        reviewStates[path]?.let { reviewStates[path] = it.acceptBlock(blockIndex) }
    }

    fun rejectChangeBlock(path: String, blockIndex: Int) {
        reviewStates[path]?.let { reviewStates[path] = it.rejectBlock(blockIndex) }
    }

    fun acceptAllChanges(path: String) {
        val state = reviewStates[path] ?: return
        val updated = state.acceptAll()
        reviewStates[path] = updated
        saveReviewResult(path, updated.generateFinalContent())
    }

    fun rejectAllChanges(path: String) {
        val state = reviewStates[path] ?: return
        val updated = state.rejectAll()
        reviewStates[path] = updated
        saveReviewResult(path, state.oldContent)
    }

    fun navigateToBlock(path: String, blockIndex: Int) {
        reviewStates[path]?.let { reviewStates[path] = it.setCurrentIndex(blockIndex) }
    }

    fun toggleReviewPanel(path: String) {
        showReviewPanels[path] = !(showReviewPanels[path] ?: false)
    }

    fun handleTextChange(path: String, newTextFieldValue: TextFieldValue) {
        val newText = newTextFieldValue.text
        editorContent[path] = newTextFieldValue
        val originalContent = originalContents[path] ?: readFileFromSource(path)

        if (newText != originalContent && !reviewStates.containsKey(path)) {
            val newReviewState = createCodeReviewState(path, originalContent, newText)
            if (newReviewState.totalCount > 0) {
                reviewStates[path] = newReviewState
            }
        } else if (reviewStates.containsKey(path)) {
            val currentState = reviewStates[path]!!
            val updatedState = createCodeReviewState(path, currentState.oldContent, newText)
            reviewStates[path] = updatedState.copy(
                currentBlockIndex = currentState.currentBlockIndex.coerceIn(0, (updatedState.totalCount - 1).coerceAtLeast(0))
            )
        }
    }
}

@Composable
fun rememberEditorScreenState(
    chatViewModel: ChatViewModel,
    fileManager: FileManager,
): EditorScreenState {
    return remember {
        EditorScreenState(
            chatViewModel = chatViewModel,
            fileManager = fileManager,
        )
    }
}
