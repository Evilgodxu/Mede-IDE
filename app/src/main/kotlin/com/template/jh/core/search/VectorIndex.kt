package com.template.jh.core.search

import java.util.PriorityQueue
import kotlin.math.ln
import kotlin.math.sqrt

/** 索引代码块 */
data class IndexedChunk(
    val filePath: String,
    val startLine: Int,
    val endLine: Int,
    val text: String,
)

/** 搜索结果 */
data class SearchMatch(
    val filePath: String,
    val score: Double,
    val snippet: String,
    val startLine: Int,
    val endLine: Int,
)

/** 基于 TF-IDF 稀疏向量 + 余弦相似度的内存向量检索索引 */
class VectorIndex {

    private val chunks = mutableListOf<IndexedChunk>()
    private val vectors = mutableListOf<Map<String, Double>>()  // term -> tf-idf weight
    private var df = mutableMapOf<String, Int>()                 // 文档频率
    private var totalDocs = 0

    /** 清空索引 */
    fun clear() {
        chunks.clear()
        vectors.clear()
        df.clear()
        totalDocs = 0
    }

    /** 索引数 */
    val size: Int get() = chunks.size

    /** 向索引中添加文件内容 */
    fun addDocument(filePath: String, content: String) {
        val lines = content.lines()
        val chunkSize = 50
        val overlap = 10

        for (start in 0..<lines.size step (chunkSize - overlap)) {
            val end = (start + chunkSize).coerceAtMost(lines.size)
            val chunkText = lines.subList(start, end).joinToString("\n")
            if (chunkText.isBlank()) continue

            val chunk = IndexedChunk(filePath, start + 1, end, chunkText)
            val tokens = tokenize(chunkText)
            if (tokens.isEmpty()) continue

            val tf = computeTf(tokens)
            chunks.add(chunk)
            vectors.add(tf)
            // 更新 DF
            tokens.toSet().forEach { t -> df[t] = (df[t] ?: 0) + 1 }
            totalDocs++
        }
    }

    /** 搜索并返回 top-K 结果 */
    fun search(query: String, topK: Int = 10): List<SearchMatch> {
        if (totalDocs == 0 || query.isBlank()) return emptyList()

        val queryTokens = tokenize(query)
        if (queryTokens.isEmpty()) return emptyList()

        val queryTf = computeTf(queryTokens)
        val queryVec = applyIdf(queryTf)

        // 计算所有 chunk 的 TF-IDF 向量
        val docsTfIdf = vectors.map { applyIdf(it) }

        // 余弦相似度 + 堆排序取 topK
        val heap = PriorityQueue<Pair<Int, Double>>(compareBy { it.second })

        docsTfIdf.forEachIndexed { idx, docVec ->
            val score = cosineSimilarity(queryVec, docVec)
            if (score > 0) {
                heap.offer(idx to score)
                if (heap.size > topK) heap.poll()
            }
        }

        // 收集并排序结果
        val results = mutableListOf<SearchMatch>()
        while (heap.isNotEmpty()) {
            val (idx, score) = heap.poll()!!
            val chunk = chunks[idx]
            val snippet = extractSnippet(chunk)
            results.add(SearchMatch(
                filePath = chunk.filePath,
                score = score,
                snippet = snippet,
                startLine = chunk.startLine,
                endLine = chunk.endLine,
            ))
        }
        results.reverse() // 从低到高 -> 高到低

        // 按文件去重，保留分数最高的块
        return deduplicateByFile(results, topK)
    }

    /** 按文件路径去重，每个文件只保留最高分块 */
    private fun deduplicateByFile(results: List<SearchMatch>, limit: Int): List<SearchMatch> {
        val best = mutableMapOf<String, SearchMatch>()
        for (r in results) {
            val existing = best[r.filePath]
            if (existing == null || r.score > existing.score) {
                best[r.filePath] = r
            }
        }
        return best.entries
            .sortedByDescending { it.value.score }
            .take(limit)
            .map { it.value }
    }

    /** 代码感知分词：保留标识符、拆 camelCase/snake_case */
    private fun tokenize(text: String): List<String> {
        val raw = text.lowercase()
        val tokens = mutableListOf<String>()

        // 提取所有标识符和关键词
        val identPattern = Regex("""[a-z_]\w*""")
        identPattern.findAll(raw).forEach { m ->
            val word = m.value
            // 拆 camelCase: "findUserById" -> ["find", "user", "by", "id"]
            tokens.addAll(splitCamelCase(word))
        }

        // 过滤短词和常见停用词
        val stopWords = setOf(
            "the", "a", "an", "is", "are", "was", "were", "be", "been", "being",
            "have", "has", "had", "do", "does", "did", "will", "would", "could",
            "should", "may", "might", "must", "shall", "can", "need", "dare",
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
        )

        return tokens.filter { it.length > 1 && it !in stopWords }
    }

    /** 拆分 camelCase 和 snake_case */
    private fun splitCamelCase(word: String): List<String> {
        val parts = mutableListOf<String>()

        // 先按 snake_case 拆分
        val snakeParts = word.split("_").filter { it.isNotEmpty() }
        for (part in snakeParts) {
            // 再按 camelCase 拆分: "findUserById"
            val camelParts = Regex("""[a-z]+|[A-Z][a-z]*|\d+""").findAll(part).map { it.value.lowercase() }.toList()
            if (camelParts.isNotEmpty()) {
                parts.addAll(camelParts)
            } else {
                parts.add(part)
            }
        }
        return parts
    }

    /** 计算词频 (TF): term -> normalized frequency */
    private fun computeTf(tokens: List<String>): Map<String, Double> {
        if (tokens.isEmpty()) return emptyMap()
        val termCount = mutableMapOf<String, Int>()
        tokens.forEach { t -> termCount[t] = (termCount[t] ?: 0) + 1 }
        val maxFreq = termCount.values.max().toDouble()
        return termCount.mapValues { (_, count) -> count / maxFreq }
    }

    /** 应用 IDF 权重: tf-idf = tf * log(N / df) */
    private fun applyIdf(tf: Map<String, Double>): Map<String, Double> {
        if (totalDocs == 0) return tf
        return tf.mapValues { (term, tfVal) ->
            val docFreq = df[term] ?: 1
            tfVal * ln(totalDocs.toDouble() / docFreq)
        }
    }

    /** 余弦相似度 */
    private fun cosineSimilarity(a: Map<String, Double>, b: Map<String, Double>): Double {
        var dot = 0.0
        var normA = 0.0
        var normB = 0.0

        // a 的向量贡献
        for ((term, weightA) in a) {
            normA += weightA * weightA
            val weightB = b[term] ?: 0.0
            if (weightB > 0) {
                dot += weightA * weightB
            }
        }
        // b 中 a 没有的词
        for ((_, weightB) in b) {
            normB += weightB * weightB
        }

        val denom = sqrt(normA) * sqrt(normB)
        return if (denom == 0.0) 0.0 else dot / denom
    }

    /** 提取摘要片段：包含最高 tf-idf 词的行附近上下 3 行 */
    private fun extractSnippet(chunk: IndexedChunk): String {
        val lines = chunk.text.lines()
        if (lines.size <= 7) return chunk.text
        return lines.take(7).joinToString("\n") + "\n..."
    }

    /** 索引统计信息 */
    fun stats(): String {
        val fileCount = chunks.map { it.filePath }.distinct().size
        val totalTerms = vectors.sumOf { it.size }
        return "索引: ${fileCount} 文件, ${chunks.size} 代码块, ${df.size} 唯一词项"
    }
}
