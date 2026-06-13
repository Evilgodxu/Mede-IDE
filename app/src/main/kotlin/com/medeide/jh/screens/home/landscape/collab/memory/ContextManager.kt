package com.medeide.jh.screens.home.landscape.collab.memory

import android.util.Log
import com.medeide.jh.screens.home.landscape.collab.ai.AIToolSet
import com.medeide.jh.screens.home.landscape.collab.config.ChatConfig
import com.medeide.jh.data.storage.FileManager
import com.medeide.jh.model.Rule
import com.medeide.jh.model.chat.AttachedFile
import com.medeide.jh.model.chat.ChatMessage
import com.medeide.jh.model.chat.ChatRole
import com.medeide.jh.model.chat.CloudModelProfile
import com.medeide.jh.model.chat.ConversationEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

// 上下文管理：System prompt、编辑器上下文、token 估算、消息压缩
class ContextManager(
    private val conversationMemory: ConversationMemory,
) {
    @Volatile private var _sysPromptCache: String? = null

    fun buildSystemInstruction(
        sysPromptCache: String?,
        userName: String,
        rules: List<Rule>,
        activeRoleId: String,
        cloudModelEnabled: Boolean,
    ): String {
        if (!cloudModelEnabled) {
            sysPromptCache?.let { return it }
        }
        val sb = StringBuilder()

        // 查找当前激活的角色
        val activeRole = rules.find { it.id == activeRoleId } ?: rules.firstOrNull { it.isDefault }

        if (activeRole != null) {
            sb.appendLine(activeRole.content)
            if (userName.isNotBlank()) sb.appendLine("用户: $userName")
            if (!activeRole.isDefault) {
                sb.appendLine().appendLine("【角色设定】")
                sb.appendLine("- ${activeRole.name}: ${activeRole.content}")
            }
        } else {
            sb.appendLine("你是智能编程助手，使用内置工具协助用户完成文件操作、代码编辑、项目构建、网络搜索等开发任务。")
            if (userName.isNotBlank()) sb.appendLine("用户: $userName")
        }

        if (!cloudModelEnabled) {
            // 本地模型：需要行为准则和工具指示（文本 prompt 驱动）
            sb.appendLine()
            sb.appendLine("核心行为准则:")
            sb.appendLine("1. 工具优先 - 接到任务后立即调用工具执行，有工具可用时绝不空谈方案")
            sb.appendLine("2. 先行动后解释 - 直接执行操作，完成后用1-2句简述结果，不写长篇分析")
            sb.appendLine("3. 禁止反复询问 - 不问是否要执行/继续/修改，除非工具调用失败或信息严重不足")
            sb.appendLine("4. 连续调用 - 不要停下来问接下来怎么办，先搜集信息再做判断")
            sb.appendLine("5. 输出简洁 - 只回复关键结果或操作摘要，不输出思考过程或计划步骤")
            sb.appendLine()
            sb.appendLine("工具使用示例:")
            sb.appendLine("- 用户说找到登录相关的代码 -> 调用 searchCodebase 或 grep")
            sb.appendLine("- 用户说帮我改变量名 -> 调用 readFile + replaceInFile")
            sb.appendLine("- 用户说这个项目有什么文件 -> 调用 listFiles")
            sb.appendLine("- 用户说创建文件 -> 调用 writeFile")
            sb.appendLine("- 用户说构建报错 -> 调用 getDiagnostics 或 runCommand")
            sb.appendLine("- 用户说搜索文档 -> 调用 searchWeb")
            sb.appendLine("- 用户说删除目录 -> 调用 deleteFile")
            sb.appendLine("- 改代码流程: readFile -> replaceInFile/batchReplaceInFile (直接改不问)")
            sb.appendLine()
            sb.appendLine("可用工具:")
            sb.appendLine("阅读: listFiles, readFile, grep, searchCodebase, glob, searchConversationMemory, getRecentConversationMemory")
            sb.appendLine("编辑: writeFile, replaceInFile, batchReplaceInFile, deleteFile, createDirectory, moveFile, copyFile")
            sb.appendLine("终端: 无（Android 不支持 shell 命令）")
            sb.appendLine("网络: searchWeb, visitWeb, downloadFile, httpRequest")
            sb.appendLine()
            sb.appendLine("工具调用格式（重要）:")
            sb.appendLine("- 必须使用 <tool_call> 标签包裹 JSON，不要使用 <|tool_call|>（无管道符）")
            sb.appendLine("- 格式: <tool_call>{\"name\":\"工具名\",\"arguments\":{\"参数名\":\"参数值\"}}</tool_call>")
            sb.appendLine("- 示例: <tool_call>{\"name\":\"deleteFile\",\"arguments\":{\"file_paths\":\"/storage/emulated/0/test.txt\"}}</tool_call>")
            sb.appendLine("- 示例: <tool_call>{\"name\":\"readFile\",\"arguments\":{\"file_path\":\"/path/to/file.txt\",\"limit\":200}}</tool_call>")
            sb.appendLine("- 不要包含 result 字段，工具执行结果由系统返回，你不需要模拟执行结果")
            sb.appendLine()
            sb.appendLine("历史记录查询:")
            sb.appendLine("- 如果用户提及曾经讨论/修改/确认过的内容而你记不清，使用 searchConversationMemory() 搜索历史对话")
            sb.appendLine("- 需要了解最近的记忆记录使用 getRecentConversationMemory()")
            sb.appendLine("- 注意: 详细的历史工具执行结果需要调用上述工具获取")
        }

        if (!cloudModelEnabled) {
            _sysPromptCache = sb.toString()
        }
        return sb.toString()
    }

    fun invalidateSysPromptCache() { _sysPromptCache = null }
    fun getSysPromptCache(): String? = _sysPromptCache

    fun buildEditorContext(
        activeFilePath: String,
        projectRootName: String,
        openedFilePaths: List<String>,
        fileManager: FileManager,
        aiToolSet: AIToolSet,
    ): String {
        val ctx = StringBuilder()
        ctx.appendLine("[实时状态]")

        val now = java.time.LocalDateTime.now()
        ctx.appendLine("当前时间: ${now.year}-${"%02d".format(now.monthValue)}-${"%02d".format(now.dayOfMonth)} ${"%02d".format(now.hour)}:${"%02d".format(now.minute)}")

        val absoluteRoot = aiToolSet.getProjectRootPath()
        if (absoluteRoot.isNotBlank()) {
            ctx.appendLine("当前项目: $absoluteRoot")
        }

        val active = activeFilePath.takeIf { it.isNotBlank() }
        if (active != null) ctx.appendLine("活动标签: $active")

        if (openedFilePaths.isNotEmpty()) {
            ctx.appendLine("已打开:")
            openedFilePaths.take(10).forEach { path -> ctx.appendLine("  $path") }
            if (openedFilePaths.size > 10) ctx.appendLine("  ... 及 ${openedFilePaths.size - 10} 个")
        }

        if (ctx.length < 40) return ""
        return ctx.toString()
    }

    fun buildFileAttachmentBlock(refs: List<AttachedFile>): String {
        if (refs.isEmpty()) return ""
        val block = StringBuilder()
        refs.forEach { f -> block.appendLine("用户要求查看 \"${f.path}\"") }
        return block.toString().trimEnd()
    }

    fun getContextWindow(
        cloud: Boolean,
        cloudProfiles: List<CloudModelProfile>,
        activeProfileId: String,
        localContextTokens: Int,
    ): Int {
        if (!cloud) return localContextTokens.coerceAtLeast(ChatConfig.DEFAULT_LOCAL_CONTEXT_WINDOW)
        val profile = cloudProfiles.find { it.id == activeProfileId }
        return profile?.contextWindow ?: ChatConfig.DEFAULT_CONTEXT_WINDOW
    }

    fun getCompressThreshold(contextWindow: Int, sysPromptCache: String?): Int {
        return (contextWindow * 0.35).toInt() - estimateSystemPromptTokens(sysPromptCache)
    }

    fun estimateSystemPromptTokens(sysPromptCache: String?): Int {
        val cached = sysPromptCache
        if (cached != null) return (cached.length / 3.5f).toInt().coerceAtLeast(200)
        return 800
    }

    fun estimateTokens(text: String): Int {
        if (text.isEmpty()) return 0
        val bytes = text.toByteArray(Charsets.UTF_8)
        return (bytes.size / ChatConfig.UTFS_BYTES_PER_TOKEN).toInt().coerceAtLeast(1)
    }

    fun estimateContextTokens(messages: List<ChatMessage>): Int =
        messages.sumOf { estimateTokens(it.content) }

    fun truncateToolMessages(messages: List<ChatMessage>): List<ChatMessage> {
        return messages.map { msg ->
            if (msg.role == ChatRole.Tool) {
                msg.copy(content = truncateToolContent(msg.content))
            } else msg
        }
    }

    private fun truncateToolContent(content: String): String {
        val lines = content.lines()
        if (lines.size <= ChatConfig.TOOL_TRUNCATE_LINES * 2 + 1) return content
        val head = lines.take(ChatConfig.TOOL_TRUNCATE_LINES)
        val tail = lines.takeLast(ChatConfig.TOOL_TRUNCATE_LINES)
        return buildString {
            head.forEach { appendLine(it) }
            appendLine("... (中间截断 ${lines.size - ChatConfig.TOOL_TRUNCATE_LINES * 2} 行) ...")
            tail.forEach { appendLine(it) }
        }
    }

    fun truncateToTokenLimit(messages: List<ChatMessage>, maxTokens: Int): List<ChatMessage> {
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

    fun selectContextForLocal(messages: List<ChatMessage>, maxTokens: Int): List<ChatMessage> {
        if (maxTokens <= 0 || messages.size <= 2) return messages
        val currentExchange = messages.takeLast(2)
        val history = messages.dropLast(2).filterNot {
            it.role == ChatRole.Model && it.content.startsWith("[上下文压缩累计]")
        }
        if (history.isEmpty()) return currentExchange

        data class Scored(val msg: ChatMessage, val idx: Int, val tokens: Int, val priority: Int)
        val scored = history.mapIndexed { i, msg ->
            val priority = when {
                msg.role == ChatRole.Tool -> 8
                msg.role == ChatRole.Model && (msg.content.contains("```") || msg.content.contains("[工具调用:")) -> 6
                msg.role == ChatRole.User && (msg.content.contains("```") || msg.content.contains("filePath")) -> 6
                msg.role == ChatRole.Model && msg.content.startsWith("[上下文") -> 2
                else -> 1
            }
            Scored(msg, i, estimateTokens(msg.content), priority)
        }

        val exchangeTokens = estimateContextTokens(currentExchange)
        val totalBudget = maxTokens - exchangeTokens
        if (totalBudget <= 0) return currentExchange

        var budget = totalBudget
        val windowSelected = mutableSetOf<Int>()

        for (i in scored.indices.reversed()) {
            if (budget <= 0) break
            val s = scored[i]
            if ((totalBudget - budget) > totalBudget * 0.7 && s.priority <= 1) continue
            if (s.tokens <= budget) { windowSelected.add(s.idx); budget -= s.tokens }
        }
        for (i in scored.indices) {
            if (budget <= 0) break
            val s = scored[i]
            if (s.idx in windowSelected || s.priority < 6 || s.tokens > budget) continue
            windowSelected.add(s.idx); budget -= s.tokens
        }
        var uc = 0
        for (i in scored.indices.reversed()) {
            if (scored[i].msg.role == ChatRole.User) {
                if (scored[i].idx !in windowSelected && scored[i].tokens <= budget) {
                    windowSelected.add(scored[i].idx); budget -= scored[i].tokens
                }
                uc++
                if (uc >= ChatConfig.KEEP_EXCHANGES) break
            }
        }
        return scored.filter { it.idx in windowSelected }.map { it.msg } + currentExchange
    }

    data class CompressResult(
        val messages: List<ChatMessage>,
        val removedTokens: Int,
        val summaryContent: String,
        val keyFactsPreserved: Int = 0,
    )

    companion object {
        // 工具结果截断行数
        private const val COMPACT_TOOL_LINES = 50
        // 模型结论提取尾部保留行数
        private const val COMPACT_MODEL_TAIL_LINES = 8
    }

    /**
     * 为历史消息生成压缩摘要，保留完整的执行链条：
     *   用户请求 → 记忆查看 → 工具执行 → 模型结论
     *
     * - 最近 KEEP_EXCHANGES 轮：完整保留
     * - 更早的消息：逐条压缩，保留关键信息而非指向记忆工具
     */
    private fun compactHistoryMessage(msg: ChatMessage): ChatMessage = when (msg.role) {
        // 用户消息：完整保留（起始节点）
        ChatRole.User -> msg

        // 模型消息：保留工具调用 + 压缩结论
        ChatRole.Model -> {
            val lines = msg.content.lines()
            val toolCallLines = lines.filter { l ->
                l.contains("[工具调用:") || l.contains("[Tool call:") ||
                    l.trimStart().startsWith("{") && l.contains("\"method\"")
            }
            val replyLines = lines.filter { l ->
                !l.contains("[工具调用:") && !l.contains("[Tool call:") &&
                    !l.trimStart().startsWith("{") &&
                    l.isNotBlank()
            }

            val compactContent = buildString {
                // 工具调用线索
                if (toolCallLines.isNotEmpty()) {
                    toolCallLines.forEach { appendLine(it) }
                    if (replyLines.isNotEmpty()) appendLine()
                }
                // 提取结论性内容（尾部关键行）
                if (replyLines.isNotEmpty()) {
                    if (replyLines.size <= COMPACT_MODEL_TAIL_LINES) {
                        replyLines.forEach { appendLine(it) }
                    } else {
                        appendLine("[结论]")
                        replyLines.takeLast(COMPACT_MODEL_TAIL_LINES).forEach { appendLine(it) }
                    }
                }
            }
            if (compactContent.isBlank()) msg else msg.copy(content = compactContent)
        }

        // 工具结果：保留概要 + 首尾关键行（完整内容仍可从记忆全量查询）
        ChatRole.Tool -> {
            val lines = msg.content.lines()
            val toolName = msg.toolName ?: "未知工具"
            if (lines.size <= COMPACT_TOOL_LINES + 1) {
                // 结果本来就不大，直接保留
                msg
            } else {
                val head = lines.take(COMPACT_TOOL_LINES / 2)
                val tail = lines.takeLast(COMPACT_TOOL_LINES / 2)
                buildString {
                    appendLine("[工具: $toolName] 共 ${lines.size} 行，保留摘要:")
                    head.forEach { appendLine(it) }
                    appendLine("... (中间截断 ${lines.size - COMPACT_TOOL_LINES} 行，完整结果可通过记忆工具查询) ...")
                    tail.forEach { appendLine(it) }
                }.let { msg.copy(content = it) }
            }
        }

        ChatRole.System -> msg
    }

    suspend fun compressMessages(
        messages: List<ChatMessage>,
        activeConversationId: String?,
        threshold: Int = getCompressThreshold(ChatConfig.DEFAULT_CONTEXT_WINDOW, _sysPromptCache),
    ): CompressResult {
        val totalTokens = estimateContextTokens(messages)
        if (totalTokens <= threshold) return CompressResult(messages, 0, "")

        val truncated = truncateToolMessages(messages)
        val recent = mutableListOf<ChatMessage>()
        var userCount = 0
        for (i in truncated.indices.reversed()) {
            recent.add(0, truncated[i])
            if (truncated[i].role == ChatRole.User) {
                userCount++
                if (userCount >= ChatConfig.KEEP_EXCHANGES) break
            }
        }

        val keepEnd = truncated.size - recent.size
        if (keepEnd <= 0) return CompressResult(messages, 0, "")

        // 保留最近的完整轮次
        val combined = mutableListOf<ChatMessage>()
        // 对历史消息（keepEnd 之前）做压缩摘要
        for (i in 0 until keepEnd) {
            combined.add(compactHistoryMessage(truncated[i]))
        }
        // 追加最近的完整轮次
        combined.addAll(recent)

        val removedTokens = totalTokens - estimateContextTokens(combined)
        return CompressResult(
            messages = combined,
            removedTokens = removedTokens,
            summaryContent = "有 ${keepEnd} 条历史消息已压缩为摘要，可通过记忆工具查询完整内容",
            keyFactsPreserved = combined.size,
        )
    }

    fun chatMessagesToLiteRT(messages: List<ChatMessage>): List<com.google.ai.edge.litertlm.Message> =
        messages.map { msg ->
            when (msg.role) {
                ChatRole.User -> com.google.ai.edge.litertlm.Message.user(msg.content)
                ChatRole.Model -> com.google.ai.edge.litertlm.Message.model(msg.content)
                ChatRole.System -> com.google.ai.edge.litertlm.Message.system(msg.content)
                ChatRole.Tool -> com.google.ai.edge.litertlm.Message.tool(
                    com.google.ai.edge.litertlm.Contents.of(
                        listOf(com.google.ai.edge.litertlm.Content.ToolResponse(
                            name = msg.toolName ?: msg.toolCallId ?: "call_${msg.id.take(8)}",
                            response = msg.content
                        ))
                    )
                )
            }
        }

    fun getMemoryStats() = conversationMemory.getStats()
    fun refreshMemoryState() = Unit
}
