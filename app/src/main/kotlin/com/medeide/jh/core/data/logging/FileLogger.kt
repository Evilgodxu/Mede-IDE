package com.medeide.jh.core.data.logging

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 异步文件日志，单线程顺序写入，自动轮转。
 * 所有写操作提交到单线程执行器，不阻塞调用方。
 * 同时镜像到 logcat（debug 可见）。
 */
object FileLogger {
    private const val TAG = "FileLogger"
    private const val MAX_SIZE = 2 * 1024 * 1024L
    private const val MAX_BACKUPS = 2
    private const val LOG_NAME = "app.log"

    @Volatile private var logDir: File? = null
    @Volatile private var initialized = false

    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "FileLogger").apply { isDaemon = true }
    }

    fun init(context: Context) {
        if (initialized) return
        logDir = File(
            context.getExternalFilesDir(null) ?: context.filesDir,
            "logs"
        )
        logDir?.mkdirs()
        initialized = true
        submit { cleanup() }
    }

    /** 关闭执行器并等待已有任务完成（仅用于测试） */
    fun shutdown() {
        executor.shutdown()
        executor.awaitTermination(1, TimeUnit.SECONDS)
    }

    fun e(tag: String, msg: String, tr: Throwable? = null) {
        if (tr != null) Log.e(tag, msg, tr) else Log.e(tag, msg)
        if (initialized) submit { write('E', tag, msg, tr) }
    }

    fun w(tag: String, msg: String, tr: Throwable? = null) {
        Log.w(tag, msg, tr)
        if (initialized) submit { write('W', tag, msg, tr) }
    }

    fun i(tag: String, msg: String) {
        Log.i(tag, msg)
        if (initialized) submit { write('I', tag, msg, null) }
    }

    fun d(tag: String, msg: String) {
        Log.d(tag, msg)
        if (initialized) submit { write('D', tag, msg, null) }
    }

    fun readAll(): String {
        val dir = logDir ?: return ""
        val primary = File(dir, LOG_NAME)
        if (!primary.exists()) return ""
        return try {
            val sb = StringBuilder()
            for (i in MAX_BACKUPS downTo 1) {
                val backup = File(dir, "$LOG_NAME.$i")
                if (backup.exists()) {
                    sb.append(backup.readText()).append('\n')
                }
            }
            sb.append(primary.readText())
            sb.toString()
        } catch (e: Exception) {
            Log.e(TAG, "读取日志失败", e)
            ""
        }
    }

    fun getLogDir(): String = logDir?.absolutePath ?: ""

    private fun submit(action: () -> Unit) {
        executor.submit(action)
    }

    private fun write(level: Char, tag: String, msg: String, tr: Throwable?) {
        val dir = logDir ?: return
        val file = File(dir, LOG_NAME)
        try {
            if (file.exists() && file.length() > MAX_SIZE) rotate(file)
            val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
            val entry = buildString {
                append("[$ts] [$level/$tag] $msg")
                if (tr != null) {
                    append('\n')
                    append(Log.getStackTraceString(tr))
                }
                append('\n')
            }
            file.appendText(entry)
        } catch (e: Exception) {
            Log.e(TAG, "写入日志失败", e)
        }
    }

    private fun rotate(file: File) {
        val lastBackup = File(file.parentFile, "$LOG_NAME.$MAX_BACKUPS")
        if (lastBackup.exists()) lastBackup.delete()
        for (i in MAX_BACKUPS - 1 downTo 1) {
            val src = File(file.parentFile, "$LOG_NAME.$i")
            if (src.exists()) {
                src.renameTo(File(file.parentFile, "$LOG_NAME.${i + 1}"))
            }
        }
        file.renameTo(File(file.parentFile, "$LOG_NAME.1"))
    }

    private fun cleanup() {
        logDir?.listFiles()?.forEach { f ->
            if (f.name.startsWith(LOG_NAME) && f.length() == 0L) f.delete()
        }
    }
}
