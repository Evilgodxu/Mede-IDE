package com.medeide.jh.screens.home.landscape.workspace.preview

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.io.File

data class WebFileEntry(
    val name: String,
    val path: String,
    val relativePath: String,
)

@Composable
fun PreviewPanel(
    projectDirPath: String,
    onPreviewFile: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var webFiles by remember { mutableStateOf<List<WebFileEntry>>(emptyList()) }
    var isScanning by remember { mutableStateOf(false) }

    fun scanWebFiles() {
        val dir = File(projectDirPath)
        if (!dir.isDirectory) return
        isScanning = true
        val result = mutableListOf<WebFileEntry>()
        dir.walkTopDown().forEach { file ->
            if (file.isFile) {
                val ext = file.extension.lowercase()
                if (ext in setOf("html", "htm", "xhtml")) {
                    val relPath = file.absolutePath.removePrefix(projectDirPath).trimStart('/')
                    result.add(WebFileEntry(file.name, file.absolutePath, relPath))
                }
            }
        }
        webFiles = result.sortedBy { it.relativePath }
        isScanning = false
    }

    LaunchedEffect(projectDirPath) {
        scanWebFiles()
    }

    Column(modifier = modifier.fillMaxSize().padding(8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = "Web 预览",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { scanWebFiles() }, enabled = !isScanning) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "重新扫描",
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "${webFiles.size} 个文件",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (projectDirPath.isEmpty()) {
            Text(
                text = "请先打开项目目录",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp),
            )
        } else if (webFiles.isEmpty()) {
            Text(
                text = if (isScanning) "扫描中..." else "未找到 HTML 文件",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp),
            )
        } else {
            LazyColumn {
                items(webFiles, key = { it.path }) { entry ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPreviewFile(entry.path) }
                            .padding(horizontal = 4.dp, vertical = 8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = entry.name,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = entry.relativePath,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                }
            }
        }
    }
}
