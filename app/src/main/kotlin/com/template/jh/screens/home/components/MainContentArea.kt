package com.template.jh.screens.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.template.jh.R
import com.template.jh.model.TabItem
import com.template.jh.model.TabType
import com.template.jh.screens.home.ChatViewModel
import com.template.jh.screens.home.components.audio.AudioPlaybackState
import com.template.jh.screens.home.components.audio.AudioPlayer
import com.template.jh.screens.home.components.preview.ImagePreview
import com.template.jh.screens.home.components.viewer.ArchiveViewer
import com.template.jh.screens.home.components.viewer.VideoPlayer
import com.template.jh.screens.home.components.settings.SettingsPane
import com.template.jh.screens.home.components.viewer.WebPreview
import com.template.jh.screens.home.components.viewer.VideoPlaybackState

// 中间主内容区
@Composable
fun MainContentArea(
    onOpenFolder: () -> Unit = {},
    chatViewModel: ChatViewModel? = null,
    audioPlaybackState: AudioPlaybackState? = null,
    videoPlaybackState: VideoPlaybackState? = null,
    openedFolderName: String? = null,
    recentFolderName: String? = null,
    onOpenRecentFolder: () -> Unit = {},
    tabs: List<TabItem> = emptyList(),
    activeTabIndex: Int = -1,
    onSelectTab: (Int) -> Unit = {},
    onCloseTab: (Int) -> Unit = {},
    onSaveAndCloseTab: (Int) -> Unit = {},
    onForceCloseTab: (Int) -> Unit = {},
    onCloseAllTabs: () -> Unit = {},
    onSaveAllTabs: () -> Unit = {},
    onSaveCurrent: () -> Unit = {},
    isFileModified: (String) -> Boolean = { false },
    previewModeTabs: Set<String> = emptySet(),
    onTogglePreviewMode: (String) -> Unit = {},
    getEditorContent: (String) -> androidx.compose.ui.text.input.TextFieldValue = { androidx.compose.ui.text.input.TextFieldValue("") },
    onPreviewContentChange: (String, androidx.compose.ui.text.input.TextFieldValue) -> Unit = { _, _ -> },

    tabContent: @Composable (String) -> Unit = {},
) {
    val hasTabs = tabs.isNotEmpty()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (hasTabs) {
            EditorTabBar(
                tabs = tabs,
                activeIndex = activeTabIndex,
                onSelectTab = onSelectTab,
                onCloseTab = onCloseTab,
                onSaveAndCloseTab = onSaveAndCloseTab,
                onForceCloseTab = onForceCloseTab,
                onCloseAllTabs = onCloseAllTabs,
                onSaveAllTabs = onSaveAllTabs,
                onSaveCurrent = onSaveCurrent,
                isFileModified = isFileModified,
            )
            // 当前路径指示
            if (activeTabIndex in tabs.indices) {
                val activeTab = tabs[activeTabIndex]
                if (activeTab.type == TabType.File || activeTab.type == TabType.Image
                    || activeTab.type == TabType.Audio || activeTab.type == TabType.Video
                    || activeTab.type == TabType.Archive || activeTab.type == TabType.Preview) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.12f))
                            .padding(horizontal = 12.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            // tab.id 现在是完整路径（filePath 优先），直接显示
                            text = activeTab.id,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            HorizontalDivider(
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (activeTabIndex in tabs.indices) {
                val activeTab = tabs[activeTabIndex]
                when (activeTab.type) {
                    TabType.Settings -> {
                        SettingsPane(
                            modifier = Modifier.fillMaxSize(),
                            chatViewModel = chatViewModel,
                        )
                    }
                    TabType.File -> {
                        tabContent(activeTab.id)
                    }
                    TabType.Image -> {
                        ImagePreview(
                            imagePath = activeTab.id,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    TabType.Audio -> {
                        AudioPlayer(
                            audioPath = activeTab.id,
                            state = audioPlaybackState ?: return@Box,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    TabType.Video -> {
                        VideoPlayer(
                            videoPath = activeTab.id,
                            state = videoPlaybackState ?: return@Box,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    TabType.Archive -> {
                        ArchiveViewer(
                            archivePath = activeTab.id,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    TabType.Preview -> {
                        val isPreview = activeTab.id in previewModeTabs
                        WebPreview(
                            filePath = activeTab.id,
                            isPreviewMode = isPreview,
                            onToggleMode = { onTogglePreviewMode(activeTab.id) },
                            textFieldValue = getEditorContent(activeTab.id),
                            onTextChange = { onPreviewContentChange(activeTab.id, it) },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            } else if (openedFolderName == null) {
                // 欢迎页（仅未打开文件夹时）
                WelcomeContent(
                    onOpenFolder = onOpenFolder,
                    recentFolderName = recentFolderName,
                    onOpenRecentFolder = onOpenRecentFolder,
                )
            }
        }

    }
}

// Tab 栏（可水平滚动）
@Composable
private fun EditorTabBar(
    tabs: List<TabItem>,
    activeIndex: Int,
    onSelectTab: (Int) -> Unit = {},
    onCloseTab: (Int) -> Unit,
    onSaveAndCloseTab: (Int) -> Unit = {},
    onForceCloseTab: (Int) -> Unit = {},
    onCloseAllTabs: () -> Unit = {},
    onSaveAllTabs: () -> Unit = {},
    onSaveCurrent: () -> Unit = {},
    isFileModified: (String) -> Boolean = { false },
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var closeConfirmTabIndex by remember { mutableIntStateOf(-1) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEachIndexed { index, tab ->
                val isActive = index == activeIndex
                Row(
                    modifier = Modifier
                        .height(32.dp)
                        .background(
                            if (isActive) MaterialTheme.colorScheme.background
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
                        )
                        .clickable { onSelectTab(index) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = when (tab.type) {
                            TabType.Settings -> Icons.Default.Settings
                            TabType.File -> Icons.Default.Description
                            TabType.Image -> Icons.Default.Image
                            TabType.Audio -> Icons.Default.MusicNote
                            TabType.Video -> Icons.Default.Videocam
                            TabType.Archive -> Icons.Default.FolderZip
                            TabType.Preview -> Icons.Default.Language
                        },
                        contentDescription = null,
                        modifier = Modifier
                            .padding(start = 10.dp, end = 4.dp)
                            .size(14.dp),
                        tint = if (isActive) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = tab.title,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isActive) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 120.dp)
                    )
                    // 关闭按钮 + 未保存文件的下拉菜单
                    Box {
                        IconButton(
                            onClick = {
                                if (tab.type == TabType.File && isFileModified(tab.id)) {
                                    closeConfirmTabIndex = index
                                } else {
                                    onCloseTab(index)
                                }
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.tab_close),
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        DropdownMenu(
                            expanded = closeConfirmTabIndex == index,
                            onDismissRequest = { closeConfirmTabIndex = -1 },
                            modifier = Modifier.heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.75f).dp)
                        ) {
                            DropdownMenuItem(
                                text = { Text("不保存关闭") },
                                onClick = {
                                    closeConfirmTabIndex = -1
                                    onForceCloseTab(index)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("保存并关闭") },
                                onClick = {
                                    closeConfirmTabIndex = -1
                                    onSaveAndCloseTab(index)
                                },
                            )
                        }
                    }
                }
            }
        }

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
                modifier = Modifier.heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.75f).dp)
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
                        onSaveAllTabs()
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
    recentFolderName: String? = null,
    onOpenRecentFolder: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // 授权入口已移至顶部工具栏"文件"下拉菜单，此处仅显示最近记录
        Text(
            text = "通过顶部「文件 → 授权文件管理」打开项目",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        if (recentFolderName != null) {
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "最近",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))
            RecentProjectCard(name = recentFolderName, path = recentFolderName, onClick = onOpenRecentFolder)
        }
    }
}

@Composable
private fun RecentProjectCard(name: String, path: String, onClick: () -> Unit = {}) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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
            Column(modifier = Modifier.weight(1f)) {
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
