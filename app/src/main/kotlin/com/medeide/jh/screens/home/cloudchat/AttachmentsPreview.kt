package com.medeide.jh.screens.home.cloudchat

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 附件预览：显示已选文件/图片列表，支持移除 */
@Composable
fun AttachmentsPreview(
    filePaths: List<String>,
    imageUris: List<Uri>,
    onDetachFile: (String) -> Unit,
    onDetachImage: (Uri) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (filePaths.isEmpty() && imageUris.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth()) {
        if (imageUris.isNotEmpty()) {
            Text("图片", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 2.dp),
            ) {
                items(imageUris, key = { it.toString() }) { uri ->
                    ImageAttachmentChip(uri = uri, onDetach = { onDetachImage(uri) })
                }
            }
            Spacer(Modifier.height(6.dp))
        }
        if (filePaths.isNotEmpty()) {
            Text("文件", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp))
            filePaths.forEach { path ->
                FileAttachmentChip(path = path, onDetach = { onDetachFile(path) })
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun FileAttachmentChip(path: String, onDetach: () -> Unit) {
    val name = path.substringAfterLast('/').let {
        if (it.length > 35) it.take(32) + "..." else it
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Icon(Icons.Default.Description, null, Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(4.dp))
        Text(name, style = MaterialTheme.typography.labelSmall,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.width(4.dp))
        Icon(Icons.Default.Close, "移除", Modifier.size(14.dp).clickable { onDetach() },
            tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ImageAttachmentChip(uri: Uri, onDetach: () -> Unit) {
    val ctx = LocalContext.current
    val thumbnail = remember(uri) { loadThumbnail(ctx, uri) }

    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
    ) {
        if (thumbnail != null) {
            androidx.compose.foundation.Image(
                bitmap = thumbnail,
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().size(64.dp),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(Modifier.fillMaxWidth().size(64.dp), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Image, null, Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Icon(Icons.Default.Close, "移除", Modifier
            .align(Alignment.TopEnd)
            .size(16.dp)
            .clickable { onDetach() },
            tint = MaterialTheme.colorScheme.error)
    }
}

private fun loadThumbnail(ctx: Context, uri: Uri): ImageBitmap? {
    return try {
        val resolver = ctx.contentResolver
        val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
        resolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, opts)?.asImageBitmap()
        }
    } catch (_: Exception) {
        null
    }
}
