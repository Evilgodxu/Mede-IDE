package com.template.jh.screens.home.components.resourcepanel

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

// 单个扁平目录树节点 - 单一职责：渲染一个节点
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FlatTreeItem(
    node: ResourceNode,
    isExpanded: Boolean,
    isLoading: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    contextMenu: @Composable (Boolean, () -> Unit) -> Unit,
) {
    var showContextMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = { showContextMenu = true; onLongClick(); Unit },
            )
            .padding(start = (node.depth * 16).dp)
            .padding(vertical = 2.dp)
            .heightIn(min = 28.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (node.isDirectory) {
            if (isLoading) {
                CircularProgressIndicator(
                    Modifier.size(12.dp),
                    strokeWidth = 1.5.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = if (isExpanded) "折叠" else "展开",
                    modifier = Modifier.size(14.dp).padding(end = 2.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(2.dp))
        } else {
            Spacer(Modifier.width(16.dp))
        }

        Icon(
            imageVector = FileTreeIcon.icon(node, isExpanded),
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = FileTreeIcon.tint(node, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onSurfaceVariant),
        )

        Spacer(Modifier.width(6.dp))

        Text(
            text = node.name,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }

    Box {
        contextMenu(showContextMenu) { showContextMenu = false }
    }
}
