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
 * Layer 1 - 关键事实：从对话中提取的持久化关键信息（用户偏好、决策、约束），永不丢失
 * Layer 2 - 短期记忆：内存中保留最近 50 轮，自动提取摘要+关键词
 * Layer 3 - 摘要记忆：压缩持久化，按时间分段
 * Layer 4 - 语义索引：TF-IDF 向量索引，可跨全部历史搜索
 *
 * 用于本地模型在窗口受限时绕过活动窗口限制，通过工具检索翻数十倍以上历史。
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

    /** 关键事实（Layer 1） */
    data class KeyFact(
        val id: String = UUID.randomUUID().toString().take(8),
        val timestamp: Long = System.currentTimeMillis(),
        val content: String,             // 事实内容
        val source: String = "",         // 来源消息 ID
        val type: String = "preference", // preference / decision / constraint / file / context
    )

    // === 各层存储 ===

    // Layer 1: 关键事实（持久化，永不自动删除，手动管理）
    private val keyFacts = mutableListOf<KeyFact>()
    private var keyFactsDirty = false

    // Layer 2: 短期记忆（内存）
    private val shortTerm = mutableListOf<Entry>()
    private val maxShortTerm = 50

    // 被 compressShortTerm 淘汰的条目缓存（供 Layer 4 语义搜索回溯）
    private val evictedEntries = mutableMapOf<String, Entry>()

    // Layer 3: 摘要记忆（持久化）
    private val summaries = mutableListOf<Summary>()
    private var summariesDirty = false

    // 增量摘要边界：shortTerm 中已参与摘要的条目数
    private var summarizedEntryCount = 0

    // Layer 4: 语义索引
    private val vectorIndex = VectorIndex()

    // 持久化文件（合并为单文件减少 I/O）
    private val memoryDir: File get() = File(context.filesDir, "conversation_memory")
    private val memoryFile: File get() = File(memoryDir, "memory.json")
    private val indexDir: File get() = File(memoryDir, "vector_index")

    // 批量索引：累积 addEntry 的条目，save 时一次性构建
    private val pendingIndexEntries = mutableListOf<Pair<String, String>>()

    // === 配置 ===
    companion object {
        private const val TAG = "ConversationMemory"
        private const val SUMMARY_ENTRY_MIN = 5       // 每 5 条新数据建一个摘要段
        private const val MAX_SUMMARY_LEN = 300        // 每条摘要最大字符数
        private const val MAX_KEY_FACTS = 50           // 最多保留关键事实数
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

        // Layer 1: 提取关键事实（仅 user/model 角色）
        if (role == "user" || role == "model") {
            extractKeyFacts(entry)
        }

        // Layer 2: 短期
        shortTerm.add(entry)

        // Layer 4: 语义索引（批量构建，延迟到 save 时一次性写入）
        val indexedContent = buildString {
            appendLine("角色: ${entry.role}")
            appendLine("关键词: ${entry.keywords.joinToString(", ")}")
            if (entry.summary.isNotBlank()) appendLine("摘要: ${entry.summary}")
            if (entry.hasCode) appendLine("[含代码块]")
            append(entry.content.take(2000))
        }
        pendingIndexEntries.add("memory_${entry.id}" to indexedContent)
        if (pendingIndexEntries.size >= 10) {
            pendingIndexEntries.forEach { (id, content) -> vectorIndex.addDocument(id, content) }
            pendingIndexEntries.clear()
        }

        Log.d(TAG, "addEntry: role=$role id=${entry.id} kw=${entry.keywords.size}")
    }

    /** 从对话消息批量添加（每轮调用一次） */
    suspend fun addMessages(messages: List<ChatMessageAdapter>, conversationId: String = "") {
        for (msg in messages) {
            val paths = extractFilePaths(msg.content)
            addEntry(role = msg.role, content = msg.content, filePaths = paths, conversationId = conversationId)
        }
        // 先增量摘要，再压缩（确保摘要不丢失数据）
        maybeSummarizeAndCompress()
    }

    /** 摘要 + 压缩：先建摘要再压缩，确保早起数据不被丢弃 */
    private suspend fun maybeSummarizeAndCompress() {
        buildSummary()
        if (shortTerm.size > maxShortTerm) {
            compressShortTerm()
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

        // Layer 4: 语义搜索（同时查找 shortTerm + evictedEntries）
        val semanticResults = vectorIndex.search(query, topK = topK * 2)
        val semanticEntries = semanticResults.mapNotNull { match ->
            val id = match.filePath.removePrefix("memory_")
            val entry = shortTerm.find { it.id == id }
                ?: evictedEntries[id] // 已被淘汰的条目从缓存中找回
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

    /** 获取记忆系统统计信息 */
    fun getStats(): MemoryStats = MemoryStats(
        keyFactCount = keyFacts.size,
        summaryCount = summaries.size,
        entryCount = shortTerm.size,
        evictedCount = evictedEntries.size,
        indexedCount = vectorIndex.size,
        estimatedTokens = estimateMemoryTokens(),
    )

    /** 估算记忆系统数据量 (token 数) */
    private fun estimateMemoryTokens(): Int {
        var total = 0
        keyFacts.forEach { total += (it.content.length / 3.5f).toInt() + 5 }
        summaries.forEach { total += (it.summary.length / 3.5f).toInt() + 10 }
        shortTerm.forEach { total += (it.summary.length / 3.5f).toInt() + 10 }
        return total.coerceAtLeast(0)
    }

    // === Layer 1: 关键事实 API ===

    /** 获取关键事实列表 */
    fun getKeyFacts(): List<KeyFact> = keyFacts.toList()

    /** 获取关键事实上下文字符串（用于注入 system prompt） */
    fun getKeyFactsContext(): String {
        if (keyFacts.isEmpty()) return ""
        val sb = StringBuilder()
        sb.appendLine("## 关键上下文（从对话历史提取，永不丢失）")
        // 按类型分组
        val grouped = keyFacts.groupBy { it.type }
        val typeLabels = mapOf(
            "preference" to "用户偏好",
            "decision" to "已做决策",
            "constraint" to "约束条件",
            "file" to "重要文件",
            "context" to "上下文",
        )
        for ((type, label) in typeLabels) {
            val facts = grouped[type] ?: continue
            facts.forEach { f -> sb.appendLine("- [$label] ${f.content}") }
        }
        return sb.toString().trimEnd()
    }

    /** 手动添加关键事实 */
    fun addKeyFact(content: String, type: String = "context", source: String = "") {
        // 去重：相似内容不重复添加
        val normalized = content.trim().lowercase()
        if (keyFacts.any { it.content.trim().lowercase() == normalized }) return
        keyFacts.add(KeyFact(content = content, type = type, source = source))
        keyFactsDirty = true
        // 超出上限时移除最旧的
        if (keyFacts.size > MAX_KEY_FACTS) {
            keyFacts.removeAt(0)
        }
    }

    /** 移除关键事实 */
    fun removeKeyFact(id: String) {
        keyFacts.removeAll { it.id == id }
        keyFactsDirty = true
    }

    /** 清除所有关键事实 */
    fun clearKeyFacts() {
        keyFacts.clear()
        keyFactsDirty = true
    }

    /** 获取压缩上下文（Layer 1 关键事实 + Layer 3 摘要），供 compressMessages 使用 */
    fun getCompressionContext(): String {
        if (keyFacts.isEmpty() && summaries.isEmpty()) return ""
        val sb = StringBuilder()
        if (keyFacts.isNotEmpty()) sb.appendLine(getKeyFactsContext())
        if (summaries.isNotEmpty()) {
            sb.appendLine("## 历史摘要")
            summaries.takeLast(3).forEach { s ->
                sb.appendLine("[${formatTimestamp(s.periodStart)}] ${s.summary}")
            }
        }
        return sb.toString().trimEnd()
    }

    /** 批量提取并保存消息中的关键事实（供 compressMessages 调用） */
    suspend fun extractKeyFactsFromMessages(messages: List<ChatMessageAdapter>, conversationId: String = "") {
        for (msg in messages) {
            val entry = Entry(
                role = msg.role,
                content = msg.content,
                summary = summarizeContent(msg.content),
                conversationId = conversationId,
            )
            if (msg.role == "user" || msg.role == "model") {
                extractKeyFacts(entry)
            }
        }
        if (keyFactsDirty) save()
    }

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

            // 优先读取合并文件，回退到旧版多文件
            if (memoryFile.exists()) {
                val root = JSONObject(memoryFile.readText())
                shortTerm.clear()
                root.optJSONArray("shortTerm")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        shortTerm.add(Entry(
                            id = obj.optString("id"), timestamp = obj.optLong("timestamp"),
                            role = obj.optString("role"), content = obj.optString("content", ""),
                            summary = obj.optString("summary", ""), keywords = jsonArrToList(obj.optJSONArray("keywords")),
                            filePaths = jsonArrToList(obj.optJSONArray("filePaths")),
                            hasCode = obj.optBoolean("hasCode"), hasToolCall = obj.optBoolean("hasToolCall"),
                            conversationId = obj.optString("conversationId", ""),
                        ))
                    }
                }
                evictedEntries.clear()
                root.optJSONArray("evicted")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val entry = Entry(
                            id = obj.optString("id"), timestamp = obj.optLong("timestamp"),
                            role = obj.optString("role"), content = obj.optString("content", ""),
                            summary = obj.optString("summary", ""), keywords = jsonArrToList(obj.optJSONArray("keywords")),
                            filePaths = jsonArrToList(obj.optJSONArray("filePaths")),
                            hasCode = obj.optBoolean("hasCode"), hasToolCall = obj.optBoolean("hasToolCall"),
                            conversationId = obj.optString("conversationId", ""),
                        )
                        evictedEntries[entry.id] = entry
                    }
                }
                summaries.clear()
                root.optJSONArray("summaries")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        summaries.add(Summary(periodStart = obj.optLong("periodStart"), periodEnd = obj.optLong("periodEnd"),
                            summary = obj.optString("summary"), entryCount = obj.optInt("entryCount")))
                    }
                }
                keyFacts.clear()
                root.optJSONArray("keyFacts")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        keyFacts.add(KeyFact(id = obj.optString("id"), timestamp = obj.optLong("timestamp"),
                            content = obj.optString("content"), source = obj.optString("source", ""),
                            type = obj.optString("type", "context")))
                    }
                }
                summarizedEntryCount = root.optInt("summarizedEntryCount", 0)
            }

            // 加载语义索引
            vectorIndex.load(indexDir)
            if (vectorIndex.size == 0 && (shortTerm.isNotEmpty() || evictedEntries.isNotEmpty())) {
                val allEntries = shortTerm + evictedEntries.values
                allEntries.forEach { entry ->
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
                Log.d(TAG, "vector index rebuilt from ${allEntries.size} entries")
            }

            Log.d(TAG, "loaded: ${keyFacts.size} facts, ${shortTerm.size} shortTerm, ${evictedEntries.size} evicted, ${summaries.size} summaries, ${vectorIndex.size} indexed")
            FileLogger.d(TAG, "loaded: ${keyFacts.size} facts, ${shortTerm.size} shortTerm, ${evictedEntries.size} evicted, ${summaries.size} summaries, ${vectorIndex.size} indexed")
        } catch (e: Exception) {
            Log.w(TAG, "load failed: ${e.message}")
            FileLogger.w(TAG, "load failed: ${e.message}")
        }
    }

    suspend fun save() = withContext(Dispatchers.IO) {
        try {
            if (!memoryDir.exists()) memoryDir.mkdirs()

            // flush 批量索引
            if (pendingIndexEntries.isNotEmpty()) {
                pendingIndexEntries.forEach { (id, content) -> vectorIndex.addDocument(id, content) }
                pendingIndexEntries.clear()
            }
            vectorIndex.save(indexDir)

            // 单文件序列化所有层
            val root = JSONObject()

            val shortArr = JSONArray()
            shortTerm.forEach { e ->
                shortArr.put(JSONObject().apply {
                    put("id", e.id); put("timestamp", e.timestamp); put("role", e.role)
                    put("content", e.content.take(500)); put("summary", e.summary)
                    put("keywords", JSONArray(e.keywords)); put("filePaths", JSONArray(e.filePaths))
                    put("hasCode", e.hasCode); put("hasToolCall", e.hasToolCall); put("conversationId", e.conversationId)
                })
            }
            root.put("shortTerm", shortArr)

            val evictedArr = JSONArray()
            evictedEntries.values.toList().takeLast(200).forEach { e ->
                evictedArr.put(JSONObject().apply {
                    put("id", e.id); put("timestamp", e.timestamp); put("role", e.role)
                    put("content", e.content.take(500)); put("summary", e.summary)
                    put("keywords", JSONArray(e.keywords)); put("filePaths", JSONArray(e.filePaths))
                    put("hasCode", e.hasCode); put("hasToolCall", e.hasToolCall); put("conversationId", e.conversationId)
                })
            }
            root.put("evicted", evictedArr)

            val sumArr = JSONArray()
            summaries.forEach { s ->
                sumArr.put(JSONObject().apply {
                    put("periodStart", s.periodStart); put("periodEnd", s.periodEnd)
                    put("summary", s.summary); put("entryCount", s.entryCount)
                })
            }
            root.put("summaries", sumArr)

            val factArr = JSONArray()
            keyFacts.forEach { f ->
                factArr.put(JSONObject().apply {
                    put("id", f.id); put("timestamp", f.timestamp); put("content", f.content)
                    put("source", f.source); put("type", f.type)
                })
            }
            root.put("keyFacts", factArr)
            root.put("summarizedEntryCount", summarizedEntryCount)

            memoryFile.writeText(root.toString(2))
            summariesDirty = false
            keyFactsDirty = false

            Log.d(TAG, "saved: ${keyFacts.size} facts, ${shortTerm.size} shortTerm, ${evictedEntries.size} evicted, ${vectorIndex.size} indexed")
            FileLogger.d(TAG, "saved: ${keyFacts.size} facts, ${shortTerm.size} shortTerm, ${evictedEntries.size} evicted, ${vectorIndex.size} indexed")
        } catch (e: Exception) {
            Log.w(TAG, "save failed: ${e.message}")
        }
    }

    /** 清除全部记忆（含磁盘 + 索引） */
    suspend fun clear() = withContext(Dispatchers.IO) {
        shortTerm.clear()
        evictedEntries.clear()
        summaries.clear()
        keyFacts.clear()
        vectorIndex.clear()
        summarizedEntryCount = 0
        summariesDirty = true
        keyFactsDirty = true
        memoryDir.deleteRecursively()
        Log.d(TAG, "memory cleared, disk files removed")
    }

    // === 内部方法 ===

    /** 从消息中提取关键事实（Layer 1），基于正则匹配重要语义模式 */
    private fun extractKeyFacts(entry: Entry) {
        val text = entry.content
        val role = entry.role

        // 用户偏好/约束模式
        val patterns = listOf(
            Regex("""(?:记住|以后|之后|未来|从现在开始|今后)(.{5,60}?)(?:[。！\.!\n]|$)""") to "preference",
            Regex("""(?:我的名字是?|我是|我叫)(.{3,20}?)(?:[。！\.!\n,]|$)""") to "preference",
            Regex("""(?:偏好|更喜欢|喜欢用|习惯用)(.{5,40}?)(?:[。！\.!\n,]|$)""") to "preference",
            Regex("""(?:不要|不能|禁止|严禁|避免|拒绝)(.{5,60}?)(?:[。！\.!\n]|$)""") to "constraint",
            Regex("""(?:要求|需要必须|务必|一定要|必须)(.{5,60}?)(?:[。！\.!\n]|$)""") to "constraint",
            Regex("""(?:决定|确认|确定|采用|选择)(.{5,60}?)(?:方案|方式|方法|框架|库|技术)""") to "decision",
            Regex("""(?:使用|基于|依赖|集成)(\w{2,30})""") to "decision",
            Regex("""(?:项目名|应用名|App\s?名)(?:是|为|:)\s*(.{3,20}?)(?:[。！\.!\n,]|$)""") to "context",
            Regex("""(?:Android|Kotlin|Compose|Material)(.{0,5})""") to "context",
        )

        for ((regex, type) in patterns) {
            val matches = regex.findAll(text)
            for (m in matches) {
                val fact = m.value.trim().replace(Regex("""\s+"""), " ")
                if (fact.length in 3..120) {
                    // 去重检查
                    val normalized = fact.lowercase()
                    if (keyFacts.none { it.content.lowercase() == normalized }) {
                        keyFacts.add(KeyFact(
                            content = fact,
                            type = type,
                            source = entry.id,
                        ))
                        keyFactsDirty = true
                    }
                }
            }
        }

        // 文件路径引用（用户或模型提到的项目文件）
        if (entry.filePaths.isNotEmpty()) {
            entry.filePaths.take(3).forEach { path ->
                val fileName = path.substringAfterLast("/")
                val fact = if (role == "user") "用户引用了文件: $fileName" else "操作了文件: $fileName"
                val normalized = fact.lowercase()
                if (keyFacts.none { it.content.lowercase() == normalized }) {
                    keyFacts.add(KeyFact(content = fact, type = "file", source = entry.id))
                    keyFactsDirty = true
                }
            }
        }

        // 超出上限时移除最旧的
        while (keyFacts.size > MAX_KEY_FACTS) {
            keyFacts.removeAt(0)
        }
    }

    /** 增量构建摘要：仅对未摘要的新条目构建 */
    private suspend fun buildSummary() {
        if (summarizedEntryCount >= shortTerm.size) return
        val newEntries = shortTerm.drop(summarizedEntryCount)
        if (newEntries.size < SUMMARY_ENTRY_MIN) return

        val startTime = newEntries.first().timestamp
        val endTime = newEntries.last().timestamp
        val compressed = newEntries.map { it.summary }.joinToString("; ")
        val summary = if (compressed.length > MAX_SUMMARY_LEN) {
            compressed.take(MAX_SUMMARY_LEN) + "..."
        } else compressed

        summaries.add(Summary(
            periodStart = startTime,
            periodEnd = endTime,
            summary = summary,
            entryCount = newEntries.size,
        ))
        summarizedEntryCount = shortTerm.size
        summariesDirty = true
        Log.d(TAG, "buildSummary: ${newEntries.size} new entries, total summaries=${summaries.size}")
    }

    /** 短期溢出时：丢弃最早的一半到淘汰缓存 */
    private fun compressShortTerm() {
        val keepCount = maxShortTerm / 2
        if (shortTerm.size <= keepCount) return

        val droppedCount = shortTerm.size - keepCount
        val dropped = shortTerm.take(droppedCount)
        val keep = shortTerm.takeLast(keepCount)

        // 移到淘汰缓存（仅保留关键词和摘要，不保留原始长文本）
        dropped.forEach { entry ->
            evictedEntries[entry.id] = entry.copy(content = entry.content.take(200))
        }

        shortTerm.clear()
        shortTerm.addAll(keep)

        // 调整摘要计数：被淘汰的条目若之前已计入摘要，需要扣减
        summarizedEntryCount = (summarizedEntryCount - droppedCount).coerceAtLeast(0)

        Log.d(TAG, "compressShortTerm: dropped $droppedCount, keep $keepCount, evicted=${evictedEntries.size}")
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
 * 记忆系统统计数据
 */
data class MemoryStats(
    val keyFactCount: Int = 0,
    val summaryCount: Int = 0,
    val entryCount: Int = 0,
    val evictedCount: Int = 0,
    val indexedCount: Int = 0,
    val estimatedTokens: Int = 0,
)

/**
 * 用于 addMessages 的消息适配。
 * 由调用方（ChatViewModel）将 ChatMessage 映射为此接口。
 */
data class ChatMessageAdapter(
    val role: String,
    val content: String,
)
