package com.medeide.jh.core.data.repository

import android.content.Context
import com.medeide.jh.core.data.logging.FileLogger
import com.medeide.jh.model.chat.ChatMessage
import com.medeide.jh.model.chat.ChatRole
import com.medeide.jh.model.chat.ConversationEntry
import com.medeide.jh.model.chat.ToolCallInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class ConversationRepository(private val context: Context) {

    private val storageFile: File
        get() = File(context.filesDir, "conversations.json")

    suspend fun load(): List<ConversationEntry> = withContext(Dispatchers.IO) {
        try {
            if (!storageFile.exists()) return@withContext emptyList()
            val json = storageFile.readText()
            val arr = JSONArray(json)
            val list = (0 until arr.length()).map { i -> parseEntry(arr.getJSONObject(i)) }
            FileLogger.d("ConvRepo", "loaded ${list.size} conversations")
            list
        } catch (e: Exception) {
            FileLogger.e("ConvRepo", "load failed", e)
            emptyList()
        }
    }

    suspend fun save(conversations: List<ConversationEntry>) = withContext(Dispatchers.IO) {
        try {
            val arr = JSONArray()
            conversations.forEach { arr.put(toJson(it)) }
            storageFile.writeText(arr.toString())
            FileLogger.d("ConvRepo", "saved ${conversations.size} conversations (${storageFile.length()} bytes)")
        } catch (e: Exception) {
            FileLogger.e("ConvRepo", "save failed", e)
        }
    }

    private fun parseEntry(obj: JSONObject): ConversationEntry = ConversationEntry(
        id = obj.optString("id", ""),
        title = obj.optString("title", ""),
        messages = parseMessages(obj.optJSONArray("messages")),
        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
    )

    private fun parseMessages(arr: JSONArray?): List<ChatMessage> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).map { i ->
            val msg = arr.getJSONObject(i)
            ChatMessage(
                id = msg.optString("id", ""),
                role = ChatRole.valueOf(msg.optString("role", "User")),
                content = msg.optString("content", ""),
                reasoningContent = msg.optString("reasoningContent", ""),
                isStreaming = false,
                timestamp = msg.optLong("timestamp", System.currentTimeMillis()),
            )
        }
    }

    private fun toJson(entry: ConversationEntry): JSONObject = JSONObject().apply {
        put("id", entry.id)
        put("title", entry.title)
        put("timestamp", entry.timestamp)
        put("messages", JSONArray().apply {
            entry.messages.forEach { msg ->
                put(JSONObject().apply {
                    put("id", msg.id)
                    put("role", msg.role.name)
                    put("content", msg.content)
                    put("reasoningContent", msg.reasoningContent)
                    put("timestamp", msg.timestamp)
                })
            }
        })
    }
}
