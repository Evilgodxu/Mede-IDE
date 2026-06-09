package com.template.jh.core.memory

import android.content.Context
import android.util.Log
import com.template.jh.core.search.VectorIndex
import com.template.jh.core.utils.FileLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import kotlin.math.min

/**
 * 对话历史多层级记忆系统。
 *
 * Layer 2 - 短期记忆：内存中保留最近 50 轮，自动提取摘要+关键词
 * Layer 3 - 摘要记忆：压缩持久化，按时间分段
 * Layer 4 - 语义索引：TF-IDF 向量索引，可跨全部历史搜索
 *
 * 用于本地模型（4K 限制）绕过活动窗口限制，通过工具检索翻数十倍以上历史。
 */
class ConversationMemory(private val context: Context) {

    // === 数据模型 ===

    /** 单条记忆条目 */
    data class Entry(
        val id: String = UUID.randomUUID().toString().take(8),
        val timestamp: Long = System.currentTimeMillis(),
        val role: String = "",          // user / model / tool
        val content: String = "",        // 原始内容（短期层保留）
        val summary: String = "",        // 摘要（长期层使用）
        val keywords: List<String> = emptyList(),
        val filePaths: List<String> = emptyList(),
        val hasCode: Boolean = false,
        val hasToolCall: Boolean = false,
        val conversationId: String = "", // 所属对话 ID，空表示跨对话
    )

    /** 时间段摘要 */
    data class Summary(
        val periodStart: Long,
        val periodEnd: Long,
        val summary: String,
        val entryCount: Int,
    )

    // === 各层存储 ===

    // Layer 2: 短期记忆（内存）
    private val shortTerm = mutableListOf<Entry>()
    private val maxShortTerm = 50

    // Layer 3: 摘要记忆（持久化）
    private val summaries = mutableListOf<Summary>()
    private var summariesDirty = false

    // Layer 4: 语义索引
    private val vectorIndex = VectorIndex()

    // 持久化文件
    private val memoryDir: File get() = File(context.filesDir, "conversation_memory")
    private val shortTermFile: File get() = File(memoryDir, "short_term.json")
    private val summaryFile: File get() = File(memoryDir, "summaries.json")
    private val indexDir: File get() = File(memoryDir, "vector_index")

    // === 配置 ===
    companion object {
        private const val TAG = "ConversationMemory"
        private const val SUMMARY_ENTRY_MIN = 5       // 每 5 条建一个摘要段
        private const val SHORT_TERM_FLUSH_THRESHOLD = 3  // 短期满 50 条时触发压缩
        private const val MAX_SUMMARY_LEN = 300        // 每条摘要最大字符数
    }

    // === 核心 API ===

    /** 添加一条记忆（自动分发到各层） */
    suspend fun addEntry(
        role: String,
        content: String,
        filePaths: List<String> = emptyList(),
        conversationId: String = "",
    ) = withContext(Dispatchers.Default) {
        val entry = Entry(
            id = UUID.randomUUID().toString().take(8),
            timestamp = System.currentTimeMillis(),
            role = role,
            content = content,
            summary = summarizeContent(content),
            keywords = extractKeywords(content),
            filePaths = filePaths,
            hasCode = content.contains("```") || content.contains("```"),
            hasToolCall = content.contains("[工具调用:") || content.contains("\"tool_name\"") || content.contains("\"name\""),
            conversationId = conversationId,
        )

        // Layer 2: 短期
        shortTerm.add(entry)
        if (shortTerm.size > maxShortTerm) {
            compressShortTerm()
        }

        // Layer 4: 语义索引
        val indexedContent = buildString {
            appendLine("角色: ${entry.role}")
            appendLine("关键词: ${entry.keywords.joinToString(", ")}")
            if (entry.summary.isNotBlank()) appendLine("摘要: ${entry.summary}")
            if (entry.hasCode) appendLine("[含代码块]")
            append(entry.content.take(2000))
        }
        vectorIndex.addDocument("memory_${entry.id}", indexedContent)

        Log.d(TAG, "addEntry: role=$role id=${entry.id} kw=${entry.keywords.size}")
    }

    /** 从对话消息批量添加（每轮调用一次） */
    suspend fun addMessages(messages: List<ChatMessageAdapter>, conversationId: String = "") {
        for (msg in messages) {
            val paths = extractFilePaths(msg.content)
            addEntry(role = msg.role, content = msg.content, filePaths = paths, conversationId = conversationId)
        }
        // 达到摘要阈值时构建摘要
        if (shortTerm.size % SUMMARY_ENTRY_MIN == 0) {
            buildSummary()
        }
        save()
    }

    /** 搜索记忆（跨 Layer 2 + Layer 4），可选按对话隔离 */
    fun search(query: String, topK: Int = 5, conversationId: String? = null): List<Entry> {
        if (query.isBlank()) return emptyList()

        // Layer 2: 短期记忆匹配（关键词命中）
        val queryLower = query.lowercase()
        val queryKeywords = queryLower.split(Regex("\\s+")).filter { it.length > 1 }
        val shortTermHits = shortTerm.filter { entry ->
            if (conversationId != null && entry.conversationId != conversationId) return@filter false
            queryKeywords.any { kw ->
                entry.keywords.any { it.contains(kw) } ||
                entry.summary.lowercase().contains(kw) ||
                entry.content.lowercase().contains(kw)
            }
        }

        // Layer 4: 语义搜索
        val semanticResults = vectorIndex.search(query, topK = topK * 2)
        val semanticEntries = semanticResults.mapNotNull { match ->
            val id = match.filePath.removePrefix("memory_")
            val entry = shortTerm.find { it.id == id }
            if (entry != null && conversationId != null && entry.conversationId != conversationId) null
            else entry
        }.filterNotNull()

        // 合并去重，按相关性排序
        val combined = LinkedHashSet<Entry>()
        combined.addAll(shortTermHits.take(topK))
        combined.addAll(semanticEntries.take(topK))
        return combined.toList().take(topK)
    }

    /** 搜索记忆（跨对话版本，兼容旧调用） */
    fun search(query: String, topK: Int = 5): List<Entry> = search(query, topK, null)

    /** 获取最近 N 条记忆 */
    fun recent(count: Int = 5): List<Entry> =
        shortTerm.takeLast(count.coerceAtMost(shortTerm.size)).reversed()

    /** 获取摘要上下文（用于注入提示词），可选按对话隔离 */
    fun getMemoryContext(conversationId: String = ""): String {
        if (shortTerm.isEmpty() && summaries.isEmpty()) return ""

        val sb = StringBuilder()
        sb.appendLine("【对话历史记忆】")

        // Layer 3: 摘要
        if (summaries.isNotEmpty()) {
            sb.appendLine("--- 长期记忆摘要 ---")
            summaries.takeLast(3).forEach { s ->
                sb.appendLine("[${formatTimestamp(s.periodStart)}] ${s.summary}")
            }
        }

        // Layer 2: 最近几条摘要（优先当前对话）
        if (shortTerm.isNotEmpty()) {
            val recentEntries = if (conversationId.isNotBlank()) {
                // 优先当前对话，不足则补充跨对话
                val current = shortTerm.filter { it.conversationId == conversationId }.takeLast(3)
                val others = shortTerm.filter { it.conversationId != conversationId }.takeLast(3 - current.size)
                (current + others)
            } else {
                shortTerm.takeLast(3)
            }
            if (recentEntries.isNotEmpty()) {
                sb.appendLine("--- 近期对话回顾 ---")
                recentEntries.forEach { e ->
                    val tag = if (e.conversationId.isNotBlank() && e.conversationId == conversationId) "[当前对话]" else ""
                    sb.appendLine("$tag[${e.role}] ${e.summary}")
                }
            }
        }

        return sb.toString().trimEnd()
    }

    /** 获取当前对话的记忆上下文（兼容旧调用） */
    fun getMemoryContext(): String = getMemoryContext("")

    /** 获取语义搜索结果的格式化文本（供工具返回） */
    fun searchFormatted(query: String, topK: Int = 5): String {
        val results = search(query, topK)
        if (results.isEmpty()) return "未找到相关记忆。"

        return buildString {
            appendLine("对话历史搜索结果（${results.size} 条）:")
            results.forEachIndexed { i, e ->
                appendLine("  $i. [${e.role}] ${e.summary}")
                if (e.filePaths.isNotEmpty()) appendLine("     文件: ${e.filePaths.joinToString(", ")}")
                if (e.keywords.isNotEmpty()) appendLine("     关键词: ${e.keywords.joinToString(", ")}")
                appendLine("     时间: ${formatTimestamp(e.timestamp)}")
            }
        }
    }

    /** 获取格式化后的最近记忆 */
    fun recentFormatted(count: Int = 5): String {
        val entries = recent(count)
        if (entries.isEmpty()) return "暂无对话历史。"

        return buildString {
            appendLine("最近 ${entries.size} 条对话记忆:")
            entries.forEachIndexed { i, e ->
                appendLine("  $i. [${e.role}] ${e.summary}")
            }
        }
    }

    // === 持久化 ===

    suspend fun load() = withContext(Dispatchers.IO) {
        try {
            if (!memoryDir.exists()) memoryDir.mkdirs()

            // 加载短期记忆
            if (shortTermFile.exists()) {
                val json = shortTermFile.readText()
                val arr = JSONArray(json)
                shortTerm.clear()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    shortTerm.add(Entry(
                        id = obj.optString("id"),
                        timestamp = obj.optLong("timestamp"),
                        role = obj.optString("role"),
                        content = obj.optString("content", ""),
                        summary = obj.optString("summary", ""),
                        keywords = jsonArrToList(obj.optJSONArray("keywords")),
                        filePaths = jsonArrToList(obj.optJSONArray("filePaths")),
                        hasCode = obj.optBoolean("hasCode"),
                        hasToolCall = obj.optBoolean("hasToolCall"),
                        conversationId = obj.optString("conversationId", ""),
                    ))
                }
            }

            // 加载摘要
            if (summaryFile.exists()) {
                val json = summaryFile.readText()
                val arr = JSONArray(json)
                summaries.clear()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    summaries.add(Summary(
                        periodStart = obj.optLong("periodStart"),
                        periodEnd = obj.optLong("periodEnd"),
                        summary = obj.optString("summary"),
                        entryCount = obj.optInt("entryCount"),
                    ))
                }
            }

            // 加载语义索引；若索引为空但 shortTerm 有数据则重建
            vectorIndex.load(indexDir)
            if (vectorIndex.size == 0 && shortTerm.isNotEmpty()) {
                shortTerm.forEach { entry ->
                    val indexedContent = buildString {
                        appendLine("角色: ${entry.role}")
                        appendLine("关键词: ${entry.keywords.joinToString(", ")}")
                        if (entry.summary.isNotBlank()) appendLine("摘要: ${entry.summary}")
                        if (entry.hasCode) appendLine("[含代码块]")
                        append(entry.content)
                    }
                    vectorIndex.addDocument("memory_${entry.id}", indexedContent)
                }
                vectorIndex.save(indexDir)
                Log.d(TAG, "vector index rebuilt from ${shortTerm.size} entries")
            }

            Log.d(TAG, "loaded: ${shortTerm.size} entries, ${summaries.size} summaries, ${vectorIndex.size} indexed")
            FileLogger.d(TAG, "loaded: ${shortTerm.size} entries, ${summaries.size} summaries, ${vectorIndex.size} indexed")
        } catch (e: Exception) {
            Log.w(TAG, "load failed: ${e.message}")
            FileLogger.w(TAG, "load failed: ${e.message}")
        }
    }

    suspend fun save() = withContext(Dispatchers.IO) {
        try {
            if (!memoryDir.exists()) memoryDir.mkdirs()

            // 保存短期记忆（仅摘要层，不保存原始内容节省空间）
            val shortArr = JSONArray()
            shortTerm.forEach { e ->
                shortArr.put(JSONObject().apply {
                    put("id", e.id)
                    put("timestamp", e.timestamp)
                    put("role", e.role)
                    put("content", e.content.take(500))  // 原始内容只存 500 字符
                    put("summary", e.summary)
                    put("keywords", JSONArray(e.keywords))
                    put("filePaths", JSONArray(e.filePaths))
                    put("hasCode", e.hasCode)
                    put("hasToolCall", e.hasToolCall)
                    put("conversationId", e.conversationId)
                })
            }
            shortTermFile.writeText(shortArr.toString(2))

            // 保存摘要
            if (summariesDirty) {
                val sumArr = JSONArray()
                summaries.forEach { s ->
                    sumArr.put(JSONObject().apply {
                        put("periodStart", s.periodStart)
                        put("periodEnd", s.periodEnd)
                        put("summary", s.summary)
                        put("entryCount", s.entryCount)
                    })
                }
                summaryFile.writeText(sumArr.toString(2))
                summariesDirty = false
            }

            // 保存语义索引
            vectorIndex.save(indexDir)

            Log.d(TAG, "saved: ${shortTerm.size} entries, ${vectorIndex.size} indexed")
            FileLogger.d(TAG, "saved: ${shortTerm.size} entries, ${vectorIndex.size} indexed")
        } catch (e: Exception) {
            Log.w(TAG, "save failed: ${e.message}")
        }
    }

    /** 清除全部记忆（含磁盘 + 索引） */
    suspend fun clear() = withContext(Dispatchers.IO) {
        shortTerm.clear()
        summaries.clear()
        vectorIndex.clear()
        summariesDirty = true
        // 清除磁盘文件
        memoryDir.deleteRecursively()
        Log.d(TAG, "memory cleared, disk files removed")
    }

    // === 内部方法 ===

    /** 批量压缩短期记忆为摘要 */
    private suspend fun buildSummary() {
        if (shortTerm.isEmpty()) return
        val startTime = shortTerm.first().timestamp
        val endTime = shortTerm.last().timestamp

        val compressed = shortTerm.map { it.summary }.joinToString("; ")
        val summary = if (compressed.length > MAX_SUMMARY_LEN) {
            compressed.take(MAX_SUMMARY_LEN) + "..."
        } else compressed

        summaries.add(Summary(
            periodStart = startTime,
            periodEnd = endTime,
            summary = summary,
            entryCount = shortTerm.size,
        ))
        summariesDirty = true
    }

    /** 短期溢出时：丢弃最早的一半（已摘要化的） */
    private fun compressShortTerm() {
        val keep = shortTerm.takeLast(maxShortTerm / 2)
        shortTerm.clear()
        shortTerm.addAll(keep)
    }

    /** 提取关键词：高频有意义词 */
    private fun extractKeywords(text: String): List<String> {
        val stopWords = setOf(
            "the", "a", "an", "is", "are", "was", "were", "be", "been", "being",
            "have", "has", "had", "do", "does", "did", "will", "would", "could",
            "should", "may", "might", "must", "shall", "can", "need",
            "to", "of", "in", "for", "on", "with", "at", "by", "from", "as",
            "into", "through", "during", "before", "after", "above", "below",
            "then", "once", "here", "there", "when", "where", "why", "how",
            "all", "each", "few", "more", "most", "other", "some", "such",
            "no", "nor", "not", "only", "own", "same", "so", "than", "too",
            "very", "just", "and", "but", "if", "or", "because", "until",
            "while", "what", "which", "who", "whom", "this", "that", "these",
            "those", "am", "it", "its", "we", "our", "you", "your", "he",
            "him", "his", "she", "her", "they", "them", "their",
            "get", "set", "use", "val", "var", "fun", "class", "return",
            "null", "true", "false", "import", "package", "override",
            "是", "的", "了", "在", "有", "和", "就", "不", "人", "都", "一",
            "一个", "上", "也", "很", "到", "说", "要", "去", "你", "会",
            "着", "没有", "看", "好", "自己", "这", "他", "她", "它", "们",
        )
        val raw = text.lowercase()
        // 提取长词和文件路径
        val words = Regex("""[a-zA-Z_]\w{2,}""").findAll(raw).map { it.value }.toList()
        val paths = Regex("""[\w/.]+\.[a-zA-Z]{2,4}""").findAll(raw).map { it.value }.toList()
        val all = words + paths
        val freq = all.groupingBy { it }.eachCount()
        return freq.entries
            .filter { it.key.length > 2 && it.key !in stopWords }
            .sortedByDescending { it.value }
            .take(8)
            .map { it.key }
    }

    /** 提取文件路径 */
    private fun extractFilePaths(text: String): List<String> {
        return Regex("""[\w/.]+\.[a-zA-Z]{2,4}""").findAll(text)
            .map { it.value }
            .filter { it.contains("/") }
            .toList()
    }

    /** 内容摘要 */
    private fun summarizeContent(text: String): String {
        val cleaned = text
            .replace(Regex("```[\\s\\S]*?```"), "[代码块]")
            .replace(Regex("\\[工具调用:.*?\\]"), "[工具调用]")
            .replace(Regex("\\{[\"\\w\\s,:]+\\}"), "[工具结果]")
            .replace(Regex("\\n{2,}"), " ")
            .trim()
        return if (cleaned.length > 120) cleaned.take(120) + "..." else cleaned
    }

    private fun formatTimestamp(ts: Long): String {
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = ts }
        return String.format("%tH:%tM", cal, cal)
    }

    private fun jsonArrToList(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).map { arr.optString(it) }
    }
}

/**
 * 用于 addMessages 的消息适配。
 * 由调用方（ChatViewModel）将 ChatMessage 映射为此接口。
 */
data class ChatMessageAdapter(
    val role: String,
    val content: String,
)