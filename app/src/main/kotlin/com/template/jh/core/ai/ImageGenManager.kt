package com.template.jh.core.ai

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.util.Log
import com.template.jh.core.utils.FileLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

data class ImageGenParams(
    val prompt: String = "",
    val negativePrompt: String = "",
    val steps: Int = 20,
    val cfgScale: Float = 7.0f,
    val seed: Int = -1,
    val width: Int = 512,
    val height: Int = 512,
)

data class GenerationRequest(
    val id: String = java.util.UUID.randomUUID().toString(),
    val params: ImageGenParams,
    val timestamp: Long = System.currentTimeMillis(),
    val status: GenStatus = GenStatus.Pending,
    val outputPath: String = "",
    val errorMessage: String = "",
)

enum class GenStatus { Idle, Pending, Generating, Completed, Failed }

data class ImageGenState(
    val params: ImageGenParams = ImageGenParams(),
    val status: GenStatus = GenStatus.Idle,
    val currentRequest: GenerationRequest? = null,
    val history: List<GenerationRequest> = emptyList(),
    val generatedImages: List<GenImageInfo> = emptyList(),
    val downloadStatus: DownloadStatus = DownloadStatus.Idle,
    val downloadProgress: Float = 0f,
    val downloadError: String = "",
    val extractProgress: Float = 0f,
    val extractStatus: String = "",
)

data class GenImageInfo(
    val path: String,
    val name: String,
    val size: Long,
    val timestamp: Long = System.currentTimeMillis(),
)

data class RecommendedImageModel(
    val name: String,
    val size: String,
    val url: String,
    val description: String,
    val fileName: String,
    /** ZIP 内模型目录名，解压后用于识别模型文件 */
    val modelId: String = fileName.substringBeforeLast(".zip").substringBeforeLast("_"),
)

data class CloudImageGenProfile(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String = "",
    val apiEndpoint: String = "",
    val apiKey: String = "",
    val modelName: String = "",
) {
    val displayName: String get() = name.ifEmpty { modelName }
}

class ImageGenManager(private val context: Context) {
    private val _state = MutableStateFlow(ImageGenState())
    val state: StateFlow<ImageGenState> = _state

    @Volatile private var downloadCancelled = false
    @Volatile private var downloadPaused = false

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .build()
    }

    private val baseDir: File get() = context.getExternalFilesDir(null) ?: context.filesDir

    private val modelsDir: File get() = File(baseDir, "models").also { it.mkdirs() }

    private val genDir: File get() {
        val dir = File(baseDir, "generated_images")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /** 返回模型解压后的目录 */
    fun getModelDir(modelName: String): File = File(modelsDir, modelName.substringBeforeLast(".zip"))

    /** 检查模型是否已下载并解压 */
    fun isModelDownloaded(modelName: String): Boolean {
        val dir = getModelDir(modelName)
        return dir.exists() && dir.listFiles()?.any { it.isFile && it.extension == "bin" } == true
    }

    /** 获取解压后模型目录中的 .bin 文件列表 */
    fun getModelBinFiles(modelName: String): List<File> {
        val dir = getModelDir(modelName)
        if (!dir.exists()) return emptyList()
        return dir.listFiles()?.filter { it.isFile && it.extension == "bin" }?.sorted() ?: emptyList()
    }

    fun setParams(params: ImageGenParams) {
        _state.update { it.copy(params = params) }
    }

    suspend fun submitGeneration(params: ImageGenParams): GenerationRequest {
        val request = GenerationRequest(
            params = params,
            status = GenStatus.Pending,
        )
        saveRequestToFile(request)
        _state.update { state ->
            state.copy(
                status = GenStatus.Pending,
                currentRequest = request,
                history = listOf(request) + state.history,
            )
        }
        return request
    }

    suspend fun generateImage(params: ImageGenParams): String {
        val request = submitGeneration(params)
        _state.update { it.copy(status = GenStatus.Generating) }

        return try {
            val dateStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val fileName = "gen_${dateStr}.png"
            val outputFile = File(genDir, fileName)

            // 尝试用原生引擎生成
            if (StableDiffusionEngine.isNativeAvailable()) {
                val engine = StableDiffusionEngine.getInstance(context)
                val modelDir = getModelDir(ANYTHING_V5_MODEL.fileName)
                try {
                    val bitmap = engine.generate(
                        modelDir = modelDir,
                        prompt = params.prompt,
                        negativePrompt = params.negativePrompt,
                        steps = params.steps,
                        cfgScale = params.cfgScale,
                        seed = params.seed,
                        width = params.width,
                        height = params.height,
                        onProgress = { progress ->
                            _state.update { it.copy(extractProgress = progress) }
                        },
                    )
                    withContext(Dispatchers.IO) {
                        FileOutputStream(outputFile).use { out ->
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                        }
                        bitmap.recycle()
                    }
                } catch (e: Exception) {
                    Log.w("ImageGenManager", "Native engine failed, falling back to placeholder", e)
                    withContext(Dispatchers.IO) { savePlaceholderImage(outputFile, params) }
                }
            } else {
                // 原生引擎不可用时回退到占位图
                withContext(Dispatchers.IO) {
                    savePlaceholderImage(outputFile, params)
                }
            }

            val info = GenImageInfo(
                path = outputFile.absolutePath,
                name = fileName,
                size = outputFile.length(),
            )

            val completedRequest = request.copy(
                status = GenStatus.Completed,
                outputPath = outputFile.absolutePath,
            )

            _state.update { state ->
                state.copy(
                    status = GenStatus.Completed,
                    currentRequest = completedRequest,
                    history = state.history.map { if (it.id == request.id) completedRequest else it },
                    generatedImages = listOf(info) + state.generatedImages,
                )
            }

            outputFile.absolutePath
        } catch (e: Exception) {
            Log.e("ImageGenManager", "generateImage failed", e)
            FileLogger.e("ImageGenManager", "generateImage failed: ${e.message}", e)
            val failedRequest = request.copy(
                status = GenStatus.Failed,
                errorMessage = e.message ?: "生成失败",
            )
            _state.update { it.copy(
                status = GenStatus.Failed,
                currentRequest = failedRequest,
                history = it.history.map { h -> if (h.id == request.id) failedRequest else h },
            )}
            throw e
        }
    }

    // ---- 从外部 URI 加载 ZIP 模型压缩包 ----

    suspend fun loadModelFromZipUri(uri: Uri, fileName: String) {
        downloadCancelled = false
        downloadPaused = false
        _state.update { it.copy(
            downloadStatus = DownloadStatus.Downloading,
            downloadProgress = 0f,
            extractProgress = 0f,
            extractStatus = "正在复制文件...",
        )}
        try {
            val destFile = File(modelsDir, fileName)
            withContext(Dispatchers.IO) {
                val input = context.contentResolver.openInputStream(uri)
                    ?: throw IOException("无法读取所选文件")
                FileOutputStream(destFile).use { output ->
                    val buf = ByteArray(32 * 1024)
                    var read: Int
                    var copied = 0L
                    while (input.read(buf).also { read = it } != -1) {
                        if (downloadCancelled) throw java.util.concurrent.CancellationException("已取消")
                        output.write(buf, 0, read)
                        copied += read
                        _state.update { it.copy(
                            downloadProgress = (copied.toFloat() / (copied + 1)).coerceAtMost(0.99f)
                        )}
                    }
                }
                input.close()
            }

            if (!downloadCancelled) {
                _state.update { it.copy(
                    downloadStatus = DownloadStatus.Downloading,
                    downloadProgress = 1f,
                    extractStatus = "正在解压...",
                )}
                extractZip(destFile, getModelDir(fileName))
                destFile.delete()

                if (!downloadCancelled) {
                    _state.update { it.copy(
                        downloadStatus = DownloadStatus.Completed,
                        downloadProgress = 1f,
                        extractProgress = 1f,
                        extractStatus = "解压完成",
                    )}
                }
            }
        } catch (e: java.util.concurrent.CancellationException) {
            _state.update { it.copy(
                downloadStatus = DownloadStatus.Idle,
                downloadProgress = 0f,
                downloadError = "已取消",
                extractStatus = "",
            )}
        } catch (e: Exception) {
            Log.e("ImageGenManager", "loadModelFromZipUri failed", e)
            FileLogger.e("ImageGenManager", "loadModelFromZipUri failed: ${e.message}", e)
            _state.update { it.copy(
                downloadStatus = DownloadStatus.Error,
                downloadError = e.message ?: "加载失败",
                extractStatus = "失败: ${e.message}",
            )}
        }
    }

    // ---- 下载 + 解压 ----

    suspend fun downloadModel(url: String, fileName: String) {
        downloadCancelled = false
        downloadPaused = false
        _state.update { it.copy(
            downloadStatus = DownloadStatus.Downloading,
            downloadProgress = 0f,
            extractProgress = 0f,
            extractStatus = "",
        )}
        try {
            val destFile = File(modelsDir, fileName)
            withContext(Dispatchers.IO) {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36")
                    .header("Accept", "*/*")
                    .header("Accept-Encoding", "identity")
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("服务器返回 ${response.code}: ${response.message}")
                    }
                    val body = response.body ?: throw IOException("响应体为空")
                    val totalBytes = body.contentLength()
                    val input = body.byteStream()
                    FileOutputStream(destFile).use { output ->
                        val buf = ByteArray(32 * 1024)
                        var read: Int
                        var copied = 0L
                        var lastReportTime = 0L
                        while (input.read(buf).also { read = it } != -1) {
                            if (downloadCancelled) throw java.util.concurrent.CancellationException("下载已取消")
                            while (downloadPaused) {
                                if (downloadCancelled) throw java.util.concurrent.CancellationException("下载已取消")
                                delay(200)
                            }
                            output.write(buf, 0, read)
                            copied += read
                            val now = System.currentTimeMillis()
                            if (now - lastReportTime > 50 || copied >= totalBytes) {
                                lastReportTime = now
                                val progress = if (totalBytes > 0) {
                                    copied.toFloat() / totalBytes
                                } else {
                                    (copied.toFloat() / (copied + 512 * 1024)).coerceAtMost(0.95f)
                                }
                                _state.update { it.copy(downloadProgress = progress) }
                            }
                        }
                    }
                }
            }

            if (!downloadCancelled) {
                _state.update { it.copy(
                    downloadStatus = DownloadStatus.Downloading,
                    downloadProgress = 1f,
                    extractStatus = "正在解压...",
                )}

                // 自动解压 .zip
                if (fileName.endsWith(".zip", true)) {
                    extractZip(destFile, getModelDir(fileName))
                    destFile.delete() // 删除压缩包
                }

                if (!downloadCancelled) {
                    _state.update { it.copy(
                        downloadStatus = DownloadStatus.Completed,
                        downloadProgress = 1f,
                        extractProgress = 1f,
                        extractStatus = "解压完成",
                    )}
                }
            }
        } catch (e: java.util.concurrent.CancellationException) {
            _state.update { it.copy(
                downloadStatus = DownloadStatus.Idle,
                downloadProgress = 0f,
                downloadError = "已取消",
                extractStatus = "",
            )}
        } catch (e: Exception) {
            Log.e("ImageGenManager", "downloadModel failed", e)
            FileLogger.e("ImageGenManager", "downloadModel failed: ${e.message}", e)
            _state.update { it.copy(
                downloadStatus = DownloadStatus.Error,
                downloadError = e.message ?: "下载失败",
                extractStatus = "失败: ${e.message}",
            )}
        }
    }

    /** 解压 ZIP 到目标目录 */
    private suspend fun extractZip(zipFile: File, destDir: File) = withContext(Dispatchers.IO) {
        destDir.mkdirs()
        // 先解压到临时目录
        val tmpDir = File(destDir.parentFile, ".extract_${destDir.name}")
        tmpDir.mkdirs()
        tmpDir.deleteRecursively()
        tmpDir.mkdirs()

        try {
            // 先删除旧模型目录
            if (destDir.exists()) destDir.deleteRecursively()

            var totalEntries = 0
            var extractedEntries = 0

            // 统计总条目数
            ZipInputStream(zipFile.inputStream().buffered()).use { zis ->
                while (zis.nextEntry != null) { totalEntries++; zis.closeEntry() }
            }

            // 实际解压
            ZipInputStream(zipFile.inputStream().buffered()).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val fileName = entry.name.substringAfterLast('/')
                        if (fileName.isNotEmpty() && !fileName.startsWith(".") && !fileName.startsWith("__MACOSX")) {
                            val outFile = File(tmpDir, fileName)
                            FileOutputStream(outFile).use { output ->
                                zis.copyTo(output)
                            }
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                    extractedEntries++
                    if (totalEntries > 0) {
                        _state.update { it.copy(extractProgress = extractedEntries.toFloat() / totalEntries) }
                    }
                }
            }

            // 移动到目标目录
            tmpDir.listFiles()?.forEach { file ->
                file.copyTo(File(destDir, file.name), overwrite = true)
            }
        } finally {
            if (tmpDir.exists()) tmpDir.deleteRecursively()
        }
    }

    fun cancelDownload() { downloadCancelled = true; downloadPaused = false }

    fun pauseDownload() { downloadPaused = true; _state.update { it.copy(downloadStatus = DownloadStatus.Paused) } }

    fun resumeDownload() { downloadPaused = false; _state.update { it.copy(downloadStatus = DownloadStatus.Downloading) } }

    fun resetDownloadState() {
        _state.update { it.copy(
            downloadStatus = DownloadStatus.Idle,
            downloadProgress = 0f,
            downloadError = "",
            extractProgress = 0f,
            extractStatus = "",
        )}
        downloadCancelled = false
        downloadPaused = false
    }

    // ---- 图片输出 ----

    fun getOutputDir(): File = genDir

    fun getRecentImages(): List<GenImageInfo> = _state.value.generatedImages

    fun listGeneratedImages(): List<GenImageInfo> {
        val files = genDir.listFiles() ?: emptyArray()
        return files
            .filter { it.isFile && isImageFile(it.name) }
            .sortedByDescending { it.lastModified() }
            .map { GenImageInfo(
                path = it.absolutePath,
                name = it.name,
                size = it.length(),
                timestamp = it.lastModified(),
            )}
    }

    private fun isImageFile(name: String) = name.let {
        it.endsWith(".png", true) || it.endsWith(".jpg", true) ||
        it.endsWith(".jpeg", true) || it.endsWith(".webp", true) ||
        it.endsWith(".gif", true) || it.endsWith(".bmp", true)
    }

    // ---- 占位图（原生引擎不可用时回退）----

    private fun savePlaceholderImage(file: File, params: ImageGenParams) {
        val w = params.width.coerceIn(256, 2048)
        val h = params.height.coerceIn(256, 2048)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        val paint = android.graphics.Paint().apply {
            color = android.graphics.Color.rgb(30, 40, 60)
            style = android.graphics.Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        paint.apply {
            color = android.graphics.Color.rgb(100, 180, 255)
            textSize = w.coerceAtMost(h) * 0.05f
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
        }
        val lines = listOf(
            "AnythingV5 (QNN)",
            "Prompt: ${params.prompt.take(60)}",
            "Steps: ${params.steps}  CFG: ${params.cfgScale}",
            "Seed: ${params.seed}  ${w}x${h}",
            "[原生引擎未激活 - 占位图]",
        )
        var y = h * 0.3f
        for (line in lines) {
            canvas.drawText(line, w / 2f, y, paint)
            y += paint.textSize * 1.5f
        }
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        bitmap.recycle()
    }

    private fun saveRequestToFile(request: GenerationRequest) {
        try {
            val file = File(genDir.parentFile?.let { File(it, "gen_requests") } ?: return, "${request.id}.json")
            file.parentFile?.mkdirs()
            val json = JSONObject().apply {
                put("id", request.id)
                put("prompt", request.params.prompt)
                put("negative_prompt", request.params.negativePrompt)
                put("steps", request.params.steps)
                put("cfg_scale", request.params.cfgScale)
                put("seed", request.params.seed)
                put("width", request.params.width)
                put("height", request.params.height)
                put("timestamp", request.timestamp)
                put("status", request.status.name)
            }
            file.writeText(json.toString(2))
        } catch (_: Exception) {}
    }

    /** 扫描已检测到的本地生图模型（已下载并解压） */
    fun scanDetectedModels(): List<RecommendedImageModel> {
        return RECOMMENDED_IMAGE_MODELS.filter { isModelDownloaded(it.fileName) }
    }

    companion object {
        val ANYTHING_V5_MODEL = RecommendedImageModel(
            name = "Anything V5.0 (SD1.5)",
            size = "~1.1 GB",
            url = "https://huggingface.co/xororz/sd-qnn/resolve/main/AnythingV5_qnn2.28_8gen1.zip",
            description = "高质二次元风格·骁龙QNN (NPU)",
            fileName = "AnythingV5_qnn2.28_8gen1.zip",
            modelId = "AnythingV5_qnn2.28_8gen1",
        )

        val RECOMMENDED_IMAGE_MODELS = listOf(ANYTHING_V5_MODEL)
    }

    fun resetState() {
        _state.value = ImageGenState()
    }
}
