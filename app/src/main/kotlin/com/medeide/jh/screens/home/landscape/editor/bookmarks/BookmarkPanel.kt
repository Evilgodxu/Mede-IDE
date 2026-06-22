package com.medeide.jh.screens.home.landscape.editor.bookmarks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarkPanel(
    bookmarkManager: BookmarkManager,
    editorState: com.medeide.jh.screens.home.logic.EditorScreenState,
    onNavigateToBookmark: (String, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val bookmarks by bookmarkManager.bookmarks.collectAsState(initial = emptyList())

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = "书签",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 16.dp),
        )

        if (bookmarks.isEmpty()) {
            Text(
                text = "暂无书签",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn {
                items(bookmarks, key = { it.id }) { bookmark ->
                    BookmarkItemRow(
                        bookmark = bookmark,
                        onNavigate = { onNavigateToBookmark(bookmark.filePath, bookmark.line) },
                        onDelete = {
                            bookmarkManager.removeBookmark(bookmark.id)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun BookmarkItemRow(
    bookmark: Bookmark,
    onNavigate: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).clickable(onClick = onNavigate),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = bookmark.label ?: "书签 #${bookmark.line}",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = "${bookmark.filePath}:${bookmark.line}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "删除书签")
        }
    }
}
