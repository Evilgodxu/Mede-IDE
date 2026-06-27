package com.medeide.jh.screens.home.filebrowser.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.medeide.jh.screens.home.filebrowser.logic.FileEntry
import com.medeide.jh.core.utils.isArchiveFile
import com.medeide.jh.core.utils.isAudioFile
import com.medeide.jh.core.utils.isImageFile
import com.medeide.jh.core.utils.isTextFile
import com.medeide.jh.core.utils.isVideoFile

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileListRow(
    file: FileEntry,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onSwipe: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var offsetX by remember { mutableStateOf(0f) }
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else Color.Transparent,
        label = "selectionBg",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .offset { IntOffset(offsetX.toInt(), 0) }
            .pointerInput(file.absolutePath) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (kotlin.math.abs(offsetX) > 60f) onSwipe()
                        offsetX = 0f
                    },
                    onHorizontalDrag = { _, dragAmount -> offsetX += dragAmount },
                )
            }
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .background(bgColor)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isSelectionMode) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
            )
            Spacer(Modifier.width(8.dp))
        }
        Icon(
            imageVector = fileIcon(file),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = fileTint(file),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = file.name,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

private fun fileIcon(file: FileEntry): ImageVector = when (file) {
    is FileEntry.Archive -> Icons.Default.Archive
    is FileEntry.Real -> fileIcon(file.file.name, file.isDirectory)
}

private fun fileIcon(name: String, isDirectory: Boolean): ImageVector = when {
    isDirectory -> Icons.Default.Folder
    isImageFile(name) -> Icons.Default.Image
    isAudioFile(name) -> Icons.Default.MusicNote
    isVideoFile(name) -> Icons.Default.Videocam
    isArchiveFile(name) -> Icons.Default.Archive
    isTextFile(name) -> Icons.Default.Description
    else -> Icons.AutoMirrored.Filled.InsertDriveFile
}

@Composable
private fun fileTint(file: FileEntry): Color = when (file) {
    is FileEntry.Archive -> MaterialTheme.colorScheme.onSurfaceVariant
    is FileEntry.Real -> fileTint(file.file.name, file.isDirectory)
}

@Composable
private fun fileTint(name: String, isDirectory: Boolean): Color = when {
    isDirectory -> MaterialTheme.colorScheme.primary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}
