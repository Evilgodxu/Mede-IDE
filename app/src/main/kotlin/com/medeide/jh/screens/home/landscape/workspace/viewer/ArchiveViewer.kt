package com.medeide.jh.screens.home.landscape.workspace.viewer

import android.net.Uri
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Arrangement
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

private data class ArchiveEntry(
    val name: String,
    val isDirectory: Boolean,
    val size: Long,
    val compressedSize: Long,
)

@Composable
fun ArchiveViewer(
    archivePath: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var entries by remember { mutableStateOf<List<ArchiveEntry>>(emptyList()) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var totalEntries by remember { mutableStateOf(0) }

    val ext = archivePath.substringAfterLast('.').lowercase()

    LaunchedEffect(archivePath) {
        if (ext != "zip") {
            errorMsg = "暂不支持 \"$ext\" 格式，仅支持 ZIP"
            return@LaunchedEffect
        }
        try {
            val zipStream = if (archivePath.startsWith("content://")) {
                val uri = Uri.parse(archivePath)
                ZipInputStream(context.contentResolver.openInputStream(uri))
            } else {
                ZipInputStream(FileInputStream(archivePath))
            }
            val list = mutableListOf<ArchiveEntry>()
            zipStream.use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    list.add(ArchiveEntry(
                        name = entry.name,
                        isDirectory = entry.isDirectory,
                        size = entry.size,
                        compressedSize = entry.compressedSize,
                    ))
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            entries = list
            totalEntries = list.size
        } catch (e: Exception) {
            errorMsg = "读取失败: ${e.message}"
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF1E1E1E))
    ) {
        if (errorMsg != null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = errorMsg!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
            }
            return@Column
        }

        // 文件总数
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF252525))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.FolderZip, null, Modifier.size(16.dp), tint = Color(0xFFAAAAAA))
            Spacer(Modifier.width(6.dp))
            Text(
                text = "$totalEntries 个条目",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFFAAAAAA),
            )
        }
        HorizontalDivider(color = Color(0xFF333333), thickness = 0.5.dp)

        if (entries.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "压缩包为空",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF666666),
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                itemsIndexed(entries) { index, entry ->
                    EntryRow(entry = entry)
                    if (index < entries.size - 1) {
                        HorizontalDivider(color = Color(0xFF2A2A2A), thickness = 0.5.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun EntryRow(entry: ArchiveEntry) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (entry.isDirectory) Icons.Default.Folder else Icons.Default.Description,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = if (entry.isDirectory) MaterialTheme.colorScheme.primary else Color(0xFF888888),
        )
        Spacer(Modifier.width(8.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = entry.name,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFCCCCCC),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 18.sp,
            )
            if (!entry.isDirectory && entry.size > 0) {
                Text(
                    text = formatEntrySize(entry.size) + " (压缩: ${formatEntrySize(entry.compressedSize)})",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF777777),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun formatEntrySize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> "${"%.1f".format(bytes.toDouble() / (1024 * 1024))} MB"
}
