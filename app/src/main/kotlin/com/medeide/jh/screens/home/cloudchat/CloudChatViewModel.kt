package com.medeide.jh.screens.home.cloudchat

import android.net.Uri
import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medeide.jh.core.data.logging.FileLogger
import com.medeide.jh.core.data.repository.ConversationRepository
import com.medeide.jh.core.data.repository.UserPreferencesRepository
import com.medeide.jh.core.data.repository.UsageAnalyticsRepository
import com.medeide.jh.core.data.source.remote.CloudLLMClient
import com.medeide.jh.core.data.source.remote.ModelCancellationToken
import com.medeide.jh.model.chat.AiBehaviorSettings
import com.medeide.jh.model.chat.ApiUsage
import com.medeide.jh.model.chat.ChatMessage
import com.medeide.jh.core.data.analytics.LlmCallRecord
import com.medeide.jh.model.chat.ChatMode
import com.medeide.jh.model.chat.ChatRole
import com.medeide.jh.model.chat.CloudModelProfile
import com.medeide.jh.model.chat.CloudToolCall
import com.medeide.jh.model.chat.ConversationEntry
import com.medeide.jh.model.chat.DisplayItem
import com.medeide.jh.model.chat.DisplayRole
import com.medeide.jh.model.chat.EngineStatus
import com.medeide.jh.model.chat.FileOpStatus
import com.medeide.jh.model.chat.FileOpType
import com.medeide.jh.model.chat.FileOperation
import com.medeide.jh.model.chat.UserProfile
import com.medeide.jh.screens.home.cloudchat.aitools.AIToolSet
import com.medeide.jh.screens.home.cloudchat.aitools.InputOptimizer
import com.medeide.jh.screens.home.cloudchat.aitools.OptimizeMode
import com.medeide.jh.screens.home.cloudchat.aitools.ToolCallHandler
import com.medeide.jh.screens.home.cloudchat.aitools.ToolExecutionCallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class CloudChatViewModel(
    private val preferencesRepo: UserPreferencesRepository,
    private val conversationRepo: ConversationRepository,
    private val cloudLLMClient: CloudLLMClient,
    private val usageAnalyticsRepository: UsageAnalyticsRepository,
) : ViewModel() {

    private val aiToolSet = AIToolSet(
        projectRootCallback = { _state.value.projectRoot.ifEmpty { Environment.getExternalStorageDirectory().absolutePath } },
        isReadOnlyCallback = { _state.value.projectRoot.isEmpty() },
    )
    private val toolCallHandler = ToolCallHandler(aiToolSet)
    private val inputOptimizer = InputOptimizer(cloudLLMClient)
    private var currentToken: ModelCancellationToken? = null
    /** 工具名称 → FileOperation.id 追踪 */
    private val _pendingFileOpIds = mutableMapOf<String, String>()

    private val _state = MutableStateFlow(CloudChatUiState())
    val state: StateFlow<CloudChatUiState> = _state.asStateFlow()

    init {
        FileLogger.i("ChatVM", "init")
        loadPreferences()
        loadConversations()
        loadChatMode()
        loadAiBehavior()
    }

    private fun loadPreferences() {
        viewModelScope.launch {
            preferencesRepo.cloudModelProfiles.collect { p ->
                _state.update {
                    it.copy(
                        cloudModelProfiles = p,
                        cloudModelEnabled = p.any { cp -> cp.apiKey.isNotBlank() },
                    )
                }
            }
        }
        viewModelScope.launch {
            preferencesRepo.activeCloudProfileId.collect { id -> _state.update { it.copy(activeCloudProfileId = id) } }
        }
        viewModelScope.launch {
            preferencesRepo.userProfile.collect { p ->
                _state.update { it.copy(userName = p.userName, agentName = p.agentName.ifEmpty { "AI" }) }
            }
        }
    }

    private fun loadChatMode() {
        viewModelScope.launch {
            preferencesRepo.chatMode.collect { mode -> _state.update { it.copy(chatMode = mode) } }
        }
    }

    private fun loadAiBehavior() {
        viewModelScope.launch {
            preferencesRepo.aiBehavior.collect { b -> _state.update { it.copy(aiBehavior = b) } }
        }
    }

    private fun loadConversations() {
        viewModelScope.launch {
            val list = conversationRepo.load()
            FileLogger.d("ChatVM", "loaded ${list.size} conversations")
            _state.update { it.copy(conversations = list) }
        }
    }

    // ════════════════════════════════════════════════
    //  输入
    // ════════════════════════════════════════════════

    fun setInputText(text: String) { _state.update { it.copy(inputText = text) } }

    fun setOptimizeMode(mode: OptimizeMode) {
        _state.update { it.copy(optimizeMode = mode) }
    }

    fun optimizeInput() {
        val s = _state.value
        val text = s.inputText.trim()
        if (text.isEmpty()) return
        if (s.isOptimizing) return
        val profile = s.activeProfile ?: run {
            _state.update { it.copy(engineStatus = EngineStatus.Error, engineErrorMessage = "未启用云端模型，无法优化") }
            return
        }
        val mode = s.optimizeMode
        _state.update { it.copy(isOptimizing = true) }
        FileLogger.i("ChatVM", "optimizeInput mode=${mode.name} text=${text.take(60)}")
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = inputOptimizer.optimize(text, mode, profile)
                if (result.isNotEmpty() && result != text) {
                    _state.update { it.copy(inputText = result) }
                }
            } catch (e: Exception) {
                FileLogger.e("ChatVM", "optimizeInput failed", e)
                _state.update { it.copy(engineStatus = EngineStatus.Error, engineErrorMessage = "优化失败: ${e.message}") }
            } finally {
                _state.update { it.copy(isOptimizing = false) }
            }
        }
    }

    // ════════════════════════════════════════════════
    //  文件 / 图片
    // ════════════════════════════════════════════════

    fun attachFile(path: String) { _state.update { it.copy(attachedFilePaths = (it.attachedFilePaths + path).distinct()) } }
    fun detachFile(path: String) { _state.update { it.copy(attachedFilePaths = it.attachedFilePaths - path) } }
    fun attachImage(uri: Uri) { _state.update { it.copy(attachedImageUris = (it.attachedImageUris + uri).distinct()) } }
    fun detachImage(uri: Uri) { _state.update { it.copy(attachedImageUris = it.attachedImageUris - uri) } }

    // ════════════════════════════════════════════════
    //  发送 / 中断（GenerationFlow 风格）
    // ════════════════════════════════════════════════

    fun setEditorContext(context: String) {
        _state.update { it.copy(editorContext = context) }
    }

    fun setProjectRoot(path: String) {
        FileLogger.i("ChatVM", "setProjectRoot=$path")
        _state.update { it.copy(projectRoot = path) }
    }

    fun cancelSend() {
        currentToken?.cancel()
        currentToken = null
        FileLogger.i("ChatVM", "cancelSend")
        _state.update { it.copy(isLoading = false, isSending = false, fileOperations = emptyList()) }
        _pendingFileOpIds.clear()
    }

    // ════════════════════════════════════════════════
    //  文件操作状态管理（工具卡片）
    // ════════════════════════════════════════════════

    private val FILE_OP_TOOLS = mapOf(
        "writeFile" to FileOpType.WriteFile,
        "createDirectory" to FileOpType.CreateDirectory,
        "deleteFile" to FileOpType.DeleteFile,
        "moveFile" to FileOpType.MoveFile,
        "copyFile" to FileOpType.CopyFile,
    )

    /** 添加 InProgress 文件操作记录 */
    private fun addFileOp(name: String, type: FileOpType, filePath: String, parentMessageId: String) {
        val op = FileOperation(type = type, filePath = filePath, parentMessageId = parentMessageId)
        _pendingFileOpIds[name] = op.id
        viewModelScope.launch(Dispatchers.Main) {
            _state.update { it.copy(fileOperations = it.fileOperations + op,
                displayItems = it.messages.toDisplayItems(it.fileOperations + op)) }
        }
    }

    /** 更新文件操作状态为 Success / Error */
    private fun updateFileOp(id: String, status: FileOpStatus, errorMessage: String? = null) {
        viewModelScope.launch(Dispatchers.Main) {
            kotlinx.coroutines.delay(30)
            _state.update { state ->
                val updated = state.fileOperations.map { op ->
                    if (op.id == id) op.copy(status = status, errorMessage = errorMessage) else op
                }
                state.copy(fileOperations = updated,
                    displayItems = state.messages.toDisplayItems(updated))
            }
        }
    }

    private fun extractFilePath(name: String, args: Map<String, String>): String =
        args["file_path"] ?: args["file_paths"] ?: args["path"] ?: args["source"] ?: args["destination"] ?: "未知路径"

    fun sendStreamingMessage() {
        val s = _state.value
        val text = s.inputText.trim()
        if (text.isBlank()) return
        val profile = s.activeProfile ?: run {
            _state.update { it.copy(engineStatus = EngineStatus.Error, engineErrorMessage = "未启用云端模型") }
            return
        }
        FileLogger.i("ChatVM", "send text=${text.take(100)} profile=${profile.modelName} projectRoot=${s.projectRoot}")
        val filesCtx = s.attachedFilePaths.joinToString("\n") { "[附件] $it" }
        val fullContent = if (filesCtx.isEmpty()) text else "$text\n\n$filesCtx"
        val userMsg = ChatMessage(role = ChatRole.User, content = fullContent)

        _state.update {
            it.copy(inputText = "", isLoading = true, isSending = true,
                engineStatus = EngineStatus.Loading, engineErrorMessage = "",
                attachedFilePaths = emptyList(), attachedImageUris = emptyList())
        }

        viewModelScope.launch {
            val convId = ensureActiveConversation()
            val toolsJson = AIToolSet.buildOpenAIToolsJson()
            var msgs = _state.value.messages + userMsg
            updateConvMsgs(convId, msgs)
            val startTime = System.currentTimeMillis()
            val systemPrompt = buildString {
                val un = _state.value.userName.ifEmpty { "用户" }
                val an = _state.value.agentName
                append("你的名字是「$an」，你是一个 AI 编程助手，帮助用户解决编程问题。用户的名字是「$un」。在回复时请使用你的名字「$an」自称，使用「$un」称呼用户。")
                val ctx = _state.value.editorContext
                if (ctx.isNotBlank()) append("\n\n${ctx}")
            }

            // 工具调用循环（max 20 rounds）
            var round = 0
            val maxRounds = 20
            var hasToolCalls = true
            val throttle = StreamThrottle()
            var totalUsage = ApiUsage()

            while (hasToolCalls && round < maxRounds) {
                round++
                hasToolCalls = false
                FileLogger.d("ChatVM", "round $round")
                val token = ModelCancellationToken()
                currentToken = token

                val sid = UUID.randomUUID().toString()
                val sm = ChatMessage(id = sid, role = ChatRole.Model, content = "", isStreaming = true, isThinking = true)
                msgs = msgs + sm
                _state.update { it.copy(messages = msgs, displayItems = msgs.toDisplayItems(it.fileOperations)) }

                // 每轮重新绑定回调以便 sid 正确
                toolCallHandler.callback = object : ToolExecutionCallback {
                    override fun onToolStart(name: String, args: Map<String, String>) {
                        FILE_OP_TOOLS[name]?.let { type ->
                            addFileOp(name, type, extractFilePath(name, args), sid)
                        }
                    }
                    override fun onToolResult(name: String, args: Map<String, String>, result: String) {
                        val success = !result.startsWith("[ERROR]") && !result.startsWith("工具执行异常")
                        _pendingFileOpIds.remove(name)?.let { opId ->
                            updateFileOp(opId, if (success) FileOpStatus.Success else FileOpStatus.Error,
                                if (success) null else result.take(100))
                        }
                    }
                }

                val sb = StringBuilder()
                val reasoningSb = StringBuilder()
                val toolCalls = mutableListOf<CloudToolCall>()

                val usage = try {
                    withContext(Dispatchers.IO) {
                        cloudLLMClient.sendMessage(
                            profile = profile, systemPrompt = systemPrompt,
                            messages = msgs.filter { it.id != sid },
                            token = token,
                            onText = { chunk ->
                                sb.append(chunk)
                                val display = sb.toString()
                                throttle.request {
                                    _state.update { state ->
                                        val up = state.messages.map { if (it.id == sid) it.copy(content = display, isThinking = false) else it }
                                        state.copy(messages = up, displayItems = up.toDisplayItems(state.fileOperations))
                                    }
                                }
                            },
                            onReasoning = { rc ->
                                reasoningSb.append(rc)
                            },
                            onToolCalls = { tcs -> toolCalls.addAll(tcs) },
                            toolsJson = if (round == 1) toolsJson else null,
                        )
                    }
                } catch (e: Exception) {
                    if (token.isCancelled) return@launch
                    FileLogger.e("ChatVM", "send error round=$round", e)
                    _state.update { state ->
                        val up = state.messages.map { if (it.id == sid) it.copy(content = it.content.ifEmpty { "请求失败: ${e.message}" }, isStreaming = false, isThinking = false) else it }
                        state.copy(messages = up, displayItems = up.toDisplayItems(state.fileOperations),
                            isLoading = false, isSending = false,
                            engineStatus = EngineStatus.Error, engineErrorMessage = e.message ?: "未知错误")
                    }
                    return@launch
                }

                totalUsage = ApiUsage(
                    promptTokens = totalUsage.promptTokens + usage.third.promptTokens,
                    completionTokens = totalUsage.completionTokens + usage.third.completionTokens,
                )

                // 刷新最终内容
                val fullText = sb.toString()
                val fullReasoning = reasoningSb.toString()
                throttle.flush {
                    _state.update { state ->
                        val up = state.messages.map { if (it.id == sid) it.copy(content = fullText, reasoningContent = fullReasoning, isStreaming = false, isThinking = false) else it }
                        state.copy(messages = up, displayItems = up.toDisplayItems(state.fileOperations))
                    }
                }

                // 判断工具调用
                val extracted = if (toolCalls.isEmpty()) {
                    withContext(Dispatchers.Default) { toolCallHandler.extractToolCalls(fullText) }
                } else null

                if (extracted != null && extracted.isNotEmpty()) {
                    hasToolCalls = true
                    msgs = msgs.map { if (it.id == sid) it.copy(content = fullText, isStreaming = false) else it }
                    for ((name, args) in extracted) {
                        val result = withContext(Dispatchers.IO) { toolCallHandler.executeTool(name, args) }
                        msgs = msgs + ChatMessage(
                            role = ChatRole.Tool, content = result,
                            toolCallId = "call_${UUID.randomUUID().toString().take(8)}", toolName = name,
                        )
                    }
                } else if (toolCalls.isNotEmpty()) {
                    hasToolCalls = true
                    msgs = msgs.map { if (it.id == sid) it.copy(content = fullText, isStreaming = false) else it }
                    for (tc in toolCalls) {
                        val args = try {
                            val m = mutableMapOf<String, String>()
                            val json = org.json.JSONObject(tc.arguments)
                            json.keys().forEach { k -> m[k] = json.optString(k, "") }
                            m
                        } catch (_: Exception) { emptyMap() }
                        val result = withContext(Dispatchers.IO) { toolCallHandler.executeTool(tc.functionName, args) }
                        msgs = msgs + ChatMessage(
                            role = ChatRole.Tool, content = result,
                            toolCallId = tc.id, toolName = tc.functionName,
                        )
                    }
                } else {
                    msgs = msgs.map { if (it.id == sid) it.copy(content = fullText, isStreaming = false) else it }
                }
                _state.update { it.copy(messages = msgs, displayItems = msgs.toDisplayItems(it.fileOperations)) }
            }

            _state.update { it.copy(isLoading = false, isSending = false, engineStatus = EngineStatus.Idle) }
            updateConvMsgs(convId, msgs)
            FileLogger.i("ChatVM", "send done rounds=$round msgs=${msgs.size} usage=$totalUsage")

            // 真实记录 token 消耗
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    usageAnalyticsRepository.recordCall(
                        LlmCallRecord(
                            modelName = profile.modelName,
                            provider = "cloud",
                            promptTokens = totalUsage.promptTokens,
                            completionTokens = totalUsage.completionTokens,
                            durationMs = System.currentTimeMillis() - startTime,
                            success = true,
                            errorMessage = null,
                        )
                    )
                } catch (e: Exception) {
                    FileLogger.e("ChatVM", "record usage failed", e)
                }
            }
        }
    }

    // ════════════════════════════════════════════════
    //  对话管理
    // ════════════════════════════════════════════════

    private suspend fun ensureActiveConversation(): String {
        val s = _state.value
        s.activeConversationId?.let { return it }
        val nc = ConversationEntry(title = "新对话", timestamp = System.currentTimeMillis())
        _state.update { it.copy(conversations = it.conversations + nc, activeConversationId = nc.id) }
        conversationRepo.save(_state.value.conversations)
        return nc.id
    }

    private suspend fun updateConvMsgs(cid: String, msgs: List<ChatMessage>) {
        val updated = _state.value.conversations.map { conv ->
            if (conv.id == cid) {
                val first = msgs.firstOrNull { it.role == ChatRole.User }
                val title = if (conv.title == "新对话" && first != null) first.content.take(30).let { if (it.length < first.content.length) "${it}…" else it } else conv.title
                conv.copy(messages = msgs, title = title, timestamp = System.currentTimeMillis())
            } else conv
        }
        _state.update { it.copy(conversations = updated) }
        conversationRepo.save(updated)
    }

    fun switchConversation(cid: String) {
        cancelSend()
        val c = _state.value.conversations.find { it.id == cid } ?: return
        _state.update { it.copy(activeConversationId = cid, messages = c.messages,
            displayItems = c.messages.toDisplayItems(), engineStatus = EngineStatus.Idle, engineErrorMessage = "", isHistoryOpen = false) }
    }

    fun newConversation() {
        cancelSend()
        _state.update { it.copy(activeConversationId = null, messages = emptyList(), displayItems = emptyList(),
            inputText = "", engineStatus = EngineStatus.Idle, engineErrorMessage = "") }
    }

    fun deleteConversation(cid: String) {
        viewModelScope.launch {
            val up = _state.value.conversations.filter { it.id != cid }
            _state.update { it.copy(conversations = up,
                activeConversationId = if (it.activeConversationId == cid) null else it.activeConversationId,
                messages = if (it.activeConversationId == cid) emptyList() else it.messages,
                displayItems = if (it.activeConversationId == cid) emptyList() else it.displayItems) }
            conversationRepo.save(up)
        }
    }

    fun toggleHistory() { _state.update { it.copy(isHistoryOpen = !it.isHistoryOpen) } }
    fun closeHistory() { _state.update { it.copy(isHistoryOpen = false) } }

    // ════════════════════════════════════════════════
    //  模型配置
    // ════════════════════════════════════════════════

    fun addCloudProfile(name: String, ep: String, key: String, model: String, ctx: Int, maxTk: Int, maxRounds: Int = 200) {
        viewModelScope.launch {
            val p = CloudModelProfile(name = name, apiEndpoint = ep, apiKey = key, modelName = model, contextWindow = ctx, maxTokens = maxTk, maxToolRounds = maxRounds)
            val up = _state.value.cloudModelProfiles + p
            preferencesRepo.setCloudModelProfiles(up)
            if (up.size == 1) preferencesRepo.setActiveCloudProfileId(p.id)
        }
    }
    fun updateCloudProfile(p: CloudModelProfile) { viewModelScope.launch { preferencesRepo.setCloudModelProfiles(_state.value.cloudModelProfiles.map { if (it.id == p.id) p else it }) } }
    fun removeCloudProfile(pid: String) {
        viewModelScope.launch {
            val up = _state.value.cloudModelProfiles.filter { it.id != pid }
            preferencesRepo.setCloudModelProfiles(up)
            if (_state.value.activeCloudProfileId == pid) preferencesRepo.setActiveCloudProfileId(up.firstOrNull()?.id ?: "")
        }
    }
    fun switchCloudProfile(pid: String) { viewModelScope.launch { preferencesRepo.setActiveCloudProfileId(pid) } }
    fun verifyCloudConnection() {
        viewModelScope.launch {
            val p = _state.value.activeProfile ?: return@launch
            _state.update { it.copy(engineStatus = EngineStatus.Loading, engineErrorMessage = "验证中…") }
            cloudLLMClient.verifyConnection(p).onSuccess { _state.update { it.copy(engineStatus = EngineStatus.Idle, engineErrorMessage = "ok") } }
                .onFailure { e -> _state.update { it.copy(engineStatus = EngineStatus.Error, engineErrorMessage = e.message ?: "验证失败") } }
        }
    }
    fun clearError() { _state.update { it.copy(engineErrorMessage = "") } }

    // ── AI 行为设置 ──

    fun setChatMode(mode: ChatMode) {
        viewModelScope.launch { preferencesRepo.setChatMode(mode) }
    }

    fun setAiBehavior(settings: AiBehaviorSettings) {
        viewModelScope.launch { preferencesRepo.setAiBehavior(settings) }
    }
}

private fun List<ChatMessage>.toDisplayItems(fileOps: List<FileOperation> = emptyList()): List<DisplayItem> {
    val base = mapNotNull { msg ->
        if (msg.role == ChatRole.Tool) return@mapNotNull null  // 工具调用结果不显示在对话中
        DisplayItem(id = msg.id, role = when (msg.role) { ChatRole.User -> DisplayRole.User; else -> DisplayRole.Model },
            content = msg.content, isStreaming = msg.isStreaming, isThinking = msg.isThinking,
            reasoningContent = msg.reasoningContent, timestamp = msg.timestamp)
    }
    if (fileOps.isEmpty()) return base
    val opsByMsg = fileOps.groupBy { it.parentMessageId }
    return base.map { item -> opsByMsg[item.id]?.let { item.copy(fileOperations = it) } ?: item }
}
