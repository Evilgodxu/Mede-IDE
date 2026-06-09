package com.template.jh.screens.home.components.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.template.jh.R
import com.template.jh.data.repository.RecentEntry

@Composable
fun SearchBar(
    recentFiles: List<RecentEntry>,
    recentFolders: List<RecentEntry>,
    onOpenRecentFile: (String) -> Unit,
    onOpenRecentFolder: (String) -> Unit,
    dropdownMaxHeight: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    var searchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchDropdownExpanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        if (searchActive) {
            TextField(
                value = searchQuery,
                onValueChange = { searchQuery = it; searchDropdownExpanded = true },
                placeholder = { Text("搜索最近文件...", style = MaterialTheme.typography.labelSmall) },
                singleLine = true,
                textStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurface),
                modifier = Modifier
                    .width(180.dp)
                    .heightIn(min = 32.dp),
                colors = TextFieldDefaults.colors(
                    unfocusedContainerColor = Color.Transparent,
                    focusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = MaterialTheme.colorScheme.primary,
                ),
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "关闭搜索",
                        modifier = Modifier
                            .size(16.dp)
                            .clickable {
                                searchActive = false
                                searchQuery = ""
                                searchDropdownExpanded = false
                            },
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            )
        } else {
            IconButton(
                onClick = { searchActive = true; searchDropdownExpanded = true },
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

        // 搜索下拉结果
        DropdownMenu(
            expanded = searchDropdownExpanded,
            onDismissRequest = { searchDropdownExpanded = false },
            modifier = Modifier.heightIn(max = dropdownMaxHeight).widthIn(min = 220.dp),
        ) {
            val allRecent = recentFolders.map { it to "目录" } + recentFiles.map { it to "文件" }
            val filtered = if (searchQuery.isBlank()) allRecent
            else allRecent.filter { (e, _) ->
                e.name.contains(searchQuery, ignoreCase = true) ||
                e.path.contains(searchQuery, ignoreCase = true)
            }

            if (filtered.isEmpty()) {
                DropdownMenuItem(
                    text = { Text(if (searchQuery.isBlank()) stringResource(R.string.recent_files_empty) else "无匹配结果") },
                    onClick = { searchDropdownExpanded = false },
                    enabled = false,
                )
            } else {
                filtered.take(10).forEach { (entry, type) ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(
                                    text = entry.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = entry.path,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        },
                        onClick = {
                            searchDropdownExpanded = false
                            searchQuery = ""
                            searchActive = false
                            if (type == "目录") onOpenRecentFolder(entry.path)
                            else onOpenRecentFile(entry.path)
                        },
                    )
                }
            }
        }
    }
}
