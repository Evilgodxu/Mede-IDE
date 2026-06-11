package com.template.jh.screens.home.components.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.template.jh.core.utils.TermuxShell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 终端面板组件
 * 复用Operit项目的终端实现，支持命令执行和输出显示
 */
@Composable
fun TerminalPanel(
    initialDirectory: String = "/storage/emulated/0",
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // 终端状态
    var commandInput by remember { mutableStateOf("") }
    var isExecuting by remember { mutableStateOf(false) }
    val outputLines = remember { mutableStateListOf<TerminalOutputLine>() }
    var currentDirectory by remember { mutableStateOf(initialDirectory) }

    // 自动滚动到底部
    LaunchedEffect(outputLines.size) {
        if (outputLines.isNotEmpty()) {
            listState.animateScrollToItem(outputLines.size - 1)
        }
    }

    // 执行命令
    fun executeCommand(command: String) {
        val trimmedCommand = command.trim()
        if (trimmedCommand.isBlank()) return

        // 添加命令到输出
        outputLines.add(TerminalOutputLine.Command("$ $trimmedCommand"))
        commandInput = ""
        isExecuting = true

        scope.launch(Dispatchers.IO) {
            try {
                val result = executeShellCommand(trimmedCommand, currentDirectory)
                withContext(Dispatchers.Main) {
                    if (result.isNotEmpty()) {
                        result.lines().forEach { line ->
                            if (line.isNotBlank()) {
                                outputLines.add(TerminalOutputLine.Output(line))
                            }
                        }
                    }
                    isExecuting = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    outputLines.add(TerminalOutputLine.Error(e.message ?: "执行失败"))
                    isExecuting = false
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        // 终端标题栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Terminal,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "Android 终端",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.weight(1f))
            Text(
                currentDirectory,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(8.dp))
            // 清除按钮
            IconButton(
                onClick = { outputLines.clear() },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Clear,
                    contentDescription = "清除",
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        // 输出区域
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (outputLines.isEmpty()) {
                // 空状态提示
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "输入命令开始执行",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(outputLines) { line ->
                        TerminalOutputLineItem(line)
                    }
                }
            }
        }

        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        // 命令输入区域
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "$",
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = 8.dp)
            )

            OutlinedTextField(
                value = commandInput,
                onValueChange = { commandInput = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("输入命令...") },
                singleLine = true,
                shape = RoundedCornerShape(8.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = { executeCommand(commandInput) }
                ),
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                )
            )

            Spacer(Modifier.width(8.dp))

            IconButton(
                onClick = { executeCommand(commandInput) },
                enabled = commandInput.isNotBlank() && !isExecuting
            ) {
                if (isExecuting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "执行",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

/**
 * 终端输出行
 */
sealed class TerminalOutputLine {
    abstract val text: String
    data class Command(override val text: String) : TerminalOutputLine()
    data class Output(override val text: String) : TerminalOutputLine()
    data class Error(override val text: String) : TerminalOutputLine()
}

/**
 * 终端输出行项
 */
@Composable
private fun TerminalOutputLineItem(line: TerminalOutputLine) {
    val (text, color) = when (line) {
        is TerminalOutputLine.Command -> line.text to MaterialTheme.colorScheme.primary
        is TerminalOutputLine.Output -> line.text to MaterialTheme.colorScheme.onSurface
        is TerminalOutputLine.Error -> line.text to MaterialTheme.colorScheme.error
    }

    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        color = color,
        modifier = Modifier.fillMaxWidth()
    )
}

/**
 * 执行shell命令（优先使用 Termux，不可用时回退系统 shell）
 */
private fun executeShellCommand(command: String, workingDir: String): String {
    return if (TermuxShell.isAvailable) {
        val fullCmd = "cd \"$workingDir\" && $command"
        TermuxShell.execOrNull(fullCmd) ?: TermuxShell.exec(fullCmd).second
    } else {
        try {
            val process = ProcessBuilder("sh", "-c", command)
                .directory(java.io.File(workingDir))
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            process.waitFor()
            output
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }
}

