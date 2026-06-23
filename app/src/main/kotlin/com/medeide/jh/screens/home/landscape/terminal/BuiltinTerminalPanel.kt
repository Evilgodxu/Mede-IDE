package com.medeide.jh.screens.home.landscape.terminal

import android.content.Context
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient

private const val TAG = "BuiltinTerminalPanel"

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

            override fun logError(tag: String, message: String) {}
            override fun logWarn(tag: String, message: String) {}
            override fun logInfo(tag: String, message: String) {}
            override fun logDebug(tag: String, message: String) {}
            override fun logVerbose(tag: String, message: String) {}
            override fun logStackTraceWithMessage(tag: String, message: String, e: Exception) {}
            override fun logStackTrace(tag: String, e: Exception) {}
        }
    }

    val viewClient = remember {
        object : TerminalViewClient {
            override fun onScale(scale: Float): Float = scale.coerceIn(0.5f, 2.0f)
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
            override fun logError(tag: String, message: String) {}
            override fun logWarn(tag: String, message: String) {}
            override fun logInfo(tag: String, message: String) {}
            override fun logDebug(tag: String, message: String) {}
            override fun logVerbose(tag: String, message: String) {}
            override fun logStackTraceWithMessage(tag: String, message: String, e: Exception) {}
            override fun logStackTrace(tag: String, e: Exception) {}
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
        view.isFocusable = true
        view.isFocusableInTouchMode = true
        terminalView = view
    }

    fun showKeyboard(view: TerminalView) {
        view.requestFocus()
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(view, InputMethodManager.SHOW_FORCED)
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (isSessionActive && terminalView != null) {
            AndroidView(
                factory = {
                    terminalView!!.apply {
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { view ->
                    if (!view.hasFocus()) {
                        view.requestFocus()
                    }
                },
                onRelease = {}
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                Text("点击启动终端", color = Color.Gray)
            }
        }

        if (!isSessionActive) {
            Box(
                modifier = Modifier.fillMaxSize().clickable {
                    initializeTerminal()
                }
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize().clickable {
                    terminalView?.let { showKeyboard(it) }
                }
            )
        }
    }
}
