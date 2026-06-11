package com.template.jh.screens.home

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.ToolCall
import com.google.ai.edge.litertlm.tool
import com.template.jh.core.ai.AIToolSet
import com.template.jh.core.ai.FileOperationEvents
import com.template.jh.core.ai.ToolCallHandler
import com.template.jh.core.ai.ToolExecutionCallback
import com.template.jh.core.analytics.LlmCallRecord
import com.template.jh.core.analytics.UsageStats
import com.template.jh.core.config.ChatConfig
import com.template.jh.core.memory.ChatMessageAdapter
import com.template.jh.core.memory.ContextManager
import com.template.jh.core.memory.ConversationMemory
import com.template.jh.core.storage.FileManager
import com.template.jh.core.utils.FileLogger
import com.template.jh.core.utils.ImageProcessor
import com.template.jh.data.repository.ConversationRepository
import com.template.jh.data.repository.UsageAnalyticsRepository
import com.template.jh.data.repository.UserPreferencesRepository
import com.template.jh.data.source.local.LiteRTManager
import com.template.jh.data.source.local.toSamplerConfig
import com.template.jh.data.source.remote.CloudLLMClient
import com.template.jh.model.Rule
import com.template.jh.model.SkillItem
import com.template.jh.model.chat.AttachedFile
import com.template.jh.model.chat.ChatMessage
import com.template.jh.model.chat.ChatRole
import com.template.jh.model.chat.CloudModelConfig
import com.template.jh.model.chat.CloudModelProfile
import com.template.jh.model.chat.ConversationEntry
import com.template.jh.model.chat.DisplayItem
import com.template.jh.model.chat.DisplayRole
import com.template.jh.model.chat.EngineStatus
import com.template.jh.model.chat.ModelActivity
import com.template.jh.model.chat.ModelParams
import com.template.jh.model.chat.BackendType
import com.template.jh.screens.home.components.chat.toDisplayItems
import com.template.jh.screens.home.logic.utils.FileTypeUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ChatViewModel(
    application: Application,
    private val conversationRepo: ConversationRepository,
    private val preferencesRepo: UserPreferencesRepository,
    private val fileManager: FileManager,
    private val usageAnalyticsRepo: UsageAnalyticsRepository,
    private val liteRTManager: LiteRTManager,
    private val conversationMemory: ConversationMemory,
    private val aiToolSet: AIToolSet,
    private val cloudLLMClient: CloudLLMClient,
    private val toolCallHandler: ToolCallHandler,
    private val contextManager: ContextManager,
    private val imageProcessor: ImageProcessor,
    private val inputOptimizer: com.template.jh.core.ai.InputOptimizer,
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state

    data class StreamingState(val msgId: String, val content: String)
    private val _streamingContent = MutableStateFlow<StreamingState?>(null)
    val streamingContent: StateFlow<StreamingState?> = _streamingContent.asStateFlow()

    val displayItems: StateFlow<List<DisplayItem>> = combine(
        _state, _streamingContent
    ) { s, stream ->
        val items = toDisplayItems(s.messages)
        if (stream == null) return@combine items
        val lastModelIdx = items.indexOfLast { it.role == DisplayRole.Model }
        if (lastModelIdx < 0) return@combine items
        items.toMutableList().also { list ->
            val old = list[lastModelIdx]
            list[lastModelIdx] = old.copy(content = stream.content, isStreaming = true)
        }
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currentToolActivity: StateFlow<DisplayItem?> = _state.map { s ->
        if (s.isLoading && s.modelActivity != ModelActivity.Idle) {
            val label = s.modelActivity.displayLabel()
            val detail = if (s.activityDetail.isNotBlank()) ": ${s.activityDetail}" else ""
            DisplayItem(
                id = "tool_activity",
                role = DisplayRole.ToolActivity,
                content = "$label$detail",
                isStreaming = true,
                timestamp = System.currentTimeMillis(),
            )
        } else null
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val usageStats: StateFlow<UsageStats> = usageAnalyticsRepo.stats.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), UsageStats()
    )

    val contextTokenCount: StateFlow<Int> = _state.map { s ->
        val sysTokens = contextManager.estimateSystemPromptTokens(contextManager.getSysPromptCache())
        var ascii = 0; var other = 0
        for (msg in s.messages) {
            for (c in msg.content) {
                if (c.code <= 127) ascii++ else other++
            }
        }
        ascii / 4 + other / 2 + sysTokens
    }.flowOn(Dispatchers.Default)
        .distinctUntilChanged().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private var sendJob: Job? = null
    @Volatile private var activeConversation: Conversation? = null
    private var pendingInitialMessages: List<Message>? = null

    // System prompt 依赖值缓存
    @Volatile private var sysPromptUserName: String = ""
    @Volatile private var sysPromptRules: List<Rule> = emptyList()
    @Volatile private var sysPromptSkills: List<SkillItem> = emptyList()
    @Volatile private var sysPromptDeepThink: Boolean = false
    @Volatile private var sysPromptVersion: Int = 0
    @Volatile private var convSysPromptVersion: Int = -1

    // ========== 封装方法 ==========

    private fun buildSystemInstruction(): String = contextManager.buildSystemInstruction(
        sysPromptCache = contextManager.getSysPromptCache(),
        userName = sysPromptUserName,
        rules = sysPromptRules,
        skills = sysPromptSkills,
        deepThink = sysPromptDeepThink,
        cloudModelEnabled = _state.value.cloudModelEnabled,
        aiToolSet = aiToolSet,
    )

    private fun buildEditorContext(): String = contextManager.buildEditorContext(
        activeFilePath = _state.value.activeFilePath,
        projectRootName = _state.value.projectRootName,
        openedFilePaths = _state.value.openedFilePaths,
        fileManager = fileManager,
        aiToolSet = aiToolSet,
    )

    private fun getContextWindow(): Int = contextManager.getContextWindow(
        cloud = _state.value.cloudModelEnabled,
        cloudProfiles = _state.value.cloudModelProfiles,
        activeProfileId = _state.value.activeCloudProfileId,
        localContextTokens = _state.value.contextMaxTokens,
    )

    private fun getCompressThreshold(): Int = contextManager.getCompressThreshold(
        contextWindow = getContextWindow(),
        sysPromptCache = contextManager.getSysPromptCache(),
    )

    private fun refreshMemoryState() {
        val stats = contextManager.getMemoryStats()
        _state.update {
            it.copy(
                memoryKeyFactCount = stats.keyFactCount,
                memorySummaryCount = 0,
                memoryEntryCount = stats.entryCount,
                memoryTotalTokens = stats.estimatedTokens,
            )
        }
    }

    /** 构建上下文仪表板数据（在 Composable 中调用） */
    fun buildDashboardData(): DashboardData {
        val s = _state.value
        val snapshot = com.template.jh.core.memory.VisualizerEngine.buildSnapshot(
            messages = s.messages,
            maxTokens = s.contextMaxTokens,
            isCompressed = s.isContextCompressed,
            compressedTokens = s.contextCompressedTokens,
            compressedCount = s.contextCompressedCount,
        )
        val breakdown = com.template.jh.core.memory.VisualizerEngine.buildTokenBreakdown(
            messages = s.messages,
            sysPromptTokens = contextManager.estimateSystemPromptTokens(contextManager.getSysPromptCache()),
        )
        val architecture = com.template.jh.core.memory.VisualizerEngine.buildMemoryArchitecture(conversationMemory)
        val keyFactCategories = com.template.jh.core.memory.VisualizerEngine.buildKeyFactCategories(conversationMemory)
        val compressionHistory = com.template.jh.core.memory.VisualizerEngine.buildCompressionHistory(s.messages)
        return DashboardData(snapshot, breakdown, architecture, keyFactCategories, compressionHistory)
    }

    data class DashboardData(
        val snapshot: com.template.jh.core.memory.ContextSnapshot,
        val breakdown: com.template.jh.core.memory.TokenBreakdown,
        val architecture: com.template.jh.core.memory.MemoryArchitecture,
        val keyFactCategories: com.template.jh.core.memory.KeyFactCategories,
        val compressionHistory: List<com.template.jh.core.memory.CompressionRecord>,
    )

    private suspend fun applyCompressResult(result: ContextManager.CompressResult) {
        if (result.removedTokens > 0) {
            _state.update {
                it.copy(
                    contextCompressedTokens = it.contextCompressedTokens + result.removedTokens,
                    contextCompressedCount = it.contextCompressedCount + 1,
                    isContextCompressed = true,
                    contextSummary = result.summaryContent.ifBlank { it.contextSummary },
                )
            }
            refreshMemoryState()
        }
    }

    private suspend fun compressMessages(
        messages: List<ChatMessage>,
        threshold: Int = getCompressThreshold(),
    ): List<ChatMessage> {
        val result = contextManager.compressMessages(
            messages = messages,
            activeConversationId = _state.value.activeConversationId,
            threshold = threshold,
        )
        applyCompressResult(result)
        return result.messages
    }

    // ========== init ==========

    init {
        scanModels()
        viewModelScope.launch {
            combine(
                preferencesRepo.deepThinkEnabled,
                preferencesRepo.userName,
                preferencesRepo.rules,
                preferencesRepo.skills,
                _state.map { it.cloudModelEnabled }.distinctUntilChanged(),
            ) { think, name, rules, skills, _ ->
                sysPromptDeepThink = think
                sysPromptUserName = name
                sysPromptRules = rules
                sysPromptSkills = skills
                contextManager.invalidateSysPromptCache()
                sysPromptVersion++
            }.collect {}
        }
        viewModelScope.launch {
            try {
                val savedParams = preferencesRepo.modelParams.first()
                if (savedParams != ModelParams()) {
                    liteRTManager.modelParams = savedParams
                    liteRTManager.enableSpeculativeDecoding = savedParams.enableSpeculativeDecoding
                    liteRTManager.backendType = savedParams.backendType
                    _state.update {
                        it.copy(
                            modelParams = savedParams,
                            backendType = savedParams.backendType,
                            enableSpeculativeDecoding = savedParams.enableSpeculativeDecoding,
                            contextMaxTokens = if (!it.cloudModelEnabled) savedParams.contextWindowTokens else it.contextMaxTokens,
                        )
                    }
                }
            } catch (_: Exception) {}
        }
        viewModelScope.launch {
            val saved = conversationRepo.load()
            _state.update { it.copy(conversations = saved) }
            conversationMemory.load()
            refreshMemoryState()
            try {
                val autoLoad = preferencesRepo.autoLoadLastModel.first()
                val lastPath = preferencesRepo.lastModelPath.first()
                val backend = preferencesRepo.backendType.first()
                val npuDir = preferencesRepo.npuLibraryDir.first()
                if (autoLoad && !lastPath.isNullOrEmpty()) {
                    val file = java.io.File(lastPath)
                    if (file.exists()) {
                        liteRTManager.backendType = backend
                        liteRTManager.npuLibraryDir = npuDir
                        liteRTManager.loadModel(lastPath)
                        preferencesRepo.setCloudModelEnabled(false)
                        _state.update { it.copy(cloudModelEnabled = false, backendType = backend, npuLibraryDir = npuDir) }
                    }
                }
            } catch (_: Exception) {}
            launch {
                liteRTManager.state.collect { engineState ->
                    _state.update {
                        it.copy(
                            engineStatus = engineState.status,
                            engineErrorMessage = engineState.errorMessage,
                            modelName = engineState.modelName,
                            contextMaxTokens = if (!it.cloudModelEnabled && engineState.status == EngineStatus.Ready && engineState.contextWindow > 0)
                                engineState.contextWindow else it.contextMaxTokens,
                            isMultimodal = liteRTManager.isMultimodal,
                        )
                    }
                    if (engineState.status == EngineStatus.Ready && engineState.modelPath.isNotEmpty()) {
                        preferencesRepo.setLastModelPath(engineState.modelPath)
                    }
                }
            }
            launch {
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
        viewModelScope.launch {
            combine(
                preferencesRepo.backendType,
                preferencesRepo.npuLibraryDir,
                preferencesRepo.enableSpeculativeDecoding,
            ) { type, npuDir, mtp ->
                liteRTManager.backendType = type
                liteRTManager.npuLibraryDir = npuDir
                liteRTManager.enableSpeculativeDecoding = mtp
                _state.update { it.copy(backendType = type, npuLibraryDir = npuDir, enableSpeculativeDecoding = mtp) }
            }.collect {}
        }
        viewModelScope.launch {
            combine(
                preferencesRepo.cloudModelEnabled,
                preferencesRepo.cloudModelProfiles,
                preferencesRepo.activeCloudProfileId,
            ) { enabled, profiles, activeId ->
                _state.update { it.copy(cloudModelEnabled = enabled, cloudModelProfiles = profiles, activeCloudProfileId = activeId) }
                updateContextMaxTokens()
            }.collect {}
        }
    }

    // ========== UI State Accessors ==========

    fun setInputText(text: String) { _state.update { it.copy(inputText = text) } }

    fun attachImage(uri: Uri) {
        val current = _state.value.attachedImageUris
        if (current.size >= ChatConfig.MAX_ATTACHED_IMAGES) return
        if (uri !in current) { _state.update { it.copy(attachedImageUris = current + uri) } }
    }

    fun detachImage(uri: Uri) { _state.update { it.copy(attachedImageUris = it.attachedImageUris - uri) } }
    fun clearAttachedImages() { _state.update { it.copy(attachedImageUris = emptyList()) } }

    fun loadModel(modelPath: String) {
        viewModelScope.launch {
            sendJob?.cancel()
            val backend = _state.value.backendType
            val npuDir = _state.value.npuLibraryDir
            liteRTManager.backendType = backend
            liteRTManager.npuLibraryDir = npuDir
            closeConversation()
            liteRTManager.loadModel(modelPath)
            preferencesRepo.setCloudModelEnabled(false)
            _state.update { it.copy(cloudModelEnabled = false) }
        }
    }

    fun loadModelFromUri(uri: Uri) {
        closeModelPicker()
        viewModelScope.launch {
            sendJob?.cancel()
            closeConversation()
            liteRTManager.loadModelFromUri(uri)
            preferencesRepo.setCloudModelEnabled(false)
            _state.update { it.copy(cloudModelEnabled = false) }
        }
    }

    fun scanModels() {
        viewModelScope.launch(Dispatchers.IO) {
            val models = liteRTManager.scanModels()
            _state.update { it.copy(availableModels = models) }
        }
    }

    fun downloadModel(url: String, fileName: String) {
        viewModelScope.launch { liteRTManager.downloadModel(url, fileName) }
    }

    fun cancelDownload() { liteRTManager.cancelDownload() }
    fun pauseDownload() { liteRTManager.pauseDownload() }
    fun resumeDownload() { liteRTManager.resumeDownload() }
    fun resetDownload() { liteRTManager.resetDownloadState() }

    fun setModelParams(params: ModelParams) {
        liteRTManager.modelParams = params
        liteRTManager.enableSpeculativeDecoding = params.enableSpeculativeDecoding
        liteRTManager.backendType = params.backendType
        _state.update {
            it.copy(
                modelParams = params,
                backendType = params.backendType,
                enableSpeculativeDecoding = params.enableSpeculativeDecoding,
                contextMaxTokens = if (!it.cloudModelEnabled) params.contextWindowTokens else it.contextMaxTokens,
            )
        }
        viewModelScope.launch { preferencesRepo.setModelParams(params) }
        val modelPath = liteRTManager.currentModelPath ?: return
        loadModel(modelPath)
    }

    fun setBackendType(type: BackendType) {
        viewModelScope.launch {
            preferencesRepo.setBackendType(type)
            if (type == BackendType.NPU) {
                val ctx = getApplication<Application>()
                val detected = LiteRTManager.detectNpuLibraryDir(ctx)
                preferencesRepo.setNpuLibraryDir(detected)
                liteRTManager.npuLibraryDir = detected
                _state.update { it.copy(npuLibraryDir = detected) }
            }
        }
    }

    fun setNpuLibraryDir(dir: String) { viewModelScope.launch { preferencesRepo.setNpuLibraryDir(dir) } }
    fun setEnableSpeculativeDecoding(enabled: Boolean) { viewModelScope.launch { preferencesRepo.setEnableSpeculativeDecoding(enabled) } }
    fun toggleModelPicker() { _state.update { it.copy(isModelPickerOpen = !it.isModelPickerOpen) } }
    fun closeModelPicker() { _state.update { it.copy(isModelPickerOpen = false) } }

    fun setProjectRoot(uri: Uri?) {
        aiToolSet.projectUri = uri
        uri?.let { fileManager.setProjectUri(it) } ?: fileManager.clearProjectUri()
        val name = uri?.let { extractFolderName(it) } ?: ""
        _state.update { it.copy(projectRootName = name) }
    }

    fun setProjectRootPath(absolutePath: String, displayName: String) {
        _state.update { it.copy(projectRootName = displayName) }
    }

    private fun extractFolderName(uri: Uri): String = try {
        val docId = android.provider.DocumentsContract.getTreeDocumentId(uri)
        val decoded = java.net.URLDecoder.decode(docId, "UTF-8")
        decoded.substringAfter(':').substringAfterLast('/')
    } catch (_: Exception) { "" }

    fun setOpenedFilePaths(paths: List<String>) { _state.update { it.copy(openedFilePaths = paths) } }
    fun setActiveFileContext(path: String, cursorLine: Int, cursorLineContent: String = "") {
        _state.update { it.copy(activeFilePath = path, cursorLine = cursorLine, cursorLineContent = cursorLineContent) }
    }
    fun setTerminalActive(active: Boolean) { _state.update { it.copy(terminalActive = active) } }
    fun setModifiedFilePaths(paths: List<String>) { _state.update { it.copy(modifiedFilePaths = paths) } }

    fun attachFile(path: String, name: String) {
        val refs = _state.value.attachedFileRefs
        if (refs.any { it.path == path }) return
        val displayName = if (refs.any { it.name == name }) "${name} (${path.substringBeforeLast('/')})" else name
        _state.update { it.copy(attachedFileRefs = refs + AttachedFile(name = displayName, path = path)) }
    }

    fun detachFile(index: Int) {
        _state.update { it.copy(attachedFileRefs = it.attachedFileRefs.toMutableList().also { list -> if (index in list.indices) list.removeAt(index) }) }
    }

    fun resetUsageStats() { viewModelScope.launch { usageAnalyticsRepo.resetStats() } }

    private suspend fun recordUsage(call: LlmCallRecord) { usageAnalyticsRepo.recordCall(call) }

    // ========== 文件打开请求 ==========

    private val _openFileRequests = MutableSharedFlow<String>(extraBufferCapacity = 5)
    val openFileRequests: SharedFlow<String> = _openFileRequests

    fun requestOpenFile(path: String) { _openFileRequests.tryEmit(path) }

    // ========== sendMessage ==========

    fun sendMessage() {
        val text = _state.value.inputText.trim()
        val images = _state.value.attachedImageUris
        val files = _state.value.attachedFileRefs
        if (text.isEmpty() && images.isEmpty() && files.isEmpty()) return
        val isCloud = _state.value.cloudModelEnabled
        if (!isCloud) {
            if (!liteRTManager.isInitialized) {
                _state.update { it.copy(engineErrorMessage = "请先加载模型或启用云端模型") }
                return
            }
        }
        val fileBlock = contextManager.buildFileAttachmentBlock(files)
        val userContent = buildString {
            append(text)
            if (fileBlock.isNotBlank()) { appendLine(); append(fileBlock) }
            if (images.isNotEmpty()) { appendLine(); append("[已附加 ${images.size} 张图片]") }
        }
        val userMsg = ChatMessage(role = ChatRole.User, content = userContent, imageUris = images)
        val modelMsgId = java.util.UUID.randomUUID().toString()
        val placeholderMsg = ChatMessage(id = modelMsgId, role = ChatRole.Model, content = "", isStreaming = true)
        _state.update { it.copy(messages = it.messages + userMsg + placeholderMsg, inputText = "", attachedImageUris = emptyList(), attachedFileRefs = emptyList(), isLoading = true) }
        val ctx = getApplication<Application>()
        sendJob?.cancel()
        sendJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val tempImagePaths = mutableListOf<String>()
                for (uri in images) {
                    try {
                        val tempFile = imageProcessor.uriToTempFile(uri)
                        if (tempFile != null) tempImagePaths.add(tempFile.absolutePath)
                    } catch (_: Exception) {}
                }
                if (images.isEmpty()) {
                    val activePath = _state.value.activeFilePath
                    if (activePath.isNotBlank()) {
                        val fileName = activePath.substringAfterLast('/')
                        if (FileTypeUtil.isImageFile(fileName)) {
                            try {
                                val uri = android.net.Uri.fromFile(java.io.File(activePath))
                                val tempFile = imageProcessor.uriToTempFile(uri)
                                if (tempFile != null) tempImagePaths.add(tempFile.absolutePath)
                            } catch (_: Exception) {}
                        }
                    }
                }
                val isCloud = _state.value.cloudModelEnabled
                if (isCloud) {
                    processWithCloudTools(text, modelMsgId, ctx, tempImagePaths)
                } else {
                    ensureConversation(autoToolCalling = false)
                    processLocalMessage(text, modelMsgId, tempImagePaths)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                _state.update { it.copy(modelActivity = ModelActivity.Idle, activityDetail = "") }
                throw e
            } catch (e: Exception) {
                Log.e("ChatViewModel", "sendMessage failed", e)
                FileLogger.e("ChatViewModel", "sendMessage failed: ${e.message}", e)
                updateModelMessage(modelMsgId, "\n\n[错误: ${e.message}]", false)
                finalizeModelMessage(modelMsgId)
            }
        }
    }

    fun cancelGeneration() {
        sendJob?.cancel()
        // 不销毁会话，匹配官方示例永不关闭模式
        _state.value.messages.lastOrNull()?.let { msg -> if (msg.isStreaming) finalizeModelMessage(msg.id) }
        _state.update { it.copy(modelActivity = ModelActivity.Idle, activityDetail = "") }
    }

    // ========== 输入优化（独立于推理工作流） ==========

    private var _optimizeMode = com.template.jh.core.ai.InputOptimizer.Mode.CODE
    val optimizeMode: com.template.jh.core.ai.InputOptimizer.Mode get() = _optimizeMode

    fun setOptimizeMode(mode: com.template.jh.core.ai.InputOptimizer.Mode) {
        _optimizeMode = mode
    }

    fun optimizeInput() {
        val text = _state.value.inputText.trim()
        if (text.isEmpty()) return
        if (_state.value.isOptimizing) return
        val mode = _optimizeMode
        _state.update { it.copy(isOptimizing = true) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = if (_state.value.cloudModelEnabled) {
                    val profile = _state.value.cloudModelProfiles.find { it.id == _state.value.activeCloudProfileId }
                    if (profile != null) {
                        val cfg = com.template.jh.model.chat.CloudModelConfig(
                            enabled = true, apiEndpoint = profile.apiEndpoint,
                            apiKey = profile.apiKey, modelName = profile.modelName,
                            maxTokens = profile.maxTokens,
                        )
                        inputOptimizer.optimizeWithCloud(text, mode, cfg)
                    } else text
                } else {
                    inputOptimizer.optimizeWithLocal(text, mode)
                }
                if (result.isNotEmpty() && result != text) {
                    _state.update { it.copy(inputText = result) }
                }
            } catch (e: Exception) {
                Log.e("ChatViewModel", "optimizeInput error", e)
                FileLogger.e("ChatViewModel", "optimizeInput error: ${e.message}", e)
            } finally {
                _state.update { it.copy(isOptimizing = false) }
            }
        }
    }

    // ========== Conversation 管理 ==========

    private fun updateContextMaxTokens() {
        val s = _state.value
        if (!s.cloudModelEnabled) {
            if (s.contextMaxTokens <= 0 || s.contextMaxTokens == ChatConfig.DEFAULT_CONTEXT_WINDOW) {
                _state.update { it.copy(contextMaxTokens = ChatConfig.DEFAULT_LOCAL_CONTEXT_WINDOW) }
            }
            return
        }
        val window = s.cloudModelProfiles.find { it.id == s.activeCloudProfileId }?.contextWindow
            ?: ChatConfig.DEFAULT_CONTEXT_WINDOW
        _state.update { it.copy(contextMaxTokens = window) }
    }

    private fun ensureConversation(autoToolCalling: Boolean = false): Conversation {
        val initialMsgs = pendingInitialMessages
        pendingInitialMessages = null
        // 每轮重建，匹配 Gallery 官方应用模式
        activeConversation?.let { try { it.close() } catch (_: Exception) {} }
        activeConversation = null
        convSysPromptVersion = sysPromptVersion
        val conv = liteRTManager.createConversation(
            ConversationConfig(
                systemInstruction = Contents.of(buildSystemInstruction()),
                initialMessages = initialMsgs ?: emptyList(),
                samplerConfig = liteRTManager.modelParams.toSamplerConfig(),
                tools = listOf(tool(aiToolSet)),
                automaticToolCalling = autoToolCalling,
                channels = listOf(com.google.ai.edge.litertlm.Channel("thinking", "<think>", "</think>")),
            )
        )
        activeConversation = conv
        return conv
    }

    // ========== 本地模型处理（含工具调用） ==========

    private suspend fun processLocalMessage(
        text: String, msgId: String,
        imagePaths: List<String> = emptyList(),
    ) {
        val conv = activeConversation
        if (conv == null) {
            Log.e("ChatViewModel", "processLocalMessage: activeConversation is null")
            updateModelMessage(msgId, "\n\n[错误: 模型会话未就绪]", false)
            finalizeModelMessage(msgId)
            _state.update { it.copy(modelActivity = ModelActivity.Idle, activityDetail = "") }
            return
        }
        // 上下文注入消息渠道（不走系统指令）
        val convId = _state.value.activeConversationId ?: ""
        val memoryCtx = contextManager.getMemoryContext(convId)
        val keyFactsCtx = contextManager.getKeyFactsContext()
        val editorCtx = buildEditorContext()
        val messageBuilder = StringBuilder()
        if (memoryCtx.isNotBlank()) messageBuilder.appendLine(memoryCtx).appendLine()
        if (keyFactsCtx.isNotBlank()) messageBuilder.appendLine(keyFactsCtx).appendLine()
        if (editorCtx.isNotBlank()) messageBuilder.appendLine(editorCtx).appendLine()
        if (sysPromptRules.isNotEmpty()) {
            messageBuilder.appendLine("【系统规则】")
            sysPromptRules.forEach { r -> messageBuilder.appendLine("- ${r.name}: ${r.content}") }
            messageBuilder.appendLine()
        }
        val enabledSkills = sysPromptSkills.filter { it.enabled }
        if (enabledSkills.isNotEmpty()) {
            messageBuilder.appendLine("【已启用技能】")
            enabledSkills.forEach { s ->
                messageBuilder.appendLine("${s.name}: ${s.description}")
                if (s.prompt.isNotBlank()) messageBuilder.appendLine(s.prompt.take(500))
            }
            messageBuilder.appendLine()
        }
        messageBuilder.append(text)
        val userText = messageBuilder.toString().trim()

        val hasImages = imagePaths.isNotEmpty()
        val thinkCtx = mapOf("enable_thinking" to sysPromptDeepThink)
        var currentMessage: Message = if (hasImages) {
            val contents = mutableListOf<Content>(Content.Text(userText))
            imagePaths.forEach { contents.add(Content.ImageFile(absolutePath = it)) }
            Message.user(Contents.of(contents))
        } else {
            Message.user(userText)
        }
        var currentMsgId = msgId
        var channelContent: String? = null

        try {
            for (round in 0..<ChatConfig.MAX_TOOL_RETRY_ROUNDS) {
                val roundText = StringBuilder()
                val roundToolCalls = mutableListOf<ToolCall>()

                conv.sendMessageAsync(currentMessage, extraContext = thinkCtx).collect { msg ->
                    val c = msg.contents.toString()
                    if (c.isNotEmpty()) {
                        roundText.append(c)
                        updateModelMessage(currentMsgId, c, true)
                    }
                    if (msg.toolCalls.isNotEmpty()) {
                        roundToolCalls.addAll(msg.toolCalls)
                    }
                    if (msg.channels.isNotEmpty()) {
                        channelContent = (channelContent ?: "") + msg.channels.values.joinToString("\n")
                    }
                }

                if (roundToolCalls.isEmpty()) {
                    // 无工具调用 → 最终回复
                    break
                }

                // 执行工具调用
                val toolResults = mutableListOf<Content.ToolResponse>()
                val display = StringBuilder()

                for (tc in roundToolCalls) {
                    val argsStr = tc.arguments.mapValues { it.value?.toString() ?: "" }
                    val name = tc.name
                    val result = withContext(Dispatchers.IO) {
                        toolCallHandler.executeAiTool(name, argsStr)
                    }
                    display.appendLine("[工具调用: $name]")
                    display.appendLine(result)
                    toolResults.add(Content.ToolResponse(name, result))
                }

                // 显示工具结果
                updateModelMessage(currentMsgId, "\n\n${display.toString().trimEnd()}", false)
                finalizeModelMessage(currentMsgId)

                // 发回工具结果，进入下一轮
                currentMessage = Message.tool(Contents.of(toolResults))
                currentMsgId = java.util.UUID.randomUUID().toString()
                _state.update { it.copy(messages = it.messages + ChatMessage(id = currentMsgId, role = ChatRole.Model, content = "", isStreaming = true)) }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            FileLogger.e("ChatViewModel", "processLocalMessage failed: ${e.message}", e)
            updateModelMessage(currentMsgId, "\n\n[错误: ${e.message}]", false)
        } finally {
            finalizeModelMessage(currentMsgId, channelContent = channelContent)
            try { conv.close() } catch (_: Exception) {}
            if (activeConversation === conv) { activeConversation = null }
        }
    }

    // ========== 云端模型处理 ==========

    private suspend fun processWithCloudTools(
        text: String, msgId: String,
        ctx: Application,
        imagePaths: List<String> = emptyList(),
    ) {
        val profile = _state.value.cloudModelProfiles.find { it.id == _state.value.activeCloudProfileId }
        if (profile == null) {
            updateModelMessage(msgId, "\n\n[错误: 未选择云端模型配置]", false)
            finalizeModelMessage(msgId)
            return
        }
        if (profile.apiKey.isBlank()) {
            updateModelMessage(msgId, "\n\n[错误: 未配置 API Key]", false)
            finalizeModelMessage(msgId)
            return
        }
        val cfg = CloudModelConfig(
            enabled = true,
            apiEndpoint = profile.apiEndpoint,
            apiKey = profile.apiKey,
            modelName = profile.modelName,
            maxTokens = profile.maxTokens,
        )

        var currentMsgId = msgId
        var cloudRounds = 0
        _state.update { it.copy(modelActivity = ModelActivity.Thinking, activityDetail = "") }

        val historyMessages = mutableListOf<ChatMessage>()
        val lastUserIdx = _state.value.messages.indexOfLast { it.role == ChatRole.User && it.id != currentMsgId }
        for ((idx, msg) in _state.value.messages.withIndex()) {
            if (msg.id == currentMsgId) continue
            if (msg.role == ChatRole.User || msg.role == ChatRole.Model || msg.role == ChatRole.Tool) {
                if (msg.role == ChatRole.User && idx == lastUserIdx) continue
                if (msg.content.isNotBlank() || msg.role == ChatRole.Model) historyMessages.add(msg)
            }
        }
        historyMessages.clear()
        historyMessages.addAll(compressMessages(historyMessages))
        val editorCtx = buildEditorContext()
        if (editorCtx.isNotBlank()) {
            historyMessages.add(ChatMessage(role = ChatRole.System, content = editorCtx))
        }
        historyMessages.add(ChatMessage(role = ChatRole.User, content = text))

        val existingSummary = _state.value.contextSummary
        if (existingSummary.isNotBlank() && historyMessages.none { it.content.startsWith("[上下文摘要]") }) {
            historyMessages.add(0, ChatMessage(
                role = ChatRole.Model,
                content = "[上下文摘要]\n$existingSummary",
            ))
        }

        while (true) {
            cloudRounds++
            val ctxThreshold = getCompressThreshold()
            if (contextManager.estimateContextTokens(historyMessages) > ctxThreshold) {
                historyMessages.clear()
                historyMessages.addAll(compressMessages(historyMessages))
                val summary = _state.value.contextSummary
                if (summary.isNotBlank() && historyMessages.none { it.content.startsWith("[上下文摘要]") }) {
                    historyMessages.add(0, ChatMessage(
                        role = ChatRole.Model,
                        content = "[上下文摘要]\n$summary",
                    ))
                }
            }
            val fullResponse = StringBuilder()
            val roundStartTime = System.currentTimeMillis()
            var apiUsage = com.template.jh.model.chat.ApiUsage()
            val toolsJson = AIToolSet.buildOpenAIToolsJson()
            val nativeToolCalls = mutableListOf<com.template.jh.model.chat.CloudToolCall>()
            try {
                val (resp, usage, tcs) = cloudLLMClient.sendMessage(
                    config = cfg,
                    systemPrompt = buildSystemInstruction(),
                    messages = historyMessages,
                    onChunk = { chunk ->
                        fullResponse.append(chunk)
                        updateModelMessage(currentMsgId, chunk, true)
                    },
                    toolsJson = toolsJson,
                    imagePaths = imagePaths,
                )
                apiUsage = usage
                nativeToolCalls.addAll(tcs)
            } catch (e: Exception) {
                val duration = System.currentTimeMillis() - roundStartTime
                val errMsg = e.message ?: "Unknown error"
                val isContextError = errMsg.contains("context_length", ignoreCase = true) ||
                    errMsg.contains("maximum context", ignoreCase = true) ||
                    errMsg.contains("token limit", ignoreCase = true) ||
                    errMsg.contains("too many tokens", ignoreCase = true) ||
                    errMsg.contains("maximum prompt length", ignoreCase = true)
                if (isContextError && historyMessages.size > 3) {
                    Log.w("ChatViewModel", "context length exceeded, force compressing history")
                    FileLogger.w("ChatViewModel", "context length exceeded, force compressing history")
                    val forcedThreshold = (getContextWindow() * 0.5).toInt()
                    historyMessages.clear()
                    historyMessages.addAll(compressMessages(historyMessages, forcedThreshold))
                    val summary = _state.value.contextSummary
                    if (summary.isNotBlank() && historyMessages.none { it.content.startsWith("[上下文摘要]") }) {
                        historyMessages.add(0, ChatMessage(
                            role = ChatRole.Model,
                            content = "[上下文摘要]\n$summary",
                        ))
                    }
                    cloudRounds--
                    continue
                }
                Log.e("ChatViewModel", "cloudSendMessage failed", e)
                FileLogger.e("ChatViewModel", "cloudSendMessage failed: ${errMsg}", e)
                recordUsage(LlmCallRecord(
                    modelName = cfg.modelName,
                    provider = "cloud",
                    promptTokens = 0, completionTokens = 0,
                    durationMs = duration, success = false,
                    errorMessage = errMsg,
                ))
                updateModelMessage(currentMsgId, "\n\n[云端模型错误: ${errMsg}]", false)
                finalizeModelMessage(currentMsgId)
                _state.update { it.copy(modelActivity = ModelActivity.Idle, activityDetail = "") }
                return
            }

            val duration = System.currentTimeMillis() - roundStartTime
            recordUsage(LlmCallRecord(
                modelName = cfg.modelName, provider = "cloud",
                promptTokens = apiUsage.promptTokens,
                completionTokens = apiUsage.completionTokens,
                durationMs = duration, success = true, errorMessage = null,
            ))

            val response = fullResponse.toString().trim()
            val toolCalls = if (nativeToolCalls.isNotEmpty()) {
                nativeToolCalls.map { tc ->
                    val argsMap = try {
                        val obj = org.json.JSONObject(tc.arguments)
                        obj.keys().asSequence().associate { key -> key to obj.optString(key, "") }
                    } catch (_: Exception) { emptyMap() }
                    Triple(tc.id, tc.functionName, argsMap)
                }
            } else {
                toolCallHandler.extractJsonToolCalls(response)
            }

            if (toolCalls == null || (toolCalls.isEmpty() && response.isBlank())) {
                historyMessages.add(ChatMessage(role = ChatRole.Model, content = response))
                finalizeModelMessage(currentMsgId)
                _state.update { it.copy(modelActivity = ModelActivity.Idle, activityDetail = "") }
                return
            }

            val firstTool = toolCalls.firstOrNull()
            if (firstTool != null) {
                val act = toolCallHandler.toolNameToActivity(firstTool.second)
                val detail = firstTool.third["path"] ?: firstTool.third["command"] ?: firstTool.third["query"] ?: ""
                _state.update { it.copy(modelActivity = act, activityDetail = detail) }
            }

            val results = coroutineScope {
                toolCalls.map { call ->
                    val funcName = call.second
                    val funcArgs = call.third
                    async(Dispatchers.IO) {
                        if (funcName !in ChatConfig.KNOWN_TOOLS) return@async "未知工具: $funcName"
                        try { toolCallHandler.executeAiTool(funcName, funcArgs) }
                        catch (e: Exception) { "$funcName 执行失败: ${e.message}" }
                    }
                }.map { it.await() }
            }

            val toolCallId = "call_${java.util.UUID.randomUUID().toString().take(8)}"
            historyMessages.add(ChatMessage(
                role = ChatRole.Model, content = response,
                toolCallId = toolCallId,
            ))

            var combinedResult = results.joinToString("\n\n")
            val lintBlock = toolCallHandler.autoInjectLint(toolCalls)
            if (lintBlock != null) { combinedResult += "\n\n$lintBlock" }
            val hadModifyingTools = toolCalls.any { it.second in ChatConfig.MODIFYING_TOOLS }

            val display = StringBuilder()
            for (i in toolCalls.indices) {
                val name = toolCalls[i].second
                display.appendLine("[工具调用: $name]")
                display.appendLine(results[i])
                if (i < toolCalls.size - 1) display.appendLine()
            }
            updateModelMessage(currentMsgId, "\n\n${display.toString().trimEnd()}", false)
            finalizeModelMessage(currentMsgId)

            _state.update { it.copy(modelActivity = ModelActivity.ProcessingResult, activityDetail = "") }

            historyMessages.add(ChatMessage(
                role = ChatRole.Tool, content = combinedResult,
                toolCallId = toolCallId,
            ))

            if (cloudRounds % ChatConfig.SUMMARIZE_INTERVAL == 0) {
                historyMessages.clear()
                historyMessages.addAll(compressMessages(historyMessages.toList()))
                val summary = _state.value.contextSummary
                if (summary.isNotBlank() && historyMessages.none { it.content.startsWith("[上下文摘要]") }) {
                    historyMessages.add(0, ChatMessage(
                        role = ChatRole.Model,
                        content = "[上下文摘要]\n$summary",
                    ))
                }
            }

            if (hadModifyingTools || cloudRounds % 3 == 0) {
                val fullCtx = buildEditorContext()
                if (fullCtx.isNotBlank()) {
                    historyMessages.add(ChatMessage(role = ChatRole.System, content = fullCtx))
                }
            }

            currentMsgId = java.util.UUID.randomUUID().toString()
            _state.update { it.copy(messages = it.messages + ChatMessage(id = currentMsgId, role = ChatRole.Model, content = "", isStreaming = true), isLoading = true) }
        }
    }

    // ========== 云端配置管理 ==========

    fun setCloudModelEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesRepo.setCloudModelEnabled(enabled) }
        _state.update { it.copy(cloudModelEnabled = enabled) }
        updateContextMaxTokens()
    }

    fun addCloudProfile(name: String, apiEndpoint: String, apiKey: String, modelName: String, contextWindow: Int = 128000) {
        val id = java.util.UUID.randomUUID().toString()
        val newProfile = CloudModelProfile(id = id, name = name.ifEmpty { modelName },
            apiEndpoint = apiEndpoint, apiKey = apiKey, modelName = modelName,
            contextWindow = contextWindow)
        viewModelScope.launch {
            val current = preferencesRepo.cloudModelProfiles.first()
            val updated = current + newProfile
            preferencesRepo.setCloudModelProfiles(updated)
            if (updated.size == 1) preferencesRepo.setActiveCloudProfileId(id)
        }
    }

    fun removeCloudProfile(profileId: String) {
        viewModelScope.launch {
            val current = preferencesRepo.cloudModelProfiles.first()
            val updated = current.filter { it.id != profileId }
            preferencesRepo.setCloudModelProfiles(updated)
            val activeId = preferencesRepo.activeCloudProfileId.first()
            if (activeId == profileId) {
                preferencesRepo.setActiveCloudProfileId(updated.firstOrNull()?.id ?: "")
            }
        }
    }

    fun updateCloudProfile(profile: CloudModelProfile) {
        viewModelScope.launch {
            val current = preferencesRepo.cloudModelProfiles.first()
            preferencesRepo.setCloudModelProfiles(current.map { if (it.id == profile.id) profile else it })
        }
    }

    fun switchCloudProfile(profileId: String) {
        viewModelScope.launch {
            preferencesRepo.setActiveCloudProfileId(profileId)
            preferencesRepo.setCloudModelEnabled(true)
        }
    }

    fun verifyCloudConnection() {
        val profile = _state.value.cloudModelProfiles.find { it.id == _state.value.activeCloudProfileId }
        if (profile == null) { _state.update { it.copy(engineErrorMessage = "未选择云端模型配置") }; return }
        val cfg = CloudModelConfig(true, profile.apiEndpoint, profile.apiKey, profile.modelName, maxTokens = profile.maxTokens)
        _state.update { it.copy(engineErrorMessage = "验证中…") }
        viewModelScope.launch {
            val result = cloudLLMClient.verifyConnection(cfg)
            _state.update { it.copy(engineErrorMessage = if (result == "ok") "" else result) }
        }
    }

    private fun closeConversation() {
        val old = activeConversation
        activeConversation = null
        pendingInitialMessages = null
        if (old != null) {
            try { old.cancelProcess() } catch (_: Exception) {}
            // 异步销毁旧会话，C++ ~SessionAdvanced 的 WaitUntilDone 可能耗时很久
            // 阻塞的话 → 单线程执行池被旧任务占满 → 新会话任务排队
            viewModelScope.launch(Dispatchers.IO) {
                try { old.close() } catch (_: Exception) {}
            }
        }
    }

    // ========== 对话历史管理 ==========

    fun clearMessages() {
        sendJob?.cancel()
        _state.update { it.copy(messages = emptyList(), inputText = "",
            isContextCompressed = false, contextCompressedTokens = 0, contextCompressedCount = 0,
            contextSummary = "") }
        viewModelScope.launch(Dispatchers.IO) { closeConversation() }
    }

    fun newConversation() {
        sendJob?.cancel()
        _streamingContent.value = null
        val s = _state.value
        val updatedConversations = if (s.messages.isNotEmpty()) {
            val title = s.messages.firstOrNull { it.role == ChatRole.User }?.content?.take(30) ?: "新对话"
            val entry = ConversationEntry(
                id = s.activeConversationId ?: java.util.UUID.randomUUID().toString(),
                title = title, messages = s.messages,
            )
            val exists = s.conversations.any { it.id == entry.id }
            if (exists) s.conversations.map { if (it.id == entry.id) entry else it }
            else listOf(entry) + s.conversations
        } else s.conversations
        _state.update {
            it.copy(messages = emptyList(), inputText = "", isLoading = false,
                conversations = updatedConversations, activeConversationId = null)
        }
        viewModelScope.launch(Dispatchers.IO) {
            closeConversation()
            persistConversations(updatedConversations)
        }
    }

    fun switchConversation(entry: ConversationEntry) {
        sendJob?.cancel()
        _streamingContent.value = null
        pendingInitialMessages = entry.messages.mapNotNull { msg ->
            when (msg.role) {
                ChatRole.User -> Message.user(msg.content)
                ChatRole.Model -> Message.model(toolCallHandler.stripToolCallJson(msg.content))
                ChatRole.System -> Message.system(msg.content)
                ChatRole.Tool -> Message.tool(Contents.of(listOf(Content.ToolResponse(
                    msg.toolCallId ?: "call_${msg.id.take(8)}", msg.content))))
            }
        }
        _state.update { it.copy(messages = entry.messages, inputText = "", isLoading = false,
            activeConversationId = entry.id, isHistoryOpen = false,
            isContextCompressed = false, contextCompressedTokens = 0, contextCompressedCount = 0,
            contextSummary = "") }
        viewModelScope.launch(Dispatchers.IO) { closeConversation() }
    }

    fun deleteConversation(entryId: String) {
        val currentState = _state.value
        val updated = currentState.conversations.filter { it.id != entryId }
        val isActive = currentState.activeConversationId == entryId
        _state.update {
            if (isActive) it.copy(conversations = updated, messages = emptyList(), activeConversationId = null)
            else it.copy(conversations = updated)
        }
        viewModelScope.launch(Dispatchers.IO) {
            if (isActive) closeConversation()
            persistConversations(updated)
        }
    }

    fun toggleHistory() { _state.update { it.copy(isHistoryOpen = !it.isHistoryOpen) } }
    fun closeHistory() { _state.update { it.copy(isHistoryOpen = false) } }

    // ========== 消息状态更新 ==========

    private fun updateModelMessage(msgId: String, chunk: String, append: Boolean) {
        _streamingContent.value = StreamingState(
            msgId = msgId,
            content = if (append) {
                (_streamingContent.value?.content ?: "") + chunk
            } else chunk,
        )
    }

    private fun finalizeModelMessage(msgId: String, channelContent: String? = null) {
        val finalContent = _streamingContent.value?.content ?: ""
        _state.update { state ->
            val updatedMessages = state.messages.map { msg ->
                if (msg.id == msgId) msg.copy(content = finalContent, isStreaming = false, channelContent = channelContent ?: msg.channelContent)
                else msg
            }
            state.copy(messages = updatedMessages, isLoading = false)
        }
        _streamingContent.value = null
        viewModelScope.launch { autoSaveToMemory() }
        saveCurrentToHistory()
    }

    private fun saveCurrentToHistory() {
        val s = _state.value
        if (s.messages.isEmpty()) return
        val title = s.messages.firstOrNull { it.role == ChatRole.User }?.content?.take(30) ?: "新对话"
        val entry = ConversationEntry(id = s.activeConversationId ?: java.util.UUID.randomUUID().toString(), title = title, messages = s.messages)
        val updated = if (s.conversations.any { it.id == entry.id }) s.conversations.map { if (it.id == entry.id) entry else it }
        else listOf(entry) + s.conversations
        _state.update { it.copy(conversations = updated, activeConversationId = entry.id) }
        persistConversations(updated)
    }

    private fun persistConversations(conversations: List<ConversationEntry>) {
        viewModelScope.launch(Dispatchers.IO) { conversationRepo.save(conversations) }
    }

    private suspend fun autoSaveToMemory() {
        val msgs = _state.value.messages
        if (msgs.isEmpty()) return
        val recentUser = msgs.lastOrNull { it.role == ChatRole.User && !it.content.startsWith("[系统指令]") }
        val recentModel = msgs.lastOrNull { it.role == ChatRole.Model && it.isStreaming == false && !it.content.startsWith("[上下文") }
        val toSave = mutableListOf<ChatMessageAdapter>()
        recentUser?.let { toSave.add(ChatMessageAdapter("user", it.content.take(2000))) }
        recentModel?.let { toSave.add(ChatMessageAdapter("model", it.content.take(2000))) }
        if (toSave.isNotEmpty()) {
            val convId = _state.value.activeConversationId ?: ""
            conversationMemory.addMessages(toSave, convId)
            refreshMemoryState()
        }
    }

    override fun onCleared() {
        super.onCleared()
        sendJob?.cancel()
        // onCleared 中 viewModelScope 可能已取消，需要同步关闭
        try { activeConversation?.close() } catch (_: Exception) {}
        activeConversation = null
        pendingInitialMessages = null
        saveCurrentToHistory()
        liteRTManager.close()
    }
}
