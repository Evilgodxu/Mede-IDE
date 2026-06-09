package com.template.jh.screens.home

import android.net.Uri
import com.template.jh.model.chat.AttachedFile
import com.template.jh.model.chat.ChatMessage
import com.template.jh.model.chat.CloudModelProfile
import com.template.jh.model.chat.ConversationEntry
import com.template.jh.model.chat.DownloadStatus
import com.template.jh.model.chat.EngineStatus
import com.template.jh.model.chat.ModelActivity
import com.template.jh.model.chat.ModelInfo
import com.template.jh.model.chat.ModelParams

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
    val cursorLineContent: String = "", // 光标所在行的文本内容
    val terminalActive: Boolean = false, // 终端是否有进程在运行
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
