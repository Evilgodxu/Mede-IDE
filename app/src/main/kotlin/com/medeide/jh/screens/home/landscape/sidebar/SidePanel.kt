package com.medeide.jh.screens.home.landscape.sidebar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.medeide.jh.screens.home.filebrowser.FileBrowserScreen
import com.medeide.jh.screens.home.landscape.sidebar.searchreplacepanel.SearchReplacePanel
import com.medeide.jh.screens.home.landscape.sidebar.searchreplacepanel.SearchReplaceState

@Composable
fun SidePanel(
    selectedTab: SidebarTab?,
    fileBrowserRootPath: String = "",
    onOpenFile: (String, Int) -> Unit = { _, _ -> },
    onFilesDeleted: (Set<String>) -> Unit = {},
    onAddToConversation: (String) -> Unit = {},
    onOpenAsProject: (String) -> Unit = {},
    onExitProjectMode: () -> Unit = {},
    isProjectModeActive: Boolean = false,
    searchState: SearchReplaceState? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        when (selectedTab) {
            SidebarTab.Explorer -> {
                if (fileBrowserRootPath.isNotBlank()) {
                    FileBrowserScreen(
                        rootPath = fileBrowserRootPath,
                        columnCount = 2,
                        onOpenFile = { path -> onOpenFile(path, 1) },
                        onFilesDeleted = onFilesDeleted,
                        onAddToConversation = onAddToConversation,
                        onOpenAsProject = onOpenAsProject,
                        onExitProjectMode = onExitProjectMode,
                        isProjectModeActive = isProjectModeActive,
                    )
                } else {
                    Text(
                        text = "未选择路径",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
            SidebarTab.Search -> {
                Header("搜索替换")
                if (searchState != null && fileBrowserRootPath.isNotBlank()) {
                    SearchReplacePanel(
                        rootPath = fileBrowserRootPath,
                        state = searchState,
                        onOpenFile = onOpenFile,
                    )
                } else {
                    Text(
                        text = "未选择路径",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
            null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "选择左侧图标打开面板",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                }
            }
        }
    }
}

@Composable
private fun Header(title: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
    HorizontalDivider(
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
    )
}
