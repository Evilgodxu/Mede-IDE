package com.medeide.jh.screens.home

import android.Manifest
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.window.core.layout.WindowSizeClass
import com.medeide.jh.screens.home.landscape.HomeLandscapeScreen
import com.medeide.jh.screens.home.landscape.collab.CollabPanel
import com.medeide.jh.screens.home.landscape.sidebar.SidePanel
import com.medeide.jh.screens.home.landscape.sidebar.Sidebar
import com.medeide.jh.screens.home.landscape.sidebar.SidebarTab
import com.medeide.jh.screens.home.landscape.sidebar.searchreplacepanel.rememberSearchReplaceState
import com.medeide.jh.screens.home.landscape.topbar.MainTopBar
import com.medeide.jh.screens.home.landscape.topbar.audioplayer.AudioPlaybackState
import com.medeide.jh.core.model.AudioTrack
import com.medeide.jh.screens.home.landscape.topbar.audioplayer.PlayMode
import com.medeide.jh.screens.home.landscape.workspace.MainContentArea
import com.medeide.jh.core.model.TabItem
import com.medeide.jh.core.model.TabType
import com.medeide.jh.core.model.displayNameFromPath
import com.medeide.jh.core.utils.fileTypeForPath
import com.medeide.jh.screens.home.portrait.HomePortraitScreen
import com.medeide.jh.screens.home.portrait.topbar.PortraitTopBar
import com.medeide.jh.screens.permission.LocalActivity
import com.medeide.jh.ui.adaptive.rememberWindowSizeClass
import com.medeide.jh.core.data.repository.UserPreferencesRepository
import com.medeide.jh.core.data.source.local.LiteRTEngineManager
import com.medeide.jh.core.data.source.local.LiteRTModelRepository
import com.medeide.jh.model.chat.EngineStatus
import com.medeide.jh.screens.home.cloudchat.CloudChatViewModel
import com.medeide.jh.screens.home.localchat.LocalModelInfo
import com.medeide.jh.screens.home.recent.RecentFileEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import java.io.File
import kotlin.random.Random

@Composable
fun HomeScreen() {
    val windowSizeClass = rememberWindowSizeClass()
    val isLandscape = windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)
    val userPrefs: UserPreferencesRepository = koinInject()
    val userProfile by userPrefs.userProfile.collectAsState(initial = com.medeide.jh.model.chat.UserProfile())
    val savedMode by userPrefs.ideMode.collectAsState(initial = null)
    var isIdeMode by rememberSaveable { mutableStateOf(isLandscape) }
    // DataStore 有保存值时覆盖初始值
    LaunchedEffect(savedMode) {
        savedMode?.let { isIdeMode = it }
    }
    val activity = LocalActivity.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val chatViewModel: CloudChatViewModel = koinViewModel()
    val homeViewModel: HomeViewModel = koinViewModel()
    val modelRepository: LiteRTModelRepository = koinInject()
    val engineManager: LiteRTEngineManager = koinInject()

    // ── 本地模型状态 ──
    var localModels by remember { mutableStateOf<List<LocalModelInfo>>(emptyList()) }
    var localEngineStatus by remember { mutableStateOf(EngineStatus.Idle) }
    var localModelName by remember { mutableStateOf("") }

    val onScanModels: () -> Unit = {
        scope.launch(Dispatchers.IO) {
            val files = modelRepository.scanDownloadedModels()
            val models = files.map { file ->
                val displayName = file.nameWithoutExtension
                    .replace("-", " ")
                    .replace("_", " ")
                LocalModelInfo(
                    fileName = file.name,
                    displayName = displayName,
                    sizeBytes = file.length(),
                    path = file.absolutePath,
                )
            }
            localModels = models
        }
    }
    val onLoadModel: (String) -> Unit = { path ->
        localEngineStatus = EngineStatus.Loading
        scope.launch(Dispatchers.IO) {
            val result = engineManager.loadModel(path)
            result.onSuccess {
                localEngineStatus = EngineStatus.Ready
                localModelName = File(path).nameWithoutExtension
                    .replace("-", " ")
                    .replace("_", " ")
                    .take(16)
            }.onFailure {
                localEngineStatus = EngineStatus.Error
                localModelName = ""
            }
        }
    }

    // 文件选择器：浏览模型文件
    val modelFilePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            localEngineStatus = EngineStatus.Loading
            scope.launch(Dispatchers.IO) {
                try {
                    val input = context.contentResolver.openInputStream(it)
                    val fileName = "imported_${System.currentTimeMillis()}.litertlm"
                    val destDir = File(context.filesDir, "litertlm_models")
                    destDir.mkdirs()
                    val dest = File(destDir, fileName)
                    input?.use { src -> dest.outputStream().use { dst -> src.copyTo(dst) } }
                    onLoadModel(dest.absolutePath)
                } catch (e: Exception) {
                    localEngineStatus = EngineStatus.Error
                }
            }
        }
    }

    // 生命周期感知：回到桌面时暂停/释放播放器，返回时恢复
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    homeViewModel.audioPlaybackState.exoPlayer?.pause()
                    homeViewModel.audioPlaybackState.isPlaying = false
                    homeViewModel.workspaceVideoState.mediaPlayer?.pause()
                    homeViewModel.workspaceVideoState.isPlaying = false
                    homeViewModel.workspaceAudioState.exoPlayer?.pause()
                    homeViewModel.workspaceAudioState.isPlaying = false
                }
                Lifecycle.Event.ON_DESTROY -> {
                    homeViewModel.audioPlaybackState.release()
                    homeViewModel.workspaceVideoState.release()
                    homeViewModel.workspaceAudioState.release()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(isIdeMode) {
        activity.requestedOrientation = if (isIdeMode) {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        onDispose {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    var selectedTab by remember { mutableStateOf<SidebarTab?>(null) }
    var isHistoryOpen by rememberSaveable { mutableStateOf(false) }
    var isDashboardOpen by rememberSaveable { mutableStateOf(false) }
    var isFileBrowserOpen by rememberSaveable { mutableStateOf(false) }
    var isSettingsOpen by rememberSaveable { mutableStateOf(false) }
    var lastBackPressed by remember { mutableLongStateOf(0L) }
    val audioPlaybackState = homeViewModel.audioPlaybackState
    var scannedAudioTracks by remember { mutableStateOf<List<AudioTrack>>(emptyList()) }
    var scanned by remember { mutableStateOf(false) }

    BackHandler {
        when {
            isHistoryOpen || isDashboardOpen || isFileBrowserOpen || isSettingsOpen -> {
                isHistoryOpen = false
                isDashboardOpen = false
                isFileBrowserOpen = false
                isSettingsOpen = false
            }
            System.currentTimeMillis() - lastBackPressed > 2000 -> {
                lastBackPressed = System.currentTimeMillis()
                Toast.makeText(context, "再按一次返回桌面", Toast.LENGTH_SHORT).show()
            }
            else -> activity.finish()
        }
    }

    // 工作区 Tab 状态
    var workspaceTabs by remember { mutableStateOf<List<TabItem>>(emptyList()) }
    var workspaceActiveIndex by remember { mutableIntStateOf(-1) }
    var openFileLineRequest by remember { mutableStateOf<Pair<String, Int>?>(null) }
    val searchState = rememberSearchReplaceState()

    // 文件管理器根路径（进入工作模式时可切换为项目目录）
    val storageRoot = remember { Environment.getExternalStorageDirectory().absolutePath }
    var fileBrowserRoot by remember { mutableStateOf(storageRoot) }

    // 最近文件列表
    val recentFiles by userPrefs.recentFiles.collectAsState(initial = emptyList())

    // 音乐权限请求
    val audioPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && !scanned) {
            scanned = true
            scope.launch { scannedAudioTracks = AudioPlaybackState.scanDeviceAudio(context) }
        }
    }

    val chatState by chatViewModel.state.collectAsState()

    // 跟踪编辑器/工作目录状态变化，更新实时上下文
    val isProjectMode = fileBrowserRoot != storageRoot
    LaunchedEffect(workspaceTabs, workspaceActiveIndex, isIdeMode, fileBrowserRoot) {
        val ctx = buildString {
            val now = java.time.LocalDateTime.now()
            appendLine("[实时状态]")
            appendLine("当前时间: ${now.year}-${"%02d".format(now.monthValue)}-${"%02d".format(now.dayOfMonth)} ${"%02d".format(now.hour)}:${"%02d".format(now.minute)}")
            if (isProjectMode) {
                appendLine("工作目录: $fileBrowserRoot")
            }
            if (isIdeMode) {
                val active = workspaceActiveIndex.takeIf { it >= 0 && it < workspaceTabs.size }?.let { workspaceTabs[it] }
                if (active != null) {
                    appendLine("编辑中文件: ${active.id}")
                }
            }
        }
        chatViewModel.setEditorContext(ctx.toString().trimEnd())
        chatViewModel.setProjectRoot(if (isProjectMode) fileBrowserRoot else "")
    }

    Scaffold(
        topBar = {
            if (isIdeMode) {
                MainTopBar(
                    isIdeMode = isIdeMode,
                    onToggleLayoutMode = {
                        isIdeMode = false
                        scope.launch { userPrefs.setIdeMode(false) }
                    },
                    audioPlaybackState = audioPlaybackState,
                    scannedAudioTracks = scannedAudioTracks,
                    onScanMusic = {
                        if (scanned) return@MainTopBar
                        if (ContextCompat.checkSelfPermission(context, audioPermission)
                            == PackageManager.PERMISSION_GRANTED
                        ) {
                            scanned = true
                            scope.launch {
                                scannedAudioTracks = AudioPlaybackState.scanDeviceAudio(context)
                            }
                        } else {
                            permissionLauncher.launch(audioPermission)
                        }
                    },
                    onPlayAudioTrack = { track ->
                        if (track.path == audioPlaybackState.currentAudioPath) return@MainTopBar
                        audioPlaybackState.release()
                        audioPlaybackState.playlist = scannedAudioTracks
                        audioPlaybackState.currentIndex = scannedAudioTracks.indexOfFirst { it.path == track.path }.coerceAtLeast(0)
                        audioPlaybackState.currentAudioPath = track.path
                        audioPlaybackState.currentSongName = track.name

                        val player = ExoPlayer.Builder(context).build()
                        audioPlaybackState.exoPlayer = player
                        val uri = if (track.path.startsWith("content://")) Uri.parse(track.path) else Uri.fromFile(File(track.path))
                        player.setMediaItem(MediaItem.fromUri(uri))
                        player.prepare()
                        player.play()
                        player.addListener(object : Player.Listener {
                            override fun onIsPlayingChanged(isPlaying: Boolean) {
                                audioPlaybackState.isPlaying = isPlaying
                            }
                            override fun onPlaybackStateChanged(playbackState: Int) {
                                when (playbackState) {
                                    Player.STATE_READY -> {
                                        audioPlaybackState.isPrepared = true
                                        audioPlaybackState.duration = player.duration.coerceAtLeast(0L).toFloat()
                                    }
                                    Player.STATE_ENDED -> {
                                        val pl = audioPlaybackState.playlist
                                        val ci = audioPlaybackState.currentIndex
                                        val next = when (audioPlaybackState.playMode) {
                                            PlayMode.RepeatOne -> ci // 同一首
                                            PlayMode.RepeatAll -> if (pl.size > 1) (ci + 1) % pl.size else ci
                                            PlayMode.Shuffle -> if (pl.size > 1) {
                                                var n: Int; do { n = Random.nextInt(pl.size) } while (n == ci && pl.size > 1); n
                                            } else ci
                                        }
                                        if (next == ci || pl.size <= 1) {
                                            // 同一首或仅一首：直接 seek
                                            player.seekTo(0)
                                            player.play()
                                        } else {
                                            val nextTrack = pl[next]
                                            audioPlaybackState.currentIndex = next
                                            audioPlaybackState.currentSongName = nextTrack.name
                                            audioPlaybackState.currentAudioPath = nextTrack.path
                                            audioPlaybackState.currentPosition = 0f
                                            audioPlaybackState.lyrics = emptyList()
                                            audioPlaybackState.currentLyricIndex = -1
                                            val uri2 = Uri.fromFile(File(nextTrack.path))
                                            player.stop()
                                            player.clearMediaItems()
                                            player.setMediaItem(MediaItem.fromUri(uri2))
                                            player.prepare()
                                            player.play()
                                        }
                                    }
                                }
                            }
                        })
                    },
                    onStopAudio = { audioPlaybackState.release() },
                    onFindReplace = { searchState.isToolbarVisible = true },
                    onTerminal = {
                        val tab = TabItem(id = "terminal", title = "终端", type = TabType.Terminal)
                        val existIdx = workspaceTabs.indexOfFirst { it.id == "terminal" }
                        if (existIdx >= 0) {
                            workspaceActiveIndex = existIdx
                        } else {
                            workspaceTabs = workspaceTabs + tab
                            workspaceActiveIndex = workspaceTabs.size - 1
                        }
                    },
                    recentFiles = recentFiles,
                    onOpenRecentFile = { entry ->
                        fileBrowserRoot = entry.path
                        scope.launch { userPrefs.addRecentFile(entry) }
                    },
                    cloudProfiles = chatState.cloudModelProfiles,
                    activeCloudProfileId = chatState.activeCloudProfileId,
                    cloudModelEnabled = chatState.cloudModelEnabled,
                    engineStatus = localEngineStatus,
                    modelName = localModelName,
                    availableModels = localModels,
                    onScanModels = onScanModels,
                    onLoadModel = onLoadModel,
                    onSwitchCloudProfile = { chatViewModel.switchCloudProfile(it) },
                    onBrowseModelFile = { modelFilePickerLauncher.launch(arrayOf("application/octet-stream", "*/*")) },
                )
            } else {
                PortraitTopBar(
                    isIdeMode = isIdeMode,
                    onToggleLayoutMode = {
                        isIdeMode = true
                        scope.launch { userPrefs.setIdeMode(true) }
                    },
                    onNewConversation = {
                        isHistoryOpen = false
                        isDashboardOpen = false
                        isFileBrowserOpen = false
                        isSettingsOpen = false
                        chatViewModel.newConversation()
                    },
                    onHistory = {
                        isHistoryOpen = !isHistoryOpen
                        isDashboardOpen = false
                        isFileBrowserOpen = false
                        isSettingsOpen = false
                    },
                    onDashboard = {
                        isDashboardOpen = !isDashboardOpen
                        isHistoryOpen = false
                        isFileBrowserOpen = false
                        isSettingsOpen = false
                    },
                    onFileBrowser = {
                        isFileBrowserOpen = !isFileBrowserOpen
                        isHistoryOpen = false
                        isDashboardOpen = false
                        isSettingsOpen = false
                    },
                    onSettings = {
                        isSettingsOpen = !isSettingsOpen
                        isHistoryOpen = false
                        isDashboardOpen = false
                        isFileBrowserOpen = false
                    },
                )
            }
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { innerPadding ->
        if (isIdeMode) {
            HomeLandscapeScreen(
                sideIconBar = {
                    Sidebar(
                        selectedTab = selectedTab,
                        onTabClick = { tab ->
                            selectedTab = if (selectedTab == tab) null else tab
                        }
                    )
                },
                sidePanel = {
                    SidePanel(
                        selectedTab = selectedTab,
                        fileBrowserRootPath = if (selectedTab in listOf(SidebarTab.Explorer, SidebarTab.Search)) fileBrowserRoot else "",
                        onOpenFile = { path, line ->
                            val type = fileTypeForPath(path)
                            if (type == null) {
                                Toast.makeText(context, "不支持打开此文件类型", Toast.LENGTH_SHORT).show()
                                return@SidePanel
                            }
                            val title = displayNameFromPath(path)
                            val tab = TabItem(id = path, title = title, type = type)
                            val existIdx = workspaceTabs.indexOfFirst { it.id == path }
                            workspaceTabs = if (existIdx >= 0) {
                                workspaceActiveIndex = existIdx
                                workspaceTabs
                            } else {
                                workspaceTabs + tab
                            }
                            workspaceActiveIndex = if (existIdx >= 0) existIdx else workspaceTabs.size - 1
                            if (line > 1) {
                                openFileLineRequest = path to line
                            }
                        },
                        onFilesDeleted = { deletedPaths ->
                            val newTabs = workspaceTabs.filter { it.id !in deletedPaths }
                            if (newTabs.size < workspaceTabs.size) {
                                workspaceTabs = newTabs
                                workspaceActiveIndex = workspaceActiveIndex.coerceAtMost(newTabs.size - 1)
                            }
                        },
                        onAddToConversation = { path -> chatViewModel.attachFile(path) },
                        onOpenAsProject = { path ->
                            fileBrowserRoot = path
                            scope.launch {
                                userPrefs.addRecentFile(RecentFileEntry(path = path, name = File(path).name))
                            }
                        },
                        onExitProjectMode = { fileBrowserRoot = storageRoot },
                        isProjectModeActive = fileBrowserRoot != storageRoot,
                        searchState = searchState,
                    )
                },
                isSidePanelVisible = selectedTab != null,
                sidePanelWidth = when (selectedTab) {
                    SidebarTab.Search -> 180.dp
                    else -> 360.dp
                },
                workspaceContent = {
                    MainContentArea(
                        tabs = workspaceTabs,
                        activeTabIndex = workspaceActiveIndex,
                        onSelectTab = { workspaceActiveIndex = it },
                        onCloseTab = { idx ->
                            val newTabs = workspaceTabs.toMutableList().apply { removeAt(idx) }
                            workspaceTabs = newTabs
                            if (workspaceActiveIndex >= newTabs.size) {
                                workspaceActiveIndex = (newTabs.size - 1).coerceAtLeast(-1)
                            }
                        },
                        onAddTab = { tab ->
                            val existIdx = workspaceTabs.indexOfFirst { it.id == tab.id }
                            if (existIdx >= 0) {
                                workspaceActiveIndex = existIdx
                            } else {
                                workspaceTabs = workspaceTabs + tab
                                workspaceActiveIndex = workspaceTabs.size - 1
                            }
                        },
                        onForceCloseTab = { idx ->
                            val newTabs = workspaceTabs.toMutableList().apply { removeAt(idx) }
                            workspaceTabs = newTabs
                            if (workspaceActiveIndex >= newTabs.size) {
                                workspaceActiveIndex = (newTabs.size - 1).coerceAtLeast(-1)
                            }
                        },
                        onSaveAndCloseTab = { idx ->
                            val newTabs = workspaceTabs.toMutableList().apply { removeAt(idx) }
                            workspaceTabs = newTabs
                            if (workspaceActiveIndex >= newTabs.size) {
                                workspaceActiveIndex = (newTabs.size - 1).coerceAtLeast(-1)
                            }
                        },
                        searchState = searchState,
                        openFileLineRequest = openFileLineRequest,
                        onOpenFileLineRequestHandled = { openFileLineRequest = null },
                        videoPlaybackState = homeViewModel.workspaceVideoState,
                        audioPlaybackState = homeViewModel.workspaceAudioState,
                    )
                },
                collabPanel = {
                    CollabPanel(
                        viewModel = chatViewModel,
                        userName = userProfile.userName,
                        agentName = userProfile.agentName,
                        onSettings = {
                            val tab = TabItem(id = "settings", title = "IDE 设置", type = TabType.Settings)
                            val existIdx = workspaceTabs.indexOfFirst { it.id == "settings" }
                            if (existIdx >= 0) {
                                workspaceActiveIndex = existIdx
                            } else {
                                workspaceTabs = workspaceTabs + tab
                                workspaceActiveIndex = workspaceTabs.size - 1
                            }
                        },
                    )
                },
                modifier = Modifier
                    .fillMaxSize()
                    .consumeWindowInsets(innerPadding)
                    .padding(innerPadding)
            )
        } else {
            HomePortraitScreen(
                chatViewModel = chatViewModel,
                isHistoryOpen = isHistoryOpen,
                onHistoryDismiss = { isHistoryOpen = false },
                isDashboardOpen = isDashboardOpen,
                onDashboardDismiss = { isDashboardOpen = false },
                isFileBrowserOpen = isFileBrowserOpen,
                onFileBrowserDismiss = { isFileBrowserOpen = false },
                isSettingsOpen = isSettingsOpen,
                onSettingsDismiss = { isSettingsOpen = false },
                onSettingsOpen = { isSettingsOpen = true },
                fileBrowserRoot = fileBrowserRoot,
                onAddToConversation = { path -> chatViewModel.attachFile(path) },
                onOpenAsProject = { path ->
                    fileBrowserRoot = path
                    scope.launch {
                        userPrefs.addRecentFile(RecentFileEntry(path = path, name = File(path).name))
                    }
                },
                onExitProjectMode = { fileBrowserRoot = storageRoot },
                isProjectModeActive = fileBrowserRoot != storageRoot,
                userName = userProfile.userName,
                agentName = userProfile.agentName,
                userAvatarUri = userProfile.userAvatarUri,
                agentAvatarUri = userProfile.agentAvatarUri,
                modifier = Modifier
                    .fillMaxSize()
                    .consumeWindowInsets(innerPadding)
                    .padding(innerPadding)
            )
        }
    }
}
