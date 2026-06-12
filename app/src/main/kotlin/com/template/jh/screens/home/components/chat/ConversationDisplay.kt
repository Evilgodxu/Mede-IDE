package com.template.jh.screens.home.components.chat

import com.template.jh.model.chat.ChatMessage
import com.template.jh.model.chat.ChatRole
import com.template.jh.model.chat.DisplayItem
import com.template.jh.model.chat.DisplayRole

/** ChatMessage → DisplayItem 一对一映射，Tool/System 角色返回 null */
fun ChatMessage.toDisplayItemOrNull(): DisplayItem? {
    val displayRole = when (role) {
        ChatRole.User -> DisplayRole.User
        ChatRole.Model -> DisplayRole.Model
        else -> return null
    }
    return DisplayItem(
        id = id,
        role = displayRole,
        content = content.cleanForDisplay(),
        channelContent = channelContent,
        isStreaming = isStreaming,
        timestamp = timestamp,
        imageUris = imageUris,
    )
}

/** 直接映射列表，过滤掉 Tool/System */
fun List<ChatMessage>.toDisplayItems(): List<DisplayItem> =
    mapNotNull { it.toDisplayItemOrNull() }

/** 清理消息内容中的控制前缀和工具调用 JSON */
private fun String.cleanForDisplay(): String {
    if (isBlank()) return this
    var result = this
    // 移除开头的 [用户请求] 标记行
    result = result.replace(Regex("^\\[用户请求]\\s*"), "").trimStart('\n', '\r')
    // 移除末尾的 [已附加 X 张图片] 及 [用户指定的文件] 块
    result = result.replace(Regex("\\n\\[已附加.*?]$"), "")
    result = result.replace(Regex("\\n\\[用户指定的文件.*"), "")
    // 移除独立行的工具调用 JSON（仅松散匹配）
    result = result.replace(TOOL_CALL_LINE_REGEX, "")
    // 移除深度思考 <think> 标记（漏网之鱼）
    result = result.replace(Regex("</?think>"), "")
    result = result.replace(Regex("""\n{3,}"""), "\n\n").trim()
    return result
}

/** 匹配独立行的工具调用 JSON 对象 */
private val TOOL_CALL_LINE_REGEX = Regex(
    """(?m)^\s*\{[\s\S]*?"(?:name|tool_name)"\s*:\s*"[^"]*"[\s\S]*?\}\s*$"""
)
