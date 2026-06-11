package com.template.jh.core.utils

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 文件日志记录器
 * 将 Log 输出同步写入私有外部目录 logs/app.log
 * 自动轮转：超过 MAX_SIZE 后重命名为 .1 并新建
 */
object FileLogger {

    private const val TAG = "FileLogger"
    private const val MAX_SIZE = 2 * 1024 * 1024L  // 2MB
    private const val MAX_BACKUPS = 2
    private const val LOG_NAME = "app.log"

    @Volatile private var logDir: File? = null
    @Volatile private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        logDir = File(context.getExternalFilesDir(null) ?: context.filesDir, "logs")
        logDir?.mkdirs()
        initialized = true
        // 清理过期的备份
        cleanup()
    }

    fun e(tag: String, msg: String, tr: Throwable? = null) {
        if (!initialized) return
        write('E', tag, msg, tr)
    }

    fun w(tag: String, msg: String, tr: Throwable? = null) {
        if (!initialized) return
        write('W', tag, msg, tr)
    }

    fun i(tag: String, msg: String) {
        if (!initialized) return
        write('I', tag, msg, null)
    }

    fun d(tag: String, msg: String) {
        if (!initialized) return
        write('D', tag, msg, null)
    }

    /** 读取当前日志文件内容（用于分享） */
    fun readAll(): String {
        val dir = logDir ?: return ""
        val primary = File(dir, LOG_NAME)
        if (!primary.exists()) return ""
        return try {
            val sb = StringBuilder()
            // 旧备份优先（按时间顺序）
            for (i in MAX_BACKUPS downTo 1) {
                val backup = File(dir, "$LOG_NAME.$i")
                if (backup.exists()) {
                    sb.append(backup.readText())
                    sb.append('\n')
                }
            }
            sb.append(primary.readText())
            sb.toString()
        } catch (e: Exception) {
            Log.e(TAG, "读取全部日志失败", e)
            ""
        }
    }

    /** 获取日志文件目录路径 */
    fun getLogDir(): String = logDir?.absolutePath ?: ""

    @Synchronized
    private fun write(level: Char, tag: String, msg: String, tr: Throwable?) {
        val dir = logDir ?: return
        val file = File(dir, LOG_NAME)
        try {
            // 轮转检测
            if (file.exists() && file.length() > MAX_SIZE) {
                rotate(file)
            }
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

    @Synchronized
    private fun rotate(file: File) {
        // 移除最旧的备份
        val lastBackup = File(file.parentFile, "$LOG_NAME.$MAX_BACKUPS")
        if (lastBackup.exists()) lastBackup.delete()
        // 依次后移
        for (i in MAX_BACKUPS - 1 downTo 1) {
            val src = File(file.parentFile, "$LOG_NAME.$i")
            if (src.exists()) {
                src.renameTo(File(file.parentFile, "$LOG_NAME.${i + 1}"))
            }
        }
        // 当前 → .1
        file.renameTo(File(file.parentFile, "$LOG_NAME.1"))
    }

    private fun cleanup() {
        val dir = logDir ?: return
        try {
            dir.listFiles()?.forEach { f ->
                if (f.name.startsWith(LOG_NAME) && f.length() == 0L) f.delete()
            }
        } catch (_: Exception) {}
    }
}
