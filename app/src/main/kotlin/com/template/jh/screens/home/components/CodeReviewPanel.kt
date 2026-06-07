package com.template.jh.screens.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.template.jh.core.editor.ChangeBlock
import com.template.jh.core.editor.ChangeBlockStatus
import com.template.jh.core.editor.CodeReviewState
import com.template.jh.core.editor.LineChangeType
import com.template.jh.core.editor.backgroundColor
import com.template.jh.core.editor.color
import com.template.jh.core.editor.displayText

/**
 * 代码审查面板：显示所有修改块，支持逐项确认/拒绝
 */
@Composable
fun CodeReviewPanel(
    reviewState: CodeReviewState,
    onAcceptBlock: (Int) -> Unit,
    onRejectBlock: (Int) -> Unit,
    onNavigateToBlock: (Int) -> Unit,
    fontSizeSp: Float = 12f,
    lineHeightSp: Float = 18f,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    val lineHeightDp = lineHeightSp.sp.value.dp

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // 顶部统计信息
        ReviewStatsHeader(
            pendingCount = reviewState.pendingCount,
            acceptedCount = reviewState.acceptedCount,
            rejectedCount = reviewState.rejectedCount,
            totalCount = reviewState.totalCount
        )

        // 修改块列表
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(scrollState)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            reviewState.changeBlocks.forEachIndexed { index, block ->
                ChangeBlockCard(
                    block = block,
                    index = index,
                    isCurrent = index == reviewState.currentBlockIndex,
                    fontSizeSp = fontSizeSp,
                    lineHeightSp = lineHeightSp,
                    lineHeightDp = lineHeightDp,
                    onAccept = { onAcceptBlock(index) },
                    onReject = { onRejectBlock(index) },
                    onClick = { onNavigateToBlock(index) }
                )
            }
        }

        // 底部操作栏
        ReviewActionBar(
            onAcceptAll = {
                reviewState.changeBlocks.forEachIndexed { index, block ->
                    if (block.status == ChangeBlockStatus.PENDING) {
                        onAcceptBlock(index)
                    }
                }
            },
            onRejectAll = {
                reviewState.changeBlocks.forEachIndexed { index, block ->
                    if (block.status == ChangeBlockStatus.PENDING) {
                        onRejectBlock(index)
                    }
                }
            },
            canAcceptAll = reviewState.pendingCount > 0,
            canRejectAll = reviewState.pendingCount > 0
        )
    }
}

/**
 * 统计信息头部
 */
@Composable
private fun ReviewStatsHeader(
    pendingCount: Int,
    acceptedCount: Int,
    rejectedCount: Int,
    totalCount: Int,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        StatItem("待审查", pendingCount, Color(0xFFCCAA00))
        StatItem("已接受", acceptedCount, Color(0xFF22CC22))
        StatItem("已拒绝", rejectedCount, Color(0xFFCC2222))
        StatItem("总计", totalCount, MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun StatItem(label: String, count: Int, color: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.titleMedium,
            color = color,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 修改块卡片
 */
@Composable
private fun ChangeBlockCard(
    block: ChangeBlock,
    index: Int,
    isCurrent: Boolean,
    fontSizeSp: Float,
    lineHeightSp: Float,
    lineHeightDp: Dp,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    onClick: () -> Unit,
) {
    val borderColor = when {
        isCurrent -> MaterialTheme.colorScheme.primary
        block.isLocked -> block.status.color().copy(alpha = 0.5f)
        else -> MaterialTheme.colorScheme.outlineVariant
    }

    val bgAlpha = if (block.isLocked) 0.3f else 1f

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = block.status.backgroundColor().copy(alpha = bgAlpha),
                shape = RoundedCornerShape(8.dp)
            )
            .border(
                width = if (isCurrent) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        // 头部：状态标识 + 操作按钮
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 状态标签
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // 序号
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(
                            color = block.status.color().copy(alpha = 0.2f),
                            shape = RoundedCornerShape(4.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${index + 1}",
                        style = MaterialTheme.typography.labelSmall,
                        color = block.status.color(),
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                }

                // 状态文本
                if (block.isLocked) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = block.status.color()
                    )
                }
                Text(
                    text = block.status.displayText(),
                    style = MaterialTheme.typography.labelSmall,
                    color = block.status.color(),
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                )
            }

            // 操作按钮（仅未锁定时显示）
            if (!block.isLocked) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // 拒绝按钮
                    IconButton(
                        onClick = onReject,
                        modifier = Modifier.size(28.dp),
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = Color(0xFFCC2222).copy(alpha = 0.15f)
                        )
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "拒绝",
                            modifier = Modifier.size(16.dp),
                            tint = Color(0xFFCC2222)
                        )
                    }
                    // 接受按钮
                    IconButton(
                        onClick = onAccept,
                        modifier = Modifier.size(28.dp),
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = Color(0xFF22CC22).copy(alpha = 0.15f)
                        )
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "接受",
                            modifier = Modifier.size(16.dp),
                            tint = Color(0xFF22CC22)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 代码对比
        Row(
            modifier = Modifier.fillMaxWidth()
        ) {
            // 旧代码列
            if (block.oldContent.isNotEmpty()) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "原代码",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                    )
                    block.oldContent.forEach { line ->
                        Text(
                            text = if (line.isEmpty()) " " else line,
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = fontSizeSp.sp,
                                color = MaterialTheme.colorScheme.error,
                                lineHeight = lineHeightSp.sp,
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.height(lineHeightDp)
                        )
                    }
                }
            }

            if (block.oldContent.isNotEmpty() && block.newContent.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .padding(horizontal = 4.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
            }

            // 新代码列
            if (block.newContent.isNotEmpty()) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "新代码",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF22CC22).copy(alpha = 0.8f)
                    )
                    block.newContent.forEach { line ->
                        Text(
                            text = if (line.isEmpty()) " " else line,
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = fontSizeSp.sp,
                                color = Color(0xFF22CC22),
                                lineHeight = lineHeightSp.sp,
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.height(lineHeightDp)
                        )
                    }
                }
            }
        }

        // 行号信息
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.End
        ) {
            val info = buildString {
                if (block.oldStartLine > 0) {
                    append("原${block.oldStartLine}")
                    if (block.oldEndLine - block.oldStartLine > 1) {
                        append("-${block.oldEndLine - 1}")
                    }
                }
                if (block.oldStartLine > 0 && block.newStartLine > 0) {
                    append(" → ")
                }
                if (block.newStartLine > 0) {
                    append("新${block.newStartLine}")
                    if (block.newEndLine - block.newStartLine > 1) {
                        append("-${block.newEndLine - 1}")
                    }
                }
            }
            Text(
                text = info,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}

/**
 * 底部操作栏
 */
@Composable
private fun ReviewActionBar(
    onAcceptAll: () -> Unit,
    onRejectAll: () -> Unit,
    canAcceptAll: Boolean,
    canRejectAll: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        // 全部拒绝
        androidx.compose.material3.TextButton(
            onClick = onRejectAll,
            enabled = canRejectAll,
            colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                contentColor = Color(0xFFCC2222)
            )
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text("全部拒绝")
        }

        // 全部接受
        androidx.compose.material3.TextButton(
            onClick = onAcceptAll,
            enabled = canAcceptAll,
            colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                contentColor = Color(0xFF22CC22)
            )
        ) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text("全部接受")
        }
    }
}

/**
 * 精简版审查导航器（用于编辑器右上角）
 */
@Composable
fun ReviewNavigator(
    reviewState: CodeReviewState,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onAcceptCurrent: () -> Unit,
    onRejectCurrent: () -> Unit,
    onOpenPanel: () -> Unit,
) {
    val currentBlock = reviewState.changeBlocks.getOrNull(reviewState.currentBlockIndex)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
                RoundedCornerShape(20.dp)
            )
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .border(
                1.dp,
                MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                RoundedCornerShape(20.dp)
            )
    ) {
        // 上一条
        IconButton(
            onClick = onPrev,
            modifier = Modifier.size(24.dp),
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
            ),
        ) {
            Icon(Icons.Default.KeyboardArrowUp, "上一处", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
        }

        // 计数显示
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.clickable(onClick = onOpenPanel)
        ) {
            Text(
                text = "${reviewState.currentBlockIndex + 1}/${reviewState.totalCount}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "剩${reviewState.pendingCount}",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }

        // 下一条
        IconButton(
            onClick = onNext,
            modifier = Modifier.size(24.dp),
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
            ),
        ) {
            Icon(Icons.Default.KeyboardArrowDown, "下一处", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
        }

        // 分隔线
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(16.dp)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        )

        // 当前块状态
        currentBlock?.let { block ->
            if (block.isLocked) {
                // 已锁定状态
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = block.status.displayText(),
                    modifier = Modifier
                        .size(20.dp)
                        .padding(horizontal = 4.dp),
                    tint = block.status.color()
                )
            } else {
                // 拒绝当前
                IconButton(
                    onClick = onRejectCurrent,
                    modifier = Modifier.size(28.dp),
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = Color(0xFFCC2222).copy(alpha = 0.15f)
                    ),
                ) {
                    Icon(Icons.Default.Close, "拒绝", Modifier.size(16.dp), tint = Color(0xFFCC2222))
                }
                // 接受当前
                IconButton(
                    onClick = onAcceptCurrent,
                    modifier = Modifier.size(28.dp),
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = Color(0xFF22CC22).copy(alpha = 0.15f)
                    ),
                ) {
                    Icon(Icons.Default.Check, "接受", Modifier.size(16.dp), tint = Color(0xFF22CC22))
                }
            }
        }
    }
}
