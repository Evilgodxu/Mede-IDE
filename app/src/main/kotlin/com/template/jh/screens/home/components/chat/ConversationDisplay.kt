package com.template.jh.screens.home.components.chat

import com.template.jh.model.chat.ChatMessage
import com.template.jh.model.chat.ChatRole
import com.template.jh.model.chat.DisplayItem
import com.template.jh.model.chat.DisplayRole

private val thinkRegex = Regex("""\[think\](.*?)\[/think]""", RegexOption.DOT_MATCHES_ALL)
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

private fun thinkBlocks(text: String): Pair<List<String>, String> {
    if (text.length > 50_000) return emptyList<String>() to text  // 超大文本跳过正则
    val blocks = thinkRegex.findAll(text).map { it.groupValues[1].trim() }.toList()
    val cleaned = text.replace(thinkRegex, "").trim()
    return blocks to cleaned
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
                    thinkBlocks = emptyList(), isStreaming = false,
                    timestamp = msg.timestamp, imageUris = msg.imageUris,
                ))
                i++
            }
            ChatRole.Model -> {
                val modelMsgs = mutableListOf<ChatMessage>()
                while (i < messages.size && messages[i].role == ChatRole.Model) {
                    modelMsgs.add(messages[i]); i++
                }
                // 跳过 tool 消息，不显示在对话中
                while (i < messages.size && messages[i].role == ChatRole.Tool) { i++ }
                result.add(mergeModelMessages(modelMsgs))
            }
            ChatRole.Tool, ChatRole.System -> { i++ }
        }
    }
    return result
}

/** 合并模型消息（过滤工具调用 JSON），生成一条显示条目，不含工具结果 */
private fun mergeModelMessages(modelMsgs: List<ChatMessage>): DisplayItem {
    val allThinkBlocks = mutableListOf<String>()
    val contentParts = mutableListOf<String>()

    for (msg in modelMsgs) {
        val (blocks, cleaned) = thinkBlocks(msg.content)
        allThinkBlocks.addAll(blocks)
        val stripped = stripToolCalls(cleaned)
        if (stripped.isNotBlank()) contentParts.add(stripped)
    }

    return DisplayItem(
        id = modelMsgs.first().id, role = DisplayRole.Model,
        content = contentParts.joinToString("\n\n").trim(),
        thinkBlocks = allThinkBlocks,
        isStreaming = modelMsgs.any { it.isStreaming },
        timestamp = modelMsgs.first().timestamp,
    )
}
