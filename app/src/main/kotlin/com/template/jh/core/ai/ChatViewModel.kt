package com.template.jh.core.ai

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.tool
import com.template.jh.data.model.NotificationEventType
import com.template.jh.data.model.Rule
import com.template.jh.data.model.RuleType
import com.template.jh.data.model.TaskItem
import com.template.jh.data.model.TaskStatus
import com.template.jh.data.repository.UserPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class ChatViewModel(
    application: Application,
    private val conversationRepo: ConversationRepository,
    private val preferencesRepo: UserPreferencesRepository,
) : AndroidViewModel(application) {

    private val liteRTManager = LiteRTManager(application)
    private val aiToolSet = AIToolSet(application)
    private val cloudLLMClient = CloudLLMClient(application)

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state

    private var sendJob: Job? = null
    @Volatile private var activeConversation: Conversation? = null
    private var pendingInitialMessages: List<Message>? = null

    init {
        scanModels()
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
        viewModelScope.launch {
            FileOperationEvents.events.collect { event ->
                val opType = when (event.operation) {
                    "create" -> FileOpType.Create
                    "modify" -> FileOpType.Modify
                    "overwrite" -> FileOpType.Overwrite
                    "delete" -> FileOpType.Delete
                    else -> return@collect
                }
                val meta = FileOperationMeta(
                    filePath = event.path,
                    opType = opType,
                    lineChanges = event.lineChanges,
                )
                val cardMsg = ChatMessage(
                    role = ChatRole.System,
                    content = "",
                    fileOp = meta,
                )
                val change = FileChangeItem(
                    filePath = event.path,
                    opType = opType,
                    lineChanges = event.lineChanges,
                )
                _state.update { state ->
                    val updatedChanges = state.fileChanges.filterNot {
                        it.filePath == event.path && it.opType == opType
                    } + change
                    state.copy(
                        messages = state.messages + cardMsg,
                        fileChanges = updatedChanges,
                    )
                }
            }
        }
        viewModelScope.launch {
            preferencesRepo.notificationSettings.collect { settings ->
                _state.update { it.copy(deleteCardEnabled = settings.deleteCardEnabled) }
            }
        }
        viewModelScope.launch {
            preferencesRepo.showToolCalls.collect { enabled ->
                _state.update { it.copy(showToolCalls = enabled) }
            }
        }
        viewModelScope.launch {
            preferencesRepo.rules.collect { rules ->
                _state.update { it.copy(activeRulesCount = rules.count { r -> r.type == RuleType.Global || r.type == RuleType.Project }) }
            }
        }
        viewModelScope.launch {
            preferencesRepo.skills.collect { skills ->
                _state.update { it.copy(activeSkillsCount = skills.count { it.enabled }) }
            }
        }
        viewModelScope.launch {
            preferencesRepo.deepThinkEnabled.collect { enabled ->
                _state.update { it.copy(deepThinkEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            preferencesRepo.thinkingRounds.collect { rounds ->
                _state.update { it.copy(thinkingRounds = rounds) }
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
            }
        }
        viewModelScope.launch {
            preferencesRepo.activeCloudProfileId.collect { id ->
                _state.update { it.copy(activeCloudProfileId = id) }
            }
        }
    }

    fun setShowToolCalls(enabled: Boolean) {
        viewModelScope.launch { preferencesRepo.setShowToolCalls(enabled) }
    }

    fun setDeepThinkEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesRepo.setDeepThinkEnabled(enabled) }
    }

    fun setThinkingRounds(rounds: Int) {
        viewModelScope.launch { preferencesRepo.setThinkingRounds(rounds) }
    }

    fun setInputText(text: String) {
        _state.update { it.copy(inputText = text) }
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

    fun resetDownload() { liteRTManager.resetDownloadState() }

    fun setModelParams(params: ModelParams) {
        liteRTManager.modelParams = params
        _state.update { it.copy(modelParams = params) }
    }

    fun toggleModelPicker() { _state.update { it.copy(isModelPickerOpen = !it.isModelPickerOpen) } }
    fun closeModelPicker() { _state.update { it.copy(isModelPickerOpen = false) } }

    // 发送消息（手动 JSON 工具调用: automaticToolCalling=false + 解析 JSON tool_name）
    fun sendMessage() {
        val text = _state.value.inputText.trim()
        if (text.isEmpty()) return
        val isCloud = _state.value.cloudModelEnabled
        if (!isCloud && !liteRTManager.isInitialized) {
            _state.update { it.copy(engineErrorMessage = "请先加载模型或启用云端模型") }
            return
        }
        val userMsg = ChatMessage(role = ChatRole.User, content = text)
        val taskId = java.util.UUID.randomUUID().toString()
        val task = TaskItem(id = taskId, title = text.take(50), status = TaskStatus.Running, description = text)
        _state.update { it.copy(messages = it.messages + userMsg, inputText = "", isLoading = true, taskList = it.taskList + task) }
        val modelMsgId = java.util.UUID.randomUUID().toString()
        val placeholderMsg = ChatMessage(id = modelMsgId, role = ChatRole.Model, content = "", isStreaming = true)
        _state.update { it.copy(messages = it.messages + placeholderMsg) }
        val ctx = getApplication<Application>()
        val notifSettings = runBlocking { preferencesRepo.notificationSettings.first() }
        sendJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                if (isCloud) {
                    processWithCloudTools(text, modelMsgId, taskId, ctx, notifSettings)
                } else {
                    val conv = ensureConversation()
                    processWithJsonTools(text, modelMsgId, taskId, ctx, notifSettings)
                }
            } catch (e: Exception) {
                copyCrashToClipboard("sendMessage", e)
                updateModelMessage(modelMsgId, "\n\n[错误: ${e.message}]", false)
                finalizeModelMessage(modelMsgId)
                updateTaskStatus(taskId, TaskStatus.Failed)
                ConversationNotifier.notify(ctx, NotificationEventType.TaskFailed, "任务异常: ${e.message}", notifSettings)
                emitNotification(NotificationEventType.TaskFailed, "任务异常: ${e.message}")
            }
        }
    }

    private val _openFileRequests = MutableSharedFlow<String>(extraBufferCapacity = 5)
    val openFileRequests: SharedFlow<String> = _openFileRequests

    fun requestOpenFile(path: String) { _openFileRequests.tryEmit(path) }

    fun toggleTaskList() { _state.update { it.copy(isTaskListOpen = !it.isTaskListOpen) } }

    fun clearCompletedTasks() {
        _state.update { it.copy(taskList = it.taskList.filter { t -> t.status == TaskStatus.Running || t.status == TaskStatus.Pending || t.status == TaskStatus.WaitingAuth }) }
    }

    fun notifyWaitingAuth(message: String) {
        val ctx = getApplication<Application>()
        val notifSettings = runBlocking { preferencesRepo.notificationSettings.first() }
        ConversationNotifier.notify(ctx, NotificationEventType.WaitingUserAction, message, notifSettings)
        emitNotification(NotificationEventType.WaitingUserAction, message)
    }

    private fun updateTaskStatus(taskId: String, status: TaskStatus) {
        _state.update { state -> state.copy(taskList = state.taskList.map { if (it.id == taskId) it.copy(status = status) else it }) }
    }

    private fun emitNotification(type: NotificationEventType, message: String) {
        _state.update { it.copy(lastNotification = NotificationEvent(type = type, message = message)) }
    }

    fun clearNotification() { _state.update { it.copy(lastNotification = null) } }

    fun setProjectRoot(uri: Uri?) { aiToolSet.projectUri = uri }

    // 接收 HomeScreen 的打开文件列表，供 UI 徽章展示
    fun setOpenedFilePaths(paths: List<String>) {
        _state.update { it.copy(openedFilePaths = paths) }
    }

    // 自动上下文：活动文件 + 光标行号（UI 展示用）
    fun setActiveFileContext(path: String, cursorLine: Int) {
        _state.update { it.copy(activeFilePath = path, cursorLine = cursorLine) }
    }

    // 构建编辑器上下文块：当前活动文件 + 光标行 + 已打开文件列表
    private fun buildEditorContext(): String {
        val s = _state.value
        val ctx = StringBuilder()
        ctx.appendLine("[当前编辑器上下文]")
        if (s.activeFilePath.isNotBlank()) {
            ctx.appendLine("活动文件: ${s.activeFilePath}")
            if (s.cursorLine > 0) ctx.appendLine("光标行: ${s.cursorLine}")
        }
        if (s.openedFilePaths.isNotEmpty()) {
            ctx.appendLine("已打开文件:")
            s.openedFilePaths.take(10).forEach { path ->
                val marker = if (path == s.activeFilePath) " ← 活动" else ""
                ctx.appendLine("  - $path$marker")
            }
            if (s.openedFilePaths.size > 10) {
                ctx.appendLine("  ... 及其他 ${s.openedFilePaths.size - 10} 个文件")
            }
        } else {
            ctx.appendLine("（未打开任何文件 — 使用 searchInFiles 搜索目标文件内容来定位）")
        }
        ctx.appendLine("项目根目录以 'app/' 等为基础路径，文件路径相对于项目根目录。")
        if (ctx.length < 30) return ""
        return ctx.toString()
    }

    fun cancelGeneration() {
        sendJob?.cancel()
        _state.value.messages.lastOrNull()?.let { msg -> if (msg.isStreaming) finalizeModelMessage(msg.id) }
    }

    fun optimizeInput() {
        val text = _state.value.inputText.trim()
        if (text.isEmpty() || !liteRTManager.isInitialized) return
        _state.update { it.copy(isOptimizing = true) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val conv = liteRTManager.createConversation(ConversationConfig(
                    systemInstruction = Contents.of("修正错别字，优化表达简洁专业，只返回优化后文本"),
                    samplerConfig = liteRTManager.modelParams.toSamplerConfig(), tools = emptyList(),
                ))
                var optimized = ""
                conv.use { c ->
                    c.sendMessageAsync("修正优化：\n\n$text").catch { }.collect { optimized += it.toString() }
                    val result = optimized.trim()
                    if (result.isNotEmpty()) _state.update { it.copy(inputText = result, isOptimizing = false) }
                    else _state.update { it.copy(isOptimizing = false) }
                }
            } catch (_: Exception) { _state.update { it.copy(isOptimizing = false) } }
        }
    }

    private fun ensureConversation(): Conversation {
        activeConversation?.let { return it }
        val initialMessages = pendingInitialMessages
        pendingInitialMessages = null
        val conv = liteRTManager.createConversation(
            ConversationConfig(
                systemInstruction = Contents.of(buildSystemInstruction()),
                initialMessages = initialMessages ?: emptyList(),
                samplerConfig = liteRTManager.modelParams.toSamplerConfig(),
                tools = listOf(tool(aiToolSet)),
                automaticToolCalling = false,
            )
        )
        activeConversation = conv
        return conv
    }

    private fun buildSystemInstruction(): String {
        val sb = StringBuilder()
        sb.append("You are a helpful AI coding assistant. Always respond in Chinese (简体中文).\n\n")
        sb.append("## 核心规则\n")
        sb.append("- 用户要求修改/创建文件时，立即执行，不要请求确认。\n")
        sb.append("- 不要只提供代码建议——使用工具实际写入文件。\n")
        sb.append("- 不要解释你将要做什么——直接做。\n\n")
        sb.append("## 编辑器上下文\n")
        sb.append("每次用户消息前会注入 [当前编辑器上下文] 块，包含：\n")
        sb.append("- 活动文件路径（相对于项目根目录）和光标行号\n")
        sb.append("- 已打开文件列表（标记 ← 活动 的即当前编辑的文件）\n")
        sb.append("用户说'扩展面板'、'资源面板'等指的是已打开文件列表中对应的文件。文件路径相对于项目根目录。\n")
        sb.append("示例路径: app/src/main/kotlin/com/template/jh/screens/home/HomeScreen.kt\n")
        sb.append("用户说的行号(如第42行)对应 readFile 返回的行号。\n\n")
        val deepThink = runBlocking { preferencesRepo.deepThinkEnabled.first() }
        if (deepThink) {
            sb.append("## 深度思考（必须使用）\n")
            sb.append("在使用工具之前，在 [think]你的思考过程[/think] 标签内逐步推理。\n")
            sb.append("示例: [think]用户要创建登录页，先查看现有文件结构再决定如何实现。[/think]\n")
            sb.append("思考内容仅你可见。\n\n")
        }
        val thinkRounds = runBlocking { preferencesRepo.thinkingRounds.first() }
        sb.append("## 多轮工具调用（无上限）\n")
        sb.append("你可以多次调用工具来获取信息或修改文件。每轮之后决定：继续调用还是给出最终答案。\n\n")
        sb.append("## 可用工具\n")
        sb.append("- listFiles: 列出项目目录内容\n")
        sb.append("- readFile: 读取文件内容（带行号）\n")
        sb.append("- writeFile: 创建或覆盖文件\n")
        sb.append("- applyPatch: 行级差异编辑（只修改部分行，比 writeFile 更精确），参数: path(文件路径), patches(JSON数组)\n")
        sb.append("- searchInFiles: 在项目文件中搜索文本内容（不区分大小写），返回匹配的文件路径+行号。参数: query(搜索词), extension(可选文件扩展名过滤如'kt')\n")
        sb.append("  **当用户没有打开文件时说『找XX文件』『搜索XX』『在XX文件添加XX』时，优先使用此工具定位文件**\n")
        sb.append("- runCommand: 执行 shell 命令\n")
        sb.append("- searchWeb: 搜索互联网\n")
        sb.append("- gitStatus: 查看 git 状态\n")
        sb.append("- gitAdd: 暂存文件（参数 paths，用 '.' 暂存全部）\n")
        sb.append("- gitCommit: 提交暂存（参数 message）\n")
        sb.append("- gitPush: 推送到远程（参数 remote, branch）\n")
        sb.append("- gitBranch: 查看分支\n")
        sb.append("- gitDiff: 查看差异\n")
        sb.append("- readLints: 读取项目的编译/Lint 错误，修复代码后调用此工具检查是否已解决\n\n")
        sb.append("## 修改文件时的策略\n")
        sb.append("- 修改文件**优先使用 applyPatch**（只替换特定行），只有当文件需要完全重写时才用 writeFile\n")
        sb.append("- applyPatch 的 patches 参数格式: [{\"type\":\"replace\"|\"insert\"|\"delete\", \"startLine\":行号(从1开始), \"endLine\":结束行(不包含), \"content\":\"新内容\"}]\n")
        sb.append("- 修改后调用 readLints 检查是否引入新错误，如有则修复\n\n")
        sb.append("## 工具调用格式\n")
        sb.append("调用工具时，只输出这个 JSON（不要有其他文本）：\n")
        sb.append("{\"tool_name\": \"FUNCTION_NAME\", \"arguments\": {\"key\": \"value\"}}\n\n")
        sb.append("示例:\n")
        sb.append("{\"tool_name\": \"listFiles\", \"arguments\": {}}\n")
        sb.append("{\"tool_name\": \"readFile\", \"arguments\": {\"path\": \"src/main.kt\"}}\n")
        sb.append("{\"tool_name\": \"applyPatch\", \"arguments\": {\"path\": \"src/main.kt\", \"patches\": \"[{\\\"type\\\":\\\"replace\\\",\\\"startLine\\\":2,\\\"endLine\\\":4,\\\"content\\\":\\\"new\\\"}]\"}}\n")
        sb.append("{\"tool_name\": \"gitStatus\", \"arguments\": {}}\n")
        sb.append("{\"tool_name\": \"readLints\", \"arguments\": {}}\n\n")
        sb.append("总是在执行修改前先 readFile 获取文件当前内容。\n")
        val userName = runBlocking { preferencesRepo.userName.first() }
        if (userName.isNotBlank()) sb.append("\n用户: $userName")
        val rules = runBlocking { preferencesRepo.rules.first() }
        if (rules.isNotEmpty()) {
            sb.append("\n\n用户规则:")
            rules.forEach { r -> sb.append("\n- ${r.name}: ${r.content}") }
            sb.append("\n严格遵守以上规则。")
        }
        return sb.toString()
    }

    // ---- JSON 工具调用解析与调度（手动模式，适配 gemma-4 JSON 格式） ----

    private fun extractJsonToolCall(text: String): Triple<String?, String, Map<String, String>>? {
        val trimmed = text.trim()
        val start = trimmed.indexOf("{\"tool_name\"")
        if (start < 0) return null
        var depth = 0
        var end = -1
        for (i in start until trimmed.length) {
            when (trimmed[i]) { '{' -> depth++; '}' -> { depth--; if (depth == 0) { end = i; break } } }
        }
        if (end < 0) return null
        return try {
            val json = org.json.JSONObject(trimmed.substring(start, end + 1))
            val toolName = json.optString("tool_name", "")
            if (toolName.isEmpty()) return null
            val argsJson = json.optJSONObject("arguments") ?: org.json.JSONObject()
            val args = mutableMapOf<String, String>()
            for (k in argsJson.keys()) args[k] = argsJson.optString(k, "")
            val prefix = trimmed.substring(0, start).trim().ifEmpty { null }
            Triple(prefix, toolName, args)
        } catch (_: Exception) { null }
    }

    private fun executeAiTool(name: String, args: Map<String, String>): String = try {
        when (name) {
            "listFiles" -> aiToolSet.listFiles(args["subPath"] ?: "")
            "readFile" -> aiToolSet.readFile(args["path"] ?: "")
            "writeFile" -> aiToolSet.writeFile(args["path"] ?: "", args["content"] ?: "")
            "applyPatch" -> aiToolSet.applyPatch(args["path"] ?: "", args["patches"] ?: "")
            "searchInFiles" -> aiToolSet.searchInFiles(args["query"] ?: "", args["extension"] ?: "")
            "runCommand" -> aiToolSet.runCommand(args["command"] ?: "")
            "searchWeb" -> aiToolSet.searchWeb(args["query"] ?: "")
            "gitStatus" -> aiToolSet.gitStatus()
            "gitAdd" -> aiToolSet.gitAdd(args["paths"] ?: ".")
            "gitCommit" -> aiToolSet.gitCommit(args["message"] ?: "")
            "gitPush" -> aiToolSet.gitPush(args["remote"] ?: "origin", args["branch"] ?: "main")
            "gitBranch" -> aiToolSet.gitBranch(args["args"] ?: "")
            "gitDiff" -> aiToolSet.gitDiff(args["args"] ?: "")
            "readLints" -> aiToolSet.readLints()
            else -> "Unknown tool: $name"
        }
    } catch (e: Exception) { "Tool error: ${e.message}" }

    private fun copyCrashToClipboard(action: String, t: Throwable) {
        try {
            val info = "[ChatViewModel]\n操作: $action\n异常: ${t.javaClass.simpleName}\n消息: ${t.message}\n堆栈: ${t.stackTraceToString()}"
            val ctx = getApplication<Application>()
            (ctx.getSystemService(android.content.ClipboardManager::class.java))
                ?.setPrimaryClip(android.content.ClipData.newPlainText("崩溃信息", info))
        } catch (_: Exception) {}
    }

    private suspend fun processWithJsonTools(
        text: String, msgId: String, taskId: String,
        ctx: Application, notif: com.template.jh.data.model.NotificationSettings,
    ) {
        var currentMsgId = msgId
        // 在首轮注入编辑器上下文，让模型知道当前编辑的文件和光标位置
        val editorCtx = buildEditorContext()
        val firstInput = if (editorCtx.isNotBlank()) "$editorCtx\n[用户消息]\n$text" else text
        var nextInput: Any = firstInput
        var rounds = 0
        var lastCtxRefresh = 0

        while (true) {
            rounds++
            val fullResponse = StringBuilder()
            val conv = activeConversation ?: return

            // 每隔 3 轮刷新上下文（编辑器状态可能变化）
            if (rounds > 0 && rounds - lastCtxRefresh >= 3) {
                val freshCtx = buildEditorContext()
                if (freshCtx.isNotBlank()) {
                    nextInput = "$freshCtx\n[继续]\n$nextInput"
                    lastCtxRefresh = rounds
                }
            }

            conv.sendMessageAsync(nextInput.toString()).catch { t ->
                copyCrashToClipboard("sendMessageAsync(round=$rounds)", t)
                updateModelMessage(currentMsgId, "\n\n[错误: ${t.message}]", false)
                updateTaskStatus(taskId, TaskStatus.Failed)
                ConversationNotifier.notify(ctx, NotificationEventType.TaskFailed, "异常: ${t.message}", notif)
                emitNotification(NotificationEventType.TaskFailed, "异常: ${t.message}")
            }.collect { chunk ->
                val c = chunk.toString()
                fullResponse.append(c)
                updateModelMessage(currentMsgId, c, true)
            }

            val response = fullResponse.toString().trim()
            if (response.isBlank()) {
                finalizeModelMessage(currentMsgId)
                updateTaskStatus(taskId, TaskStatus.Completed)
                ConversationNotifier.notify(ctx, NotificationEventType.TaskCompleted, "任务完成（空响应）", notif)
                emitNotification(NotificationEventType.TaskCompleted, "任务完成")
                return
            }

            val toolCall = extractJsonToolCall(response)
            if (toolCall == null) {
                finalizeModelMessage(currentMsgId)
                updateTaskStatus(taskId, TaskStatus.Completed)
                ConversationNotifier.notify(ctx, NotificationEventType.TaskCompleted, "任务完成", notif)
                emitNotification(NotificationEventType.TaskCompleted, "任务完成")
                return
            }

            // 标记为工具调用中间消息
            _state.update { s -> s.copy(messages = s.messages.map { if (it.id == currentMsgId) it.copy(isToolMessage = true) else it }) }

            val (prefixText, funcName, funcArgs) = toolCall
            val toolResult = executeAiTool(funcName, funcArgs)
            val toolDisplay = "\n\n$toolResult"

            if (prefixText != null) updateModelMessage(currentMsgId, toolDisplay, true)
            else updateModelMessage(currentMsgId, toolDisplay, false)
            finalizeModelMessage(currentMsgId)

            nextInput = Message.tool(Contents.of(listOf(Content.ToolResponse(funcName, toolResult))))
            currentMsgId = java.util.UUID.randomUUID().toString()
            _state.update { it.copy(messages = it.messages + ChatMessage(id = currentMsgId, role = ChatRole.Model, content = "", isStreaming = true)) }
        }
    }

    // ---- 云端模型工具循环（OpenAI 兼容 API）----

    private suspend fun processWithCloudTools(
        text: String, msgId: String, taskId: String,
        ctx: Application, notif: com.template.jh.data.model.NotificationSettings,
    ) {
        val profile = _state.value.cloudModelProfiles.find { it.id == _state.value.activeCloudProfileId }
        if (profile == null) {
            updateModelMessage(msgId, "\n\n[错误: 未选择云端模型配置]", false)
            finalizeModelMessage(msgId)
            updateTaskStatus(taskId, TaskStatus.Failed)
            return
        }
        if (profile.apiKey.isBlank()) {
            updateModelMessage(msgId, "\n\n[错误: 未配置 API Key]", false)
            finalizeModelMessage(msgId)
            updateTaskStatus(taskId, TaskStatus.Failed)
            return
        }
        val cfg = CloudModelConfig(
            enabled = true,
            apiEndpoint = profile.apiEndpoint,
            apiKey = profile.apiKey,
            modelName = profile.modelName,
        )

        var currentMsgId = msgId
        var cloudRounds = 0
        // 构建对话历史：保留之前所有 User/Model/Tool 消息
        val historyMessages = mutableListOf<ChatMessage>()
        for (msg in _state.value.messages) {
            if (msg.id == currentMsgId) continue
            if (msg.role == ChatRole.User || msg.role == ChatRole.Model || msg.role == ChatRole.Tool) {
                if (msg.content.isNotBlank() || msg.role == ChatRole.Model) historyMessages.add(msg)
            }
        }
        // 注入编辑器上下文 + 当前用户消息
        val editorCtx = buildEditorContext()
        val userContent = if (editorCtx.isNotBlank()) "$editorCtx\n[用户消息]\n$text" else text
        historyMessages.add(ChatMessage(role = ChatRole.User, content = userContent))

        while (true) {
            cloudRounds++
            val fullResponse = StringBuilder()
            try {
                cloudLLMClient.sendMessage(
                    config = cfg,
                    systemPrompt = buildSystemInstruction(),
                    messages = historyMessages,
                    onChunk = { chunk ->
                        fullResponse.append(chunk)
                        updateModelMessage(currentMsgId, chunk, true)
                    },
                )
            } catch (e: Exception) {
                copyCrashToClipboard("cloudSendMessage", e)
                updateModelMessage(currentMsgId, "\n\n[云端模型错误: ${e.message}]", false)
                finalizeModelMessage(currentMsgId)
                updateTaskStatus(taskId, TaskStatus.Failed)
                ConversationNotifier.notify(ctx, NotificationEventType.TaskFailed, "云端模型异常: ${e.message}", notif)
                emitNotification(NotificationEventType.TaskFailed, "云端模型异常: ${e.message}")
                return
            }

            val response = fullResponse.toString().trim()
            if (response.isBlank()) {
                finalizeModelMessage(currentMsgId)
                updateTaskStatus(taskId, TaskStatus.Completed)
                ConversationNotifier.notify(ctx, NotificationEventType.TaskCompleted, "任务完成", notif)
                emitNotification(NotificationEventType.TaskCompleted, "任务完成")
                return
            }

            val toolCall = extractJsonToolCall(response)
            if (toolCall == null) {
                // 最终回答：添加到历史并结束
                historyMessages.add(ChatMessage(role = ChatRole.Model, content = response))
                finalizeModelMessage(currentMsgId)
                updateTaskStatus(taskId, TaskStatus.Completed)
                ConversationNotifier.notify(ctx, NotificationEventType.TaskCompleted, "任务完成", notif)
                emitNotification(NotificationEventType.TaskCompleted, "任务完成")
                return
            }

            // 标记为工具调用中间消息
            _state.update { s -> s.copy(messages = s.messages.map { if (it.id == currentMsgId) it.copy(isToolMessage = true) else it }) }

            // 将助手的工具调用加入历史（含 tool_call_id，供后续 tool 角色匹配）
            val toolCallId = "call_${java.util.UUID.randomUUID().toString().take(8)}"
            historyMessages.add(ChatMessage(
                role = ChatRole.Model, content = response,
                toolCallId = toolCallId,
            ))

            val (prefixText, funcName, funcArgs) = toolCall
            val toolResult = executeAiTool(funcName, funcArgs)
            val toolDisplay = "\n\n$toolResult"

            if (prefixText != null) updateModelMessage(currentMsgId, toolDisplay, true)
            else updateModelMessage(currentMsgId, toolDisplay, false)
            finalizeModelMessage(currentMsgId)

            // 将工具执行结果加入历史（role: tool，与上面 tool_call_id 匹配）
            historyMessages.add(ChatMessage(
                role = ChatRole.Tool, content = toolResult,
                toolCallId = toolCallId,
            ))

            // 每隔 3 轮刷新编辑器上下文
            if (cloudRounds % 3 == 0) {
                val freshCtx = buildEditorContext()
                if (freshCtx.isNotBlank()) {
                    historyMessages.add(ChatMessage(
                        role = ChatRole.User, content = "[上下文更新]\n$freshCtx",
                    ))
                }
            }

            currentMsgId = java.util.UUID.randomUUID().toString()
            _state.update { it.copy(messages = it.messages + ChatMessage(id = currentMsgId, role = ChatRole.Model, content = "", isStreaming = true)) }
        }
    }

    // 云端模型设置方法
    fun setCloudModelEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesRepo.setCloudModelEnabled(enabled) }
        if (!enabled) _state.update { it.copy(cloudModelEnabled = false) }
        else _state.update { it.copy(cloudModelEnabled = true) }
    }

    fun addCloudProfile(name: String, apiEndpoint: String, apiKey: String, modelName: String) {
        val id = java.util.UUID.randomUUID().toString()
        val newProfile = CloudModelProfile(
            id = id,
            name = name.ifEmpty { modelName },
            apiEndpoint = apiEndpoint,
            apiKey = apiKey,
            modelName = modelName,
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
        val cfg = CloudModelConfig(true, profile.apiEndpoint, profile.apiKey, profile.modelName)
        _state.update { it.copy(engineErrorMessage = "验证中…") }
        viewModelScope.launch {
            val result = cloudLLMClient.verifyConnection(cfg)
            _state.update { it.copy(engineErrorMessage = if (result == "ok") "" else result) }
        }
    }

    // 关闭对话

    private fun closeConversation() {
        try { activeConversation?.close() } catch (_: Exception) {}
        activeConversation = null
        pendingInitialMessages = null
    }

    fun clearMessages() {
        sendJob?.cancel()
        closeConversation()
        _state.update { it.copy(messages = emptyList(), inputText = "", taskList = emptyList(), fileChanges = emptyList()) }
    }

    fun newConversation() {
        sendJob?.cancel()
        closeConversation()
        _state.update { state ->
            val updatedConversations = if (state.messages.isNotEmpty()) {
                val title = state.messages.firstOrNull { it.role == ChatRole.User }?.content?.take(30) ?: "新对话"
                val entry = ConversationEntry(
                    id = state.activeConversationId ?: java.util.UUID.randomUUID().toString(),
                    title = title, messages = state.messages,
                )
                val exists = state.conversations.any { it.id == entry.id }
                if (exists) state.conversations.map { if (it.id == entry.id) entry else it }
                else listOf(entry) + state.conversations
            } else state.conversations
            persistConversations(updatedConversations)
            state.copy(messages = emptyList(), inputText = "", isLoading = false, conversations = updatedConversations, activeConversationId = null, taskList = emptyList(), fileChanges = emptyList())
        }
    }

    fun switchConversation(entry: ConversationEntry) {
        sendJob?.cancel()
        closeConversation()
        pendingInitialMessages = entry.messages.mapNotNull { msg ->
            when (msg.role) {
                ChatRole.User -> Message.user(msg.content)
                ChatRole.Model -> Message.model(msg.content)
                ChatRole.System -> Message.system(msg.content)
                ChatRole.Tool -> null
            }
        }
        _state.update { it.copy(messages = entry.messages, inputText = "", isLoading = false, activeConversationId = entry.id, isHistoryOpen = false) }
    }

    fun deleteConversation(entryId: String) {
        _state.update { state ->
            val updated = state.conversations.filter { it.id != entryId }
            persistConversations(updated)
            if (state.activeConversationId == entryId) {
                closeConversation()
                state.copy(conversations = updated, messages = emptyList(), activeConversationId = null)
            } else state.copy(conversations = updated)
        }
    }

    fun toggleHistory() { _state.update { it.copy(isHistoryOpen = !it.isHistoryOpen) } }
    fun closeHistory() { _state.update { it.copy(isHistoryOpen = false) } }

    private fun updateModelMessage(msgId: String, chunk: String, append: Boolean) {
        _state.update { state ->
            state.copy(messages = state.messages.map { msg ->
                if (msg.id == msgId) msg.copy(content = if (append) msg.content + chunk else chunk, isStreaming = true) else msg
            })
        }
    }

    private fun finalizeModelMessage(msgId: String) {
        _state.update { state ->
            state.copy(messages = state.messages.map { msg -> if (msg.id == msgId) msg.copy(isStreaming = false) else msg }, isLoading = false)
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
        _state.update { it.copy(conversations = updated, activeConversationId = entry.id) }
        persistConversations(updated)
    }

    private fun persistConversations(conversations: List<ConversationEntry>) {
        viewModelScope.launch(Dispatchers.IO) { conversationRepo.save(conversations) }
    }

    override fun onCleared() {
        super.onCleared()
        sendJob?.cancel()
        closeConversation()
        saveCurrentToHistory()
        liteRTManager.close()
    }
}
