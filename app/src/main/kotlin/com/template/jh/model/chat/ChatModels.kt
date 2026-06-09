package com.template.jh.model.chat

import android.net.Uri
import java.util.UUID

// 附件文件（用户添加到对话中的文件引用，仅含路径信息，内容由模型通过 readFile 获取）
data class AttachedFile(
    val name: String,
    val path: String,
)

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: ChatRole,
    val content: String,
    val isStreaming: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
    val isToolMessage: Boolean = false, // 是否为工具调用中间消息（JSON+执行结果）
    val toolCallId: String? = null,     // 工具调用 ID，用于 API role:tool 匹配
    val imageUris: List<Uri> = emptyList(), // 附加图片 URI（用于聊天消息中显示缩略图）
)

enum class ChatRole { User, Model, System, Tool }

enum class ModelActivity {
    Idle,
    Thinking,
    ListingFiles,
    ReadingFile,
    WritingFile,
    EditingFile,
    DeletingFile,
    CreatingDirectory,
    SearchingCode,
    SearchingWeb,
    RunningCommand,
    GitOperation,
    ReadingLints,
    ExecutingTool,
    ProcessingResult;

    fun displayLabel(): String = when (this) {
        Idle -> ""
        Thinking -> "思考中…"
        ListingFiles -> "正在列出目录"
        ReadingFile -> "正在读取文件"
        WritingFile -> "正在写入文件"
        EditingFile -> "正在修改文件"
        DeletingFile -> "正在删除文件"
        CreatingDirectory -> "正在创建目录"
        SearchingCode -> "正在搜索代码"
        SearchingWeb -> "正在搜索网络"
        RunningCommand -> "正在执行命令"
        GitOperation -> "正在执行 Git 操作"
        ReadingLints -> "正在检查编译错误"
        ExecutingTool -> "正在执行操作"
        ProcessingResult -> "正在处理结果"
    }
}

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

// 模型推理参数
data class ModelParams(
    val topK: Int = 20,
    val topP: Double = 0.9,
    val temperature: Double = 0.2,
    val seed: Int = 0,
    val maxOutputTokens: Int = 1048576,
) {
    init {
        require(topK > 0) { "topK must be positive, got $topK" }
        require(topP in 0.0..1.0) { "topP must be 0~1, got $topP" }
        require(temperature >= 0) { "temperature must be >= 0, got $temperature" }
        require(maxOutputTokens in 256..1048576) { "maxOutputTokens must be 256~1048576, got $maxOutputTokens" }
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

data class ModelInfo(val path: String, val name: String, val size: Long) {
    val sizeText: String get() = when {
        size < 1024 -> "$size B"
        size < 1024 * 1024 -> "${size / 1024} KB"
        size < 1024 * 1024 * 1024 -> "${"%.1f".format(size / (1024.0 * 1024.0))} MB"
        else -> "${"%.2f".format(size / (1024.0 * 1024.0 * 1024.0))} GB"
    }
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
enum class DisplayRole { User, Model, ToolActivity }

data class DisplayItem(
    val id: String,
    val role: DisplayRole,
    val content: String,           // 显示文本（已过滤工具调用 JSON/标记）
    val thinkBlocks: List<String>, // 提取的 [think] 块内容
    val isStreaming: Boolean,
    val timestamp: Long,
    val imageUris: List<Uri> = emptyList(), // 图片 URI 列表（仅 User 消息）
)
