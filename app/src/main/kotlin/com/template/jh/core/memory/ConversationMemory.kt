package com.template.jh.core.memory

import android.content.Context
import android.util.Log
import com.template.jh.core.utils.FileLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * 对话历史记忆系统。
 * 全量存储每条消息，通过工具（searchConversationMemory / getRecentConversationMemory）按需查询。
 * 不自动注入上下文，不分析关键事实，不分类。
 */
class ConversationMemory(private val context: Context) {

    data class Entry(
        val id: String = UUID.randomUUID().toString().take(8),
        val timestamp: Long = System.currentTimeMillis(),
        val role: String = "",
        val content: String = "",
        val conversationId: String = "",
    )

    private val entries = mutableListOf<Entry>()
    private val maxEntries = 500

    private val memoryDir: File get() = File(context.filesDir, "conversation_memory")
    private val memoryFile: File get() = File(memoryDir, "memory.json")

    companion object {
        private const val TAG = "ConversationMemory"
    }

    suspend fun addEntry(
        role: String,
        content: String,
        conversationId: String = "",
    ) = withContext(Dispatchers.Default) {
        val entry = Entry(
            id = UUID.randomUUID().toString().take(8),
            timestamp = System.currentTimeMillis(),
            role = role,
            content = content,
            conversationId = conversationId,
        )
        entries.add(entry)
        if (entries.size > maxEntries) {
            entries.removeAt(0)
        }
        Log.d(TAG, "添加条目: role=$role id=${entry.id}")
    }

    suspend fun addMessages(messages: List<ChatMessageAdapter>, conversationId: String = "") {
        for (msg in messages) {
            addEntry(role = msg.role, content = msg.content, conversationId = conversationId)
        }
        save()
    }

    fun search(query: String, topK: Int = 5, conversationId: String? = null): List<Entry> {
        if (query.isBlank()) return emptyList()

        val queryLower = query.lowercase()
        val queryKeywords = queryLower.split(Regex("\\s+")).filter { it.length > 1 }

        return entries.filter { entry ->
            if (conversationId != null && entry.conversationId != conversationId) return@filter false
            val content = entry.content.lowercase()
            queryKeywords.any { kw -> content.contains(kw) }
        }.take(topK)
    }

    fun search(query: String, topK: Int = 5): List<Entry> = search(query, topK, null)

    fun recent(count: Int = 5): List<Entry> =
        entries.takeLast(count.coerceAtMost(entries.size)).reversed()

    fun getStats(): MemoryStats = MemoryStats(
        entryCount = entries.size,
        estimatedTokens = entries.sumOf { (it.content.length / 3.5f).toInt() },
    )

    fun searchFormatted(query: String, topK: Int = 5, conversationId: String? = null): String {
        val results = if (conversationId != null) search(query, topK, conversationId) else search(query, topK)
        if (results.isEmpty()) return "未找到相关记忆。"

        return buildString {
            appendLine("对话历史搜索结果（${results.size} 条）:")
            results.forEachIndexed { i, e ->
                val preview = if (e.content.length > 200) e.content.take(200) + "..." else e.content
                appendLine("  $i. [${e.role}] $preview")
                appendLine("     时间: ${formatTimestamp(e.timestamp)}")
            }
        }
    }

    fun recentFormatted(count: Int = 5, conversationId: String? = null): String {
        val result = if (conversationId != null) {
            entries.filter { it.conversationId == conversationId }.takeLast(count).reversed()
        } else {
            recent(count)
        }
        if (result.isEmpty()) return "暂无对话历史。"

        return buildString {
            appendLine("最近 ${result.size} 条对话记忆:")
            result.forEachIndexed { i, e ->
                appendLine("--- ${e.role} ---")
                appendLine(e.content)
            }
        }
    }

    // === 持久化 ===

    suspend fun load() = withContext(Dispatchers.IO) {
        try {
            if (!memoryDir.exists()) memoryDir.mkdirs()
            if (memoryFile.exists()) {
                val root = JSONObject(memoryFile.readText())
                entries.clear()
                root.optJSONArray("entries")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        entries.add(Entry(
                            id = obj.optString("id"),
                            timestamp = obj.optLong("timestamp"),
                            role = obj.optString("role"),
                            content = obj.optString("content", ""),
                            conversationId = obj.optString("conversationId", ""),
                        ))
                    }
                }
            }
            Log.d(TAG, "加载完成: ${entries.size} 条记忆")
            FileLogger.d(TAG, "加载完成: ${entries.size} 条记忆")
        } catch (e: Exception) {
            Log.w(TAG, "加载失败: ${e.message}")
            FileLogger.w(TAG, "加载失败: ${e.message}")
        }
    }

    suspend fun save() = withContext(Dispatchers.IO) {
        try {
            if (!memoryDir.exists()) memoryDir.mkdirs()

            val root = JSONObject()
            val arr = JSONArray()
            entries.forEach { e ->
                arr.put(JSONObject().apply {
                    put("id", e.id)
                    put("timestamp", e.timestamp)
                    put("role", e.role)
                    put("content", e.content)
                    put("conversationId", e.conversationId)
                })
            }
            root.put("entries", arr)
            memoryFile.writeText(root.toString(2))

            Log.d(TAG, "保存完成: ${entries.size} 条记忆")
            FileLogger.d(TAG, "保存完成: ${entries.size} 条记忆")
        } catch (e: Exception) {
            Log.w(TAG, "保存失败: ${e.message}")
        }
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        entries.clear()
        memoryDir.deleteRecursively()
        Log.d(TAG, "记忆已清除")
    }

    // === 内部 ===

    private fun formatTimestamp(ts: Long): String {
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = ts }
        return String.format("%tH:%tM", cal, cal)
    }
}

data class MemoryStats(
    val entryCount: Int = 0,
    val estimatedTokens: Int = 0,
)

data class ChatMessageAdapter(
    val role: String,
    val content: String,
)
