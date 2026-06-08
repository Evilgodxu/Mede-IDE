package com.template.jh.screens.home.components.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// 审查模式导航器 - 单一职责：diff块导航UI
@Composable
fun ReviewNavigator(
    totalChanges: Int,
    currentIndex: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onAcceptCurrent: () -> Unit,
    onRejectCurrent: () -> Unit,
    onOpenPanel: () -> Unit,
) {
    Column(
        modifier = Modifier
            .background(
                MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (totalChanges > 0) {
            Text(
                "${currentIndex + 1}/$totalChanges",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row {
            IconButton(onClick = onPrev, enabled = currentIndex > 0) {
                Icon(Icons.Default.KeyboardArrowUp, "上一个", tint = if (currentIndex > 0) MaterialTheme.colorScheme.onSurface else Color.Gray)
            }
            IconButton(onClick = onNext, enabled = currentIndex < totalChanges - 1) {
                Icon(Icons.Default.KeyboardArrowDown, "下一个", tint = if (currentIndex < totalChanges - 1) MaterialTheme.colorScheme.onSurface else Color.Gray)
            }
        }
        Row {
            IconButton(onClick = onAcceptCurrent) {
                Icon(Icons.Default.Check, "接受", tint = Color(0xFF22CC22))
            }
            IconButton(onClick = onRejectCurrent) {
                Icon(Icons.Default.Close, "拒绝", tint = Color(0xFFCC2222))
            }
        }
        IconButton(onClick = onOpenPanel) {
            Icon(Icons.Default.Description, "面板", modifier = Modifier.size(18.dp))
        }
    }
}
