package com.medeide.jh.screens.home.landscape.workspace.terminal

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

private val TerminalBg = Color(0xFF1E1E1E)
private val TerminalHeaderBg = Color(0xFF2D2D2D)
private val TerminalFg = Color(0xFFCCCCCC)
private val TerminalGreen = Color(0xFF4EC9B0)
private val TerminalDim = Color(0xFF6A6A6A)
private val TerminalPrompt = Color(0xFF89D185)

private data class TerminalItem(val type: String, val text: String)

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TerminalPage(modifier: Modifier = Modifier) {
    val scrollState = rememberScrollState()
    val hScrollState = rememberScrollState()
    val keyboardController = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    
    var terminalCommand by remember { mutableStateOf("") }
    val commandHistory = remember { mutableStateListOf<TerminalItem>() }
    
    val blinkTransition = rememberInfiniteTransition(label = "cursor")
    val cursorAlpha by blinkTransition.animateFloat(
        initialValue = 1f, targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 530),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "cursorAlpha",
    )
    
    val focusRequester = remember { FocusRequester() }
    
    // 真实终端进程
    val process = remember {
        try {
            val proc = Runtime.getRuntime().exec("/system/bin/sh")
            // 启动一个线程持续读取输出
            Thread {
                try {
                    val reader = BufferedReader(InputStreamReader(proc.inputStream))
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        // 输出被丢弃，因为我们用单独的命令执行
                    }
                } catch (_: Exception) {}
            }.apply { isDaemon = true; start() }
            proc
        } catch (e: Exception) {
            null
        }
    }
    
    // 启动时输出欢迎信息
    LaunchedEffect(Unit) {
        commandHistory.add(TerminalItem("output", "Mede Terminal v1.0"))
        commandHistory.add(TerminalItem("output", "Type 'help' for available commands"))
        commandHistory.add(TerminalItem("output", ""))
    }

    // 自动聚焦输入框
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }
    
    // 清理资源
    DisposableEffect(Unit) {
        onDispose {
            process?.destroy()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(TerminalBg),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 终端顶栏
            TerminalHeaderBar()

            // 终端输出区
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .horizontalScroll(hScrollState)
                    .padding(12.dp),
            ) {
                Column {
                    // 显示命令历史
                    commandHistory.forEach { item ->
                        Text(
                            text = buildTerminalOutput(item),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                lineHeight = 20.sp,
                            ),
                        )
                    }
                    
                    Spacer(Modifier.height(4.dp))
                    
                    // 当前输入行
                    TerminalInputBar(
                        command = terminalCommand,
                        cursorAlpha = cursorAlpha,
                        focusRequester = focusRequester,
                        onCommandChanged = { terminalCommand = it },
                        onSendCommand = { cmd ->
                            if (cmd.isNotEmpty()) {
                                commandHistory.add(TerminalItem("input", cmd))
                                terminalCommand = ""
                                
                                // 执行真实命令
                                scope.launch {
                                    val result = executeCommand(process, cmd)
                                    result.forEach { line ->
                                        commandHistory.add(TerminalItem("output", line))
                                    }
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

private suspend fun executeCommand(process: Process?, command: String): List<String> = withContext(Dispatchers.IO) {
    val result = mutableListOf<String>()
    
    if (process == null) {
        result.add("Error: Could not start shell process")
        return@withContext result
    }
    
    try {
        // 使用单独进程执行命令
        val proc = Runtime.getRuntime().exec(arrayOf("/system/bin/sh", "-c", command))
        
        // 读取标准输出
        proc.inputStream.bufferedReader().useLines { lines ->
            lines.forEach { line ->
                if (line.isNotBlank()) {
                    result.add(line)
                }
            }
        }
        
        // 读取错误输出
        proc.errorStream.bufferedReader().useLines { lines ->
            lines.forEach { line ->
                if (line.isNotBlank()) {
                    result.add("Error: $line")
                }
            }
        }
        
        proc.waitFor()
        
        // 如果没有输出，添加一个空行
        if (result.isEmpty()) {
            result.add("")
        }
    } catch (e: Exception) {
        result.add("Error: ${e.message}")
    }
    
    result
}

@Composable
private fun TerminalHeaderBar() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(TerminalHeaderBg)
            .padding(horizontal = 8.dp)
            .height(32.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                imageVector = Icons.Default.Terminal,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = TerminalFg,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "终端",
                style = MaterialTheme.typography.labelSmall,
                color = TerminalFg,
            )
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun TerminalInputBar(
    command: String,
    cursorAlpha: Float,
    focusRequester: FocusRequester,
    onCommandChanged: (String) -> Unit,
    onSendCommand: (String) -> Unit,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clickable {
                focusRequester.requestFocus()
                keyboardController?.show()
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$ ",
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
            ),
            color = TerminalPrompt,
        )
        // 使用 TextField 确保输入可见
        androidx.compose.material3.TextField(
            value = command,
            onValueChange = { onCommandChanged(it) },
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester),
            textStyle = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                color = TerminalFg,
            ),
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                onDone = { onSendCommand(command) },
            ),
            colors = androidx.compose.material3.TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                cursorColor = TerminalFg,
                focusedTextColor = TerminalFg,
                unfocusedTextColor = TerminalFg,
            ),
        )
    }
}

private fun buildTerminalOutput(item: TerminalItem) = buildAnnotatedString {
    when (item.type) {
        "input" -> {
            withStyle(SpanStyle(color = TerminalGreen)) {
                append(" ~ ")
            }
            withStyle(SpanStyle(color = TerminalPrompt)) {
                append("$ ")
            }
            withStyle(SpanStyle(color = TerminalFg)) {
                append("${item.text}\n")
            }
        }
        "output" -> {
            withStyle(SpanStyle(color = TerminalDim)) {
                append("${item.text}\n")
            }
        }
    }
}
