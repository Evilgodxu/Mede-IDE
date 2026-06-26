package com.medeide.jh.model.chat

import android.net.Uri
import java.util.UUID

enum class ChatRole { User, Model, System, Tool }

data class ToolCallInfo(
    val id: String,
    val name: String,
    val arguments: String,
)

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: ChatRole,
    val content: String,
    /** 推理过程内容（reasoning_content） */
    val reasoningContent: String = "",
    val isStreaming: Boolean = false,
    /** 是否处于思维链展开状态 */
    val isThinking: Boolean = false,
    val isError: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
    val toolCallId: String? = null,
    val toolName: String? = null,
    val toolCalls: List<ToolCallInfo> = emptyList(),
    val channelContent: String? = null,
    val imageUris: List<Uri> = emptyList(),
)

data class ConversationEntry(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val messages: List<ChatMessage> = emptyList(),
    val timestamp: Long = System.currentTimeMillis(),
)

data class CloudModelProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val apiEndpoint: String = "https://api.openai.com/v1",
    val apiKey: String = "",
    val modelName: String = "gpt-4o",
    val contextWindow: Int = 128000,
    val maxTokens: Int = 16000,
    val maxToolRounds: Int = 200,
)

data class ApiUsage(
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
)

data class CloudToolCall(
    val id: String,
    val functionName: String,
    val arguments: String,
)

enum class EngineStatus { Idle, Loading, Ready, Error }

enum class DownloadStatus { Idle, Downloading, Paused, Completed, Error }

data class DownloadState(
    val status: DownloadStatus = DownloadStatus.Idle,
    val fileName: String = "",
    val progress: Float = 0f,
    val errorMessage: String = "",
)

data class ModelParams(
    val topK: Int = 10,
    val topP: Double = 0.7,
    val temperature: Double = 0.1,
    val seed: Int = 0,
    val contextWindowTokens: Int = 4096,
    val enableSpeculativeDecoding: Boolean = false,
    val backendType: BackendType = BackendType.GPU,
)

enum class BackendType(val displayName: String) {
    CPU("CPU"), GPU("GPU"), NPU("NPU");
    companion object {
        fun fromName(name: String): BackendType =
            entries.find { it.name.equals(name, ignoreCase = true) } ?: CPU
    }
}

enum class DisplayRole { User, Model }

data class DisplayItem(
    val id: String,
    val role: DisplayRole,
    val content: String,
    val isStreaming: Boolean,
    val isThinking: Boolean = false,
    val reasoningContent: String = "",
    val fileOperations: List<FileOperation> = emptyList(),
    val timestamp: Long = System.currentTimeMillis(),
    val channelContent: String? = null,
    val imageUris: List<Uri> = emptyList(),
)

/** 文件操作类型 */
enum class FileOpType { WriteFile, CreateDirectory, DeleteFile, MoveFile, CopyFile }

/** 文件操作状态 */
enum class FileOpStatus { InProgress, Success, Error }

/** 文件操作记录（用于UI显示"正在写入…"卡片） */
data class FileOperation(
    val id: String = UUID.randomUUID().toString(),
    val type: FileOpType,
    val filePath: String,
    val status: FileOpStatus = FileOpStatus.InProgress,
    val errorMessage: String? = null,
    val parentMessageId: String = "",
)

/** 用户/Agent 资料（名称 + 头像） */
data class UserProfile(
    val userName: String = "",
    val userAvatarUri: String = "",
    val agentName: String = "AI",
    val agentAvatarUri: String = "",
)

data class AttachedFile(
    val name: String,
    val path: String,
)

data class ModelInfo(
    val path: String,
    val name: String,
    val size: Long = 0,
    val sizeText: String = "",
)

data class CloudModelConfig(
    val enabled: Boolean = false,
    val apiEndpoint: String = "",
    val apiKey: String = "",
    val modelName: String = "",
    val maxTokens: Int = 16000,
    val contextWindow: Int = 128000,
)

data class EngineState(
    val status: EngineStatus = EngineStatus.Idle,
    val modelPath: String = "",
    val modelName: String = "",
    val progress: Float = 0f,
    val errorMessage: String = "",
    val contextWindow: Int = 4096,
)
