package com.template.jh.screens.home.components.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatIndentDecrease
import androidx.compose.material.icons.automirrored.filled.FormatIndentIncrease
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 触屏浮动操作栏
 *
 * 替代 PC 物理按键（Tab/Shift+Tab/Ctrl+/）的纯触屏方案。
 * 置于编辑器底部，拇指可及范围内。
 */
@Composable
fun EditorFloatingToolbar(
    onIndent: () -> Unit,
    onDedent: () -> Unit,
    onToggleComment: () -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
                RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
            )
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
            )
            .padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToolbarButton(
            icon = Icons.AutoMirrored.Filled.FormatIndentDecrease,
            desc = "减少缩进",
            onClick = onDedent,
        )
        ToolbarButton(
            icon = Icons.AutoMirrored.Filled.FormatIndentIncrease,
            desc = "增加缩进",
            onClick = onIndent,
        )
        ToolbarButton(
            icon = Icons.Default.Code,
            desc = "切换注释",
            onClick = onToggleComment,
        )

        // 分隔
        androidx.compose.material3.VerticalDivider(
            modifier = Modifier.height(28.dp),
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        )

        ToolbarButton(
            icon = Icons.Default.Remove,
            desc = "缩小字号",
            onClick = onZoomOut,
        )
        ToolbarButton(
            icon = Icons.Default.Add,
            desc = "放大字号",
            onClick = onZoomIn,
        )
    }
}

@Composable
private fun ToolbarButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    desc: String,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(44.dp),
        colors = IconButtonDefaults.iconButtonColors(
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
        ),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = desc,
            modifier = Modifier.size(22.dp),
        )
    }
}
