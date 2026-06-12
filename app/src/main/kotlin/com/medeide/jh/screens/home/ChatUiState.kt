package com.medeide.jh.screens.home

import android.net.Uri
import com.medeide.jh.model.chat.AttachedFile
import com.medeide.jh.model.chat.ChatMessage
import com.medeide.jh.model.chat.CloudModelProfile
import com.medeide.jh.model.chat.ConversationEntry
import com.medeide.jh.model.chat.DownloadStatus
import com.medeide.jh.model.chat.EngineStatus
import com.medeide.jh.model.chat.ModelInfo
import com.medeide.jh.model.chat.ModelParams
import com.medeide.jh.model.chat.BackendType

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val inputText: String = "",
    val engineStatus: EngineStatus = EngineStatus.Idle,
    val engineErrorMessage: String = "",
    val modelName: String = "",
    val isModelPickerOpen: Boolean = false,
    val availableModels: List<ModelInfo> = emptyList(),
    val isLoading: Boolean = false,
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
    // 记忆系统状态
    val memoryEntryCount: Int = 0,         // 记忆条目数
    val memoryTotalTokens: Int = 0,        // 记忆系统 token 估算（不占当前窗口）
    val attachedImageUris: List<Uri> = emptyList(),
    // 本地推理后端配置
    val backendType: BackendType = BackendType.GPU,
    val npuLibraryDir: String = "",
    // MTP (Multi-Turn Prediction / Speculative Decoding)
    val enableSpeculativeDecoding: Boolean = false,
    // 当前加载的模型是否支持多模态
    val isMultimodal: Boolean = false,
)
