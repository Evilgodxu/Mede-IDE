package com.medeide.jh.screens.home.landscape.sidebar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medeide.jh.data.storage.FileManager
import com.medeide.jh.screens.home.logic.EditorScreenState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 搜索结果项 */
data class SearchResultItem(
    val filePath: String,
    val lineNumber: Int,
    val matchText: String,
    val contextLines: List<FileManager.MatchLine>,
)

/** 按文件分组的搜索结果 */
data class FileSearchResult(
    val filePath: String,
    val matches: List<SearchResultItem>,
)

@Composable
fun SearchReplacePanel(
    fileManager: FileManager,
    editorState: EditorScreenState,
    /** 点击搜索结果后自动关闭面板的回调 */
    onClosePanel: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // 从 EditorScreenState 恢复持久化状态
    var searchQuery by remember { mutableStateOf(editorState.persistentSearchQuery) }
    var replaceText by remember { mutableStateOf(editorState.persistentReplaceText) }
    var isRegex by remember { mutableStateOf(editorState.persistentIsRegex) }
    var isCaseSensitive by remember { mutableStateOf(editorState.persistentIsCaseSensitive) }
    var isWholeWord by remember { mutableStateOf(editorState.persistentIsWholeWord) }
    var isSearching by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // 面板展开时将持久化内容同步到工具栏输入框
    LaunchedEffect(Unit) {
        editorState.currentSearchQuery = editorState.persistentSearchQuery
        editorState.currentReplaceText = editorState.persistentReplaceText
    }

    // 按文件分组（直接从持久化结果读取）
    val fileGroupedResults = remember(editorState.persistentSearchResults) {
        editorState.persistentSearchResults.groupBy { it.filePath }.map { (path, items) ->
            FileSearchResult(path, items)
        }
    }

    /** 执行搜索（后台协程，不阻塞 UI） */
    fun performSearch() {
        if (searchQuery.isBlank()) {
            editorState.persistentSearchResults = emptyList()
            return
        }
        isSearching = true
        editorState.persistentSearchResults = emptyList()
        // 同步持久化状态
        editorState.persistentSearchQuery = searchQuery
        editorState.persistentReplaceText = replaceText
        editorState.persistentIsRegex = isRegex
        editorState.persistentIsCaseSensitive = isCaseSensitive
        editorState.persistentIsWholeWord = isWholeWord

        val query = searchQuery
        val regex = isRegex
        val caseSensitive = isCaseSensitive
        val wholeWord = isWholeWord

        scope.launch {
            var pattern = if (regex) query else Regex.escape(query)
            if (wholeWord) {
                pattern = "\\b${pattern}\\b"
            }
            val matches = withContext(Dispatchers.IO) {
                fileManager.grepStructured(
                    pattern = pattern,
                    ignoreCase = !caseSensitive,
                    contextLines = 1,
                    maxResults = 200,
                )
            }

            editorState.persistentSearchResults = matches.map { m ->
                SearchResultItem(
                    filePath = m.filePath,
                    lineNumber = m.lineNumber,
                    matchText = m.matchText,
                    contextLines = m.contextLines,
                )
            }
            isSearching = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f))
    ) {
        // 搜索输入框
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .padding(horizontal = 8.dp, vertical = 2.dp)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(4.dp),
                )
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (searchQuery.isEmpty()) {
                Text(
                    "搜索文件内容…",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                )
            }
            BasicTextField(
                value = searchQuery,
                onValueChange = {
                    searchQuery = it
                    editorState.currentSearchQuery = it
                },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // 替换输入框
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .padding(horizontal = 8.dp, vertical = 2.dp)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(4.dp),
                )
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (replaceText.isEmpty()) {
                Text(
                    "替换为…",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                )
            }
            BasicTextField(
                value = replaceText,
                onValueChange = {
                    replaceText = it
                    editorState.currentReplaceText = it
                },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // 工具栏 - 第一行：匹配模式选项
        val chipBorder = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 1.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = isRegex,
                onClick = { isRegex = !isRegex },
                label = { Text("正则", fontSize = 10.sp) },
                modifier = Modifier.height(24.dp),
                border = if (isRegex) chipBorder else null,
            )
            FilterChip(
                selected = isWholeWord,
                onClick = { isWholeWord = !isWholeWord },
                label = { Text("全词", fontSize = 10.sp) },
                modifier = Modifier.height(24.dp),
                border = if (isWholeWord) chipBorder else null,
            )
            FilterChip(
                selected = isCaseSensitive,
                onClick = { isCaseSensitive = !isCaseSensitive },
                label = { Text("大小", fontSize = 10.sp) },
                modifier = Modifier.height(24.dp),
                border = if (isCaseSensitive) chipBorder else null,
            )
        }

        // 工具栏 - 第二行：操作按钮（居中显示，按钮间 8dp 间距）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 1.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 清空搜索（清空输入框、替换框和搜索结果）
            FilledIconButton(
                onClick = {
                    searchQuery = ""
                    replaceText = ""
                    editorState.persistentSearchResults = emptyList()
                    editorState.persistentSearchQuery = ""
                    editorState.persistentReplaceText = ""
                    editorState.currentSearchQuery = ""
                    editorState.currentReplaceText = ""
                },
                modifier = Modifier.size(26.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Icon(Icons.Default.ClearAll, "清空搜索", modifier = Modifier.size(14.dp))
            }

            Spacer(Modifier.width(8.dp))

            // 全部替换
            FilledIconButton(
                onClick = {
                    if (searchQuery.isBlank() || editorState.persistentSearchResults.isEmpty()) return@FilledIconButton
                    var pattern = if (isRegex) searchQuery else Regex.escape(searchQuery)
                    if (isWholeWord) {
                        pattern = "\\b${pattern}\\b"
                    }
                    val replacement = replaceText
                    val ignoreCase = !isCaseSensitive

                    scope.launch {
                        val count = withContext(Dispatchers.IO) {
                            fileManager.replaceInFiles(
                                pattern = pattern,
                                replacement = replacement,
                                ignoreCase = ignoreCase,
                            )
                        }
                        if (count > 0) {
                            val changedPaths = editorState.persistentSearchResults.map { it.filePath }.distinct()
                            for (path in changedPaths) {
                                val absPath = if (path.startsWith("/")) path
                                    else "${fileManager.projectDirPath.trimEnd('/')}/$path"
                                if (absPath in editorState.editorContent) {
                                    val newContent = editorState.readFileFromSource(absPath)
                                    editorState.editorContent[absPath] =
                                        androidx.compose.ui.text.input.TextFieldValue(newContent)
                                    editorState.originalContents[absPath] = newContent
                                }
                            }
                            performSearch()
                        }
                    }
                },
                modifier = Modifier.size(26.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
                ),
            ) {
                Icon(Icons.Default.SwapHoriz, "全部替换", modifier = Modifier.size(14.dp))
            }

            Spacer(Modifier.width(8.dp))

            // 搜索（刷新）
            FilledIconButton(
                onClick = { performSearch() },
                modifier = Modifier.size(26.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                ),
            ) {
                Icon(Icons.Default.Search, "搜索内容", modifier = Modifier.size(14.dp))
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(top = 2.dp),
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant,
        )

        // 结果计数
        if (editorState.persistentSearchResults.isNotEmpty()) {
            Text(
                text = "${fileGroupedResults.size} 个文件, ${editorState.persistentSearchResults.size} 处匹配",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }

        // 搜索结果列表 / 加载 / 空状态
        Box(modifier = Modifier.fillMaxSize()) {
            // 搜索结果列表
            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                items(fileGroupedResults, key = { it.filePath }) { fileResult ->
                    FileResultItem(
                        fileResult = fileResult,
                        searchQuery = searchQuery,
                        onMatchClick = { match ->
                            val absPath = if (match.filePath.startsWith("/")) match.filePath
                                else "${fileManager.projectDirPath.trimEnd('/')}/${match.filePath}"
                            editorState.openFileAtLine(absPath, match.lineNumber)
                            // 保存当前搜索上下文供编辑器悬浮按钮使用
                            editorState.currentSearchMatches = fileResult.matches.filter {
                                it.filePath == match.filePath
                            }
                            editorState.searchToolbarQuerySnapshot = searchQuery
                            editorState.currentSearchMatchIndex = fileResult.matches.indexOf(match).coerceAtLeast(0)
                            editorState.currentSearchQuery = searchQuery
                            editorState.currentReplaceText = replaceText
                            editorState.isSearchToolbarVisible = true
                            // 关闭面板
                            onClosePanel()
                        },
                    )
                }
            }

            // 搜索中状态
            if (isSearching) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "搜索中…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // 空状态（未搜索且无结果时不显示）
            if (!isSearching && editorState.persistentSearchResults.isEmpty() && searchQuery.isNotEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "未找到匹配内容",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                }
            }
        }
    }
}

@Composable
private fun FileResultItem(
    fileResult: FileSearchResult,
    searchQuery: String,
    onMatchClick: (SearchResultItem) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                fileResult.matches.firstOrNull()?.let { onMatchClick(it) }
            }
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = fileResult.filePath,
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
            ),
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Text(
            text = "${fileResult.matches.size} 处匹配",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.padding(start = 8.dp),
        )

        fileResult.matches.take(3).forEach { match ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onMatchClick(match) }
                    .padding(start = 8.dp, top = 1.dp, bottom = 1.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${match.lineNumber}",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.width(32.dp),
                )
                Text(
                    text = match.matchText.take(120),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (fileResult.matches.size > 3) {
            Text(
                text = "... 还有 ${fileResult.matches.size - 3} 处",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }

    HorizontalDivider(
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
    )
}
