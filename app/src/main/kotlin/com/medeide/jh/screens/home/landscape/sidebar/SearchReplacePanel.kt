package com.medeide.jh.screens.home.landscape.sidebar

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FindReplace
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medeide.jh.data.storage.FileManager
import com.medeide.jh.screens.home.logic.EditorScreenState

// 搜索结果项
data class SearchResultItem(
    val filePath: String,
    val lineNumber: Int,
    val matchText: String,
    val contextLines: List<FileManager.MatchLine>,
)

// 按文件分组的搜索结果
data class FileSearchResult(
    val filePath: String,
    val matches: List<SearchResultItem>,
)

@Composable
fun SearchReplacePanel(
    fileManager: FileManager,
    editorState: EditorScreenState,
    modifier: Modifier = Modifier,
) {
    var searchQuery by remember { mutableStateOf("") }
    var replaceText by remember { mutableStateOf("") }
    var isRegex by remember { mutableStateOf(false) }
    var isCaseSensitive by remember { mutableStateOf(false) }
    var isSearching by remember { mutableStateOf(false) }
    val searchResults = remember { mutableStateListOf<SearchResultItem>() }

    // 按文件分组
    val fileGroupedResults = remember(searchResults.size, searchResults.hashCode()) {
        searchResults.groupBy { it.filePath }.map { (path, items) ->
            FileSearchResult(path, items)
        }
    }

    // 执行搜索
    fun performSearch() {
        if (searchQuery.isBlank()) {
            searchResults.clear()
            return
        }
        isSearching = true
        searchResults.clear()

        val pattern = if (isRegex) searchQuery else Regex.escape(searchQuery)
        val matches = fileManager.grepStructured(
            pattern = pattern,
            ignoreCase = !isCaseSensitive,
            contextLines = 1,
            maxResults = 200,
        )

        searchResults.addAll(matches.map { m ->
            SearchResultItem(
                filePath = m.filePath,
                lineNumber = m.lineNumber,
                matchText = m.matchText,
                contextLines = m.contextLines,
            )
        })
        isSearching = false
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f))
    ) {
        // 搜索输入框
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("搜索文件内容…", fontSize = 13.sp) },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
            ),
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = ""; searchResults.clear() }) {
                        Icon(Icons.Default.Close, "清空", modifier = Modifier.size(16.dp))
                    }
                }
            },
        )

        // 替换输入框
        OutlinedTextField(
            value = replaceText,
            onValueChange = { replaceText = it },
            placeholder = { Text("替换为…", fontSize = 13.sp) },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
            ),
        )

        // 工具栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 刷新（搜索）
            FilledIconButton(
                onClick = { performSearch() },
                modifier = Modifier.size(28.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                ),
            ) {
                Icon(Icons.Default.Refresh, "搜索", modifier = Modifier.size(16.dp))
            }

            // 清空结果
            FilledIconButton(
                onClick = { searchResults.clear() },
                modifier = Modifier.size(28.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Icon(Icons.Default.Close, "清空结果", modifier = Modifier.size(16.dp))
            }

            Spacer(Modifier.width(4.dp))

            // 正则切换
            FilterChip(
                selected = isRegex,
                onClick = { isRegex = !isRegex },
                label = { Text(".*", fontSize = 11.sp) },
                modifier = Modifier.height(26.dp),
            )

            // 大小写切换
            FilterChip(
                selected = isCaseSensitive,
                onClick = { isCaseSensitive = !isCaseSensitive },
                label = { Text("Aa", fontSize = 11.sp) },
                modifier = Modifier.height(26.dp),
            )

            Spacer(Modifier.weight(1f))

            // 全部替换
            FilledIconButton(
                onClick = {
                    if (searchQuery.isBlank() || searchResults.isEmpty()) return@FilledIconButton
                    val pattern = if (isRegex) searchQuery else Regex.escape(searchQuery)
                    val count = fileManager.replaceInFiles(
                        pattern = pattern,
                        replacement = replaceText,
                        ignoreCase = !isCaseSensitive,
                    )
                    if (count > 0) {
                        // 替换后重新读取已打开的文件内容
                        val changedPaths = searchResults.map { it.filePath }.distinct()
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
                },
                modifier = Modifier.size(28.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
                ),
            ) {
                Icon(Icons.Default.FindReplace, "全部替换", modifier = Modifier.size(16.dp))
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(top = 2.dp),
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant,
        )

        // 结果计数
        if (searchResults.isNotEmpty()) {
            Text(
                text = "${fileGroupedResults.size} 个文件, ${searchResults.size} 处匹配",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }

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
                        editorState.currentSearchMatchIndex = fileResult.matches.indexOf(match).coerceAtLeast(0)
                        editorState.currentSearchQuery = searchQuery
                        editorState.currentReplaceText = replaceText
                        editorState.isSearchToolbarVisible = true
                    },
                )
            }
        }

        // 空状态
        if (searchResults.isEmpty() && !isSearching && searchQuery.isNotEmpty()) {
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

        if (isSearching) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "搜索中…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
                // 点击文件行跳转到第一个匹配
                fileResult.matches.firstOrNull()?.let { onMatchClick(it) }
            }
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        // 文件名
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

        // 匹配数
        Text(
            text = "${fileResult.matches.size} 处匹配",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.padding(start = 8.dp),
        )

        // 匹配行预览（最多显示 3 条）
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

        // 如果匹配超过 3 处，显示 "更多…"
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
