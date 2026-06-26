package com.medeide.jh.core.data.source.local

import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import com.medeide.jh.core.data.logging.FileLogger
import com.medeide.jh.model.chat.DownloadStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** 单个模型的下载状态 */
data class ModelDownloadState(
    val status: DownloadStatus = DownloadStatus.Idle,
    val progress: Float = 0f,
    val errorMessage: String = "",
)

/**
 * 管理本地 .litertlm 模型文件的存储、发现和下载。
 */
class LiteRTModelRepository(private val context: Context) {

    private val modelsDir: File
        get() = File(context.filesDir, "litertlm_models").also { it.mkdirs() }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .writeTimeout(300, TimeUnit.SECONDS)
        .build()

    private val _downloadStates = MutableStateFlow(mapOf<String, ModelDownloadState>())
    val downloadStates: StateFlow<Map<String, ModelDownloadState>> = _downloadStates.asStateFlow()

    private val cancelFlags = mutableMapOf<String, AtomicBoolean>()

    /** 扫描本地已下载的模型文件（多位置扫描） */
    fun scanDownloadedModels(): List<File> {
        val seen = mutableSetOf<String>()
        val results = mutableListOf<File>()

        fun addIfLitertlm(file: File) {
            if (file.extension != "litertlm") return
            if (seen.add(file.absolutePath)) {
                results.add(file)
            }
        }

        fun scanDir(dir: File, depth: Int = 0) {
            if (depth > 3 || !dir.isDirectory) return
            try {
                dir.listFiles()?.forEach { f ->
                    if (f.isFile) addIfLitertlm(f)
                    else if (f.isDirectory && depth < 3) scanDir(f, depth + 1)
                }
            } catch (_: Exception) { }
        }

        // 1. 应用私有模型目录
        scanDir(modelsDir)

        // 2. 应用外部存储
        context.getExternalFilesDir(null)?.let { scanDir(it) }
        context.getExternalFilesDir(null)?.let { File(it, "models").takeIf(File::exists)?.let(::scanDir) }

        // 3. 公共 Downloads 目录
        try { scanDir(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)) } catch (_: Exception) {}

        // 4. 外部存储根目录
        try { scanDir(Environment.getExternalStorageDirectory()) } catch (_: Exception) {}

        // 5. 常见路径
        listOf("/storage/emulated/0/Download", "/storage/emulated/0/Models", "/storage/emulated/0/litertlm").forEach {
            scanDir(File(it))
        }

        // 6. MediaStore 补充查询（Android 10+）
        try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            context.contentResolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Downloads.DISPLAY_NAME, MediaStore.Downloads.RELATIVE_PATH),
                "${MediaStore.Downloads.DISPLAY_NAME} LIKE ?",
                arrayOf("%.litertlm"),
                null
            )?.use { cursor ->
                val nameIdx = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME)
                val pathIdx = cursor.getColumnIndexOrThrow(MediaStore.Downloads.RELATIVE_PATH)
                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameIdx) ?: continue
                    val rel = cursor.getString(pathIdx) ?: ""
                    val fullPath = File(downloadsDir, "$rel$name").absolutePath
                    if (seen.add(fullPath)) {
                        results.add(File(fullPath))
                    }
                }
            }
        } catch (_: Exception) {}

        return results.sortedByDescending { it.lastModified() }
    }

    /** 检查指定模型是否已下载 */
    fun isModelDownloaded(fileName: String): Boolean {
        return File(modelsDir, fileName).exists()
    }

    /** 获取模型文件的绝对路径 */
    fun getModelPath(fileName: String): String {
        return File(modelsDir, fileName).absolutePath
    }

    /** 下载模型，支持进度回调 */
    suspend fun downloadModel(
        url: String,
        fileName: String,
    ): Result<String> = withContext(Dispatchers.IO) {
        if (isModelDownloaded(fileName)) {
            _downloadStates.update(fileName, DownloadStatus.Completed, 1f)
            return@withContext Result.success(getModelPath(fileName))
        }

        val cancelFlag = AtomicBoolean(false)
        cancelFlags[fileName] = cancelFlag

        _downloadStates.update(fileName, DownloadStatus.Downloading, 0f)

        try {
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                _downloadStates.update(fileName, DownloadStatus.Error, 0f,
                    "HTTP ${response.code}")
                return@withContext Result.failure(Exception("下载失败: HTTP ${response.code}"))
            }

            val body = response.body ?: run {
                _downloadStates.update(fileName, DownloadStatus.Error, 0f, "响应体为空")
                return@withContext Result.failure(Exception("响应体为空"))
            }

            val contentLength = body.contentLength()
            val targetFile = File(modelsDir, fileName)
            val tempFile = File(modelsDir, "${fileName}.tmp")

            FileOutputStream(tempFile).use { output ->
                val buffer = ByteArray(32 * 1024)
                var bytesRead: Long = 0
                val input = body.byteStream()

                while (true) {
                    if (cancelFlag.get()) {
                        input.close()
                        tempFile.delete()
                        _downloadStates.update(fileName, DownloadStatus.Idle, 0f)
                        return@withContext Result.failure(Exception("下载已取消"))
                    }

                    val read = input.read(buffer)
                    if (read == -1) break
                    output.write(buffer, 0, read)
                    bytesRead += read

                    if (contentLength > 0) {
                        val progress = (bytesRead.toFloat() / contentLength).coerceAtMost(1f)
                        _downloadStates.update(fileName, DownloadStatus.Downloading, progress)
                    }
                }
            }

            // 下载完成后重命名
            if (tempFile.exists()) {
                targetFile.delete()
                tempFile.renameTo(targetFile)
            }

            _downloadStates.update(fileName, DownloadStatus.Completed, 1f)
            cancelFlags.remove(fileName)

            FileLogger.i("LiteRTRepo", "模型下载完成: $fileName (${targetFile.length() / 1024 / 1024} MB)")
            Result.success(targetFile.absolutePath)
        } catch (e: Exception) {
            if (cancelFlag.get()) {
                _downloadStates.update(fileName, DownloadStatus.Idle, 0f)
            } else {
                _downloadStates.update(fileName, DownloadStatus.Error, 0f, e.message ?: "未知错误")
                FileLogger.e("LiteRTRepo", "模型下载失败: $fileName", e)
            }
            Result.failure(e)
        }
    }

    /** 取消下载 */
    fun cancelDownload(fileName: String) {
        cancelFlags[fileName]?.set(true)
    }

    /** 暂停/继续（通过取消实现暂停，重新下载续传） */
    fun pauseDownload(fileName: String) {
        cancelFlags[fileName]?.set(true)
        _downloadStates.update(fileName, DownloadStatus.Paused,
            _downloadStates.value[fileName]?.progress ?: 0f)
    }

    /** 删除模型文件 */
    suspend fun deleteModel(fileName: String) = withContext(Dispatchers.IO) {
        File(modelsDir, fileName).delete()
        File(modelsDir, "${fileName}.tmp").delete()
        _downloadStates.update(fileName, DownloadStatus.Idle, 0f)
    }

    /** 获取模型文件大小 */
    fun getModelSize(fileName: String): Long {
        return File(modelsDir, fileName).length()
    }

    private fun MutableStateFlow<Map<String, ModelDownloadState>>.update(
        fileName: String,
        status: DownloadStatus,
        progress: Float,
        errorMessage: String = "",
    ) {
        this.value = this.value + (fileName to ModelDownloadState(status, progress, errorMessage))
    }
}
