package com.medeide.jh.screens.home.landscape.workspace.preview.image

import android.graphics.BitmapFactory
import android.provider.OpenableColumns
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.io.File

@Composable
fun ImagePreview(
    imagePath: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var bitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var fileInfo by remember { mutableStateOf<Pair<String, Long>?>(null) }
    var scale by remember { mutableFloatStateOf(1f) }
    var showInfo by remember { mutableStateOf(true) }

    LaunchedEffect(imagePath) {
        bitmap = null
        errorMsg = null
        fileInfo = null
        scale = 1f
        try {
            if (imagePath.startsWith("content://")) {
                val uri = android.net.Uri.parse(imagePath)
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                        val name = if (nameIdx >= 0) cursor.getString(nameIdx) else "未知"
                        val size = if (sizeIdx >= 0) cursor.getLong(sizeIdx) else 0L
                        fileInfo = name to size
                    }
                }
                context.contentResolver.openInputStream(uri)?.use { input ->
                    bitmap = BitmapFactory.decodeStream(input)
                }
            } else {
                bitmap = BitmapFactory.decodeFile(imagePath)
                val f = File(imagePath)
                if (f.exists()) {
                    fileInfo = f.name to f.length()
                }
            }
            if (bitmap == null && errorMsg == null) {
                errorMsg = "无法解码图片"
            }
        } catch (e: Exception) {
            errorMsg = "加载失败: ${e.message}"
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        when {
            errorMsg != null -> ImageErrorPlaceholder(errorMsg!!, Modifier.fillMaxSize())
            bitmap != null -> ZoomableImage(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = "预览图片",
                onScaleChange = { scale = it },
                modifier = Modifier.fillMaxSize(),
            )
            else -> LoadingPlaceholder(Modifier.fillMaxSize())
        }

        fileInfo?.let { (name, sizeBytes) ->
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
            ) {
                if (showInfo) {
                    Column(modifier = Modifier.clickable { showInfo = false }) {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFEEEEEE),
                        )
                        Text(
                            text = formatFileSize(sizeBytes),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFBBBBBB),
                        )
                        Text(
                            text = "${bitmap?.width ?: 0} × ${bitmap?.height ?: 0}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFAAAAAA),
                        )
                        Text(
                            text = "${"%.0f".format(scale * 100)}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF999999),
                        )
                    }
                } else {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "显示信息",
                        modifier = Modifier
                            .size(20.dp)
                            .clickable { showInfo = true },
                        tint = Color(0xFFEEEEEE),
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadingPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.Image,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = Color(0xFF555555),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "加载中…",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF666666),
                textAlign = TextAlign.Center,
            )
        }
    }
}

private fun formatFileSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> "${"%.1f".format(bytes.toDouble() / (1024 * 1024))} MB"
}
