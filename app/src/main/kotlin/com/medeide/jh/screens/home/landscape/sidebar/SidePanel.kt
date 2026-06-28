package com.medeide.jh.screens.home.landscape.sidebar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.medeide.jh.screens.home.filebrowser.FileBrowserScreen
import com.medeide.jh.screens.home.landscape.sidebar.searchreplacepanel.SearchReplacePanel
import com.medeide.jh.screens.home.landscape.sidebar.searchreplacepanel.SearchReplaceState
import com.medeide.jh.core.data.repository.UserPreferencesRepository
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext

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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var recentEntries by remember { mutableStateOf<List<com.medeide.jh.screens.home.recent.RecentFileEntry>>(emptyList()) }
    var showRecentDialog by remember { mutableStateOf(false) }
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
                        onOpenFile = { path ->
                            onOpenFile(path, 1)
                            scope.launch {
                                try {
                                    val repo = UserPreferencesRepository(context)
                                    val name = path.substringAfterLast('/')
                                    repo.addRecentFile(
                                        com.medeide.jh.screens.home.recent.RecentFileEntry(
                                            path = path,
                                            name = name,
                                            isDirectory = false,
                                            lastOpenedTime = System.currentTimeMillis(),
                                        )
                                    )
                                } catch (_: Exception) {}
                            }
                        },
                        onFilesDeleted = onFilesDeleted,
                        onAddToConversation = onAddToConversation,
                        onOpenAsProject = onOpenAsProject,
                        onExitProjectMode = onExitProjectMode,
                        isProjectModeActive = isProjectModeActive,
                        onNavigateTo = { path ->
                            scope.launch {
                                try {
                                    val repo = UserPreferencesRepository(context)
                                    val name = path.substringAfterLast('/')
                                    repo.addRecentFile(
                                        com.medeide.jh.screens.home.recent.RecentFileEntry(
                                            path = path,
                                            name = name,
                                            isDirectory = true,
                                            lastOpenedTime = System.currentTimeMillis(),
                                        )
                                    )
                                } catch (_: Exception) {}
                            }
                        },
                        onShowRecent = {
                            scope.launch {
                                try {
                                    val repo = UserPreferencesRepository(context)
                                    recentEntries = repo.recentFiles.first()
                                    showRecentDialog = true
                                } catch (_: Exception) {}
                            }
                        },
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

    if (showRecentDialog) {
        AlertDialog(
            onDismissRequest = { showRecentDialog = false },
            title = { Text("最近访问", fontWeight = FontWeight.SemiBold) },
            text = {
                LazyColumn {
                    if (recentEntries.isEmpty()) {
                        item { Text("暂无记录", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    } else {
                        items(recentEntries) { entry ->
                            TextButton(
                                onClick = {
                                    showRecentDialog = false
                                    // 这里可以导航到该路径，但 FileBrowserScreen 没有暴露 navigateTo 外部接口
                                    // 暂时仅展示
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Text(entry.name, fontWeight = FontWeight.Medium)
                                    Text(
                                        text = entry.path,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            },
            dismissButton = { TextButton(onClick = { showRecentDialog = false }) { Text("关闭") } },
        )
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
