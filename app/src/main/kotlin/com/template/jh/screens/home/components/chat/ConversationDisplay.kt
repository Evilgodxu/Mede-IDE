package com.template.jh.screens.home.components.chat

import com.template.jh.model.chat.ChatMessage
import com.template.jh.model.chat.ChatRole
import com.template.jh.model.chat.DisplayItem
import com.template.jh.model.chat.DisplayRole

private val toolJsonRegex = Regex(
    """\{\s*"(?:name|tool_name)"\s*:\s*"[^"]*"\s*(?:,\s*"arguments"\s*:\s*\{[^}]*\}\s*)?\}""",
)

/**
 * 移除用户消息中注入给 AI 模型看的控制前缀，仅保留用户实际输入内容。
 */
private fun stripUserControlPrefixes(text: String): String {
    // 移除开头的 [用户请求] 标记行
    val cleaned = text.replace(Regex("^\\[用户请求]\\s*"), "")
        .trimStart('\n', '\r')
        // 移除末尾的 [已附加 X 张图片] 及 [用户指定的文件...] 块
        .replace(Regex("\\n\\[已附加.*]$"), "")
        .replace(Regex("\\n\\[用户指定的文件.*"), "")
        .trim()
    return cleaned
}

private fun stripToolCalls(text: String): String {
    var result = text
    // 移除独立行的工具调用 JSON
    result = toolJsonRegex.replace(result) { match ->
        val before = result.substring(0, match.range.first)
        val lineStart = before.lastIndexOf('\n') + 1
        val isStandalone = before.substring(lineStart).all { it == ' ' || it == '\t' }
        if (isStandalone) "" else match.value
    }
    result = result.replace(Regex("""\n{3,}"""), "\n\n").trim()
    return result
}

fun toDisplayItems(messages: List<ChatMessage>): List<DisplayItem> {
    if (messages.isEmpty()) return emptyList()

    val result = mutableListOf<DisplayItem>()
    var i = 0

    while (i < messages.size) {
        val msg = messages[i]
        when (msg.role) {
            ChatRole.User -> {
                result.add(DisplayItem(
                    id = msg.id, role = DisplayRole.User,
                    content = stripUserControlPrefixes(msg.content),
                    isStreaming = false,
                    timestamp = msg.timestamp, imageUris = msg.imageUris,
                ))
                i++
            }
            ChatRole.Model -> {
                val modelMsgs = mutableListOf<ChatMessage>()
                val toolMsgs = mutableListOf<ChatMessage>()
                while (i < messages.size && messages[i].role == ChatRole.Model) {
                    modelMsgs.add(messages[i]); i++
                }
                while (i < messages.size && messages[i].role == ChatRole.Tool) {
                    toolMsgs.add(messages[i]); i++
                }
                result.add(mergeModelMessages(modelMsgs, toolMsgs))
            }
            ChatRole.Tool, ChatRole.System -> { i++ }
        }
    }
    return result
}

/** 合并模型消息及紧随的工具结果，生成一条显示条目 */
private fun mergeModelMessages(modelMsgs: List<ChatMessage>, toolMsgs: List<ChatMessage> = emptyList()): DisplayItem {
    val contentParts = mutableListOf<String>()
    var thinkingContent: String? = null

    for (msg in modelMsgs) {
        val stripped = stripToolCalls(msg.content)
        if (stripped.isNotBlank()) contentParts.add(stripped)
        if (msg.channelContent != null) {
            thinkingContent = (thinkingContent ?: "") + "\n" + msg.channelContent
        }
    }

    // 追加工具结果到显示内容
    for (tMsg in toolMsgs) {
        if (tMsg.content.isNotBlank()) contentParts.add(tMsg.content)
    }

    if (contentParts.isEmpty() && modelMsgs.isNotEmpty()) {
        val last = modelMsgs.last()
        val stripped = stripToolCalls(last.content)
        if (stripped.isNotBlank()) contentParts.add(stripped)
        else if (toolMsgs.isNotEmpty()) contentParts.addAll(toolMsgs.mapNotNull { it.content.takeIf { it.isNotBlank() } })
    }

    return DisplayItem(
        id = modelMsgs.first().id, role = DisplayRole.Model,
        content = contentParts.joinToString("\n\n").trim(),
        channelContent = thinkingContent?.trim(),
        isStreaming = modelMsgs.any { it.isStreaming },
        timestamp = modelMsgs.first().timestamp,
    )
}
