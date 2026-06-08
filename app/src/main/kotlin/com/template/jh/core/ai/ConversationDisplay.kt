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
private val toolCallMarkerRegex = Regex("""\[工具调用:\s*\w+\]""")

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
        // 仅移除独立行的 JSON（行首仅空白 → {）
        val lineStart = before.lastIndexOf('\n') + 1
        val isStandalone = before.substring(lineStart).all { it == ' ' || it == '\t' }
        if (isStandalone) "" else match.value
    }
    // 移除 [工具调用: xxx] 标记及其后的内容
    result = toolCallMarkerRegex.replace(result, "")
    // 移除工具调用后的结果文本块（跟随 [工具调用: xxx] 之后的大段文本）
    // 从标记到下一个非空行或末尾
    result = result.replace(Regex("""\[工具调用:\s*\w+\].*"""), "")
    // 清理多余空行
    result = result.replace(Regex("""\n{3,}"""), "\n\n").trim()
    return result
}

fun toDisplayItems(messages: List<ChatMessage>): List<DisplayItem> {
    if (messages.isEmpty()) return emptyList()

    val result = mutableListOf<DisplayItem>()

    // 按用户消息分组：每个用户消息与其后的模型消息为一组
    var i = 0
    while (i < messages.size) {
        val msg = messages[i]
        when (msg.role) {
            ChatRole.User -> {
                result.add(DisplayItem(
                    id = msg.id,
                    role = DisplayRole.User,
                    content = stripUserControlPrefixes(msg.content),
                    thinkBlocks = emptyList(),
                    isStreaming = false,
                    timestamp = msg.timestamp,
                    imageUris = msg.imageUris,
                ))
                i++
            }
            ChatRole.Model -> {
                // 收集从当前位置开始的连续模型消息（含 isToolMessage）
                val modelMsgs = mutableListOf<ChatMessage>()
                while (i < messages.size && messages[i].role == ChatRole.Model) {
                    modelMsgs.add(messages[i])
                    i++
                }
                // 跳过中间的 Tool 消息（但已由 ViewModel 在 model 消息后插入）
                // 如果后面还有 model 消息，也一起合并
                while (i < messages.size && messages[i].role == ChatRole.Tool) {
                    i++
                }
                // 模型消息合并为一条显示条目
                result.add(mergeModelMessages(modelMsgs))
            }
            ChatRole.Tool, ChatRole.System -> {
                i++
            }
        }
    }

    return result
}

private fun mergeModelMessages(msgs: List<ChatMessage>): DisplayItem {
    val allThinkBlocks = mutableListOf<String>()
    val contentParts = mutableListOf<String>()

    for (msg in msgs) {
        val (blocks, cleaned) = thinkBlocks(msg.content)
        allThinkBlocks.addAll(blocks)

        val stripped = stripToolCalls(cleaned)
        if (stripped.isNotBlank()) contentParts.add(stripped)
    }

    // 若所有消息都被过滤（只有工具调用），取第一条消息的 think 块后的开头文本作为内容
    val last = msgs.lastOrNull()
    if (contentParts.isEmpty() && last != null) {
        val (_, cleaned) = thinkBlocks(last.content)
        val stripped = stripToolCalls(cleaned)
        if (stripped.isNotBlank()) contentParts.add(stripped)
    }

    val mergedContent = contentParts.joinToString("\n\n").trim()
    return DisplayItem(
        id = msgs.first().id,
        role = DisplayRole.Model,
        content = mergedContent,
        thinkBlocks = allThinkBlocks,
        isStreaming = msgs.any { it.isStreaming },
        timestamp = msgs.first().timestamp,
    )
}
