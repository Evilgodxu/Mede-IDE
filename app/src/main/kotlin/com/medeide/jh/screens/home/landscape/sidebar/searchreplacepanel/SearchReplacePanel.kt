package com.medeide.jh.screens.home.landscape.sidebar.searchreplacepanel

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import com.medeide.jh.screens.home.landscape.workspace.model.displayNameFromPath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SearchReplacePanel(
    rootPath: String,
    state: SearchReplaceState,
    onOpenFile: (String, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 从共享状态恢复
    var searchQuery by remember { mutableStateOf(state.searchQuery) }
    var replaceText by remember { mutableStateOf(state.replaceText) }
    var isRegex by remember { mutableStateOf(state.isRegex) }
    var isCaseSensitive by remember { mutableStateOf(state.isCaseSensitive) }
    var isWholeWord by remember { mutableStateOf(state.isWholeWord) }
    var isSearching by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(state.searchQuery) { searchQuery = state.searchQuery }
    LaunchedEffect(state.replaceText) { replaceText = state.replaceText }
    LaunchedEffect(state.isRegex) { isRegex = state.isRegex }
    LaunchedEffect(state.isCaseSensitive) { isCaseSensitive = state.isCaseSensitive }
    LaunchedEffect(state.isWholeWord) { isWholeWord = state.isWholeWord }

    val fileGroupedResults = remember(state.persistentSearchResults) {
        state.persistentSearchResults.groupBy { it.filePath }.map { (path, items) ->
            FileSearchResult(path, items)
        }
    }

    fun syncToState() {
        state.searchQuery = searchQuery
        state.replaceText = replaceText
        state.isRegex = isRegex
        state.isCaseSensitive = isCaseSensitive
        state.isWholeWord = isWholeWord
    }

    fun performSearch() {
        syncToState()
        if (searchQuery.isBlank()) {
            state.persistentSearchResults = emptyList()
            return
        }
        isSearching = true
        state.persistentSearchResults = emptyList()
        val pattern = state.buildPattern()
        val ignoreCase = !isCaseSensitive
        scope.launch {
            val matches = withContext(Dispatchers.IO) {
                grepStructured(rootPath, pattern, ignoreCase, contextLines = 1, maxResults = 200)
            }
            state.persistentSearchResults = matches
            isSearching = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f))
    ) {
        SearchField(
            value = searchQuery,
            onValueChange = {
                searchQuery = it
                state.searchQuery = it
            },
            placeholder = "搜索文件内容…",
        )
        SearchField(
            value = replaceText,
            onValueChange = {
                replaceText = it
                state.replaceText = it
            },
            placeholder = "替换为…",
        )

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
                onClick = { isRegex = !isRegex; state.isRegex = isRegex },
                label = { Text("正则", fontSize = 10.sp) },
                modifier = Modifier.height(24.dp),
                border = if (isRegex) chipBorder else null,
            )
            FilterChip(
                selected = isWholeWord,
                onClick = { isWholeWord = !isWholeWord; state.isWholeWord = isWholeWord },
                label = { Text("全词", fontSize = 10.sp) },
                modifier = Modifier.height(24.dp),
                border = if (isWholeWord) chipBorder else null,
            )
            FilterChip(
                selected = isCaseSensitive,
                onClick = { isCaseSensitive = !isCaseSensitive; state.isCaseSensitive = isCaseSensitive },
                label = { Text("大小", fontSize = 10.sp) },
                modifier = Modifier.height(24.dp),
                border = if (isCaseSensitive) chipBorder else null,
            )
        }

        // 第二行：文本按钮，间距与第一行芯片一致（2.dp）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 1.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextActionButton(
                text = "清除",
                onClick = {
                    searchQuery = ""
                    replaceText = ""
                    state.clearAll()
                },
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            TextActionButton(
                text = "替换",
                onClick = {
                    syncToState()
                    if (searchQuery.isBlank() || state.persistentSearchResults.isEmpty()) return@TextActionButton
                    val pattern = state.buildPattern()
                    val ignoreCase = !isCaseSensitive
                    scope.launch {
                        val changed = withContext(Dispatchers.IO) {
                            replaceInFiles(rootPath, pattern, replaceText, ignoreCase)
                        }
                        state.changedFiles.addAll(changed)
                        performSearch()
                    }
                },
                containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
                contentColor = MaterialTheme.colorScheme.error,
            )
            TextActionButton(
                text = "搜索",
                onClick = { performSearch() },
                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                contentColor = MaterialTheme.colorScheme.primary,
            )
        }

        HorizontalDivider(
            modifier = Modifier.padding(top = 2.dp),
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant,
        )

        if (state.persistentSearchResults.isNotEmpty()) {
            Text(
                text = "${fileGroupedResults.size} 个文件, ${state.persistentSearchResults.size} 处匹配",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }

        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(fileGroupedResults, key = { it.filePath }) { fileResult ->
                    FileResultItem(
                        fileResult = fileResult,
                        onMatchClick = { match ->
                            val absPath = resolveAbsolutePath(rootPath, match.filePath)
                            state.isToolbarVisible = true
                            state.searchQuery = searchQuery
                            state.replaceText = replaceText
                            state.currentSearchMatchIndex = fileResult.matches.indexOf(match).coerceAtLeast(0)
                            onOpenFile(absPath, match.lineNumber)
                        },
                    )
                }
            }

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

            if (!isSearching && state.persistentSearchResults.isEmpty() && searchQuery.isNotEmpty()) {
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
private fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
) {
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
        if (value.isEmpty()) {
            Text(
                placeholder,
                fontSize = 12.sp,
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
private fun TextActionButton(
    text: String,
    onClick: () -> Unit,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
) {
    Box(
        modifier = Modifier
            .height(24.dp)
            .background(containerColor, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            color = contentColor,
        )
    }
}

@Composable
private fun FileResultItem(
    fileResult: FileSearchResult,
    onMatchClick: (SearchResultItem) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { fileResult.matches.firstOrNull()?.let { onMatchClick(it) } }
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = displayNameFromPath(fileResult.filePath),
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
