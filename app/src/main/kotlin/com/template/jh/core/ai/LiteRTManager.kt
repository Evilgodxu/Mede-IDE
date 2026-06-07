package com.template.jh.core.ai

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ExperimentalFlags
import com.google.ai.edge.litertlm.LogSeverity
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

// 引擎状态
enum class EngineStatus {
    Idle, Loading, Ready, Error
}

data class EngineState(
    val status: EngineStatus = EngineStatus.Idle,
    val modelPath: String = "",
    val modelName: String = "",
    val errorMessage: String = "",
    val progress: Float = 0f,
)

// 下载状态
enum class DownloadStatus {
    Idle, Downloading, Completed, Error
}

data class DownloadState(
    val status: DownloadStatus = DownloadStatus.Idle,
    val fileName: String = "",
    val progress: Float = 0f,
    val errorMessage: String = "",
)

// 模型推理参数
data class ModelParams(
    val topK: Int = 40,
    val topP: Double = 0.95,
    val temperature: Double = 0.8,
    val seed: Int = 0,
) {
    init {
        require(topK > 0) { "topK must be positive, got $topK" }
        require(topP in 0.0..1.0) { "topP must be 0~1, got $topP" }
        require(temperature >= 0) { "temperature must be >= 0, got $temperature" }
    }
    fun toSamplerConfig() = SamplerConfig(topK = topK, topP = topP, temperature = temperature, seed = seed)
}

// LiteRT-LM 引擎管理器
@OptIn(com.google.ai.edge.litertlm.ExperimentalApi::class)
class LiteRTManager(private val context: Context) : AutoCloseable {

    private val _state = MutableStateFlow(EngineState())
    val state: StateFlow<EngineState> = _state

    private val _downloadState = MutableStateFlow(DownloadState())
    val downloadState: StateFlow<DownloadState> = _downloadState

    @Volatile private var engine: Engine? = null

    @Volatile var isInitialized: Boolean = false
        private set

    @Volatile var modelParams: ModelParams = ModelParams()

    init {
        Engine.setNativeMinLogSeverity(LogSeverity.ERROR)
        // 禁用工具名的 snake_case 转换，保持原始函数名
        ExperimentalFlags.convertCamelToSnakeCaseInToolDescription = false
    }

    // 直接从 URL 下载模型到系统下载目录
    suspend fun downloadModel(url: String, fileName: String) {
        _downloadState.value = DownloadState(status = DownloadStatus.Downloading, fileName = fileName, progress = 0f)
        try {
            withContext(Dispatchers.IO) {
                val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!downloadDir.exists()) downloadDir.mkdirs()
                val destFile = File(downloadDir, fileName)

                val connection = URL(url).openConnection() as HttpURLConnection
                connection.apply {
                    connectTimeout = 15000
                    readTimeout = 30000
                    instanceFollowRedirects = true
                }
                val totalBytes = connection.contentLengthLong
                connection.inputStream.use { input ->
                    FileOutputStream(destFile).use { output ->
                        val buf = ByteArray(8192)
                        var read: Int
                        var copied = 0L
                        while (input.read(buf).also { read = it } != -1) {
                            output.write(buf, 0, read)
                            copied += read
                            if (totalBytes > 0) {
                                _downloadState.value = DownloadState(
                                    status = DownloadStatus.Downloading,
                                    fileName = fileName,
                                    progress = copied.toFloat() / totalBytes,
                                )
                            }
                        }
                    }
                }
                connection.disconnect()
            }
            _downloadState.value = DownloadState(status = DownloadStatus.Completed, fileName = fileName, progress = 1f)
        } catch (e: Exception) {
            copyCrashToClipboard("downloadModel", e)
            _downloadState.value = DownloadState(status = DownloadStatus.Error, fileName = fileName,
                progress = 0f, errorMessage = e.message ?: "下载失败")
        }
    }

    fun resetDownloadState() {
        _downloadState.value = DownloadState()
    }

    // 从 SAF URI 加载模型
    suspend fun loadModelFromUri(uri: Uri) {
        val modelDir = File(context.filesDir, "models")
        if (!modelDir.exists()) modelDir.mkdirs()
        val fileName = resolveUriFileName(uri) ?: "model.litertlm"
        val destFile = File(modelDir, fileName)
        _state.value = EngineState(status = EngineStatus.Loading, modelPath = destFile.absolutePath, modelName = fileName, progress = 0.05f)
        try {
            withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    destFile.outputStream().use { output ->
                        val buf = ByteArray(8192)
                        var read: Int; var copied = 0L; val total = querySize(uri)
                        while (input.read(buf).also { read = it } != -1) {
                            output.write(buf, 0, read); copied += read
                            if (total > 0) _state.value = _state.value.copy(progress = 0.05f + 0.05f * (copied.toFloat() / total))
                        }
                    }
                } ?: throw IllegalStateException("无法读取文件")
            }
            loadModel(destFile.absolutePath)
        } catch (e: Exception) {
            copyCrashToClipboard("loadModelFromUri", e)
            _state.value = EngineState(status = EngineStatus.Error, modelPath = destFile.absolutePath, modelName = fileName, errorMessage = e.message ?: "文件复制失败")
        }
    }

    private fun resolveUriFileName(uri: Uri): String? = try {
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) { val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME); if (i >= 0) c.getString(i) else null } else null
        }
    } catch (_: Exception) { null }

    private fun querySize(uri: Uri): Long = try {
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) { val i = c.getColumnIndex(OpenableColumns.SIZE); if (i >= 0) c.getLong(i) else -1L } else -1L
        } ?: -1L
    } catch (_: Exception) { -1L }

    // 加载本地模型
    suspend fun loadModel(modelPath: String) {
        val file = File(modelPath)
        if (!file.exists()) { _state.value = EngineState(status = EngineStatus.Error, modelPath = modelPath, errorMessage = "模型文件不存在: $modelPath"); return }
        if (!file.canRead()) { _state.value = EngineState(status = EngineStatus.Error, modelPath = modelPath, errorMessage = "无法读取模型文件: $modelPath"); return }

        if (!isValidLitertlmFile(file)) {
            _state.value = EngineState(status = EngineStatus.Error, modelPath = modelPath, modelName = file.name,
                errorMessage = "模型格式不兼容，需从 HuggingFace litert-community 下载专用 .litertlm 文件")
            return
        }

        _state.value = EngineState(status = EngineStatus.Loading, modelPath = modelPath, modelName = file.name, progress = 0.1f)
        try {
            withContext(Dispatchers.IO) {
                closeEngine()
                val config = EngineConfig(modelPath = modelPath, backend = Backend.CPU(), cacheDir = context.cacheDir.absolutePath)
                val newEngine = Engine(config)
                newEngine.initialize()
                engine = newEngine
                isInitialized = true
            }
            _state.value = EngineState(status = EngineStatus.Ready, modelPath = modelPath, modelName = file.name, progress = 1f)
        } catch (e: Exception) {
            copyCrashToClipboard("loadModel", e)
            engine = null; isInitialized = false
            _state.value = EngineState(status = EngineStatus.Error, modelPath = modelPath, modelName = file.name, errorMessage = e.message ?: "未知错误")
        }
    }

    fun createConversation(config: ConversationConfig): Conversation {
        val eng = engine ?: throw IllegalStateException("引擎未初始化，请先加载模型")
        return eng.createConversation(config)
    }

    fun unloadModel() { closeEngine(); isInitialized = false; _state.value = EngineState() }

    private fun closeEngine() { try { engine?.close() } catch (_: Exception) {}; engine = null }

    override fun close() { unloadModel() }

    // 扫描 .litertlm 文件（递归+MediaStore双通道）
    fun scanModels(customPaths: List<String> = emptyList()): List<ModelInfo> {
        val seen = mutableSetOf<String>()
        val models = mutableListOf<ModelInfo>()

        fun addIfNew(file: File) {
            val abs = file.absolutePath
            if (seen.add(abs) && file.isFile && file.extension == "litertlm") {
                models.add(ModelInfo(path = abs, name = file.nameWithoutExtension, size = file.length()))
            }
        }

        // 递归扫描目录，depth限制防止过深
        fun scanDir(dir: File, depth: Int = 0) {
            if (depth > 4 || !dir.exists() || !dir.isDirectory) return
            try {
                dir.listFiles()?.forEach { f ->
                    if (f.isFile) addIfNew(f)
                    else if (f.isDirectory && depth < 4) scanDir(f, depth + 1)
                }
            } catch (_: Exception) {}
        }

        // 构建扫描路径列表（优先级从高到低）
        val scanPaths = mutableListOf<String>()

        // 1. 官方 Downloads 目录
        scanPaths.add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath)

        // 2. 外部存储根目录（有 MANAGE_EXTERNAL_STORAGE 时可读）
        try { scanPaths.add(Environment.getExternalStorageDirectory().absolutePath) } catch (_: Exception) {}

        // 3. 应用专属外部存储
        context.getExternalFilesDir(null)?.absolutePath?.let { scanPaths.add(it) }

        // 4. 应用内部存储
        scanPaths.add(context.filesDir.absolutePath)
        scanPaths.add(File(context.filesDir, "models").absolutePath)

        // 5. 用户自定义路径
        scanPaths.addAll(customPaths)

        // 6. 常见设备下载路径（兼容不同厂商）
        val fallbackPaths = listOf(
            "/storage/emulated/0/Download",
            "/storage/emulated/0/Models",
            "/storage/emulated/0/litertlm",
        )
        scanPaths.addAll(fallbackPaths)

        // 文件系统扫描
        for (path in scanPaths) {
            if (path.isBlank()) continue
            val dir = File(path)
            if (dir.exists() && dir.isDirectory) scanDir(dir)
        }

        // MediaStore 补充查询（Android 10+ Downloads 集合）
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                context.contentResolver.query(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    arrayOf(MediaStore.Downloads.DISPLAY_NAME, MediaStore.Downloads.SIZE, MediaStore.Downloads.RELATIVE_PATH),
                    "${MediaStore.Downloads.DISPLAY_NAME} LIKE ?",
                    arrayOf("%.litertlm"),
                    null
                )?.use { cursor ->
                    val nameIdx = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndexOrThrow(MediaStore.Downloads.SIZE)
                    val pathIdx = cursor.getColumnIndexOrThrow(MediaStore.Downloads.RELATIVE_PATH)
                    while (cursor.moveToNext()) {
                        val name = cursor.getString(nameIdx) ?: continue
                        val size = cursor.getLong(sizeIdx)
                        val rel = cursor.getString(pathIdx) ?: ""
                        // 拼接完整路径: DownloadsDir + relativePath + name
                        val fullPath = File(downloadsDir, "$rel$name").absolutePath
                        if (seen.add(fullPath)) {
                            models.add(ModelInfo(path = fullPath, name = name.removeSuffix(".litertlm"), size = size))
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        return models
    }

    // 验证魔数
    private fun isValidLitertlmFile(file: File): Boolean = try {
        file.inputStream().use { stream ->
            val magic = ByteArray(8)
            if (stream.read(magic) != 8) false else String(magic) == "LITERTLM"
        }
    } catch (_: Exception) { false }

    private fun copyCrashToClipboard(action: String, e: Exception) {
        try {
            val info = "[LiteRTManager 崩溃]\n操作: $action\n异常: ${e.javaClass.simpleName}\n消息: ${e.message}\n堆栈: ${e.stackTraceToString()}"
            (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("崩溃信息", info))
        } catch (_: Exception) {}
    }

    companion object {
        val RECOMMENDED_MODELS = listOf(
            RecommendedModel("gemma-4-E2B-IT", "~2.6 GB",
                "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm?download=true",
                "Gemma4 最新，6GB+ RAM", "gemma-4-E2B-it.litertlm"),
        )
    }
}

data class ModelInfo(val path: String, val name: String, val size: Long) {
    val sizeText: String get() = when {
        size < 1024 -> "$size B"
        size < 1024 * 1024 -> "${size / 1024} KB"
        size < 1024 * 1024 * 1024 -> "${"%.1f".format(size / (1024.0 * 1024.0))} MB"
        else -> "${"%.2f".format(size / (1024.0 * 1024.0 * 1024.0))} GB"
    }
}

data class RecommendedModel(val name: String, val size: String, val url: String, val description: String, val fileName: String)
