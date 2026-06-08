package com.template.jh.core.ai

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
import com.template.jh.data.model.Rule
import com.template.jh.data.model.RuleType
import com.template.jh.core.analytics.LlmCallRecord
import com.template.jh.core.analytics.UsageStats
import com.template.jh.data.repository.UsageAnalyticsRepository
import com.template.jh.data.repository.UserPreferencesRepository
import com.template.jh.core.utils.FileLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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
    private val aiToolSet = AIToolSet(application, fileManager)
    private val cloudLLMClient = CloudLLMClient(application)
    private val userPreferencesRepo = preferencesRepo

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state
    val usageStats: StateFlow<UsageStats> = usageAnalyticsRepo.stats.stateIn(
        viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), UsageStats()
    )

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
            FileOperationEvents.events.collect { event ->
                // 文件操作事件由 EditorScreenState 的 createReviewForEvent 处理
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

    fun setInputText(text: String) {
        _state.update { it.copy(inputText = text) }
    }

    // 图片附件管理
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

    // 发送消息（手动 JSON 工具调用 + 活动状态反馈）
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
        val fileContent = buildFileAttachmentBlock()
        val userContent = buildString {
            append(text)
            if (fileContent.isNotBlank()) append(fileContent)
            if (images.isNotEmpty()) append("\n[已附加 ${images.size} 张图片]")
        }
        val userMsg = ChatMessage(role = ChatRole.User, content = userContent)
        val modelMsgId = java.util.UUID.randomUUID().toString()
        val placeholderMsg = ChatMessage(id = modelMsgId, role = ChatRole.Model, content = "", isStreaming = true)
        _state.update { it.copy(messages = it.messages + userMsg + placeholderMsg, inputText = "", attachedImageUris = emptyList(), attachedFileRefs = emptyList(), isLoading = true) }
        val ctx = getApplication<Application>()
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
                if (isCloud) {
                    processWithCloudTools(text, modelMsgId, ctx)
                } else {
                    // LiteRT: 上下文超出阈值时压缩并重建 Conversation
                    val currentMsgs = _state.value.messages
                    if (estimateContextTokens(currentMsgs) > COMPRESS_THRESHOLD) {
                        val compressed = compressMessages(currentMsgs)
                        _state.update { it.copy(messages = compressed) }
                        resetConversation()
                    }
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

    // 接受文件的所有修改
    fun acceptAllChanges(filePath: String) {
        FileOperationEvents.notifyAcceptAll(filePath)
    }

    // 拒绝文件的所有修改
    fun rejectAllChanges(filePath: String) {
        FileOperationEvents.notifyRejectAll(filePath)
    }





    fun setProjectRoot(uri: Uri?) {
        aiToolSet.projectUri = uri
        uri?.let { fileManager.setProjectUri(it) } ?: fileManager.clearProjectUri()
        // 从 URI 提取文件夹显示名并存入状态
        val name = uri?.let { extractFolderName(it) } ?: ""
        _state.update { it.copy(projectRootName = name) }
    }

    private fun extractFolderName(uri: Uri): String {
        return try {
            val docId = android.provider.DocumentsContract.getTreeDocumentId(uri)
            java.net.URLDecoder.decode(docId.substringAfterLast('%'), "UTF-8")
                .substringAfterLast('/')
                .substringAfterLast(':')
                .ifEmpty { "" }
        } catch (_: Exception) { "" }
    }

    // 接收 HomeScreen 的打开文件列表，供 UI 徽章展示
    fun setOpenedFilePaths(paths: List<String>) {
        _state.update { it.copy(openedFilePaths = paths) }
    }

    // 自动上下文：活动文件 + 光标行号（UI 展示用）
    fun setActiveFileContext(path: String, cursorLine: Int) {
        _state.update { it.copy(activeFilePath = path, cursorLine = cursorLine) }
    }

    // 已修改文件路径列表
    fun setModifiedFilePaths(paths: List<String>) {
        _state.update { it.copy(modifiedFilePaths = paths) }
    }

    // 添加文件附件：预读内容并存入状态（同名文件自动追加路径前缀区分）
    fun attachFile(path: String, name: String) {
        val refs = _state.value.attachedFileRefs
        val exists = refs.any { it.path == path }
        if (exists) return
        val content = runCatching { fileManager?.readFileRaw(path) }.getOrNull()
        if (content == null) return
        // 同名文件自动去重：同名但路径不同时标记
        val displayName = if (refs.any { it.name == name }) "${name} (${path.substringBeforeLast('/')})" else name
        _state.update { it.copy(attachedFileRefs = refs + AttachedFile(name = displayName, path = path, content = content)) }
    }

    // 移除指定文件附件
    fun detachFile(index: Int) {
        _state.update { it.copy(attachedFileRefs = it.attachedFileRefs.toMutableList().also { list -> if (index in list.indices) list.removeAt(index) }) }
    }

    // 用量统计
    fun resetUsageStats() {
        viewModelScope.launch { usageAnalyticsRepo.resetStats() }
    }

    private suspend fun recordUsage(call: LlmCallRecord) {
        usageAnalyticsRepo.recordCall(call)
    }

    // 构建编辑器上下文块：当前活动文件 + 光标行 + 已打开文件列表 + 修改状态
    private fun buildEditorContext(): String {
        val s = _state.value
        val ctx = StringBuilder()
        ctx.appendLine("[当前编辑器上下文]")
        if (s.projectRootName.isNotBlank()) {
            ctx.appendLine("项目: ${s.projectRootName}")
        }
        if (s.activeFilePath.isNotBlank()) {
            ctx.appendLine("活动文件: ${s.activeFilePath}")
            if (s.cursorLine > 0) ctx.appendLine("光标行: ${s.cursorLine}")
        }
        if (s.openedFilePaths.isNotEmpty()) {
            ctx.appendLine("已打开文件:")
            s.openedFilePaths.take(10).forEach { path ->
                val marker = if (path == s.activeFilePath) " ← 活动" else ""
                val dirty = if (path in s.modifiedFilePaths) " [已修改]" else ""
                ctx.appendLine("  - $path$marker$dirty")
            }
            if (s.openedFilePaths.size > 10) {
                ctx.appendLine("  ... 及其他 ${s.openedFilePaths.size - 10} 个文件")
            }
        } else {
            ctx.appendLine("（未打开任何文件 — 使用 searchInFiles 搜索目标文件内容来定位）")
        }
        ctx.appendLine("文件路径相对于项目根目录。")
        ctx.appendLine("标注 [已修改] 的文件存在未保存的更改，活动文件光标所在行号已标注。")
        if (ctx.length < 30) return ""
        return ctx.toString()
    }

    // 构建文件附件内容块（从 attachedFileRefs 中读取预存的文件内容）
    private fun buildFileAttachmentBlock(): String {
        val refs = _state.value.attachedFileRefs
        if (refs.isEmpty()) return ""
        val block = StringBuilder()
        refs.forEach { f ->
            block.appendLine()
            block.appendLine("[文件: ${f.path}]")
            block.appendLine("```${f.name.substringAfterLast('.')}")
            block.append(f.content)
            if (!f.content.endsWith('\n')) block.appendLine()
            block.appendLine("```")
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
        _state.update { it.copy(isOptimizing = true) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val conv = liteRTManager.createConversation(ConversationConfig(
                    systemInstruction = Contents.of(buildOptimizePrompt()),
                    samplerConfig = liteRTManager.modelParams.toSamplerConfig(),
                    tools = emptyList(),
                ))
                var optimized = ""
                conv.use { c ->
                    c.sendMessageAsync(text).catch { }.collect { optimized += it.toString() }
                    val result = optimized.trim()
                    if (result.isNotEmpty()) _state.update { it.copy(inputText = result, isOptimizing = false) }
                    else _state.update { it.copy(isOptimizing = false) }
                }
            } catch (_: Exception) { _state.update { it.copy(isOptimizing = false) } }
        }
    }

    private fun buildOptimizePrompt(): String {
        return """你是一个专业的编程助手，负责优化用户的输入内容。

优化目标：
1. 修正错别字和语法错误
2. 使表达更简洁、专业
3. 保持原意不变
4. 如果是代码相关问题，确保术语准确

重要规则：
- 只返回优化后的文本内容
- 不要添加解释、前缀或后缀
- 不要改变用户的原始意图
- 如果是代码片段，保持代码格式和缩进

示例：
输入："帮我写一个登陆页面，要有用户名密码输入框"
输出："创建一个登录页面，包含用户名和密码输入框"

输入："这个函数有bug，需要修复一下"
输出："该函数存在缺陷，需要进行修复""".trimIndent()
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
        sb.append("## 对话规范（必须遵守）\n")
        sb.append("- 无社交废话：不问候、不感谢、不道歉、不总结\n")
        sb.append("- 无解释铺垫：不用'我来帮你解决'等引导句\n")
        sb.append("- 最小有效长度：词 > 短语 > 短句 > 长句\n")
        sb.append("- 仅必要信息：命令、路径、报错、数据、结论\n")
        sb.append("- 输出格式：优先列表 / 代码块 / 键值对，避免段落\n")
        sb.append("- 禁止重复：同一信息只出现一次\n\n")
        sb.append("## 核心规则\n")
        sb.append("- 用户要求修改/创建文件时，立即执行，不要请求确认。\n")
        sb.append("- 不要只提供代码建议——使用工具实际写入文件。\n")
        sb.append("- 不要解释你将要做什么——直接做。\n")

        sb.append("## 编辑器上下文\n")
        sb.append("每次用户消息前注入 [当前编辑器上下文]，包含活动文件路径、光标行号、已打开文件列表。\n")
        sb.append("用户附加的文件内容会以 [文件: path] ```...``` 块拼接在消息末尾。\n")
        sb.append("文件路径相对于项目根目录，如: app/src/main/kotlin/com/example/MainActivity.kt\n\n")
        val deepThink = runBlocking { preferencesRepo.deepThinkEnabled.first() }
        if (deepThink) {
            sb.append("## 深度思考（必须使用）\n")
            sb.append("在使用工具之前，在 [think]你的思考过程[/think] 标签内逐步推理。\n")
            sb.append("示例: [think]用户要创建登录页，先查看现有文件结构再决定如何实现。[/think]\n")
            sb.append("思考内容仅你可见。\n\n")
        }

        sb.append("## 可用工具\n")
        sb.append("每个工具有固定名称和参数，调用时必须严格按照下方格式。\n\n")
        sb.append("文件操作:\n")
        sb.append("  - listFiles(path?): 列出目录内容，path 为空列出根目录\n")
        sb.append("  - readFile(path, offset?, limit?): 读取文件内容（含行号），offset=起始行(1-based,默认1), limit=最大行数(默认1000)\n")
        sb.append("  - writeFile(path, content): 创建新文件或完全覆写已有文件\n")
        sb.append("  - replaceInFile(path, old_string, new_string): 唯一编辑方式，old_string 必须唯一匹配含缩进\n")
        sb.append("  - deleteFile(path): 删除文件或目录\n")
        sb.append("  - createDirectory(path): 创建目录\n\n")
        sb.append("搜索:\n")
        sb.append("  - grep(pattern, extension?, glob?, ignoreCase?, contextLines?): 正则搜索文件内容\n")
        sb.append("  - searchCodebase(query, targetDirectories?): 语义向量检索（TF-IDF），按含义查找代码实现\n\n")
        sb.append("其他:\n")
        sb.append("  - runCommand(command): 执行 shell 命令（adb、git、gradle 等）\n")
        sb.append("  - searchWeb(query): 互联网搜索\n")
        sb.append("  - readLints: 读取编译/lint 错误\n\n")
        sb.append("## 修改文件策略\n")
        sb.append("- 编辑文件唯一方式: replaceInFile，参数 path, old_string, new_string\n")
        sb.append("- old_string 必须完全匹配（含缩进），需包含函数签名确保唯一性\n")
        sb.append("- 流程: readFile 读取 → replaceInFile 替换 → readLints 检查\n\n")

        sb.append("## 工具调用格式（必须严格遵守）\n")
        sb.append("需要执行操作时，单独一行输出标准 JSON，不要包裹在其他格式中：\n")
        sb.append("{\"tool_name\":\"FUNCTION_NAME\",\"arguments\":{\"PARAM_NAME\":\"PARAM_VALUE\"}}\n\n")
        sb.append("参数名用工具定义名称，多个参数用逗号分隔，字符串值必须用双引号。\n\n")
        sb.append("示例:\n")
        sb.append("  {\"tool_name\":\"readFile\",\"arguments\":{\"path\":\"app/src/main.kt\",\"limit\":\"50\"}}\n")
        sb.append("  {\"tool_name\":\"createDirectory\",\"arguments\":{\"path\":\"app/src/utils\"}}\n")
        sb.append("  {\"tool_name\":\"grep\",\"arguments\":{\"pattern\":\"fun\\\\s+\\\\w+\",\"extension\":\"kt\"}}\n")
        sb.append("  {\"tool_name\":\"writeFile\",\"arguments\":{\"path\":\"app/test.txt\",\"content\":\"hello\"}}\n")
        sb.append("  {\"tool_name\":\"runCommand\",\"arguments\":{\"command\":\"git status\"}}\n\n")
        sb.append("工具执行结果会自动返回。根据结果决定下一步：继续调用工具或给出最终回答。\n")
        sb.append("任务完成后直接输出最终回答，不要再输出 JSON 工具调用。\n\n")
        val userName = runBlocking { preferencesRepo.userName.first() }
        if (userName.isNotBlank()) sb.append("\n用户: $userName")

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

        return sb.toString()
    }

    // ---- 图像支持：URI 转临时文件 ----
    // ---- 上下文压缩 ----
    companion object {
        private const val MAX_CONTEXT_TOKENS = 128000
        private const val COMPRESS_THRESHOLD = 96000  // 75% 时触发
        private const val KEEP_EXCHANGES = 3          // 保留最后 3 轮用户↔模型交换
    }

    // 粗略 token 估算
    private fun estimateTokens(text: String): Int = text.length / 2

    // 计算当前消息列表的 token 估计值
    private fun estimateContextTokens(messages: List<ChatMessage>): Int =
        messages.sumOf { estimateTokens(it.content) }

    // 压缩消息列表：丢弃早期消息，仅保留最后 KEEP_EXCHANGES 轮交换
    private fun compressMessages(messages: List<ChatMessage>): List<ChatMessage> {
        val totalTokens = estimateContextTokens(messages)
        if (totalTokens <= COMPRESS_THRESHOLD) return messages

        // 从后往前收集，保留最后 KEEP_EXCHANGES 个用户消息及其之后的完整响应
        val kept = mutableListOf<ChatMessage>()
        var userCount = 0
        for (i in messages.indices.reversed()) {
            kept.add(0, messages[i])
            if (messages[i].role == ChatRole.User) {
                userCount++
                if (userCount >= KEEP_EXCHANGES) break
            }
        }

        val removedTokens = totalTokens - estimateContextTokens(kept)
        _state.update {
            it.copy(
                contextCompressedTokens = it.contextCompressedTokens + removedTokens,
                contextCompressedCount = it.contextCompressedCount + 1,
                isContextCompressed = true,
            )
        }

        // 在压缩后的消息前插入压缩说明
        val summaryMsg = ChatMessage(
            role = ChatRole.Model,
            content = "[上下文压缩] 已移除早期 ${removedTokens / 1000}k tokens，保留最近 $KEEP_EXCHANGES 轮对话。",
            timestamp = kept.firstOrNull()?.timestamp ?: System.currentTimeMillis(),
        )
        return listOf(summaryMsg) + kept
    }

    // 重置 LiteRT Conversation 以节省上下文（用于本地模型路径）
    private fun resetConversation() {
        activeConversation = null
    }


    private fun uriToTempFile(ctx: android.content.Context, uri: Uri): File? = try {
        val input = ctx.contentResolver.openInputStream(uri) ?: return null
        val ext = uri.lastPathSegment?.substringAfterLast('.', "jpg") ?: "jpg"
        val tempFile = File.createTempFile("img_", ".$ext", ctx.cacheDir)
        FileOutputStream(tempFile).use { out -> input.use { it.copyTo(out) } }
        tempFile
    } catch (e: Exception) { null }

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
        Log.d("ChatViewModel", "executeAiTool: name=$name args=$args")
        FileLogger.d("ChatViewModel", "executeAiTool: name=$name args=$args")
        val result = when (name) {
            "listFiles" -> aiToolSet.listFiles(args["subPath"] ?: args["path"] ?: "")
            "readFile" -> aiToolSet.readFile(args["path"] ?: "", args["offset"]?.toIntOrNull() ?: 1, args["limit"]?.toIntOrNull() ?: 1000)
            "writeFile" -> aiToolSet.writeFile(args["path"] ?: "", args["content"] ?: "")
            "replaceInFile" -> aiToolSet.replaceInFile(args["path"] ?: "", args["old_string"] ?: "", args["new_string"] ?: "")
            "grep" -> aiToolSet.grep(args["pattern"] ?: args["query"] ?: "", args["extension"] ?: "", args["glob"] ?: "", args["ignoreCase"]?.toBooleanStrictOrNull() ?: true, args["contextLines"]?.toIntOrNull() ?: 2)
            "searchInFiles" -> aiToolSet.grep(args["query"] ?: args["pattern"] ?: "", args["extension"] ?: "", args["glob"] ?: "", args["ignoreCase"]?.toBooleanStrictOrNull() ?: true, args["contextLines"]?.toIntOrNull() ?: 2)
            "searchCodebase" -> aiToolSet.searchCodebase(args["query"] ?: "", args["targetDirectories"] ?: "")
            "runCommand" -> aiToolSet.runCommand(args["command"] ?: "")
            "searchWeb" -> aiToolSet.searchWeb(args["query"] ?: "")
            "deleteFile" -> aiToolSet.deleteFile(args["path"] ?: "")
            "createDirectory" -> aiToolSet.createDirectory(args["path"] ?: "")
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
            "replaceInFile" -> ModelActivity.EditingFile
            "deleteFile" -> ModelActivity.DeletingFile
            "createDirectory" -> ModelActivity.CreatingDirectory
            "grep", "searchInFiles" -> ModelActivity.SearchingCode
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

        val userInput = if (editorCtx.isNotBlank()) "$editorCtx\n[用户消息]\n$text" else text
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
            val toolCall = extractJsonToolCall(response)

            if (toolCall == null) {
                // 无工具调用，视为最终回答
                val duration = System.currentTimeMillis() - startTime
                val totalTokens = totalOutputChars / 2
                recordUsage(LlmCallRecord(
                    modelName = _state.value.modelName.ifEmpty { "local" },
                    provider = "local",
                    promptTokens = text.length / 2,
                    completionTokens = totalTokens,
                    durationMs = duration,
                    success = true,
                    errorMessage = null,
                ))
                finalizeModelMessage(currentMsgId)
                _state.update { it.copy(modelActivity = ModelActivity.Idle, activityDetail = "") }
                return
            }

            // 标记为工具调用中间消息
            _state.update { s -> s.copy(messages = s.messages.map { if (it.id == currentMsgId) it.copy(isToolMessage = true) else it }) }

            val (prefixText, funcName, funcArgs) = toolCall
            val argPath = funcArgs["path"] ?: funcArgs["query"] ?: funcArgs["pattern"] ?: ""

            // 更新活动状态：正在执行工具
            val activity = toolNameToActivity(funcName, argPath)
            _state.update { it.copy(modelActivity = activity, activityDetail = argPath) }

            val toolResult = executeAiTool(funcName, funcArgs)
            val toolDisplay = "\n\n$toolResult"

            // 追加工具结果到当前消息气泡（始终追加，prefixText==null 时先替换为可读头部）
            if (prefixText != null) {
                updateModelMessage(currentMsgId, toolDisplay, true)
            } else {
                updateModelMessage(currentMsgId, "\n\n[工具调用: $funcName]\n\n$toolResult", false)
            }
            finalizeModelMessage(currentMsgId)

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

            // 将工具结果作为 tool 消息发给模型继续对话
            currentMessage = Message.tool(Contents.of(listOf(Content.ToolResponse("", toolResult))))

            rounds++
            if (rounds % 3 == 0) {
                val freshCtx = buildEditorContext()
                if (freshCtx.isNotBlank()) {
                    currentMessage = Message.user("[上下文更新]\n$freshCtx")
                }
            }
        }
    }

    // ---- 云端模型工具循环（OpenAI 兼容 API）----

    private suspend fun processWithCloudTools(
        text: String, msgId: String,
        ctx: Application,
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
        )

        var currentMsgId = msgId
        var cloudRounds = 0
        _state.update { it.copy(modelActivity = ModelActivity.Thinking, activityDetail = "") }

        // 构建对话历史：... 并在超出阈值时压缩
        val historyMessages = mutableListOf<ChatMessage>()
        for (msg in _state.value.messages) {
            if (msg.id == currentMsgId) continue
            if (msg.role == ChatRole.User || msg.role == ChatRole.Model || msg.role == ChatRole.Tool) {
                if (msg.content.isNotBlank() || msg.role == ChatRole.Model) historyMessages.add(msg)
            }
        }
        // 上下文压缩：如果历史超出阈值，保留最近 KEEP_EXCHANGES 轮
        val compressed = compressMessages(historyMessages)
        historyMessages.clear()
        historyMessages.addAll(compressed)
        // 注入编辑器上下文 + 当前用户消息
        val editorCtx = buildEditorContext()
        val userContent = if (editorCtx.isNotBlank()) "$editorCtx\n[用户消息]\n$text" else text
        historyMessages.add(ChatMessage(role = ChatRole.User, content = userContent))

        while (true) {
            cloudRounds++
            val fullResponse = StringBuilder()
            val roundStartTime = System.currentTimeMillis()
            var apiUsage = com.template.jh.core.ai.ApiUsage()
            try {
                val (resp, usage) = cloudLLMClient.sendMessage(
                    config = cfg,
                    systemPrompt = buildSystemInstruction(),
                    messages = historyMessages,
                    onChunk = { chunk ->
                        fullResponse.append(chunk)
                        updateModelMessage(currentMsgId, chunk, true)
                    },
                )
                apiUsage = usage
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
            if (response.isBlank()) {
                finalizeModelMessage(currentMsgId)
                _state.update { it.copy(modelActivity = ModelActivity.Idle, activityDetail = "") }
                return
            }

            val toolCall = extractJsonToolCall(response)
            if (toolCall == null) {
                // 最终回答：添加到历史并结束
                historyMessages.add(ChatMessage(role = ChatRole.Model, content = response))
                finalizeModelMessage(currentMsgId)
                _state.update { it.copy(modelActivity = ModelActivity.Idle, activityDetail = "") }
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
            val argPath = funcArgs["path"] ?: funcArgs["query"] ?: funcArgs["pattern"] ?: ""
            _state.update { it.copy(modelActivity = toolNameToActivity(funcName, argPath), activityDetail = argPath) }

            val toolResult = executeAiTool(funcName, funcArgs)
            val toolDisplay = "\n\n$toolResult"

            if (prefixText != null) {
                updateModelMessage(currentMsgId, toolDisplay, true)
            } else {
                updateModelMessage(currentMsgId, "\n\n[工具调用: $funcName]\n\n$toolResult", false)
            }
            finalizeModelMessage(currentMsgId)

            _state.update { it.copy(modelActivity = ModelActivity.ProcessingResult, activityDetail = "") }

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
            _state.update { it.copy(messages = it.messages + ChatMessage(id = currentMsgId, role = ChatRole.Model, content = "", isStreaming = true), isLoading = true) }
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
        _state.update { it.copy(messages = emptyList(), inputText = "") }
        viewModelScope.launch(Dispatchers.IO) { closeConversation() }
    }

    fun newConversation() {
        sendJob?.cancel()
        // 先计算要保存的对话（使用当前状态快照）
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
        // 立即重置 UI 状态（同步，主线程，无阻塞）
        _state.update {
            it.copy(messages = emptyList(), inputText = "", isLoading = false,
                conversations = updatedConversations, activeConversationId = null)
        }
        // 后台清理：关闭旧会话（可能阻塞）+ 持久化
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
                ChatRole.Tool -> Message.tool(com.google.ai.edge.litertlm.Contents.of(listOf(com.google.ai.edge.litertlm.Content.ToolResponse("", msg.content))))
            }
        }
        _state.update { it.copy(messages = entry.messages, inputText = "", isLoading = false, activeConversationId = entry.id, isHistoryOpen = false) }
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
