package com.template.jh.screens.home.components.resourcepanel

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.template.jh.screens.home.FileTypeUtil

object FileTreeIcon {
    fun icon(node: ResourceNode, isExpanded: Boolean): ImageVector = when {
        node.isDirectory && isExpanded -> Icons.Default.FolderOpen
        node.isDirectory -> Icons.Default.Folder
        FileTypeUtil.isImageFile(node.name) -> Icons.Default.Image
        FileTypeUtil.isAudioFile(node.name) -> Icons.Default.MusicNote
        FileTypeUtil.isVideoFile(node.name) -> Icons.Default.Videocam
        FileTypeUtil.isArchiveFile(node.name) -> Icons.Default.FolderZip
        FileTypeUtil.isTextFile(node.name) -> Icons.Default.Code
        else -> Icons.AutoMirrored.Filled.InsertDriveFile
    }

    fun tint(node: ResourceNode, primary: Color, onSurfaceVariant: Color): Color = when {
        node.isDirectory -> primary
        node.name.endsWith(".kt") || node.name.endsWith(".java") -> Color(0xFF7F52FF)
        node.name.endsWith(".xml") -> Color(0xFF2196F3)
        node.name.endsWith(".gradle") || node.name.endsWith(".kts") -> Color(0xFF00BCD4)
        else -> onSurfaceVariant
    }
}
