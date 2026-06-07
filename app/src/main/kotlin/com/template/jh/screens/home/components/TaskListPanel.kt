package com.template.jh.screens.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Pending
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.template.jh.core.ai.FileChangeItem
import com.template.jh.core.ai.FileOpType
import com.template.jh.data.model.TaskItem
import com.template.jh.data.model.TaskStatus

/**
 * 任务清单面板 - 显示当前对话的任务列表和相关文件
 */
@Composable
fun TaskListPanel(
    tasks: List<TaskItem>,
    fileChanges: List<FileChangeItem>,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onTaskClick: ((TaskItem) -> Unit)? = null,
    onFileClick: ((String) -> Unit)? = null,
    // 审查操作回调
    onAcceptAllChanges: ((String) -> Unit)? = null,
    onRejectAllChanges: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    // 分离主任务和子任务
    val mainTasks = tasks.filter { !it.isSubTask }
    val subTasks = tasks.filter { it.isSubTask }

    // 计算进度（只统计子任务，如果没有子任务则统计主任务）
    val progressTasks = if (subTasks.isNotEmpty()) subTasks else mainTasks
    val completedCount = progressTasks.count { it.status == TaskStatus.Completed }
    val totalCount = progressTasks.size
    val allCompleted = totalCount > 0 && completedCount == totalCount

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // 头部 - 显示任务进度统计
        TaskPanelHeader(
            completedCount = completedCount,
            totalCount = totalCount,
            allCompleted = allCompleted,
            hasFilesToReview = fileChanges.isNotEmpty(),
            isExpanded = isExpanded,
            onToggleExpand = onToggleExpand
        )

        if (tasks.isEmpty() && fileChanges.isEmpty()) {
            // 空状态
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "暂无任务",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                // 子任务列表（大模型生成的任务清单）
                if (subTasks.isNotEmpty()) {
                    item {
                        SectionTitle("任务清单")
                    }
                    items(subTasks.sortedBy { it.order }, key = { it.id }) { task ->
                        SubTaskItemRow(
                            task = task,
                            onClick = { onTaskClick?.invoke(task) }
                        )
                    }
                }

                // 主任务列表（如果没有子任务则显示）
                if (subTasks.isEmpty() && mainTasks.isNotEmpty()) {
                    item {
                        SectionTitle("任务列表")
                    }
                    items(mainTasks, key = { it.id }) { task ->
                        TaskItemRow(
                            task = task,
                            onClick = { onTaskClick?.invoke(task) }
                        )
                    }
                }

                // 完成后显示总结（如果有文件需要审查）
                if (allCompleted && fileChanges.isNotEmpty()) {
                    item {
                        CompletionSummaryCard(
                            fileCount = fileChanges.size,
                            onReviewClick = { /* 可以跳转到第一个文件 */ }
                        )
                    }
                }

                // 相关文件
                if (fileChanges.isNotEmpty()) {
                    item {
                        SectionTitle("涉及文件")
                    }
                    items(fileChanges, key = { it.filePath + it.timestamp }) { fileChange ->
                        FileChangeItemRow(
                            fileChange = fileChange,
                            onClick = { onFileClick?.invoke(fileChange.filePath) },
                            onAcceptAll = { onAcceptAllChanges?.invoke(fileChange.filePath) },
                            onRejectAll = { onRejectAllChanges?.invoke(fileChange.filePath) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskPanelHeader(
    completedCount: Int,
    totalCount: Int,
    allCompleted: Boolean,
    hasFilesToReview: Boolean,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit
) {
    val progressText = when {
        allCompleted && hasFilesToReview -> "任务完成 文件需要审查"
        totalCount > 0 -> "$completedCount/$totalCount 任务完成"
        else -> "任务清单"
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggleExpand)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val icon = when {
                    allCompleted && hasFilesToReview -> Icons.Default.Description
                    allCompleted -> Icons.Default.TaskAlt
                    else -> Icons.Default.CheckCircle
                }
                val tint = when {
                    allCompleted -> Color(0xFF22CC22)
                    else -> MaterialTheme.colorScheme.primary
                }
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = tint
                )
                Text(
                    text = progressText,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Icon(
                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (isExpanded) "收起" else "展开",
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    HorizontalDivider(
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    )
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    )
}

@Composable
private fun TaskItemRow(
    task: TaskItem,
    onClick: () -> Unit
) {
    val (icon, iconColor, bgColor) = when (task.status) {
        TaskStatus.Pending -> Triple(
            Icons.Default.Pending,
            MaterialTheme.colorScheme.onSurfaceVariant,
            Color.Transparent
        )
        TaskStatus.Running -> Triple(
            Icons.Default.PlayArrow,
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
        )
        TaskStatus.Completed -> Triple(
            Icons.Default.Check,
            Color(0xFF22CC22),
            Color(0x3322CC22)
        )
        TaskStatus.Failed -> Triple(
            Icons.Default.Error,
            MaterialTheme.colorScheme.error,
            MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
        )
        TaskStatus.WaitingAuth -> Triple(
            Icons.Default.HourglassEmpty,
            Color(0xFFFFA500),
            Color(0x33FFA500)
        )
        TaskStatus.Interrupted -> Triple(
            Icons.Default.Error,
            MaterialTheme.colorScheme.error,
            MaterialTheme.colorScheme.error.copy(alpha = 0.05f)
        )
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clickable(onClick = onClick),
        color = bgColor,
        shape = RoundedCornerShape(4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = iconColor
            )

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (task.description.isNotEmpty()) {
                    Text(
                        text = task.description,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // 状态标签
            val statusText = when (task.status) {
                TaskStatus.Pending -> "待处理"
                TaskStatus.Running -> "执行中"
                TaskStatus.Completed -> "完成"
                TaskStatus.Failed -> "失败"
                TaskStatus.WaitingAuth -> "待授权"
                TaskStatus.Interrupted -> "中断"
            }
            Text(
                text = statusText,
                style = MaterialTheme.typography.labelSmall,
                color = iconColor
            )
        }
    }
}

// 子任务项（大模型生成的任务清单）
@Composable
private fun SubTaskItemRow(
    task: TaskItem,
    onClick: () -> Unit
) {
    val isCompleted = task.status == TaskStatus.Completed
    val icon = if (isCompleted) Icons.Default.CheckCircle else Icons.Default.Circle
    val iconColor = if (isCompleted) Color(0xFF22CC22) else MaterialTheme.colorScheme.outline
    val textColor = if (isCompleted) {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val textDecoration = if (isCompleted) androidx.compose.ui.text.style.TextDecoration.LineThrough else null

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = iconColor
        )

        Text(
            text = task.title,
            style = MaterialTheme.typography.bodySmall,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textDecoration = textDecoration,
            modifier = Modifier.weight(1f)
        )
    }
}

// 完成总结卡片
@Composable
private fun CompletionSummaryCard(
    fileCount: Int,
    onReviewClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clickable(onClick = onReviewClick),
        color = Color(0xFF22CC22).copy(alpha = 0.1f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.TaskAlt,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = Color(0xFF22CC22)
            )

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "所有任务已完成",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "$fileCount 个文件需要审查",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = "去审查",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                color = Color(0xFF22CC22)
            )
        }
    }
}

@Composable
private fun FileChangeItemRow(
    fileChange: FileChangeItem,
    onClick: () -> Unit,
    onAcceptAll: () -> Unit,
    onRejectAll: () -> Unit
) {
    val fileName = fileChange.filePath.substringAfterLast('/')
    val dirPath = fileChange.filePath.substringBeforeLast('/', "")

    // 解析行数变化（+新增/-删除）
    val addedLines = if (fileChange.lineChanges > 0) fileChange.lineChanges else 0
    val deletedLines = if (fileChange.lineChanges < 0) -fileChange.lineChanges else 0

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clickable(onClick = onClick),
        color = Color.Transparent,
        shape = RoundedCornerShape(4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // 左侧：文件名
            Text(
                text = fileName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // 中间：文件路径（自动省略）
            if (dirPath.isNotEmpty()) {
                Text(
                    text = dirPath,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }

            // 右侧：新增/删除行数
            if (addedLines > 0) {
                Text(
                    text = "+$addedLines",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF22CC22),
                    fontWeight = FontWeight.Medium
                )
            }
            if (deletedLines > 0) {
                Text(
                    text = "-$deletedLines",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFCC2222),
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            // 确认按钮（√）
            IconButton(
                onClick = onAcceptAll,
                modifier = Modifier.size(24.dp),
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = Color(0xFF22CC22).copy(alpha = 0.15f)
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "接受所有修改",
                    modifier = Modifier.size(14.dp),
                    tint = Color(0xFF22CC22)
                )
            }

            // 拒绝按钮（×）
            IconButton(
                onClick = onRejectAll,
                modifier = Modifier.size(24.dp),
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = Color(0xFFCC2222).copy(alpha = 0.15f)
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "拒绝所有修改",
                    modifier = Modifier.size(14.dp),
                    tint = Color(0xFFCC2222)
                )
            }
        }
    }
}
