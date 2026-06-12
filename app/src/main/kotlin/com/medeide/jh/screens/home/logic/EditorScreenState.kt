package com.medeide.jh.screens.home.logic

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
import androidx.compose.ui.text.TextRange
import com.medeide.jh.data.storage.FileManager
import com.medeide.jh.model.TabItem
import com.medeide.jh.model.TabType
import com.medeide.jh.model.displayNameFromPath
import com.medeide.jh.screens.home.ChatViewModel

class EditorScreenState(
    val chatViewModel: ChatViewModel,
    val fileManager: FileManager,
) {
    var onSaveTabs: () -> Unit = {}
    val tabs = mutableStateListOf<TabItem>()
    var activeTabIndex by mutableIntStateOf(-1)
    val settingsTabId = "__settings__"
    val terminalTabId = "__terminal__"

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

    fun openPreviewTab(path: String, displayName: String? = null) {
        openTab(TabItem(path, displayName ?: displayNameFromPath(path), TabType.Preview))
    }

    fun openFileTab(path: String, displayName: String? = null) {
        openTab(TabItem(path, displayName ?: displayNameFromPath(path), TabType.File))
    }

    /** 打开文件并将光标定位到指定行 */
    fun openFileAtLine(path: String, line: Int, displayName: String? = null) {
        openFileTab(path, displayName)
        val content = editorContent.getOrPut(path) { TextFieldValue(readFileFromSource(path)) }
        if (line <= 1 || content.text.isEmpty()) return
        val offset = calculateLineOffset(content.text, line).coerceAtMost(content.text.length)
        editorContent[path] = content.copy(selection = TextRange(offset))
    }

    private fun calculateLineOffset(text: String, line: Int): Int {
        var charCount = 0
        var currentLine = 1
        for (ch in text) {
            if (currentLine >= line) break
            charCount++
            if (ch == '\n') currentLine++
        }
        return charCount
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

    fun openTerminalTab(title: String) {
        openTab(TabItem(terminalTabId, title, TabType.Terminal))
    }

    fun closeTerminalTab() {
        val idx = tabs.indexOfFirst { it.id == terminalTabId }
        if (idx >= 0) {
            tabs.removeAt(idx)
            activeTabIndex = when {
                tabs.isEmpty() -> -1
                activeTabIndex >= tabs.size -> tabs.size - 1
                else -> activeTabIndex.coerceIn(0, tabs.size - 1)
            }
        }
    }

    val isTerminalTabOpen: Boolean get() = tabs.any { it.id == terminalTabId }

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

    fun displayNameFromPath(path: String): String = com.medeide.jh.model.displayNameFromPath(path)

    fun handleTextChange(path: String, newTextFieldValue: TextFieldValue) {
        editorContent[path] = newTextFieldValue
    }

    // === Diff & Merge ===

    /** 打开 Diff 视图：对比两个文件的文本差异 */
    fun openDiffView(oldPath: String, newPath: String) {
        val oldContent = readFileFromSource(oldPath)
        val newContent = readFileFromSource(newPath)
        val diffContent = buildDiffOutput(oldPath, oldContent, newPath, newContent)
        val diffPath = "__diff__${oldPath.substringAfterLast('/')}__${newPath.substringAfterLast('/')}"
        editorContent[diffPath] = TextFieldValue(diffContent)
        originalContents[diffPath] = diffContent
        openTab(TabItem(diffPath, "Diff: ${displayNameFromPath(oldPath)}", TabType.Preview))
    }

    private fun buildDiffOutput(oldPath: String, oldText: String, newPath: String, newText: String): String {
        val oldLines = oldText.lines()
        val newLines = newText.lines()
        val sb = StringBuilder()
        sb.appendLine("=== Diff: ${oldPath.substringAfterLast('/')} → ${newPath.substringAfterLast('/')} ===")
        sb.appendLine()
        val maxLen = maxOf(oldLines.size, newLines.size)
        for (i in 0 until maxLen) {
            val oldLine = oldLines.getOrNull(i)
            val newLine = newLines.getOrNull(i)
            when {
                oldLine == null -> sb.appendLine("+ $newLine")
                newLine == null -> sb.appendLine("- $oldLine")
                oldLine != newLine -> {
                    sb.appendLine("- $oldLine")
                    sb.appendLine("+ $newLine")
                }
                else -> sb.appendLine("  $oldLine")
            }
        }
        return sb.toString()
    }

    /** 合并：将 newPath 文件内容追加到 oldPath 文件末尾 */
    fun mergeFiles(oldPath: String, newPath: String) {
        val oldContent = readFileFromSource(oldPath)
        val newContent = readFileFromSource(newPath)
        val merged = buildString {
            appendLine(oldContent.trimEnd())
            appendLine()
            appendLine("// === Merged from: ${newPath.substringAfterLast('/')} ===")
            appendLine()
            appendLine(newContent.trimStart())
        }
        editorContent[oldPath] = TextFieldValue(merged)
        originalContents[oldPath] = merged
        // 标记为已修改
        val idx = getTabIdxById(oldPath)
        if (idx < 0) {
            openFileTab(oldPath)
        } else {
            activeTabIndex = idx
        }
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
