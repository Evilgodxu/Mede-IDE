package com.medeide.jh.screens.home.cloudchat.aitools

import com.medeide.jh.core.data.source.remote.CloudLLMClient
import com.medeide.jh.core.data.source.remote.ModelCancellationToken
import com.medeide.jh.model.chat.ChatMessage
import com.medeide.jh.model.chat.ChatRole
import com.medeide.jh.model.chat.CloudModelProfile

enum class OptimizeMode(val label: String, val description: String) {
    CODE("代码", "优化代码编写指令，补充上下文细节"),
    NOVEL("小说", "优化小说创作提示，丰富角色与情节"),
    COPYWRITING("文案", "优化文案策划，增强表达力与吸引力"),
}

class InputOptimizer(private val cloudLLMClient: CloudLLMClient) {

    suspend fun optimize(text: String, mode: OptimizeMode, profile: CloudModelProfile): String {
        val prompt = when (mode) {
            OptimizeMode.CODE -> """你是一个代码编辑器输入优化工具。用户正在写代码相关的指令。
将用户的输入优化为更清晰、精确的编程指令。添加必要的上下文细节（如语言、框架、文件路径等）。
ONLY输出优化后的文本，不要添加任何解释。
如果输入已经足够清晰，直接输出原文本。"""

            OptimizeMode.NOVEL -> """你是一个小说创作助手。用户在创作故事内容。
优化用户的小说创作提示：丰富角色设定、场景描写、情节构思、对话风格等。
保持用户原有的创作意图，补充使故事更生动的元素。
ONLY输出优化后的文本，不要添加任何解释。
如果输入已经足够好，直接输出原文本。"""

            OptimizeMode.COPYWRITING -> """你是一个专业文案策划优化工具。用户正在撰写文案/策划内容。
优化用户的文案输入：增强表达力、优化结构、精确用词、提升吸引力。
根据不同文案类型（营销/公关/策划/创意）调整语气和风格。
ONLY输出优化后的文本，不要添加任何解释。
如果输入已经足够好，直接输出原文本。"""
        }

        val result = StringBuilder()
        val token = ModelCancellationToken()
        try {
            cloudLLMClient.sendMessage(
                profile = profile,
                systemPrompt = prompt,
                messages = listOf(ChatMessage(role = ChatRole.User, content = text)),
                token = token,
                onText = { result.append(it) },
                toolsJson = null,
            )
        } catch (_: Exception) {
            return text
        }
        return result.toString().trim().ifEmpty { text }
    }
}
