package com.template.jh.screens.home.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.template.jh.core.storage.FileManager.MatchLine
import com.template.jh.core.storage.FileManager.SearchMatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SearchPanel(
    onSearch: (pattern: String, extension: String, ignoreCase: Boolean) -> List<SearchMatch>,
    onReplaceAll: (pattern: String, replacement: String, extension: String, ignoreCase: Boolean) -> Int,
    onOpenFileAtLine: (path: String, line: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pattern by remember { mutableStateOf("") }
    var replacement by remember { mutableStateOf("") }
    var extension by remember { mutableStateOf("") }
    var ignoreCase by remember { mutableStateOf(true) }
    var showReplace by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<SearchMatch>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var replaceCount by remember { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()

    Column(modifier = modifier.fillMaxSize().padding(8.dp)) {
        // 搜索输入
        OutlinedTextField(
            value = pattern,
            onValueChange = { pattern = it },
            placeholder = { Text("搜索内容...", style = MaterialTheme.typography.bodySmall) },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall,
            modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp),
            trailingIcon = {
                if (pattern.isNotEmpty()) {
                    IconButton(onClick = { pattern = ""; results = emptyList() }) {
                        Icon(Icons.Default.Close, contentDescription = "清除", modifier = Modifier.size(16.dp))
                    }
                }
            },
        )

        Spacer(Modifier.height(4.dp))

        // 搜索选项行
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = extension,
                onValueChange = { extension = it },
                placeholder = { Text("扩展名", style = MaterialTheme.typography.labelSmall) },
                singleLine = true,
                textStyle = MaterialTheme.typography.labelSmall,
                modifier = Modifier.width(80.dp).heightIn(min = 36.dp),
            )
            Spacer(Modifier.width(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = ignoreCase,
                    onCheckedChange = { ignoreCase = it },
                    modifier = Modifier.size(24.dp),
                )
                Text("忽略大小写", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = {
                if (pattern.isNotBlank()) {
                    isSearching = true
                    replaceCount = null
                    scope.launch {
                        val r = withContext(Dispatchers.IO) { onSearch(pattern, extension, ignoreCase) }
                        results = r
                        isSearching = false
                    }
                }
            }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Search, contentDescription = "搜索", modifier = Modifier.size(20.dp))
            }
        }

        // 替换区域
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (showReplace) "替换 ▼" else "替换 ▶",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { showReplace = !showReplace }.padding(4.dp),
            )
        }
        if (showReplace) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = replacement,
                    onValueChange = { replacement = it },
                    placeholder = { Text("替换为...", style = MaterialTheme.typography.bodySmall) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f).heightIn(min = 36.dp),
                )
                Spacer(Modifier.width(4.dp))
                OutlinedButton(
                    onClick = {
                        if (pattern.isNotBlank()) {
                            replaceCount = null
                            scope.launch {
                                val count = withContext(Dispatchers.IO) {
                                    onReplaceAll(pattern, replacement, extension, ignoreCase)
                                }
                                replaceCount = count
                                // 搜索刷新
                                val r = withContext(Dispatchers.IO) { onSearch(pattern, extension, ignoreCase) }
                                results = r
                            }
                        }
                    },
                    modifier = Modifier.heightIn(min = 32.dp),
                ) {
                    Text("全部替换", style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        // 状态
        if (isSearching) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
        }
        replaceCount?.let {
            Text(
                text = "已替换 $it 个文件",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        if (results.isNotEmpty()) {
            Text(
                text = "找到 ${results.size} 处匹配",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        // 结果列表
        if (results.isEmpty() && !isSearching) {
            Text(
                text = if (pattern.isBlank()) "输入搜索内容后点击搜索按钮" else "无匹配结果",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp).align(Alignment.CenterHorizontally),
            )
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(results, key = { "${it.filePath}:${it.lineNumber}" }) { match ->
                SearchResultItem(
                    match = match,
                    onOpen = { onOpenFileAtLine(match.filePath, match.lineNumber) },
                )
            }
        }
    }
}

@Composable
private fun SearchResultItem(
    match: SearchMatch,
    onOpen: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp).clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
    ) {
        Column(modifier = Modifier.padding(6.dp)) {
            // 文件路径+行号
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "${match.filePath}:${match.lineNumber}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // 匹配行内容
            Text(
                text = match.matchText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 16.dp, top = 2.dp),
            )
            // 上下文（仅高亮行前后）
            val ctx = match.contextLines.filter { !it.isMatch }
            if (ctx.isNotEmpty()) {
                Text(
                    text = ctx.joinToString(" | ") { it.text.take(60) },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 16.dp),
                )
            }
        }
    }
}
