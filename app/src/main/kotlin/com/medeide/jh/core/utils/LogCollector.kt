package com.medeide.jh.core.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LogCollector {

    suspend fun collectAndShareLogs(context: Context): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val logContent = collectLogs(context)
            val logFile = createLogFile(context, logContent)
            shareLogFile(context, logFile)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun collectLogs(context: Context): String {
        val sb = StringBuilder()

        // 头部信息
        sb.append("=".repeat(60)).append("\n")
        sb.append("Android AI IDE Log Report\n")
        sb.append("Generated: ").append(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())).append("\n")
        sb.append("Device: ").append(android.os.Build.MANUFACTURER).append(" ").append(android.os.Build.MODEL).append("\n")
        sb.append("Android: ").append(android.os.Build.VERSION.RELEASE).append(" (API ").append(android.os.Build.VERSION.SDK_INT).append(")\n")
        sb.append("App Version: ").append(getAppVersion()).append("\n")
        sb.append("=".repeat(60)).append("\n\n")

        // 1. 优先从文件日志读取（release 构建 logcat 不可用）
        val fileLogs = FileLogger.readAll()
        if (fileLogs.isNotBlank()) {
            sb.append("--- File Logs ---\n")
            sb.append(fileLogs)
            sb.append("\n\n")
        } else {
            sb.append("(文件日志为空，文件目录: ").append(FileLogger.getLogDir()).append(")\n\n")
        }

        // 2. 尝试从 logcat 补充（仅在 debuggable 构建有效）
        try {
            val process = Runtime.getRuntime().exec("logcat -d -v threadtime -t 200")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val logcatSb = StringBuilder()
            reader.use { br ->
                var line: String?
                while (br.readLine().also { line = it } != null) {
                    logcatSb.append(line).append("\n")
                }
            }
            val logcatText = logcatSb.toString().trim()
            if (logcatText.isNotBlank()) {
                sb.append("--- Logcat (last 200 lines) ---\n")
                sb.append(logcatText)
                sb.append("\n")
            }
        } catch (_: Exception) {
            sb.append("(logcat 不可用，非 debuggable 构建)\n")
        }

        return sb.toString()
    }

    private fun getAppVersion(): String {
        return try {
            val ctx = com.medeide.jh.MyApplication.instance
            val pkgInfo = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
            "${pkgInfo.versionName} (${pkgInfo.longVersionCode})"
        } catch (_: Exception) {
            "Unknown"
        }
    }

    private fun createLogFile(context: Context, content: String): File {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "android_ai_ide_logs_$timestamp.log"

        val logsDir = File(context.cacheDir, "logs").apply {
            if (!exists()) mkdirs()
        }

        return File(logsDir, fileName).apply {
            writeText(content)
        }
    }

    private fun shareLogFile(context: Context, file: File) {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Android AI IDE Logs")
            putExtra(Intent.EXTRA_TEXT, "Attached are the application logs.")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val chooser = Intent.createChooser(shareIntent, "Send Logs").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        context.startActivity(chooser)
    }
}
