package com.medeide.jh.core.data.source.local

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.LogSeverity
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import com.google.ai.edge.litertlm.SamplerConfig
import com.medeide.jh.core.data.logging.FileLogger
import com.medeide.jh.model.chat.BackendType
import com.medeide.jh.model.chat.ModelParams
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 管理 LiteRT-LM 引擎的整个生命周期：加载、对话、卸载。
 * 所有 Engine/Conversation 操作应在后台协程中调用。
 */
class LiteRTEngineManager(private val context: Context) {

    private var engine: Engine? = null
    private var conversation: Conversation? = null
    private val _isLoaded = AtomicBoolean(false)
    private var _modelParams = ModelParams()

    val isLoaded: Boolean get() = _isLoaded.get()

    /** 当前加载的模型路径 */
    var modelPath: String = ""
        private set

    /** 创建对话时使用的系统指令（含名称等），持久化到下次创建 */
    var systemInstructionForConversation: String = ""

    /** 更新参数（不重载模型，下次加载时生效） */
    fun updateParams(params: ModelParams) {
        _modelParams = params
    }

    /** 加载模型，阻塞直到完成。应在后台线程调用。 */
    suspend fun loadModel(
        modelPath: String,
        params: ModelParams = _modelParams,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val file = File(modelPath)
            if (!file.exists()) {
                return@withContext Result.failure(Exception("模型文件不存在: $modelPath"))
            }
            if (!isValidLitertlmFile(file)) {
                return@withContext Result.failure(Exception("模型格式不兼容，需从 HuggingFace litert-community 下载专用 .litertlm 文件"))
            }

            unloadModel()
            this@LiteRTEngineManager.modelPath = modelPath
            _modelParams = params

            // 静默 native 日志
            Engine.setNativeMinLogSeverity(LogSeverity.ERROR)

            val backend = when (params.backendType) {
                BackendType.CPU -> Backend.CPU()
                BackendType.GPU -> Backend.GPU()
                BackendType.NPU -> Backend.NPU(nativeLibraryDir = context.applicationInfo.nativeLibraryDir)
            }

            val cacheDir = File(context.cacheDir, "litertlm_cache").also { it.mkdirs() }.absolutePath

            val engineConfig = EngineConfig(
                modelPath = modelPath,
                backend = backend,
                maxNumTokens = params.contextWindowTokens,
                cacheDir = cacheDir,
            )

            val eng = Engine(engineConfig)
            eng.initialize()
            engine = eng
            _isLoaded.set(true)

            FileLogger.i("LiteRTEngine", "模型加载完成: $modelPath backend=${params.backendType}")
            Result.success(Unit)
        } catch (e: Exception) {
            FileLogger.e("LiteRTEngine", "模型加载失败", e)
            _isLoaded.set(false)
            engine = null
            Result.failure(e)
        }
    }

    /** 创建新对话。需在模型已加载后调用。 */
    fun createConversation(
        systemInstruction: String? = null,
        samplerConfig: SamplerConfig? = samplerFromParams(_modelParams),
    ): Conversation? {
        val eng = engine ?: return null
        try {
            conversation?.close()
            val config = ConversationConfig(
                systemInstruction = systemInstruction?.let { com.google.ai.edge.litertlm.Contents.of(it) },
                samplerConfig = samplerConfig,
            )
            val conv = eng.createConversation(config)
            conversation = conv
            return conv
        } catch (e: Exception) {
            FileLogger.e("LiteRTEngine", "创建对话失败", e)
            return null
        }
    }

    /** 获取当前对话实例，如不存在则创建新的 */
    fun getOrCreateConversation(): Conversation? {
        if (conversation?.isAlive == true) return conversation
        return createConversation(systemInstructionForConversation.ifEmpty { null })
    }

    /** 发送消息（同步），返回完整的 Message */
    fun sendMessage(text: String, extraContext: Map<String, Any> = emptyMap()): Message? {
        val conv = getOrCreateConversation() ?: return null
        return try {
            conv.sendMessage(text, extraContext)
        } catch (e: Exception) {
            FileLogger.e("LiteRTEngine", "sendMessage失败", e)
            null
        }
    }

    /** 发送消息（流式，Flow） */
    fun sendMessageAsync(
        text: String,
        callback: MessageCallback,
        extraContext: Map<String, Any> = emptyMap(),
    ) {
        val conv = getOrCreateConversation() ?: run {
            callback.onError(IllegalStateException("模型未加载"))
            return
        }
        try {
            conv.sendMessageAsync(text, callback, extraContext)
        } catch (e: Exception) {
            FileLogger.e("LiteRTEngine", "sendMessageAsync失败", e)
            callback.onError(e)
        }
    }

    /** 取消正在进行的推理 */
    fun cancelProcess() {
        try {
            conversation?.cancelProcess()
        } catch (_: Exception) { }
    }

    /** 卸载模型，释放资源 */
    suspend fun unloadModel() = withContext(Dispatchers.IO) {
        try {
            conversation?.close()
        } catch (_: Exception) { }
        conversation = null
        try {
            engine?.close()
        } catch (_: Exception) { }
        engine = null
        _isLoaded.set(false)
        modelPath = ""
        FileLogger.i("LiteRTEngine", "模型已卸载")
    }

    companion object {
        fun samplerFromParams(params: ModelParams): SamplerConfig = SamplerConfig(
            topK = params.topK,
            topP = params.topP,
            temperature = params.temperature,
            seed = params.seed,
        )

        // NPU 驱动库特征文件名
        private val NPU_VENDOR_LIBS = listOf(
            "libQnnHtp.so",       // 高通 QNN HTP（Hexagon）
            "libQnnCpu.so",       // 高通 QNN CPU fallback
            "libQnnGpu.so",       // 高通 QNN GPU
            "libQnnDsp.so",       // 高通 QNN DSP
            "libneuropilot.so",   // 联发科 NeuroPilot
            "libmtk_drv.so",      // 联发科驱动
            "libhiai.so",         // 华为 HiAI（Kirin NPU）
            "libhiai_ir.so",      // 华为 HiAI IR
            "libedgetpu.so",      // Google Edge TPU / 三星
            "librknnrt.so",       // Rockchip RKNN
            "libamlnpu.so",       // Amlogic NPU
            "libnpu.so",          // 通用 / Allwinner NPU
        )

        // 常见 NPU 驱动库存放目录
        private val NPU_SCAN_DIRS = listOf(
            "/vendor/lib64/",
            "/vendor/lib/",
            "/vendor/lib64/egl/",
            "/system/lib64/",
            "/system/vendor/lib64/",
            "/system/lib/",
            "/vendor/firmware/",
        )

        /** 自动检测设备上 NPU 驱动库所在目录 */
        fun detectNpuLibraryDir(context: Context): String {
            for (dirPath in NPU_SCAN_DIRS) {
                val dir = File(dirPath)
                if (!dir.isDirectory) continue
                val files = dir.listFiles() ?: continue
                val fileNames = files.map { it.name }.toSet()
                if (NPU_VENDOR_LIBS.any { it in fileNames }) {
                    FileLogger.i("LiteRTEngine", "检测到 NPU 库目录: $dirPath")
                    return dir.absolutePath
                }
            }
            return context.applicationInfo.nativeLibraryDir
        }

        /** 验证文件是否为有效的 .litertlm 格式（检查魔数） */
        fun isValidLitertlmFile(file: File): Boolean = try {
            file.inputStream().use { stream ->
                val magic = ByteArray(8)
                if (stream.read(magic) != 8) false else String(magic) == "LITERTLM"
            }
        } catch (_: Exception) { false }

        /** 设备是否支持 NPU 加速 */
        fun hasNpuSupport(context: Context): Boolean {
            return detectNpuLibraryDir(context) != context.applicationInfo.nativeLibraryDir ||
                context.applicationInfo.nativeLibraryDir.isNotBlank()
        }
    }
}
