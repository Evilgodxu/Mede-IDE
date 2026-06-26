package com.medeide.jh.screens.home.localchat

import com.medeide.jh.model.chat.ChatMessage
import com.medeide.jh.model.chat.DownloadState
import com.medeide.jh.model.chat.EngineStatus
import com.medeide.jh.model.chat.ModelParams

data class LocalChatUiState(
    // 对话
    val messages: List<ChatMessage> = emptyList(),
    val inputText: String = "",
    val isLoading: Boolean = false,
    val isSending: Boolean = false,

    // 引擎状态
    val engineStatus: EngineStatus = EngineStatus.Idle,
    val engineErrorMessage: String = "",

    // 模型管理
    val loadedModelName: String = "",
    val loadedModelPath: String = "",
    val isModelLoaded: Boolean = false,
    val modelParams: ModelParams = ModelParams(),
    val detectedModels: List<LocalModelInfo> = emptyList(),

    // 下载
    val downloadStates: Map<String, DownloadState> = emptyMap(),
    // 用户/Agent 名称
    val userName: String = "",
    val agentName: String = "AI",
)

data class LocalModelInfo(
    val fileName: String,
    val displayName: String,
    val sizeBytes: Long = 0,
    val path: String = "",
)
