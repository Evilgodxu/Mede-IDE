package com.medeide.jh.screens.home.cloudchat

import android.net.Uri
import com.medeide.jh.model.chat.AiBehaviorSettings
import com.medeide.jh.model.chat.ChatMessage
import com.medeide.jh.model.chat.ChatMode
import com.medeide.jh.model.chat.CloudModelProfile
import com.medeide.jh.model.chat.ConversationEntry
import com.medeide.jh.model.chat.DisplayItem
import com.medeide.jh.model.chat.EngineStatus
import com.medeide.jh.model.chat.FileOperation
import com.medeide.jh.screens.home.cloudchat.aitools.OptimizeMode

data class CloudChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val displayItems: List<DisplayItem> = emptyList(),
    val inputText: String = "",
    val engineStatus: EngineStatus = EngineStatus.Idle,
    val engineErrorMessage: String = "",
    val isLoading: Boolean = false,
    val isSending: Boolean = false,
    val isModelPickerOpen: Boolean = false,
    val cloudModelEnabled: Boolean = false,
    val cloudModelProfiles: List<CloudModelProfile> = emptyList(),
    val activeCloudProfileId: String = "",
    val conversations: List<ConversationEntry> = emptyList(),
    val activeConversationId: String? = null,
    val isHistoryOpen: Boolean = false,
    val attachedFilePaths: List<String> = emptyList(),
    val attachedImageUris: List<Uri> = emptyList(),
    val chatMode: ChatMode = ChatMode.Agent,
    val aiBehavior: AiBehaviorSettings = AiBehaviorSettings(),
    val editorContext: String = "",
    // 工作目录（空=只读模式，只有进入工作模式后才能读写）
    val projectRoot: String = "",
    val isOptimizing: Boolean = false,
    val optimizeMode: OptimizeMode = OptimizeMode.CODE,
    // 模型生成过程中的文件操作记录，供 UI 显示操作状态卡片
    val fileOperations: List<FileOperation> = emptyList(),
    // 用户/Agent 名称
    val userName: String = "",
    val agentName: String = "AI",
) {
    val activeProfile: CloudModelProfile?
        get() = cloudModelProfiles.find { it.id == activeCloudProfileId }

    val activeProfileName: String
        get() = activeProfile?.let { p ->
            p.name.ifEmpty { p.modelName }
        } ?: "未配置"
}
