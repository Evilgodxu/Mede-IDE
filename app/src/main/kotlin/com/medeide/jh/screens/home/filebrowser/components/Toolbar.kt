package com.medeide.jh.screens.home.filebrowser.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@Composable
fun NormalToolbar(
    canGoUp: Boolean,
    canGoForward: Boolean,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onCreate: () -> Unit,
    onSync: () -> Unit,
    onReset: () -> Unit,
    showSync: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        ToolbarButton(
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "后退",
            enabled = canGoUp,
            onClick = onBack,
        )
        ToolbarButton(
            icon = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = "前进",
            enabled = canGoForward,
            onClick = onForward,
        )
        ToolbarButton(
            icon = Icons.Default.Add,
            contentDescription = "新建",
            onClick = onCreate,
        )
        if (showSync) {
            ToolbarButton(
                icon = Icons.Default.Sync,
                contentDescription = "同步",
                onClick = onSync,
            )
        }
        ToolbarButton(
            icon = Icons.Default.Home,
            contentDescription = "重置",
            onClick = onReset,
        )
    }
}

@Composable
fun SelectionToolbar(
    onSelectAll: () -> Unit,
    onInvert: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        TextButton(onClick = onSelectAll) {
            Icon(Icons.Default.SelectAll, contentDescription = null, modifier = Modifier.size(18.dp))
            Text("全选", modifier = Modifier.padding(start = 4.dp))
        }
        TextButton(onClick = onInvert) {
            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Text("反选", modifier = Modifier.padding(start = 4.dp))
        }
        TextButton(onClick = onCancel) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
            Text("取消", modifier = Modifier.padding(start = 4.dp))
        }
    }
}

@Composable
private fun ToolbarButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, enabled = enabled) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(20.dp),
            tint = if (enabled) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
        )
    }
}
