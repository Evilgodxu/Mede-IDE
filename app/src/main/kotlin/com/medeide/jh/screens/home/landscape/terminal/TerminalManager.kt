package com.medeide.jh.screens.home.landscape.terminal

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter

/**
 * 终端管理器 - 负责执行命令、管理进程生命周期
 */
class TerminalManager {

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
            val processBuilder = ProcessBuilder("sh")
                .directory(java.io.File(workingDirectory).takeIf { it.exists() && it.isDirectory } ?: java.io.File("/"))
                .redirectErrorStream(true)

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
                val reader = BufferedReader(InputStreamReader(session.process?.inputStream))
                val buffer = CharArray(4096)
                var read: Int
                while (reader.read(buffer).also { read = it } != -1) {
                    val output = String(buffer, 0, read)
                    session.outputChannel.trySend(TerminalOutput.Stdout(output))
                }
                // 进程结束，等待退出码
                val exitCode = session.process?.waitFor() ?: -1
                session.outputChannel.trySend(TerminalOutput.Exit(exitCode))
            } catch (e: Exception) {
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
