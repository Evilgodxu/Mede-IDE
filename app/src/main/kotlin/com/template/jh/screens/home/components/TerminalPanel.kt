package com.template.jh.screens.home.components

import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.text.method.ScrollingMovementMethod
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Dangerous
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.template.jh.core.terminal.TerminalSession
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val BG_COLOR = androidx.compose.ui.graphics.Color(0xFF1E1E1E)
private val TEXT_COLOR = androidx.compose.ui.graphics.Color(0xFFD4D4D4)
private val PROMPT_COLOR = androidx.compose.ui.graphics.Color(0xFF4EC9B0)
private val INPUT_TEXT_COLOR = androidx.compose.ui.graphics.Color(0xFFDCDCAA)

private val ansiRegex = Regex("\u001b\\[[0-9;]*[a-zA-Z]")
private val ansiOscRegex = Regex("\u001b\\].*?(\u0007|\u001b\\\\|\n)")

@Composable
fun TerminalPanel(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val session = remember { TerminalSession() }
    val output by session.output.collectAsState()
    val isRunning by session.isRunning.collectAsState()

    var inputText by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val textViewRef = remember { mutableStateOf<TextView?>(null) }

    // 启动终端
    LaunchedEffect(Unit) {
        session.start(scope)
    }

    // 自动滚动到最新输出
    LaunchedEffect(output) {
        textViewRef.value?.let { tv ->
            delay(50)
            tv.post { tv.scrollTo(0, tv.layout.height - tv.height + tv.paddingBottom) }
        }
    }

    // 自动获取焦点
    LaunchedEffect(isRunning) {
        if (isRunning) {
            delay(100)
            focusRequester.requestFocus()
        }
    }

    // 清理
    DisposableEffect(Unit) {
        onDispose { session.stop() }
    }

    Column(modifier = modifier.background(MaterialTheme.colorScheme.surface)) {
        TerminalTitleBar(
            onClose = onClose,
            onClear = { session.clearOutput() },
            onCtrlC = { session.sendCtrlC() },
            onCopy = {
                try {
                    val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cb.setPrimaryClip(android.content.ClipData.newPlainText("terminal", stripAnsi(output)))
                } catch (_: Exception) {}
            },
            isRunning = isRunning,
        )
        HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )

        // 终端输出显示
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(BG_COLOR)
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
                        isVerticalScrollBarEnabled = true
                        textViewRef.value = this
                    }
                },
                update = { tv ->
                    val clean = stripAnsi(output)
                    if (clean != tv.text.toString()) {
                        tv.text = clean
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // 输入栏
        TerminalInputBar(
            inputText = inputText,
            onInputChange = { inputText = it },
            onExecute = {
                val cmd = inputText.trimEnd()
                session.executeCommand(cmd)
                inputText = ""
                focusRequester.requestFocus()
            },
            onHistoryUp = {
                inputText = session.getHistoryPrev()
            },
            onHistoryDown = {
                inputText = session.getHistoryNext()
            },
            onCtrlC = { session.sendCtrlC() },
            focusRequester = focusRequester,
            isRunning = isRunning,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun TerminalInputBar(
    inputText: String,
    onInputChange: (String) -> Unit,
    onExecute: () -> Unit,
    onHistoryUp: () -> Unit,
    onHistoryDown: () -> Unit,
    onCtrlC: () -> Unit,
    focusRequester: FocusRequester,
    isRunning: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .background(androidx.compose.ui.graphics.Color(0xFF252526))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 提示符
        Text(
            text = "$ ",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                color = PROMPT_COLOR,
                fontWeight = FontWeight.Bold,
            )
        )

        // 输入框
        BasicTextField(
            value = inputText,
            onValueChange = { onInputChange(it) },
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester)
                .onKeyEvent { event ->
                    when (event.key) {
                        Key.Enter -> { onExecute(); true }
                        Key.DirectionUp -> { onHistoryUp(); true }
                        Key.DirectionDown -> { onHistoryDown(); true }
                        else -> false
                    }
                },
            textStyle = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                color = INPUT_TEXT_COLOR,
            ),
            cursorBrush = SolidColor(androidx.compose.ui.graphics.Color(0xFFD4D4D4)),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrect = false,
                keyboardType = KeyboardType.Ascii,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { onExecute() }),
            singleLine = true,
            decorationBox = { innerTextField ->
                Box {
                    if (inputText.isEmpty() && isRunning) {
                        Text(
                            text = "输入命令...",
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                color = androidx.compose.ui.graphics.Color(0xFF6A6A6A),
                            )
                        )
                    }
                    innerTextField()
                }
            }
        )

        // Ctrl+C 按钮
        IconButton(
            onClick = onCtrlC,
            enabled = isRunning,
            modifier = Modifier.size(24.dp)
        ) {
            Icon(
                Icons.Default.Dangerous, "中断 (Ctrl+C)",
                modifier = Modifier.size(14.dp),
                tint = if (isRunning) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
            )
        }
    }
}

@Composable
private fun TerminalTitleBar(
    onClose: () -> Unit,
    onClear: () -> Unit,
    onCtrlC: () -> Unit,
    onCopy: () -> Unit,
    isRunning: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = 12.dp, vertical = 0.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 状态指示器
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(
                    if (isRunning) androidx.compose.ui.graphics.Color(0xFF4CAF50)
                    else MaterialTheme.colorScheme.error,
                    shape = androidx.compose.foundation.shape.CircleShape
                )
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "TERMINAL",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = if (isRunning) "(运行中)" else "(已停止)",
            style = MaterialTheme.typography.labelSmall,
            color = if (isRunning) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.error,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.weight(1f))

        // 复制
        IconButton(
            onClick = onCopy,
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                Icons.Default.ContentCopy, null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // 清除
        IconButton(
            onClick = onClear,
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                Icons.Default.DeleteSweep, null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // 关闭
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

/** 清理 ANSI 转义序列 */
private fun stripAnsi(text: String): String {
    var result = text.replace(ansiOscRegex, "")
    result = result.replace(ansiRegex, "")
    result = result.replace("\r\n", "\n")
    result = result.replace("\r", "")
    return result
}
