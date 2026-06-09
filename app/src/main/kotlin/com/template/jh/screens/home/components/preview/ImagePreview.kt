package com.template.jh.screens.home.components.preview

import android.graphics.BitmapFactory
import android.provider.OpenableColumns
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var containerWidth by remember { mutableFloatStateOf(0f) }
    var containerHeight by remember { mutableFloatStateOf(0f) }

    // 限制平移不超出可视边界
    fun clampOffsets() {
        if (scale <= 1f) {
            offsetX = 0f
            offsetY = 0f
            return
        }
        val excessX = containerWidth * (scale - 1f) / 2f
        val excessY = containerHeight * (scale - 1f) / 2f
        offsetX = offsetX.coerceIn(-excessX, excessX)
        offsetY = offsetY.coerceIn(-excessY, excessY)
    }

    LaunchedEffect(imagePath) {
        try {
            if (imagePath.startsWith("content://")) {
                val uri = android.net.Uri.parse(imagePath)
                // 从 ContentResolver 查询文件名和大小
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                        val name = if (nameIdx >= 0) cursor.getString(nameIdx) else "未知"
                        val size = if (sizeIdx >= 0) cursor.getLong(sizeIdx) else 0L
                        fileInfo = name to size
                    }
                }
                // 解码图片
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

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF1E1E1E))
            .onGloballyPositioned { coords ->
                containerWidth = coords.size.width.toFloat()
                containerHeight = coords.size.height.toFloat()
                clampOffsets()
            }
    ) {
        if (errorMsg != null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.BrokenImage, null,
                        Modifier.size(48.dp), tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f))
                    Spacer(Modifier.height(8.dp))
                    Text(errorMsg!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center)
                }
            }
        } else if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = "预览图片",
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offsetX
                        translationY = offsetY
                        clip = true
                    }
                    .pointerInput(Unit) {
                        detectTransformGestures { centroid, pan, zoom, _ ->
                            val newScale = (scale * zoom).coerceIn(0.5f, 5f)
                            if (zoom != 1f) {
                                // 以捏合中心为基准缩放，保持该点不动
                                val cx = centroid.x
                                val cy = centroid.y
                                val ratio = newScale / scale
                                offsetX = offsetX * ratio + cx * (1f - ratio)
                                offsetY = offsetY * ratio + cy * (1f - ratio)
                                scale = newScale
                            }
                            offsetX += pan.x
                            offsetY += pan.y
                            clampOffsets()
                        }
                    },
                contentScale = ContentScale.Fit,
            )

            // 底部信息栏
            fileInfo?.let { (name, size) ->
                Card(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(8.dp)
                        .fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xCC000000)
                    ),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Info, null,
                            Modifier.size(14.dp), tint = Color(0xFFAAAAAA))
                        Spacer(Modifier.width(6.dp))
                        Text(name,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFCCCCCC))
                        Spacer(Modifier.width(16.dp))
                        Text(formatFileSize(size),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFAAAAAA))
                        Spacer(Modifier.width(16.dp))
                        Text("${bitmap!!.width} × ${bitmap!!.height}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFAAAAAA))
                        Spacer(Modifier.weight(1f))
                        Text("${"%.0f".format(scale * 100)}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF888888))
                    }
                }
            }
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Image, null,
                        Modifier.size(48.dp), tint = Color(0xFF555555))
                    Spacer(Modifier.height(8.dp))
                    Text("加载中…",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF666666))
                }
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> "${"%.1f".format(bytes.toDouble() / (1024 * 1024))} MB"
}

fun formatTimestamp(millis: Long): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    return sdf.format(Date(millis))
}
