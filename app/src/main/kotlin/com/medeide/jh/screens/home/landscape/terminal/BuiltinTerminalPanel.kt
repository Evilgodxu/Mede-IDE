package com.medeide.jh.screens.home.landscape.terminal

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient

private const val TAG = "BuiltinTerminalPanel"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuiltinTerminalPanel(
    currentPath: String,
    onNavigateToFile: (String) -> Unit,
    onClosePanel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var terminalView by remember { mutableStateOf<TerminalView?>(null) }
    var terminalSession by remember { mutableStateOf<TerminalSession?>(null) }
    var commandInput by remember { mutableStateOf("") }
    var isSessionActive by remember { mutableStateOf(false) }

    val sessionClient = remember {
        object : TerminalSessionClient {
            override fun onTextChanged(changedSession: TerminalSession) {
                terminalView?.onScreenUpdated()
            }

            override fun onTitleChanged(changedSession: TerminalSession) {}

            override fun onSessionFinished(finishedSession: TerminalSession) {
                isSessionActive = false
            }

            override fun onCopyTextToClipboard(session: TerminalSession, text: String) {}

            override fun onPasteTextFromClipboard(session: TerminalSession?) {}

            override fun onBell(session: TerminalSession) {}

            override fun onColorsChanged(session: TerminalSession) {}

            override fun onTerminalCursorStateChange(state: Boolean) {}

            override fun setTerminalShellPid(session: TerminalSession, pid: Int) {}

            override fun getTerminalCursorStyle(): Int = 0

            override fun logError(tag: String, message: String) {
                Log.e(tag, message)
            }

            override fun logWarn(tag: String, message: String) {
                Log.w(tag, message)
            }

            override fun logInfo(tag: String, message: String) {
                Log.i(tag, message)
            }

            override fun logDebug(tag: String, message: String) {
                Log.d(tag, message)
            }

            override fun logVerbose(tag: String, message: String) {
                Log.v(tag, message)
            }

            override fun logStackTraceWithMessage(tag: String, message: String, e: Exception) {
                Log.e(tag, message, e)
            }

            override fun logStackTrace(tag: String, e: Exception) {
                Log.e(tag, "", e)
            }
        }
    }

    val viewClient = remember {
        object : TerminalViewClient {
            override fun onScale(scale: Float): Float {
                return scale.coerceIn(0.5f, 2.0f)
            }

            override fun onSingleTapUp(e: android.view.MotionEvent) {}

            override fun shouldBackButtonBeMappedToEscape(): Boolean = false

            override fun shouldEnforceCharBasedInput(): Boolean = false

            override fun shouldUseCtrlSpaceWorkaround(): Boolean = false

            override fun isTerminalViewSelected(): Boolean = true

            override fun copyModeChanged(copyMode: Boolean) {}

            override fun onKeyDown(keyCode: Int, e: android.view.KeyEvent, session: TerminalSession): Boolean = false

            override fun onKeyUp(keyCode: Int, e: android.view.KeyEvent): Boolean = false

            override fun onLongPress(event: android.view.MotionEvent): Boolean = false

            override fun readControlKey(): Boolean = false

            override fun readAltKey(): Boolean = false

            override fun readShiftKey(): Boolean = false

            override fun readFnKey(): Boolean = false

            override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession): Boolean = false

            override fun onEmulatorSet() {}

            override fun logError(tag: String, message: String) {
                Log.e(tag, message)
            }

            override fun logWarn(tag: String, message: String) {
                Log.w(tag, message)
            }

            override fun logInfo(tag: String, message: String) {
                Log.i(tag, message)
            }

            override fun logDebug(tag: String, message: String) {
                Log.d(tag, message)
            }

            override fun logVerbose(tag: String, message: String) {
                Log.v(tag, message)
            }

            override fun logStackTraceWithMessage(tag: String, message: String, e: Exception) {
                Log.e(tag, message, e)
            }

            override fun logStackTrace(tag: String, e: Exception) {
                Log.e(tag, "", e)
            }
        }
    }

    fun initializeTerminal() {
        if (terminalView != null) return

        val shellPath = "/system/bin/sh"
        val cwd = currentPath.ifEmpty { "/storage/emulated/0" }
        val args = arrayOf(shellPath)
        val env = arrayOf(
            "HOME=/storage/emulated/0",
            "PATH=/system/bin:/system/xbin:/storage/emulated/0/bin",
            "TERM=xterm-256color",
            "LANG=zh_CN.UTF-8"
        )

        val session = TerminalSession(shellPath, cwd, args, env, 5000, sessionClient)
        terminalSession = session
        isSessionActive = true

        val view = TerminalView(context, null)
        view.setTerminalViewClient(viewClient)
        view.setTextSize(14)
        view.attachSession(session)
        terminalView = view
    }

    fun sendCommand(command: String) {
        terminalSession?.let { session ->
            session.write(command + "\n")
        }
    }

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        TopAppBar(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("内置终端")
                }
            },
            navigationIcon = {
                IconButton(onClick = onClosePanel) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "关闭")
                }
            },
            actions = {
                IconButton(onClick = { initializeTerminal() }) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "启动终端")
                }
            }
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color.Black)
        ) {
            if (terminalView != null) {
                AndroidView(
                    factory = { terminalView!! },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("点击右上角按钮启动终端", color = Color.Gray)
                        Text("或使用下方输入框直接执行命令", color = Color.Gray, fontSize = 12.sp)
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = commandInput,
                onValueChange = { commandInput = it },
                modifier = Modifier
                    .weight(1f)
                    .background(Color.Black, androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                    .padding(8.dp),
                textStyle = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    color = Color.White
                ),
                singleLine = true,
                placeholder = {
                    Text(
                        "输入命令...",
                        color = Color.Gray,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp
                    )
                }
            )

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = {
                    if (commandInput.isNotBlank()) {
                        if (!isSessionActive) {
                            initializeTerminal()
                        }
                        sendCommand(commandInput)
                        commandInput = ""
                    }
                },
                enabled = commandInput.isNotBlank()
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "发送")
            }
        }
    }
}