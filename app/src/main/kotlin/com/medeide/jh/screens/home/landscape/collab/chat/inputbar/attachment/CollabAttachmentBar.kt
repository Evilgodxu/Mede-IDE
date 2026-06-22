package com.medeide.jh.screens.home.landscape.collab.chat.inputbar.attachment

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image as ComposeImage
import com.medeide.jh.model.chat.AttachedFile

// 协作区消息输入框 - 附件展示区
@Composable
fun CollabAttachmentBar(
    attachedFileRefs: List<AttachedFile> = emptyList(),
    onDetachFile: (Int) -> Unit = {},
    attachedImageUris: List<android.net.Uri> = emptyList(),
    onDetachImage: (android.net.Uri) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    Column(modifier = modifier.fillMaxWidth()) {
        // 文件附件芯片（支持水平滚动）
        if (attachedFileRefs.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                attachedFileRefs.forEachIndexed { index, file ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(
                                start = 8.dp, end = 2.dp, top = 2.dp, bottom = 2.dp
                            )
                        ) {
                            Text(
                                "📄 ${file.name}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 160.dp)
                            )
                            IconButton(
                                onClick = { onDetachFile(index) },
                                Modifier.size(20.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    "移除",
                                    Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                }
            }
        }

        // 图片缩略图预览区
        if (attachedImageUris.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                attachedImageUris.forEach { uri ->
                    Box(modifier = Modifier.size(56.dp)) {
                        val thumbnail = remember(uri) { loadThumbnail(context, uri) }
                        thumbnail?.let { bmp ->
                            ComposeImage(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(6.dp)),
                                contentScale = ContentScale.Crop
                            )
                        } ?: Box(
                            Modifier
                                .size(56.dp)
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant,
                                    RoundedCornerShape(6.dp)
                                )
                        )
                        // 删除按钮
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(16.dp)
                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                .clickable { onDetachImage(uri) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Default.Close,
                                "移除",
                                Modifier.size(10.dp),
                                tint = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

// 从 content URI 加载缩略图（Compose 中缓存）
internal fun loadThumbnail(context: Context, uri: android.net.Uri): Bitmap? = try {
    val size = android.util.Size(200, 200)
    context.contentResolver.loadThumbnail(uri, size, null)
} catch (_: Exception) {
    null
}
