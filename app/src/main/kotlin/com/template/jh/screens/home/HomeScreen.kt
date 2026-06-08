package com.template.jh.screens.home

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import com.template.jh.R
import com.template.jh.core.ai.ChatViewModel
import com.template.jh.core.storage.FileManager
import com.template.jh.screens.home.components.AIChatPanel
import com.template.jh.screens.home.components.MainContentArea
import com.template.jh.screens.home.components.MainTopBar
import com.template.jh.screens.home.components.Sidebar
import com.template.jh.screens.home.components.SidebarTab
import com.template.jh.screens.home.components.ThreeColumnLayout
import com.template.jh.screens.home.components.editor.CodeEditor
import com.template.jh.screens.home.components.resourcepanel.ResourcePanel
import com.template.jh.ui.adaptive.rememberWindowSizeClass
import org.koin.androidx.compose.koinViewModel

@Composable
fun HomeScreen(
    viewModel: HomeViewModel = koinViewModel(),
    chatViewModel: ChatViewModel = koinViewModel(),
) {
    val windowSizeClass = rememberWindowSizeClass()
    val context = LocalContext.current
    val homeState by viewModel.state.collectAsState()
    val files by viewModel.files.collectAsState()
    val chatState by chatViewModel.state.collectAsState()
    val settingsTabTitle = stringResource(R.string.settings_tab_name)

    var selectedTab by remember { mutableStateOf<SidebarTab?>(null) }
    var isSettingsOpen by remember { mutableStateOf(false) }
    var isTerminalVisible by remember { mutableStateOf(false) }
    var autoOpened by remember { mutableStateOf(false) }
    var cursorLine by remember { mutableIntStateOf(0) }

    val fileManager = org.koin.java.KoinJavaComponent.get<FileManager>(FileManager::class.java)
    val editorState = rememberEditorScreenState(chatViewModel, fileManager)

    // Tab 持久化 - 使用相对路径
    editorState.onSaveTabs = {
        val fileTabs = editorState.tabs.filter { it.type == TabType.File }
        val paths = fileTabs.map { it.id }
        if (paths.isNotEmpty()) viewModel.saveOpenedTabs(paths)
        chatViewModel.setOpenedFilePaths(paths)
        val activeTab = editorState.tabs.getOrNull(editorState.activeTabIndex)
        if (activeTab != null && activeTab.type == TabType.File) {
            chatViewModel.setActiveFileContext(activeTab.id, cursorLine)
        }
        val modifiedPaths = fileTabs.filter { editorState.isFileModified(it.id) }.map { it.id }
        chatViewModel.setModifiedFilePaths(modifiedPaths)
    }

    LaunchedEffect(Unit) {
        chatViewModel.openFileRequests.collect { path ->
            val fileName = displayNameFromPath(path)
            if (FileTypeUtil.isImageFile(fileName)) {
                editorState.openTab(TabItem(path, fileName, TabType.Image))
            } else {
                editorState.openFileTab(path)
            }
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? -> uri?.let { chatViewModel.loadModelFromUri(it) } }

    val fileOpenLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? -> uri?.let { editorState.openFileTab(uri.toString()) } }

    val saveAsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("*/*")
    ) { uri: Uri? ->
        uri?.let { targetUri ->
            val tab = editorState.tabs.getOrNull(editorState.activeTabIndex) ?: return@let
            val content = editorState.editorContent[tab.id]?.text ?: return@let
            try { context.contentResolver.openOutputStream(targetUri)?.use { it.write(content.toByteArray(Charsets.UTF_8)) } } catch (_: Exception) {}
        }
    }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.openFolder(it)
            chatViewModel.setProjectRoot(it)
            viewModel.saveLastOpenedFolder(it.toString())
            selectedTab = SidebarTab.Explorer
        }
    }

    val lastFolderUri by viewModel.lastOpenedFolderUri.collectAsState()
    val savedTabs by viewModel.openedFileTabs.collectAsState()
    LaunchedEffect(lastFolderUri) {
        val uri = lastFolderUri
        if (!autoOpened && !uri.isNullOrBlank()) {
            autoOpened = true
            viewModel.openFolder(Uri.parse(uri))
            chatViewModel.setProjectRoot(Uri.parse(uri))
            selectedTab = SidebarTab.Explorer
            savedTabs.forEach { path -> editorState.openFileTab(path) }
        }
    }

    val closeFolder: () -> Unit = {
        viewModel.closeFolder()
        chatViewModel.setProjectRoot(null)
        editorState.closeAllTabs()
        editorState.editorContent.clear()
        isSettingsOpen = false
        selectedTab = null
        autoOpened = false
    }

    var closeConfirmPath by remember { mutableStateOf<String?>(null) }
    var showNewFileDialog by remember { mutableStateOf(false) }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var showRecentFilesDialog by remember { mutableStateOf(false) }
    var newFileName by remember { mutableStateOf("") }
    var newFolderName by remember { mutableStateOf("") }

    val recentFolderName = remember(lastFolderUri, homeState.openedFolderName) {
        if (homeState.openedFolderName != null) null
        else if (!lastFolderUri.isNullOrBlank()) {
            runCatching { DocumentFile.fromTreeUri(context, Uri.parse(lastFolderUri))?.name ?: "最近文件夹" }.getOrDefault("最近文件夹")
        } else null
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
                onBrowseModelFile = { filePickerLauncher.launch(arrayOf("application/octet-stream", "*/*")) },
                onSwitchCloudProfile = { chatViewModel.switchCloudProfile(it) },
                onTerminalClick = { isTerminalVisible = !isTerminalVisible },
                onCloseFolder = closeFolder,
                onOpenFile = { fileOpenLauncher.launch(arrayOf("*/*")) },
                onOpenFolder = { folderPickerLauncher.launch(null) },
                onRecentFiles = { showRecentFilesDialog = true },
                onSaveAll = { editorState.tabs.filter { it.type == TabType.File }.forEach { editorState.saveFile(it.id) } },
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { innerPadding ->
        ThreeColumnLayout(
            sidebar = {
                Sidebar(
                    selectedTab = selectedTab,
                    onTabClick = { tab -> selectedTab = if (selectedTab == tab) null else tab },
                )
            },
            leftPanel = {
                LeftPanelContent(
                    selectedTab = selectedTab,
                    homeState = homeState,
                    files = files,
                    viewModel = viewModel,
                    chatViewModel = chatViewModel,
                    editorState = editorState,
                    onFileClick = { fileItem ->
                        when (FileTypeUtil.openMode(fileItem.name, fileItem.size)) {
                            FileOpenMode.IMAGE -> {
                                editorState.openTab(TabItem(fileItem.uri.toString(), fileItem.name, TabType.Image))
                            }
                            FileOpenMode.TEXT -> {
                                val path = if (fileItem.relativePath.isNotEmpty()) fileItem.relativePath else fileItem.uri.toString()
                                editorState.editorContent.remove(path)
                                editorState.openFileTab(path, fileItem.name)
                            }
                            FileOpenMode.UNSUPPORTED -> {
                                val msg = if (fileItem.size > FileTypeUtil.MAX_TEXT_SIZE) {
                                    "文件过大 (${fileItem.size / 1024 / 1024}MB)，无法以文本模式打开"
                                } else {
                                    "不支持打开此格式"
                                }
                                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    onAddToConversation = { fileItem ->
                        if (!fileItem.isDirectory) {
                            val path = if (fileItem.relativePath.isNotEmpty()) fileItem.relativePath else fileItem.uri.toString()
                            chatViewModel.attachFile(path, fileItem.name)
                        }
                    },
                    onOpenFileTab = { editorState.openFileTab(it) },
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
                    tabs = editorState.tabs,
                    activeTabIndex = editorState.activeTabIndex,
                    onSelectTab = { editorState.activeTabIndex = it },
                    onCloseTab = { idx ->
                        val tab = editorState.tabs.getOrNull(idx) ?: return@MainContentArea
                        if (tab.id == editorState.settingsTabId) {
                            editorState.closeSettingsTab()
                            isSettingsOpen = false
                        } else if (tab.type == TabType.File && editorState.isFileModified(tab.id)) {
                            closeConfirmPath = tab.id
                        } else {
                            editorState.closeTab(idx)
                        }
                    },
                    isTerminalVisible = isTerminalVisible,
                    onTerminalClose = { isTerminalVisible = false },
                    onCloseAllTabs = { editorState.closeAllTabs(); isSettingsOpen = false; viewModel.saveOpenedTabs(emptyList()) },
                    onSaveCurrent = {
                        val tab = editorState.tabs.getOrNull(editorState.activeTabIndex)
                        if (tab?.type == TabType.File) editorState.saveFile(tab.id)
                    },
                    onSaveAllTabs = { editorState.tabs.filter { it.type == TabType.File }.forEach { editorState.saveFile(it.id) } },
                    tabContent = { path ->
                        val tfv = editorState.editorContent.getOrPut(path) { TextFieldValue(editorState.readFileFromSource(path)) }

                        CodeEditor(
                            text = tfv,
                            onTextChange = {
                                editorState.handleTextChange(path, it)
                                val modified = editorState.tabs.filter { t -> t.type == TabType.File && editorState.isFileModified(t.id) }.map { it.id }
                                chatViewModel.setModifiedFilePaths(modified)
                            },
                            modifier = Modifier.fillMaxSize(),
                            onAddToChat = { selectedText ->
                                val current = chatViewModel.state.value.inputText
                                chatViewModel.setInputText(if (current.isBlank()) selectedText else "$current\n\n$selectedText")
                            },
                            onCursorChange = { line -> cursorLine = line },
                        )
                    },
                )
            },
            rightPanel = {
                AIChatPanel(
                    onSettingsClick = {
                        isSettingsOpen = true
                        editorState.openSettingsTab(settingsTabTitle)
                    },
                    viewModel = chatViewModel,
                )
            },
            modifier = Modifier
                .fillMaxSize()
                .consumeWindowInsets(innerPadding)
                .padding(innerPadding),
        )
    }

    if (showNewFileDialog) {
        AlertDialog(
            onDismissRequest = { showNewFileDialog = false },
            title = { Text(stringResource(R.string.dialog_new_file_title)) },
            text = { OutlinedTextField(value = newFileName, onValueChange = { newFileName = it }, placeholder = { Text(stringResource(R.string.dialog_new_file_name_hint)) }, singleLine = true) },
            confirmButton = { TextButton(onClick = { val name = newFileName.trim(); if (name.isNotEmpty()) { homeState.openedFolderUri?.let { viewModel.createFile(Uri.parse(it), name, false) }; selectedTab = SidebarTab.Explorer; showNewFileDialog = false } }) { Text(stringResource(R.string.dialog_confirm)) } },
            dismissButton = { TextButton(onClick = { showNewFileDialog = false }) { Text(stringResource(R.string.chat_cancel)) } },
        )
    }
    if (showNewFolderDialog) {
        AlertDialog(
            onDismissRequest = { showNewFolderDialog = false },
            title = { Text(stringResource(R.string.dialog_new_folder_title)) },
            text = { OutlinedTextField(value = newFolderName, onValueChange = { newFolderName = it }, placeholder = { Text(stringResource(R.string.dialog_new_folder_name_hint)) }, singleLine = true) },
            confirmButton = { TextButton(onClick = { val name = newFolderName.trim(); if (name.isNotEmpty()) { homeState.openedFolderUri?.let { viewModel.createFile(Uri.parse(it), name, true) }; selectedTab = SidebarTab.Explorer; showNewFolderDialog = false } }) { Text(stringResource(R.string.dialog_save)) } },
            dismissButton = { TextButton(onClick = { showNewFolderDialog = false }) { Text(stringResource(R.string.chat_cancel)) } },
        )
    }

    closeConfirmPath?.let { path ->
        val tabTitle = editorState.tabs.find { it.id == path }?.title ?: path
        AlertDialog(
            onDismissRequest = { closeConfirmPath = null },
            title = { Text("文件已修改") },
            text = { Text("「${tabTitle}」已被修改，是否保存更改？") },
            confirmButton = { TextButton(onClick = { editorState.saveFile(path); closeConfirmPath = null; editorState.closeTab(editorState.getTabIdxById(path)) }) { Text("保存") } },
            dismissButton = {
                Row {
                    TextButton(onClick = { closeConfirmPath = null; editorState.closeTab(editorState.getTabIdxById(path)) }) { Text("不保存") }
                    TextButton(onClick = { closeConfirmPath = null }) { Text(stringResource(R.string.chat_cancel)) }
                }
            },
        )
    }

    val openFileTabs = editorState.tabs.filter { it.type == TabType.File }
    if (showRecentFilesDialog) {
        AlertDialog(
            onDismissRequest = { showRecentFilesDialog = false },
            title = { Text(stringResource(R.string.file_menu_recent_files)) },
            text = {
                if (openFileTabs.isEmpty()) {
                    Text(stringResource(R.string.recent_files_empty))
                } else {
                    androidx.compose.foundation.layout.Column {
                        openFileTabs.forEach { tab ->
                            TextButton(onClick = {
                                showRecentFilesDialog = false
                                val idx = editorState.tabs.indexOf(tab)
                                if (idx >= 0) editorState.activeTabIndex = idx
                            }) { Text(tab.title) }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showRecentFilesDialog = false }) { Text(stringResource(R.string.chat_cancel)) } },
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
    editorState: EditorScreenState,
    onFileClick: (FileItem) -> Unit = {},
    onAddToConversation: (FileItem) -> Unit = {},
    onOpenFileTab: (String) -> Unit = {},
) {
    when (selectedTab) {
        SidebarTab.Explorer -> {
            ResourcePanel(
                openedFolderName = homeState.openedFolderName,
                files = files,
                onListChildren = { relativePath, callback ->
                    viewModel.listChildren(relativePath, callback)
                },
                onFileClick = onFileClick,
                onAddToConversation = onAddToConversation,
                onRename = { relativePath, newName ->
                    val file = files.find { it.relativePath == relativePath }
                    if (file != null) viewModel.renameFile(file.uri, newName)
                },
                onDelete = { relativePath ->
                    val file = files.find { it.relativePath == relativePath }
                    if (file != null) viewModel.deleteFile(file.uri)
                },
                onCreate = { relativePath, name, isDir ->
                    val parentFile = files.find { it.relativePath == relativePath }
                    if (parentFile != null) viewModel.createFile(parentFile.uri, name, isDir)
                    else homeState.openedFolderUri?.let { viewModel.createFile(Uri.parse(it), name, isDir) }
                },
            )
        }
        null -> {}
    }
}
