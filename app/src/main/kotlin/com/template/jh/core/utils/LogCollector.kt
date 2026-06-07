package com.template.jh.core.utils

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
            val logContent = collectLogs()
            val logFile = createLogFile(context, logContent)
            shareLogFile(context, logFile)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun collectLogs(): String {
        val process = Runtime.getRuntime().exec("logcat -d -v threadtime")
        val reader = BufferedReader(InputStreamReader(process.inputStream))
        val logBuilder = StringBuilder()
        
        // 添加设备信息头
        logBuilder.append("=".repeat(60)).append("\n")
        logBuilder.append("Android AI IDE Log Report\n")
        logBuilder.append("Generated: ").append(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())).append("\n")
        logBuilder.append("Device: ").append(android.os.Build.MANUFACTURER).append(" ").append(android.os.Build.MODEL).append("\n")
        logBuilder.append("Android: ").append(android.os.Build.VERSION.RELEASE).append(" (API ").append(android.os.Build.VERSION.SDK_INT).append(")\n")
        logBuilder.append("App Version: ").append(getAppVersion()).append("\n")
        logBuilder.append("=".repeat(60)).append("\n\n")
        
        reader.use { br ->
            var line: String?
            while (br.readLine().also { line = it } != null) {
                logBuilder.append(line).append("\n")
            }
        }
        
        return logBuilder.toString()
    }

    private fun getAppVersion(): String {
        return try {
            val context = com.template.jh.MyApplication.instance
            val pkgInfo = context.packageManager.getPackageInfo(context.packageName, 0)
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
