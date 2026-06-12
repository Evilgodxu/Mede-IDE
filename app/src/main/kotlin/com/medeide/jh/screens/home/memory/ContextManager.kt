package com.medeide.jh.screens.home.memory

import android.util.Log
import com.medeide.jh.screens.home.ai.AIToolSet
import com.medeide.jh.screens.home.config.ChatConfig
import com.medeide.jh.data.storage.FileManager
import com.medeide.jh.model.Rule
import com.medeide.jh.model.SkillItem
import com.medeide.jh.model.chat.AttachedFile
import com.medeide.jh.model.chat.ChatMessage
import com.medeide.jh.model.chat.ChatRole
import com.medeide.jh.model.chat.CloudModelProfile
import com.medeide.jh.model.chat.ConversationEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/** 上下文管理：System prompt、编辑器上下文、token 估算、消息压缩 */
class ContextManager(
    private val conversationMemory: ConversationMemory,
) {
    // 缓存计算值
    @Volatile private var _sysPromptCache: String? = null

    // === System Prompt ===

    /** 构建 system instruction — 按需传入依赖值，无状态 */
    fun buildSystemInstruction(
        sysPromptCache: String?,
        userName: String,
        rules: List<Rule>,
        skills: List<SkillItem>,
        cloudModelEnabled: Boolean,
        aiToolSet: AIToolSet? = null,
    ): String {
        sysPromptCache?.let { return it }
        val sb = StringBuilder()
        sb.append("你是智能编程助手，使用内置工具协助用户完成文件操作、代码编辑、项目构建、网络搜索等开发任务。")
        if (userName.isNotBlank()) sb.append("\n用户: $userName")
        _sysPromptCache = sb.toString()
        return sb.toString()
    }

    fun invalidateSysPromptCache() { _sysPromptCache = null }
    fun getSysPromptCache(): String? = _sysPromptCache

    // === 实时状态上下文 ===

    fun buildEditorContext(
        activeFilePath: String,
        projectRootName: String,
        openedFilePaths: List<String>,
        fileManager: FileManager,
        aiToolSet: AIToolSet,
    ): String {
        val ctx = StringBuilder()
        ctx.appendLine("[实时状态]")

        // 系统时间
        val now = java.time.LocalDateTime.now()
        ctx.appendLine("当前时间: ${now.year}-${"%02d".format(now.monthValue)}-${"%02d".format(now.dayOfMonth)} ${"%02d".format(now.hour)}:${"%02d".format(now.minute)}")

        // 项目路径
        val absoluteRoot = aiToolSet.getProjectRootPath()
        if (absoluteRoot.isNotBlank()) {
            ctx.appendLine("当前项目: $absoluteRoot")
        }

        // 活动标签（音频/视频/图片/代码/文本等正在查看或编辑的内容）
        val active = activeFilePath.takeIf { it.isNotBlank() }
        if (active != null) {
            ctx.appendLine("活动标签: $active")
        }

        // 已打开文件
        if (openedFilePaths.isNotEmpty()) {
            ctx.appendLine("已打开:")
            openedFilePaths.take(10).forEach { path ->
                ctx.appendLine("  $path")
            }
            if (openedFilePaths.size > 10) {
                ctx.appendLine("  ... 及 ${openedFilePaths.size - 10} 个")
            }
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

    // === Token 估算 ===

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

    // === 消息压缩/截断 ===

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

        // Step 1: 滑动窗口
        for (i in scored.indices.reversed()) {
            if (budget <= 0) break
            val s = scored[i]
            if ((totalBudget - budget) > totalBudget * 0.7 && s.priority <= 1) continue
            if (s.tokens <= budget) {
                windowSelected.add(s.idx)
                budget -= s.tokens
            }
        }
        // Step 2: 优先级回溯
        for (i in scored.indices) {
            if (budget <= 0) break
            val s = scored[i]
            if (s.idx in windowSelected || s.priority < 6 || s.tokens > budget) continue
            windowSelected.add(s.idx)
            budget -= s.tokens
        }
        // Step 3: 确保最后 KEEP_EXCHANGES 轮用户消息完整
        var uc = 0
        for (i in scored.indices.reversed()) {
            if (scored[i].msg.role == ChatRole.User) {
                if (scored[i].idx !in windowSelected && scored[i].tokens <= budget) {
                    windowSelected.add(scored[i].idx)
                    budget -= scored[i].tokens
                }
                uc++
                if (uc >= ChatConfig.KEEP_EXCHANGES) break
            }
        }
        // Step 4: 按原始时间顺序组装
        return scored.filter { it.idx in windowSelected }.map { it.msg } + currentExchange
    }

    /** 压缩结果 */
    data class CompressResult(
        val messages: List<ChatMessage>,
        val removedTokens: Int,
        val summaryContent: String,
        val keyFactsPreserved: Int = 0,
    )

    /** 执行消息压缩 — 返回结果由调用方更新状态。
     *  优化策略：语义分块 + 优先级保留 + 记忆入库 */
    suspend fun compressMessages(
        messages: List<ChatMessage>,
        activeConversationId: String?,
        threshold: Int = getCompressThreshold(ChatConfig.DEFAULT_CONTEXT_WINDOW, _sysPromptCache),
    ): CompressResult {
        val totalTokens = estimateContextTokens(messages)
        if (totalTokens <= threshold) return CompressResult(messages, 0, "")

        // Step 1: 截断长工具输出
        val truncated = truncateToolMessages(messages)

        // Step 2: 保留最近 KEEP_EXCHANGES 轮完整对话
        val recent = mutableListOf<ChatMessage>()
        var userCount = 0
        for (i in truncated.indices.reversed()) {
            recent.add(0, truncated[i])
            if (truncated[i].role == ChatRole.User) {
                userCount++
                if (userCount >= ChatConfig.KEEP_EXCHANGES) break
            }
        }

        // Step 3: 早期消息直接丢弃，保留最近对话轮次即可
        val keepEnd = truncated.size - recent.size
        val combined = if (keepEnd > 0) recent else truncated
        val removedTokens = totalTokens - estimateContextTokens(combined)
        return CompressResult(
            messages = combined,
            removedTokens = removedTokens,
            summaryContent = "",
            keyFactsPreserved = 0,
        )
    }

    /** 聊天消息 → LiteRT Message 列表 */
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

    /** 获取记忆系统统计信息 */
    fun getMemoryStats() = conversationMemory.getStats()
    fun refreshMemoryState() = Unit  // 调用方自行获取 stats
}
