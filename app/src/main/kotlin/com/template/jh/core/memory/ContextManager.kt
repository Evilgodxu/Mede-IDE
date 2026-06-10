package com.template.jh.core.memory

import android.util.Log
import com.template.jh.core.ai.AIToolSet
import com.template.jh.core.config.ChatConfig
import com.template.jh.core.storage.FileManager
import com.template.jh.model.Rule
import com.template.jh.model.SkillItem
import com.template.jh.model.chat.AttachedFile
import com.template.jh.model.chat.ChatMessage
import com.template.jh.model.chat.ChatRole
import com.template.jh.model.chat.CloudModelProfile
import com.template.jh.model.chat.ConversationEntry
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
        deepThink: Boolean,
        cloudModelEnabled: Boolean,
    ): String {
        sysPromptCache?.let { return it }
        val sb = StringBuilder()
        sb.append(
            """
You are an AI coding assistant. Reply in 简体中文.

## 执行准则
- 指令即执行，不问"是否要/你想怎样"；模糊指令推断后直接执行
- 最小修改范围：不改无关代码，编辑已有文件优先
- 工具错误：分析原因 → 修正参数 → 重试

## 输出规范
- 无废话最小长度：不问候/不感谢/不道歉/不总结，仅输出必要信息

## 工具调用
- 框架自动处理，仅 User 消息为实际请求。

""".trimIndent()
        )
        sb.appendLine()
        if (userName.isNotBlank()) sb.append("\n用户: $userName")
        _sysPromptCache = sb.toString()
        return sb.toString()
    }

    fun invalidateSysPromptCache() { _sysPromptCache = null }
    fun getSysPromptCache(): String? = _sysPromptCache

    // === 编辑器上下文 ===

    fun buildEditorContext(
        activeFilePath: String,
        projectRootName: String,
        openedFilePaths: List<String>,
        modifiedFilePaths: List<String>,
        cursorLine: Int,
        fileManager: FileManager,
        aiToolSet: AIToolSet,
    ): String {
        val ctx = StringBuilder()
        ctx.appendLine("[当前编辑器上下文]")
        if (activeFilePath.isNotBlank()) {
            ctx.appendLine("活动文件: $activeFilePath")
        }
        if (projectRootName.isNotBlank()) {
            val absoluteRoot = aiToolSet.getProjectRootPath()
            ctx.appendLine("项目: $projectRootName")
            if (absoluteRoot.isNotBlank()) {
                ctx.appendLine("项目绝对路径: $absoluteRoot")
            }
            val tree = fileManager.buildFileTreeString(maxDepth = 2, maxItems = 20)
            if (tree.isNotBlank()) {
                ctx.appendLine("工作区结构:")
                tree.lines().forEach { ctx.appendLine("  $it") }
            }
        }
        if (openedFilePaths.isNotEmpty()) {
            ctx.appendLine("已打开文件:")
            openedFilePaths.take(10).forEach { path ->
                val dirty = if (path in modifiedFilePaths) " [已修改]" else ""
                val active = if (path == activeFilePath) " ← 活动" else ""
                ctx.appendLine("  - $path$dirty$active")
            }
            if (openedFilePaths.size > 10) {
                ctx.appendLine("  ... 及其他 ${openedFilePaths.size - 10} 个文件")
            }
        } else {
            ctx.appendLine("（未打开任何文件 — 可使用 listFiles 查看项目文件结构再定位目标）")
        }
        if (ctx.length < 30) return ""
        return ctx.toString()
    }

    fun buildFileAttachmentBlock(refs: List<AttachedFile>): String {
        if (refs.isEmpty()) return ""
        val block = StringBuilder()
        block.appendLine("[用户指定的文件（支持绝对路径和相对路径），使用 readFile 查看内容]")
        refs.forEach { f -> block.appendLine("  - ${f.name} (${f.path})") }
        return block.toString()
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
        return (contextWindow * 0.75).toInt() - estimateSystemPromptTokens(sysPromptCache)
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
        val triggerType: String = "auto",  // auto / force / periodic
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

        // Step 1: 截断工具输出（长工具返回只保留头尾）
        val truncated = truncateToolMessages(messages)

        // Step 2: 按优先级评分每个消息
        val scored = truncated.mapIndexed { idx, msg ->
            val priority = messagePriority(msg, idx, truncated.size)
            ScoredMessage(msg, idx, estimateTokens(msg.content), priority)
        }

        // Step 3: 智能分块 — 保留高优先级 + 最近 KEEP_EXCHANGES 轮
        val recent = mutableListOf<ChatMessage>()
        var userCount = 0
        for (i in truncated.indices.reversed()) {
            recent.add(0, truncated[i])
            if (truncated[i].role == ChatRole.User) {
                userCount++
                if (userCount >= ChatConfig.KEEP_EXCHANGES) break
            }
        }

        // Step 4: 处理早期消息 — 按优先级选择有价值的迁移到记忆
        val keepEnd = truncated.size - recent.size
        val earlyMessages = truncated.take(keepEnd)
        val combined = mutableListOf<ChatMessage>()
        var summaryContent = ""

        if (keepEnd > 0) {
            val convId = activeConversationId ?: ""
            // 仅将高优先级早期消息迁移到记忆（避免噪音）
            val highPriorityEarly = earlyMessages.filterIndexed { idx, msg ->
                (scored.getOrNull(idx)?.priority ?: 1) >= 3
            }
            val adapters = highPriorityEarly.map { msg ->
                ChatMessageAdapter(
                    role = when (msg.role) {
                        ChatRole.User -> "user"
                        ChatRole.Model -> "model"
                        ChatRole.Tool -> "tool"
                        else -> "system"
                    },
                    content = msg.content.take(2000),
                )
            }
            if (adapters.isNotEmpty()) {
                conversationMemory.addMessages(adapters, convId)
            }

            // 生成摘要上下文字段
            val memCtx = conversationMemory.getCompressionContext(convId)
            if (memCtx.isNotBlank()) {
                summaryContent = memCtx
            }
            if (summaryContent.isNotBlank()) {
                combined.add(ChatMessage(
                    role = ChatRole.Model,
                    content = "[上下文摘要]\n$summaryContent",
                    timestamp = System.currentTimeMillis(),
                ))
            } else {
                val memorySummary = conversationMemory.getMemoryContext(convId)
                if (memorySummary.isNotBlank()) {
                    combined.add(ChatMessage(
                        role = ChatRole.Model,
                        content = "[早期对话摘要]\n$memorySummary",
                        timestamp = System.currentTimeMillis(),
                    ))
                }
            }
        }
        combined.addAll(recent)

        val removedTokens = totalTokens - estimateContextTokens(combined)
        val keyFactCount = conversationMemory.getKeyFacts().size

        // Step 5: 插入/更新累计通知
        val existingSummaryIndex = combined.indexOfFirst {
            it.role == ChatRole.Model && it.content.startsWith("[上下文压缩累计]")
        }
        val hasMemory = keyFactCount > 0
        val summaryMsg = ChatMessage(
            role = ChatRole.Model,
            content = buildString {
                val removedK = if (removedTokens < 1000) "<1k" else "${removedTokens / 1000}k"
                append("[上下文压缩累计] 累计移除 $removedK tokens，")
                append("保留最近 ${ChatConfig.KEEP_EXCHANGES} 轮对话")
                append("，早期对话已保存到记忆系统")
                if (hasMemory) append("（含 $keyFactCount 条关键事实保活）")
                append("。")
            },
            timestamp = System.currentTimeMillis(),
        )
        val finalMessages = if (existingSummaryIndex >= 0) {
            combined.toMutableList().also { list -> list[existingSummaryIndex] = summaryMsg }
        } else {
            listOf(summaryMsg) + combined
        }
        return CompressResult(
            messages = finalMessages,
            removedTokens = removedTokens,
            summaryContent = summaryContent,
            keyFactsPreserved = keyFactCount,
        )
    }

    /** 消息优先级评分（1-10） */
    private fun messagePriority(msg: ChatMessage, index: Int, total: Int): Int {
        var score = 1
        val content = msg.content
        // 角色权重
        score += when (msg.role) {
            ChatRole.User -> 4   // 用户消息最重要
            ChatRole.Tool -> 3   // 工具调用结果次之
            ChatRole.Model -> 2  // 模型响应
            ChatRole.System -> 1
        }
        // 内容信号
        if (content.contains("```") || content.contains("~~~")) score += 2  // 含代码
        if (content.contains("关键") || content.contains("重要") || content.contains("必须")) score += 1
        if (content.contains("文件") || content.contains("filePath") || content.contains("/") ) score += 1
        if (content.length > 500) score += 1  // 长消息可能更有价值
        // 位置衰减（越早的消息价值越低）
        val positionRatio = index.toFloat() / total.coerceAtLeast(1)
        score = (score * (1f - positionRatio * 0.3f)).toInt().coerceAtLeast(1)
        return score.coerceAtMost(10)
    }

    private data class ScoredMessage(
        val msg: ChatMessage, val idx: Int, val tokens: Int, val priority: Int
    )

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
    fun getMemoryContext(convId: String) = conversationMemory.getMemoryContext(convId)
    fun getKeyFactsContext() = conversationMemory.getKeyFactsContext()
    fun refreshMemoryState() = Unit  // 调用方自行获取 stats
}
