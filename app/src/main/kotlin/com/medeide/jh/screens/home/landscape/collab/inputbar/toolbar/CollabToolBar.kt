package com.medeide.jh.screens.home.landscape.collab.inputbar.toolbar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.medeide.jh.ui.components.VoiceInputButton

@Composable
fun CollabToolBar(
    inputText: String = "",
    onInputChange: (String) -> Unit = {},
    onOptimize: () -> Unit = {},
    onImagePick: () -> Unit = {},
    onFilePick: () -> Unit = {},
    onSend: () -> Unit = {},
    onStop: () -> Unit = {},
    isSending: Boolean = false,
    isOptimizing: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onOptimize, enabled = !isOptimizing, modifier = Modifier.size(32.dp)) {
            if (isOptimizing) {
                androidx.compose.material3.CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(Icons.Default.AutoAwesome, "优化输入", Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        IconButton(onClick = onFilePick, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.AttachFile, "添加文件", Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onImagePick, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Image, "添加图片", Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        VoiceInputButton(
            currentInput = inputText,
            onInputChange = onInputChange,
        )
        if (isSending) {
            IconButton(onClick = onStop, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Stop, "停止", Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.error)
            }
        } else {
            IconButton(onClick = onSend, modifier = Modifier.size(32.dp)) {
                Icon(Icons.AutoMirrored.Filled.Send, "发送", Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
