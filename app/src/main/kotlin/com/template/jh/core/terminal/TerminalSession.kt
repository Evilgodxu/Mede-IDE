package com.template.jh.core.terminal

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.TimeUnit

/**
 * 终端会话管理器
 * 通过 ProcessBuilder 启动 shell 进程，管理 stdin/stdout 流
 * 检测 Termux bash 优先使用，否则使用系统 shell
 */
class TerminalSession {

    companion object {
        private const val TAG = "TerminalSession"
        private const val MAX_OUTPUT_LINES = 2000
        private const val TRIM_THRESHOLD = 1500

        // 优先使用 Termux bash，回退到系统 sh
        private const val TERMUX_BASH = "/data/data/com.termux/files/usr/bin/bash"
        private const val SYSTEM_SH = "/system/bin/sh"

        private val ANSI_REGEX = Regex("\\u001b\\[[0-9;]*[a-zA-Z]")
        private val ANSI_OSC = Regex("\\u001b\\].*?(\\u0007|\\u001b\\\\|\\n)")
    }

    private var process: Process? = null
    private var stdin: OutputStream? = null
    private var stdoutReader: BufferedReader? = null
    private var readJob: Job? = null

    private val _output = MutableStateFlow("")
    val output: StateFlow<String> = _output

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning

    /** Termux 是否已安装 */
    val isTermuxAvailable: Boolean
        get() = File(TERMUX_BASH).exists()

    /** 命令历史 */
    private val history = mutableListOf<String>()
    private var historyIndex = -1

    /** 启动 shell 进程 */
    fun start(scope: CoroutineScope) {
        if (_isRunning.value) return
        try {
            val shellPath = if (isTermuxAvailable) TERMUX_BASH else SYSTEM_SH
            val env = buildEnvironment(shellPath)

            val pb = ProcessBuilder(shellPath)
            pb.environment().putAll(env)
            pb.redirectErrorStream(true)

            process = pb.start()
            stdin = process!!.outputStream
            stdoutReader = process!!.inputStream.bufferedReader()
            _isRunning.value = true

            appendOutput(getBanner(shellPath))

            readJob = scope.launch(Dispatchers.IO) {
                try {
                    val buffer = CharArray(4096)
                    while (isActive && _isRunning.value) {
                        val bytesRead = stdoutReader?.read(buffer) ?: -1
                        if (bytesRead < 0) break
                        val chunk = String(buffer, 0, bytesRead)
                        appendOutput(chunk)
                    }
                } catch (e: IOException) {
                    if (_isRunning.value) Log.e(TAG, "IO error reading stdout", e)
                } finally {
                    _isRunning.value = false
                }
            }
        } catch (e: Exception) {
            appendOutput("终端启动失败: ${e.message}\r\n")
            _isRunning.value = false
        }
    }

    /** 执行命令 */
    fun executeCommand(command: String) {
        if (!_isRunning.value || command.isBlank()) return
        history.add(command)
        historyIndex = history.size
        writeToStdin(command.toByteArray(Charsets.UTF_8))
        writeToStdin("\n".toByteArray(Charsets.UTF_8))
    }

    /** 发送 Ctrl+C */
    fun sendCtrlC() {
        writeToStdin(byteArrayOf(0x03))
    }

    /** 发送 Tab */
    fun sendTab() {
        writeToStdin(byteArrayOf(0x09))
    }

    /** 发送 Escape */
    fun sendEscape() {
        writeToStdin(byteArrayOf(0x1B))
    }

    /** 获取上一条历史命令 */
    fun getHistoryPrev(): String {
        if (history.isEmpty()) return ""
        historyIndex = maxOf(0, historyIndex - 1)
        return history[historyIndex]
    }

    /** 获取下一条历史命令 */
    fun getHistoryNext(): String {
        if (history.isEmpty()) return ""
        historyIndex = minOf(history.size, historyIndex + 1)
        return if (historyIndex >= history.size) "" else history[historyIndex]
    }

    /** 清空输出 */
    fun clearOutput() {
        _output.value = ""
    }

    /** 停止终端 */
    fun stop() {
        _isRunning.value = false
        readJob?.cancel()
        readJob = null
        try { stdin?.close() } catch (_: Exception) {}
        try { stdoutReader?.close() } catch (_: Exception) {}
        try {
            process?.let { p ->
                p.destroyForcibly()
                p.waitFor(500, TimeUnit.MILLISECONDS)
            }
        } catch (_: Exception) {}
        process = null
        stdin = null
        stdoutReader = null
    }

    /** 清理 ANSI 转义序列 */
    fun stripAnsi(text: String): String {
        var result = text.replace(ANSI_OSC, "")
        result = result.replace(ANSI_REGEX, "")
        return result
    }

    private fun buildEnvironment(shellPath: String): Map<String, String> {
        val env = mutableMapOf<String, String>()
        if (shellPath == TERMUX_BASH) {
            val prefix = "/data/data/com.termux/files/usr"
            env["PREFIX"] = prefix
            env["HOME"] = "/data/data/com.termux/files/home"
            env["PATH"] = "$prefix/bin:$prefix/bin/applets:/system/bin:/system/xbin"
            env["LD_LIBRARY_PATH"] = "$prefix/lib"
            env["SHELL"] = shellPath
        } else {
            env["HOME"] = "/data/local/tmp"
            env["PATH"] = "/system/bin:/system/xbin:/vendor/bin"
            env["SHELL"] = shellPath
        }
        env["TERM"] = "xterm-256color"
        env["LANG"] = "en_US.UTF-8"
        env["PS1"] = "\\$ "
        return env
    }

    private fun getBanner(shellPath: String): String {
        val name = if (shellPath == TERMUX_BASH) "Termux" else "Android Shell"
        val shellName = File(shellPath).name
        return "\u001b[32m$name\u001b[0m \u001b[37m($shellName) \u001b[33m已启动\u001b[0m\r\n"
    }

    /** 追加输出到缓冲区，超出上限时截断头部 */
    private fun appendOutput(text: String) {
        var current = _output.value + text
        if (countLines(current) > MAX_OUTPUT_LINES) {
            val lines = current.lines()
            current = lines.drop(lines.size - TRIM_THRESHOLD).joinToString("\n")
            // 确保以换行结尾
            if (!current.endsWith("\n")) current += "\n"
        }
        _output.value = current
    }

    private fun countLines(text: String): Int {
        var count = 0
        for (c in text) { if (c == '\n') count++ }
        return count
    }

    private fun writeToStdin(data: ByteArray) {
        try {
            stdin?.let { s ->
                s.write(data)
                s.flush()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Write failed", e)
        }
    }
}
