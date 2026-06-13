package com.medeide.jh.screens.home

import android.Manifest
import android.net.Uri
import android.content.pm.PackageManager
import android.os.Environment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.medeide.jh.R
import com.medeide.jh.model.FileItem
import com.medeide.jh.model.TabItem
import com.medeide.jh.model.TabType
import com.medeide.jh.screens.home.landscape.collab.viewmodel.ChatViewModel
import com.medeide.jh.screens.home.ai.FileOperationEvents
import com.medeide.jh.data.storage.FileManager
import com.medeide.jh.screens.home.landscape.collab.chat.CollabPanel
import com.medeide.jh.screens.home.landscape.workspace.MainContentArea
import com.medeide.jh.screens.home.landscape.topbar.MainTopBar
import com.medeide.jh.screens.home.landscape.sidebar.Sidebar
import com.medeide.jh.screens.home.landscape.sidebar.SidebarTab
import com.medeide.jh.screens.home.landscape.sidebar.SearchReplacePanel
import com.medeide.jh.screens.home.ThreeColumnLayout
import com.medeide.jh.screens.home.landscape.workspace.editor.CodeEditor
import com.medeide.jh.screens.home.landscape.sidebar.resourcepanel.ResourcePanel
import com.medeide.jh.screens.home.logic.EditorScreenState
import com.medeide.jh.screens.home.logic.rememberEditorScreenState
import com.medeide.jh.screens.home.logic.utils.FileTypeUtil
import com.medeide.jh.screens.home.logic.utils.FileOpenMode
import com.medeide.jh.model.displayNameFromPath
import com.medeide.jh.ui.adaptive.rememberWindowSizeClass
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
    var cursorLine by remember { mutableIntStateOf(0) }
    var cursorLineContent by remember { mutableStateOf("") }
    var previewModeTabs by remember { mutableStateOf(setOf<String>()) }

    val fileManager = org.koin.java.KoinJavaComponent.get<FileManager>(FileManager::class.java)
    val editorState = rememberEditorScreenState(chatViewModel, fileManager)
    val audioPlaybackState = remember { com.medeide.jh.screens.home.landscape.topbar.audio.AudioPlaybackState() }
    val videoPlaybackState = remember { com.medeide.jh.screens.home.landscape.workspace.viewer.VideoPlaybackState() }

    // 每次启动时检测权限，已有权限则自动打开存储目录
    LaunchedEffect(Unit) {
        if (Environment.isExternalStorageManager()) {
            viewModel.openDirectStorage()
            viewModel.recordRecentFolder("/storage/emulated/0", "存储根目录")
            chatViewModel.setProjectRootPath("/storage/emulated/0", "存储根目录")
            selectedTab = SidebarTab.Explorer
        }
    }

    // Tab 持久化
    editorState.onSaveTabs = {
        val fileTabs = editorState.tabs.filter { it.type == TabType.File || it.type == TabType.Image
            || it.type == TabType.Audio || it.type == TabType.Video || it.type == TabType.Archive
            || it.type == TabType.Preview }
        val paths = fileTabs.map { it.id }
        viewModel.saveOpenedTabs(paths.filter { !it.startsWith("content://") })
        chatViewModel.setOpenedFilePaths(paths)
        val activeTab = editorState.tabs.getOrNull(editorState.activeTabIndex)
        if (activeTab != null && activeTab.type != TabType.Settings) {
            chatViewModel.setActiveFileContext(activeTab.id, cursorLine, cursorLineContent)
        }
        val modifiedPaths = editorState.tabs.filter { it.type == TabType.File && editorState.isFileModified(it.id) }.map { it.id }
        chatViewModel.setModifiedFilePaths(modifiedPaths)
    }

    LaunchedEffect(Unit) {
        chatViewModel.openFileRequests.collect { path ->
            val fileName = displayNameFromPath(path)
            when {
                FileTypeUtil.isImageFile(fileName) ->
                    editorState.openTab(TabItem(path, fileName, TabType.Image))
                FileTypeUtil.isAudioFile(fileName) ->
                    editorState.openTab(TabItem(path, fileName, TabType.Audio))
                FileTypeUtil.isVideoFile(fileName) ->
                    editorState.openTab(TabItem(path, fileName, TabType.Video))
                FileTypeUtil.isArchiveFile(fileName) ->
                    editorState.openTab(TabItem(path, fileName, TabType.Archive))
                else -> editorState.openFileTab(path)
            }
        }
    }

    // 同步编辑器状态
    LaunchedEffect(Unit) {
        FileOperationEvents.events.collect { event ->
            when (event.operation) {
                "create" -> {
                    // 工具创建文件后自动打开并切换到对应标签页
                    val eventPath = event.path
                    val absolutePath = if (eventPath.startsWith("/")) eventPath
                        else "${fileManager.projectDirPath.trimEnd('/')}/${eventPath.trimStart('/')}"
                    if (java.io.File(absolutePath).isFile) {
                        editorState.openFileTab(absolutePath)
                    }
                    val modifiedPaths = editorState.tabs
                        .filter { it.type == TabType.File && editorState.isFileModified(it.id) }
                        .map { it.id }
                    chatViewModel.setModifiedFilePaths(modifiedPaths)
                }
                "modify", "overwrite" -> {
                    // 匹配 editorContent 中的 key：支持绝对/相对路径不一致
                    val eventPath = event.path
                    val matchedKey = editorState.editorContent.keys.firstOrNull { key ->
                        key == eventPath ||
                        key.endsWith("/$eventPath") ||
                        eventPath.endsWith("/$key") ||
                        // 如果一方是绝对路径另一方是相对路径，去掉项目根目录前缀后比较
                        (eventPath.startsWith("/") && key.startsWith("/")).not() &&
                        eventPath.removePrefix("/").let { eventRelative ->
                            key.removePrefix("/") == eventRelative
                        }
                    } ?: eventPath
                    if (matchedKey in editorState.editorContent) {
                        val newContent = editorState.readFileFromSource(matchedKey)
                        editorState.editorContent[matchedKey] = TextFieldValue(newContent)
                        editorState.originalContents[matchedKey] = newContent
                    }
                    val modifiedPaths = editorState.tabs
                        .filter { it.type == TabType.File && editorState.isFileModified(it.id) }
                        .map { it.id }
                    chatViewModel.setModifiedFilePaths(modifiedPaths)
                }
                "delete" -> {
                    // 路径模糊匹配：AI 工具传相对路径，但标签页 id 存绝对路径
                    val eventPath = event.path
                    val tabIdx = editorState.tabs.indexOfFirst { tab ->
                        tab.id == eventPath ||
                        tab.id.endsWith("/$eventPath") ||
                        eventPath.endsWith("/${tab.id.substringAfterLast('/')}") ||
                        tab.id.substringAfterLast('/') == eventPath.substringAfterLast('/')
                    }
                    if (tabIdx >= 0) editorState.forceCloseTab(tabIdx)
                    chatViewModel.setModifiedFilePaths(
                        editorState.tabs
                            .filter { it.type == TabType.File && editorState.isFileModified(it.id) }
                            .map { it.id }
                    )
                }
            }
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? -> uri?.let { chatViewModel.loadModelFromUri(it) } }

    val saveAsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("*/*")
    ) { uri: Uri? ->
        uri?.let { targetUri ->
            val tab = editorState.tabs.getOrNull(editorState.activeTabIndex) ?: return@let
            val content = editorState.editorContent[tab.id]?.text ?: return@let
            try { context.contentResolver.openOutputStream(targetUri)?.use { it.write(content.toByteArray(Charsets.UTF_8)) } } catch (_: Exception) {}
        }
    }

    /** 授权文件管理按钮：有权限直接打开，无权限跳系统设置 */
    val onOpenFolder: () -> Unit = {
        if (Environment.isExternalStorageManager()) {
            viewModel.openDirectStorage()
            viewModel.recordRecentFolder("/storage/emulated/0", "存储根目录")
            chatViewModel.setProjectRootPath("/storage/emulated/0", "存储根目录")
            selectedTab = SidebarTab.Explorer
        } else {
            try {
                val intent = android.content.Intent(
                    android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION
                ).apply {
                    data = android.net.Uri.parse("package:${context.packageName}")
                }
                context.startActivity(intent)
            } catch (_: android.content.ActivityNotFoundException) {
                // 部分设备不支持包名定向，降级到通用权限列表页
                try {
                    val fallback = android.content.Intent(
                        android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION
                    ).apply {
                        data = android.net.Uri.parse("package:${context.packageName}")
                    }
                    context.startActivity(fallback)
                } catch (e: Exception) {
                    android.util.Log.e("HomeScreen", "降级权限意图也失败", e)
                }
            } catch (e: Exception) {
                android.util.Log.e("HomeScreen", "打开系统权限设置失败", e)
            }
        }
    }

    // 检查是否已经在直接访问模式下打开了存储
    val isStorageActive = homeState.openedFolderName != null

    var showNewFileDialog by remember { mutableStateOf(false) }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var newFileName by remember { mutableStateOf("") }
    var newFolderName by remember { mutableStateOf("") }
    // 工具栏音乐播放
    var scannedAudioTracks by remember { mutableStateOf<List<com.medeide.jh.screens.home.landscape.topbar.audio.AudioTrack>>(emptyList()) }
    var audioScanRequested by remember { mutableStateOf(false) }
    var hasAudioPermission by remember {
        mutableStateOf(androidx.core.content.ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_MEDIA_AUDIO
        ) == PackageManager.PERMISSION_GRANTED)
    }
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasAudioPermission = granted
        if (granted) audioScanRequested = true
    }
    val audioScanScope = rememberCoroutineScope()
    LaunchedEffect(audioScanRequested) {
        if (audioScanRequested && hasAudioPermission && scannedAudioTracks.isEmpty()) {
            withContext(Dispatchers.IO) {
                scannedAudioTracks = com.medeide.jh.screens.home.landscape.topbar.audio.AudioPlaybackState.scanDeviceAudio(context)
            }
        }
    }
    fun playAudioTrack(track: com.medeide.jh.screens.home.landscape.topbar.audio.AudioTrack) {
        try {
            if (audioPlaybackState.exoPlayer == null) {
                val player = androidx.media3.exoplayer.ExoPlayer.Builder(context).build()
                player.addListener(object : androidx.media3.common.Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == androidx.media3.common.Player.STATE_ENDED) {
                            val pl = audioPlaybackState.playlist
                            val ci = audioPlaybackState.currentIndex
                            if (pl.size > 1) {
                                val next = (ci + 1) % pl.size
                                val nextTrack = pl[next]
                                playAudioTrack(nextTrack)
                            } else {
                                audioPlaybackState.isPlaying = false
                                audioPlaybackState.currentPosition = 0f
                                player.seekTo(0)
                            }
                        }
                    }
                })
                audioPlaybackState.exoPlayer = player
            }
            val uri = if (track.path.startsWith("content://")) android.net.Uri.parse(track.path) else android.net.Uri.fromFile(java.io.File(track.path))
            audioPlaybackState.exoPlayer?.apply {
                stop()
                clearMediaItems()
                setMediaItem(androidx.media3.common.MediaItem.fromUri(uri))
                prepare()
                play()
            }
            audioPlaybackState.currentAudioPath = track.path
            audioPlaybackState.currentSongName = track.name
            audioPlaybackState.isPlaying = true
            audioPlaybackState.playlist = scannedAudioTracks
            audioPlaybackState.currentIndex = scannedAudioTracks.indexOfFirst { it.path == track.path }.coerceAtLeast(0)
            audioScanScope.launch(Dispatchers.IO) {
                audioPlaybackState.lyrics = com.medeide.jh.screens.home.landscape.topbar.audio.LyricsParser.loadFromFile(context, track.path)
            }
        } catch (e: Exception) {
            audioPlaybackState.errorMsg = e.message
        }
    }
    val onPlayAudioTrack: (com.medeide.jh.screens.home.landscape.topbar.audio.AudioTrack) -> Unit = { playAudioTrack(it) }
    val onStopAudio: () -> Unit = {
        audioPlaybackState.release()
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
                audioPlaybackState = audioPlaybackState,
                scannedAudioTracks = scannedAudioTracks,
                onScanMusic = {
                    if (hasAudioPermission) audioScanRequested = true
                    else audioPermissionLauncher.launch(Manifest.permission.READ_MEDIA_AUDIO)
                },
                onPlayAudioTrack = onPlayAudioTrack,
                onStopAudio = onStopAudio,
                // 编辑菜单
                onUndo = { editorState.handleUndo() },
                onRedo = { editorState.handleRedo() },
                onCopyAll = { editorState.copyAllText(context) },
                onFindReplace = {
                    editorState.openSearchToolbar()
                    // 如果侧栏搜索面板未打开，不自动切换（保持当前面板）
                    // 查找替换工具栏已在编辑器右上角弹出
                },
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { innerPadding ->
        ThreeColumnLayout(
            sideIconBar = {
                Sidebar(
                    selectedTab = selectedTab,
                    onTabClick = { tab -> selectedTab = if (selectedTab == tab) null else tab },
                )
            },
            sidePanel = {
                LeftPanelContent(
                    selectedTab = selectedTab,
                    homeState = homeState,
                    files = files,
                    viewModel = viewModel,
                    chatViewModel = chatViewModel,
                    editorState = editorState,
                    fileManager = fileManager,
                    onFileClick = { fileItem ->
                        // 文本文件使用 filePath（相对/绝对均可，EditorScreenState 会处理)
                        // 非文本文件需要绝对路径或 content:// URI
                        val filePath = when {
                            fileItem.filePath.isNotEmpty() -> fileItem.filePath
                            fileItem.relativePath.isNotEmpty() -> fileItem.relativePath
                            else -> fileItem.uri.toString()
                        }
                        viewModel.recordRecentFile(filePath, fileItem.name)
                        // Web 文件在工作区以编辑+预览模式打开
                        val webExt = fileItem.name.substringAfterLast('.', "").lowercase()
                        if (webExt in setOf("html", "htm", "xhtml")) {
                            editorState.openPreviewTab(filePath, fileItem.name)
                            return@LeftPanelContent
                        }
                        when (FileTypeUtil.openMode(fileItem.name, fileItem.size)) {
                            FileOpenMode.IMAGE -> {
                                val id = if (fileItem.filePath.isNotEmpty()) fileItem.filePath else fileItem.uri.toString()
                                editorState.openTab(TabItem(id, fileItem.name, TabType.Image))
                            }
                            FileOpenMode.AUDIO -> {
                                val id = if (fileItem.filePath.isNotEmpty()) fileItem.filePath else fileItem.uri.toString()
                                editorState.openTab(TabItem(id, fileItem.name, TabType.Audio))
                            }
                            FileOpenMode.VIDEO -> {
                                val id = if (fileItem.filePath.isNotEmpty()) fileItem.filePath else fileItem.uri.toString()
                                editorState.openTab(TabItem(id, fileItem.name, TabType.Video))
                            }
                            FileOpenMode.ARCHIVE -> {
                                val id = if (fileItem.filePath.isNotEmpty()) fileItem.filePath else fileItem.uri.toString()
                                editorState.openTab(TabItem(id, fileItem.name, TabType.Archive))
                            }
                            FileOpenMode.TEXT -> {
                                editorState.editorContent.remove(filePath)
                                editorState.openFileTab(filePath, fileItem.name)
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
                            // 使用绝对路径或 content:// URI，确保 AI 可读取
                            val attachPath = when {
                                fileItem.filePath.isNotEmpty() -> fileItem.filePath
                                else -> fileItem.uri.toString()
                            }
                            chatViewModel.attachFile(attachPath, fileItem.name)
                        }
                    },
                    onOpenFileTab = { editorState.openFileTab(it) },
                    onCloseSearchPanel = { selectedTab = null },
                )
            },
            isSidePanelVisible = selectedTab != null,
            isSidePanelExpanded = selectedTab != SidebarTab.Search,
            workspaceContent = {
                MainContentArea(
                    chatViewModel = chatViewModel,
                    audioPlaybackState = audioPlaybackState,
                    videoPlaybackState = videoPlaybackState,
                    tabs = editorState.tabs,
                    activeTabIndex = editorState.activeTabIndex,
                    onSelectTab = { idx ->
                        val tab = editorState.tabs.getOrNull(idx)
                        if (tab != null) editorState.openTab(tab)
                    },
                    onCloseTab = { idx ->
                        val tab = editorState.tabs.getOrNull(idx) ?: return@MainContentArea
                        if (tab.type == TabType.Video) videoPlaybackState.release()
                        if (tab.id == editorState.settingsTabId) {
                            editorState.closeSettingsTab()
                            isSettingsOpen = false
                        } else {
                            editorState.closeTab(idx)
                        }
                    },
                    onSaveAndCloseTab = { idx ->
                        val tab = editorState.tabs.getOrNull(idx) ?: return@MainContentArea
                        if (tab.type == TabType.File) editorState.saveFile(tab.id)
                        editorState.closeTab(idx)
                        if (tab.type == TabType.Video) videoPlaybackState.release()
                    },
                    onForceCloseTab = { idx ->
                        val tab = editorState.tabs.getOrNull(idx) ?: return@MainContentArea
                        if (tab.type == TabType.Video) videoPlaybackState.release()
                        editorState.forceCloseTab(idx)
                    },
                    isFileModified = { path -> editorState.isFileModified(path) },
                    onCloseAllTabs = {
                        videoPlaybackState.release()
                        editorState.closeAllTabs(); isSettingsOpen = false; viewModel.saveOpenedTabs(emptyList())
                    },
                    onSaveCurrent = {
                        val tab = editorState.tabs.getOrNull(editorState.activeTabIndex)
                        if (tab?.type == TabType.File) editorState.saveFile(tab.id)
                    },
                    onSaveAllTabs = { editorState.tabs.filter { it.type == TabType.File }.forEach { editorState.saveFile(it.id) } },
                    previewModeTabs = previewModeTabs,
                    projectDirPath = homeState.projectDirPath,
                    onTogglePreviewMode = { path ->
                        previewModeTabs = if (path in previewModeTabs) previewModeTabs - path else previewModeTabs + path
                    },
                    getEditorContent = { path ->
                        editorState.editorContent.getOrPut(path) { TextFieldValue(editorState.readFileFromSource(path)) }
                    },
                    onPreviewContentChange = { path, tfv ->
                        editorState.handleTextChange(path, tfv)
                        val modified = editorState.tabs.filter { t -> t.type == TabType.File && editorState.isFileModified(t.id) }.map { it.id }
                        chatViewModel.setModifiedFilePaths(modified)
                    },
                    tabContent = { path ->
                        val tfv = editorState.editorContent.getOrPut(path) { TextFieldValue(editorState.readFileFromSource(path)) }

                        CodeEditor(
                            text = tfv,
                            onTextChange = {
                                editorState.onBeforeTextChange(path)
                                editorState.handleTextChange(path, it)
                                val modified = editorState.tabs.filter { t -> t.type == TabType.File && editorState.isFileModified(t.id) }.map { it.id }
                                chatViewModel.setModifiedFilePaths(modified)
                            },
                            modifier = Modifier.fillMaxSize(),
                            onAddToChat = { selectedText ->
                                val current = chatViewModel.state.value.inputText
                                chatViewModel.setInputText(if (current.isBlank()) selectedText else "$current\n\n$selectedText")
                            },
                            onCursorChange = { line, lineContent ->
                                cursorLine = line
                                cursorLineContent = lineContent
                            },
                        )
                    },
                    // 搜索替换相关
                    currentSearchMatches = editorState.currentSearchMatches,
                    currentSearchMatchIndex = editorState.currentSearchMatchIndex,
                    onSearchNavUp = {
                        val matches = editorState.currentSearchMatches
                        if (matches.isEmpty()) return@MainContentArea
                        val currentIdx = editorState.currentSearchMatchIndex.coerceIn(0, matches.size - 1)
                        val newIdx = if (currentIdx <= 0) matches.size - 1 else currentIdx - 1
                        editorState.currentSearchMatchIndex = newIdx
                        val match = matches[newIdx]
                        val absPath = if (match.filePath.startsWith("/")) match.filePath
                            else "${homeState.projectDirPath.trimEnd('/')}/${match.filePath}"
                        editorState.openFileAtLine(absPath, match.lineNumber)
                    },
                    onSearchNavDown = {
                        val matches = editorState.currentSearchMatches
                        if (matches.isEmpty()) return@MainContentArea
                        val currentIdx = editorState.currentSearchMatchIndex.coerceIn(0, matches.size - 1)
                        val newIdx = if (currentIdx >= matches.size - 1) 0 else currentIdx + 1
                        editorState.currentSearchMatchIndex = newIdx
                        val match = matches[newIdx]
                        val absPath = if (match.filePath.startsWith("/")) match.filePath
                            else "${homeState.projectDirPath.trimEnd('/')}/${match.filePath}"
                        editorState.openFileAtLine(absPath, match.lineNumber)
                    },
                    onReplaceCurrent = { filePath ->
                        val matches = editorState.currentSearchMatches
                        val idx = editorState.currentSearchMatchIndex
                        if (idx !in matches.indices) return@MainContentArea
                        val match = matches[idx]
                        val absPath = if (match.filePath.startsWith("/")) match.filePath
                            else "${homeState.projectDirPath.trimEnd('/')}/${match.filePath}"
                        val content = editorState.editorContent[absPath]?.text ?: return@MainContentArea
                        val lines = content.lines().toMutableList()
                        if (match.lineNumber - 1 in lines.indices) {
                            val lineIdx = match.lineNumber - 1
                            val oldLine = lines[lineIdx]
                            val searchQuery = editorState.currentSearchQuery
                            val newLine = if (searchQuery.isNotEmpty()) {
                                oldLine.replaceFirst(searchQuery, editorState.currentReplaceText, ignoreCase = true)
                            } else oldLine
                            lines[lineIdx] = newLine
                            val newContent = lines.joinToString("\n")
                            editorState.editorContent[absPath] = TextFieldValue(newContent)
                            editorState.currentSearchMatchIndex = (idx + 1).coerceAtMost(matches.size - 1)
                        }
                    },
                    // 查找替换工具栏参数
                    isSearchToolbarVisible = editorState.isSearchToolbarVisible,
                    toolbarSearchQuery = editorState.currentSearchQuery,
                    toolbarReplaceText = editorState.currentReplaceText,
                    onToolbarSearchQueryChange = { editorState.currentSearchQuery = it },
                    onToolbarReplaceTextChange = { editorState.currentReplaceText = it },
                    onCloseSearchToolbar = { editorState.isSearchToolbarVisible = false },
                    onClearSearch = {
                        editorState.currentSearchQuery = ""
                        editorState.currentReplaceText = ""
                        editorState.currentSearchMatches = emptyList()
                        editorState.persistentSearchResults = emptyList()
                    },
                )
            },
            collabPanel = {
                CollabPanel(
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

    val dialogMaxHeight = with(LocalConfiguration.current) { (screenHeightDp.dp * 0.75f).coerceAtLeast(200.dp) }

    if (showNewFileDialog) {
        AlertDialog(
            onDismissRequest = { showNewFileDialog = false },
            title = { Text(stringResource(R.string.dialog_new_file_title)) },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = dialogMaxHeight)
                        .verticalScroll(rememberScrollState()),
                ) {
                    OutlinedTextField(value = newFileName, onValueChange = { newFileName = it }, placeholder = { Text(stringResource(R.string.dialog_new_file_name_hint)) }, singleLine = true)
                }
            },
            confirmButton = { TextButton(onClick = { val name = newFileName.trim(); if (name.isNotEmpty()) { viewModel.createFile("", name, false); selectedTab = SidebarTab.Explorer; showNewFileDialog = false } }) { Text(stringResource(R.string.dialog_confirm)) } },
            dismissButton = { TextButton(onClick = { showNewFileDialog = false }) { Text(stringResource(R.string.chat_cancel)) } },
        )
    }
    if (showNewFolderDialog) {
        AlertDialog(
            onDismissRequest = { showNewFolderDialog = false },
            title = { Text(stringResource(R.string.dialog_new_folder_title)) },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = dialogMaxHeight)
                        .verticalScroll(rememberScrollState()),
                ) {
                    OutlinedTextField(value = newFolderName, onValueChange = { newFolderName = it }, placeholder = { Text(stringResource(R.string.dialog_new_folder_name_hint)) }, singleLine = true)
                }
            },
            confirmButton = { TextButton(onClick = { val name = newFolderName.trim(); if (name.isNotEmpty()) { viewModel.createFile("", name, true); selectedTab = SidebarTab.Explorer; showNewFolderDialog = false } }) { Text(stringResource(R.string.dialog_save)) } },
            dismissButton = { TextButton(onClick = { showNewFolderDialog = false }) { Text(stringResource(R.string.chat_cancel)) } },
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
    fileManager: FileManager,
    onFileClick: (FileItem) -> Unit = {},
    onAddToConversation: (FileItem) -> Unit = {},
    onOpenFileTab: (String) -> Unit = {},
    onCloseSearchPanel: () -> Unit = {},
) {
    when (selectedTab) {
        null -> {}
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
                    viewModel.renameFile(relativePath, newName)
                },
                onDelete = { relativePath ->
                    viewModel.deleteFile(relativePath)
                },
                onCreate = { relativePath, name, isDir ->
                    viewModel.createFile(relativePath, name, isDir)
                },
                onCopy = { srcPath, dstDirPath ->
                    viewModel.copyFile(srcPath, dstDirPath)
                },
                onMove = { srcPath, dstDirPath ->
                    viewModel.moveFile(srcPath, dstDirPath)
                },
                onCompress = { paths, archiveName, format, level, password ->
                    viewModel.compressFiles(paths, archiveName, format, level, password)
                },
                onDiff = { oldPath, newPath ->
                    editorState.openDiffView(oldPath, newPath)
                },
                onMerge = { oldPath, newPath ->
                    editorState.mergeFiles(oldPath, newPath)
                },
                onOpenAsProject = { filePath ->
                    viewModel.openAsProjectDirectory(filePath)
                },
                projectDirPath = homeState.projectDirPath,
            )
        }
        SidebarTab.Search -> {
            SearchReplacePanel(
                fileManager = fileManager,
                editorState = editorState,
                onClosePanel = onCloseSearchPanel,
            )
        }
    }
}
