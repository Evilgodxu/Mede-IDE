package com.medeide.jh.screens.home.cloudchat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.medeide.jh.model.chat.FileOpStatus
import com.medeide.jh.model.chat.FileOpType
import com.medeide.jh.model.chat.FileOperation

/** 文件操作状态卡片组（绑定在对应消息下方） */
@Composable
fun FileOperationsBar(
    operations: List<FileOperation>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        operations.forEach { op ->
            FileOperationCard(operation = op)
        }
    }
}

@Composable
private fun FileOperationCard(operation: FileOperation) {
    val bgColor = when (operation.status) {
        FileOpStatus.InProgress -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        FileOpStatus.Success -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        FileOpStatus.Error -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
    }
    val label = when (operation.type) {
        FileOpType.WriteFile -> "写入"
        FileOpType.CreateDirectory -> "创建目录"
        FileOpType.DeleteFile -> "删除"
        FileOpType.MoveFile -> "移动"
        FileOpType.CopyFile -> "复制"
    }
    val displayPath = operation.filePath.substringAfterLast('/').let {
        if (it.length > 40) it.take(37) + "..." else it
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusIndicator(operation.status)
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(4.dp))
        Text(displayPath, style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun StatusIndicator(status: FileOpStatus) {
    val (color, symbol) = when (status) {
        FileOpStatus.InProgress -> MaterialTheme.colorScheme.primary to "↻"
        FileOpStatus.Success -> MaterialTheme.colorScheme.tertiary to "✓"
        FileOpStatus.Error -> MaterialTheme.colorScheme.error to "✗"
    }
    Text(symbol, style = MaterialTheme.typography.labelSmall,
        color = color, fontWeight = FontWeight.Bold)
}
