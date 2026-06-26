package com.medeide.jh.screens.home.filebrowser.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Archive
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.medeide.jh.screens.home.filebrowser.PanelSide

@Composable
fun FileContextMenu(
    expanded: Boolean,
    panelSide: PanelSide,
    isArchive: Boolean,
    isInsideArchive: Boolean = false,
    isDirectory: Boolean = false,
    isProjectModeActive: Boolean = false,
    selectedCount: Int,
    onDismiss: () -> Unit,
    onCopyName: () -> Unit,
    onCopyPath: () -> Unit,
    onRename: () -> Unit,
    onCompress: () -> Unit,
    onExtract: () -> Unit,
    onExtractToLeft: () -> Unit = {},
    onExtractToRight: () -> Unit = {},
    onInfo: () -> Unit,
    onDelete: () -> Unit,
    onMoveToOtherSide: () -> Unit,
    onCopyToOtherSide: () -> Unit,
    onAddToConversation: () -> Unit = {},
    onOpenAsProject: () -> Unit = {},
    onExitProjectMode: () -> Unit = {},
    showCrossPanelActions: Boolean = true,
) {
    if (!expanded) return

    DropdownMenu(
        expanded = true,
        onDismissRequest = onDismiss,
        modifier = Modifier.padding(vertical = 4.dp),
    ) {
        if (isInsideArchive) {
            // ── 压缩包内条目菜单 ──
            if (showCrossPanelActions) {
                MenuRow(
                    left = {
                        MenuCell(
                            "解压到左侧",
                            Icons.Default.Archive,
                            onClick = { onDismiss(); onExtractToLeft() },
                        )
                    },
                    right = {
                        MenuCell(
                            "解压到右侧",
                            Icons.Default.Archive,
                            onClick = { onDismiss(); onExtractToRight() },
                        )
                    },
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp))
            } else {
                MenuCell(
                    "解压到当前目录",
                    Icons.Default.Archive,
                    onClick = { onDismiss(); onExtract() },
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp))
            }
            MenuRow(
                left = {
                    MenuCell(
                        "复制名称",
                        Icons.Default.ContentPaste,
                        onClick = { onDismiss(); onCopyName() },
                    )
                },
                right = {
                    MenuCell(
                        "复制路径",
                        Icons.Default.ContentCopy,
                        onClick = { onDismiss(); onCopyPath() },
                    )
                },
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp))
            MenuCell(
                "属性",
                Icons.Default.Info,
                onClick = { onDismiss(); onInfo() },
            )
        } else {
            // ── 普通文件/目录/真实压缩包菜单 ──
            // 添加到对话
            MenuCell(
                "添加到对话",
                Icons.Default.Add,
                onClick = { onDismiss(); onAddToConversation() },
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp))

            // 进入/退出工作模式（仅目录、单选时显示）
            if (isDirectory && !isProjectModeActive && selectedCount <= 1) {
                MenuCell(
                    "进入工作模式",
                    Icons.Default.FolderOpen,
                    onClick = { onDismiss(); onOpenAsProject() },
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp))
            } else if (isDirectory && isProjectModeActive && selectedCount <= 1) {
                MenuCell(
                    "退出工作模式",
                    Icons.Default.FolderOpen,
                    onClick = { onDismiss(); onExitProjectMode() },
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp))
            }

            if (showCrossPanelActions) {
                val targetDir = if (panelSide == PanelSide.Left) "右侧" else "左侧"
                MenuRow(
                    left = {
                        MenuCell(
                            "移动至$targetDir",
                            Icons.Default.ContentPaste,
                            onClick = { onDismiss(); onMoveToOtherSide() },
                        )
                    },
                    right = {
                        MenuCell(
                            "复制至$targetDir",
                            Icons.Default.ContentCopy,
                            onClick = { onDismiss(); onCopyToOtherSide() },
                        )
                    },
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp))
            }

            MenuRow(
                left = {
                    MenuCell(
                        "复制名称",
                        Icons.Default.ContentPaste,
                        onClick = { onDismiss(); onCopyName() },
                    )
                },
                right = {
                    MenuCell(
                        "复制路径",
                        Icons.Default.ContentCopy,
                        onClick = { onDismiss(); onCopyPath() },
                    )
                },
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp))

            MenuRow(
                left = {
                    MenuCell(
                        "重命名",
                        Icons.Default.DriveFileRenameOutline,
                        onClick = { onDismiss(); onRename() },
                    )
                },
                right = {
                    if (isArchive) {
                        if (showCrossPanelActions) {
                            MenuCell(
                                "解压",
                                Icons.Default.Archive,
                                onClick = { onDismiss(); onExtract() },
                            )
                        } else {
                            MenuCell(
                                "解压到当前目录",
                                Icons.Default.Archive,
                                onClick = { onDismiss(); onExtract() },
                            )
                        }
                    } else {
                        val label = if (selectedCount > 1) "压缩 $selectedCount 项" else "压缩"
                        MenuCell(
                            label,
                            Icons.Default.Archive,
                            onClick = { onDismiss(); onCompress() },
                        )
                    }
                },
            )

            if (isArchive && showCrossPanelActions) {
                HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp))
                MenuRow(
                    left = {
                        MenuCell(
                            "解压到左侧",
                            Icons.Default.Archive,
                            onClick = { onDismiss(); onExtractToLeft() },
                        )
                    },
                    right = {
                        MenuCell(
                            "解压到右侧",
                            Icons.Default.Archive,
                            onClick = { onDismiss(); onExtractToRight() },
                        )
                    },
                )
            }

            HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp))

            val deleteLabel = if (selectedCount > 1) "删除 $selectedCount 项" else "删除"
            MenuRow(
                left = {
                    MenuCell(
                        "属性",
                        Icons.Default.Info,
                        onClick = { onDismiss(); onInfo() },
                    )
                },
                right = {
                    MenuCell(
                        text = deleteLabel,
                        icon = Icons.Default.Delete,
                        onClick = { onDismiss(); onDelete() },
                        tint = MaterialTheme.colorScheme.error,
                    )
                },
            )
        }
    }
}

@Composable
private fun ColumnScope.MenuCell(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    tint: Color = Color.Unspecified,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = if (tint != Color.Unspecified) tint else MaterialTheme.colorScheme.onSurface,
            )
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

@Composable
private fun ColumnScope.MenuRow(
    left: @Composable ColumnScope.() -> Unit,
    right: @Composable ColumnScope.() -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.weight(1f, fill = false)) { Column { left() } }
        Box(modifier = Modifier.weight(1f, fill = false)) { Column { right() } }
    }
}
