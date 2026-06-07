package com.template.jh.core.ai

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.google.ai.edge.litertlm.Tool
import com.google.ai.edge.litertlm.ToolParam
import com.google.ai.edge.litertlm.ToolSet
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

// DuckDuckGo 联网搜索工具，注册到 ConversationConfig.tools 后模型可自动调用
class WebSearchTool(private val context: Context) : ToolSet {

    @Tool(description = "搜索互联网获取最新信息。当需要查询实时信息、专业术语定义、最新资料或不确定的内容时调用此工具。")
    fun searchWeb(
        @ToolParam(description = "搜索查询关键词，使用简洁准确的搜索词") query: String,
    ): Map<String, Any> {
        return try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = URL("https://api.duckduckgo.com/?q=$encodedQuery&format=json&no_html=1&skip_disambig=1")
            val conn = url.openConnection() as HttpURLConnection
            conn.apply {
                connectTimeout = 10000
                readTimeout = 10000
                setRequestProperty("User-Agent", "Android-AI-IDE/1.0")
            }
            val response = BufferedReader(InputStreamReader(conn.inputStream)).readText()
            conn.disconnect()

            val json = JSONObject(response)
            val results = mutableListOf<String>()

            json.optString("AbstractText", "").takeIf { it.isNotBlank() }?.let {
                results.add("摘要: $it")
                json.optString("AbstractURL", "").takeIf { u -> u.isNotBlank() }?.let { results.add("来源: $it") }
            }

            json.optString("Answer", "").takeIf { it.isNotBlank() }?.let { results.add("答案: $it") }

            json.optJSONArray("RelatedTopics")?.let { topics ->
                for (i in 0 until minOf(topics.length(), 3)) {
                    topics.optJSONObject(i)?.optString("Text")?.takeIf { it.isNotBlank() }?.let { results.add("相关内容: $it") }
                }
            }

            if (results.isEmpty()) mapOf("result" to "未找到搜索结果，请使用已有知识回答")
            else mapOf("result" to results.joinToString("\n\n"))
        } catch (e: Exception) {
            copyCrashToClipboard("searchWeb", e)
            mapOf("result" to "搜索服务暂时不可用: ${e.message}")
        }
    }

    private fun copyCrashToClipboard(action: String, e: Exception) {
        try {
            val info = "[WebSearchTool 异常]\n操作: $action\n异常: ${e.javaClass.simpleName}\n消息: ${e.message}\n堆栈: ${e.stackTraceToString()}"
            (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("崩溃信息", info))
        } catch (_: Exception) {}
    }
}
