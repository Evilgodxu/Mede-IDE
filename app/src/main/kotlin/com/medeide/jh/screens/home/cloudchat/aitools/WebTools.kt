package com.medeide.jh.screens.home.cloudchat.aitools

import org.json.JSONArray
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class WebTools {
    companion object {
        fun buildOpenAIToolsJson(): JSONArray {
            val arr = JSONArray()
            arr.put(buildWebSearchTool())
            arr.put(buildWebFetchTool())
            return arr
        }

        private fun buildWebSearchTool() = toolDef("webSearch",
            "通过搜索引擎搜索互联网上的最新信息。适合获取实时资讯、文档、API参考等。",
            required = listOf("query"),
            "query" to p("string", "搜索关键词，如 'Android Jetpack Compose Button 文档'"),
        )

        private fun buildWebFetchTool() = toolDef("webFetch",
            "获取指定 URL 的网页内容并返回 Markdown 格式。适合阅读文档、博客等。",
            required = listOf("url"),
            "url" to p("string", "完整 URL，如 'https://developer.android.com/jetpack/compose'"),
        )
    }
}

// ── 实现 ──

fun webSearch(query: String): String {
    return try {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = URL("https://html.duckduckgo.com/html/?q=$encoded")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
        val reader = BufferedReader(InputStreamReader(conn.inputStream))
        val html = reader.readText()
        reader.close()
        // 简单提取搜索结果文本
        val snippets = Regex("""<a[^>]*class="result__a"[^>]*>[\s\S]*?<a[^>]*class="result__snippet"[^>]*>([\s\S]*?)</a>""")
            .findAll(html)
            .mapNotNull { it.groupValues.getOrNull(1)?.replace(Regex("<[^>]+>"), "")?.trim() }
            .filter { it.isNotBlank() }
            .take(5)
            .toList()
        if (snippets.isEmpty()) "[OK] 搜索完成，无结果（可能被限制）"
        else "[OK] 搜索结果:\n${snippets.mapIndexed { i, s -> "${i + 1}. $s" }.joinToString("\n")}"
    } catch (e: Exception) {
        "[ERROR] 搜索失败: ${e.message}"
    }
}

fun webFetch(url: String): String {
    return try {
        val u = URL(if (!url.startsWith("http")) "https://$url" else url)
        val conn = u.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 10_000
        conn.readTimeout = 15_000
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
        val reader = BufferedReader(InputStreamReader(conn.inputStream))
        val html = reader.readText()
        reader.close()
        val text = html
            .replace(Regex("""<script[^>]*>[\s\S]*?</script>"""), "")
            .replace(Regex("""<style[^>]*>[\s\S]*?</style>"""), "")
            .replace(Regex("<[^>]+>"), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
        val maxLen = 5000
        val content = if (text.length > maxLen) text.take(maxLen) + "\n\n... (内容已截断，完整 ${text.length} 字符)" else text
        "[OK] $content"
    } catch (e: Exception) {
        "[ERROR] 获取失败: ${e.message}"
    }
}
