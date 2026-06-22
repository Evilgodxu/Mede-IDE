package com.medeide.jh.screens.home.landscape.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchPanel(
    rootPath: String,
    onFileSelected: (File, Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var searchQuery by remember { mutableStateOf("") }
    var useRegex by remember { mutableStateOf(false) }
    var caseSensitive by remember { mutableStateOf(false) }
    var filePattern by remember { mutableStateOf("*") }
    var isSearching by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
    var resultCount by remember { mutableStateOf(0) }

    fun performSearch() {
        if (searchQuery.isBlank()) return
        isSearching = true
        results = emptyList()
        resultCount = 0

        scope.launch {
            try {
                val collectedResults = mutableListOf<SearchResult>()
                FileSearchEngine.search(
                    rootPath = rootPath,
                    query = searchQuery,
                    useRegex = useRegex,
                    caseSensitive = caseSensitive,
                    filePattern = filePattern.takeIf { it != "*" },
                )
                    .catch { e ->
                        // 错误处理
                    }
                    .collect { result ->
                        collectedResults.add(result)
                        resultCount++
                        if (collectedResults.size <= 200) {
                            results = collectedResults.toList()
                        }
                    }
                if (collectedResults.size > 200) {
                    results = collectedResults.take(200)
                }
            } finally {
                isSearching = false
            }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // 搜索输入区域
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                // 搜索关键词
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("搜索内容") },
                    placeholder = { Text("输入搜索关键词...") },
                    singleLine = true,
                    trailingIcon = {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "清除")
                        }
                    },
                )

                Spacer(Modifier.height(8.dp))

                // 文件名过滤
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = filePattern,
                        onValueChange = { filePattern = it },
                        modifier = Modifier.weight(1f),
                        label = { Text("文件模式") },
                        placeholder = { Text("*.kt") },
                        singleLine = true,
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { performSearch() },
                        enabled = searchQuery.isNotBlank() && !isSearching,
                    ) {
                        Icon(Icons.Default.Search, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("搜索")
                    }
                }

                Spacer(Modifier.height(8.dp))

                // 选项
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = useRegex,
                            onCheckedChange = { useRegex = it },
                        )
                        Text("正则表达式")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = caseSensitive,
                            onCheckedChange = { caseSensitive = it },
                        )
                        Text("区分大小写")
                    }
                    if (isSearching) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("搜索中...")
                    } else if (resultCount > 0) {
                        Text("找到 $resultCount 个结果${if (results.size < resultCount) "（显示前 200 条）" else ""}")
                    }
                }
            }
        }

        // 搜索结果列表
        SelectionContainer {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .horizontalScroll(rememberScrollState())
                    .padding(8.dp),
            ) {
                results.forEach { result ->
                    SearchResultItem(
                        result = result,
                        query = searchQuery,
                        onClick = {
                            onFileSelected(result.file, result.line, result.column)
                        },
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                }
                if (results.isEmpty() && !isSearching && searchQuery.isNotBlank()) {
                    Text(
                        "没有找到匹配的结果",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchResultItem(
    result: SearchResult,
    query: String,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(8.dp),
    ) {
        // 文件路径
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Description,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = result.file.relativeToOrNull(File(result.file.parentFile.absoluteFile.parent))?.path
                    ?: result.file.name,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "行 ${result.line}, 列 ${result.column}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(4.dp))

        // 内容预览（高亮匹配部分）
        val highlightedContent = buildAnnotatedString {
            if (result.matchStart > 0) {
                append(result.content.substring(0, result.matchStart))
            }
            withStyle(SpanStyle(background = Color.Yellow, color = Color.Black)) {
                append(result.content.substring(result.matchStart, result.matchEnd + 1))
            }
            if (result.matchEnd + 1 < result.content.length) {
                append(result.content.substring(result.matchEnd + 1))
            }
        }

        Text(
            text = highlightedContent,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            lineHeight = 18.sp,
        )
    }
}
