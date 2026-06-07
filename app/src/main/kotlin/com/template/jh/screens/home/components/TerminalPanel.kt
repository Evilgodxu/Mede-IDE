package com.template.jh.screens.home.components

import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.method.ScrollingMovementMethod
import android.widget.EditText
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import java.io.InputStream
import java.io.OutputStream

@Composable
fun TerminalPanel(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var outputText by remember { mutableStateOf("") }
    var inputText by remember { mutableStateOf("") }
    var processJob by remember { mutableStateOf<Job?>(null) }
    var process by remember { mutableStateOf<Process?>(null) }
    var stdin by remember { mutableStateOf<OutputStream?>(null) }
    var stdout by remember { mutableStateOf<InputStream?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            processJob?.cancel()
            try { process?.destroy() } catch (_: Exception) {}
        }
    }

    Column(modifier = modifier.background(MaterialTheme.colorScheme.surface)) {
        TerminalTitleBar(onClose = onClose)
        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        // 终端输出显示
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(androidx.compose.ui.graphics.Color(0xFF1E1E1E))
        ) {
            AndroidView(
                factory = { ctx ->
                    TextView(ctx).apply {
                        setTextColor(android.graphics.Color.parseColor("#D4D4D4"))
                        setBackgroundColor(android.graphics.Color.parseColor("#1E1E1E"))
                        textSize = 12f
                        typeface = Typeface.MONOSPACE
                        movementMethod = ScrollingMovementMethod()
                        setPadding(8, 4, 8, 4)
                        setLineSpacing(0f, 1.1f)
                    }
                },
                update = { tv ->
                    if (outputText != tv.text.toString()) {
                        tv.text = outputText
                        // 自动滚到底
                        tv.post { tv.scrollTo(0, tv.layout.height - tv.height) }
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
