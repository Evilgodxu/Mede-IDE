package com.medeide.jh.screens.home.components.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.medeide.jh.R
import com.medeide.jh.data.repository.RecentEntry

@Composable
fun SearchBar(
    recentFiles: List<RecentEntry>,
    recentFolders: List<RecentEntry>,
    onOpenRecentFile: (String) -> Unit,
    onOpenRecentFolder: (String) -> Unit,
    dropdownMaxHeight: androidx.compose.ui.unit.Dp,
    projectDirPath: String = "",
    modifier: Modifier = Modifier,
) {
    var searchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    // 过滤 + 排序（先计算供 dropdown 判断用）
    val filteredFolders = if (projectDirPath.isNotBlank()) {
        recentFolders.filter { it.path != projectDirPath }
    } else {
        recentFolders
    }
    val allRecent = filteredFolders.map { it to "目录" } + recentFiles.map { it to "文件" }
    val searchDropdownExpanded = searchActive && (searchQuery.isNotEmpty() || allRecent.isNotEmpty())
    val filtered = if (searchQuery.isBlank()) allRecent
    else allRecent.filter { (e, _) ->
        e.name.contains(searchQuery, ignoreCase = true) ||
        e.path.contains(searchQuery, ignoreCase = true)
    }
    val displayItems = filtered.take(10)

    Box(modifier = modifier) {
        if (searchActive) {
            // 使用 BasicTextField 避免 Material3 TextField 内部 contentPadding 导致行高不足时文字被裁剪
            val textStyle = MaterialTheme.typography.labelSmall
            Row(
                modifier = Modifier
                    .width(140.dp)
                    .height(28.dp)
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    singleLine = true,
                    textStyle = textStyle.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                    decorationBox = { innerTextField ->
                        Box {
                            if (searchQuery.isEmpty()) {
                                Text(
                                    "搜索...",
                                    style = textStyle,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                )
                            }
                            innerTextField()
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "关闭搜索",
                    modifier = Modifier
                        .size(16.dp)
                        .clickable {
                            searchActive = false
                            searchQuery = ""
                        },
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            IconButton(
                onClick = { searchActive = true },
                modifier = Modifier.size(28.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "搜索最近文件",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // 自定义下拉列表（代替 DropdownMenu 避免焦点丢失导致输入法关闭）
        if (searchDropdownExpanded) {
            val density = LocalDensity.current
            Card(
                modifier = Modifier
                    .offset { IntOffset(0, with(density) { 32.dp.toPx().toInt() }) }
                    .widthIn(min = 140.dp)
                    .heightIn(max = dropdownMaxHeight),
                shape = RoundedCornerShape(8.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(
                    modifier = Modifier
                        .heightIn(max = dropdownMaxHeight)
                        .widthIn(min = 140.dp),
                ) {
                    if (displayItems.isEmpty()) {
                        Text(
                            text = if (searchQuery.isBlank()) stringResource(R.string.recent_files_empty) else "无匹配结果",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(12.dp),
                        )
                    } else {
                        displayItems.forEachIndexed { idx, (entry, type) ->
                            if (idx > 0) HorizontalDivider()
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        searchQuery = ""
                                        searchActive = false
                                        if (type == "目录") onOpenRecentFolder(entry.path)
                                        else onOpenRecentFile(entry.path)
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                            ) {
                                Column(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                                    Text(
                                        text = entry.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    Text(
                                        text = entry.path,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
