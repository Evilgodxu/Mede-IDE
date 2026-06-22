package com.medeide.jh.screens.home.landscape.sidebar.resourcepanel

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.medeide.jh.R

// 左列菜单项
@Composable
private fun ColumnScope.MenuCell(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    tint: Color = Color.Unspecified,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        androidx.compose.foundation.layout.Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Icon(icon, null, Modifier.size(16.dp), tint = if (tint != Color.Unspecified) tint else MaterialTheme.colorScheme.onSurface)
            androidx.compose.foundation.layout.Spacer(Modifier.width(8.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = if (tint != Color.Unspecified) tint else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// 左列长按菜单（单列布局，无留白）
@Composable
fun LeftTreeContextMenu(
    expanded: Boolean,
    node: ResourceNode,
    selectedCount: Int = 1,
    selectedPaths: List<String> = emptyList(),
    archivePath: String? = null,
    onDismiss: () -> Unit,
    onAddToConversation: () -> Unit,
    onCreateFile: () -> Unit,
    onCreateDirectory: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onCopyToRight: (() -> Unit)? = null,
    onMoveToRight: (() -> Unit)? = null,
    onExtractToRight: ((List<String>) -> Unit)? = null,
    onCopyName: () -> Unit,
    onCopyPath: () -> Unit,
    onViewInfo: (() -> Unit)? = null,
    onCompress: (() -> Unit)? = null,
    onOpenAsProject: (() -> Unit)? = null,
    onExitProjectMode: (() -> Unit)? = null,
    isProjectModeActive: Boolean = false,
) {
    val showOpenAsProject = onOpenAsProject != null && node.isDirectory && !isProjectModeActive
    val showExitProject = onExitProjectMode != null && isProjectModeActive
    val isMultiSelect = selectedCount > 1
    val screenHeightDp = LocalConfiguration.current.screenHeightDp
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier
            .widthIn(min = 200.dp)
            .heightIn(max = (screenHeightDp * 0.75f).dp)
            .padding(vertical = 4.dp),
    ) {
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = (screenHeightDp * 0.75f).dp)
                .verticalScroll(scrollState)
        ) {
            // 添加到对话
            MenuCell(stringResource(R.string.add_to_conversation), Icons.Default.Add, { onDismiss(); onAddToConversation() })
            HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp))

            // 进入工作模式 / 退出工作模式（仅目录，单选）
            if (showOpenAsProject && !isMultiSelect) {
                MenuCell("进入工作模式", Icons.Default.FolderOpen, { onDismiss(); onOpenAsProject?.invoke() })
                HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp))
            } else if (showExitProject && !isMultiSelect) {
                MenuCell("退出工作模式", Icons.Default.FolderOpen, { onDismiss(); onExitProjectMode?.invoke() })
                HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp))
            }

            // 移动到右侧 / 复制到右侧（仅单选）
            if (!isMultiSelect) {
                onMoveToRight?.let {
                    MenuCell("移动到右侧", Icons.AutoMirrored.Filled.ArrowForward, { onDismiss(); it() })
                }
                onCopyToRight?.let {
                    MenuCell("复制到右侧", Icons.AutoMirrored.Filled.ArrowForward, { onDismiss(); it() })
                }
                if (onMoveToRight != null || onCopyToRight != null) {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp))
                }
            }

            // 解压到右侧（在压缩包内浏览时可用）
            if (archivePath != null && onExtractToRight != null) {
                val extractPaths = selectedPaths.ifEmpty { listOf("") }
                MenuCell("解压到右侧", Icons.AutoMirrored.Filled.ArrowForward, { onDismiss(); onExtractToRight(extractPaths) })
                HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp))
            }

            // 复制名称 / 复制路径
            MenuRow(
                left = { MenuCell("复制名称", Icons.Default.ContentCopy, { onDismiss(); onCopyName() }) },
                right = { MenuCell("复制路径", Icons.Default.ContentCopy, { onDismiss(); onCopyPath() }) },
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp))

            // 重命名 / 压缩（仅单选）
            if (!isMultiSelect) {
                MenuRow(
                    left = { MenuCell("重命名", Icons.Default.DriveFileRenameOutline, { onDismiss(); onRename() }) },
                    right = { onCompress?.let { MenuCell("压缩", Icons.Default.Archive, { onDismiss(); it() }) } },
                )
            } else {
                onCompress?.let { MenuCell("压缩 $selectedCount 项", Icons.Default.Archive, { onDismiss(); it() }) }
            }

            // 属性（仅单选）
            if (onViewInfo != null && !isMultiSelect) {
                MenuCell("属性", Icons.Default.Info, { onDismiss(); onViewInfo() })
            }

            HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp))

            // 删除
            MenuCell(
                text = if (isMultiSelect) "删除 $selectedCount 项" else "删除",
                icon = Icons.Default.Delete,
                onClick = { onDismiss(); onDelete() },
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}

// 双列行（用于新建文件/目录、重命名/压缩、复制名称/路径）
@Composable
private fun ColumnScope.MenuRow(
    left: @Composable ColumnScope.() -> Unit,
    right: @Composable ColumnScope.() -> Unit,
) {
    androidx.compose.foundation.layout.Row(modifier = Modifier.fillMaxWidth()) {
        androidx.compose.foundation.layout.Box(modifier = Modifier.weight(1f)) { Column { left() } }
        androidx.compose.foundation.layout.Box(modifier = Modifier.weight(1f)) { Column { right() } }
    }
}
