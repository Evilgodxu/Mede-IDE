package com.medeide.jh.screens.home.landscape.collab.chat.utils

import com.medeide.jh.model.chat.ChatMessage
import com.medeide.jh.model.chat.ChatRole
import com.medeide.jh.model.chat.DisplayItem
import com.medeide.jh.model.chat.DisplayRole
import com.medeide.jh.model.chat.FileOperation

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

/** 映射为 DisplayItem 列表并关联文件操作记录（按 parentMessageId 绑定到对应消息） */
fun toDisplayItemsWithFileOps(
    messages: List<ChatMessage>,
    fileOperations: List<FileOperation>,
): List<DisplayItem> {
    // 按 parentMessageId 分组文件操作
    val opsByMsg = fileOperations.groupBy { it.parentMessageId }
    return messages.mapNotNull { msg ->
        msg.toDisplayItemOrNull()?.let { item ->
            val ops = opsByMsg[msg.id]
            if (ops.isNullOrEmpty()) item else item.copy(fileOperations = ops)
        }
    }
}

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
    // 移除深度思考块（<think> 和 [think] 两种格式）
    result = result.replace(Regex("<think>[\\s\\S]*?</think>"), "")
    result = result.replace(Regex("\\[think][\\s\\S]*?\\[/think]"), "")
    result = result.replace(Regex("""\n{3,}"""), "\n\n").trim()
    return result
}

/** 匹配独立行的工具调用 JSON 对象 */
private val TOOL_CALL_LINE_REGEX = Regex(
    """(?m)^\s*\{[\s\S]*?"(?:name|tool_name)"\s*:\s*"[^"]*"[\s\S]*?\}\s*$"""
)
