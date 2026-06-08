package com.template.jh.core.ai

import android.net.Uri
import java.util.UUID

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
    ProcessingResult,
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
    // 任务清单
    val taskList: List<com.template.jh.data.model.TaskItem> = emptyList(),
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
    // 文件引用映射：短名称 → 完整路径（用户输入 @filename 时自动解析）
    val fileRefMap: Map<String, String> = emptyMap(),
    // 通知设置
    val notificationSettings: com.template.jh.data.model.NotificationSettings = com.template.jh.data.model.NotificationSettings(),
    // 附着的图片 URI 列表
    val attachedImageUris: List<Uri> = emptyList(),
)
