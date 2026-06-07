package com.template.jh.screens.home

import android.net.Uri
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
import androidx.window.core.layout.WindowSizeClass
import com.template.jh.R
import com.template.jh.core.ai.ChatViewModel
import com.template.jh.core.ai.FileOperationEvents
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

    // 打开/切换 Tab
    fun openTab(tab: TabItem) {
        val idx = tabs.indexOfFirst { it.id == tab.id }
        if (idx >= 0) {
            activeTabIndex = idx
        } else {
            tabs.add(tab)
            activeTabIndex = tabs.size - 1
        }
    }

    // 打开文件 Tab
    fun openFileTab(path: String, displayName: String? = null) {
        openTab(TabItem(path, displayName ?: displayNameFromPath(path), TabType.File))
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

    // 读取文件内容
    fun loadFileContent(path: String): TextFieldValue {
        editorContent[path]?.let { return it }
        val text = if (path.startsWith("content://")) {
            runCatching {
                context.contentResolver.openInputStream(Uri.parse(path))
                    ?.bufferedReader()?.readText() ?: "无法读取文件"
            }.getOrDefault("无法读取文件")
        } else {
            val file = File(workspaceRoot, path.trimStart('/'))
            runCatching { file.readText() }.getOrDefault("无法读取文件")
        }
        val tfv = TextFieldValue(text)
        editorContent[path] = tfv
        return tfv
    }

    // 保存文件
    fun saveFile(path: String) {
        val content = editorContent[path]?.text ?: return
        try {
            if (path.startsWith("content://")) {
                context.contentResolver.openOutputStream(Uri.parse(path), "wt")?.use {
                    it.write(content.toByteArray(Charsets.UTF_8))
                }
            } else {
                File(workspaceRoot, path.trimStart('/')).writeText(content)
            }
        } catch (_: Exception) {}
    }

    // 自动打开 AI 操作的文件
    LaunchedEffect(Unit) {
        FileOperationEvents.events.collect { event ->
            if (event.operation != "delete") {
                openFileTab(event.path)
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

    // SAF 文件夹选择器
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.openFolder(it)
            chatViewModel.setProjectRoot(it)
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
                        openFileTab(fileItem.uri.toString(), fileItem.name)
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
                        }
                    },
                    onCloseAllTabs = {
                        tabs.clear()
                        activeTabIndex = -1
                        isSettingsOpen = false
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
                        val tfv = loadFileContent(path)
                        CodeEditor(
                            text = tfv,
                            onTextChange = { editorContent[path] = it },
                            modifier = Modifier.fillMaxSize(),
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
                onAddToConversation = { fileItem ->
                    val currentText = chatViewModel.state.value.inputText
                    val label = if (fileItem.isDirectory) "[${fileItem.name}]" else "[${fileItem.name}]"
                    val newText = if (currentText.isBlank()) label else "$currentText $label"
                    chatViewModel.setInputText(newText)
                },
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
