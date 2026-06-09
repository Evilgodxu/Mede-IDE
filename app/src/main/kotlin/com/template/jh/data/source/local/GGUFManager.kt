package com.template.jh.data.source.local

import android.content.Context
import android.util.Log
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
 *
 * 构建参考:
 *   git clone https://github.com/ggml-org/llama.cpp
 *   cmake -B build-android \
 *     -DCMAKE_TOOLCHAIN_FILE=\$NDK/build/cmake/android.toolchain.cmake \
 *     -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-26 \
 *     -DLLAMA_VULKAN=ON -DBUILD_SHARED_LIBS=ON \
 *     -DGGML_CUDA=OFF
 *   cmake --build build-android --config Release
 *   # cp build-android/libllama.so, libggml.so, libggml-vulkan.so -> app/jniLibs/arm64-v8a/
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

    // ---- JNI (matches llama.cpp android JNI) ----
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

    /** 读取模型元数据（如 context_length），返回 key→value map */
    external fun getModelInfo(modelPath: String): Array<String>?

    /** 初始化多模态投影器 */
    external fun initMultimodal(ctx: Long, projectorPath: String, useGpu: Boolean): Boolean
    /** 查询多模态能力 */
    external fun getMultimodalSupport(ctx: Long): BooleanArray?

    interface GenerateCallback {
        fun onToken(token: String): Boolean
    }
}

/**
 * GGUF 模型管理器 — 基于 llama.cpp 的 .gguf 推理引擎。
 *
 * 与 [LiteRTManager] 平行，负责 GGUF 模型加载 / 推理 / 卸载。
 *
 * ## 关键特性
 * - GGUF 魔数校验（前 4 字节 "GGUF"）
 * - 自动读取模型 context_length 元数据
 * - 多模态投影器（mmproj）支持
 * - ctx_shift / use_mlock 配置
 * - 参数透传至原生层
 */
class GGUFManager(private val context: Context) : AutoCloseable {

    private val _state = MutableStateFlow(EngineState())
    val state: StateFlow<EngineState> = _state

    @Volatile private var modelPtr: Long = 0
    @Volatile private var ctxPtr: Long = 0
    @Volatile var isInitialized: Boolean = false
        private set

    // === 可配置参数（对应 llama.cpp） ===
    /** 上下文窗口大小（默认 2048，加载时尝试读取模型 metadata 覆盖） */
    @Volatile var nCtx: Int = 2048
    /** CPU 线程数 */
    @Volatile var nThreads: Int = 4
    /** 批处理大小 */
    @Volatile var nBatch: Int = 512
    /** GPU 卸载层数（0=纯 CPU，99=全部卸到 GPU） */
    @Volatile var nGpuLayers: Int = 0
    /** 锁定物理内存（避免换页） */
    @Volatile var useMlock: Boolean = true
    /** 上下文偏移（长对话滚动历史） */
    @Volatile var ctxShift: Boolean = true
    /** 额外原生参数（key→value，透传至 llama.cpp，如 repeat_penalty, mirostat 等） */
    @Volatile var extraParams: Map<String, String> = emptyMap()

    // === 采样参数（传至 generate） ===
    @Volatile var temperature: Float = 0.2f
    @Volatile var topK: Int = 40
    @Volatile var topP: Float = 0.9f
    @Volatile var seed: Int = -1
    @Volatile var maxTokens: Int = 2048

    // === 多模态 ===
    /** 多模态投影器文件路径（.mmproj / .gguf） */
    @Volatile var projectorPath: String = ""
    /** 多模态是否可用 */
    @Volatile var imagesSupported: Boolean = false
        private set
    /** 当前模型是否支持多模态 */
    @Volatile var isMultimodal: Boolean = false
        private set

    /** 原生库已加载 */
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

    // ============================================================
    //  GGUF 文件校验
    // ============================================================

    companion object {
        /** GGUF 魔数：前 4 字节 = "GGUF" */
        private val GGUF_MAGIC = byteArrayOf(0x47, 0x47, 0x55, 0x46) // "GGUF"

        /** 验证文件是否为有效的 GGUF 格式 */
        fun isValidGGUFFile(file: File): Boolean = try {
            RandomAccessFile(file, "r").use { raf ->
                if (raf.length() < 4) return@use false
                val magic = ByteArray(4)
                raf.readFully(magic)
                magic.contentEquals(GGUF_MAGIC)
            }
        } catch (_: Exception) { false }

        /**
         * 从 GGUF 文件头读取 context_length 元数据。
         *
         * GGUF v3 文件结构:
         *   [4] magic
         *   [4] version (uint32)
         *   [8] tensor_count (uint64)
         *   [8] metadata_kv_count (uint64)
         *   --- metadata KV pairs ---
         *
         * 返回 0 表示读取失败。
         */
        fun readContextLength(file: File): Int = try {
            RandomAccessFile(file, "r").use { raf ->
                val magic = ByteArray(4)
                raf.readFully(magic)
                if (!magic.contentEquals(GGUF_MAGIC)) return@use 0

                val version = readU32(raf)        // uint32 version
                val tensorCount = readU64(raf)     // uint64 tensor_count
                val kvCount = readU64(raf)         // uint64 metadata_kv_count

                // 跳过前两个 KV 的偏移计算
                for (i in 0 until kvCount.toInt().coerceAtMost(200)) {
                    val key = readGGUFString(raf) ?: break
                    val kvType = readU32(raf)      // uint32 value-type
                    val value = when (kvType.toInt()) {
                        6 -> { // int64
                            readU64(raf).toString()
                        }
                        8 -> { // float64
                            readF64(raf).toString()
                        }
                        12 -> { // string
                            readGGUFString(raf) ?: ""
                        }
                        else -> {
                            // 跳过复杂类型
                            skipGGUFValue(raf, kvType.toInt())
                            ""
                        }
                    }
                    if (key == "llama.context_length" || key.contains("context_length")) {
                        val ctxLen = value.toIntOrNull()
                        if (ctxLen != null && ctxLen > 0) return@use ctxLen
                    }
                }
                0
            }
        } catch (_: Exception) { 0 }

        private fun readU32(raf: RandomAccessFile): Long {
            val bytes = ByteArray(4)
            raf.readFully(bytes)
            return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getInt().toLong() and 0xFFFFFFFFL
        }

        private fun readU64(raf: RandomAccessFile): Long {
            val bytes = ByteArray(8)
            raf.readFully(bytes)
            return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).long
        }

        private fun readF64(raf: RandomAccessFile): Double {
            val bytes = ByteArray(8)
            raf.readFully(bytes)
            return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).double
        }

        private fun readGGUFString(raf: RandomAccessFile): String? {
            val len = readU64(raf)
            if (len <= 0 || len > 1024 * 1024) return null
            val bytes = ByteArray(len.toInt())
            raf.readFully(bytes)
            return String(bytes, Charsets.UTF_8)
        }

        private fun skipGGUFValue(raf: RandomAccessFile, type: Int) {
            when (type) {
                0 -> {} // uint8
                1 -> {} // int8
                2 -> { raf.skipBytes(2) } // uint16
                3 -> { raf.skipBytes(2) } // int16
                4 -> { raf.skipBytes(4) } // uint32
                5 -> { raf.skipBytes(4) } // int32
                6 -> { raf.skipBytes(8) } // float32
                7 -> { raf.skipBytes(8) } // bool
                8 -> { raf.skipBytes(8) } // float64
                10 -> { raf.skipBytes(4) } // uint32 (tensor)
                11 -> { // array
                    val elemType = readU32(raf)
                    val len = readU64(raf)
                    for (j in 0 until len) skipGGUFValue(raf, elemType.toInt())
                }
                12 -> { readGGUFString(raf) } // string
                else -> { raf.skipBytes(8) }
            }
        }

        /** 推荐的 GGUF 模型 */
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

    /** 加载 GGUF 模型 */
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
                modelName = file.name, errorMessage = "Invalid GGUF magic bytes (expected 'GGUF' header)")
            return
        }

        // 读取模型元数据中的 context_length
        val metaCtx = readContextLength(file)
        val effectiveCtx = if (metaCtx > 0) metaCtx else nCtx.coerceAtMost(8192)
        if (metaCtx > 0) nCtx = effectiveCtx

        _state.value = EngineState(status = EngineStatus.Loading, modelPath = modelPath,
            modelName = file.name, progress = 0.1f)

        try {
            withContext(Dispatchers.IO) {
                unloadNative()
                modelPtr = LlamaNative.loadModel(modelPath, nGpuLayers)
                require(modelPtr != 0L) { "llama.cpp loadModel returned null" }
                ctxPtr = LlamaNative.createContext(modelPtr, effectiveCtx, nThreads, nBatch)
                require(ctxPtr != 0L) { "llama.cpp createContext returned null" }
                isInitialized = true
            }

            // 加载多模态投影器
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
                modelName = file.name, progress = 1f, contextWindow = effectiveCtx)
        } catch (e: Exception) {
            Log.e("GGUFManager", "loadModel failed", e)
            FileLogger.e("GGUFManager", "loadModel failed: ${e.message}", e)
            unloadNative()
            _state.value = EngineState(status = EngineStatus.Error, modelPath = modelPath,
                modelName = file.name, errorMessage = e.message ?: "load failed")
        }
    }

    /** 流式生成文本 */
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

    /** 重置 KV cache */
    fun resetContext() {
        if (ctxPtr != 0L) {
            try { LlamaNative.resetContext(ctxPtr) } catch (_: Exception) {}
        }
    }

    /** 卸载模型 */
    fun unloadModel() {
        unloadNative()
        isInitialized = false
        imagesSupported = false
        isMultimodal = false
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
