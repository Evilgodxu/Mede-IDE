package com.medeide.jh.screens.home.localchat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medeide.jh.core.data.logging.FileLogger
import com.medeide.jh.core.data.repository.UserPreferencesRepository
import com.medeide.jh.core.data.source.local.LiteRTEngineManager
import com.medeide.jh.core.data.source.local.LiteRTModelRepository
import com.medeide.jh.model.chat.ChatMessage
import com.medeide.jh.model.chat.ChatRole
import com.medeide.jh.model.chat.EngineStatus
import com.medeide.jh.model.chat.ModelParams
import com.medeide.jh.model.chat.UserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

class LocalChatViewModel(
    private val engineManager: LiteRTEngineManager,
    private val modelRepository: LiteRTModelRepository,
    private val preferencesRepo: UserPreferencesRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(LocalChatUiState())
    val state: StateFlow<LocalChatUiState> = _state.asStateFlow()

    private var sendJob: Job? = null

    init {
        refreshDetectedModels()
        // 启动时恢复持久化的模型参数
        viewModelScope.launch {
            try {
                val saved = preferencesRepo.modelParams.first()
                _state.update { it.copy(modelParams = saved) }
                engineManager.updateParams(saved)
                FileLogger.i("LocalChatVM", "已恢复模型参数: ctx=${saved.contextWindowTokens} backend=${saved.backendType}")
            } catch (_: Exception) { }
        }
        // 加载用户/Agent 名称
        viewModelScope.launch {
            preferencesRepo.userProfile.collect { p ->
                _state.update { it.copy(userName = p.userName, agentName = p.agentName.ifEmpty { "AI" }) }
                updateNameInstruction(p.userName, p.agentName.ifEmpty { "AI" })
            }
        }
    }

    fun setInputText(text: String) {
        _state.update { it.copy(inputText = text) }
    }

    fun setModelParams(params: ModelParams) {
        _state.update { it.copy(modelParams = params) }
        engineManager.updateParams(params)
        // 持久化到 DataStore，重启不丢失
        viewModelScope.launch {
            preferencesRepo.setModelParams(params)
        }
    }

    // ════════════════════════════════════════════════
    //  模型管理
    // ════════════════════════════════════════════════

    /** 刷新已检测到的本地模型列表 */
    fun refreshDetectedModels() {
        val files = modelRepository.scanDownloadedModels()
        val models = files.map { file ->
            val displayName = file.nameWithoutExtension
                .replace("-", " ")
                .replace("_", " ")
            LocalModelInfo(
                fileName = file.name,
                displayName = displayName,
                sizeBytes = file.length(),
                path = file.absolutePath,
            )
        }
        _state.update { it.copy(detectedModels = models) }
    }

    /** 加载模型 */
    fun loadModel(path: String) {
        val fileName = File(path).name
        val params = _state.value.modelParams

        _state.update { it.copy(engineStatus = EngineStatus.Loading, engineErrorMessage = "") }

        viewModelScope.launch(Dispatchers.IO) {
            val result = engineManager.loadModel(path, params)
            result.onSuccess {
                _state.update {
                    it.copy(
                        engineStatus = EngineStatus.Ready,
                        isModelLoaded = true,
                        loadedModelName = fileName,
                        loadedModelPath = path,
                    )
                }
                FileLogger.i("LocalChatVM", "模型加载成功: $fileName")
            }.onFailure { e ->
                _state.update {
                    it.copy(
                        engineStatus = EngineStatus.Error,
                        engineErrorMessage = "模型加载失败: ${e.message}",
                        isModelLoaded = false,
                        loadedModelName = "",
                        loadedModelPath = "",
                    )
                }
                FileLogger.e("LocalChatVM", "模型加载失败: $fileName", e)
            }
        }
    }

    /** 卸载模型 */
    fun unloadModel() {
        viewModelScope.launch {
            engineManager.unloadModel()
            _state.update {
                it.copy(
                    engineStatus = EngineStatus.Idle,
                    isModelLoaded = false,
                    loadedModelName = "",
                    loadedModelPath = "",
                )
            }
        }
    }

    // ════════════════════════════════════════════════
    //  下载
    // ════════════════════════════════════════════════

    /** 下载模型，监听进度 */
    fun downloadModel(url: String, fileName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            modelRepository.downloadModel(url, fileName)
                .onSuccess { path ->
                    refreshDetectedModels()
                    FileLogger.i("LocalChatVM", "下载完成: $fileName -> $path")
                }
                .onFailure { e ->
                    FileLogger.e("LocalChatVM", "下载失败: $fileName", e)
                }
        }
    }

    fun cancelDownload(fileName: String) {
        modelRepository.cancelDownload(fileName)
    }

    fun pauseDownload(fileName: String) {
        modelRepository.pauseDownload(fileName)
    }

    fun deleteModel(fileName: String) {
        viewModelScope.launch {
            modelRepository.deleteModel(fileName)
            refreshDetectedModels()
        }
    }

    // ════════════════════════════════════════════════
    //  名称指令
    // ════════════════════════════════════════════════

    private fun updateNameInstruction(userName: String, agentName: String) {
        val un = userName.ifEmpty { "用户" }
        engineManager.systemInstructionForConversation = "你的名字是「${agentName}」，你是一个 AI 编程助手，帮助用户解决编程问题。用户的名字是「${un}」。在回复时请使用你的名字「${agentName}」自称，使用「${un}」称呼用户。"
    }

    // ════════════════════════════════════════════════
    //  对话
    // ════════════════════════════════════════════════

    fun sendMessage() {
        val text = _state.value.inputText.trim()
        if (text.isEmpty() || !_state.value.isModelLoaded) return

        val userMsg = ChatMessage(role = ChatRole.User, content = text)
        _state.update {
            it.copy(
                inputText = "",
                isLoading = true,
                isSending = true,
                messages = it.messages + userMsg,
            )
        }

        sendJob = viewModelScope.launch {
            val sid = UUID.randomUUID().toString()
            val modelMsg = ChatMessage(id = sid, role = ChatRole.Model, content = "", isStreaming = true)
            _state.update { it.copy(messages = it.messages + modelMsg) }

            val sb = StringBuilder()

            engineManager.sendMessageAsync(
                text = text,
                callback = object : com.google.ai.edge.litertlm.MessageCallback {
                    override fun onMessage(message: com.google.ai.edge.litertlm.Message) {
                        val chunk = message.toString()
                        sb.append(chunk)
                        _state.update { state ->
                            val up = state.messages.map {
                                if (it.id == sid) it.copy(content = sb.toString(), isStreaming = true) else it
                            }
                            state.copy(messages = up)
                        }
                    }

                    override fun onDone() {
                        _state.update { state ->
                            val up = state.messages.map {
                                if (it.id == sid) it.copy(content = sb.toString(), isStreaming = false) else it
                            }
                            state.copy(isLoading = false, isSending = false)
                        }
                    }

                    override fun onError(throwable: Throwable) {
                        _state.update { state ->
                            val up = state.messages.map {
                                if (it.id == sid) it.copy(
                                    content = sb.toString().ifEmpty { "错误: ${throwable.message}" },
                                    isStreaming = false, isError = true,
                                ) else it
                            }
                            state.copy(
                                messages = up,
                                isLoading = false,
                                isSending = false,
                                engineStatus = EngineStatus.Error,
                                engineErrorMessage = throwable.message ?: "未知错误",
                            )
                        }
                    }
                },
            )
        }
    }

    fun cancelSend() {
        engineManager.cancelProcess()
        sendJob?.cancel()
        sendJob = null
        _state.update { it.copy(isLoading = false, isSending = false) }
    }

    fun newConversation() {
        cancelSend()
        engineManager.createConversation(engineManager.systemInstructionForConversation.ifEmpty { null })
        _state.update { it.copy(messages = emptyList(), inputText = "") }
    }

    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch {
            engineManager.unloadModel()
        }
    }
}
