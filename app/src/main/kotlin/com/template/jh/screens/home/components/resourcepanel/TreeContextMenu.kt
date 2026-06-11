package com.template.jh.screens.home.components.resourcepanel

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.OpenInNew
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.template.jh.R

// 双列菜单项
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
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Icon(icon, null, Modifier.size(16.dp), tint = if (tint != Color.Unspecified) tint else MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.width(8.dp))
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

// 双列行
@Composable
private fun ColumnScope.MenuRow(
    left: @Composable ColumnScope.() -> Unit,
    right: @Composable ColumnScope.() -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.weight(1f)) { Column { left() } }
        Box(modifier = Modifier.weight(1f)) { Column { right() } }
    }
}

// 目录树右键/长按菜单
@Composable
fun TreeContextMenu(
    expanded: Boolean,
    node: ResourceNode,
    selectedCount: Int = 1,
    selectedPaths: List<String> = emptyList(),
    archivePath: String? = null,
    onDismiss: () -> Unit,
    onOpenFile: () -> Unit,
    onAddToConversation: () -> Unit,
    onCreateFile: () -> Unit,
    onCreateDirectory: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onCopyToLeft: (() -> Unit)? = null,
    onCopyToRight: (() -> Unit)? = null,
    onMoveToLeft: (() -> Unit)? = null,
    onMoveToRight: (() -> Unit)? = null,
    onExtractToLeft: ((List<String>) -> Unit)? = null,
    onExtractToRight: ((List<String>) -> Unit)? = null,
    onCopyName: () -> Unit,
    onCopyPath: () -> Unit,
    onViewInfo: (() -> Unit)? = null,
    onCompress: (() -> Unit)? = null,
    onOpenAsProject: (() -> Unit)? = null,
    currentProjectPath: String = "",
) {
    val context = LocalContext.current
    val hasProjectSet = currentProjectPath.isNotBlank()
    val showOpenAsProject = onOpenAsProject != null && node.isDirectory && !hasProjectSet
    val isMultiSelect = selectedCount > 1
    val screenHeightDp = LocalConfiguration.current.screenHeightDp
    val hasCopyOrMove = onCopyToLeft != null || onCopyToRight != null ||
        onMoveToLeft != null || onMoveToRight != null || archivePath != null

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier
            .widthIn(min = 260.dp)
            .heightIn(max = (screenHeightDp * 0.75f).dp)
            .padding(vertical = 4.dp),
    ) {
        // 滚动容器
        val scrollState = rememberScrollState()
        Column(modifier = Modifier.verticalScroll(scrollState)) {
            // Group 1: 打开 + 添加到对话
            if (!node.isDirectory && !isMultiSelect) {
                MenuRow(
                    left = { MenuCell("打开文件", Icons.AutoMirrored.Filled.OpenInNew, { onDismiss(); onOpenFile(); Unit }) },
                    right = { MenuCell(stringResource(R.string.add_to_conversation), Icons.Default.Add, { onDismiss(); onAddToConversation(); Unit }) },
                )
            } else {
                MenuCell(stringResource(R.string.add_to_conversation), Icons.Default.Add, { onDismiss(); onAddToConversation(); Unit })
            }
            HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp))

            // Group 2: 复制/移动/解压（仅单选时显示）
            if (!isMultiSelect) {
                if (onCopyToLeft != null && onCopyToRight != null) {
                    MenuRow(
                        left = { MenuCell("复制到左侧", Icons.AutoMirrored.Filled.ArrowBack, { onDismiss(); onCopyToLeft(); Unit }) },
                        right = { MenuCell("复制到右侧", Icons.AutoMirrored.Filled.ArrowForward, { onDismiss(); onCopyToRight(); Unit }) },
                    )
                } else {
                    onCopyToLeft?.let { MenuCell("复制到左侧", Icons.AutoMirrored.Filled.ArrowBack, { onDismiss(); it(); Unit }) }
                    onCopyToRight?.let { MenuCell("复制到右侧", Icons.AutoMirrored.Filled.ArrowForward, { onDismiss(); it(); Unit }) }
                }
                if (onMoveToLeft != null && onMoveToRight != null) {
                    MenuRow(
                        left = { MenuCell("移动到左侧", Icons.AutoMirrored.Filled.ArrowBack, { onDismiss(); onMoveToLeft(); Unit }) },
                        right = { MenuCell("移动到右侧", Icons.AutoMirrored.Filled.ArrowForward, { onDismiss(); onMoveToRight(); Unit }) },
                    )
                } else {
                    onMoveToLeft?.let { MenuCell("移动到左侧", Icons.AutoMirrored.Filled.ArrowBack, { onDismiss(); it(); Unit }) }
                    onMoveToRight?.let { MenuCell("移动到右侧", Icons.AutoMirrored.Filled.ArrowForward, { onDismiss(); it(); Unit }) }
                }
                if (archivePath != null) {
                    if (onExtractToLeft != null && onExtractToRight != null) {
                        MenuRow(
                            left = { MenuCell("解压到左侧", Icons.AutoMirrored.Filled.ArrowBack, { onDismiss(); onExtractToLeft(selectedPaths); Unit }) },
                            right = { MenuCell("解压到右侧", Icons.AutoMirrored.Filled.ArrowForward, { onDismiss(); onExtractToRight(selectedPaths); Unit }) },
                        )
                    } else {
                        onExtractToLeft?.let { MenuCell("解压到左侧", Icons.AutoMirrored.Filled.ArrowBack, { onDismiss(); it(selectedPaths); Unit }) }
                        onExtractToRight?.let { MenuCell("解压到右侧", Icons.AutoMirrored.Filled.ArrowForward, { onDismiss(); it(selectedPaths); Unit }) }
                    }
                }
                if (hasCopyOrMove) {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp))
                }
            }

            // Group 3: 目录操作
            if (node.isDirectory && !isMultiSelect) {
                if (showOpenAsProject) {
                    MenuCell("以工作目录打开", Icons.Default.FolderOpen, { onDismiss(); onOpenAsProject(); Unit })
                }
                val createFileFn = onCreateFile
                val createDirFn = onCreateDirectory
                if (createFileFn != null && createDirFn != null) {
                    MenuRow(
                        left = { MenuCell("新建文件", Icons.Default.Add, { onDismiss(); createFileFn(); Unit }) },
                        right = { MenuCell("新建目录", Icons.Default.Folder, { onDismiss(); createDirFn(); Unit }) },
                    )
                } else {
                    createFileFn?.let { MenuCell("新建文件", Icons.Default.Add, { onDismiss(); it(); Unit }) }
                    createDirFn?.let { MenuCell("新建目录", Icons.Default.Folder, { onDismiss(); it(); Unit }) }
                }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp))
            }

            // Group 4: 重命名 + 压缩
            if (!isMultiSelect && onCompress != null) {
                MenuRow(
                    left = { MenuCell("重命名", Icons.Default.DriveFileRenameOutline, { onDismiss(); onRename(); Unit }) },
                    right = { MenuCell(if (isMultiSelect) "压缩 $selectedCount 项" else "压缩", Icons.Default.Archive, { onDismiss(); onCompress(); Unit }) },
                )
            } else {
                if (!isMultiSelect) {
                    MenuCell("重命名", Icons.Default.DriveFileRenameOutline, { onDismiss(); onRename(); Unit })
                }
                onCompress?.let { MenuCell(if (isMultiSelect) "压缩 $selectedCount 项" else "压缩", Icons.Default.Archive, { onDismiss(); it(); Unit }) }
            }

            // Group 5: 复制名称 + 复制路径
            MenuRow(
                left = { MenuCell("复制名称", Icons.Default.ContentCopy, { onDismiss(); onCopyName(); Unit }) },
                right = { MenuCell("复制路径", Icons.Default.ContentCopy, { onDismiss(); onCopyPath(); Unit }) },
            )

            // Group 6: 查看信息
            if (onViewInfo != null && !isMultiSelect) {
                MenuCell("查看信息", Icons.Default.Info, { onDismiss(); onViewInfo(); Unit })
            }

            HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp))

            // 删除
            MenuCell(
                text = if (isMultiSelect) "删除 $selectedCount 项" else "删除",
                icon = Icons.Default.Delete,
                onClick = { onDismiss(); onDelete(); Unit },
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}
