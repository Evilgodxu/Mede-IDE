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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.window.core.layout.WindowSizeClass
import com.template.jh.R
import com.template.jh.core.ai.ChatViewModel
import com.template.jh.ui.adaptive.rememberWindowSizeClass
import com.template.jh.screens.home.components.AIChatPanel
import com.template.jh.screens.home.components.MainContentArea
import com.template.jh.screens.home.components.MainTopBar
import com.template.jh.screens.home.components.ResourcePanel
import com.template.jh.screens.home.components.SearchPanel
import com.template.jh.screens.home.components.Sidebar
import com.template.jh.screens.home.components.SidebarTab
import com.template.jh.screens.home.components.ThreeColumnLayout
import org.koin.androidx.compose.koinViewModel

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
            selectedTab = SidebarTab.Explorer
        }
    }

    Scaffold(
        topBar = {
            MainTopBar(
                windowSizeClass = windowSizeClass,
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
                )
            },
            isLeftPanelVisible = selectedTab != null,
            centerContent = {
                MainContentArea(
                    isSettingsOpen = isSettingsOpen,
                    onCloseSettings = { isSettingsOpen = false },
                    onOpenFolder = { folderPickerLauncher.launch(null) },
                    onNewProject = {},
                    onCloneGit = {},
                    chatViewModel = chatViewModel,
                    onBrowseModelFile = {
                        filePickerLauncher.launch(arrayOf("application/octet-stream", "*/*"))
                    },
                    openedFolderName = homeState.openedFolderName,
                )
            },
            rightPanel = {
                AIChatPanel(
                    onSettingsClick = { isSettingsOpen = true },
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
) {
    when (selectedTab) {
        SidebarTab.Explorer -> {
            ResourcePanel(
                openedFolderName = homeState.openedFolderName,
                files = files,
                onListChildren = { uri, callback ->
                    viewModel.listChildren(uri, callback)
                },
            )
        }
        SidebarTab.Search -> {
            SearchPanel()
        }
        SidebarTab.SourceControl -> {
            SourceControlPanel()
        }
        SidebarTab.Preview -> {
            PreviewPanel()
        }
        SidebarTab.Extensions -> {
            ExtensionsPanel()
        }
        null -> { /* 不显示 */ }
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
