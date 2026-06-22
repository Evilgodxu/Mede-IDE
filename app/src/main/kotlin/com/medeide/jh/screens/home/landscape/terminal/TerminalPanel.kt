package com.medeide.jh.screens.home.landscape.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalPanel(
    terminalManager: TerminalManager,
    initialWorkingDirectory: String,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var currentSession by remember { mutableStateOf<TerminalManager.TerminalSession?>(null) }
    var outputText by remember { mutableStateOf("") }
    var commandInput by remember { mutableStateOf("") }
    var sessions by remember { mutableStateOf(listOf<TerminalManager.TerminalSession>()) }
    var showSessions by remember { mutableStateOf(false) }

    // 检测 py 脚本是否存在
    val toolkitScriptPath = remember(initialWorkingDirectory) {
        val possiblePaths = listOf(
            File(initialWorkingDirectory, "android_dev_toolkit.py"),
            File(initialWorkingDirectory, "android_dev_toolkit..py"),
            File("/storage/emulated/0/Termux/Android/android_dev_toolkit.py"),
        )
        possiblePaths.find { it.exists() }?.absolutePath
    }

    // 初始化第一个终端会话
    LaunchedEffect(Unit) {
        val session = terminalManager.createSession(initialWorkingDirectory)
        currentSession = session
        sessions = terminalManager.getActiveSessions()

        // 显示欢迎信息
        outputText += "欢迎使用 Mede IDE 终端！\n"
        if (toolkitScriptPath != null) {
            outputText += "检测到开发工具脚本：$toolkitScriptPath\n"
            outputText += "运行命令：python3 android_dev_toolkit.py\n"
            outputText += "或点击工具栏的 🛠️ 按钮快速启动\n"
            outputText += "（注：此脚本需要 Python 环境，如未安装请先运行：pkg install python）\n"
        }
        outputText += "\n"

        // 监听输出
        terminalManager.getOutputFlow(session.id)?.collect { output ->
            when (output) {
                is TerminalManager.TerminalOutput.Stdout -> {
                    outputText += output.text
                }
                is TerminalManager.TerminalOutput.Stderr -> {
                    outputText += output.text
                }
                is TerminalManager.TerminalOutput.Error -> {
                    outputText += "\n[错误] ${output.message}\n"
                }
                is TerminalManager.TerminalOutput.Started -> {
                    outputText += "终端已启动 (PID: ${output.pid})\n"
                }
                is TerminalManager.TerminalOutput.Exit -> {
                    outputText += "\n进程退出，代码: ${output.code}\n"
                }
            }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // 工具栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {
                    scope.launch {
                        val session = terminalManager.createSession(initialWorkingDirectory)
                        currentSession = session
                        sessions = terminalManager.getActiveSessions()
                    }
                }) {
                    Icon(Icons.Default.Add, contentDescription = "新建终端")
                }
                IconButton(onClick = { showSessions = true }) {
                    Icon(Icons.Default.List, contentDescription = "会话列表")
                }
                IconButton(onClick = {
                    currentSession?.let { session ->
                        scope.launch {
                            terminalManager.closeSession(session.id)
                            sessions = terminalManager.getActiveSessions()
                            currentSession = sessions.firstOrNull()
                        }
                    }
                }) {
                    Icon(Icons.Default.Close, contentDescription = "关闭终端")
                }
                // 快速启动开发工具脚本
                if (toolkitScriptPath != null) {
                    IconButton(onClick = {
                        currentSession?.let { session ->
                            scope.launch {
                                // 先检查 Python 是否安装
                                terminalManager.executeCommand(session.id, "which python3 || which python")
                            }
                        }
                    }) {
                        Icon(Icons.Default.Build, contentDescription = "运行开发工具")
                    }
                }
            }
            Text(
                text = currentSession?.name ?: "无终端",
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        // 输出区域
        SelectionContainer {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color(0xFF1E1E1E))
                    .horizontalScroll(rememberScrollState())
                    .padding(8.dp),
            ) {
                Text(
                    text = outputText,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    color = Color(0xFFD4D4D4),
                    lineHeight = 20.sp,
                )
            }
        }

        // 命令输入
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "$ ",
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                color = Color(0xFF4EC9B0),
            )
            OutlinedTextField(
                value = commandInput,
                onValueChange = { commandInput = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                ),
                placeholder = {
                    Text(
                        "输入命令...",
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                },
            )
            IconButton(
                onClick = {
                    if (commandInput.isNotBlank() && currentSession != null) {
                        scope.launch {
                            terminalManager.executeCommand(currentSession!!.id, commandInput)
                        }
                        commandInput = ""
                    }
                },
            ) {
                Icon(Icons.Default.Send, contentDescription = "执行命令")
            }
        }
    }

    // 会话列表对话框
    if (showSessions) {
        AlertDialog(
            onDismissRequest = { showSessions = false },
            title = { Text("终端会话") },
            text = {
                Column {
                    sessions.forEach { session ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text(session.name, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    session.workingDirectory,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Row {
                                TextButton(
                                    onClick = {
                                        currentSession = session
                                        showSessions = false
                                    },
                                ) {
                                    Text("切换")
                                }
                                IconButton(
                                    onClick = {
                                        scope.launch {
                                            terminalManager.closeSession(session.id)
                                            sessions = terminalManager.getActiveSessions()
                                            if (currentSession?.id == session.id) {
                                                currentSession = sessions.firstOrNull()
                                            }
                                        }
                                    },
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "关闭")
                                }
                            }
                        }
                    }
                    if (sessions.isEmpty()) {
                        Text("没有活跃的终端会话")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSessions = false }) {
                    Text("关闭")
                }
            },
        )
    }
}
