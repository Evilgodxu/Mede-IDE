package com.template.jh.core.memory

import android.util.Log
import com.template.jh.core.config.ChatConfig
import com.template.jh.model.chat.ChatMessage
import com.template.jh.model.chat.ChatRole

/**
 * 记忆/压缩可视化数据模型与计算引擎。
 * 不依赖 Compose，提供纯数据模型供 UI 层渲染图表。
 */

// ========== 分层数据模型 ==========

/** 上下文快照（L1 概览层） */
data class ContextSnapshot(
    val usedTokens: Int,
    val maxTokens: Int,
    val ratio: Float,                    // 0..1
    val isCompressed: Boolean,
    val compressedTokens: Int,
    val compressedCount: Int,
    val messageCount: Int,
    val toolCallCount: Int,
    val timestamp: Long = System.currentTimeMillis(),
)

/** Token 用量明细（L2 详情层） */
data class TokenBreakdown(
    val systemPromptTokens: Int,
    val userMessagesTokens: Int,
    val modelMessagesTokens: Int,
    val toolOutputTokens: Int,
    val memoryContextTokens: Int,
    val compressedSavedTokens: Int,
) {
    val totalTokens get() = systemPromptTokens + userMessagesTokens + modelMessagesTokens + toolOutputTokens
    val segments get() = listOf(
        Segment("System", systemPromptTokens, 0xFF607D8B.toInt()),
        Segment("用户", userMessagesTokens, 0xFF4CAF50.toInt()),
        Segment("模型", modelMessagesTokens, 0xFFFFA000.toInt()),
        Segment("工具", toolOutputTokens, 0xFF2196F3.toInt()),
    )
    data class Segment(val label: String, val tokens: Int, val color: Int)
}

/** 压缩记录（L2 时间线层） */
data class CompressionRecord(
    val timestamp: Long,
    val removedTokens: Int,
    val triggerType: TriggerType,
    val keptExchanges: Int,
    val summaryGenerated: Boolean,
    val keyFactsPreserved: Int,
) {
    enum class TriggerType { AutoThreshold, ContextError, Manual, Periodic }
}

/** 记忆架构快照（L2 记忆层） */
data class MemoryArchitecture(
    val layer1: LayerInfo,  // 关键事实
    val layer2: LayerInfo,  // 短期记忆
    val totalTokens: Int,
) {
    data class LayerInfo(
        val entryCount: Int,
        val maxEntries: Int,
        val estimatedTokens: Int,
        val categories: List<CategoryInfo> = emptyList(),
    ) {
        val ratio get() = if (maxEntries > 0) entryCount.toFloat() / maxEntries else 0f
    }
    data class CategoryInfo(val name: String, val count: Int, val label: String)
}

/** 关键事实分类统计 */
data class KeyFactCategories(
    val preferences: Int = 0,
    val decisions: Int = 0,
    val constraints: Int = 0,
    val fileReferences: Int = 0,
    val contextInfo: Int = 0,
) {
    val total get() = preferences + decisions + constraints + fileReferences + contextInfo
    fun toList() = listOf(
        Cat("用户偏好", preferences, 0xFFE91E63.toInt()),
        Cat("已做决策", decisions, 0xFF9C27B0.toInt()),
        Cat("约束条件", constraints, 0xFFF44336.toInt()),
        Cat("文件引用", fileReferences, 0xFF4CAF50.toInt()),
        Cat("上下文", contextInfo, 0xFF2196F3.toInt()),
    )
    data class Cat(val label: String, val count: Int, val color: Int)
}


// ========== 计算引擎 ==========

object VisualizerEngine {

    private const val TAG = "VisualizerEngine"

    /** 从消息列表构建上下文快照 */
    fun buildSnapshot(
        messages: List<ChatMessage>,
        maxTokens: Int,
        isCompressed: Boolean,
        compressedTokens: Int,
        compressedCount: Int,
    ): ContextSnapshot {
        val used = estimateTotalTokens(messages)
        return ContextSnapshot(
            usedTokens = used,
            maxTokens = maxTokens,
            ratio = if (maxTokens > 0) (used.toFloat() / maxTokens).coerceIn(0f, 1f) else 0f,
            isCompressed = isCompressed,
            compressedTokens = compressedTokens,
            compressedCount = compressedCount,
            messageCount = messages.size,
            toolCallCount = messages.count { it.role == ChatRole.Tool },
        )
    }

    /** 构建 Token 用量明细 */
    fun buildTokenBreakdown(messages: List<ChatMessage>, sysPromptTokens: Int): TokenBreakdown {
        var userTokens = 0
        var modelTokens = 0
        var toolTokens = 0
        for (msg in messages) {
            val t = estimateTokens(msg.content)
            when (msg.role) {
                ChatRole.User -> userTokens += t
                ChatRole.Model -> {
                    if (msg.content.startsWith("[上下文压缩累计]") || msg.content.startsWith("[上下文摘要]"))
                        modelTokens += minOf(t, 100)
                    else modelTokens += t
                }
                ChatRole.Tool -> toolTokens += t
                else -> {}
            }
        }
        return TokenBreakdown(
            systemPromptTokens = sysPromptTokens,
            userMessagesTokens = userTokens,
            modelMessagesTokens = modelTokens,
            toolOutputTokens = toolTokens,
            memoryContextTokens = 0,
            compressedSavedTokens = 0,
        )
    }

    /** 构建记忆架构快照 */
    fun buildMemoryArchitecture(memory: ConversationMemory): MemoryArchitecture {
        val facts = memory.getKeyFacts()
        val entries = memory.recent(200) // get all
        val stats = memory.getStats()

        // 关键事实分类统计
        val catMap = facts.groupBy { it.type }
        val categories = listOf(
            "preference" to "用户偏好",
            "decision" to "决策记录",
            "constraint" to "约束规则",
            "file" to "文件引用",
            "context" to "上下文",
        ).mapNotNull { (type, label) ->
            val count = (catMap[type]?.size ?: 0)
            if (count > 0) MemoryArchitecture.CategoryInfo(type, count, label) else null
        }

        return MemoryArchitecture(
            layer1 = MemoryArchitecture.LayerInfo(
                entryCount = facts.size,
                maxEntries = 50,
                estimatedTokens = stats.estimatedTokens,
                categories = categories,
            ),
            layer2 = MemoryArchitecture.LayerInfo(
                entryCount = entries.size,
                maxEntries = 50,
                estimatedTokens = entries.sumOf { estimateTokens(it.summary) },
                categories = emptyList(),
            ),
            totalTokens = stats.estimatedTokens,
        )
    }

    /** 构建关键事实分类 */
    fun buildKeyFactCategories(memory: ConversationMemory): KeyFactCategories {
        val facts = memory.getKeyFacts()
        return KeyFactCategories(
            preferences = facts.count { it.type == "preference" },
            decisions = facts.count { it.type == "decision" },
            constraints = facts.count { it.type == "constraint" },
            fileReferences = facts.count { it.type == "file" },
            contextInfo = facts.count { it.type == "context" },
        )
    }

    /** 从消息历史构建压缩记录列表 */
    fun buildCompressionHistory(messages: List<ChatMessage>): List<CompressionRecord> {
        return messages.filter {
            it.role == ChatRole.Model && it.content.startsWith("[上下文压缩累计]")
        }.mapNotNull { msg ->
            val removed = Regex("""累计移除\s*<?(\d+)k?""")
                .find(msg.content)?.groupValues?.get(1)?.toIntOrNull()
            val target = if (removed != null && !msg.content.contains("k")) removed else (removed ?: 0) * 1000
            // 从消息间距估算压缩次数
            val summaryGenerated = msg.content.contains("摘要") || msg.content.contains("记忆")
            CompressionRecord(
                timestamp = msg.timestamp,
                removedTokens = target,
                triggerType = CompressionRecord.TriggerType.AutoThreshold,
                keptExchanges = ChatConfig.KEEP_EXCHANGES,
                summaryGenerated = summaryGenerated,
                keyFactsPreserved = 0,
            )
        }.distinctBy { it.timestamp }
    }

    // === Token 估算 ===
    private fun estimateTokens(text: String): Int {
        if (text.isEmpty()) return 0
        val bytes = text.toByteArray(Charsets.UTF_8)
        return (bytes.size / ChatConfig.UTFS_BYTES_PER_TOKEN).toInt().coerceAtLeast(1)
    }

    private fun estimateTotalTokens(messages: List<ChatMessage>): Int =
        messages.sumOf { estimateTokens(it.content) }
}

/** 颜色色阶工具 */
object HeatColors {
    fun ratioColor(ratio: Float): Long = when {
        ratio < 0.3f -> 0xFF4CAF50  // 绿色 安全
        ratio < 0.5f -> 0xFF8BC34A  // 浅绿
        ratio < 0.7f -> 0xFFFFA000  // 黄
        ratio < 0.85f -> 0xFFFF6F00 // 橙
        else          -> 0xFFE53935  // 红 紧张
    }

    fun ratioColorSmooth(ratio: Float): Long {
        val r = (ratio * 2f).coerceIn(0f, 1f)
        val g = (1f - ratio * 1.2f).coerceIn(0f, 1f)
        val red = (r * 220 + (1 - r) * 76).toInt()
        val green = (g * 175 + (1 - g) * 175).toInt()
        return (0xFF shl 24) or (red shl 16) or (green shl 8) or 50
    }

    // 分段色板
    val segmentColors = listOf(
        0xFF4CAF50, 0xFF8BC34A, 0xFFCDDC39,
        0xFFFFC107, 0xFFFF9800, 0xFFFF5722,
        0xFFE53935, 0xFFC62828,
    )

    val memoryColors = listOf(
        0xFFE91E63, 0xFF9C27B0, 0xFF673AB7,
        0xFF3F51B5, 0xFF2196F3, 0xFF00BCD4,
        0xFF009688, 0xFF4CAF50,
    )
}
