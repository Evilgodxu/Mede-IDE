package com.medeide.jh.screens.home.landscape.workspace

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Videocam
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medeide.jh.R
import com.medeide.jh.model.TabItem
import com.medeide.jh.model.TabType
import com.medeide.jh.screens.home.ChatViewModel
import com.medeide.jh.screens.home.audioplayer.AudioPlaybackState
import com.medeide.jh.screens.home.audioplayer.AudioPlayer
import com.medeide.jh.screens.home.landscape.workspace.preview.image.ImagePreview
import com.medeide.jh.screens.home.landscape.workspace.viewer.ArchiveViewer
import com.medeide.jh.screens.home.landscape.workspace.viewer.VideoPlayer
import com.medeide.jh.screens.home.settings.SettingsPane
import com.medeide.jh.screens.home.landscape.workspace.viewer.WebPreview
import com.medeide.jh.screens.home.landscape.workspace.viewer.MarkdownPreview
import com.medeide.jh.screens.home.landscape.workspace.viewer.VideoPlaybackState

// 工作区主内容
@Composable
fun MainContentArea(
    chatViewModel: ChatViewModel? = null,
    audioPlaybackState: AudioPlaybackState? = null,
    videoPlaybackState: VideoPlaybackState? = null,
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
    projectDirPath: String = "",
    onTogglePreviewMode: (String) -> Unit = {},
    getEditorContent: (String) -> androidx.compose.ui.text.input.TextFieldValue = { androidx.compose.ui.text.input.TextFieldValue("") },
    onPreviewContentChange: (String, androidx.compose.ui.text.input.TextFieldValue) -> Unit = { _, _ -> },

    tabContent: @Composable (String) -> Unit = {},
    // 搜索替换相关
    currentSearchMatches: List<com.medeide.jh.screens.home.model.SearchResultItem> = emptyList(),
    currentSearchMatchIndex: Int = -1,
    onSearchNavUp: () -> Unit = {},
    onSearchNavDown: () -> Unit = {},
    onReplaceCurrent: (String) -> Unit = {},
    isSearchToolbarVisible: Boolean = false,
    toolbarSearchQuery: String = "",
    toolbarReplaceText: String = "",
    onToolbarSearchQueryChange: (String) -> Unit = {},
    onToolbarReplaceTextChange: (String) -> Unit = {},
    onCloseSearchToolbar: () -> Unit = {},
    onClearSearch: () -> Unit = {},
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

        // 工作区
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
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
                        // 获取当前文件路径以便过滤搜索匹配
                        val fileMatches = remember(activeTab.id, currentSearchMatches) {
                            currentSearchMatches.filter { m ->
                                activeTab.id.endsWith(m.filePath) || m.filePath.endsWith(activeTab.id.substringAfterLast('/'))
                            }
                        }
                        Box(modifier = Modifier.fillMaxSize()) {
                            tabContent(activeTab.id)

                            // 编辑器右上角查找替换工具栏
                            if (isSearchToolbarVisible) {
                                val matchCount = fileMatches.size
                                val currentIdx = if (currentSearchMatchIndex >= 0 &&
                                    currentSearchMatchIndex < currentSearchMatches.size &&
                                    (activeTab.id.endsWith(currentSearchMatches[currentSearchMatchIndex].filePath) ||
                                     currentSearchMatches[currentSearchMatchIndex].filePath.endsWith(activeTab.id.substringAfterLast('/'))))
                                    currentSearchMatchIndex else 0
                                EditorSearchOverlay(
                                    matchCount = matchCount,
                                    currentIndex = currentIdx,
                                    searchQuery = toolbarSearchQuery,
                                    replaceText = toolbarReplaceText,
                                    onSearchQueryChange = onToolbarSearchQueryChange,
                                    onReplaceTextChange = onToolbarReplaceTextChange,
                                    onNavUp = onSearchNavUp,
                                    onNavDown = onSearchNavDown,
                                    onReplaceCurrent = { onReplaceCurrent(activeTab.id) },
                                    onClose = onCloseSearchToolbar,
                                    onClearSearch = onClearSearch,
                                    modifier = Modifier.align(Alignment.TopEnd),
                                )
                            }
                        }
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
                    TabType.Markdown -> {
                        val isPreview = activeTab.id in previewModeTabs
                        val fileMatches = remember(activeTab.id, currentSearchMatches) {
                            currentSearchMatches.filter { m ->
                                activeTab.id.endsWith(m.filePath) || m.filePath.endsWith(activeTab.id.substringAfterLast('/'))
                            }
                        }
                        Box(modifier = Modifier.fillMaxSize()) {
                            MarkdownPreview(
                                filePath = activeTab.id,
                                isPreviewMode = isPreview,
                                onToggleMode = { onTogglePreviewMode(activeTab.id) },
                                textFieldValue = getEditorContent(activeTab.id),
                                onTextChange = { onPreviewContentChange(activeTab.id, it) },
                                modifier = Modifier.fillMaxSize(),
                            )

                            // 代码模式下显示查找替换工具栏
                            if (!isPreview && isSearchToolbarVisible) {
                                val matchCount = fileMatches.size
                                val currentIdx = if (currentSearchMatchIndex >= 0 &&
                                    currentSearchMatchIndex < currentSearchMatches.size &&
                                    (activeTab.id.endsWith(currentSearchMatches[currentSearchMatchIndex].filePath) ||
                                     currentSearchMatches[currentSearchMatchIndex].filePath.endsWith(activeTab.id.substringAfterLast('/'))))
                                    currentSearchMatchIndex else 0
                                EditorSearchOverlay(
                                    matchCount = matchCount,
                                    currentIndex = currentIdx,
                                    searchQuery = toolbarSearchQuery,
                                    replaceText = toolbarReplaceText,
                                    onSearchQueryChange = onToolbarSearchQueryChange,
                                    onReplaceTextChange = onToolbarReplaceTextChange,
                                    onNavUp = onSearchNavUp,
                                    onNavDown = onSearchNavDown,
                                    onReplaceCurrent = { onReplaceCurrent(activeTab.id) },
                                    onClose = onCloseSearchToolbar,
                                    onClearSearch = onClearSearch,
                                    modifier = Modifier.align(Alignment.TopEnd),
                                )
                            }
                        }
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
            }
        }
    }
}

// 紧凑型搜索输入框（无额外内边距，32dp高度下内容安全可视）
@Composable
private fun MiniSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(32.dp)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                shape = RoundedCornerShape(4.dp),
            )
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) {
            Text(
                text = placeholder,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall.copy(
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// 编辑器查找替换工具栏（右上角，双行）
@Composable
private fun EditorSearchOverlay(
    matchCount: Int,
    currentIndex: Int,
    searchQuery: String,
    replaceText: String,
    onSearchQueryChange: (String) -> Unit,
    onReplaceTextChange: (String) -> Unit,
    onNavUp: () -> Unit,
    onNavDown: () -> Unit,
    onReplaceCurrent: () -> Unit,
    onClose: () -> Unit,
    onClearSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .padding(6.dp),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
        shadowElevation = 6.dp,
    ) {
        Column(
            modifier = Modifier.padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // 第一行：搜索输入 + 上箭头 + 下箭头
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                MiniSearchField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = "搜索…",
                    modifier = Modifier.width(140.dp),
                )

                // 匹配计数
                Text(
                    text = "$currentIndex/$matchCount",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 2.dp),
                )

                // 上箭头
                IconButton(
                    onClick = onNavUp,
                    modifier = Modifier.size(24.dp),
                ) {
                    Icon(
                        Icons.Default.KeyboardArrowUp,
                        contentDescription = "上一个匹配",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // 下箭头
                IconButton(
                    onClick = onNavDown,
                    modifier = Modifier.size(24.dp),
                ) {
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = "下一个匹配",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // 第二行：替换输入 + 替换按钮 + 关闭按钮
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                MiniSearchField(
                    value = replaceText,
                    onValueChange = onReplaceTextChange,
                    placeholder = "替换为…",
                    modifier = Modifier.width(140.dp),
                )

                // 替换按钮
                IconButton(
                    onClick = onReplaceCurrent,
                    modifier = Modifier.size(24.dp),
                ) {
                    Icon(
                        Icons.Default.SwapHoriz,
                        contentDescription = "替换当前匹配",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }

                // 清空按钮
                IconButton(
                    onClick = onClearSearch,
                    modifier = Modifier.size(24.dp),
                ) {
                    Icon(
                        Icons.Default.ClearAll,
                        contentDescription = "清空搜索",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // 关闭按钮
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(24.dp),
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "关闭查找替换工具栏",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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
                            TabType.Markdown -> Icons.Default.Code
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


