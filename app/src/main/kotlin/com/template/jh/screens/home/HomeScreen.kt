package com.template.jh.screens.home

import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import androidx.window.core.layout.WindowSizeClass
import com.template.jh.R
import com.template.jh.core.ai.ChatViewModel
import com.template.jh.core.ai.FileOperationEvents
import com.template.jh.core.editor.CodeReviewState
import com.template.jh.core.editor.LineChangeType
import com.template.jh.core.editor.computeLineDiff
import com.template.jh.core.editor.createCodeReviewState
import com.template.jh.screens.home.components.AIChatPanel
import com.template.jh.screens.home.components.CodeEditor
import com.template.jh.screens.home.components.MainContentArea
import com.template.jh.screens.home.components.MainTopBar
import com.template.jh.screens.home.components.ResourcePanel
import com.template.jh.screens.home.components.SearchPanel
import com.template.jh.screens.home.components.Sidebar
import com.template.jh.screens.home.components.SidebarTab
import com.template.jh.screens.home.components.TaskListPanel
import com.template.jh.screens.home.components.ThreeColumnLayout
import com.template.jh.ui.adaptive.rememberWindowSizeClass
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import java.io.File

// 主屏幕，三列布局：侧边栏+可展开面板、中间主内容、右侧AI协作
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = koinViewModel(),
    chatViewModel: ChatViewModel = koinViewModel(),
) {
    val windowSizeClass = rememberWindowSizeClass()
    var selectedTab by remember { mutableStateOf<SidebarTab?>(null) }
    var isSettingsOpen by remember { mutableStateOf(false) }
    var isTerminalVisible by remember { mutableStateOf(false) }
    val homeState by viewModel.state.collectAsState()
    val files by viewModel.files.collectAsState()
    val chatState by chatViewModel.state.collectAsState()
    val context = LocalContext.current

    // 统一 Tab 列表（设置 + 文件）
    val tabs = remember { mutableStateListOf<TabItem>() }
    var activeTabIndex by remember { mutableIntStateOf(-1) }
    val settingsTabId = "__settings__"
    val settingsTabTitle = stringResource(R.string.settings_tab_name)

    // 编辑器内容缓存
    val editorContent = remember { mutableStateMapOf<String, TextFieldValue>() }
    // 文件原始内容缓存（用于检测修改和创建审查状态）
    val originalContents = remember { mutableStateMapOf<String, String>() }
    val workspaceRoot = remember { File(context.filesDir, "workspace") }
    // 相对路径 → content:// URI 缓存（SAF findFile 可能找不到隐藏文件，直接用原始 URI 读取）
    val relativeToContentUri = remember { mutableMapOf<String, String>() }
    // 关闭 tab 前确认保存对话框
    var closeConfirmPath by remember { mutableStateOf<String?>(null) }

    // SAF content:// 路径 → 相对于项目根目录的路径
    fun safPathToRelative(safPath: String): String {
        if (!safPath.startsWith("content://")) return safPath
        val projectUriStr = homeState.openedFolderUri ?: return safPath
        return try {
            val treeUri = Uri.parse(projectUriStr)
            val treeDocId = DocumentsContract.getTreeDocumentId(treeUri)
            val fileDocId = DocumentsContract.getDocumentId(Uri.parse(safPath))
            val prefix = treeDocId.trimEnd('/') + "/"
            if (fileDocId.startsWith(prefix)) {
                fileDocId.removePrefix(prefix)
            } else {
                val idx = fileDocId.indexOf("/")
                if (idx > 0) fileDocId.substring(idx + 1) else safPath
            }
        } catch (_: Exception) { safPath }
    }

    // 持久化文件 tabs + 同步到 ChatViewModel（让大模型感知打开的文件，使用相对路径）
    fun saveFileTabs() {
        val paths = tabs.filter { it.type == TabType.File }.map { it.id }
        if (paths.isNotEmpty()) viewModel.saveOpenedTabs(paths)
        // 转换为相对路径给大模型
        val projectUriStr = homeState.openedFolderUri
        val relPaths = if (projectUriStr != null) {
            paths.map { p -> safPathToRelative(p) }
        } else paths
        chatViewModel.setOpenedFilePaths(relPaths)
        // 同步当前活动文件（供 AI 感知正在编辑的文件）
        val activeTab = tabs.getOrNull(activeTabIndex)
        if (activeTab != null && activeTab.type == TabType.File) {
            val activeRel = if (projectUriStr != null) safPathToRelative(activeTab.id) else activeTab.id
            chatViewModel.setActiveFileContext(activeRel, 0)
        }
    }

    // 打开/切换 Tab
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

    // 打开文件 Tab（统一使用相对路径作为 ID，避免 content:// URI 与 AI 操作的相对路径不一致）
    fun openFileTab(path: String, displayName: String? = null) {
        val relPath = if (path.startsWith("content://")) {
            val rel = safPathToRelative(path)
            relativeToContentUri[rel] = path // 缓存原始 content:// URI
            rel
        } else path
        openTab(TabItem(relPath, displayName ?: displayNameFromPath(relPath), TabType.File))
    }

    // 打开设置 Tab
    fun openSettingsTab() {
        isSettingsOpen = true
        openTab(TabItem(settingsTabId, settingsTabTitle, TabType.Settings))
    }

    // 关闭设置 Tab
    fun closeSettingsTab() {
        isSettingsOpen = false
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

    // 从实际存储读取文件（SAF projectUri → workspaceRoot 兜底）
    fun readFileFromSource(path: String): String {
        // 优先通过缓存获取 content:// URI 直接读取（SAF findFile 可能找不到隐藏文件）
        val contentUri = relativeToContentUri[path]
        if (contentUri != null) {
            return runCatching {
                context.contentResolver.openInputStream(Uri.parse(contentUri))
                    ?.bufferedReader()?.readText()
            }.getOrDefault("无法读取文件") ?: "无法读取文件"
        }
        if (path.startsWith("content://")) {
            return runCatching {
                context.contentResolver.openInputStream(Uri.parse(path))
                    ?.bufferedReader()?.readText()
            }.getOrDefault("无法读取文件") ?: "无法读取文件"
        }
        val projectUriStr = homeState.openedFolderUri
        if (projectUriStr != null) {
            return runCatching {
                val treeUri = Uri.parse(projectUriStr)
                var doc = DocumentFile.fromTreeUri(context, treeUri) ?: return@runCatching "无法读取文件"
                for (segment in path.trimStart('/').split('/')) {
                    if (segment.isEmpty()) continue
                    doc = doc.findFile(segment) ?: return@runCatching "无法读取文件"
                }
                context.contentResolver.openInputStream(doc.uri)?.bufferedReader()?.readText()
                    ?: "无法读取文件"
            }.getOrDefault("无法读取文件")
        }
        return runCatching {
            File(workspaceRoot, path.trimStart('/')).readText()
        }.getOrDefault("无法读取文件")
    }

    // 读取文件内容（缓存优先 → readFileFromSource 兜底）
    fun loadFileContent(path: String): TextFieldValue {
        editorContent[path]?.let { return it }
        val content = readFileFromSource(path)
        // 保存原始内容用于后续比较
        if (!originalContents.containsKey(path)) {
            originalContents[path] = content
        }
        val tfv = TextFieldValue(content)
        editorContent[path] = tfv
        return tfv
    }

    // 检查文件是否已被修改（与原始文件内容比较）
    fun isFileModified(path: String): Boolean {
        val current = editorContent[path]?.text ?: return false
        val original = readFileFromSource(path)
        return current != original
    }

    // 保存文件（通过 SAF 相对路径或 workspaceRoot 兜底）
    fun saveFile(path: String) {
        val content = editorContent[path]?.text ?: return
        try {
            val projectUriStr = homeState.openedFolderUri
            if (projectUriStr != null) {
                val treeUri = Uri.parse(projectUriStr)
                var doc = DocumentFile.fromTreeUri(context, treeUri) ?: return
                for (segment in path.trimStart('/').split('/')) {
                    if (segment.isEmpty()) continue
                    doc = doc.findFile(segment) ?: return
                }
                context.contentResolver.openOutputStream(doc.uri, "wt")?.use {
                    it.write(content.toByteArray(Charsets.UTF_8))
                }
            } else {
                File(workspaceRoot, path.trimStart('/')).writeText(content)
            }
            // 更新原始内容为保存后的内容
            originalContents[path] = content
        } catch (_: Exception) {}
    }

    // 待审阅修改状态（新的代码审查系统）
    val reviewStates = remember { mutableStateMapOf<String, CodeReviewState>() }
    // 每个文件是否显示审查面板
    val showReviewPanels = remember { mutableStateMapOf<String, Boolean>() }

    // 自动打开 AI 操作的文件 + 创建代码审查状态
    LaunchedEffect(Unit) {
        FileOperationEvents.events.collect { event ->
            android.util.Log.d("HomeScreen", "FileOperationEvents: path=${event.path}, operation=${event.operation}")
            editorContent.remove(event.path)

            // 创建代码审查状态（无论是 pending 还是 modify 操作）
            if ((event.operation == "pending" || event.operation == "modify") &&
                event.originalContent.isNotEmpty() && event.newContent.isNotEmpty()) {
                val reviewState = createCodeReviewState(event.path, event.originalContent, event.newContent)
                reviewStates[event.path] = reviewState
                showReviewPanels[event.path] = false // 默认不显示面板
                android.util.Log.d("HomeScreen", "Created review state for ${event.path} with ${reviewState.totalCount} change blocks")
            }

            if (event.operation != "delete") {
                openFileTab(event.path)
            }
        }
    }

    // 保存审查结果到文件
    fun saveReviewResult(path: String, content: String) {
        val projectUriStr = homeState.openedFolderUri
        if (projectUriStr != null) {
            runCatching {
                val treeUri = Uri.parse(projectUriStr)
                var doc = DocumentFile.fromTreeUri(context, treeUri) ?: return@runCatching
                for (segment in path.trimStart('/').split('/')) {
                    if (segment.isEmpty()) continue
                    doc = doc.findFile(segment) ?: return@runCatching
                }
                context.contentResolver.openOutputStream(doc.uri, "wt")?.use { out ->
                    out.write(content.toByteArray(Charsets.UTF_8))
                }
            }
        }
        // 更新原始内容为保存后的内容
        originalContents[path] = content
        editorContent.remove(path)
        reviewStates.remove(path)
        showReviewPanels.remove(path)
    }

    // 接受指定修改块
    fun acceptChangeBlock(path: String, blockIndex: Int) {
        val state = reviewStates[path] ?: return
        val updated = state.acceptBlock(blockIndex)
        reviewStates[path] = updated
        android.util.Log.d("HomeScreen", "Accepted block $blockIndex for $path")
    }

    // 拒绝指定修改块
    fun rejectChangeBlock(path: String, blockIndex: Int) {
        val state = reviewStates[path] ?: return
        val updated = state.rejectBlock(blockIndex)
        reviewStates[path] = updated
        android.util.Log.d("HomeScreen", "Rejected block $blockIndex for $path")
    }

    // 接受所有修改
    fun acceptAllChanges(path: String) {
        val state = reviewStates[path] ?: return
        val updated = state.acceptAll()
        reviewStates[path] = updated
        // 写入最终内容
        saveReviewResult(path, updated.generateFinalContent())
        android.util.Log.d("HomeScreen", "Accepted all changes for $path")
    }

    // 拒绝所有修改
    fun rejectAllChanges(path: String) {
        val state = reviewStates[path] ?: return
        val updated = state.rejectAll()
        reviewStates[path] = updated
        // 写回原始内容
        saveReviewResult(path, state.oldContent)
        android.util.Log.d("HomeScreen", "Rejected all changes for $path")
    }

    // 导航到指定修改块
    fun navigateToBlock(path: String, blockIndex: Int) {
        val state = reviewStates[path] ?: return
        reviewStates[path] = state.setCurrentIndex(blockIndex)
    }

    // 切换审查面板显示
    fun toggleReviewPanel(path: String) {
        showReviewPanels[path] = !(showReviewPanels[path] ?: false)
    }

    // 兼容旧接口：接受全部（用于旧版 UI）
    fun acceptEdit(path: String) {
        acceptAllChanges(path)
    }

    // 兼容旧接口：拒绝全部（用于旧版 UI）
    fun rejectEdit(path: String) {
        rejectAllChanges(path)
    }

    // 监听审查事件（接受/拒绝所有修改）
    LaunchedEffect(Unit) {
        FileOperationEvents.reviewEvents.collect { event ->
            android.util.Log.d("HomeScreen", "ReviewEvent: path=${event.path}, action=${event.action}")
            when (event.action) {
                com.template.jh.core.ai.ReviewAction.AcceptAll -> acceptAllChanges(event.path)
                com.template.jh.core.ai.ReviewAction.RejectAll -> rejectAllChanges(event.path)
            }
        }
    }

    // 打开 AI 操作卡片指定的文件
    LaunchedEffect(Unit) {
        chatViewModel.openFileRequests.collect { path ->
            openFileTab(path)
        }
    }

    // SAF 文件选择器（模型加载）
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { chatViewModel.loadModelFromUri(it) }
    }

    // 自动打开上次文件夹
    val lastFolderUri by viewModel.lastOpenedFolderUri.collectAsState()
    val savedTabs by viewModel.openedFileTabs.collectAsState()
    var autoOpened by remember { mutableStateOf(false) }
    val closeFolder: () -> Unit = {
        viewModel.closeFolder()
        chatViewModel.setProjectRoot(null)
        tabs.clear()
        editorContent.clear()
        relativeToContentUri.clear()
        reviewStates.clear()
        showReviewPanels.clear()
        activeTabIndex = -1
        isSettingsOpen = false
        selectedTab = null
        autoOpened = false
    }

    // 对话框状态
    var showNewFileDialog by remember { mutableStateOf(false) }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var showRecentFilesDialog by remember { mutableStateOf(false) }
    var newFileName by remember { mutableStateOf("") }
    var newFolderName by remember { mutableStateOf("") }

    // SAF 文件选择器（打开文件）
    val fileOpenLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            openFileTab(it.toString())
        }
    }

    // SAF 另存为
    val saveAsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri: Uri? ->
        uri?.let { targetUri ->
            val tab = tabs.getOrNull(activeTabIndex) ?: return@let
            val content = editorContent[tab.id]?.text ?: return@let
            try {
                context.contentResolver.openOutputStream(targetUri)?.use { out ->
                    out.write(content.toByteArray(Charsets.UTF_8))
                }
            } catch (_: Exception) {}
        }
    }
    LaunchedEffect(lastFolderUri) {
        val uri = lastFolderUri
        if (!autoOpened && !uri.isNullOrBlank()) {
            autoOpened = true
            val parsed = Uri.parse(lastFolderUri)
            viewModel.openFolder(parsed)
            chatViewModel.setProjectRoot(parsed)
            selectedTab = SidebarTab.Explorer
            // 恢复上次打开的文件 tabs
            if (savedTabs.isNotEmpty()) {
                savedTabs.forEach { path -> openFileTab(path) }
            }
        }
    }

    // 最近文件夹名称（当前未打开文件夹时用于欢迎页展示）
    val recentFolderName = remember(lastFolderUri, homeState.openedFolderName) {
        if (homeState.openedFolderName != null) null
        else if (!lastFolderUri.isNullOrBlank()) {
            try {
                val docFile = DocumentFile.fromTreeUri(context, Uri.parse(lastFolderUri))
                docFile?.name ?: "最近文件夹"
            } catch (_: Exception) { "最近文件夹" }
        } else null
    }

    // SAF 文件夹选择器
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.openFolder(it)
            chatViewModel.setProjectRoot(it)
            viewModel.saveLastOpenedFolder(it.toString())
            selectedTab = SidebarTab.Explorer
        }
    }

    Scaffold(
        topBar = {
            MainTopBar(
                windowSizeClass = windowSizeClass,
                engineStatus = chatState.engineStatus,
                modelName = chatState.modelName,
                availableModels = chatState.availableModels,
                cloudProfiles = chatState.cloudModelProfiles,
                activeCloudProfileId = chatState.activeCloudProfileId,
                cloudModelEnabled = chatState.cloudModelEnabled,
                onScanModels = { chatViewModel.scanModels() },
                onLoadModel = { chatViewModel.loadModel(it) },
                onBrowseModelFile = {
                    filePickerLauncher.launch(arrayOf("application/octet-stream", "*/*"))
                },
                onSwitchCloudProfile = { chatViewModel.switchCloudProfile(it) },
                onTerminalClick = { isTerminalVisible = !isTerminalVisible },
                onCloseFolder = closeFolder,
                onNewFile = { showNewFileDialog = true; newFileName = "" },
                onNewFolder = { showNewFolderDialog = true; newFolderName = "" },
                onOpenFile = { fileOpenLauncher.launch(arrayOf("*/*")) },
                onOpenFolder = { folderPickerLauncher.launch(null) },
                onRecentFiles = { showRecentFilesDialog = true },
                onSaveFile = {
                    val idx = activeTabIndex
                    val tab = tabs.getOrNull(idx) ?: return@MainTopBar
                    if (tab.type == TabType.File) saveFile(tab.id)
                },
                onSaveAs = {
                    val tab = tabs.getOrNull(activeTabIndex)
                    val name = tab?.title ?: "untitled"
                    saveAsLauncher.launch(name)
                },
                onSaveAll = {
                    for (tab in tabs) {
                        if (tab.type == TabType.File) saveFile(tab.id)
                    }
                },
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { innerPadding ->
        ThreeColumnLayout(
            sidebar = {
                Sidebar(
                    selectedTab = selectedTab,
                    onTabClick = { tab ->
                        selectedTab = if (selectedTab == tab) null else tab
                    }
                )
            },
            leftPanel = {
                LeftPanelContent(
                    selectedTab = selectedTab,
                    homeState = homeState,
                    files = files,
                    viewModel = viewModel,
                    chatViewModel = chatViewModel,
                    chatState = chatState,
                    onFileClick = { fileItem ->
                        val rel = safPathToRelative(fileItem.uri.toString())
                        editorContent.remove(rel)
                        openFileTab(fileItem.uri.toString(), fileItem.name)
                    },
                    onAddToConversation = { fileItem ->
                        val currentText = chatViewModel.state.value.inputText
                        val label = if (fileItem.isDirectory) {
                            "[目录: ${fileItem.name}]"
                        } else {
                            val cached = editorContent[fileItem.uri.toString()]?.text
                            if (cached != null) {
                                val trimmed = if (cached.length > 2000) "${cached.take(2000)}\n// ... (剩余内容已截断)" else cached
                                "文件 `${fileItem.name}` 的内容：\n```\n$trimmed\n```"
                            } else {
                                "文件: `${fileItem.name}`（请先打开此文件查看内容）"
                            }
                        }
                        val newText = if (currentText.isBlank()) label else "$currentText\n\n$label"
                        chatViewModel.setInputText(newText)
                    },
                    onOpenFileTab = { filePath ->
                        openFileTab(filePath)
                    },
                    onAcceptAllChanges = { filePath ->
                        acceptAllChanges(filePath)
                    },
                    onRejectAllChanges = { filePath ->
                        rejectAllChanges(filePath)
                    },
                )
            },
            isLeftPanelVisible = selectedTab != null,
            centerContent = {
                MainContentArea(
                    onOpenFolder = { folderPickerLauncher.launch(null) },
                    chatViewModel = chatViewModel,
                    openedFolderName = homeState.openedFolderName,
                    recentFolderName = recentFolderName,
                    onOpenRecentFolder = { folderPickerLauncher.launch(null) },
                    tabs = tabs,
                    activeTabIndex = activeTabIndex,
                    onSelectTab = { activeTabIndex = it },
                    onCloseTab = { idx ->
                        val tab = tabs.getOrNull(idx) ?: return@MainContentArea
                        if (tab.id == settingsTabId) {
                            closeSettingsTab()
                        } else if (tab.type == TabType.File && isFileModified(tab.id)) {
                            closeConfirmPath = tab.id
                        } else {
                            tabs.removeAt(idx)
                            activeTabIndex = when {
                                tabs.isEmpty() -> -1
                                activeTabIndex >= tabs.size -> tabs.size - 1
                                else -> activeTabIndex.coerceIn(0, tabs.size - 1)
                            }
                            saveFileTabs()
                        }
                    },
                    isTerminalVisible = isTerminalVisible,
                    onTerminalClose = { isTerminalVisible = false },
                    onCloseAllTabs = {
                        tabs.clear()
                        activeTabIndex = -1
                        isSettingsOpen = false
                        viewModel.saveOpenedTabs(emptyList())
                    },
                    onSaveCurrent = {
                        val idx = activeTabIndex
                        val tab = tabs.getOrNull(idx) ?: return@MainContentArea
                        if (tab.type == TabType.File) saveFile(tab.id)
                    },
                    onSaveAllTabs = {
                        for (tab in tabs) {
                            if (tab.type == TabType.File) saveFile(tab.id)
                        }
                    },
                    tabContent = { path ->
                        val tfv = editorContent.getOrPut(path) { TextFieldValue(readFileFromSource(path)) }
                        val reviewState = reviewStates[path]
                        val showPanel = showReviewPanels[path] ?: false

                        // 如果有审查状态，使用新代码；否则使用当前文件内容
                        val displayText = reviewState?.newContent ?: tfv.text

                        // 计算行级 diff（兼容旧接口）
                        val (oldLineDiffs, newLineDiffs) = if (reviewState != null) {
                            computeLineDiff(reviewState.oldContent, reviewState.newContent)
                        } else Pair(emptyMap(), emptyMap())
                        val lineDiffs = newLineDiffs.filter { it.value != LineChangeType.Unchanged }
                        val oldDiffsFiltered = oldLineDiffs.filter { it.value != LineChangeType.Unchanged }

                        // 当前修改索引
                        var currentIndex by remember(path, reviewState) {
                            mutableIntStateOf(reviewState?.currentBlockIndex ?: 0)
                        }

                        // 确保索引有效
                        val totalBlocks = reviewState?.totalCount ?: 0
                        LaunchedEffect(totalBlocks) {
                            if (currentIndex >= totalBlocks && totalBlocks > 0) {
                                currentIndex = totalBlocks - 1
                            }
                        }

                        // 处理文本修改并触发审查
                        fun handleTextChange(newTextFieldValue: TextFieldValue) {
                            val newText = newTextFieldValue.text
                            editorContent[path] = newTextFieldValue

                            // 获取原始内容（优先使用已保存的原始内容）
                            val originalContent = originalContents[path] ?: readFileFromSource(path)

                            // 如果内容发生变化且没有审查状态，创建新的审查状态
                            if (newText != originalContent && !reviewStates.containsKey(path)) {
                                val newReviewState = createCodeReviewState(path, originalContent, newText)
                                if (newReviewState.totalCount > 0) {
                                    reviewStates[path] = newReviewState
                                    android.util.Log.d("HomeScreen", "Auto-created review state for $path with ${newReviewState.totalCount} change blocks")
                                }
                            }
                            // 如果已有审查状态，更新新内容
                            else if (reviewStates.containsKey(path)) {
                                val currentState = reviewStates[path]!!
                                // 重新计算diff
                                val updatedState = createCodeReviewState(path, currentState.oldContent, newText)
                                reviewStates[path] = updatedState.copy(
                                    currentBlockIndex = currentState.currentBlockIndex.coerceIn(0, (updatedState.totalCount - 1).coerceAtLeast(0))
                                )
                            }
                        }

                        CodeEditor(
                            text = TextFieldValue(displayText),
                            onTextChange = { handleTextChange(it) },
                            modifier = Modifier.fillMaxSize(),
                            lineDiffs = lineDiffs,
                            originalContent = reviewState?.oldContent,
                            reviewState = reviewState,
                            pendingFilePath = if (reviewState != null) path else null,
                            showReviewPanel = showPanel,
                            onToggleReviewPanel = { toggleReviewPanel(path) },
                            // 修改块级别操作
                            onAcceptBlock = { blockIndex -> acceptChangeBlock(path, blockIndex) },
                            onRejectBlock = { blockIndex -> rejectChangeBlock(path, blockIndex) },
                            onNavigateToBlock = { blockIndex ->
                                currentIndex = blockIndex
                                navigateToBlock(path, blockIndex)
                            },
                            // 导航操作
                            onJumpToPrevChange = {
                                reviewState?.let { state ->
                                    val prev = state.prevPendingIndex()
                                    if (prev >= 0) {
                                        currentIndex = prev
                                        navigateToBlock(path, prev)
                                    }
                                }
                            },
                            onJumpToNextChange = {
                                reviewState?.let { state ->
                                    val next = state.nextPendingIndex()
                                    if (next >= 0) {
                                        currentIndex = next
                                        navigateToBlock(path, next)
                                    }
                                }
                            },
                            // 接受/拒绝全部
                            onAcceptChanges = { acceptAllChanges(path) },
                            onRejectChanges = { rejectAllChanges(path) },
                            // 长按菜单回调
                            onAddToChat = { selectedText ->
                                val currentText = chatViewModel.state.value.inputText
                                val newText = if (currentText.isBlank()) selectedText else "$currentText\n\n$selectedText"
                                chatViewModel.setInputText(newText)
                            },
                        )
                    },
                )
            },
            rightPanel = {
                AIChatPanel(
                    onSettingsClick = { openSettingsTab() },
                    viewModel = chatViewModel,
                )
            },
            modifier = Modifier
                .fillMaxSize()
                .consumeWindowInsets(innerPadding)
                .padding(innerPadding)
        )
    }

    // 新建文件对话框
    if (showNewFileDialog) {
        AlertDialog(
            onDismissRequest = { showNewFileDialog = false },
            title = { Text(stringResource(R.string.dialog_new_file_title)) },
            text = {
                OutlinedTextField(
                    value = newFileName,
                    onValueChange = { newFileName = it },
                    placeholder = { Text(stringResource(R.string.dialog_new_file_name_hint)) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val name = newFileName.trim()
                    if (name.isNotEmpty()) {
                        val folderUri = homeState.openedFolderUri
                        if (folderUri != null) {
                            viewModel.createFile(Uri.parse(folderUri), name, false)
                            selectedTab = SidebarTab.Explorer
                        }
                        showNewFileDialog = false
                    }
                }) { Text(stringResource(R.string.dialog_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showNewFileDialog = false }) {
                    Text(stringResource(R.string.chat_cancel))
                }
            },
        )
    }

    // 新建文件夹对话框
    if (showNewFolderDialog) {
        AlertDialog(
            onDismissRequest = { showNewFolderDialog = false },
            title = { Text(stringResource(R.string.dialog_new_folder_title)) },
            text = {
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    placeholder = { Text(stringResource(R.string.dialog_new_folder_name_hint)) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val name = newFolderName.trim()
                    if (name.isNotEmpty()) {
                        val folderUri = homeState.openedFolderUri
                        if (folderUri != null) {
                            viewModel.createFile(Uri.parse(folderUri), name, true)
                            selectedTab = SidebarTab.Explorer
                        }
                        showNewFolderDialog = false
                    }
                }) { Text(stringResource(R.string.dialog_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showNewFolderDialog = false }) {
                    Text(stringResource(R.string.chat_cancel))
                }
            },
        )
    }

    // 关闭前确认保存对话框
    closeConfirmPath?.let { path ->
        val tabTitle = tabs.find { it.id == path }?.title ?: path
        AlertDialog(
            onDismissRequest = { closeConfirmPath = null },
            title = { Text("文件已修改") },
            text = { Text("「${tabTitle}」已被修改，是否保存更改？") },
            confirmButton = {
                TextButton(onClick = {
                    saveFile(path)
                    closeConfirmPath = null
                    val idx = tabs.indexOfFirst { it.id == path }
                    if (idx >= 0) {
                        tabs.removeAt(idx)
                        activeTabIndex = when {
                            tabs.isEmpty() -> -1
                            activeTabIndex >= tabs.size -> tabs.size - 1
                            else -> activeTabIndex.coerceIn(0, tabs.size - 1)
                        }
                        saveFileTabs()
                    }
                }) { Text("保存") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        closeConfirmPath = null
                        val idx = tabs.indexOfFirst { it.id == path }
                        if (idx >= 0) {
                            tabs.removeAt(idx)
                            activeTabIndex = when {
                                tabs.isEmpty() -> -1
                                activeTabIndex >= tabs.size -> tabs.size - 1
                                else -> activeTabIndex.coerceIn(0, tabs.size - 1)
                            }
                            saveFileTabs()
                        }
                    }) { Text("不保存") }
                    TextButton(onClick = { closeConfirmPath = null }) { Text(stringResource(R.string.chat_cancel)) }
                }
            },
        )
    }

    // 最近文件对话框
    val openFileTabs = tabs.filter { it.type == TabType.File }
    if (showRecentFilesDialog) {
        AlertDialog(
            onDismissRequest = { showRecentFilesDialog = false },
            title = { Text(stringResource(R.string.file_menu_recent_files)) },
            text = {
                if (openFileTabs.isEmpty()) {
                    Text(stringResource(R.string.recent_files_empty))
                } else {
                    Column {
                        openFileTabs.forEach { tab ->
                            TextButton(onClick = {
                                showRecentFilesDialog = false
                                val idx = tabs.indexOf(tab)
                                if (idx >= 0) activeTabIndex = idx
                            }) {
                                Text(tab.title)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showRecentFilesDialog = false }) {
                    Text(stringResource(R.string.chat_cancel))
                }
            },
        )
    }
}

@Composable
private fun LeftPanelContent(
    selectedTab: SidebarTab?,
    homeState: HomeUiState,
    files: List<FileItem>,
    viewModel: HomeViewModel,
    chatViewModel: ChatViewModel,
    chatState: com.template.jh.core.ai.ChatUiState,
    onFileClick: (FileItem) -> Unit = {},
    onAddToConversation: (FileItem) -> Unit = {},
    onOpenFileTab: (String) -> Unit = {},
    onAcceptAllChanges: (String) -> Unit = {},
    onRejectAllChanges: (String) -> Unit = {},
) {
    when (selectedTab) {
        SidebarTab.Explorer -> {
            ResourcePanel(
                openedFolderName = homeState.openedFolderName,
                files = files,
                onListChildren = { uri, callback ->
                    viewModel.listChildren(uri, callback)
                },
                onFileClick = onFileClick,
                onAddToConversation = onAddToConversation,
                onRename = { uri, newName -> viewModel.renameFile(uri, newName) },
                onDelete = { uri -> viewModel.deleteFile(uri) },
                onCreate = { uri, name, isDir -> viewModel.createFile(uri, name, isDir) },
            )
        }
        SidebarTab.Tasks -> {
            TaskListPanel(
                tasks = chatState.taskList,
                fileChanges = chatState.fileChanges,
                isExpanded = chatState.isTaskListOpen,
                onToggleExpand = { chatViewModel.toggleTaskList() },
                onTaskClick = { task ->
                    // 任务点击处理
                },
                onFileClick = { filePath ->
                    onOpenFileTab(filePath)
                },
                onAcceptAllChanges = onAcceptAllChanges,
                onRejectAllChanges = onRejectAllChanges
            )
        }
        SidebarTab.Search -> { SearchPanel() }
        SidebarTab.SourceControl -> { SourceControlPanel() }
        SidebarTab.Preview -> { PreviewPanel() }
        SidebarTab.Extensions -> { ExtensionsPanel() }
        null -> {}
    }
}

@Composable
private fun SourceControlPanel() {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        androidx.compose.material3.Text(
            text = stringResource(R.string.source_control),
            style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PreviewPanel() {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        androidx.compose.material3.Text(
            text = stringResource(R.string.preview),
            style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ExtensionsPanel() {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        androidx.compose.material3.Text(
            text = stringResource(R.string.extensions),
            style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
