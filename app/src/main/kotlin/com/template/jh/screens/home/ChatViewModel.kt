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
import com.google.ai.edge.litertlm.tool
import com.template.jh.core.ai.AIToolSet
import com.template.jh.core.ai.FileOperationEvents
import com.template.jh.core.analytics.LlmCallRecord
import com.template.jh.core.analytics.UsageStats
import com.template.jh.core.memory.ChatMessageAdapter
import com.template.jh.core.memory.ConversationMemory
import com.template.jh.core.utils.FileLogger
import com.template.jh.data.repository.ConversationRepository
import com.template.jh.data.repository.UsageAnalyticsRepository
import com.template.jh.data.repository.UserPreferencesRepository
import com.template.jh.data.source.local.LiteRTManager
import com.template.jh.data.source.local.toSamplerConfig
import com.template.jh.data.source.remote.CloudLLMClient
import com.template.jh.model.Rule
import com.template.jh.model.RuleType
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
import com.template.jh.screens.home.components.chat.toDisplayItems
import com.template.jh.screens.home.logic.utils.FileTypeUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class ChatViewModel(
    application: Application,
    private val conversationRepo: ConversationRepository,
    private val preferencesRepo: UserPreferencesRepository,
    private val fileManager: com.template.jh.core.storage.FileManager,
    private val usageAnalyticsRepo: UsageAnalyticsRepository,
) : AndroidViewModel(application) {

    private val liteRTManager = LiteRTManager(application)
    private val conversationMemory = ConversationMemory(application)
    private val aiToolSet = AIToolSet(application, fileManager, conversationMemory)
    private val cloudLLMClient = CloudLLMClient(application)
    private val userPreferencesRepo = preferencesRepo

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state

    val displayItems: StateFlow<List<DisplayItem>> = _state.map { s ->
        toDisplayItems(s.messages)
    }.flowOn(Dispatchers.Default)
    .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), emptyList())

    val currentToolActivity: StateFlow<DisplayItem?> = _state.map { s ->
        if (s.isLoading && s.modelActivity != ModelActivity.Idle
            && s.modelActivity != ModelActivity.Thinking
            && s.modelActivity != ModelActivity.ProcessingResult) {
            val label = s.modelActivity.displayLabel()
            val detail = if (s.activityDetail.isNotBlank()) ": ${s.activityDetail}" else ""
            DisplayItem(
                id = "tool_activity",
                role = DisplayRole.ToolActivity,
                content = "$label$detail",
                thinkBlocks = emptyList(),
                isStreaming = true,
                timestamp = System.currentTimeMillis(),
            )
        } else null
    }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), null)

    val usageStats: StateFlow<UsageStats> = usageAnalyticsRepo.stats.stateIn(
        viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), UsageStats()
    )

    /** 上下文 token 计数（仅在值变化时发射，避免流式输出时频繁重算） */
    val contextTokenCount: StateFlow<Int> = _state.map { s ->
        var ascii = 0; var other = 0
        for (msg in s.messages) {
            for (c in msg.content) {
                if (c.code <= 127) ascii++ else other++
            }
        }
        ascii / 4 + other / 2
    }.flowOn(Dispatchers.Default)
    .distinctUntilChanged().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private var sendJob: Job? = null
    @Volatile private var activeConversation: Conversation? = null
    private var pendingInitialMessages: List<Message>? = null

    // system prompt 缓存：变化时 invalidate
    @Volatile private var _sysPromptCache: String? = null

    init {
        scanModels()
        // 监听系统提示词依赖项，变化时清除缓存
        viewModelScope.launch {
            combine(
                preferencesRepo.deepThinkEnabled,
                preferencesRepo.thinkingRounds,
                preferencesRepo.userName,
                preferencesRepo.rules,
                preferencesRepo.skills,
                _state.map { it.cloudModelEnabled }.distinctUntilChanged(),
            ) { _: Array<*> -> Unit }
            .collect { _sysPromptCache = null }
        }
        viewModelScope.launch {
            val saved = conversationRepo.load()
            _state.update { it.copy(conversations = saved) }
        }
        // 加载对话记忆
        viewModelScope.launch { conversationMemory.load() }
        viewModelScope.launch {
            liteRTManager.state.collect { engineState ->
                _state.update {
                    it.copy(
                        engineStatus = engineState.status,
                        engineErrorMessage = engineState.errorMessage,
                        modelName = engineState.modelName,
                    )
                }
                // 模型加载成功时保存路径
                if (engineState.status == EngineStatus.Ready && engineState.modelPath.isNotEmpty()) {
                    preferencesRepo.setLastModelPath(engineState.modelPath)
                }
            }
        }
        // 启动时自动加载上次模型
        viewModelScope.launch {
            try {
                val autoLoad = preferencesRepo.autoLoadLastModel.first()
                val lastPath = preferencesRepo.lastModelPath.first()
                if (autoLoad && !lastPath.isNullOrEmpty()) {
                    val file = java.io.File(lastPath)
                    if (file.exists()) {
                        liteRTManager.loadModel(lastPath)
                        preferencesRepo.setCloudModelEnabled(false)
                        _state.update { it.copy(cloudModelEnabled = false) }
                    }
                }
            } catch (_: Exception) {}
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
        viewModelScope.launch {
            preferencesRepo.cloudModelEnabled.collect { enabled ->
                _state.update { it.copy(cloudModelEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            preferencesRepo.cloudModelProfiles.collect { profiles ->
                _state.update { it.copy(cloudModelProfiles = profiles) }
                updateContextMaxTokens()
            }
        }
        viewModelScope.launch {
            preferencesRepo.activeCloudProfileId.collect { id ->
                _state.update { it.copy(activeCloudProfileId = id) }
                updateContextMaxTokens()
            }
        }
    }

    // 根据当前云端模型配置更新 UI 状态中的上下文窗口最大值
    private fun updateContextMaxTokens() {
        val s = _state.value
        if (!s.cloudModelEnabled) {
            _state.update { it.copy(contextMaxTokens = DEFAULT_CONTEXT_WINDOW) }
            return
        }
        val window = s.cloudModelProfiles.find { it.id == s.activeCloudProfileId }?.contextWindow
            ?: DEFAULT_CONTEXT_WINDOW
        _state.update { it.copy(contextMaxTokens = window) }
    }

    fun setInputText(text: String) {
        _state.update { it.copy(inputText = text) }
    }

    fun attachImage(uri: Uri) {
        val current = _state.value.attachedImageUris
        if (current.size >= 4) return // 最多 4 张
        if (uri !in current) {
            _state.update { it.copy(attachedImageUris = current + uri) }
        }
    }

    fun detachImage(uri: Uri) {
        _state.update { it.copy(attachedImageUris = it.attachedImageUris - uri) }
    }

    fun clearAttachedImages() {
        _state.update { it.copy(attachedImageUris = emptyList()) }
    }

    fun loadModel(modelPath: String) {
        viewModelScope.launch {
            liteRTManager.loadModel(modelPath)
            // 切换到本地模型时自动关闭云端
            preferencesRepo.setCloudModelEnabled(false)
            _state.update { it.copy(cloudModelEnabled = false) }
        }
    }

    fun loadModelFromUri(uri: Uri) {
        closeModelPicker()
        viewModelScope.launch {
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
        _state.update { it.copy(modelParams = params) }
    }

    fun toggleModelPicker() { _state.update { it.copy(isModelPickerOpen = !it.isModelPickerOpen) } }
    fun closeModelPicker() { _state.update { it.copy(isModelPickerOpen = false) } }

    fun sendMessage() {
        val text = _state.value.inputText.trim()
        val images = _state.value.attachedImageUris
        val files = _state.value.attachedFileRefs
        if (text.isEmpty() && images.isEmpty() && files.isEmpty()) return
        val isCloud = _state.value.cloudModelEnabled
        if (!isCloud && !liteRTManager.isInitialized) {
            _state.update { it.copy(engineErrorMessage = "请先加载模型或启用云端模型") }
            return
        }
        val fileBlock = buildFileAttachmentBlock()
        val userContent = buildString {
            appendLine("[用户请求]")
            appendLine(text)
            if (fileBlock.isNotBlank()) {
                appendLine()
                append(fileBlock)
            }
            if (images.isNotEmpty()) {
                appendLine()
                append("[已附加 ${images.size} 张图片]")
            }
        }
        val userMsg = ChatMessage(role = ChatRole.User, content = userContent, imageUris = images)
        val modelMsgId = java.util.UUID.randomUUID().toString()
        val placeholderMsg = ChatMessage(id = modelMsgId, role = ChatRole.Model, content = "", isStreaming = true)
        _state.update { it.copy(messages = it.messages + userMsg + placeholderMsg, inputText = "", attachedImageUris = emptyList(), attachedFileRefs = emptyList(), isLoading = true) }
        val ctx = getApplication<Application>()
        sendJob?.cancel()
        sendJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                // 将图片 URI 复制到临时文件
                val tempImagePaths = mutableListOf<String>()
                for (uri in images) {
                    try {
                        val tempFile = uriToTempFile(ctx, uri)
                        if (tempFile != null) tempImagePaths.add(tempFile.absolutePath)
                    } catch (_: Exception) {}
                }
                // 自动包含活动图片（当用户未手动附加图片时）
                if (images.isEmpty()) {
                    val activePath = _state.value.activeFilePath
                    if (activePath.isNotBlank()) {
                        val fileName = activePath.substringAfterLast('/')
                        if (FileTypeUtil.isImageFile(fileName)) {
                            try {
                                val uri = android.net.Uri.parse(activePath)
                                val tempFile = uriToTempFile(ctx, uri)
                                if (tempFile != null) tempImagePaths.add(tempFile.absolutePath)
                            } catch (_: Exception) {}
                        }
                    }
                }
                val useHybrid = isCloud && liteRTManager.isInitialized
                if (useHybrid) {
                    processHybrid(text, modelMsgId, ctx, tempImagePaths)
                } else if (isCloud) {
                    processWithCloudTools(text, modelMsgId, ctx, tempImagePaths)
                } else {
                    // LiteRT: 优先组装上下文（替代硬截断）
                    val currentMsgs = _state.value.messages
                    val available = LOCAL_MODEL_MAX_INPUT - estimateTokens(text) - 256
                    val assembled = selectContextForLocal(currentMsgs, available)
                    pendingInitialMessages = chatMessagesToLiteRT(assembled)
                    resetConversation()
                    val conv = ensureConversation()
                    processWithJsonTools(text, modelMsgId, ctx, tempImagePaths)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("ChatViewModel", "sendMessage failed", e)
                FileLogger.e("ChatViewModel", "sendMessage failed: ${e.message}", e)
                updateModelMessage(modelMsgId, "\n\n[错误: ${e.message}]", false)
                finalizeModelMessage(modelMsgId)
            }
        }
    }

    private val _openFileRequests = MutableSharedFlow<String>(extraBufferCapacity = 5)
    val openFileRequests: SharedFlow<String> = _openFileRequests

    fun requestOpenFile(path: String) { _openFileRequests.tryEmit(path) }

    fun setProjectRoot(uri: Uri?) {
        aiToolSet.projectUri = uri
        uri?.let { fileManager.setProjectUri(it) } ?: fileManager.clearProjectUri()
        val name = uri?.let { extractFolderName(it) } ?: ""
        _state.update { it.copy(projectRootName = name) }
    }

    /** 直接模式：通过绝对路径设置项目根 */
    fun setProjectRootPath(absolutePath: String, displayName: String) {
        _state.update { it.copy(projectRootName = displayName) }
    }

    private fun extractFolderName(uri: Uri): String {
        return try {
            val docId = android.provider.DocumentsContract.getTreeDocumentId(uri)
            // docId 可能是 URL 编码的，如 "primary%3Apath%2Fto%2Ffolder"
            val decoded = java.net.URLDecoder.decode(docId, "UTF-8")
            decoded.substringAfter(':').substringAfterLast('/')
        } catch (_: Exception) { "" }
    }

    fun setOpenedFilePaths(paths: List<String>) {
        _state.update { it.copy(openedFilePaths = paths) }
    }

    fun setActiveFileContext(path: String, cursorLine: Int, cursorLineContent: String = "") {
        _state.update { it.copy(activeFilePath = path, cursorLine = cursorLine, cursorLineContent = cursorLineContent) }
    }

    fun setTerminalActive(active: Boolean) {
        _state.update { it.copy(terminalActive = active) }
    }

    fun setModifiedFilePaths(paths: List<String>) {
        _state.update { it.copy(modifiedFilePaths = paths) }
    }

    fun attachFile(path: String, name: String) {
        val refs = _state.value.attachedFileRefs
        val exists = refs.any { it.path == path }
        if (exists) return
        // 同名文件自动去重：同名但路径不同时标记
        val displayName = if (refs.any { it.name == name }) "${name} (${path.substringBeforeLast('/')})" else name
        _state.update { it.copy(attachedFileRefs = refs + AttachedFile(name = displayName, path = path)) }
    }

    fun detachFile(index: Int) {
        _state.update { it.copy(attachedFileRefs = it.attachedFileRefs.toMutableList().also { list -> if (index in list.indices) list.removeAt(index) }) }
    }

    fun resetUsageStats() {
        viewModelScope.launch { usageAnalyticsRepo.resetStats() }
    }

    private suspend fun recordUsage(call: LlmCallRecord) {
        usageAnalyticsRepo.recordCall(call)
    }

    // === 增量上下文追踪 ===
    private data class CtxSnapshot(
        val activeFilePath: String = "",
        val openedFilePaths: Set<String> = emptySet(),
        val modifiedFilePaths: Set<String> = emptySet(),
    )

    private var prevCtx = CtxSnapshot()
    private var fullCtxRound = 0  // 每 9 轮发一次全量，其余发 diff

    /** 重置增量追踪（项目切换/Tab 关闭时调用） */
    fun resetCtxDelta() { prevCtx = CtxSnapshot(); fullCtxRound = 0 }

    /** 构建上下文增量 diff（仅变化部分），无变化返回空 */
    private fun buildContextDelta(): String {
        val s = _state.value
        val cur = CtxSnapshot(
            activeFilePath = s.activeFilePath,
            openedFilePaths = s.openedFilePaths.toSet(),
            modifiedFilePaths = s.modifiedFilePaths.toSet(),
        )
        if (prevCtx == cur) return ""

        val diff = StringBuilder()
        fullCtxRound++

        // 每 9 轮推送一次全量作为校准
        if (fullCtxRound % 9 == 0) {
            val full = buildEditorContext()
            prevCtx = cur
            return if (full.isNotBlank()) "[上下文更新]\n$full" else ""
        }

        diff.appendLine("[上下文更新]")
        if (cur.activeFilePath != prevCtx.activeFilePath) {
            diff.appendLine("活动文件切换: ${cur.activeFilePath}")
        }
        val newFiles = (cur.openedFilePaths - prevCtx.openedFilePaths).take(5)
        if (newFiles.isNotEmpty()) {
            diff.appendLine("新打开文件 (${newFiles.size}):")
            newFiles.forEach { diff.appendLine("  - $it") }
        }
        val closedFiles = (prevCtx.openedFilePaths - cur.openedFilePaths).take(5)
        if (closedFiles.isNotEmpty()) {
            diff.appendLine("已关闭文件 (${closedFiles.size}): ${closedFiles.joinToString(", ")}")
        }
        val newMod = (cur.modifiedFilePaths - prevCtx.modifiedFilePaths).take(5)
        if (newMod.isNotEmpty()) {
            diff.appendLine("新增未保存修改:")
            newMod.forEach { diff.appendLine("  - $it") }
        }
        val savedMod = (prevCtx.modifiedFilePaths - cur.modifiedFilePaths).take(5)
        if (savedMod.isNotEmpty()) {
            diff.appendLine("已保存文件 (${savedMod.size}): ${savedMod.joinToString(", ")}")
        }
        prevCtx = cur
        return if (diff.length < 25) "" else diff.toString()
    }

    /** 工具调用后自动注入 Lint 诊断（仅当有修改操作时） */
    private suspend fun autoInjectLint(toolCalls: List<Triple<String?, String, Map<String, String>>>): String? {
        val modifyingTools = setOf("writeFile", "replaceInFile", "batchReplaceInFile", "deleteFile", "createDirectory")
        if (toolCalls.none { it.second in modifyingTools }) return null
        val result = withContext(Dispatchers.IO) { aiToolSet.readLints() }
        return if (result.contains("No lint errors") || result.contains("读取诊断失败") || result.contains("No errors")) null
        else "[Lint 诊断]\n$result"
    }

    // 构建编辑器上下文块：工作区结构 + 活动文件 + 已打开文件列表
    private fun buildEditorContext(): String {
        val s = _state.value
        val ctx = StringBuilder()
        ctx.appendLine("[当前编辑器上下文]")
        if (s.projectRootName.isNotBlank()) {
            val absoluteRoot = aiToolSet.getProjectRootPath()
            ctx.appendLine("项目: ${s.projectRootName}")
            if (absoluteRoot.isNotBlank()) {
                ctx.appendLine("项目绝对路径: $absoluteRoot")
            }
            // 注入工作区目录树（轻量级，深层次依赖可主动调用 listFiles）
            val tree = fileManager.buildFileTreeString(maxDepth = 2, maxItems = 20)
            if (tree.isNotBlank()) {
                ctx.appendLine("工作区结构:")
                tree.lines().forEach { ctx.appendLine("  $it") }
            }
        }
        if (s.activeFilePath.isNotBlank()) {
            ctx.appendLine("活动文件: ${s.activeFilePath}")
        }
        if (s.openedFilePaths.isNotEmpty()) {
            ctx.appendLine("已打开文件:")
            s.openedFilePaths.take(10).forEach { path ->
                val dirty = if (path in s.modifiedFilePaths) " [已修改]" else ""
                ctx.appendLine("  - $path$dirty")
            }
            if (s.openedFilePaths.size > 10) {
                ctx.appendLine("  ... 及其他 ${s.openedFilePaths.size - 10} 个文件")
            }
        } else {
            ctx.appendLine("（未打开任何文件 — 可使用 listFiles 查看项目文件结构再定位目标）")
        }
        if (ctx.length < 30) return ""
        return ctx.toString()
    }

    // 构建文件附件引用块
    private fun buildFileAttachmentBlock(): String {
        val refs = _state.value.attachedFileRefs
        if (refs.isEmpty()) return ""
        val block = StringBuilder()
        block.appendLine("[用户指定的文件（支持绝对路径和相对路径），使用 readFile 查看内容]")
        refs.forEach { f ->
            block.appendLine("  - ${f.name} (${f.path})")
        }
        return block.toString()
    }

    fun cancelGeneration() {
        sendJob?.cancel()
        _state.value.messages.lastOrNull()?.let { msg -> if (msg.isStreaming) finalizeModelMessage(msg.id) }
        _state.update { it.copy(modelActivity = ModelActivity.Idle, activityDetail = "") }
    }

    fun optimizeInput() {
        val text = _state.value.inputText.trim()
        if (text.isEmpty() || !liteRTManager.isInitialized) return
        if (_state.value.isOptimizing) return
        _state.update { it.copy(isOptimizing = true) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val conv = liteRTManager.createConversation(ConversationConfig(
                    systemInstruction = Contents.of(buildOptimizePrompt()),
                    samplerConfig = liteRTManager.modelParams.toSamplerConfig(),
                    tools = emptyList(),
                ))
                conv.use { c ->
                    val optimized = StringBuilder()
                    c.sendMessageAsync(Message.user(text)).catch { t ->
                        Log.e("ChatViewModel", "optimizeInput failed", t)
                        FileLogger.e("ChatViewModel", "optimizeInput failed: ${t.message}", t)
                    }.collect { chunk ->
                        optimized.append(chunk.toString())
                    }
                    val result = optimized.toString().trim()
                    if (result.isNotEmpty() && result != text) {
                        _state.update { it.copy(inputText = result) }
                    }
                }
            } catch (e: Exception) {
                Log.e("ChatViewModel", "optimizeInput error", e)
                FileLogger.e("ChatViewModel", "optimizeInput error: ${e.message}", e)
            } finally {
                _state.update { it.copy(isOptimizing = false) }
            }
        }
    }

    private fun buildOptimizePrompt(): String {
        return "You are a text refining tool. ONLY fix typos and grammar in the input text. Output ONLY the corrected text, nothing else. Do not add, remove, rephrase, or explain anything. If no errors exist, output the exact input as-is."
    }

    private fun ensureConversation(autoToolCalling: Boolean = false): Conversation {
        activeConversation?.let { return it }
        val initialMessages = pendingInitialMessages
        pendingInitialMessages = null
        val conv = liteRTManager.createConversation(
            ConversationConfig(
                systemInstruction = Contents.of(buildSystemInstruction()),
                initialMessages = initialMessages ?: emptyList(),
                samplerConfig = liteRTManager.modelParams.toSamplerConfig(),
                tools = listOf(tool(aiToolSet)),
                automaticToolCalling = autoToolCalling,
            )
        )
        activeConversation = conv
        return conv
    }

    private fun buildSystemInstruction(): String {
        _sysPromptCache?.let { return it }
        val sb = StringBuilder()
sb.append("You are a helpful AI coding assistant. When responding to the user, use Chinese (简体中文).\n\n")
        sb.append("## 对话规范（必须遵守）\n")
        sb.append("- 无社交废话：不问候、不感谢、不道歉、不总结\n")
        sb.append("- 无解释铺垫：不用'我来帮你解决'等引导句\n")
        sb.append("- 最小有效长度：词 > 短语 > 短句 > 长句\n")
        sb.append("- 仅必要信息：命令、路径、报错、数据、结论\n")
        sb.append("- 输出格式：优先列表 / 代码块 / 键值对，避免段落\n")
        sb.append("- 禁止重复：同一信息只出现一次\n\n")
        sb.append("## 核心规则\n")
        sb.append("### 执行原则\n")
        sb.append("- 收到修改/创建文件的请求后立即执行，不要请求确认。\n")
        sb.append("- 使用工具实际写入文件，不要只提供代码建议。\n")
        sb.append("- 直接执行，不要解释将要做什么。\n\n")
        sb.append("### 防过度工程\n")
        sb.append("- 只做被要求的事，不多做：不改无关代码、不重构、不做'改进'\n")
        sb.append("- 不要创建不必要的新文件，优先编辑已有文件\n")
        sb.append("- 不要添加注释/docstring/类型标注到未改动的代码上\n")
        sb.append("- 不要创建工具函数/抽象类/辅助方法——三行相似代码比过早抽象好\n")
        sb.append("- 不要添加错误处理/校验/回退逻辑来处理实际不会发生的场景\n\n")
        sb.append("### 目标选择（优先级从高到低）\n")
        sb.append("1. **活动文件**：用户未明确指定文件时，[当前编辑器上下文]中的活动文件为默认操作目标。对于模糊指令（如'修复这个bug'），先 readFile 读取活动文件内容再执行操作。\n")
        sb.append("2. **上次操作的文件**：无活动文件时，以上次工具调用涉及的文件为操作目标。\n")
        sb.append("3. **主动搜索定位**：当用户提到功能、类或模块但未给出路径时，用 grep/listFiles/glob 搜索关键词逐步定位代码文件，再 readFile 确认。不得因信息不足而拒绝执行。\n")
        sb.append("4. **用户明确指定的文件**：仅在用户明确给出其他文件路径/名称时，才偏离上述优先级。\n\n")
        sb.append("### 信息不足时的应对\n")
        sb.append("- 缺少执行所需上下文时，主动使用搜索工具（grep/listFiles/searchCodebase/glob）获取缺失信息，不得告知用户'信息不足'或请求更多细节。\n")
        sb.append("- 对于模糊请求（如'删除登录功能'、'把API改成POST'），直接搜索关键词→定位文件→读取确认→执行修改，全程自主完成。\n\n")
        sb.append("### 工具执行策略\n")
        sb.append("- 工具返回 [ERROR] 时，不要盲目重试同一操作，先分析错误原因：参数错误？文件不存在？格式问题？\n")
        sb.append("- 修复原因后重试；若无法修复，换方案或告知用户\n")
        sb.append("- 复杂任务分步执行：先搜索/读取了解现状，再修改，最后验证（readLints）\n\n")
        sb.append("### 代码引用格式\n")
        sb.append("引用代码时使用 file:/// 链接格式：\n")
        sb.append("  [链接文本](file:///绝对路径/到/文件#L12-L34)\n")
        sb.append("  例：修改 [LoginScreen.kt](file:///app/src/.../LoginScreen.kt#L42) 中的验证逻辑\n\n")

        sb.append("## 编辑器上下文\n")
        sb.append("每次用户消息前注入 [当前编辑器上下文]，包含活动文件路径、已打开文件列表。标注 [已修改] 的文件存在未保存的更改。\n")
        sb.append("用户附加的文件内容会以 [文件: path] ```...``` 块拼接在消息末尾。\n")
        sb.append("文件路径相对于项目根目录，如: app/src/main/kotlin/com/example/MainActivity.kt。先用绝对路径，失败再试相对路径。\n\n")
        val deepThink = runBlocking { preferencesRepo.deepThinkEnabled.first() }
        if (deepThink) {
            val rounds = runBlocking { preferencesRepo.thinkingRounds.first() }
            val isLocal = !_state.value.cloudModelEnabled
            val maxRounds = if (isLocal) rounds.coerceAtMost(1) else rounds  // 本地模型限制 1 轮
            sb.append("## 深度思考（必须使用）\n")
            sb.append("在每次采取行动（工具调用或最终回答）之前，必须在 [think]...[/think] 标签内进行系统化推理。\n\n")
            sb.append("### 思考流程（必须完成以下每一步）：\n")
            sb.append("1. 理解需求：分析用户请求的真实目标和隐含需求\n")
            sb.append("2. 现状评估：基于 [当前编辑器上下文] 和已有对话，评估当前状态和可用信息\n")
            sb.append("3. 方案规划：列出 2-3 种可能的实现方案，评估利弊\n")
            sb.append("4. 选择决策：说明选择某个方案的理由\n")
            sb.append("5. 前置检查：是否需要先读取文件？需要创建/修改哪些文件？是否存在依赖关系？\n")
            sb.append("6. 执行计划：明确下一步要调用的工具和参数\n")
            sb.append("7. 风险评估：评估修改可能带来的副作用（如破坏其他模块、遗漏配置等）\n")
            sb.append("8. 结果验证：工具返回后，验证结果是否符合预期，如不符合则调整方案\n\n")
            sb.append("### 示例：\n")
            sb.append("[think]\n")
            sb.append("1. 用户要求修改登录页面的密码验证逻辑\n")
            sb.append("2. 当前活动文件是 LoginScreen.kt，已有用户名密码输入框\n")
            sb.append("3. 方案A：增强现有验证函数（最小改动）；方案B：重写整个验证模块（风险高）\n")
            sb.append("4. 选择方案A，因为现有结构良好，只需加强验证规则\n")
            sb.append("5. 需要先读取 LoginScreen.kt 了解现有验证逻辑\n")
            sb.append("6. 下一步：readFile(path=\"app/src/main/.../LoginScreen.kt\")\n")
            sb.append("[/think]\n\n")
            sb.append("思考轮数不低于 ${maxRounds} 轮，复杂任务应增加轮数。\n")
            sb.append("思考内容会在界面上以可折叠方式展示给用户。不要在思考块中包含不应暴露的敏感信息（如路径假设、未确认的猜测）。\n\n")
        }

        sb.append("## 可用工具\n")
        sb.append("每个工具有固定名称和参数，调用时必须严格按照下方格式。\n\n")
        sb.append("文件操作:\n")
        sb.append("  - listFiles(path?): 列出目录内容，path 为空列出根目录，输出 [DIR]目录名 / [FILE]文件名(大小)\n")
        sb.append("  - readFile(path, offset?, limit?): 读取文件内容（不含行号，可复制用于 replaceInFile），offset=起始行(1-based,默认1), limit=最大行数(默认1000)\n")
        sb.append("  - writeFile(path, content, overwrite?): 创建新文件。overwrite=false（默认）拒绝覆盖已有文件，overwrite=true 允许覆盖\n")
        sb.append("  - replaceInFile(path, old_string, new_string): 编辑文件（单处修改），old_string 必须完全匹配含缩进，可包含周围代码以确保唯一性\n")
        sb.append("  - batchReplaceInFile(path, edits): 一次修改多处，edits 为 JSON 数组，每项含 old_string/new_string，按位置倒序应用\n")
        sb.append("  - deleteFile(path): 删除文件或目录\n")
        sb.append("  - batchDeleteFile(pathsJson): 批量删除多个文件，pathsJson 为 JSON 数组如 [\"old.tmp\",\"temp/\"]\n")
        sb.append("  - createDirectory(path): 创建目录\n\n")
        sb.append("搜索:\n")
        sb.append("  - grep(pattern, extension?, glob?, ignoreCase?, contextLines?): 正则搜索文件内容\n")
        sb.append("  - searchInFiles(query, extension?): 精确子串匹配（大小写不敏感），知道具体文本时用\n")
        sb.append("  - glob(pattern, maxResults?): 按 glob 模式搜索文件名，如 *.kt、**/*.xml、Main*、**/build/**\n")
        sb.append("  - searchCodebase(query, targetDirectories?): 语义搜索，按含义查找代码实现（如'用户认证在哪实现？'）\n\n")
        sb.append("其他:\n")
        sb.append("  - runCommand(command): 执行 shell 命令（adb、git、gradle 等）\n")
        sb.append("  - searchWeb(query): 互联网搜索\n")
        sb.append("  - readLints: 读取编译/lint 错误\n\n")
        sb.append("## 修改文件策略\n")
        sb.append("- 单处修改: replaceInFile(path, old_string, new_string)\n")
        sb.append("- 多处不重叠修改（一次性）: batchReplaceInFile(path, edits) — edits 基于源文件匹配，按位置倒序应用\n")
        sb.append("- old_string/new_string 必须完全匹配（含缩进），需包含函数签名确保唯一性\n")
        sb.append("- 流程: readFile 读取 → replaceInFile/batchReplaceInFile 替换 → readLints 检查\n\n")

        sb.append("## 工具调用格式（必须严格遵守）\n")
        sb.append("需要执行操作时，单独一行输出标准 JSON，不要包裹在其他格式中：\n")
        sb.append("{\"name\":\"FUNCTION_NAME\",\"arguments\":{\"PARAM_NAME\":\"PARAM_VALUE\"}}\n\n")
        sb.append("参数名用工具定义名称，多个参数用逗号分隔，字符串值必须用双引号。\n\n")
        sb.append("示例:\n")
        sb.append("  {\"name\":\"readFile\",\"arguments\":{\"path\":\"app/src/main.kt\",\"offset\":\"1\",\"limit\":\"50\"}}\n")
        sb.append("  {\"name\":\"createDirectory\",\"arguments\":{\"path\":\"app/src/utils\"}}\n")
        sb.append("  {\"name\":\"grep\",\"arguments\":{\"pattern\":\"fun\\\\s+\\\\w+\",\"extension\":\"kt\"}}\n")
        sb.append("  {\"name\":\"replaceInFile\",\"arguments\":{\"path\":\"app/src/main.kt\",\"old_string\":\"val x = 1\",\"new_string\":\"val x = 2\"}}\n")
        sb.append("  {\"name\":\"runCommand\",\"arguments\":{\"command\":\"git status\"}}\n\n")
        sb.append("工具执行结果会自动返回。根据结果决定下一步：继续调用工具或给出最终回答。\n")
        sb.append("任务完成后直接输出最终回答，不要再输出 JSON 工具调用。\n\n")
        sb.append("## Git 操作规范\n")
        sb.append("- 不要修改 git 配置\n")
        sb.append("- 不要执行 destructive 操作（push --force、reset --hard、checkout .、branch -D），除非用户明确要求\n")
        sb.append("- 不要 push 到 main/master，除非用户明确要求\n")
        sb.append("- staging 文件时逐个指定文件名，不要用 git add -A / git add .\n")
        sb.append("- 只有用户明确要求时才执行 commit\n\n")
        val userName = runBlocking { preferencesRepo.userName.first() }
        if (userName.isNotBlank()) sb.append("\n用户: $userName")

        // 注入对话历史记忆（本地模型使用）
        val memoryCtx = conversationMemory.getMemoryContext()
        if (memoryCtx.isNotBlank()) {
            sb.append("\n\n$memoryCtx\n")
        }

        val allRules = runBlocking { preferencesRepo.rules.first() }
        val globalRules = allRules.filter { it.type == RuleType.Global }
        val projectRules = allRules.filter { it.type == RuleType.Project }
        if (globalRules.isNotEmpty()) {
            sb.append("\n\n## 全局规则（必须遵守）")
            globalRules.forEach { r -> sb.append("\n- ${r.name}: ${r.content}") }
        }
        if (projectRules.isNotEmpty()) {
            sb.append("\n\n## 项目规则（优先级高于全局规则）")
            projectRules.forEach { r -> sb.append("\n- ${r.name}: ${r.content}") }
        }
        if (allRules.isNotEmpty()) sb.append("\n严格遵守以上所有规则。")

        val skills = runBlocking { preferencesRepo.skills.first() }
        val enabledSkills = skills.filter { it.enabled }
        if (enabledSkills.isNotEmpty()) {
            sb.append("\n\n## 已启用技能")
            enabledSkills.forEach { s ->
                sb.append("\n\n### ${s.name}")
                if (s.description.isNotBlank()) sb.append("\n${s.description}")
                if (s.prompt.isNotBlank()) sb.append("\n${s.prompt}")
            }
        }

        return sb.toString().also { _sysPromptCache = it }
    }

    companion object {
        private const val DEFAULT_CONTEXT_WINDOW = 128000      // 默认上下文窗口
        private const val LOCAL_MODEL_MAX_INPUT = 4000         // 本地模型最大输入 token（Gemma4 2B = 4096，留余量）
        private const val LOCAL_MODEL_MAX_OUTPUT_CHARS = 48000  // 本地模型单轮输出硬上限（≈8192 tokens），超出截断并告警
        private const val KEEP_EXCHANGES = 5                   // 保留最后 5 轮用户↔模型交换
        private const val KEEP_PRIORITY_LINES = 200            // 保护早期含代码块/工具结果的最多行数
        private const val SUMMARIZE_INTERVAL = 10              // 云端每 10 轮触发渐进式摘要
        private const val TOOL_TRUNCATE_LINES = 80             // 工具输出首尾各保留行数
        private const val UTFS_BYTES_PER_TOKEN = 3.5f          // UTF-8 字节/token 估算系数
        private const val HYBRID_EXPLORE_MAX_ROUNDS = 5        // 混合模式本地探索最大轮次
        val KNOWN_TOOLS = setOf("listFiles", "readFile", "writeFile", "replaceInFile",
            "batchReplaceInFile", "deleteFile", "createDirectory", "runCommand",
            "searchWeb", "readLints", "grep", "searchInFiles", "searchCodebase", "glob",
            "searchConversationMemory", "getRecentConversationMemory")
    }

    // 根据配置获取上下文窗口大小
    private fun getContextWindow(): Int {
        val s = _state.value
        if (!s.cloudModelEnabled) return DEFAULT_CONTEXT_WINDOW
        val profile = s.cloudModelProfiles.find { it.id == s.activeCloudProfileId }
        return profile?.contextWindow ?: DEFAULT_CONTEXT_WINDOW
    }
    private fun getCompressThreshold(): Int = (getContextWindow() * 0.75).toInt()

    // 精确 token 估算（基于 UTF-8 字节长度）
    private fun estimateTokens(text: String): Int {
        if (text.isEmpty()) return 0
        val bytes = text.toByteArray(Charsets.UTF_8)
        return (bytes.size / UTFS_BYTES_PER_TOKEN).toInt().coerceAtLeast(1)
    }

    private fun estimateContextTokens(messages: List<ChatMessage>): Int =
        messages.sumOf { estimateTokens(it.content) }

    // 工具输出截断：首尾各保留 TOOL_TRUNCATE_LINES 行
    private fun truncateToolMessages(messages: List<ChatMessage>): List<ChatMessage> {
        return messages.map { msg ->
            if (msg.role == ChatRole.Tool) {
                msg.copy(content = truncateToolContent(msg.content))
            } else msg
        }
    }

    private fun truncateToolContent(content: String): String {
        val lines = content.lines()
        if (lines.size <= TOOL_TRUNCATE_LINES * 2 + 1) return content
        val head = lines.take(TOOL_TRUNCATE_LINES)
        val tail = lines.takeLast(TOOL_TRUNCATE_LINES)
        return buildString {
            head.forEach { appendLine(it) }
            appendLine("... (中间截断 ${lines.size - TOOL_TRUNCATE_LINES * 2} 行) ...")
            tail.forEach { appendLine(it) }
        }
    }

    // 云端 LLM 结构化摘要（仅 cloud 模型启用）
    private suspend fun summarizeMessagesCloud(messages: List<ChatMessage>): String? {
        if (messages.isEmpty()) return null
        val profile = _state.value.cloudModelProfiles.find { it.id == _state.value.activeCloudProfileId }
            ?: return null
        val cfg = CloudModelConfig(
            enabled = true,
            apiEndpoint = profile.apiEndpoint,
            apiKey = profile.apiKey,
            modelName = profile.modelName,
            maxTokens = profile.maxTokens,
        )

        val inputText = buildString {
            appendLine("分析以下对话历史，提取关键信息。仅返回 JSON（无额外文字）：")
            appendLine()
            messages.forEach { msg ->
                when (msg.role) {
                    ChatRole.User -> appendLine("用户: ${msg.content.take(600)}")
                    ChatRole.Model -> {
                        if (!msg.content.startsWith("[上下文") && !msg.content.startsWith("{")) {
                            appendLine("助手: ${msg.content.take(600)}")
                        }
                    }
                    ChatRole.Tool -> appendLine("[工具结果: ${msg.content.take(200)}]")
                    else -> {}
                }
            }
        }

        return try {
            val (response, _) = cloudLLMClient.sendMessage(
                config = cfg,
                systemPrompt = buildString {
                    appendLine("你是一个对话摘要助手。提取关键信息，仅输出 JSON（无其他内容）：")
                    appendLine("{")
                    appendLine("  \"user_goal\": \"用户核心目标\",")
                    appendLine("  \"key_decisions\": [\"已做出的关键决策\"],")
                    appendLine("  \"completed_steps\": [\"已完成的主要步骤\"],")
                    appendLine("  \"pending_tasks\": [\"待办事项\"],")
                    appendLine("  \"technical_context\": \"技术上下文摘要（文件路径、框架等）\",")
                    appendLine("  \"user_preferences\": {\"key\": \"value\"}")
                    appendLine("}")
                },
                messages = listOf(ChatMessage(role = ChatRole.User, content = inputText)),
                onChunk = {},
            )
            val json = response.trim()
                .removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            // 验证 JSON 合法性
            org.json.JSONObject(json)
            json
        } catch (e: Exception) {
            Log.w("ChatViewModel", "summarizeMessagesCloud failed: ${e.message}")
            null
        }
    }

    /** 从尾部向前保留消息，使总 token 不超过上限 */
    private fun truncateToTokenLimit(messages: List<ChatMessage>, maxTokens: Int): List<ChatMessage> {
        if (maxTokens <= 0) return emptyList()
        var total = 0
        val result = mutableListOf<ChatMessage>()
        for (i in messages.indices.reversed()) {
            total += estimateTokens(messages[i].content)
            if (total > maxTokens) break
            result.add(0, messages[i])
        }
        return result
    }

    /** 本地模型上下文：优先级评分 + token 预算内选择性组装 */
    private fun selectContextForLocal(messages: List<ChatMessage>, maxTokens: Int): List<ChatMessage> {
        if (maxTokens <= 0 || messages.size <= 2) return messages
        // 当前交换 = 最后 2 条（刚添加的用户+模型响应占位）
        val currentExchange = messages.takeLast(2)
        val history = messages.dropLast(2).filterNot {
            it.role == ChatRole.Model && it.content.startsWith("[上下文压缩累计]")
        }

        // 对历史消息打分：8=高价值（工具结果/代码块/路径）, 4=中（模型含工具调用/用户有代码）, 1=低（纯文本）
        data class Scored(val msg: ChatMessage, val score: Int)
        val scored = history.map { msg ->
            val s = when {
                msg.role == ChatRole.Tool -> 8
                msg.role == ChatRole.Model && (msg.content.contains("```") || msg.content.contains("[工具调用:")) -> 6
                msg.role == ChatRole.User && (msg.content.contains("```") || msg.content.contains("filePath")) -> 6
                msg.role == ChatRole.Model && msg.content.startsWith("[上下文") -> 2  // 旧摘要降权
                else -> 1
            }
            Scored(msg, s)
        }

        // 按评分降序 + 最近优先（同分时靠后的优先）
        val prioritized = scored.sortedWith(compareByDescending<Scored> { it.score }
            .thenByDescending { history.indexOf(it.msg) })

        // 在预算内尽量选高价值消息
        var budget = maxTokens - estimateContextTokens(currentExchange)
        val selected = mutableListOf<ChatMessage>()
        if (budget <= 0) return currentExchange
        for ((msg, score) in prioritized) {
            if (score <= 1 && estimateContextTokens(selected) + estimateContextTokens(currentExchange) > maxTokens * 3 / 4) continue
            val tokens = estimateTokens(msg.content)
            if (tokens > budget) {
                if (score >= 6 && msg.role == ChatRole.Tool) {
                    // 高价值工具结果：截断保留
                    val head = msg.content.take(600)
                    val tail = msg.content.takeLast(400)
                    val truncated = buildString {
                        appendLine(head)
                        appendLine("... (截断 ${msg.content.length - 1000} 字符) ...")
                        appendLine(tail)
                    }
                    if (estimateTokens(truncated) <= budget) {
                        selected.add(msg.copy(content = truncated))
                        budget -= estimateTokens(truncated)
                    }
                }
                continue
            }
            selected.add(msg)
            budget -= tokens
        }

        // 确保最后几轮关键决策链完整（最后 KEEP_EXCHANGES 轮 user）
        val recentUsers = mutableListOf<ChatMessage>()
        var uc = 0
        for (i in history.indices.reversed()) {
            if (history[i].role == ChatRole.User) {
                recentUsers.add(0, history[i])
                uc++
                if (uc >= KEEP_EXCHANGES) break
            }
        }
        for (ru in recentUsers.reversed()) {
            if (ru !in selected && estimateTokens(ru.content) <= budget) {
                selected.add(ru)
                budget -= estimateTokens(ru.content)
            }
        }

        // 按原始顺序重排
        val orderMap = history.withIndex().associate { (i, m) -> m to i }
        selected.addAll(currentExchange)
        return selected.sortedBy { orderMap[it] ?: Int.MAX_VALUE }
    }

    private suspend fun compressMessages(messages: List<ChatMessage>, threshold: Int = getCompressThreshold()): List<ChatMessage> {
        val totalTokens = estimateContextTokens(messages)
        if (totalTokens <= threshold) return messages

        // Step 1: 截断工具输出（双路径通用）
        val truncated = truncateToolMessages(messages)

        // Step 2: 保留最后 KEEP_EXCHANGES 轮对话
        val recent = mutableListOf<ChatMessage>()
        var userCount = 0
        for (i in truncated.indices.reversed()) {
            recent.add(0, truncated[i])
            if (truncated[i].role == ChatRole.User) {
                userCount++
                if (userCount >= KEEP_EXCHANGES) break
            }
        }

        // Step 3: 处理早期消息（云端→摘要，本地→优先级保护）
        val keepEnd = truncated.size - recent.size
        val earlyMessages = truncated.take(keepEnd)
        val combined = mutableListOf<ChatMessage>()
        var summaryJson = ""

        if (keepEnd > 0 && _state.value.cloudModelEnabled) {
            // 云端路径：LLM 结构化摘要
            summaryJson = summarizeMessagesCloud(earlyMessages) ?: ""
            if (summaryJson.isNotBlank()) {
                combined.add(ChatMessage(
                    role = ChatRole.Model,
                    content = "[上下文摘要]\n$summaryJson",
                    timestamp = System.currentTimeMillis(),
                ))
            }
        }

        // 云端摘要失败 或 本地路径：优先保留含关键信息的消息，其余摘要保留
        if (!_state.value.cloudModelEnabled || summaryJson.isBlank()) {
            // 优先消息（工具结果、代码块、路径信息）
            val priorityEarly = earlyMessages.filter { msg ->
                (msg.role == ChatRole.Tool) ||
                msg.content.contains("```") ||
                msg.content.contains("[工具调用:") ||
                msg.content.contains("filePath") ||
                msg.content.contains("\"path\":")
            }
            // 普通对话消息（简短摘要形式保留）
            val normalEarly = earlyMessages.filter { it !in priorityEarly }
            var priorityLines = 0
            for (msg in priorityEarly.reversed()) {
                combined.add(0, msg)
                priorityLines += msg.content.lines().size
                if (priorityLines > KEEP_PRIORITY_LINES) break
            }
            // 非优先消息以摘要行形式保留，避免完全丢失
            if (normalEarly.isNotEmpty()) {
                val summary = normalEarly.joinToString(" | ") { msg ->
                    val preview = msg.content.take(80).replace('\n', ' ')
                    when (msg.role) {
                        ChatRole.User -> "用户: $preview"
                        ChatRole.Model -> "助手: $preview"
                        else -> preview
                    }
                }
                combined.add(0, ChatMessage(
                    role = ChatRole.Model,
                    content = "[早期对话摘要]\n$summary",
                    timestamp = System.currentTimeMillis(),
                ))
            }
        }

        combined.addAll(recent)

        // Step 4: 更新压缩状态
        val removedTokens = totalTokens - estimateContextTokens(combined)
        _state.update {
            it.copy(
                contextCompressedTokens = it.contextCompressedTokens + removedTokens,
                contextCompressedCount = it.contextCompressedCount + 1,
                isContextCompressed = true,
                contextSummary = if (summaryJson.isNotBlank()) summaryJson else it.contextSummary,
            )
        }

        // Step 5: 插入/更新累计通知
        val existingSummaryIndex = combined.indexOfFirst {
            it.role == ChatRole.Model && it.content.startsWith("[上下文压缩累计]")
        }
        val summaryMsg = ChatMessage(
            role = ChatRole.Model,
            content = buildString {
                append("[上下文压缩累计] 累计移除 ${removedTokens / 1000}k tokens，")
                append("保留最近 $KEEP_EXCHANGES 轮对话")
                if (summaryJson.isNotBlank()) append("，早期对话已摘要压缩")
                append("。")
            },
            timestamp = System.currentTimeMillis(),
        )
        return if (existingSummaryIndex >= 0) {
            combined.toMutableList().also { list -> list[existingSummaryIndex] = summaryMsg }
        } else {
            listOf(summaryMsg) + combined
        }
    }
    private fun resetConversation() {
        activeConversation = null
    }

    private fun chatMessagesToLiteRT(messages: List<ChatMessage>): List<Message> =
        messages.map { msg ->
            when (msg.role) {
                ChatRole.User -> Message.user(msg.content)
                ChatRole.Model -> Message.model(msg.content)
                ChatRole.System -> Message.system(msg.content)
                ChatRole.Tool -> Message.tool(
                    com.google.ai.edge.litertlm.Contents.of(
                        listOf(com.google.ai.edge.litertlm.Content.ToolResponse(msg.toolCallId ?: "call_${msg.id.take(8)}", msg.content))
                    )
                )
            }
        }


    private fun uriToTempFile(ctx: android.content.Context, uri: Uri): File? = try {
        val input = ctx.contentResolver.openInputStream(uri) ?: return null
        val ext = uri.lastPathSegment?.substringAfterLast('.', "jpg") ?: "jpg"
        val tempFile = File.createTempFile("img_", ".$ext", ctx.cacheDir)
        FileOutputStream(tempFile).use { out -> input.use { it.copyTo(out) } }
        tempFile
    } catch (e: Exception) { null }



    // 检查位置是否在 [think]...[/think] 块内部
    // 使用计数器追踪嵌套深度，自动处理未闭合标签
    private fun isInsideThinkBlock(text: String, pos: Int): Boolean {
        val before = text.substring(0, pos.coerceIn(0, text.length))
        var depth = 0
        var searchFrom = 0
        while (true) {
            val openIdx = before.indexOf("[think]", searchFrom)
            val closeIdx = before.indexOf("[/think]", searchFrom)
            if (openIdx < 0 && closeIdx < 0) break
            // 先遇到 open 或只有 open → depth++
            if (closeIdx < 0 || (openIdx >= 0 && openIdx < closeIdx)) {
                depth++
                searchFrom = openIdx + 7
            } else {
                // 先遇到 close
                depth--
                searchFrom = closeIdx + 8
            }
        }
        return depth > 0
    }

    // 检查 JSON 起始位置是否合理的工具调用上下文
    private fun isStandaloneJson(text: String, pos: Int): Boolean {
        // 必须不在 think 块内部
        if (isInsideThinkBlock(text, pos)) return false
        // 检查 { 后紧跟着已知的工具字段名
        val tail = text.substring(pos + 1).take(60).trimStart()
        val knownKeys = listOf("\"name\"", "\"tool_name\"", "\"function\"", "\"arguments\"")
        return knownKeys.any { tail.startsWith(it) }
    }

    /**
     * 从模型响应中提取所有工具调用。
     * 支持单次调用和批量调用（多个 JSON 在同一输出中）。
     * @return 列表，每项为 (前置文本, 工具名, 参数) 或 null（无工具调用）
     */
    private fun extractJsonToolCalls(text: String): List<Triple<String?, String, Map<String, String>>>? {
        val trimmed = text.trim()

        // 格式 1: <tool_call>JSON</tool_call> XML 标签
        val xmlCalls = Regex("""<tool_call[^>]*>(.*?)</tool_call>""", RegexOption.DOT_MATCHES_ALL)
            .findAll(trimmed).flatMap { m -> parseToolJson(m.groupValues[1]) }.toList()
        if (xmlCalls.isNotEmpty()) return xmlCalls

        // 格式 2: ```json ... ``` / ```tool ... ``` 围栏块
        val codeBlockCalls = Regex("""```(?:json|tool)?\s*\n?(.*?)\n?```""", RegexOption.DOT_MATCHES_ALL)
            .findAll(trimmed).flatMap { m -> parseToolJson(m.groupValues[1]) }.toList()
        if (codeBlockCalls.isNotEmpty()) return codeBlockCalls

        // 检查 JSON 工具调用标记（标准格式: name, 别名: tool_name）
        val toolPatterns = listOf(
            Regex("""\{\s*"name"\s*:"""), Regex("""\{\s*"function"\s*:"""), Regex("""\{\s*"tool_name"\s*:"""),
        )
        val hasAnyTool = toolPatterns.any { it.containsMatchIn(trimmed) }
        if (!hasAnyTool) {
            // 格式 3: bare LFM2 格式 functionName(param="value")
            val lfmMatch = parseLfmFormat(trimmed)
            if (lfmMatch != null) return listOf(lfmMatch)
            // 格式 4: 已弃用函数调用格式 {"function":"name","arguments":{...}}
            val funcCall = parseFunctionCallFormat(trimmed)
            if (funcCall != null) return listOf(funcCall)
            return null
        }

        // 扫描所有行内 JSON 工具调用
        val calls = mutableListOf<Triple<String?, String, Map<String, String>>>()
        var searchStart = 0
        while (true) {
            val start = trimmed.indexOf('{', searchStart)
            if (start < 0) break
            if (isInsideThinkBlock(trimmed, start)) { searchStart = start + 1; continue }
            if (!isStandaloneJson(trimmed, start)) { searchStart = start + 1; continue }
            val end = findJsonBlockEnd(trimmed, start)
            if (end < 0) { searchStart = start + 1; continue }
            val jsonStr = trimmed.substring(start, end + 1)
            val repaired = repairJson(jsonStr)
            val parsed = parseSingleToolJson(repaired)
            if (parsed != null) {
                val prefix = if (calls.isEmpty()) trimmed.substring(0, start).trim().ifEmpty { null } else null
                calls.add(Triple(prefix, parsed.first, parsed.second))
            }
            searchStart = end + 1
        }
        return calls.ifEmpty { null }
    }

    /** 字符串感知的 JSON 修复：只修复结构层，不破坏字符串内容（如 HTML/CSS 中的 {word:}） */
    private fun repairJson(json: String): String {
        val segments = mutableListOf<Pair<Boolean, String>>()
        val buf = StringBuilder()
        var idx = 0
        var inStr = false
        while (idx < json.length) {
            when {
                !inStr && json[idx] == '"' -> {
                    if (buf.isNotEmpty()) { segments.add(false to buf.toString()); buf.clear() }
                    buf.append('"'); idx++; inStr = true
                }
                inStr && json[idx] == '\\' -> {
                    buf.append(json[idx]); idx++
                    if (idx < json.length) { buf.append(json[idx]); idx++ }
                }
                inStr && json[idx] == '"' -> {
                    buf.append('"'); idx++; inStr = false
                }
                else -> { buf.append(json[idx]); idx++ }
            }
        }
        if (buf.isNotEmpty()) segments.add(inStr to buf.toString())
        return segments.joinToString("") { (isString, content) ->
            if (isString) content
            else content
                .replace(Regex(""",\s*\}"""), "}")
            .replace(Regex(""",\s*\]"""), "]")
                .replace(Regex("""([{,])\s*(\w+)\s*:""")) { "${it.groupValues[1]}\"${it.groupValues[2]}\":" }
        }
    }

    /** 解析 LFM2 格式: functionName(param1="value1", param2=123) */
    private fun parseLfmFormat(text: String): Triple<String?, String, Map<String, String>>? {
        val knownTools = setOf("listFiles", "readFile", "writeFile", "replaceInFile",
            "batchReplaceInFile", "deleteFile", "createDirectory", "runCommand",
            "searchWeb", "readLints", "grep", "searchInFiles", "searchCodebase", "glob",
            "searchConversationMemory", "getRecentConversationMemory")
        val lfmRegex = Regex("""(\w+)\s*\((.*?)\)""", RegexOption.DOT_MATCHES_ALL)
        for (m in lfmRegex.findAll(text)) {
            val name = m.groupValues[1]
            if (name !in knownTools) continue
            val rawArgs = m.groupValues[2]
            val args = mutableMapOf<String, String>()
            // 解析 key="value" 或 key=value（不带引号）
            val argRegex = Regex("""(\w+)\s*=\s*(?:"([^"]*)"|'([^']*)'|(\S+))""")
            for (am in argRegex.findAll(rawArgs)) {
                val key = am.groupValues[1]
                val value = am.groupValues[2].ifEmpty {
                    am.groupValues[3].ifEmpty { am.groupValues[4] }
                }
                args[key] = value
            }
            return Triple(null, name, args)
        }
        return null
    }

    /** 解析 OpenAI 函数调用格式: {"function":"name","arguments":"{...}"} */
    private fun parseFunctionCallFormat(text: String): Triple<String?, String, Map<String, String>>? {
        try {
            val json = org.json.JSONObject(text.trim())
            val name = json.optString("function", "").ifEmpty { return null }
            if (name !in KNOWN_TOOLS) return null // 非已知工具则忽略
            val argsRaw = json.optString("arguments", "")
            val args = mutableMapOf<String, String>()
            if (argsRaw.isNotBlank()) {
                try {
                    val argsJson = org.json.JSONObject(argsRaw)
                    for (k in argsJson.keys()) args[k] = argsJson.get(k).toString()
                } catch (_: Exception) { args["raw"] = argsRaw }
            }
            return Triple(null, name, args)
        } catch (_: Exception) { return null }
    }

    private fun parseToolJson(text: String): Sequence<Triple<String?, String, Map<String, String>>> = sequence {
        var pos = 0
        while (true) {
            val start = text.indexOf('{', pos)
            if (start < 0) break
            val end = findJsonBlockEnd(text, start)
            if (end < 0) break
            val result = parseSingleToolJson(text.substring(start, end + 1))
            if (result != null) yield(Triple(null, result.first, result.second))
            pos = end + 1
        }
    }

    private fun parseSingleToolJson(jsonStr: String): Pair<String, Map<String, String>>? {
        return try {
            val json = org.json.JSONObject(jsonStr)
            // 标准格式: name + arguments（校验 KNOWN_TOOLS 防误判）
            val name = json.optString("name", "")
            if (name.isNotEmpty() && name in KNOWN_TOOLS && json.has("arguments")) {
                val argsJson = json.getJSONObject("arguments")
                val args = mutableMapOf<String, String>()
                for (k in argsJson.keys()) {
                    val v = argsJson.get(k)
                    args[k] = when (v) {
                        is org.json.JSONObject, is org.json.JSONArray -> v.toString()
                        else -> v.toString()
                    }
                }
                return Pair(name, args)
            }
            // 别名: tool_name（兼容旧格式）
            val toolName = json.optString("tool_name", "")
            if (toolName.isNotEmpty() && toolName in KNOWN_TOOLS) {
                val argsJson = json.optJSONObject("arguments")
                    ?: json.optJSONObject("parameters")
                    ?: json.optJSONObject("params")
                    ?: org.json.JSONObject()
                val args = mutableMapOf<String, String>()
                for (k in argsJson.keys()) {
                    val v = argsJson.get(k)
                    args[k] = when (v) {
                        is org.json.JSONObject, is org.json.JSONArray -> v.toString()
                        else -> v.toString()
                    }
                }
                return Pair(toolName, args)
            }
            // 已弃用: function + arguments
            val func = json.optString("function", "")
            if (func.isNotEmpty() && func in KNOWN_TOOLS && json.has("arguments")) {
                val argsJson = json.optJSONObject("arguments") ?: org.json.JSONObject()
                val args = mutableMapOf<String, String>()
                for (k in argsJson.keys()) {
                    val v = argsJson.get(k)
                    args[k] = when (v) {
                        is org.json.JSONObject, is org.json.JSONArray -> v.toString()
                        else -> v.toString()
                    }
                }
                return Pair(func, args)
            }
            return null
        } catch (_: Exception) {
            val repaired = repairToolArgs(jsonStr)
            if (repaired != null && repaired.first in KNOWN_TOOLS) repaired else null
        }
    }

    /** 修复本地模型常见的单参 JSON 格式错误（Python 三引号、未转义引号等） */
    private fun repairToolArgs(raw: String): Pair<String, Map<String, String>>? {
        val args = raw.trim()
        val match = Regex("""\{\s*"([^"]+)"\s*:\s*([\s\S]*?)\s*\}""").find(args) ?: return null
        val paramName = match.groupValues[1]
        var valStr = match.groupValues[2].trim()
        when {
            valStr.startsWith("\"\"\"") && valStr.endsWith("\"\"\"") -> valStr = valStr.slice(3..valStr.length - 4)
            valStr.startsWith("'''") && valStr.endsWith("'''") -> valStr = valStr.slice(3..valStr.length - 4)
            valStr.startsWith("\"") && valStr.endsWith("\"") -> valStr = valStr.slice(1..valStr.length - 2)
                .replace("\\\"", "\"").replace("\\\\", "\\")
        }
        return Pair(paramName, mapOf(paramName to valStr))
    }

    /** 解析布尔值：兼容 "true"/"false"/"True"/"False"/"1"/"0"/"yes"/"no" */
    private fun parseBool(value: String?, default: Boolean): Boolean {
        if (value == null) return default
        return when (value.trim().lowercase()) {
            "true", "1", "yes", "y", "on" -> true
            "false", "0", "no", "n", "off" -> false
            else -> default
        }
    }

    private fun executeAiTool(name: String, args: Map<String, String>): String = try {
        Log.d("ChatViewModel", "executeAiTool: name=$name args=$args")
        FileLogger.d("ChatViewModel", "executeAiTool: name=$name args=$args")
        // 参数名兼容 camelCase 和 snake_case
        fun Map<String, String>.g(key: String, vararg aliases: String): String =
            this[key] ?: aliases.firstNotNullOfOrNull { this[it] } ?: ""
        fun Map<String, String>.gInt(key: String, vararg aliases: String, default: Int = 0): Int =
            (this[key] ?: aliases.firstNotNullOfOrNull { this[it] })?.toIntOrNull() ?: default
        fun Map<String, String>.gBool(key: String, vararg aliases: String, default: Boolean = true): Boolean =
            parseBool(this[key] ?: aliases.firstNotNullOfOrNull { this[it] }, default)

        val result = when (name) {
            "listFiles" -> aiToolSet.listFiles(args.g("subPath", "sub_path", "path"))
            "readFile" -> aiToolSet.readFile(
                args.g("path"), args.gInt("offset", default = 1), args.gInt("limit", default = 1000))
            "writeFile" -> aiToolSet.writeFile(args.g("path"), args.g("content"),
                args.gBool("overwrite", default = false))
            "replaceInFile" -> aiToolSet.replaceInFile(
                args.g("path"), args.g("old_string", "oldString", "old_str"), args.g("new_string", "newString", "new_str"))
            "batchReplaceInFile" -> aiToolSet.batchReplaceInFile(
                args.g("path"), args.g("edits", "editsJson", "edits_json"))
            "grep" -> aiToolSet.grep(
                args.g("pattern", "query"),
                args.g("extension", "ext"),
                args.g("glob"),
                args.gBool("ignoreCase", "ignore_case", "caseSensitive", "case_sensitive"),
                args.gInt("contextLines", "context_lines", "context", default = 2))
            "searchInFiles" -> aiToolSet.searchInFiles(
                args.g("query"), args.g("extension", "ext"))
            "searchCodebase" -> aiToolSet.searchCodebase(
                args.g("query"), args.g("targetDirectories", "target_directories", "directories"))
            "runCommand" -> aiToolSet.runCommand(args.g("command"))
            "searchWeb" -> aiToolSet.searchWeb(args.g("query"))
            "deleteFile" -> aiToolSet.deleteFile(args.g("path"))
            "createDirectory" -> aiToolSet.createDirectory(args.g("path", "dirPath", "dir_path"))
            "readLints" -> aiToolSet.readLints()
            else -> "Unknown tool: $name"
        }
        FileLogger.d("ChatViewModel", "executeAiTool: $name returned ${result.take(200)}")
        result
    } catch (e: Exception) {
        Log.e("ChatViewModel", "executeAiTool failed: name=$name ${e.message}", e)
        FileLogger.e("ChatViewModel", "executeAiTool failed: name=$name ${e.message}", e)
        "Tool error: ${e.message}"
    }

    private fun toolNameToActivity(name: String, arg: String = ""): ModelActivity {
        return when (name) {
            "listFiles" -> ModelActivity.ListingFiles
            "readFile" -> ModelActivity.ReadingFile
            "writeFile" -> ModelActivity.WritingFile
            "replaceInFile", "batchReplaceInFile" -> ModelActivity.EditingFile
            "deleteFile" -> ModelActivity.DeletingFile
            "createDirectory" -> ModelActivity.CreatingDirectory
            "grep", "searchInFiles", "glob" -> ModelActivity.SearchingCode
            "searchCodebase" -> ModelActivity.SearchingCode
            "searchWeb" -> ModelActivity.SearchingWeb
            "runCommand" -> ModelActivity.RunningCommand
            "readLints" -> ModelActivity.ReadingLints
            else -> ModelActivity.ExecutingTool
        }
    }

    private suspend fun processWithJsonTools(
        text: String, msgId: String,
        ctx: Application,
        imagePaths: List<String> = emptyList(),
    ) {
        val conv = activeConversation ?: return
        val editorCtx = buildEditorContext()
        val hasImages = imagePaths.isNotEmpty()
        val startTime = System.currentTimeMillis()
        var totalOutputChars = 0

        val userInput = if (editorCtx.isNotBlank()) "$editorCtx\n$text" else text
        val firstMessage: Message = if (hasImages) {
            val contentList = mutableListOf<Content>().apply {
                add(Content.Text(userInput))
                imagePaths.forEach { add(Content.ImageFile(absolutePath = it)) }
            }
            Message.user(Contents.of(contentList))
        } else {
            Message.user(userInput)
        }

        _state.update { it.copy(modelActivity = ModelActivity.Thinking, activityDetail = "") }

        var currentMsgId = msgId
        var currentMessage = firstMessage
        var rounds = 0
        var failedParses = 0
        // 重复检测
        val lastToolCalls = ArrayDeque<String>(5)     // 工具调用指纹
        var lastTextResponse = ""                       // 上一轮纯文本响应

        while (true) {
            val fullResponse = StringBuilder()
            try {
                conv.sendMessageAsync(currentMessage).catch { t ->
                    Log.e("ChatViewModel", "sendMessageAsync failed", t)
                    FileLogger.e("ChatViewModel", "sendMessageAsync failed: ${t.message}", t)
                    updateModelMessage(currentMsgId, "\n\n[错误: ${t.message}]", false)
                    finalizeModelMessage(currentMsgId)
                }.collect { chunk ->
                    val c = chunk.toString()
                    fullResponse.append(c)
                    totalOutputChars += c.length
                    updateModelMessage(currentMsgId, c, true)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                _state.update { it.copy(modelActivity = ModelActivity.Idle, activityDetail = "") }
                throw e
            } catch (e: Exception) {
                val duration = System.currentTimeMillis() - startTime
                recordUsage(LlmCallRecord(
                    modelName = _state.value.modelName.ifEmpty { "local" },
                    provider = "local",
                    promptTokens = text.length / 2,
                    completionTokens = totalOutputChars / 2,
                    durationMs = duration,
                    success = false,
                    errorMessage = e.message,
                ))
                Log.e("ChatViewModel", "processWithJsonTools failed", e)
                FileLogger.e("ChatViewModel", "processWithJsonTools failed: ${e.message}", e)
                updateModelMessage(currentMsgId, "\n\n[错误: ${e.message}]", false)
                finalizeModelMessage(currentMsgId)
                _state.update { it.copy(modelActivity = ModelActivity.Idle, activityDetail = "") }
                return
            }

            val response = fullResponse.toString().trim()

            // 输出长度安全监测：超出硬上限时截断并告警
            if (fullResponse.length > LOCAL_MODEL_MAX_OUTPUT_CHARS) {
                val truncated = fullResponse.substring(0, LOCAL_MODEL_MAX_OUTPUT_CHARS)
                updateModelMessage(currentMsgId, "\n\n[注意: 输出已截断，超限 ${fullResponse.length - LOCAL_MODEL_MAX_OUTPUT_CHARS} 字符]", false)
                // 将截断后的内容设为最终消息
                val lines = truncated.lines()
                val displayText = if (lines.size > 2) {
                    lines.dropLast(2).joinToString("\n") + "\n\n[输出过长 (${fullResponse.length} 字符)，已自动截断前 $LOCAL_MODEL_MAX_OUTPUT_CHARS 字符]"
                } else truncated
                updateModelMessage(currentMsgId, displayText, false)
                finalizeModelMessage(currentMsgId)
                recordUsage(LlmCallRecord(
                    modelName = _state.value.modelName.ifEmpty { "local" },
                    provider = "local",
                    promptTokens = text.length / 2,
                    completionTokens = LOCAL_MODEL_MAX_OUTPUT_CHARS / 2,
                    durationMs = System.currentTimeMillis() - startTime,
                    success = true,
                    errorMessage = null,
                ))
                _state.update { it.copy(modelActivity = ModelActivity.Idle, activityDetail = "") }
                return
            }

            // Quality monitor: 空响应检测
            if (response.length < 3 && rounds > 0) {
                FileLogger.w("ChatViewModel", "quality: empty/short response round=$rounds")
                currentMessage = Message.user("[你似乎没有输出有效内容，请直接输出工具调用或最终答案]")
                continue
            }

            val toolCalls = extractJsonToolCalls(response)

            // Quality monitor: 输出截断检测（引擎可能静默截断）
            if (response.length > LOCAL_MODEL_MAX_OUTPUT_CHARS / 2) {
                val suspiciousTruncation = when {
                    response.count { it == '`' } % 2 != 0 -> true   // 未闭合代码块
                    response.endsWith(",") || response.endsWith("，") -> true
                    response.endsWith("```") -> false  // 自然结束代码块
                    response.endsWith("\n") -> false
                    Regex("""[.!?。！？)}\]]\s*$""").containsMatchIn(response) -> false
                    else -> response.takeLast(30).any { it in "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ" }
                }
                if (suspiciousTruncation) {
                    FileLogger.w("ChatViewModel", "possible output truncation detected: len=${response.length}, end='${response.takeLast(20)}'")
                    updateModelMessage(currentMsgId, "\n\n[⚠️ 输出可能被截断：末尾不完整]", false)
                }
            }

            // Quality monitor: 重复工具调用检测
            if (toolCalls != null) {
                val callKey = toolCalls.joinToString("|") { "${it.second}(${it.third})" }
                lastToolCalls.addLast(callKey)
                if (lastToolCalls.size > 5) lastToolCalls.removeFirst()
                // 连续 3 轮相同工具调用 → 强制终止循环
                if (lastToolCalls.size >= 3 && lastToolCalls.toSet().size == 1) {
                    FileLogger.w("ChatViewModel", "quality: repetitive tool calls detected round=$rounds")
                    currentMessage = Message.user("[当前方案陷入循环，请换一个思路：检查已有信息是否足够？工具调用参数是否正确？是否需要搜索其他文件？尝试不同方向。]")
                    lastToolCalls.clear()
                    continue
                }
            }

            if (toolCalls == null) {
                // Quality monitor: 检查是否疑似工具调用但解析失败
                if (isToolLikeContent(response)) {
                    if (failedParses < 3) {
                        failedParses++
                        FileLogger.w("ChatViewModel", "quality: suspicious unparsed content round=$rounds fail=$failedParses")
                        val hint = when {
                            response.contains("<tool") -> "使用 {\"name\":\"...\",\"arguments\":{...}} 格式，不要用 XML 标签"
                            response.contains("```") -> "不要在代码块中包裹工具调用，直接输出 JSON"
                            response.contains("tool_name", ignoreCase = true) -> "请使用标准格式 {\"name\":\"...\",\"arguments\":{...}}"
                            else -> "请严格按照 JSON 格式输出工具调用"
                        }
                        currentMessage = Message.user("[工具调用格式错误: $hint]")
                        continue
                    }
                    FileLogger.w("ChatViewModel", "quality: falling back after $failedParses failed parses")
                    val fallbackMsg = fallbackToAutoToolCalling(ctx, text, imagePaths, editorCtx, currentMsgId, startTime)
                    if (fallbackMsg != null) updateModelMessage(currentMsgId, fallbackMsg, false)
                    finalizeModelMessage(currentMsgId)
                    recordUsage(LlmCallRecord(
                        modelName = _state.value.modelName.ifEmpty { "local" },
                        provider = "local",
                        promptTokens = text.length / 2,
                        completionTokens = totalOutputChars / 2,
                        durationMs = System.currentTimeMillis() - startTime,
                        success = true, errorMessage = null,
                    ))
                    _state.update { it.copy(modelActivity = ModelActivity.Idle, activityDetail = "") }
                    return
                }
                // 无工具调用，视为最终回答
                // 文本重复检测：连续 2 轮内容相同 → 中断退出
                val textContent = response.let { r ->
                    r.removePrefix("```").removeSuffix("```").take(200)
                }
                if (rounds > 0 && textContent == lastTextResponse) {
                    FileLogger.w("ChatViewModel", "quality: repetitive text response round=$rounds")
                    currentMessage = Message.user("[输出内容重复，请换一个思路重新分析。检查已有信息是否充分，换用不同工具或搜索不同内容。]")
                    lastTextResponse = ""
                    continue
                }
                lastTextResponse = textContent

                val duration = System.currentTimeMillis() - startTime
                val totalTokens = totalOutputChars / 2
                recordUsage(LlmCallRecord(
                    modelName = _state.value.modelName.ifEmpty { "local" },
                    provider = "local",
                    promptTokens = text.length / 2,
                    completionTokens = totalTokens,
                    durationMs = duration,
                    success = true, errorMessage = null,
                ))
                finalizeModelMessage(currentMsgId)
                _state.update { it.copy(modelActivity = ModelActivity.Idle, activityDetail = "") }
                return
            }

            // 并行执行所有工具调用
            val knownTools = setOf("listFiles", "readFile", "writeFile", "replaceInFile",
                "batchReplaceInFile", "deleteFile", "createDirectory", "runCommand",
                "searchWeb", "readLints", "grep", "searchInFiles", "searchCodebase",
                "searchConversationMemory", "getRecentConversationMemory")

            // 并行执行所有工具调用
            val results = coroutineScope {
                toolCalls.map { call ->
                    val funcName = call.second
                    val funcArgs = call.third
                    async(Dispatchers.IO) {
                        if (funcName !in knownTools) return@async "未知工具: $funcName"
                        try { executeAiTool(funcName, funcArgs) }
                        catch (e: Exception) { "$funcName 执行失败: ${e.message}" }
                    }
                }.map { it.await() }
            }

            // 工具结果作为独立 Tool 消息存储
            finalizeModelMessage(currentMsgId)
            var combinedResult = results.joinToString("\n\n")

            // 自适应 Lint 注入：工具修改文件后自动检查诊断
            val lintBlock = autoInjectLint(toolCalls)
            if (lintBlock != null) {
                combinedResult += "\n\n$lintBlock"
            }
            val hadModifyingTools = toolCalls.any {
                it.second in setOf("writeFile", "replaceInFile", "batchReplaceInFile", "deleteFile", "createDirectory")
            }

            val toolMsgId = java.util.UUID.randomUUID().toString()
            _state.update {
                it.copy(messages = it.messages + ChatMessage(
                    id = toolMsgId, role = ChatRole.Tool, content = combinedResult,
                    toolCallId = "call_${toolMsgId.take(8)}",
                ))
            }

            // 创建新消息气泡用于下一轮模型回复
            currentMsgId = java.util.UUID.randomUUID().toString()
            _state.update {
                it.copy(
                    messages = it.messages + ChatMessage(id = currentMsgId, role = ChatRole.Model, content = "", isStreaming = true),
                    isLoading = true,
                    modelActivity = ModelActivity.ProcessingResult,
                    activityDetail = "",
                )
            }

            // 将工具结果发给 LiteRT Conversation
            currentMessage = Message.tool(Contents.of(listOf(Content.ToolResponse("call_${currentMsgId.take(8)}", combinedResult))))

            // 自适应上下文刷新：修改操作后立即刷新，否则每 3 轮刷新 delta
            rounds++
            if (hadModifyingTools || rounds % 3 == 0) {
                val ctxUpdate = if (hadModifyingTools) buildContextDelta() else buildContextDelta().takeIf { it.isNotBlank() } ?: buildEditorContext()
                if (ctxUpdate.isNotBlank()) {
                    currentMessage = Message.user(ctxUpdate)
                }
            }
        }
    }

    /** 查找从 start 处开始的 JSON 对象结束位置，跳过字符串字面量内的 {} */
    private fun findJsonBlockEnd(text: String, start: Int): Int {
        if (start >= text.length || text[start] != '{') return -1
        var depth = 0
        var i = start
        while (i < text.length) {
            val c = text[i]
            when {
                c == '"' -> {
                    i++
                    while (i < text.length) {
                        when (text[i]) {
                            '\\' -> i++  // 跳过转义字符
                            '"' -> break
                        }
                        i++
                    }
                }
                c == '{' -> depth++
                c == '}' -> {
                    depth--
                    if (depth == 0) return i
                }
            }
            i++
        }
        return -1
    }

    private fun isToolLikeContent(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return false
        val toolKeywords = listOf(
            "listFiles", "readFile", "writeFile", "replaceInFile", "batchReplaceInFile",
            "deleteFile", "batchDeleteFile", "createDirectory", "runCommand", "searchWeb",
            "readLints", "grep", "searchInFiles", "searchCodebase", "glob",
        )
        if (toolKeywords.any { trimmed.contains(it) }) return true
        val jsonPatterns = listOf(
            Regex("""tool_call""", RegexOption.IGNORE_CASE),
            Regex("""\{[^}]*"name"\s*:\s*"[^"]+"[^}]*"arguments"\s*:"""),
        )
        return jsonPatterns.any { it.containsMatchIn(trimmed) }
    }

    private suspend fun fallbackToAutoToolCalling(
        ctx: Application,
        text: String,
        imagePaths: List<String>,
        editorCtx: String,
        msgId: String,
        startTime: Long,
    ): String? {
        return try {
            val stateMsgs = _state.value.messages
            val historyMsgs = mutableListOf<Message>()
            for (msg in stateMsgs) {
                if (msg.id == msgId) continue
                if (msg.id == msgId && msg.role == ChatRole.Model) continue
                if (msg.role == ChatRole.User) {
                    historyMsgs.add(Message.user(msg.content))
                } else if (msg.role == ChatRole.Model && msg.content.isNotBlank()) {
                    historyMsgs.add(Message.model(msg.content))
                }
            }
            resetConversation()
            pendingInitialMessages = historyMsgs
            val fallbackConv = ensureConversation(autoToolCalling = true)

            val userInput = if (editorCtx.isNotBlank()) "$editorCtx\n[用户消息]\n$text" else text
            val msg: Message = if (imagePaths.isNotEmpty()) {
                val contentList = mutableListOf<Content>().apply {
                    add(Content.Text(userInput))
                    imagePaths.forEach { add(Content.ImageFile(absolutePath = it)) }
                }
                Message.user(Contents.of(contentList))
            } else {
                Message.user(userInput)
            }

            val sb = StringBuilder()
            fallbackConv.sendMessageAsync(msg).collect { chunk ->
                sb.append(chunk.toString())
            }
            val result = sb.toString().trim()
            if (result.isNotBlank()) result else null
        } catch (e: Exception) {
            Log.e("ChatViewModel", "fallbackToAutoToolCalling failed: ${e.message}", e)
            FileLogger.e("ChatViewModel", "fallbackToAutoToolCalling failed: ${e.message}", e)
            null
        }
    }

    // === 混合模式：本地探索 + 云端推理 ===

    /** 探索阶段允许的只读工具 */
    private val exploreOnlyTools = setOf(
        "listFiles", "readFile", "grep", "searchInFiles", "searchCodebase", "readLints"
    )

    /**
     * 混合模式主流程：
     * Phase 1: 本地探索 (read-only, capped)
     * Phase 2: 结构化压缩
     * Phase 3: 云端推理 + 工具执行
     */
    /** 如果请求很简单（短文本、无功能描述），跳过探索直接走云端 */
    private fun needsExploration(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.length < 40) return false  // 极短请求 → 直接云端
        // 仅含单个变量/属性操作，不需要探索
        val simplePatterns = listOf(
            Regex("""将\s*\S+\s*(?:改为|改成|修改为)\s*\S+"""),
            Regex("""把\s*\S+\s*(?:改为|改成|修改为)\s*\S+"""),
            Regex("""(?:修改|更改|改)\s+\S+\s+(?:为|成)\s+\S+"""),
            Regex("""(?:change|update|rename|fix|delete|remove|add)\s+\S+\s+(?:to|from|in)\s+\S+"""),
        )
        if (simplePatterns.any { it.containsMatchIn(trimmed) }) return false
        return true
    }

    private suspend fun processHybrid(
        text: String, msgId: String,
        ctx: Application,
        imagePaths: List<String> = emptyList(),
    ) {
        _state.update { it.copy(modelActivity = ModelActivity.Thinking, activityDetail = "") }
        FileLogger.d("ChatViewModel", "processHybrid: starting hybrid mode")

        // Phase 1: 本地探索（仅复杂请求需要）
        val explorationResult = if (needsExploration(text)) {
            runLocalExploration(text, msgId)
        } else {
            FileLogger.d("ChatViewModel", "processHybrid: fast path, skip exploration")
            null
        }
        FileLogger.d("ChatViewModel", "processHybrid: exploration done, ${explorationResult?.length ?: 0} chars")

        // Phase 2: 结构化压缩
        val compressed = buildExplorationContext(explorationResult)
        FileLogger.d("ChatViewModel", "processHybrid: compressed to ${compressed.length} chars")

        // Phase 3: 带上下文的云端推理
        val enrichedText = if (compressed.isNotBlank()) "$compressed\n\n$text" else text
        processWithCloudTools(enrichedText, msgId, ctx, imagePaths)
    }

    /**
     * Phase 1: 本地探索阶段。
     * 创建只读工具集的 LiteRT 对话，运行最多 HYBRID_EXPLORE_MAX_ROUNDS 轮。
     * 在探索模式下模型只能调用：listFiles, readFile, grep, searchInFiles, searchCodebase, readLints
     * 探索完成后收集所有工具结果并返回。
     */
    private suspend fun runLocalExploration(
        text: String, msgId: String
    ): String? {
        if (!liteRTManager.isInitialized) return null
        val exploreInstruction = buildString {
            appendLine(buildSystemInstruction())
            appendLine()
            appendLine("## 本地探索模式")
            appendLine("你只能使用只读工具：listFiles, readFile, grep, searchInFiles, searchCodebase, readLints")
            appendLine("禁止使用：writeFile, replaceInFile, batchReplaceInFile, deleteFile, runCommand, searchWeb")
            appendLine("你有最多 $HYBRID_EXPLORE_MAX_ROUNDS 轮来探索项目结构。")
            appendLine("当你收集到足够信息后，输出【探索完毕】并附上文件结构摘要。")
        }

        val conv = try {
            liteRTManager.createConversation(ConversationConfig(
                systemInstruction = Contents.of(exploreInstruction),
                samplerConfig = liteRTManager.modelParams.toSamplerConfig(),
                tools = listOf(tool(aiToolSet)),
            ))
        } catch (e: Exception) {
            Log.e("ChatViewModel", "hybrid: failed to create conv", e)
            FileLogger.e("ChatViewModel", "hybrid: failed to create conv: ${e.message}", e)
            return null
        }

        val exploreMessages = StringBuilder()
        var round = 0
        try {
            var currentMsg: Message = Message.user("探索项目：$text")
            while (round < HYBRID_EXPLORE_MAX_ROUNDS) {
                round++

                val response = StringBuilder()
                conv.sendMessageAsync(currentMsg).catch { t ->
                    Log.e("ChatViewModel", "hybrid explore failed round=$round", t)
                }.collect { chunk ->
                    val c = chunk.toString()
                    response.append(c)
                }

                val respText = response.toString().trim()
                if (respText.contains("探索完毕")) {
                    exploreMessages.appendLine(respText)
                    break
                }

                val toolCalls = extractJsonToolCalls(respText)
                if (toolCalls == null) {
                    exploreMessages.appendLine("[探索: $respText]")
                    // 模型无工具调用但未结束：提示继续探索
                    currentMsg = Message.user("请继续探索，或输出【探索完毕】结束探索")
                    continue
                }

                // 执行工具并收集结果
                val results = toolCallsToResults(toolCalls)
                var hasBlocked = false
                val toolResults = mutableListOf<String>()
                for ((i, call) in toolCalls.withIndex()) {
                    val r = results[i]
                    exploreMessages.appendLine(r)
                    toolResults.add(r)
                    if (r.startsWith("[拒绝]")) hasBlocked = true
                }

                // 将工具结果回传给 LiteRT Conversation 供后续推理
                if (hasBlocked) {
                    conv.sendMessageAsync(Message.user(
                        "[警告] 禁止使用写工具，请只使用只读工具。收到后输出【探索完毕】或继续探索。"
                    )).collect {}
                }
                // 合并工具结果作为 tool response 传给模型
                val combinedToolResult = toolResults.joinToString("\n")
                conv.sendMessageAsync(Message.tool(
                    Contents.of(listOf(Content.ToolResponse("explore_$round", combinedToolResult)))
                )).collect {}
                // 继续探索
                currentMsg = Message.user("继续探索项目结构，或输出【探索完毕】结束")
            }
        } catch (e: Exception) {
            Log.e("ChatViewModel", "hybrid explore error", e)
            FileLogger.e("ChatViewModel", "hybrid explore error: ${e.message}", e)
        } finally {
            try { conv.close() } catch (_: Exception) {}
        }

        return exploreMessages.toString().ifBlank { null }
    }

    /** 将工具调用列表转换为结果字符串 */
    private suspend fun toolCallsToResults(
        toolCalls: List<Triple<String?, String, Map<String, String>>>
    ): List<String> {
        return coroutineScope {
            toolCalls.map { (_, funcName, funcArgs) ->
                async(Dispatchers.IO) {
                    if (funcName !in exploreOnlyTools) {
                        "[拒绝] 禁止工具: $funcName（探索阶段仅允许只读工具）"
                    } else {
                        try {
                            val result = executeAiTool(funcName, funcArgs)
                            "[$funcName] $result"
                        } catch (e: Exception) {
                            "[$funcName] 执行失败: ${e.message}"
                        }
                    }
                }
            }.map { it.await() }
        }
    }

    /**
     * Phase 2: 结构化压缩。
     * 从探索结果中提取关键信息，生成结构化摘要。
     * 不依赖模型推理，纯文本处理。
     */
    private fun buildExplorationContext(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        val sb = StringBuilder()
        sb.appendLine("[探索结果摘要]")
        // 提取文件路径
        val filePaths = mutableSetOf<String>()
        val grepResults = mutableListOf<String>()
        var totalLines = 0

        for (line in raw.lines()) {
            when {
                line.startsWith("[readFile]") -> {
                    val parts = line.removePrefix("[readFile]").trim().split("\n").firstOrNull()
                    if (parts != null) {
                        val fp = parts.substringBefore(" ").substringBefore("(")
                        if (fp.isNotEmpty()) filePaths.add(fp)
                    }
                }
                line.startsWith("[grep]") || line.startsWith("[searchInFiles]") -> {
                    grepResults.add(line.take(120))
                }
                line.startsWith("[listFiles]") -> {
                    // 提取目录结构摘要
                }
                line.matches(Regex("^\\d+ 行|^\\d+ lines")) -> {
                    val num = line.filter { it.isDigit() }.toIntOrNull() ?: 0
                    totalLines += num
                }
            }
        }

        if (filePaths.isNotEmpty()) {
            sb.appendLine("查看的文件:")
            filePaths.take(10).forEach { sb.appendLine("  - $it") }
        }
        if (grepResults.isNotEmpty()) {
            sb.appendLine("搜索结果:")
            grepResults.take(5).forEach { sb.appendLine("  $it") }
        }
        if (totalLines > 0) {
            sb.appendLine("共计查阅约 $totalLines 行代码")
        }
        if (raw.contains("探索完毕")) {
            val summarySection = raw.substringAfter("探索完毕").take(500)
            sb.appendLine("模型自述摘要: ${summarySection.take(300)}")
        }

        return sb.toString().trimEnd()
    }

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
        // 找到最后一条 User 消息（即 sendMessage() 刚添加的那条），用于后续跳过并重新构造
        val lastUserIdx = _state.value.messages.indexOfLast { it.role == ChatRole.User && it.id != currentMsgId }
        for ((idx, msg) in _state.value.messages.withIndex()) {
            if (msg.id == currentMsgId) continue
            if (msg.role == ChatRole.User || msg.role == ChatRole.Model || msg.role == ChatRole.Tool) {
                // 跳过 sendMessage() 已添加到 UI 状态的用户消息（最后一条 User 消息），
                // 后续会重新构造带编辑器上下文的用户消息
                if (msg.role == ChatRole.User && idx == lastUserIdx) continue
                if (msg.content.isNotBlank() || msg.role == ChatRole.Model) historyMessages.add(msg)
            }
        }
        val compressed = compressMessages(historyMessages)
        historyMessages.clear()
        historyMessages.addAll(compressed)
        // 注入编辑器上下文 + 当前用户消息
        val editorCtx = buildEditorContext()
        val userContent = if (editorCtx.isNotBlank()) "$editorCtx\n$text" else text
        historyMessages.add(ChatMessage(role = ChatRole.User, content = userContent))

        // 注入已有上下文摘要（渐进式摘要的锚定状态）
        val existingSummary = _state.value.contextSummary
        if (existingSummary.isNotBlank() && historyMessages.none { it.content.startsWith("[上下文摘要]") }) {
            historyMessages.add(0, ChatMessage(
                role = ChatRole.Model,
                content = "[上下文摘要]\n$existingSummary",
            ))
        }

        while (true) {
            cloudRounds++
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
                Log.e("ChatViewModel", "cloudSendMessage failed", e)
                FileLogger.e("ChatViewModel", "cloudSendMessage failed: ${errMsg}", e)
                recordUsage(LlmCallRecord(
                    modelName = cfg.modelName,
                    provider = "cloud",
                    promptTokens = 0,
                    completionTokens = 0,
                    durationMs = duration,
                    success = false,
                    errorMessage = errMsg,
                ))
                updateModelMessage(currentMsgId, "\n\n[云端模型错误: ${errMsg}]", false)
                finalizeModelMessage(currentMsgId)
                _state.update { it.copy(modelActivity = ModelActivity.Idle, activityDetail = "") }
                return
            }

            val duration = System.currentTimeMillis() - roundStartTime
            recordUsage(LlmCallRecord(
                modelName = cfg.modelName,
                provider = "cloud",
                promptTokens = apiUsage.promptTokens,
                completionTokens = apiUsage.completionTokens,
                durationMs = duration,
                success = true,
                errorMessage = null,
            ))

            val response = fullResponse.toString().trim()

            // 优先使用原生 tool_calls，其次退化到文本 JSON 解析
            val toolCalls = if (nativeToolCalls.isNotEmpty()) {
                nativeToolCalls.map { tc ->
                    val argsMap = try {
                        val obj = org.json.JSONObject(tc.arguments)
                        obj.keys().asSequence().associate { key -> key to obj.optString(key, "") }
                    } catch (_: Exception) { emptyMap() }
                    Triple(tc.id, tc.functionName, argsMap)
                }
            } else {
                extractJsonToolCalls(response)
            }

            if (toolCalls == null || (toolCalls.isEmpty() && response.isBlank())) {
                // 最终回答：添加到历史并结束
                historyMessages.add(ChatMessage(role = ChatRole.Model, content = response))
                finalizeModelMessage(currentMsgId)
                _state.update { it.copy(modelActivity = ModelActivity.Idle, activityDetail = "") }
                return
            }

            // 并行执行所有工具
            val knownTools = setOf("listFiles", "readFile", "writeFile", "replaceInFile",
                "batchReplaceInFile", "deleteFile", "createDirectory", "runCommand",
                "searchWeb", "readLints", "grep", "searchInFiles", "searchCodebase",
                "searchConversationMemory", "getRecentConversationMemory")
            val results = coroutineScope {
                toolCalls.map { call ->
                    val funcName = call.second
                    val funcArgs = call.third
                    async(Dispatchers.IO) {
                        if (funcName !in knownTools) return@async "未知工具: $funcName"
                        try { executeAiTool(funcName, funcArgs) }
                        catch (e: Exception) { "$funcName 执行失败: ${e.message}" }
                    }
                }.map { it.await() }
            }

            // 将助手的所有工具调用加入历史
            val toolCallId = "call_${java.util.UUID.randomUUID().toString().take(8)}"
            historyMessages.add(ChatMessage(
                role = ChatRole.Model, content = response,
                toolCallId = toolCallId,
            ))

            var combinedResult = results.joinToString("\n\n")

            // 自适应 Lint 注入
            val lintBlock = autoInjectLint(toolCalls)
            if (lintBlock != null) {
                combinedResult += "\n\n$lintBlock"
            }
            val hadModifyingTools = toolCalls.any {
                it.second in setOf("writeFile", "replaceInFile", "batchReplaceInFile", "deleteFile", "createDirectory")
            }

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

            // 将合并的工具执行结果加入历史
            historyMessages.add(ChatMessage(
                role = ChatRole.Tool, content = combinedResult,
                toolCallId = toolCallId,
            ))

            // 渐进式摘要：每 SUMMARIZE_INTERVAL 轮触发一次
            if (cloudRounds % SUMMARIZE_INTERVAL == 0) {
                val progressiveMsgs = historyMessages.toList()
                val compressed = compressMessages(progressiveMsgs)
                historyMessages.clear()
                historyMessages.addAll(compressed)
                val summary = _state.value.contextSummary
                if (summary.isNotBlank() && historyMessages.none { it.content.startsWith("[上下文摘要]") }) {
                    historyMessages.add(0, ChatMessage(
                        role = ChatRole.Model,
                        content = "[上下文摘要]\n$summary",
                    ))
                }
            }

            // 自适应上下文刷新：修改操作后立即刷新，否则每 3 轮刷新 delta
            if (hadModifyingTools || cloudRounds % 3 == 0) {
                val ctxUpdate = if (hadModifyingTools) buildContextDelta() else buildContextDelta().takeIf { it.isNotBlank() } ?: buildEditorContext()
                if (ctxUpdate.isNotBlank()) {
                    historyMessages.add(ChatMessage(
                        role = ChatRole.User, content = ctxUpdate,
                    ))
                }
            }

            currentMsgId = java.util.UUID.randomUUID().toString()
            _state.update { it.copy(messages = it.messages + ChatMessage(id = currentMsgId, role = ChatRole.Model, content = "", isStreaming = true), isLoading = true) }
        }
    }

    fun setCloudModelEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesRepo.setCloudModelEnabled(enabled) }
        _state.update { it.copy(cloudModelEnabled = enabled) }
        updateContextMaxTokens()
    }

    fun addCloudProfile(name: String, apiEndpoint: String, apiKey: String, modelName: String, contextWindow: Int = 128000) {
        val id = java.util.UUID.randomUUID().toString()
        val newProfile = CloudModelProfile(
            id = id,
            name = name.ifEmpty { modelName },
            apiEndpoint = apiEndpoint,
            apiKey = apiKey,
            modelName = modelName,
            contextWindow = contextWindow,
        )
        viewModelScope.launch {
            val current = preferencesRepo.cloudModelProfiles.first()
            val updated = current + newProfile
            preferencesRepo.setCloudModelProfiles(updated)
            if (updated.size == 1) {
                preferencesRepo.setActiveCloudProfileId(id)
            }
        }
    }

    fun removeCloudProfile(profileId: String) {
        viewModelScope.launch {
            val current = preferencesRepo.cloudModelProfiles.first()
            val updated = current.filter { it.id != profileId }
            preferencesRepo.setCloudModelProfiles(updated)
            val activeId = preferencesRepo.activeCloudProfileId.first()
            if (activeId == profileId) {
                val newActive = updated.firstOrNull()?.id ?: ""
                preferencesRepo.setActiveCloudProfileId(newActive)
            }
        }
    }

    fun updateCloudProfile(profile: CloudModelProfile) {
        viewModelScope.launch {
            val current = preferencesRepo.cloudModelProfiles.first()
            val updated = current.map { if (it.id == profile.id) profile else it }
            preferencesRepo.setCloudModelProfiles(updated)
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
        try { activeConversation?.close() } catch (_: Exception) {}
        activeConversation = null
        pendingInitialMessages = null
    }

    fun clearMessages() {
        sendJob?.cancel()
        _state.update { it.copy(messages = emptyList(), inputText = "",
            isContextCompressed = false, contextCompressedTokens = 0, contextCompressedCount = 0,
            contextSummary = "") }
        viewModelScope.launch(Dispatchers.IO) { closeConversation() }
    }

    fun newConversation() {
        sendJob?.cancel()
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
        pendingInitialMessages = entry.messages.mapNotNull { msg ->
            when (msg.role) {
                ChatRole.User -> Message.user(msg.content)
                ChatRole.Model -> Message.model(msg.content)
                ChatRole.System -> Message.system(msg.content)
                ChatRole.Tool -> Message.tool(com.google.ai.edge.litertlm.Contents.of(listOf(com.google.ai.edge.litertlm.Content.ToolResponse(msg.toolCallId ?: "call_${msg.id.take(8)}", msg.content))))
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

    private fun updateModelMessage(msgId: String, chunk: String, append: Boolean) {
        _state.update { state ->
            val updatedMessages = state.messages.map { msg ->
                if (msg.id == msgId) {
                    val newContent = if (append) msg.content + chunk else chunk
                    msg.copy(content = newContent, isStreaming = true)
                } else msg
            }
            state.copy(messages = updatedMessages)
        }
    }

    private fun finalizeModelMessage(msgId: String) {
        _state.update { state ->
            val updatedMessages = state.messages.map { msg ->
                if (msg.id == msgId) {
                    msg.copy(isStreaming = false)
                } else msg
            }
            state.copy(messages = updatedMessages, isLoading = false)
        }
        // 自动保存到对话记忆
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

    /** 将当前新产生的对话轮次保存到记忆系统 */
    private suspend fun autoSaveToMemory() {
        val msgs = _state.value.messages
        if (msgs.isEmpty()) return
        // 只保存最后一轮（最近添加的用户+模型消息）
        val recentUser = msgs.lastOrNull { it.role == ChatRole.User && !it.content.startsWith("[系统指令]") }
        val recentModel = msgs.lastOrNull { it.role == ChatRole.Model && it.isStreaming == false && !it.content.startsWith("[上下文") }
        val toSave = mutableListOf<ChatMessageAdapter>()
        recentUser?.let { toSave.add(ChatMessageAdapter("user", it.content.take(2000))) }
        recentModel?.let { toSave.add(ChatMessageAdapter("model", it.content.take(2000))) }
        if (toSave.isNotEmpty()) {
            conversationMemory.addMessages(toSave)
        }
    }

    override fun onCleared() {
        super.onCleared()
        sendJob?.cancel()
        closeConversation()
        saveCurrentToHistory()
        liteRTManager.close()
    }
}
