package com.template.jh.screens.home.components

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.termux.terminal.TerminalSession
import com.termux.terminalview.TerminalView
import java.io.File

@Composable
fun TerminalPanel(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val session = remember {
        val workingDir = context.filesDir.absolutePath
        val shell = findShell()
        val cwd = if (File(workingDir).isDirectory) workingDir else "/data/data/com.template.jh"
        TerminalSession(shell, null, null, cwd, 2500)
    }

    DisposableEffect(Unit) {
        onDispose {
            try { session.finishIfRunning() } catch (_: Exception) {}
        }
    }

    Column(modifier = modifier.background(MaterialTheme.colorScheme.surface)) {
        TerminalTitleBar(onClose = onClose)
        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(androidx.compose.ui.graphics.Color(0xFF1E1E1E))
        ) {
            AndroidView(
                factory = { ctx ->
                    TerminalView(ctx).apply {
                        attachSession(session)
                        setFocusable(true)
                        isFocusableInTouchMode = true
                        requestFocus()
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun TerminalTitleBar(onClose: () -> Unit) {
    val context = LocalContext.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = 12.dp, vertical = 0.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "TERMINAL",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.weight(1f))

        IconButton(
            onClick = {
                try {
                    val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                } catch (_: Exception) {}
            },
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                Icons.Default.ContentCopy, null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        IconButton(
            onClick = onClose,
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                Icons.Default.Close, null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun findShell(): String {
    val candidates = listOf("/system/bin/bash", "/system/bin/sh")
    for (s in candidates) {
        if (File(s).exists()) return s
    }
    return "/system/bin/sh"
}
