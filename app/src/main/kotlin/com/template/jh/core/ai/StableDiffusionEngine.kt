package com.template.jh.core.ai

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/**
 * Stable Diffusion 原生推理引擎
 * 通过 ProcessBuilder 启动 libstable_diffusion_core.so 作为 HTTP 服务
 * 通信方式: http://127.0.0.1:8081/generate (JSON POST)
 * 参考: local-dream-master BackendService.kt
 */
class StableDiffusionEngine(private val context: Context) {

    companion object {
        private const val TAG = "SDEngine"
        private const val EXECUTABLE = "libstable_diffusion_core.so"
        private const val PORT = 8081
        private const val BASE_URL = "http://127.0.0.1:$PORT"
        private const val HEALTH_URL = "$BASE_URL/health"
        private const val GENERATE_URL = "$BASE_URL/generate"

        @Volatile private var instance: StableDiffusionEngine? = null
        @Volatile private var nativeDir: String? = null
        @Volatile private var checkNativeDone = false
        @Volatile private var _nativeAvailable = false

        fun isNativeAvailable(): Boolean {
            if (!checkNativeDone) {
                _nativeAvailable = nativeDir?.let { File(it, EXECUTABLE).exists() } == true
                checkNativeDone = true
            }
            return _nativeAvailable
        }

        fun getInstance(context: Context): StableDiffusionEngine {
            if (instance == null) {
                synchronized(this) {
                    if (instance == null) {
                        nativeDir = context.applicationInfo.nativeLibraryDir
                        instance = StableDiffusionEngine(context)
                    }
                }
            }
            return instance!!
        }
    }

    @Volatile private var process: Process? = null
    @Volatile private var serverRunning = false

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(3600, TimeUnit.SECONDS)
        .writeTimeout(3600, TimeUnit.SECONDS)
        .callTimeout(3600, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    /** 启动原生 HTTP 服务进程 */
    suspend fun start(modelDir: File, width: Int = 512, height: Int = 512): Boolean = withContext(Dispatchers.IO) {
        if (serverRunning) return@withContext true
        try {
            val executableFile = File(nativeDir ?: return@withContext false, EXECUTABLE)
            if (!executableFile.exists()) {
                Log.e(TAG, "executable not found: ${executableFile.absolutePath}")
                return@withContext false
            }

            // 准备运行时目录：从 assets/qnnlibs/ 复制 QNN 库
            val runtimeDir = File(context.filesDir, "runtime_libs").apply { mkdirs() }
            try {
                val qnnAssets = context.assets.list("qnnlibs")
                qnnAssets?.forEach { fileName ->
                    val target = File(runtimeDir, fileName)
                    if (!target.exists() || target.length() == 0L) {
                        context.assets.open("qnnlibs/$fileName").use { input ->
                            target.outputStream().use { output -> input.copyTo(output) }
                        }
                        target.setReadable(true, true)
                        target.setExecutable(true, true)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "QNN libs not in assets, will try system path", e)
            }

            val command = mutableListOf(
                executableFile.absolutePath,
                "--clip", File(modelDir, "clip.bin").absolutePath,
                "--unet", File(modelDir, "unet.bin").absolutePath,
                "--vae_decoder", File(modelDir, "vae_decoder.bin").absolutePath,
                "--tokenizer", File(modelDir, "tokenizer.json").absolutePath,
                "--port", PORT.toString(),
                "--text_embedding_size", "768",
            )

            val qnnHtp = File(runtimeDir, "libQnnHtp.so")
            val qnnSystem = File(runtimeDir, "libQnnSystem.so")
            if (qnnHtp.exists()) {
                command += listOf("--backend", qnnHtp.absolutePath)
                command += listOf("--system_library", qnnSystem.absolutePath)
            }

            val pb = ProcessBuilder(command)
                .directory(File(nativeDir!!))
                .redirectErrorStream(true)

            val libPath = buildString {
                append(runtimeDir.absolutePath)
                append(":").append(nativeDir)
                append(":/system/lib64:/vendor/lib64")
            }
            pb.environment()["LD_LIBRARY_PATH"] = libPath
            pb.environment()["DSP_LIBRARY_PATH"] = runtimeDir.absolutePath

            process = pb.start()
            Log.i(TAG, "backend process started")
            Log.i(TAG, "LD_LIBRARY_PATH=$libPath")

            // 监控线程
            Thread {
                try {
                    process?.inputStream?.bufferedReader()?.use { reader ->
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            Log.i(TAG, "Backend: $line")
                        }
                    }
                } catch (_: Exception) {}
            }.apply { isDaemon = true }.start()

            // 等待服务启动
            var retries = 0
            while (retries < 30) {
                if (isRunning()) {
                    serverRunning = true
                    Log.i(TAG, "backend ready")
                    return@withContext true
                }
                delay(1000)
                retries++
            }
            Log.e(TAG, "backend start timeout")
            stop()
            return@withContext false
        } catch (e: Exception) {
            Log.e(TAG, "start failed", e)
            stop()
            return@withContext false
        }
    }

    /** 检查服务是否运行 */
    suspend fun isRunning(): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(HEALTH_URL).get().build()
            httpClient.newCall(request).execute().use { it.isSuccessful }
        } catch (_: Exception) { false }
    }

    /** 执行图像生成 */
    suspend fun generate(
        modelDir: File,
        prompt: String,
        negativePrompt: String,
        steps: Int,
        cfgScale: Float,
        seed: Int,
        width: Int,
        height: Int,
        onProgress: ((Float) -> Unit)? = null,
    ): Bitmap = withContext(Dispatchers.IO) {
        // 确保服务运行
        if (!serverRunning) {
            if (!start(modelDir, width, height)) {
                throw IllegalStateException("Failed to start backend service")
            }
        }

        val json = JSONObject().apply {
            put("prompt", prompt)
            put("negative_prompt", negativePrompt)
            put("steps", steps)
            put("cfg", cfgScale)
            put("width", width)
            put("height", height)
            put("use_cfg", true)
            put("scheduler", "dpm")
            if (seed >= 0) put("seed", seed.toLong())
        }

        val request = Request.Builder()
            .url(GENERATE_URL)
            .post(json.toString().toRequestBody("application/json".toMediaTypeOrNull()))
            .build()

        var resultBitmap: Bitmap? = null

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw RuntimeException("Server returned ${response.code}")
            }
            response.body?.let { body ->
                val reader = BufferedReader(InputStreamReader(body.byteStream()))
                while (true) {
                    val line = reader.readLine() ?: break
                    if (!line.startsWith("data: ")) continue
                    val data = line.substring(6).trim()
                    if (data == "[DONE]") break

                    val msg = JSONObject(data)
                    when (msg.optString("type")) {
                        "progress" -> {
                            val step = msg.optInt("step")
                            val total = msg.optInt("total_steps", steps)
                            onProgress?.invoke(step.toFloat() / total)
                        }
                        "complete" -> {
                            val b64 = msg.optString("image")
                            val rw = msg.optInt("width", width)
                            val rh = msg.optInt("height", height)
                            if (b64.isNotEmpty()) {
                                resultBitmap = rgbToBitmap(
                                    java.util.Base64.getDecoder().decode(b64), rw, rh
                                )
                            }
                        }
                        "error" -> throw RuntimeException(msg.optString("message", "Unknown"))
                    }
                }
            }
        }
        resultBitmap ?: throw RuntimeException("No image data in response")
    }

    /** 停止服务 */
    fun stop() {
        serverRunning = false
        try {
            process?.let { p ->
                p.destroy()
                if (!p.waitFor(5, TimeUnit.SECONDS)) p.destroyForcibly()
            }
        } catch (_: Exception) {}
        process = null
    }

    private fun rgbToBitmap(rgb: ByteArray, w: Int, h: Int): Bitmap {
        val pixels = IntArray(w * h)
        for (i in 0 until w * h) {
            val idx = i * 3
            pixels[i] = (0xFF shl 24) or
                ((rgb[idx].toInt() and 0xFF) shl 16) or
                ((rgb[idx + 1].toInt() and 0xFF) shl 8) or
                (rgb[idx + 2].toInt() and 0xFF)
        }
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bmp.setPixels(pixels, 0, w, 0, 0, w, h)
        return bmp
    }
}
