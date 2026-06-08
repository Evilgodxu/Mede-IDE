package com.template.jh.core.ai

import android.net.Uri
import java.util.UUID

// 附件文件（用户添加到对话中的文件引用，仅含路径信息，内容由模型通过 readFile 获取）
data class AttachedFile(
    val name: String,
    val path: String,
)

// 聊天消息
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: ChatRole,
    val content: String,
    val isStreaming: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
    val isToolMessage: Boolean = false, // 是否为工具调用中间消息（JSON+执行结果）
    val toolCallId: String? = null,     // 工具调用 ID，用于 API role:tool 匹配
)

enum class ChatRole { User, Model, System, Tool }

// 模型当前活动状态
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

// 对话历史条目
data class ConversationEntry(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val messages: List<ChatMessage> = emptyList(),
    val timestamp: Long = System.currentTimeMillis(),
)

// 聊天 UI 状态
data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val inputText: String = "",
    val engineStatus: EngineStatus = EngineStatus.Idle,
    val engineErrorMessage: String = "",
    val modelName: String = "",
    val isModelPickerOpen: Boolean = false,
    val availableModels: List<ModelInfo> = emptyList(),
    val isLoading: Boolean = false,
    // 模型活动状态
    val modelActivity: ModelActivity = ModelActivity.Idle,
    val activityDetail: String = "", // 如文件路径、搜索关键词等详情
    // 下载状态
    val downloadStatus: DownloadStatus = DownloadStatus.Idle,
    val downloadProgress: Float = 0f,
    val downloadFileName: String = "",
    val downloadErrorMessage: String = "",
    // 模型参数
    val modelParams: ModelParams = ModelParams(),
    // 对话历史
    val conversations: List<ConversationEntry> = emptyList(),
    val activeConversationId: String? = null,
    val isHistoryOpen: Boolean = false,
    val isOptimizing: Boolean = false,
    // 云端模型
    val cloudModelEnabled: Boolean = false,
    val cloudModelProfiles: List<CloudModelProfile> = emptyList(),
    val activeCloudProfileId: String = "",
    // 项目根目录名称（SAF 文件夹名）
    val projectRootName: String = "",
    // 当前打开的文件路径列表
    val openedFilePaths: List<String> = emptyList(),
    // 自动上下文
    val activeFilePath: String = "",
    val cursorLine: Int = 0,
    // 已修改（未保存）文件路径列表
    val modifiedFilePaths: List<String> = emptyList(),
    // 附加到对话中的文件（含预读内容，发送时一并注入）
    val attachedFileRefs: List<AttachedFile> = emptyList(),
    // 上下文窗口大小（从模型配置获取，默认 128K）
    val contextMaxTokens: Int = 128000,
    // 上下文压缩状态
    val isContextCompressed: Boolean = false,
    val contextCompressedTokens: Int = 0,  // 本次对话累计压缩的 token 数
    val contextCompressedCount: Int = 0,   // 压缩次数
    // 附着的图片 URI 列表
    val attachedImageUris: List<Uri> = emptyList(),
)
