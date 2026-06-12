package com.medeide.jh.screens.home.memory

import com.medeide.jh.screens.home.config.ChatConfig
import com.medeide.jh.model.chat.ChatMessage
import com.medeide.jh.model.chat.ChatRole

/**
 * 记忆/压缩可视化数据模型与计算引擎。
 */

data class ContextSnapshot(
    val usedTokens: Int,
    val maxTokens: Int,
    val ratio: Float,
    val isCompressed: Boolean,
    val compressedTokens: Int,
    val compressedCount: Int,
    val messageCount: Int,
    val toolCallCount: Int,
    val timestamp: Long = System.currentTimeMillis(),
)

data class TokenBreakdown(
    val systemPromptTokens: Int,
    val userMessagesTokens: Int,
    val modelMessagesTokens: Int,
    val toolOutputTokens: Int,
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

object VisualizerEngine {

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

    fun buildTokenBreakdown(messages: List<ChatMessage>, sysPromptTokens: Int): TokenBreakdown {
        var userTokens = 0
        var modelTokens = 0
        var toolTokens = 0
        for (msg in messages) {
            val t = estimateTokens(msg.content)
            when (msg.role) {
                ChatRole.User -> userTokens += t
                ChatRole.Model -> modelTokens += t
                ChatRole.Tool -> toolTokens += t
                else -> {}
            }
        }
        return TokenBreakdown(
            systemPromptTokens = sysPromptTokens,
            userMessagesTokens = userTokens,
            modelMessagesTokens = modelTokens,
            toolOutputTokens = toolTokens,
            compressedSavedTokens = 0,
        )
    }

    fun buildCompressionHistory(messages: List<ChatMessage>): List<Unit> = emptyList()

    private fun estimateTokens(text: String): Int {
        if (text.isEmpty()) return 0
        val bytes = text.toByteArray(Charsets.UTF_8)
        return (bytes.size / ChatConfig.UTFS_BYTES_PER_TOKEN).toInt().coerceAtLeast(1)
    }

    private fun estimateTotalTokens(messages: List<ChatMessage>): Int =
        messages.sumOf { estimateTokens(it.content) }
}

object HeatColors {
    fun ratioColor(ratio: Float): Long = when {
        ratio < 0.3f -> 0xFF4CAF50
        ratio < 0.5f -> 0xFF8BC34A
        ratio < 0.7f -> 0xFFFFA000
        ratio < 0.85f -> 0xFFFF6F00
        else          -> 0xFFE53935
    }
}
