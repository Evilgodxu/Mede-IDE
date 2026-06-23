package com.medeide.jh.screens.home

import android.app.Application
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.tool
import com.medeide.jh.screens.home.aitools.AIToolSet
import com.medeide.jh.screens.home.aitools.ToolCallHandler
import com.medeide.jh.screens.home.aitools.ToolExecutionCallback
import com.medeide.jh.data.analytics.LlmCallRecord
import com.medeide.jh.data.analytics.ToolCallRecord
import com.medeide.jh.data.analytics.UsageStats
import com.medeide.jh.screens.home.aitools.ChatConfig
import com.medeide.jh.screens.home.memory.ChatMessageAdapter
import com.medeide.jh.screens.home.memory.ContextManager
import com.medeide.jh.screens.home.memory.ConversationMemory
import com.medeide.jh.data.storage.FileManager
import com.medeide.jh.data.utils.FileLogger
import com.medeide.jh.data.utils.ImageProcessor
import com.medeide.jh.data.repository.ConversationRepository
import com.medeide.jh.data.repository.UsageAnalyticsRepository
import com.medeide.jh.data.repository.UserPreferencesRepository
import com.medeide.jh.data.source.local.LiteRTManager
import com.medeide.jh.data.source.local.toSamplerConfig
import com.medeide.jh.data.source.remote.CloudLLMClient
import com.medeide.jh.model.DEFAULT_ROLE_ID
import com.medeide.jh.model.Rule
import com.medeide.jh.model.chat.AttachedFile
import com.medeide.jh.model.chat.ChatMessage
import com.medeide.jh.model.chat.ChatRole
import com.medeide.jh.model.chat.ToolCallInfo
import com.medeide.jh.model.chat.CloudModelConfig
import com.medeide.jh.model.chat.CloudModelProfile
import com.medeide.jh.model.chat.ConversationEntry
import com.medeide.jh.model.chat.DisplayItem
import com.medeide.jh.model.chat.EngineStatus
import com.medeide.jh.model.chat.ModelParams
import com.medeide.jh.model.chat.BackendType
import com.medeide.jh.model.chat.FileOperation
import com.medeide.jh.model.chat.FileOpStatus
import com.medeide.jh.model.chat.FileOpType
import com.medeide.jh.screens.home.landscape.collab.chat.utils.toDisplayItemsWithFileOps
import com.medeide.jh.screens.home.logic.utils.FileTypeUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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
    private val inputOptimizer: com.medeide.jh.screens.home.aitools.InputOptimizer,
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state

    val displayItems: StateFlow<List<DisplayItem>> = _state
        .map { toDisplayItemsWithFileOps(it.messages, it.fileOperations) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val usageStats: StateFlow<UsageStats> = usageAnalyticsRepo.stats.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), UsageStats()
    )

    val contextTokenCount: StateFlow<Int> = _state.map { s ->
        contextManager.estimateContextTokens(s.messages) +
            contextManager.estimateSystemPromptTokens(contextManager.getSysPromptCache())
    }.flowOn(Dispatchers.Default)
        .distinctUntilChanged().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private var sendJob: Job? = null
    @Volatile private var activeConversation: Conversation? = null
    private var pendingInitialMessages: List<Message>? = null
    /** 工具调用开始时间戳（用于计算耗时） */
    private val _toolStartTimes = mutableMapOf<String, Long>()
    /** 文件操作 ID 追踪：tool_name → FileOperation.id */
    private val _pendingFileOpIds = mutableMapOf<String, String>()

    // System prompt 依赖值缓存
    @Volatile private var sysPromptUserName: String = ""
    @Volatile private var sysPromptRules: List<Rule> = emptyList()
    @Volatile private var sysPromptActiveRoleId: String = DEFAULT_ROLE_ID
    @Volatile private var sysPromptDeepThink: Boolean = false
    @Volatile private var sysPromptVersion: Int = 0
    @Volatile private var convSysPromptVersion: Int = -1

    // ========== 封装方法 ==========

    private fun buildSystemInstruction(): String = contextManager.buildSystemInstruction(
        sysPromptCache = contextManager.getSysPromptCache(),
        userName = sysPromptUserName,
        rules = sysPromptRules,
        activeRoleId = sysPromptActiveRoleId,
        cloudModelEnabled = _state.value.cloudModelEnabled,
    )

    private fun buildEditorContext(): String = contextManager.buildEditorContext(
        activeFilePath = _state.value.activeFilePath,
        projectRootName = _state.value.projectRootName,
        openedFilePaths = _state.value.openedFilePaths,
        modifiedFilePaths = _state.value.modifiedFilePaths,
        browserCurrentDir = _state.value.browserCurrentDir,
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
                memoryEntryCount = stats.entryCount,
                memoryTotalTokens = stats.estimatedTokens,
            )
        }
    }

    /** 构建上下文仪表板数据（在 Composable 中调用） */
    fun buildDashboardData(): DashboardData {
        val s = _state.value
        val snapshot = com.medeide.jh.screens.home.memory.VisualizerEngine.buildSnapshot(
            messages = s.messages,
            maxTokens = s.contextMaxTokens,
            isCompressed = s.isContextCompressed,
            compressedTokens = s.contextCompressedTokens,
            compressedCount = s.contextCompressedCount,
        )
        val breakdown = com.medeide.jh.screens.home.memory.VisualizerEngine.buildTokenBreakdown(
            messages = s.messages,
            sysPromptTokens = contextManager.estimateSystemPromptTokens(contextManager.getSysPromptCache()),
        )
        return DashboardData(snapshot, breakdown, usageStats.value)
    }

    data class DashboardData(
        val snapshot: com.medeide.jh.screens.home.memory.ContextSnapshot,
        val breakdown: com.medeide.jh.screens.home.memory.TokenBreakdown,
        val usageStats: UsageStats = UsageStats(),
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

    /** 本地模型：collect 完成后检查上下文是否超过 35% 阈值，超限则压缩并重建 */
    private suspend fun compressLocalContextIfNeeded() {
        val msgs = _state.value.messages
        if (msgs.isEmpty()) return
        val threshold = getCompressThreshold()
        val totalTokens = contextManager.estimateContextTokens(msgs)
        if (totalTokens <= threshold) return

        val result = contextManager.compressMessages(
            messages = msgs,
            activeConversationId = _state.value.activeConversationId,
            threshold = threshold,
        )
        if (result.removedTokens <= 0) return

        // 更新 UI 消息列表为压缩后的版本
        _state.update { it.copy(messages = result.messages) }
        applyCompressResult(result)

        // 关闭当前会话，下次 ensureConversation 自动重建
        activeConversation?.let {
            try { it.cancelProcess() } catch (_: Exception) {}
            try { it.close() } catch (_: Exception) {}
        }
        activeConversation = null
        pendingInitialMessages = contextManager.chatMessagesToLiteRT(result.messages)
    }

    // ========== init ==========

    init {
        viewModelScope.launch {
            scanWhenStorageGranted()
        }
        viewModelScope.launch {
            combine(
                preferencesRepo.deepThinkEnabled,
                preferencesRepo.userName,
                preferencesRepo.rules,
                preferencesRepo.activeRoleId,
                _state.map { it.cloudModelEnabled }.distinctUntilChanged(),
            ) { think, name, rules, activeRoleId, _ ->
                sysPromptDeepThink = think
                sysPromptUserName = name
                sysPromptRules = rules
                sysPromptActiveRoleId = activeRoleId
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
            syncToolMemoryScope()
            conversationMemory.load()
            refreshMemoryState()
            try {
                val autoLoad = preferencesRepo.autoLoadLastModel.first()
                val lastPath = preferencesRepo.lastModelPath.first()
                val backend = preferencesRepo.backendType.first()
                val npuDir = preferencesRepo.npuLibraryDir.first()
                val wasCloudEnabled = preferencesRepo.cloudModelEnabled.first()
                // 上次使用的是云端模型时跳过加载本地模型
                if (autoLoad && !wasCloudEnabled && !lastPath.isNullOrEmpty()) {
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
            cloudLLMClient.cancelCurrentCall()
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
            cloudLLMClient.cancelCurrentCall()
            closeConversation()
            liteRTManager.loadModelFromUri(uri)
            preferencesRepo.setCloudModelEnabled(false)
            _state.update { it.copy(cloudModelEnabled = false) }
        }
    }

    fun scanModels() {
        viewModelScope.launch(Dispatchers.IO) {
            val models = liteRTManager.scanModels(force = true)
            _state.update { it.copy(availableModels = models) }
        }
    }

    private suspend fun scanWhenStorageGranted() {
        while (!Environment.isExternalStorageManager()) {
            delay(500)
        }
        scanModels()
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

    fun setBrowserCurrentDir(dir: String) {
        _state.update { it.copy(browserCurrentDir = dir) }
    }
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
        val userMsg = ChatMessage(role = ChatRole.User, content = text, imageUris = images)
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
                    processWithCloudTools(text, modelMsgId, ctx, tempImagePaths, files)
                } else {
                    ensureConversation()
                    processLocalMessage(text, modelMsgId, tempImagePaths, files)
                    compressLocalContextIfNeeded()
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("ChatViewModel", "发送消息失败", e)
                FileLogger.e("ChatViewModel", "发送消息失败: ${e.message}", e)
                updateModelMessage(modelMsgId, "\n\n[错误: ${e.message}]", false)
                finalizeModelMessage(modelMsgId)
            }
        }
    }

    fun cancelGeneration() {
        sendJob?.cancel()
        // 关闭云端 HTTP 连接，通知服务器停止生成，避免继续消耗 tokens
        cloudLLMClient.cancelCurrentCall()
        activeConversation?.let {
            try { it.cancelProcess() } catch (_: Exception) {}
            try { it.close() } catch (_: Exception) {}
        }
        activeConversation = null
        val msgs = _state.value.messages
        if (msgs.isNotEmpty()) {
            pendingInitialMessages = contextManager.chatMessagesToLiteRT(msgs)
        }
        _state.value.messages.lastOrNull()?.let { msg -> if (msg.isStreaming) finalizeModelMessage(msg.id) }
        // 取消时清理文件操作记录
        _state.update { it.copy(fileOperations = emptyList()) }
        _pendingFileOpIds.clear()
    }

    // ========== 文件操作状态管理 ==========

    /** 工具名称 → FileOpType 映射 */
    private val FILE_OP_TOOLS = mapOf(
        "writeFile" to FileOpType.WriteFile,
        "createDirectory" to FileOpType.CreateDirectory,
        "deleteFile" to FileOpType.DeleteFile,
        "moveFile" to FileOpType.MoveFile,
        "copyFile" to FileOpType.CopyFile,
    )

    /** 添加新的文件操作记录（状态为 InProgress），返回操作 ID */
    private fun addFileOp(name: String, type: FileOpType, filePath: String, parentMessageId: String): String {
        val op = FileOperation(type = type, filePath = filePath, parentMessageId = parentMessageId)
        _pendingFileOpIds[name] = op.id
        // 派发到 Main 确保 InProgress 状态被 Compose 收集到（工具执行可能同步阻塞）
        viewModelScope.launch(Dispatchers.Main) {
            _state.update { it.copy(fileOperations = it.fileOperations + op) }
        }
        return op.id
    }

    /** 更新文件操作状态（Success / Error） */
    private fun updateFileOp(id: String, status: FileOpStatus, errorMessage: String? = null) {
        viewModelScope.launch(Dispatchers.Main) {
            // 小幅延迟确保 InProgress >= Success 之间至少有一帧间隔
            delay(30)
            _state.update { state ->
                val updated = state.fileOperations.map { op ->
                    if (op.id == id) op.copy(status = status, errorMessage = errorMessage) else op
                }
                state.copy(fileOperations = updated)
            }
        }
    }

    /** 从 args 中提取文件路径（写文件/读文件等工具第一个参数就是路径） */
    private fun extractFilePath(name: String, args: Map<String, String>): String {
        return args["file_path"] ?: args["file_paths"] ?: args["path"] ?: args["source"] ?: args["destination"] ?: args["url"] ?: "未知路径"
    }

    // ========== 输入优化（独立于推理工作流） ==========

    private var _optimizeMode = com.medeide.jh.screens.home.aitools.InputOptimizer.Mode.CODE
    val optimizeMode: com.medeide.jh.screens.home.aitools.InputOptimizer.Mode get() = _optimizeMode

    fun setOptimizeMode(mode: com.medeide.jh.screens.home.aitools.InputOptimizer.Mode) {
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
                        val cfg = com.medeide.jh.model.chat.CloudModelConfig(
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
                Log.e("ChatViewModel", "优化输入失败", e)
                FileLogger.e("ChatViewModel", "优化输入失败: ${e.message}", e)
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

    private fun ensureConversation(autoToolCalling: Boolean = true): Conversation {
        // 复用现有会话：仅系统指令变更或切换对话时重建
        val needsRebuild = convSysPromptVersion != sysPromptVersion ||
            (pendingInitialMessages != null && pendingInitialMessages!!.isNotEmpty())
        if (!needsRebuild && activeConversation != null) {
            pendingInitialMessages = null
            return activeConversation!!
        }
        val initialMsgs = pendingInitialMessages
        pendingInitialMessages = null
        activeConversation?.let {
            try { it.cancelProcess() } catch (_: Exception) {}
            try { it.close() } catch (_: Exception) {}
        }
        activeConversation = null
        convSysPromptVersion = sysPromptVersion
        @OptIn(com.google.ai.edge.litertlm.ExperimentalApi::class)
        com.google.ai.edge.litertlm.ExperimentalFlags.filterChannelContentFromKvCache = sysPromptDeepThink
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
        files: List<AttachedFile> = emptyList(),
    ) {
        val conv = activeConversation
        if (conv == null) {
            Log.e("ChatViewModel", "本地对话失败: 活跃会话为空")
            updateModelMessage(msgId, "\n\n[错误: 模型会话未就绪]", false)
            finalizeModelMessage(msgId)
            return
        }
        // 上下文注入消息渠道
        val editorCtx = buildEditorContext()
        val messageBuilder = StringBuilder()
        if (editorCtx.isNotBlank()) messageBuilder.appendLine(editorCtx).appendLine()
        val fileBlock = contextManager.buildFileAttachmentBlock(files)
        if (fileBlock.isNotBlank()) {
            messageBuilder.appendLine(fileBlock).appendLine()
        }
        messageBuilder.append(text)
        val userText = messageBuilder.toString().trim()

        val hasImages = imagePaths.isNotEmpty()
        val thinkCtx = mapOf("enable_thinking" to sysPromptDeepThink)
        val currentMessage: Message = if (hasImages) {
            val contents = mutableListOf<Content>(Content.Text(userText))
            imagePaths.forEach { contents.add(Content.ImageFile(absolutePath = it)) }
            Message.user(Contents.of(contents))
        } else {
            Message.user(userText)
        }
        var channelContent: String? = null

        // 注入回调：框架自动执行 @Tool 方法时更新 UI 状态并记录工具调用
        toolCallHandler.callback = object : ToolExecutionCallback {
            override fun onToolStart(name: String, args: Map<String, String>) {
                _toolStartTimes[name] = System.currentTimeMillis()
                // 文件操作类的工具显示操作状态卡片
                FILE_OP_TOOLS[name]?.let { type ->
                    addFileOp(name, type, extractFilePath(name, args), msgId)
                }
            }
            override fun onToolResult(name: String, args: Map<String, String>, result: String) {
                val startMs = _toolStartTimes.remove(name) ?: return
                val durationMs = System.currentTimeMillis() - startMs
                val success = !result.startsWith("Tool error:") && !result.startsWith("Error:")
                _pendingFileOpIds.remove(name)?.let { opId ->
                    updateFileOp(opId, if (success) FileOpStatus.Success else FileOpStatus.Error,
                        if (success) null else result.take(100))
                }
                viewModelScope.launch {
                    usageAnalyticsRepo.recordToolCall(
                        ToolCallRecord(
                            toolName = name,
                            success = success,
                            durationMs = durationMs,
                        )
                    )
                }
            }
        }

        try {
            conv.sendMessageAsync(currentMessage, extraContext = thinkCtx).collect { msg ->
                val c = msg.contents.toString()
                val hasThinkChannel = msg.channels.containsKey("thought") || msg.channels.containsKey("thinking")
                val hasToolCalls = msg.toolCalls.isNotEmpty()
                // 工具调用阶段的文本内容（可能含工具 JSON）由框架内部处理，不显示到聊天界面
                if (!hasThinkChannel && !hasToolCalls && c.isNotEmpty()) {
                    updateModelMessage(msgId, c, true)
                }
                if (msg.channels.isNotEmpty()) {
                    val channelValues = msg.channels.values.joinToString("\n")
                    if (channelValues.isNotBlank()) {
                        channelContent = (channelContent ?: "") + channelValues
                    }
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            FileLogger.e("ChatViewModel", "本地对话处理失败: ${e.message}", e)
            updateModelMessage(msgId, "\n\n[错误: ${e.message}]", false)
        } finally {
            // 如果通道 API 未提供思考内容但消息中有 <think> 或 [think] 块，从中提取
            if (channelContent == null) {
                val currentMsg = _state.value.messages.find { it.id == msgId }
                if (currentMsg != null) {
                    var thinkContent: String? = null
                    val thinkTagMatch = Regex("<think>([\\s\\S]*?)</think>").find(currentMsg.content)
                    if (thinkTagMatch != null) {
                        thinkContent = thinkTagMatch.groupValues[1].trim()
                    } else {
                        val bracketMatch = Regex("\\[think]([\\s\\S]*?)\\[/think]").find(currentMsg.content)
                        if (bracketMatch != null) {
                            thinkContent = bracketMatch.groupValues[1].trim()
                        }
                    }
                    if (thinkContent != null) {
                        channelContent = thinkContent
                        val cleaned = currentMsg.content
                            .replace(Regex("<think>[\\s\\S]*?</think>"), "")
                            .replace(Regex("\\[think][\\s\\S]*?\\[/think]"), "")
                            .trim()
                        _state.update { state ->
                            val updatedMessages = state.messages.map {
                                if (it.id == msgId) it.copy(content = cleaned, channelContent = channelContent) else it
                            }
                            state.copy(messages = updatedMessages)
                        }
                    }
                }
            }
            finalizeModelMessage(msgId, channelContent = channelContent)
            toolCallHandler.callback = null
            // 不关闭会话，匹配官方示例：Conversation 持续复用
        }
    }

    // ========== 云端模型处理 ==========

    private suspend fun processWithCloudTools(
        text: String, msgId: String,
        ctx: Application,
        imagePaths: List<String> = emptyList(),
        files: List<AttachedFile> = emptyList(),
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

        val historyMessages = mutableListOf<ChatMessage>()
        val lastUserIdx = _state.value.messages.indexOfLast { it.role == ChatRole.User && it.id != currentMsgId }
        for ((idx, msg) in _state.value.messages.withIndex()) {
            if (msg.id == currentMsgId) continue
            if (msg.role == ChatRole.User || msg.role == ChatRole.Model || msg.role == ChatRole.Tool) {
                if (msg.role == ChatRole.User && idx == lastUserIdx) continue
                if (msg.content.isNotBlank() || msg.role == ChatRole.Model) historyMessages.add(msg)
            }
        }
        val editorCtx = buildEditorContext()
        if (editorCtx.isNotBlank()) {
            historyMessages.add(ChatMessage(role = ChatRole.System, content = editorCtx))
        }
        val fileBlock = contextManager.buildFileAttachmentBlock(files)
        if (fileBlock.isNotBlank()) {
            historyMessages.add(ChatMessage(role = ChatRole.System, content = fileBlock))
        }
        historyMessages.add(ChatMessage(role = ChatRole.User, content = text))

        while (true) {
            cloudRounds++
            // 工具调用轮次超限时立即打断生成（关闭 HTTP 连接 + 关闭 UI 流式状态）
            if (cloudRounds > profile.maxToolRounds) {
                Log.w("ChatViewModel", "云端工具调用轮次超限(${profile.maxToolRounds})，已打断")
                FileLogger.w("ChatViewModel", "云端工具调用轮次超限(${profile.maxToolRounds})，已打断")
                cloudLLMClient.cancelCurrentCall()
                val lastMsg = _state.value.messages.find { it.id == currentMsgId }
                if (lastMsg?.isStreaming == true) {
                    updateModelMessage(currentMsgId, "\n\n[已停止: 工具调用次数超限]", false)
                    finalizeModelMessage(currentMsgId)
                }
                return
            }

            val fullResponse = StringBuilder()
            val roundStartTime = System.currentTimeMillis()
            var apiUsage = com.medeide.jh.model.chat.ApiUsage()
            val toolsJson = AIToolSet.buildOpenAIToolsJson()
            val nativeToolCalls = mutableListOf<com.medeide.jh.model.chat.CloudToolCall>()
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
            } catch (e: kotlinx.coroutines.CancellationException) {
                // 用户主动取消，不显示错误信息
                val lastMsg = _state.value.messages.find { it.id == currentMsgId }
                if (lastMsg?.isStreaming == true) finalizeModelMessage(currentMsgId)
                return
            } catch (e: Exception) {
                val duration = System.currentTimeMillis() - roundStartTime
                val errMsg = e.message ?: "Unknown error"
                val isContextError = errMsg.contains("context_length", ignoreCase = true) ||
                    errMsg.contains("maximum context", ignoreCase = true) ||
                    errMsg.contains("token limit", ignoreCase = true) ||
                    errMsg.contains("too many tokens", ignoreCase = true) ||
                    errMsg.contains("maximum prompt length", ignoreCase = true)
                if (isContextError) {
                    Log.w("ChatViewModel", "上下文长度超限，云端 API 拒绝请求，请检查上下文窗口设置或减少对话历史")
                    FileLogger.w("ChatViewModel", "上下文长度超限，云端 API 拒绝请求")
                    updateModelMessage(currentMsgId, "\n\n[云端模型错误: 上下文长度超限，请检查上下文窗口设置或减少对话历史]", false)
                    finalizeModelMessage(currentMsgId)
                    return
                }
                Log.e("ChatViewModel", "发送到云端失败", e)
                FileLogger.e("ChatViewModel", "发送到云端失败: ${errMsg}", e)
                recordUsage(LlmCallRecord(
                    modelName = cfg.modelName,
                    provider = "cloud",
                    promptTokens = 0, completionTokens = 0,
                    durationMs = duration, success = false,
                    errorMessage = errMsg,
                ))
                updateModelMessage(currentMsgId, "\n\n[云端模型错误: ${errMsg}]", false)
                finalizeModelMessage(currentMsgId)
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
            // 提取思考块（<think> 或 [think]）作为 channelContent
            var channelContent: String? = null
            var cleanResponse = response
            val thinkTagMatch = Regex("<think>([\\s\\S]*?)</think>").find(response)
            val bracketMatch = Regex("\\[think]([\\s\\S]*?)\\[/think]").find(response)
            if (thinkTagMatch != null) {
                channelContent = thinkTagMatch.groupValues[1].trim()
                cleanResponse = response.replace(Regex("<think>[\\s\\S]*?</think>"), "").trim()
            } else if (bracketMatch != null) {
                channelContent = bracketMatch.groupValues[1].trim()
                cleanResponse = response.replace(Regex("\\[think][\\s\\S]*?\\[/think]"), "").trim()
            }
            if (channelContent != null) {
                // 用无 think 版本替换消息内容
                _state.update { state ->
                    val updatedMessages = state.messages.map { msg ->
                        if (msg.id == currentMsgId) msg.copy(content = cleanResponse, channelContent = channelContent) else msg
                    }
                    state.copy(messages = updatedMessages)
                }
            }
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
                historyMessages.add(ChatMessage(role = ChatRole.Model, content = cleanResponse))
                finalizeModelMessage(currentMsgId, channelContent = channelContent)
                return
            }

            // 设置工具调用统计回调，记录工具执行次数和耗时
            toolCallHandler.callback = object : ToolExecutionCallback {
                override fun onToolStart(name: String, args: Map<String, String>) {
                    _toolStartTimes[name] = System.currentTimeMillis()
                    // 文件操作类的工具显示操作状态卡片
                    FILE_OP_TOOLS[name]?.let { type ->
                        addFileOp(name, type, extractFilePath(name, args), currentMsgId)
                    }
                }
                override fun onToolResult(name: String, args: Map<String, String>, result: String) {
                    val startMs = _toolStartTimes.remove(name) ?: return
                    val durationMs = System.currentTimeMillis() - startMs
                    val success = !result.startsWith("Tool error:") && !result.startsWith("Error:")
                    _pendingFileOpIds.remove(name)?.let { opId ->
                        updateFileOp(opId, if (success) FileOpStatus.Success else FileOpStatus.Error,
                            if (success) null else result.take(100))
                    }
                    viewModelScope.launch {
                        usageAnalyticsRepo.recordToolCall(
                            ToolCallRecord(
                                toolName = name,
                                success = success,
                                durationMs = durationMs,
                            )
                        )
                    }
                }
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
            // 清除回调，避免影响后续操作
            toolCallHandler.callback = null

            // 构建完整 tool_calls 信息列表（含 id、name、arguments），存入 assistant 消息
            val toolCallInfoList = toolCalls.map { (id, name, argsMap) ->
                val argsJson = org.json.JSONObject().apply { argsMap.forEach { (k, v) -> put(k, v) } }.toString()
                ToolCallInfo(
                    id = id ?: "call_${java.util.UUID.randomUUID().toString().take(8)}",
                    name = name,
                    arguments = argsJson,
                )
            }
            historyMessages.add(ChatMessage(
                role = ChatRole.Model, content = response,
                toolCalls = toolCallInfoList,
            ))

            // 每个工具执行结果作为独立的 role:tool 消息添加（OpenAI 规范要求一对一匹配）
            toolCalls.forEachIndexed { i, tc ->
                val resultText = results.getOrElse(i) { "No result" }
                historyMessages.add(ChatMessage(
                    role = ChatRole.Tool,
                    content = resultText,
                    toolCallId = tc.first,
                    toolName = tc.second,
                ))
            }
            val hadModifyingTools = toolCalls.any { it.second in ChatConfig.MODIFYING_TOOLS }

            // 清理消息内容中的工具调用 JSON，工具执行结果仅发给模型不显示到聊天界面
            val cleanedContent = toolCallHandler.stripToolCallJson(cleanResponse)
            if (cleanedContent != cleanResponse) {
                _state.update { state ->
                    val updatedMessages = state.messages.map { msg ->
                        if (msg.id == currentMsgId) msg.copy(content = cleanedContent) else msg
                    }
                    state.copy(messages = updatedMessages)
                }
            }
            finalizeModelMessage(currentMsgId, channelContent = channelContent)

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

    fun addCloudProfile(name: String, apiEndpoint: String, apiKey: String, modelName: String, contextWindow: Int = 184000, maxTokens: Int = 16000, maxToolRounds: Int = 200) {
        val id = java.util.UUID.randomUUID().toString()
        val newProfile = CloudModelProfile(id = id, name = name.ifEmpty { modelName },
            apiEndpoint = apiEndpoint, apiKey = apiKey, modelName = modelName,
            contextWindow = contextWindow, maxTokens = maxTokens, maxToolRounds = maxToolRounds)
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
        cloudLLMClient.cancelCurrentCall()
        _state.update { it.copy(messages = emptyList(), inputText = "",
            isContextCompressed = false, contextCompressedTokens = 0, contextCompressedCount = 0,
            contextSummary = "") }
        viewModelScope.launch(Dispatchers.IO) { closeConversation() }
    }

    fun newConversation() {
        sendJob?.cancel()
        cloudLLMClient.cancelCurrentCall()
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
                conversations = updatedConversations, activeConversationId = null,
                isContextCompressed = false, contextCompressedTokens = 0, contextCompressedCount = 0,
                contextSummary = "")
        }
        syncToolMemoryScope()
        viewModelScope.launch(Dispatchers.IO) {
            closeConversation()
            persistConversations(updatedConversations)
        }
    }

    fun switchConversation(entry: ConversationEntry) {
        sendJob?.cancel()
        cloudLLMClient.cancelCurrentCall()
        // 仅在本地模型模式下构建 LiteRT-LM 消息（JNI 调用需要 native 库已加载）
        pendingInitialMessages = if (!_state.value.cloudModelEnabled) {
            try {
                entry.messages.mapNotNull { msg ->
                    when (msg.role) {
                        ChatRole.User -> Message.user(msg.content)
                        ChatRole.Model -> Message.model(toolCallHandler.stripToolCallJson(msg.content))
                        ChatRole.System -> Message.system(msg.content)
                        ChatRole.Tool -> Message.tool(Contents.of(listOf(Content.ToolResponse(
                             msg.toolCallId ?: "call_${msg.id.take(8)}", msg.content))))
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("ChatViewModel", "构建 LiteRT-LM 消息失败（可能未加载本地模型）: ${e.message}")
                null
            }
        } else null
        _state.update { it.copy(messages = entry.messages, inputText = "", isLoading = false,
            activeConversationId = entry.id, isHistoryOpen = false,
            isContextCompressed = false, contextCompressedTokens = 0, contextCompressedCount = 0,
            contextSummary = "") }
        syncToolMemoryScope()
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
        syncToolMemoryScope()
        viewModelScope.launch(Dispatchers.IO) {
            if (isActive) closeConversation()
            persistConversations(updated)
        }
    }

    fun toggleHistory() { _state.update { it.copy(isHistoryOpen = !it.isHistoryOpen) } }
    fun closeHistory() { _state.update { it.copy(isHistoryOpen = false) } }

    // ========== 消息状态更新 ==========

    private fun updateModelMessage(msgId: String, chunk: String, append: Boolean) {
        _state.update { state ->
            val updatedMessages = state.messages.map { msg ->
                if (msg.id == msgId) {
                    val newContent = if (append) msg.content + chunk else chunk
                    msg.copy(content = newContent, isStreaming = true, channelContent = msg.channelContent)
                } else msg
            }
            state.copy(messages = updatedMessages)
        }
    }

    private fun finalizeModelMessage(msgId: String, channelContent: String? = null) {
        _state.update { state ->
            val updatedMessages = state.messages.map { msg ->
                if (msg.id == msgId) msg.copy(
                    content = msg.content,
                    isStreaming = false,
                    channelContent = channelContent ?: msg.channelContent,
                )
                else msg
            }
            state.copy(messages = updatedMessages, isLoading = false)
        }
        if (!_state.value.cloudModelEnabled) {
            viewModelScope.launch { autoSaveToMemory() }
        }
        saveCurrentToHistory()
    }

    private fun saveCurrentToHistory() {
        val s = _state.value
        if (s.messages.isEmpty()) return
        val title = s.messages.firstOrNull { it.role == ChatRole.User }?.content?.take(30) ?: "新对话"
        val entry = ConversationEntry(id = s.activeConversationId ?: java.util.UUID.randomUUID().toString(), title = title, messages = s.messages)
        val updated = if (s.conversations.any { it.id == entry.id }) s.conversations.map { if (it.id == entry.id) entry else it }
        else listOf(entry) + s.conversations
        // 历史对话仅保留最近 15 条
        val capped = updated.take(15)
        _state.update { it.copy(conversations = capped, activeConversationId = entry.id) }
        syncToolMemoryScope()
        persistConversations(capped)
    }

    private fun persistConversations(conversations: List<ConversationEntry>) {
        viewModelScope.launch(Dispatchers.IO) { conversationRepo.save(conversations) }
    }

    /** 同步 AIToolSet 记忆隔离用的当前对话 ID */
    private fun syncToolMemoryScope() {
        aiToolSet.currentConversationId = _state.value.activeConversationId
    }

    private var lastSavedMsgIndex = -1

    private suspend fun autoSaveToMemory() {
        val msgs = _state.value.messages
        if (msgs.isEmpty()) return
        // 上下文超过 85% 时不再存储记忆（用户应开始新对话）
        val ratio = if (_state.value.contextMaxTokens > 0)
            contextManager.estimateContextTokens(msgs).toFloat() / _state.value.contextMaxTokens else 0f
        if (ratio >= 0.85f) return
        val convId = _state.value.activeConversationId ?: ""
        // 增量保存：只保存上次记录之后的新消息
        val newMessages = msgs.drop(lastSavedMsgIndex + 1)
            .filter { it.role == ChatRole.User || (it.role == ChatRole.Model && it.isStreaming == false) }
            .filterNot { it.content.startsWith("[系统指令]") || it.content.startsWith("[上下文") }
        if (newMessages.isEmpty()) return
        val toSave = newMessages.map { msg ->
            ChatMessageAdapter(if (msg.role == ChatRole.User) "user" else "model", msg.content)
        }
        conversationMemory.addMessages(toSave, convId)
        lastSavedMsgIndex = msgs.size - 1
        refreshMemoryState()
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
