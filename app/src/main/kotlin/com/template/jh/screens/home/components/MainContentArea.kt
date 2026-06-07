package com.template.jh.screens.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.template.jh.R
import com.template.jh.core.ai.ChatViewModel

// 中间主内容区
@Composable
fun MainContentArea(
    isSettingsOpen: Boolean = false,
    onCloseSettings: () -> Unit = {},
    onOpenFolder: () -> Unit = {},
    onNewProject: () -> Unit = {},
    onCloneGit: () -> Unit = {},
    chatViewModel: ChatViewModel? = null,
    onBrowseModelFile: () -> Unit = {},
    openedFolderName: String? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (isSettingsOpen) {
            // Tab 栏
            EditorTabBar(
                tabs = listOf(stringResource(R.string.settings_tab_name)),
                activeIndex = 0,
                onCloseTab = { onCloseSettings() },
                onCloseAllTabs = { onCloseSettings() }
            )
            HorizontalDivider(
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
            // 设置内容
            SettingsPane(
                modifier = Modifier.weight(1f),
                chatViewModel = chatViewModel,
                onBrowseModelFile = onBrowseModelFile,
            )
        } else {
            // 欢迎页
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                WelcomeContent(
                    onOpenFolder = onOpenFolder,
                    onNewProject = onNewProject,
                    onCloneGit = onCloneGit,
                    openedFolderName = openedFolderName,
                )
            }
        }
    }
}

// Tab 栏（模拟 IDE 编辑区顶部标签页）
@Composable
private fun EditorTabBar(
    tabs: List<String>,
    activeIndex: Int,
    onCloseTab: (Int) -> Unit,
    onCloseAllTabs: () -> Unit = {},
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        tabs.forEachIndexed { index, title ->
            val isActive = index == activeIndex
            Row(
                modifier = Modifier
                    .height(32.dp)
                    .background(
                        if (isActive) MaterialTheme.colorScheme.background
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
                    )
                    .clickable { },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(start = 10.dp, end = 4.dp)
                        .size(14.dp),
                    tint = if (isActive) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isActive) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 120.dp)
                )
                IconButton(
                    onClick = { onCloseTab(index) },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.tab_close),
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 占位，让更多按钮靠右
        Spacer(modifier = Modifier.weight(1f))

        // 更多操作按钮
        Box {
            IconButton(
                onClick = { menuExpanded = true },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.tab_more),
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.tab_close_all)) },
                    onClick = {
                        menuExpanded = false
                        onCloseAllTabs()
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.tab_save_and_close)) },
                    onClick = {
                        menuExpanded = false
                        onCloseAllTabs()
                    }
                )
            }
        }
    }
}

// 欢迎页内容
@Composable
private fun WelcomeContent(
    onOpenFolder: () -> Unit,
    onNewProject: () -> Unit,
    onCloneGit: () -> Unit,
    openedFolderName: String? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // 快捷操作按钮组
        Column(
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            QuickActionRow(
                icon = Icons.Default.Add,
                text = stringResource(R.string.new_project),
                onClick = onNewProject
            )

            QuickActionRow(
                icon = Icons.Default.FolderOpen,
                text = stringResource(R.string.open_folder),
                onClick = onOpenFolder
            )

            QuickActionRow(
                icon = Icons.Default.Storage,
                text = stringResource(R.string.clone_git_repo),
                onClick = onCloneGit
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // 最近项目
        if (openedFolderName != null) {
            Text(
                text = stringResource(R.string.recent_projects),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            RecentProjectCard(
                name = openedFolderName,
                path = openedFolderName,
            )
        }
    }
}

@Composable
private fun QuickActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun RecentProjectCard(
    name: String,
    path: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Cloud,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = path,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
