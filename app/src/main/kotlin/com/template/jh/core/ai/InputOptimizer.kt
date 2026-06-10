package com.template.jh.core.ai

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Draw
import androidx.compose.ui.graphics.vector.ImageVector
import com.template.jh.data.source.local.toSamplerConfig
import kotlinx.coroutines.withTimeoutOrNull
/**
 * 输入优化器 — 独立于主推理工作流。
 *
 * 职责：针对不同场景（编码/小说/文案）提供输入优化建议，
 * 不依赖主对话状态，不占用上下文窗口。
 */
class InputOptimizer(
    private val cloudLLMClient: com.template.jh.data.source.remote.CloudLLMClient? = null,
    private val liteRTManager: com.template.jh.data.source.local.LiteRTManager? = null,
) {
    enum class Mode(val label: String, val icon: ImageVector, val description: String) {
        CODE("代码", Icons.Default.Code, "优化代码编写指令，补充上下文细节"),
        NOVEL("小说", Icons.Default.AutoStories, "优化小说创作提示，丰富角色与情节"),
        COPYWRITING("文案", Icons.Default.Draw, "优化文案策划，增强表达力与吸引力"),
        ;

        fun prompt(): String = when (this) {
            CODE -> """你是一个代码编辑器输入优化工具。用户正在写代码相关的指令。
将用户的输入优化为更清晰、精确的编程指令。添加必要的上下文细节（如语言、框架、文件路径等）。
ONLY输出优化后的文本，不要添加任何解释。
如果输入已经足够清晰，直接输出原文本。"""

            NOVEL -> """你是一个小说创作助手。用户在创作故事内容。
优化用户的小说创作提示：丰富角色设定、场景描写、情节构思、对话风格等。
保持用户原有的创作意图，补充使故事更生动的元素。
ONLY输出优化后的文本，不要添加任何解释。
如果输入已经足够好，直接输出原文本。"""

            COPYWRITING -> """你是一个专业文案策划优化工具。用户正在撰写文案/策划内容。
优化用户的文案输入：增强表达力、优化结构、精确用词、提升吸引力。
根据不同文案类型（营销/公关/策划/创意）调整语气和风格。
ONLY输出优化后的文本，不要添加任何解释。
如果输入已经足够好，直接输出原文本。"""
        }
    }

    /** 使用本地模型优化输入（返回优化后文本） */
    suspend fun optimizeWithLocal(text: String, mode: Mode): String {
        val manager = liteRTManager ?: return text
        if (!manager.isInitialized) return text

        // 优化指令拼入用户消息而非系统指令，确保模型正确执行
        val userMessage = "${mode.prompt()}\n\n$text"
        val conv = manager.createConversation(
            com.google.ai.edge.litertlm.ConversationConfig(
                samplerConfig = manager.modelParams.toSamplerConfig(),
            )
        )
        return conv.use { c ->
            val optimized = StringBuilder()
            val collectResult = withTimeoutOrNull(60_000L) {
                c.sendMessageAsync(
                    com.google.ai.edge.litertlm.Message.user(userMessage)
                ).collect { chunk -> optimized.append(chunk.toString()) }
            }
            if (collectResult == null) text
            else optimized.toString().trim().ifEmpty { text }
        }
    }

    /** 使用云端模型优化输入（返回优化后文本） */
    suspend fun optimizeWithCloud(
        text: String,
        mode: Mode,
        config: com.template.jh.model.chat.CloudModelConfig,
    ): String {
        val client = cloudLLMClient ?: return text
        val historyMessages = listOf(
            com.template.jh.model.chat.ChatMessage(
                role = com.template.jh.model.chat.ChatRole.User,
                content = text,
            )
        )
        val result = StringBuilder()
        client.sendMessage(
            config = config,
            systemPrompt = mode.prompt(),
            messages = historyMessages,
            onChunk = { result.append(it) },
            toolsJson = "[]",
            imagePaths = emptyList(),
        )
        return result.toString().trim().ifEmpty { text }
    }
}
