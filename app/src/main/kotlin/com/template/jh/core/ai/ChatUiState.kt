package com.template.jh.core.ai

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

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val inputText: String = "",
    val engineStatus: EngineStatus = EngineStatus.Idle,
    val engineErrorMessage: String = "",
    val modelName: String = "",
    val isModelPickerOpen: Boolean = false,
    val availableModels: List<ModelInfo> = emptyList(),
    val isLoading: Boolean = false,
    val modelActivity: ModelActivity = ModelActivity.Idle,
    val activityDetail: String = "", // 如文件路径、搜索关键词等详情
    val downloadStatus: DownloadStatus = DownloadStatus.Idle,
    val downloadProgress: Float = 0f,
    val downloadFileName: String = "",
    val downloadErrorMessage: String = "",
    val modelParams: ModelParams = ModelParams(),
    val conversations: List<ConversationEntry> = emptyList(),
    val activeConversationId: String? = null,
    val isHistoryOpen: Boolean = false,
    val isOptimizing: Boolean = false,
    val cloudModelEnabled: Boolean = false,
    val cloudModelProfiles: List<CloudModelProfile> = emptyList(),
    val activeCloudProfileId: String = "",
    val projectRootName: String = "",
    val openedFilePaths: List<String> = emptyList(),
    val activeFilePath: String = "",
    val cursorLine: Int = 0,
    val modifiedFilePaths: List<String> = emptyList(),
    // 附加到对话中的文件（含预读内容，发送时一并注入）
    val attachedFileRefs: List<AttachedFile> = emptyList(),
    // 上下文窗口大小（从模型配置获取，默认 128K）
    val contextMaxTokens: Int = 128000,
    val isContextCompressed: Boolean = false,
    val contextCompressedTokens: Int = 0,  // 本次对话累计压缩的 token 数
    val contextCompressedCount: Int = 0,   // 压缩次数
    val contextSummary: String = "",       // 云端 LLM 生成的上下文结构化摘要 JSON
    val attachedImageUris: List<Uri> = emptyList(),
)
