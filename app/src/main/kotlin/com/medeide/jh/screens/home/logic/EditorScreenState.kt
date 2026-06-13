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
import com.medeide.jh.screens.home.landscape.collab.viewmodel.ChatViewModel

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

    // 当前文件中的搜索匹配结果（用于编辑器悬浮定位/替换）
    var currentSearchMatches by mutableStateOf<List<com.medeide.jh.screens.home.landscape.sidebar.SearchResultItem>>(emptyList())
    var currentSearchMatchIndex by mutableIntStateOf(-1)
    var currentSearchQuery by mutableStateOf("")
    var currentReplaceText by mutableStateOf("")
    var isSearchToolbarVisible by mutableStateOf(false)
    // 记录生成 currentSearchMatches 时的查询快照，用于判断是否需要重新搜索
    var searchToolbarQuerySnapshot by mutableStateOf("")

    // 持久化搜索结果（面板收起再展开后仍保留）
    var persistentSearchResults by mutableStateOf<List<com.medeide.jh.screens.home.landscape.sidebar.SearchResultItem>>(emptyList())
    var persistentSearchQuery by mutableStateOf("")
    var persistentReplaceText by mutableStateOf("")
    var persistentIsRegex by mutableStateOf(false)
    var persistentIsCaseSensitive by mutableStateOf(false)
    var persistentIsWholeWord by mutableStateOf(false)

    // 用于触发编辑器自动滚动到光标行的版本号，每次导航递增
    var searchScrollVersion by mutableIntStateOf(0)

    // 撤销/重做历史栈（每个文件独立）
    private val undoHistory = mutableMapOf<String, MutableList<String>>()
    private val redoHistory = mutableMapOf<String, MutableList<String>>()
    private val lastUndoPushTime = mutableMapOf<String, Long>()

    /** 获取当前处于编辑中的文件路径，没有则返回 null */
    fun getActiveFilePath(): String? {
        val idx = activeTabIndex
        if (idx !in tabs.indices) return null
        val tab = tabs[idx]
        return if (tab.type == TabType.File) tab.id else null
    }

    /** 保存当前内容到撤销栈（仅在文件编辑且内容有变化时推送） */
    fun pushUndo(path: String) {
        val current = editorContent[path]?.text ?: return
        val now = System.currentTimeMillis()
        // 节流：同一文件 300ms 内的连续编辑只记录一次
        if (now - (lastUndoPushTime[path] ?: 0L) < 300L) {
            // 更新栈顶而不是新增，保证撤销到上一个稳定状态
            val stack = undoHistory.getOrPut(path) { mutableListOf() }
            if (stack.isNotEmpty()) {
                stack[stack.lastIndex] = current
            }
            return
        }
        lastUndoPushTime[path] = now
        val stack = undoHistory.getOrPut(path) { mutableListOf() }
        // 限制栈深度
        if (stack.size >= 100) stack.removeFirst()
        stack.add(current)
        redoHistory[path]?.clear()
    }

    /** 撤销：还原到上一个编辑状态 */
    fun handleUndo() {
        val path = getActiveFilePath() ?: return
        val undoStack = undoHistory[path] ?: return
        if (undoStack.isEmpty()) return
        val currentText = editorContent[path]?.text ?: return
        val prevText = undoStack.removeLast()
        // 将当前内容推入重做栈
        redoHistory.getOrPut(path) { mutableListOf() }.add(currentText)
        editorContent[path] = TextFieldValue(prevText)
    }

    /** 重做：恢复被撤销的内容 */
    fun handleRedo() {
        val path = getActiveFilePath() ?: return
        val redoStack = redoHistory[path] ?: return
        if (redoStack.isEmpty()) return
        val currentText = editorContent[path]?.text ?: return
        val nextText = redoStack.removeLast()
        // 将当前内容推回撤销栈
        undoHistory.getOrPut(path) { mutableListOf() }.add(currentText)
        editorContent[path] = TextFieldValue(nextText)
    }

    /** 全文复制到剪贴板 */
    fun copyAllText(context: android.content.Context) {
        val path = getActiveFilePath() ?: return
        val content = editorContent[path]?.text ?: return
        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("editor", content)
        clipboard?.setPrimaryClip(clip)
    }

    /** 打开查找替换工具栏 */
    fun openSearchToolbar() {
        isSearchToolbarVisible = true
    }

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

    /** 打开文件并将光标定位到指定行，若提供 searchQuery 则选中该行中匹配的文本 */
    fun openFileAtLine(path: String, line: Int, displayName: String? = null, searchQuery: String? = null) {
        openFileTab(path, displayName)
        val content = editorContent.getOrPut(path) { TextFieldValue(readFileFromSource(path)) }
        if (line <= 1 || content.text.isEmpty()) return
        searchScrollVersion++
        val offset = calculateLineOffset(content.text, line).coerceAtMost(content.text.length)
        if (searchQuery != null) {
            val lines = content.text.lines()
            val lineText = lines.getOrNull(line - 1) ?: return
            val matchStartInLine = lineText.indexOf(searchQuery, ignoreCase = true)
            if (matchStartInLine >= 0) {
                val matchStart = (offset + matchStartInLine).coerceAtMost(content.text.length)
                val matchEnd = (matchStart + searchQuery.length).coerceAtMost(content.text.length)
                editorContent[path] = content.copy(selection = TextRange(matchStart, matchEnd))
                return
            }
        }
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

    val isTerminalTabOpen: Boolean get() = false

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

    /** 外部调用：文本变更前调用此方法记录撤销快照 */
    fun onBeforeTextChange(path: String) {
        pushUndo(path)
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
