package com.medeide.jh.screens.home.portrait.sidepanel

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.medeide.jh.screens.home.filebrowser.FileBrowserScreen
import com.medeide.jh.core.data.repository.UserPreferencesRepository
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import android.content.Context
import androidx.compose.ui.platform.LocalContext

@Composable
fun PortraitFileBrowserPanel(
    rootPath: String,
    onOpenFile: (String) -> Unit = {},
    onAddToConversation: (String) -> Unit = {},
    onOpenAsProject: (String) -> Unit = {},
    onExitProjectMode: () -> Unit = {},
    isProjectModeActive: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var recentEntries by remember { mutableStateOf<List<com.medeide.jh.screens.home.recent.RecentFileEntry>>(emptyList()) }
    var showRecentDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 24.dp),
    ) {
        Text(
            text = "文件管理器",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        )
        Spacer(modifier = Modifier.height(8.dp))
        FileBrowserScreen(
            rootPath = rootPath,
            columnCount = 1,
            onOpenFile = onOpenFile,
            onAddToConversation = onAddToConversation,
            onOpenAsProject = onOpenAsProject,
            onExitProjectMode = onExitProjectMode,
            isProjectModeActive = isProjectModeActive,
            modifier = Modifier.fillMaxWidth().weight(1f),
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
