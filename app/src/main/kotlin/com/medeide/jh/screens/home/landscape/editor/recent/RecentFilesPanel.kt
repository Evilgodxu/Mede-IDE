package com.medeide.jh.screens.home.landscape.editor.recent

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun RecentFilesPanel(
    recentFilesManager: RecentFilesManager,
    onOpenFile: (RecentFile) -> Unit,
    modifier: Modifier = Modifier,
) {
    val entries by recentFilesManager.recentFiles.collectAsState(initial = emptyList())

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = "最近文件",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 16.dp),
        )

        if (entries.isEmpty()) {
            Text(
                text = "暂无最近文件",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn {
                items(entries, key = { it.path }) { entry ->
                    RecentFileItem(
                        entry = entry,
                        onClick = { onOpenFile(entry) },
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentFileItem(
    entry: RecentFile,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (File(entry.path).isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
        )
        Column(
            modifier = Modifier.padding(start = 12.dp).weight(1f),
        ) {
            Text(
                text = entry.name,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = entry.path,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        val dateStr = remember(entry.lastOpened) {
            try {
                SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(entry.lastOpened))
            } catch (_: Exception) {
                ""
            }
        }
        Text(
            text = dateStr,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
