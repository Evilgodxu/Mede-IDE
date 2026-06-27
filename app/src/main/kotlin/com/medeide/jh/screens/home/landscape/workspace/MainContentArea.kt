package com.medeide.jh.screens.home.landscape.workspace

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.filled.Archive
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
import androidx.compose.material.icons.filled.Terminal
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medeide.jh.screens.home.landscape.sidebar.searchreplacepanel.SearchReplaceState
import com.medeide.jh.screens.home.landscape.sidebar.searchreplacepanel.SearchResultItem
import com.medeide.jh.screens.home.landscape.workspace.audioplayer.AudioPlaybackState
import com.medeide.jh.screens.home.landscape.workspace.audioplayer.AudioPlayer
import com.medeide.jh.screens.home.landscape.workspace.editor.CodeEditor
import com.medeide.jh.screens.home.landscape.workspace.editor.TextEditor
import com.medeide.jh.core.model.TabItem
import com.medeide.jh.core.model.TabType
import com.medeide.jh.screens.home.landscape.workspace.preview.image.ImagePreview
import com.medeide.jh.screens.home.landscape.workspace.settings.SettingsPane
import com.medeide.jh.screens.home.landscape.workspace.terminal.TerminalPage
import com.medeide.jh.screens.home.landscape.workspace.viewer.MarkdownPreview
import com.medeide.jh.core.model.VideoPlaybackState
import com.medeide.jh.screens.home.landscape.workspace.viewer.VideoPlayer
import com.medeide.jh.screens.home.landscape.workspace.viewer.WebPreview
import com.medeide.jh.screens.home.landscape.workspace.welcome.WelcomePage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

// 工作区主内容 — 多类型编辑器/查看器
@Composable
fun MainContentArea(
    tabs: List<TabItem>,
    activeTabIndex: Int,
    onSelectTab: (Int) -> Unit,
    onCloseTab: (Int) -> Unit,
    onAddTab: (TabItem) -> Unit = {},
    modifier: Modifier = Modifier,
    videoPlaybackState: VideoPlaybackState? = null,
    audioPlaybackState: AudioPlaybackState? = null,
    onForceCloseTab: ((Int) -> Unit)? = null,
    onSaveAndCloseTab: ((Int) -> Unit)? = null,
    onCloseSavedTabs: (() -> Unit)? = null,
    isFileModified: (String) -> Boolean = { false },
    searchState: SearchReplaceState? = null,
    openFileLineRequest: Pair<String, Int>? = null,
    onOpenFileLineRequestHandled: () -> Unit = {},
) {
    val context = LocalContext.current
    var fileContents by remember { mutableStateOf(mapOf<String, TextFieldValue>()) }
    var originalContents by remember { mutableStateOf(mapOf<String, String>()) }
    var searchScrollVersion by remember { mutableIntStateOf(0) }
    var previewModeTabs by remember { mutableStateOf(setOf<String>()) }

    // 使用外部传入的音视频播放状态（由 HomeViewModel 持有，可跨重启存活）
    val mainVideoState = videoPlaybackState ?: remember { VideoPlaybackState() }
    val mainAudioState = audioPlaybackState ?: remember { AudioPlaybackState() }

    fun readFile(path: String): String = try {
        if (path.startsWith("content://")) {
            context.contentResolver.openInputStream(Uri.parse(path))?.bufferedReader()?.use { it.readText() } ?: ""
        } else {
            File(path).readText(Charsets.UTF_8)
        }
    } catch (_: Exception) {
        ""
    }

    fun saveFile(path: String) {
        val content = fileContents[path]?.text ?: return
        try {
            if (path.startsWith("content://")) {
                context.contentResolver.openOutputStream(Uri.parse(path), "wt")?.use {
                    it.write(content.toByteArray(Charsets.UTF_8))
                }
            } else {
                File(path).writeText(content, Charsets.UTF_8)
            }
            originalContents = originalContents + (path to content)
        } catch (_: Exception) {
        }
    }

    fun internalIsModified(path: String): Boolean = originalContents[path] != fileContents[path]?.text

    fun clearTabState(path: String) {
        fileContents = fileContents - path
        originalContents = originalContents - path
    }

    fun calculateLineOffset(text: String, line: Int): Int {
        var charCount = 0
        var currentLine = 1
        for (ch in text) {
            if (currentLine >= line) break
            charCount++
            if (ch == '\n') currentLine++
        }
        return charCount
    }

    fun setSelectionToLine(path: String, line: Int, query: String? = null) {
        val content = fileContents[path] ?: return
        if (line <= 1 || content.text.isEmpty()) {
            searchScrollVersion++
            return
        }
        val offset = calculateLineOffset(content.text, line).coerceAtMost(content.text.length)
        val newSelection = if (query != null) {
            val lineText = content.text.lines().getOrNull(line - 1) ?: ""
            val startInLine = lineText.indexOf(query, ignoreCase = true)
            if (startInLine >= 0) {
                val start = (offset + startInLine).coerceAtMost(content.text.length)
                val end = (start + query.length).coerceAtMost(content.text.length)
                TextRange(start, end)
            } else {
                TextRange(offset)
            }
        } else {
            TextRange(offset)
        }
        fileContents = fileContents + (path to content.copy(selection = newSelection))
        searchScrollVersion++
    }

    LaunchedEffect(tabs, activeTabIndex) {
        val tab = tabs.getOrNull(activeTabIndex) ?: return@LaunchedEffect
        if (tab.type in listOf(TabType.File, TabType.Text, TabType.Markdown, TabType.Preview) && tab.id !in fileContents) {
            val text = withContext(Dispatchers.IO) { readFile(tab.id) }
            fileContents = fileContents + (tab.id to TextFieldValue(text))
            originalContents = originalContents + (tab.id to text)
        }
    }

    LaunchedEffect(tabs) {
        val currentIds = tabs.map { it.id }.toSet()
        previewModeTabs = previewModeTabs.filter { it in currentIds }.toSet()
    }

    LaunchedEffect(openFileLineRequest) {
        val request = openFileLineRequest ?: return@LaunchedEffect
        val (path, line) = request
        if (path !in fileContents) {
            val text = withContext(Dispatchers.IO) { readFile(path) }
            fileContents = fileContents + (path to TextFieldValue(text))
            originalContents = originalContents + (path to text)
        }
        setSelectionToLine(path, line, searchState?.searchQuery)
        onOpenFileLineRequestHandled()
    }

    // 侧边栏全部替换后刷新已打开文件
    searchState?.let { state ->
        val changed = state.changedFiles.toList()
        LaunchedEffect(changed) {
            changed.forEach { path ->
                if (path in fileContents) {
                    val text = withContext(Dispatchers.IO) { readFile(path) }
                    fileContents = fileContents + (path to TextFieldValue(text))
                    originalContents = originalContents + (path to text)
                }
            }
            state.changedFiles.clear()
        }
    }

    val hasTabs = tabs.isNotEmpty()
    val activeTab = if (activeTabIndex in tabs.indices) tabs[activeTabIndex] else null
    var mdPreviewMode by remember(activeTab?.id) { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        if (hasTabs) {
            EditorTabBar(
                tabs = tabs,
                activeIndex = activeTabIndex,
                onSelectTab = onSelectTab,
                onCloseTab = onCloseTab,
                onForceCloseTab = { idx ->
                    tabs.getOrNull(idx)?.let { clearTabState(it.id) }
                    (onForceCloseTab ?: onCloseTab)(idx)
                },
                onSaveAndCloseTab = { idx ->
                    tabs.getOrNull(idx)?.let { saveFile(it.id); clearTabState(it.id) }
                    (onSaveAndCloseTab ?: onCloseTab)(idx)
                },
                onCloseSavedTabs = {
                    val savedIds = tabs.filter { !internalIsModified(it.id) }.map { it.id }.toSet()
                    fileContents = fileContents.filterKeys { it !in savedIds }
                    originalContents = originalContents.filterKeys { it !in savedIds }
                    tabs.indices.reversed().forEach { idx ->
                        if (!internalIsModified(tabs[idx].id)) onCloseTab(idx)
                    }
                },
                isFileModified = { internalIsModified(it) || isFileModified(it) },
            )

            val showPath = activeTab != null && activeTab.type in listOf(
                TabType.File, TabType.Image, TabType.Audio, TabType.Video, TabType.Markdown, TabType.Terminal, TabType.Preview,
            )
            if (showPath) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.12f))
                        .padding(horizontal = 12.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = activeTab.id,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            HorizontalDivider(
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            if (activeTab == null && !hasTabs) {
                WelcomePage()
            } else if (activeTab != null) {
                when (activeTab.type) {
                    TabType.Settings -> SettingsPane(modifier = Modifier.fillMaxSize())
                    TabType.File -> EditorWithSearchOverlay(
                        isToolbarVisible = searchState?.isToolbarVisible == true,
                        searchState = searchState,
                        filePath = activeTab.id,
                        content = fileContents[activeTab.id] ?: TextFieldValue(""),
                        onContentChange = { fileContents = fileContents + (activeTab.id to it) },
                        searchScrollVersion = searchScrollVersion,
                        onNavigateToMatch = { setSelectionToLine(activeTab.id, it) },
                        editor = { text, onTextChange, modifier, scrollVersion ->
                            CodeEditor(
                                text = text,
                                onTextChange = onTextChange,
                                modifier = modifier,
                                searchScrollVersion = scrollVersion,
                            )
                        },
                    )
                    TabType.Text -> EditorWithSearchOverlay(
                        isToolbarVisible = searchState?.isToolbarVisible == true,
                        searchState = searchState,
                        filePath = activeTab.id,
                        content = fileContents[activeTab.id] ?: TextFieldValue(""),
                        onContentChange = { fileContents = fileContents + (activeTab.id to it) },
                        searchScrollVersion = searchScrollVersion,
                        onNavigateToMatch = { setSelectionToLine(activeTab.id, it) },
                        editor = { text, onTextChange, _, scrollVersion ->
                            TextEditor(
                                text = text,
                                onTextChange = onTextChange,
                                searchScrollVersion = scrollVersion,
                            )
                        },
                    )
                    TabType.Image -> ImagePreview(
                        imagePath = activeTab.id,
                        modifier = Modifier.fillMaxSize(),
                    )
                    TabType.Audio -> {
                        AudioPlayer(
                            audioPath = activeTab.id,
                            state = mainAudioState,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    TabType.Video -> {
                        VideoPlayer(
                            videoPath = activeTab.id,
                            state = mainVideoState,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    TabType.Markdown -> MarkdownPreview(
                        filePath = activeTab.id,
                        isPreviewMode = mdPreviewMode,
                        onToggleMode = { mdPreviewMode = !mdPreviewMode },
                        textFieldValue = fileContents[activeTab.id] ?: TextFieldValue(""),
                        onTextChange = { fileContents = fileContents + (activeTab.id to it) },
                        modifier = Modifier.fillMaxSize(),
                    )
                    TabType.Preview -> {
                        val isPreview = activeTab.id in previewModeTabs
                        WebPreview(
                            filePath = activeTab.id,
                            isPreviewMode = isPreview,
                            onToggleMode = {
                                previewModeTabs = if (isPreview) previewModeTabs - activeTab.id else previewModeTabs + activeTab.id
                            },
                            textFieldValue = fileContents[activeTab.id] ?: TextFieldValue(""),
                            onTextChange = { fileContents = fileContents + (activeTab.id to it) },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    TabType.Terminal -> TerminalPage(modifier = Modifier.fillMaxSize())
                    else -> EditorWithSearchOverlay(
                        isToolbarVisible = searchState?.isToolbarVisible == true,
                        searchState = searchState,
                        filePath = activeTab.id,
                        content = fileContents[activeTab.id] ?: TextFieldValue(""),
                        onContentChange = { fileContents = fileContents + (activeTab.id to it) },
                        searchScrollVersion = searchScrollVersion,
                        onNavigateToMatch = { setSelectionToLine(activeTab.id, it) },
                        editor = { text, onTextChange, modifier, scrollVersion ->
                            CodeEditor(
                                text = text,
                                onTextChange = onTextChange,
                                modifier = modifier,
                                searchScrollVersion = scrollVersion,
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun EditorWithSearchOverlay(
    isToolbarVisible: Boolean,
    searchState: SearchReplaceState?,
    filePath: String,
    content: TextFieldValue,
    onContentChange: (TextFieldValue) -> Unit,
    searchScrollVersion: Int,
    onNavigateToMatch: (Int) -> Unit,
    editor: @Composable (TextFieldValue, (TextFieldValue) -> Unit, Modifier, Int) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        editor(content, onContentChange, Modifier.fillMaxSize(), searchScrollVersion)

        if (isToolbarVisible && searchState != null) {
            val matches = remember(
                content.text,
                searchState.searchQuery,
                searchState.isRegex,
                searchState.isCaseSensitive,
                searchState.isWholeWord,
            ) {
                findMatchesInContent(
                    content.text,
                    searchState.searchQuery,
                    searchState.isRegex,
                    searchState.isCaseSensitive,
                    searchState.isWholeWord,
                    filePath,
                )
            }
            LaunchedEffect(matches) {
                searchState.currentSearchMatches = matches
                if (searchState.currentSearchMatchIndex !in matches.indices) {
                    searchState.currentSearchMatchIndex = if (matches.isEmpty()) -1 else 0
                }
            }
            LaunchedEffect(searchState.currentSearchMatchIndex) {
                val match = searchState.currentSearchMatches.getOrNull(searchState.currentSearchMatchIndex)
                if (match != null) {
                    onNavigateToMatch(match.lineNumber)
                }
            }

            EditorSearchOverlay(
                matchCount = matches.size,
                currentIndex = searchState.currentSearchMatchIndex,
                searchQuery = searchState.searchQuery,
                replaceText = searchState.replaceText,
                onSearchQueryChange = { searchState.searchQuery = it },
                onReplaceTextChange = { searchState.replaceText = it },
                onNavUp = {
                    val newIdx = if (matches.isEmpty()) -1
                    else if (searchState.currentSearchMatchIndex <= 0) matches.size - 1
                    else searchState.currentSearchMatchIndex - 1
                    searchState.currentSearchMatchIndex = newIdx
                },
                onNavDown = {
                    val newIdx = if (matches.isEmpty()) -1
                    else if (searchState.currentSearchMatchIndex >= matches.size - 1) 0
                    else searchState.currentSearchMatchIndex + 1
                    searchState.currentSearchMatchIndex = newIdx
                },
                onReplaceCurrent = {
                    replaceCurrentMatch(content, onContentChange, searchState, filePath)
                },
                onClose = { searchState.isToolbarVisible = false },
                onClearSearch = { searchState.clearToolbar() },
                modifier = Modifier.align(Alignment.TopEnd),
            )
        }
    }
}

private fun findMatchesInContent(
    content: String,
    query: String,
    isRegex: Boolean,
    isCaseSensitive: Boolean,
    isWholeWord: Boolean,
    filePath: String,
): List<SearchResultItem> {
    if (query.isBlank()) return emptyList()
    var pattern = if (isRegex) query else Regex.escape(query)
    if (isWholeWord) pattern = "\\b${pattern}\\b"
    val regex = if (isCaseSensitive) Regex(pattern) else Regex(pattern, RegexOption.IGNORE_CASE)
    return content.lineSequence().mapIndexedNotNull { idx, line ->
        if (regex.containsMatchIn(line)) {
            SearchResultItem(filePath, idx + 1, line.trim(), emptyList())
        } else null
    }.toList()
}

private fun replaceCurrentMatch(
    content: TextFieldValue,
    onContentChange: (TextFieldValue) -> Unit,
    searchState: SearchReplaceState,
    filePath: String,
) {
    val query = searchState.searchQuery
    val replaceText = searchState.replaceText
    if (query.isBlank()) return
    var pattern = if (searchState.isRegex) query else Regex.escape(query)
    if (searchState.isWholeWord) pattern = "\\b${pattern}\\b"
    val regex = if (searchState.isCaseSensitive) Regex(pattern) else Regex(pattern, RegexOption.IGNORE_CASE)

    val idx = searchState.currentSearchMatchIndex
    val matches = searchState.currentSearchMatches
    val match = matches.getOrNull(idx) ?: return
    val lines = content.text.lines().toMutableList()
    if (match.lineNumber - 1 !in lines.indices) return
    val oldLine = lines[match.lineNumber - 1]
    val newLine = oldLine.replaceFirst(regex, replaceText)
    if (newLine == oldLine) return
    lines[match.lineNumber - 1] = newLine
    val newContent = lines.joinToString("\n")
    onContentChange(content.copy(text = newContent))
}

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
        modifier = modifier.padding(6.dp),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
        shadowElevation = 6.dp,
    ) {
        Column(
            modifier = Modifier.padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
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
                Text(
                    text = if (matchCount == 0) "0/0" else "${currentIndex + 1}/$matchCount",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 2.dp),
                )
                IconButton(onClick = onNavUp, modifier = Modifier.size(24.dp)) {
                    Icon(
                        Icons.Default.KeyboardArrowUp,
                        contentDescription = "上一个匹配",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onNavDown, modifier = Modifier.size(24.dp)) {
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = "下一个匹配",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
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
                IconButton(onClick = onReplaceCurrent, modifier = Modifier.size(24.dp)) {
                    Icon(
                        Icons.Default.SwapHoriz,
                        contentDescription = "替换当前匹配",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
                IconButton(onClick = onClearSearch, modifier = Modifier.size(24.dp)) {
                    Icon(
                        Icons.Default.ClearAll,
                        contentDescription = "清空搜索",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onClose, modifier = Modifier.size(24.dp)) {
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

@Composable
private fun EditorTabBar(
    tabs: List<TabItem>,
    activeIndex: Int,
    onSelectTab: (Int) -> Unit,
    onCloseTab: (Int) -> Unit,
    onForceCloseTab: (Int) -> Unit = onCloseTab,
    onSaveAndCloseTab: (Int) -> Unit = onCloseTab,
    onCloseSavedTabs: () -> Unit = {},
    isFileModified: (String) -> Boolean = { false },
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var closeConfirmTabIndex by remember { mutableIntStateOf(-1) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
        verticalAlignment = Alignment.CenterVertically,
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
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f),
                        )
                        .clickable { onSelectTab(index) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = tabIcon(tab.type),
                        contentDescription = null,
                        modifier = Modifier
                            .padding(start = 10.dp, end = 4.dp)
                            .size(14.dp),
                        tint = if (isActive) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = tab.title,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isActive) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 120.dp),
                    )
                    Box {
                        IconButton(
                            onClick = {
                                if (tab.type == TabType.File && isFileModified(tab.id)) {
                                    closeConfirmTabIndex = index
                                } else {
                                    onCloseTab(index)
                                }
                            },
                            modifier = Modifier.size(24.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "关闭",
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        DropdownMenu(
                            expanded = closeConfirmTabIndex == index,
                            onDismissRequest = { closeConfirmTabIndex = -1 },
                            modifier = Modifier.heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.75f).dp),
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

        Box {
            IconButton(
                onClick = { menuExpanded = true },
                modifier = Modifier.size(28.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "更多",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
                modifier = Modifier.heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.75f).dp),
            ) {
                DropdownMenuItem(
                    text = { Text("不保存关闭全部") },
                    onClick = {
                        menuExpanded = false
                        tabs.indices.reversed().forEach { onForceCloseTab(it) }
                    },
                )
                DropdownMenuItem(
                    text = { Text("保存并关闭全部") },
                    onClick = {
                        menuExpanded = false
                        tabs.indices.reversed().forEach { onSaveAndCloseTab(it) }
                    },
                )
                DropdownMenuItem(
                    text = { Text("关闭已保存") },
                    onClick = {
                        menuExpanded = false
                        onCloseSavedTabs()
                    },
                )
            }
        }
    }
}

private fun tabIcon(type: TabType) = when (type) {
    TabType.Settings -> Icons.Default.Settings
    TabType.File -> Icons.Default.Description
    TabType.Text -> Icons.Default.Description
    TabType.Image -> Icons.Default.Image
    TabType.Audio -> Icons.Default.MusicNote
    TabType.Video -> Icons.Default.Videocam
    TabType.Archive -> Icons.Default.Archive
    TabType.Preview -> Icons.Default.Language
    TabType.Markdown -> Icons.Default.Code
    TabType.Terminal -> Icons.Default.Terminal
}
