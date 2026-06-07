package com.template.jh.screens.home

import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
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
import com.template.jh.core.editor.LineChangeType
import com.template.jh.core.editor.computeLineDiff
import com.template.jh.screens.home.components.AIChatPanel
import com.template.jh.screens.home.components.CodeEditor
import com.template.jh.screens.home.components.MainContentArea
import com.template.jh.screens.home.components.MainTopBar
import com.template.jh.screens.home.components.ResourcePanel
import com.template.jh.screens.home.components.SearchPanel
import com.template.jh.screens.home.components.Sidebar
import com.template.jh.screens.home.components.SidebarTab
import com.template.jh.screens.home.components.ThreeColumnLayout
import com.template.jh.ui.adaptive.rememberWindowSizeClass
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
    val workspaceRoot = remember { File(context.filesDir, "workspace") }
    // 相对路径 → content:// URI 缓存（SAF findFile 可能找不到隐藏文件，直接用原始 URI 读取）
    val relativeToContentUri = remember { mutableMapOf<String, String>() }

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
            }?.getOrDefault("无法读取文件") ?: "无法读取文件"
        }
        if (path.startsWith("content://")) {
            return runCatching {
                context.contentResolver.openInputStream(Uri.parse(path))
                    ?.bufferedReader()?.readText()
            }?.getOrDefault("无法读取文件") ?: "无法读取文件"
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
        val tfv = TextFieldValue(readFileFromSource(path))
        editorContent[path] = tfv
        return tfv
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
        } catch (_: Exception) {}
    }

    // 待审阅修改（文件路径 → 原始/新内容）
    data class PendingFileEdit(val filePath: String, val originalContent: String, val newContent: String)
    val pendingEdits = remember { mutableStateMapOf<String, PendingFileEdit>() }

    // 自动打开 AI 操作的文件 + 追踪待审阅修改
    LaunchedEffect(Unit) {
        FileOperationEvents.events.collect { event ->
            editorContent.remove(event.path)
            if (event.operation == "modify" && event.originalContent.isNotEmpty() && event.newContent.isNotEmpty()) {
                pendingEdits[event.path] = PendingFileEdit(event.path, event.originalContent, event.newContent)
            }
            if (event.operation != "delete") {
                openFileTab(event.path)
            }
        }
    }

    // 确认/拒绝修改
    fun acceptEdit(path: String) {
        pendingEdits.remove(path)
        editorContent.remove(path) // 强制刷新
    }
    fun rejectEdit(path: String) {
        val edit = pendingEdits[path] ?: return
        pendingEdits.remove(path)
        // 写回原始内容
        val projectUriStr = homeState.openedFolderUri
        if (projectUriStr != null) {
            runCatching {
                val treeUri = Uri.parse(projectUriStr)
                var doc = DocumentFile.fromTreeUri(context, treeUri) ?: return@runCatching
                for (segment in edit.filePath.trimStart('/').split('/')) {
                    if (segment.isEmpty()) continue
                    doc = doc.findFile(segment) ?: return@runCatching
                }
                context.contentResolver.openOutputStream(doc.uri, "wt")?.use { out ->
                    out.write(edit.originalContent.toByteArray(Charsets.UTF_8))
                }
            }
        }
        editorContent.remove(edit.filePath)
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
    LaunchedEffect(lastFolderUri) {
        if (!autoOpened && lastFolderUri != null) {
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
                onScanModels = { chatViewModel.scanModels() },
                onLoadModel = { chatViewModel.loadModel(it) },
                onBrowseModelFile = {
                    filePickerLauncher.launch(arrayOf("application/octet-stream", "*/*"))
                },
                onTerminalClick = { isTerminalVisible = !isTerminalVisible },
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
                )
            },
            isLeftPanelVisible = selectedTab != null,
            centerContent = {
                MainContentArea(
                    onOpenFolder = { folderPickerLauncher.launch(null) },
                    onNewProject = {},
                    onCloneGit = {},
                    chatViewModel = chatViewModel,
                    onBrowseModelFile = {
                        filePickerLauncher.launch(arrayOf("application/octet-stream", "*/*"))
                    },
                    openedFolderName = homeState.openedFolderName,
                    tabs = tabs,
                    activeTabIndex = activeTabIndex,
                    onSelectTab = { activeTabIndex = it },
                    onCloseTab = { idx ->
                        val tab = tabs.getOrNull(idx) ?: return@MainContentArea
                        if (tab.id == settingsTabId) {
                            closeSettingsTab()
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
                        val pending = pendingEdits[path]
                        val lineDiffs: Map<Int, LineChangeType> = if (pending != null) {
                            computeLineDiff(pending.originalContent, pending.newContent)
                                .filter { it.type != LineChangeType.Unchanged }
                                .associate { it.lineIndex to it.type }
                        } else emptyMap()
                        CodeEditor(
                            text = tfv,
                            onTextChange = { editorContent[path] = it },
                            modifier = Modifier.fillMaxSize(),
                            lineDiffs = lineDiffs,
                            pendingFilePath = if (pending != null) path else null,
                            onAcceptChanges = { acceptEdit(path) },
                            onRejectChanges = { rejectEdit(path) },
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
}

@Composable
private fun LeftPanelContent(
    selectedTab: SidebarTab?,
    homeState: HomeUiState,
    files: List<FileItem>,
    viewModel: HomeViewModel,
    chatViewModel: ChatViewModel,
    onFileClick: (FileItem) -> Unit = {},
    onAddToConversation: (FileItem) -> Unit = {},
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
