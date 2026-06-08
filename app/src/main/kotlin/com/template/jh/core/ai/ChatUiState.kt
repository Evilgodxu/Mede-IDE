package com.template.jh.core.ai

import android.net.Uri
import com.template.jh.data.model.TaskItem
import java.util.UUID

// 文件操作类型
enum class FileOpType { Create, Modify, Delete, Overwrite }

// 文件操作元数据（嵌入 ChatMessage 用于渲染操作卡片）
data class FileOperationMeta(
    val filePath: String,
    val opType: FileOpType,
    val lineChanges: Int = 0, // +N (新增) / -N (删除)
)

// 文件变更记录（对话中累计，供任务清单文件列表展示）
data class FileChangeItem(
    val filePath: String,
    val opType: FileOpType,
    val lineChanges: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
)

// 聊天消息
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: ChatRole,
    val content: String,
    val isStreaming: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
    val fileOp: FileOperationMeta? = null,
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
    val taskList: List<TaskItem> = emptyList(),
    val fileChanges: List<FileChangeItem> = emptyList(),
    val isTaskListOpen: Boolean = false,
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
    // 通知设置
    val notificationSettings: com.template.jh.data.model.NotificationSettings = com.template.jh.data.model.NotificationSettings(),
    // 附着的图片 URI 列表
    val attachedImageUris: List<Uri> = emptyList(),
)
