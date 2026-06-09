package com.template.jh.core.ai

enum class DisplayRole { User, Model, ToolActivity }

data class DisplayItem(
    val id: String,
    val role: DisplayRole,
    val content: String,           // 显示文本（已过滤工具调用 JSON/标记）
    val thinkBlocks: List<String>, // 提取的 [think] 块内容
    val isStreaming: Boolean,
    val timestamp: Long,
    val imageUris: List<android.net.Uri> = emptyList(), // 图片 URI 列表（仅 User 消息）
)

private val thinkRegex = Regex("""\[think\](.*?)\[/think]""", RegexOption.DOT_MATCHES_ALL)
private val toolJsonRegex = Regex(
    """\{\s*"tool_name"\s*:\s*"[^"]*"\s*(?:,\s*"arguments"\s*:\s*\{[^}]*\}\s*)?\}""",
)

/**
 * 移除用户消息中注入给 AI 模型看的控制前缀，仅保留用户实际输入内容。
 */
private fun stripUserControlPrefixes(text: String): String {
    // 移除开头的 [用户请求] 或 [用户消息] 标记行
    val cleaned = text.replace(Regex("^\\[用户请求]\\s*"), "")
        .replace(Regex("^\\[用户消息]\\s*"), "")
        .trimStart('\n', '\r')
        // 移除末尾的 [已附加 X 张图片] 及 [用户指定的文件...] 块
        .replace(Regex("\\n\\[已附加.*]$"), "")
        .replace(Regex("\\n\\[用户指定的文件.*"), "")
        .trim()
    return cleaned
}

private fun thinkBlocks(text: String): Pair<List<String>, String> {
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
    val allThinkBlocks = mutableListOf<String>()
    val contentParts = mutableListOf<String>()

    for (msg in modelMsgs) {
        val (blocks, cleaned) = thinkBlocks(msg.content)
        allThinkBlocks.addAll(blocks)
        val stripped = stripToolCalls(cleaned)
        if (stripped.isNotBlank()) contentParts.add(stripped)
    }

    // 追加工具结果到显示内容
    for (tMsg in toolMsgs) {
        if (tMsg.content.isNotBlank()) contentParts.add(tMsg.content)
    }

    if (contentParts.isEmpty() && modelMsgs.isNotEmpty()) {
        val last = modelMsgs.last()
        val (_, cleaned) = thinkBlocks(last.content)
        val stripped = stripToolCalls(cleaned)
        if (stripped.isNotBlank()) contentParts.add(stripped)
        else if (toolMsgs.isNotEmpty()) contentParts.addAll(toolMsgs.mapNotNull { it.content.takeIf { it.isNotBlank() } })
    }

    return DisplayItem(
        id = modelMsgs.first().id, role = DisplayRole.Model,
        content = contentParts.joinToString("\n\n").trim(),
        thinkBlocks = allThinkBlocks,
        isStreaming = modelMsgs.any { it.isStreaming },
        timestamp = modelMsgs.first().timestamp,
    )
}
