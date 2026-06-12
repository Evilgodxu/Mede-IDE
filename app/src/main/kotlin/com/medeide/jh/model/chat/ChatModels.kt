package com.medeide.jh.model.chat

import android.net.Uri
import java.util.UUID

// 附件文件（用户添加到对话中的文件引用，仅含路径信息，内容由模型通过 readFile 获取）
data class AttachedFile(
    val name: String,
    val path: String,
)

/** 工具调用信息（对应 OpenAI function calling 中 assistant 消息的 tool_calls 条目） */
data class ToolCallInfo(
    val id: String,           // 工具调用唯一 ID，如 "call_abc123"
    val name: String,         // 工具函数名，如 "readFile"
    val arguments: String,    // 参数字符串，如 "{\"path\":\"src/Main.kt\"}"
)

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: ChatRole,
    val content: String,
    val isStreaming: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
    val toolCallId: String? = null,     // 工具调用 ID，用于 API role:tool 匹配
    val toolName: String? = null,       // 工具函数名（如 "readFile"），用于 Content.ToolResponse.name
    val imageUris: List<Uri> = emptyList(), // 附加图片 URI（用于聊天消息中显示缩略图）
    val channelContent: String? = null, // LiteRT-LM channels 中的思考内容
    val toolCalls: List<ToolCallInfo> = emptyList(), // 工具调用列表（assistant 消息），云端路径按 OpenAI 规范还原
)

enum class ChatRole { User, Model, System, Tool }

data class ConversationEntry(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val messages: List<ChatMessage> = emptyList(),
    val timestamp: Long = System.currentTimeMillis(),
)

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
    val contextWindow: Int = 0,  // 当前加载模型的上下文窗口（按模型名称推断），0=未知
)

// 下载状态
enum class DownloadStatus {
    Idle, Downloading, Paused, Completed, Error
}

data class DownloadState(
    val status: DownloadStatus = DownloadStatus.Idle,
    val fileName: String = "",
    val progress: Float = 0f,
    val errorMessage: String = "",
)

// 模型推理参数（对应 LiteRT-LM SamplerConfig + EngineConfig）
data class ModelParams(
    val topK: Int = 10,
    val topP: Double = 0.7,
    val temperature: Double = 0.1,
    val seed: Int = 0,
    val contextWindowTokens: Int = 4096,        // 默认 4K
    val enableSpeculativeDecoding: Boolean = false, // MTP 推测解码
    val backendType: BackendType = BackendType.GPU, // 推理后端
) {
    init {
        require(topK > 0) { "topK must be positive, got $topK" }
        require(topP in 0.0..1.0) { "topP must be 0~1, got $topP" }
        require(temperature >= 0) { "temperature must be >= 0, got $temperature" }
        require(contextWindowTokens in 512..262144) { "contextWindowTokens must be 512~262144, got $contextWindowTokens" }
    }
}

// LiteRT-LM 后端类型（CPU/GPU/NPU）
enum class BackendType(val displayName: String) {
    CPU("CPU"),
    GPU("GPU"),
    NPU("NPU");
    companion object {
        fun fromName(name: String): BackendType =
            entries.find { it.name.equals(name, ignoreCase = true) } ?: CPU
    }
}

// 本地模型格式
enum class ModelFormat {
    LiteRTLM,   // .litertlm 格式，使用 Google AI Edge LiteRT-LM 推理
    Unknown;

    companion object {
        fun fromFileName(name: String): ModelFormat = when {
            name.endsWith(".litertlm", ignoreCase = true) -> LiteRTLM
            else -> Unknown
        }
        fun fromPath(path: String): ModelFormat = fromFileName(path.substringAfterLast('/'))
    }
}

data class ModelInfo(val path: String, val name: String, val size: Long) {
    val sizeText: String get() = when {
        size < 1024 -> "$size B"
        size < 1024 * 1024 -> "${size / 1024} KB"
        size < 1024 * 1024 * 1024 -> "${"%.1f".format(size / (1024.0 * 1024.0))} MB"
        else -> "${"%.2f".format(size / (1024.0 * 1024.0 * 1024.0))} GB"
    }
    /** 模型格式，根据文件扩展名推断 */
    val format: ModelFormat get() = ModelFormat.fromPath(path)
}

data class RecommendedModel(val name: String, val size: String, val url: String, val description: String, val fileName: String)

// 云端大模型配置（单个配置）
data class CloudModelConfig(
    val enabled: Boolean = false,
    val apiEndpoint: String = "https://api.openai.com/v1",
    val apiKey: String = "",
    val modelName: String = "gpt-4o",
    val maxTokens: Int = 8192,
)

// 云端模型配置档案（可保存多个，自由切换）
data class CloudModelProfile(
    val id: String = "",
    val name: String = "",            // 用户自定义名称，如"DeepSeek V4"
    val apiEndpoint: String = "https://api.openai.com/v1",
    val apiKey: String = "",
    val modelName: String = "gpt-4o",
    val contextWindow: Int = 128000,  // 模型上下文窗口大小（token），用于自适应压缩阈值
    val maxTokens: Int = 8192,        // 模型输出最大 token 数
)

/** API 调用返回的用量信息 */
data class ApiUsage(
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
)

/** 原生工具调用（OpenAI function calling 格式） */
data class CloudToolCall(
    val id: String,
    val functionName: String,
    val arguments: String,  // JSON string of params
)

// 显示模型
enum class DisplayRole { User, Model }

data class DisplayItem(
    val id: String,
    val role: DisplayRole,
    val content: String,              // 显示文本（已过滤工具调用 JSON/标记）
    val channelContent: String? = null, // LiteRT-LM channels 思考内容（官方 API）
    val isStreaming: Boolean,
    val timestamp: Long,
    val imageUris: List<Uri> = emptyList(), // 图片 URI 列表（仅 User 消息）
)
