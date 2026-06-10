package com.template.jh.data.source.local

import android.content.Context
import android.util.Log
import com.template.jh.core.gguf.GGUFReader
import com.template.jh.core.utils.CpuFeatureDetector
import com.template.jh.core.utils.FileLogger
import com.template.jh.model.chat.EngineState
import com.template.jh.model.chat.EngineStatus
import com.template.jh.model.chat.ModelInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * llama.cpp JNI 原生桥接。
 *
 * 匹配 llama.cpp Android 模块 JNI 接口。
 * 使用前需将编译好的 libllama.so / libggml.so / libggml-vulkan.so 放入 jniLibs/arm64-v8a/。
 */
object LlamaNative {
    private var nativeLoaded = false

    fun tryLoad(): Boolean {
        if (nativeLoaded) return true
        return try {
            System.loadLibrary("llama")
            nativeLoaded = true
            true
        } catch (e: UnsatisfiedLinkError) {
            Log.w("LlamaNative", "libllama.so not found: ${e.message}")
            false
        }
    }

    val isAvailable: Boolean get() = nativeLoaded

    external fun loadModel(modelPath: String, nGpuLayers: Int): Long
    external fun createContext(model: Long, nCtx: Int, nThreads: Int, nBatch: Int): Long
    external fun generate(
        ctx: Long, prompt: String, maxTokens: Int,
        temperature: Float, topK: Int, topP: Float, seed: Int,
        callback: GenerateCallback,
    ): Boolean
    external fun freeContext(ctx: Long)
    external fun freeModel(model: Long)
    external fun resetContext(ctx: Long)
    external fun getModelInfo(modelPath: String): Array<String>?
    external fun initMultimodal(ctx: Long, projectorPath: String, useGpu: Boolean): Boolean
    external fun getMultimodalSupport(ctx: Long): BooleanArray?

    interface GenerateCallback {
        fun onToken(token: String): Boolean
    }
}

/**
 * GGUF 模型管理器 — 基于 llama.cpp 的 .gguf 推理引擎。
 *
 * 使用 [GGUFReader] 自动读取 GGUF 文件元数据获取精确 context_length，
 * 使用 [CpuFeatureDetector] 输出设备 CPU 特性信息。
 */
class GGUFManager(private val context: Context) : AutoCloseable {

    private val _state = MutableStateFlow(EngineState())
    val state: StateFlow<EngineState> = _state

    @Volatile private var modelPtr: Long = 0
    @Volatile private var ctxPtr: Long = 0
    @Volatile var isInitialized: Boolean = false
        private set

    @Volatile var nCtx: Int = 2048
    @Volatile var nThreads: Int = CpuFeatureDetector.detect().cpuCount.coerceIn(2, 8)
    @Volatile var nBatch: Int = 512
    @Volatile var nGpuLayers: Int = 0
    @Volatile var useMlock: Boolean = true
    @Volatile var ctxShift: Boolean = true
    @Volatile var extraParams: Map<String, String> = emptyMap()

    @Volatile var temperature: Float = 0.2f
    @Volatile var topK: Int = 40
    @Volatile var topP: Float = 0.9f
    @Volatile var seed: Int = -1
    @Volatile var maxTokens: Int = 2048

    /** 从 GGUF 文件头读取到的原始 context_length（0=未知） */
    @Volatile var ggufContextLength: Int = 0
        private set

    @Volatile var projectorPath: String = ""
    @Volatile var imagesSupported: Boolean = false
        private set
    @Volatile var isMultimodal: Boolean = false
        private set
    @Volatile var nativeAvailable: Boolean = false
        private set

    init {
        nativeAvailable = LlamaNative.tryLoad()
        if (!nativeAvailable) {
            _state.value = EngineState(
                status = EngineStatus.Error,
                errorMessage = "libllama.so not found, place it in jniLibs/arm64-v8a/",
            )
        }
    }

    companion object {
        private val GGUF_MAGIC = byteArrayOf(0x47, 0x47, 0x55, 0x46)

        fun isValidGGUFFile(file: File): Boolean = try {
            RandomAccessFile(file, "r").use { raf ->
                if (raf.length() < 4) return@use false
                val magic = ByteArray(4)
                raf.readFully(magic)
                magic.contentEquals(GGUF_MAGIC)
            }
        } catch (_: Exception) { false }

        val RECOMMENDED_MODELS = listOf(
            ModelInfo(
                path = "https://huggingface.co/bartowski/Llama-3.2-1B-Instruct-GGUF/resolve/main/Llama-3.2-1B-Instruct-Q4_K_M.gguf",
                name = "Llama 3.2 1B Q4_K_M",
                size = 808L * 1024 * 1024,
            ),
            ModelInfo(
                path = "https://huggingface.co/bartowski/Llama-3.2-3B-Instruct-GGUF/resolve/main/Llama-3.2-3B-Instruct-Q4_K_M.gguf",
                name = "Llama 3.2 3B Q4_K_M",
                size = 2020L * 1024 * 1024,
            ),
            ModelInfo(
                path = "https://huggingface.co/bartowski/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/Qwen2.5-1.5B-Instruct-Q4_K_M.gguf",
                name = "Qwen2.5 1.5B Q4_K_M",
                size = 1100L * 1024 * 1024,
            ),
            ModelInfo(
                path = "https://huggingface.co/unsloth/Qwen3.5-0.8B-GGUF/resolve/main/Qwen3.5-0.8B-Q4_K_M.gguf",
                name = "Qwen 3.5 0.8B Q4_K_M",
                size = 650L * 1024 * 1024,
            ),
            ModelInfo(
                path = "https://huggingface.co/unsloth/Qwen3.5-2B-GGUF/resolve/main/Qwen3.5-2B-Q4_K_M.gguf",
                name = "Qwen 3.5 2B Q4_K_M",
                size = 1400L * 1024 * 1024,
            ),
        )
    }

    // ============================================================
    //  加载 / 卸载
    // ============================================================

    suspend fun loadModel(modelPath: String) {
        if (!nativeAvailable) {
            _state.value = EngineState(status = EngineStatus.Error, modelPath = modelPath,
                errorMessage = "libllama.so not loaded")
            return
        }

        val file = File(modelPath)
        if (!file.exists()) {
            _state.value = EngineState(status = EngineStatus.Error, modelPath = modelPath,
                errorMessage = "File not found: $modelPath")
            return
        }
        if (!isValidGGUFFile(file)) {
            _state.value = EngineState(status = EngineStatus.Error, modelPath = modelPath,
                modelName = file.name, errorMessage = "Invalid GGUF magic bytes")
            return
        }

        // 使用 GGUFReader 精确读取 context_length
        val reader = GGUFReader(file)
        val metaCtx = reader.readContextLength()
        val metaAll = reader.readMetadata()
        ggufContextLength = metaCtx

        val effectiveCtx = if (metaCtx > 0) metaCtx.coerceAtMost(8192) else nCtx
        if (metaCtx > 0) nCtx = effectiveCtx

        // 记录 CPU 特性
        val cpu = CpuFeatureDetector.detect()
        Log.d("GGUFManager", "CPU: ${cpu.featureLevel}, cores=${cpu.cpuCount}, model=${cpu.cpuModel}")
        FileLogger.d("GGUFManager", "CPU: ${cpu.featureLevel}, cores=${cpu.cpuCount}")
        val modelName = metaAll.modelName ?: file.name

        _state.value = EngineState(status = EngineStatus.Loading, modelPath = modelPath,
            modelName = modelName, progress = 0.1f, contextWindow = effectiveCtx)

        try {
            withContext(Dispatchers.IO) {
                unloadNative()
                modelPtr = LlamaNative.loadModel(modelPath, nGpuLayers)
                require(modelPtr != 0L) { "llama.cpp loadModel returned null" }
                ctxPtr = LlamaNative.createContext(modelPtr, effectiveCtx, nThreads, nBatch)
                require(ctxPtr != 0L) { "llama.cpp createContext returned null" }
                isInitialized = true
            }

            if (projectorPath.isNotBlank()) {
                try {
                    val projFile = File(projectorPath)
                    if (projFile.exists()) {
                        val ok = LlamaNative.initMultimodal(ctxPtr, projectorPath, nGpuLayers > 0)
                        if (ok) {
                            val support = LlamaNative.getMultimodalSupport(ctxPtr)
                            imagesSupported = support != null && support.size > 0 && support[0]
                            isMultimodal = imagesSupported
                        }
                    }
                } catch (e: Exception) {
                    Log.w("GGUFManager", "initMultimodal failed: ${e.message}")
                }
            }

            _state.value = EngineState(status = EngineStatus.Ready, modelPath = modelPath,
                modelName = modelName, progress = 1f, contextWindow = effectiveCtx)
        } catch (e: Exception) {
            Log.e("GGUFManager", "loadModel failed", e)
            FileLogger.e("GGUFManager", "loadModel failed: ${e.message}", e)
            unloadNative()
            _state.value = EngineState(status = EngineStatus.Error, modelPath = modelPath,
                modelName = modelName, errorMessage = e.message ?: "load failed")
        }
    }

    suspend fun generate(
        prompt: String,
        onToken: ((String) -> Boolean)? = null,
    ): String = withContext(Dispatchers.IO) {
        require(ctxPtr != 0L) { "Engine not initialized" }
        val result = StringBuilder()
        val callback = object : LlamaNative.GenerateCallback {
            override fun onToken(token: String): Boolean {
                result.append(token)
                return onToken?.invoke(token) ?: true
            }
        }
        val success = LlamaNative.generate(
            ctx = ctxPtr, prompt = prompt,
            maxTokens = maxTokens, temperature = temperature,
            topK = topK, topP = topP, seed = seed,
            callback = callback,
        )
        if (!success) throw RuntimeException("llama.cpp generate failed")
        result.toString()
    }

    fun resetContext() {
        if (ctxPtr != 0L) {
            try { LlamaNative.resetContext(ctxPtr) } catch (_: Exception) {}
        }
    }

    fun unloadModel() {
        unloadNative()
        isInitialized = false
        imagesSupported = false
        isMultimodal = false
        ggufContextLength = 0
        _state.value = EngineState()
    }

    private fun unloadNative() {
        if (ctxPtr != 0L) {
            try { LlamaNative.freeContext(ctxPtr) } catch (_: Exception) {}
            ctxPtr = 0
        }
        if (modelPtr != 0L) {
            try { LlamaNative.freeModel(modelPtr) } catch (_: Exception) {}
            modelPtr = 0
        }
    }

    override fun close() { unloadModel() }
}
