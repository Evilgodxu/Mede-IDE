package com.template.jh.core.ai

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Contents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ChatViewModel(
    application: Application,
    private val conversationRepo: ConversationRepository,
) : AndroidViewModel(application) {

    private val liteRTManager = LiteRTManager(application)

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state

    private var sendJob: Job? = null

    init {
        scanModels()
        // 加载持久化对话历史
        viewModelScope.launch {
            val saved = conversationRepo.load()
            _state.update { it.copy(conversations = saved) }
        }
        viewModelScope.launch {
            liteRTManager.state.collect { engineState ->
                _state.update {
                    it.copy(
                        engineStatus = engineState.status,
                        engineErrorMessage = engineState.errorMessage,
                        modelName = engineState.modelName,
                    )
                }
            }
        }
        viewModelScope.launch {
            liteRTManager.downloadState.collect { ds ->
                _state.update {
                    it.copy(
                        downloadStatus = ds.status,
                        downloadProgress = ds.progress,
                        downloadFileName = ds.fileName,
                        downloadErrorMessage = ds.errorMessage,
                    )
                }
            }
        }
    }

    // 输入文本更新
    fun setInputText(text: String) {
        _state.update { it.copy(inputText = text) }
    }

    // 从文件路径加载模型
    fun loadModel(modelPath: String) {
        viewModelScope.launch {
            liteRTManager.loadModel(modelPath)
        }
    }

    // 从 SAF URI 加载模型（文件选择器）
    fun loadModelFromUri(uri: Uri) {
        closeModelPicker()
        viewModelScope.launch {
            liteRTManager.loadModelFromUri(uri)
        }
    }

    // 扫描可用模型
    fun scanModels() {
        viewModelScope.launch(Dispatchers.IO) {
            val models = liteRTManager.scanModels()
            _state.update { it.copy(availableModels = models) }
        }
    }

    // 下载模型
    fun downloadModel(url: String, fileName: String) {
        viewModelScope.launch {
            liteRTManager.downloadModel(url, fileName)
        }
    }

    fun resetDownload() {
        liteRTManager.resetDownloadState()
    }

    // 更新模型参数
    fun setModelParams(params: ModelParams) {
        liteRTManager.modelParams = params
        _state.update { it.copy(modelParams = params) }
    }

    // 控制模型选择器
    fun toggleModelPicker() {
        _state.update { it.copy(isModelPickerOpen = !it.isModelPickerOpen) }
    }

    fun closeModelPicker() {
        _state.update { it.copy(isModelPickerOpen = false) }
    }

    // 发送消息
    fun sendMessage() {
        val text = _state.value.inputText.trim()
        if (text.isEmpty()) return
        if (!liteRTManager.isInitialized) {
            _state.update { it.copy(engineErrorMessage = "请先加载模型") }
            return
        }

        val userMsg = ChatMessage(role = ChatRole.User, content = text)
        _state.update {
            it.copy(
                messages = it.messages + userMsg,
                inputText = "",
                isLoading = true,
            )
        }

        // 添加占位模型消息
        val modelMsgId = java.util.UUID.randomUUID().toString()
        val placeholderMsg = ChatMessage(
            id = modelMsgId,
            role = ChatRole.Model,
            content = "",
            isStreaming = true,
        )
        _state.update { it.copy(messages = it.messages + placeholderMsg) }

        sendJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val conversation = liteRTManager.createConversation(
                    ConversationConfig(
                        systemInstruction = Contents.of("You are a helpful AI assistant."),
                        samplerConfig = liteRTManager.modelParams.toSamplerConfig(),
                    )
                )

                conversation.use { conv ->
                    conv.sendMessageAsync(text)
                        .catch { e ->
                            updateModelMessage(modelMsgId, "\n\n[错误: ${e.message}]", false)
                        }
                        .collect { message ->
                            val chunk = message.toString()
                            updateModelMessage(modelMsgId, chunk, true)
                        }
                }
                // 流结束，标记完成
                finalizeModelMessage(modelMsgId)
            } catch (e: Exception) {
                updateModelMessage(modelMsgId, "\n\n[错误: ${e.message}]", false)
                finalizeModelMessage(modelMsgId)
            }
        }
    }

    // 取消当前生成
    fun cancelGeneration() {
        sendJob?.cancel()
        _state.value.messages.lastOrNull()?.let { msg ->
            if (msg.isStreaming) {
                finalizeModelMessage(msg.id)
            }
        }
    }

    // 清空对话
    fun clearMessages() {
        sendJob?.cancel()
        _state.update { it.copy(messages = emptyList(), inputText = "") }
    }

    // 创建新对话（保存当前对话到历史）
    fun newConversation() {
        sendJob?.cancel()
        _state.update { state ->
            val updatedConversations = if (state.messages.isNotEmpty()) {
                val title = state.messages.firstOrNull { it.role == ChatRole.User }?.content?.take(30) ?: "新对话"
                val entry = ConversationEntry(
                    id = state.activeConversationId ?: java.util.UUID.randomUUID().toString(),
                    title = title,
                    messages = state.messages,
                )
                val exists = state.conversations.any { it.id == entry.id }
                if (exists) state.conversations.map { if (it.id == entry.id) entry else it }
                else listOf(entry) + state.conversations
            } else state.conversations
            persistConversations(updatedConversations)
            state.copy(messages = emptyList(), inputText = "", isLoading = false, conversations = updatedConversations, activeConversationId = null)
        }
    }

    // 切换到历史对话
    fun switchConversation(entry: ConversationEntry) {
        sendJob?.cancel()
        _state.update {
            it.copy(
                messages = entry.messages,
                inputText = "",
                isLoading = false,
                activeConversationId = entry.id,
                isHistoryOpen = false,
            )
        }
    }

    // 删除历史对话
    fun deleteConversation(entryId: String) {
        _state.update { state ->
            val updated = state.conversations.filter { it.id != entryId }
            persistConversations(updated)
            if (state.activeConversationId == entryId) {
                state.copy(conversations = updated, messages = emptyList(), activeConversationId = null)
            } else {
                state.copy(conversations = updated)
            }
        }
    }

    // 历史下拉
    fun toggleHistory() {
        _state.update { it.copy(isHistoryOpen = !it.isHistoryOpen) }
    }

    fun closeHistory() {
        _state.update { it.copy(isHistoryOpen = false) }
    }

    private fun updateModelMessage(msgId: String, chunk: String, append: Boolean) {
        _state.update { state ->
            state.copy(
                messages = state.messages.map { msg ->
                    if (msg.id == msgId) {
                        msg.copy(
                            content = if (append) msg.content + chunk else chunk,
                            isStreaming = true,
                        )
                    } else msg
                }
            )
        }
    }

    private fun finalizeModelMessage(msgId: String) {
        _state.update { state ->
            state.copy(
                messages = state.messages.map { msg ->
                    if (msg.id == msgId) msg.copy(isStreaming = false) else msg
                },
                isLoading = false,
            )
        }
        // 自动保存当前对话到历史
        saveCurrentToHistory()
    }

    // 将当前对话存入历史并持久化
    private fun saveCurrentToHistory() {
        val state = _state.value
        if (state.messages.isEmpty()) return
        val title = state.messages.firstOrNull { it.role == ChatRole.User }?.content?.take(30) ?: "新对话"
        val entry = ConversationEntry(
            id = state.activeConversationId ?: java.util.UUID.randomUUID().toString(),
            title = title,
            messages = state.messages,
        )
        val updated = if (state.conversations.any { it.id == entry.id }) {
            state.conversations.map { if (it.id == entry.id) entry else it }
        } else {
            listOf(entry) + state.conversations
        }
        _state.update { it.copy(conversations = updated, activeConversationId = entry.id) }
        persistConversations(updated)
    }

    private fun persistConversations(conversations: List<ConversationEntry>) {
        viewModelScope.launch(Dispatchers.IO) {
            conversationRepo.save(conversations)
        }
    }

    override fun onCleared() {
        super.onCleared()
        sendJob?.cancel()
        saveCurrentToHistory()
        liteRTManager.close()
    }
}
