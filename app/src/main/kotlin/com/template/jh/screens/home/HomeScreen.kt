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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.window.core.layout.WindowSizeClass
import com.template.jh.R
import com.template.jh.ui.adaptive.rememberWindowSizeClass
import com.template.jh.core.ai.ChatViewModel
import com.template.jh.screens.home.components.ThreeColumnLayout
import com.template.jh.screens.home.components.Sidebar
import com.template.jh.screens.home.components.SidebarTab
import com.template.jh.screens.home.components.ResourcePanel
import com.template.jh.screens.home.components.MainContentArea
import com.template.jh.screens.home.components.AIChatPanel
import com.template.jh.screens.home.components.MainTopBar
import com.template.jh.screens.home.components.SearchPanel
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

    // SAF 文件选择器（必须放在可直接访问 Activity 的 Composable 层级）
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { chatViewModel.loadModelFromUri(it) }
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
                    selectedTab = selectedTab
                )
            },
            isLeftPanelVisible = selectedTab != null,
            centerContent = {
                MainContentArea(
                    isSettingsOpen = isSettingsOpen,
                    onCloseSettings = { isSettingsOpen = false },
                    onOpenFolder = {},
                    onNewProject = {},
                    onCloneGit = {},
                    chatViewModel = chatViewModel,
                    onBrowseModelFile = {
                        filePickerLauncher.launch(arrayOf("application/octet-stream", "*/*"))
                    },
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
    selectedTab: SidebarTab?
) {
    when (selectedTab) {
        SidebarTab.Explorer -> {
            ResourcePanel(
                onOpenFolder = {}
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
        null -> {
            // 不显示任何内容
        }
    }
}

@Composable
private fun SourceControlPanel() {
    androidx.compose.foundation.layout.Box(
        modifier = androidx.compose.ui.Modifier.fillMaxSize(),
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
        modifier = androidx.compose.ui.Modifier.fillMaxSize(),
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
        modifier = androidx.compose.ui.Modifier.fillMaxSize(),
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        androidx.compose.material3.Text(
            text = stringResource(R.string.extensions),
            style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
