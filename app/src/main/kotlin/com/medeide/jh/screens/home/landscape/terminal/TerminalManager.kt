package com.medeide.jh.screens.home.landscape.terminal

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter

/**
 * 终端管理器 - 负责执行命令、管理进程生命周期
 */
class TerminalManager(private val context: Context?) {

    data class TerminalSession(
        val id: String,
        val name: String,
        val workingDirectory: String,
        val process: Process?,
        val writer: OutputStreamWriter?,
        val outputChannel: Channel<TerminalOutput>,
    ) {
        fun isAlive(): Boolean = process?.isAlive == true
    }

    sealed class TerminalOutput {
        data class Stdout(val text: String) : TerminalOutput()
        data class Stderr(val text: String) : TerminalOutput()
        data class Error(val message: String, val cause: Throwable? = null) : TerminalOutput()
        data class Exit(val code: Int) : TerminalOutput()
        data class Started(val pid: Int) : TerminalOutput()
    }

    private val sessions = mutableMapOf<String, TerminalSession>()
    private var sessionIdCounter = 0

    private val TERMUX_PATHS = arrayOf(
        "/data/data/com.termux/files/usr/bin/bash",
        "/data/data/com.termux/files/usr/bin/zsh",
        "/data/data/com.termux/files/usr/bin/sh"
    )

    private val TERMUX_HOME = "/data/data/com.termux/files/home"
    private val TERMUX_PATH = "/data/data/com.termux/files/usr/bin:/data/data/com.termux/files/usr/local/bin"

    private fun isTermuxInstalled(): Boolean {
        if (context == null) return false
        return try {
            context.packageManager.getPackageInfo("com.termux", 0)
            TERMUX_PATHS.any { File(it).exists() }
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun getTermuxShellPath(): String? {
        return TERMUX_PATHS.firstOrNull { File(it).exists() }
    }

    /**
     * 创建新的终端会话
     */
    suspend fun createSession(
        workingDirectory: String = System.getProperty("user.dir", "/"),
        name: String = "终端 ${++sessionIdCounter}",
    ): TerminalSession = withContext(Dispatchers.IO) {
        val id = "terminal_${System.currentTimeMillis()}"
        val outputChannel = Channel<TerminalOutput>(Channel.UNLIMITED)

        try {
            val workDir = java.io.File(workingDirectory)
            if (!workDir.exists() || !workDir.isDirectory) {
                Log.w("TerminalManager", "Working directory not valid: $workingDirectory, using /")
            }

            val shellPath = getTermuxShellPath() ?: "sh"
            val isTermux = shellPath != "sh"

            val processBuilder = ProcessBuilder(shellPath)
                .directory(if (isTermux) File(TERMUX_HOME) else (workDir.takeIf { it.exists() && it.isDirectory } ?: java.io.File("/")))
                .redirectErrorStream(true)

            if (isTermux) {
                processBuilder.environment()["HOME"] = TERMUX_HOME
                processBuilder.environment()["PATH"] = "$TERMUX_PATH:${processBuilder.environment().getOrDefault("PATH", "")}"
                processBuilder.environment()["TERM"] = "xterm"
                Log.d("TerminalManager", "Using Termux shell: $shellPath")
            }

            Log.d("TerminalManager", "Starting shell process in: ${processBuilder.directory()?.absolutePath}")
            val process = processBuilder.start()
            val writer = OutputStreamWriter(process.outputStream)

            val session = TerminalSession(
                id = id,
                name = name,
                workingDirectory = workingDirectory,
                process = process,
                writer = writer,
                outputChannel = outputChannel,
            )

            sessions[id] = session

            // 启动输出读取线程
            startReadingOutput(session)

            val pid = getProcessId(process)
            outputChannel.send(TerminalOutput.Started(pid))
            Log.d("TerminalManager", "Session created: $id, PID: $pid")

            session
        } catch (e: Exception) {
            Log.e("TerminalManager", "Failed to create session", e)
            outputChannel.trySend(TerminalOutput.Error("Failed to create terminal: ${e.message}", e))
            TerminalSession(
                id = id,
                name = name,
                workingDirectory = workingDirectory,
                process = null,
                writer = null,
                outputChannel = outputChannel,
            )
        }
    }

    /**
     * 获取会话的输出流
     */
    fun getOutputFlow(sessionId: String): Flow<TerminalOutput>? {
        return sessions[sessionId]?.outputChannel?.receiveAsFlow()
    }

    /**
     * 执行命令
     */
    suspend fun executeCommand(sessionId: String, command: String): Boolean = withContext(Dispatchers.IO) {
        val session = sessions[sessionId] ?: return@withContext false
        try {
            Log.d("TerminalManager", "Executing command in session $sessionId: $command")
            session.writer?.write("$command\n")
            session.writer?.flush()
            true
        } catch (e: Exception) {
            Log.e("TerminalManager", "Failed to execute command: $command", e)
            session.outputChannel.trySend(TerminalOutput.Error("Command failed: ${e.message}"))
            false
        }
    }

    /**
     * 关闭会话
     */
    suspend fun closeSession(sessionId: String) = withContext(Dispatchers.IO) {
        val session = sessions.remove(sessionId) ?: return@withContext
        try {
            session.writer?.close()
            session.process?.destroy()
            session.outputChannel.close()
            Log.d("TerminalManager", "Session closed: $sessionId")
        } catch (e: Exception) {
            Log.e("TerminalManager", "Failed to close session: $sessionId", e)
        }
    }

    /**
     * 获取所有活跃会话
     */
    fun getActiveSessions(): List<TerminalSession> = sessions.values.filter { it.isAlive() }

    /**
     * 启动读取进程输出
     */
    private fun startReadingOutput(session: TerminalSession) {
        Thread {
            try {
                val process = session.process ?: return@Thread
                val reader = BufferedReader(InputStreamReader(process.inputStream), 8192)
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    session.outputChannel.trySend(TerminalOutput.Stdout("$line\n"))
                }
                // 进程结束，等待退出码
                val exitCode = process.waitFor()
                session.outputChannel.trySend(TerminalOutput.Exit(exitCode))
                Log.d("TerminalManager", "Process exited with code: $exitCode")
            } catch (e: Exception) {
                Log.e("TerminalManager", "Read error", e)
                session.outputChannel.trySend(TerminalOutput.Error("Read error: ${e.message}", e))
            }
        }.apply { isDaemon = true; start() }
    }

    companion object {
        private fun getProcessId(process: Process): Int {
            return try {
                val f = java.lang.reflect.Field::class.java.getDeclaredField("impl")
                f.isAccessible = true
                val impl = f.get(process)
                val f2 = impl.javaClass.getDeclaredField("pid")
                f2.isAccessible = true
                (f2.get(impl) as Number).toInt()
            } catch (_: Exception) {
                -1
            }
        }
    }
}
