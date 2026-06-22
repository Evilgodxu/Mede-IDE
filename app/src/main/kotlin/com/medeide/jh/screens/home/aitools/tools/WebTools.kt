package com.medeide.jh.screens.home.aitools.tools

import android.util.Log
import com.medeide.jh.data.storage.FileManager
import com.medeide.jh.data.utils.FileLogger
import com.medeide.jh.screens.home.logic.FileOperationEvents
import org.jsoup.Jsoup
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class WebTools(private val fileManager: FileManager?) {

    // ================================================================
    //  联网搜索  ←→ WebSearch(query, num, lr)
    // ================================================================

    fun searchWeb(query: String, num: Int = 5, lr: String = ""): String {
        if (query.isBlank()) return err("Search query is blank.")
        Log.d("AIToolSet", "searchWeb: query=$query num=$num lr=$lr")
        FileLogger.d("AIToolSet", "searchWeb: query=$query num=$num lr=$lr")
        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val doc = Jsoup.connect("https://html.duckduckgo.com/html/?q=$encoded")
                .userAgent("Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                .timeout(15000)
                .get()
            val results = doc.select(".result").mapNotNull { el ->
                val titleEl = el.selectFirst(".result__title a") ?: return@mapNotNull null
                val title = titleEl.text().trim()
                val snippet = el.selectFirst(".result__snippet")?.text()?.trim() ?: ""
                val link = titleEl.attr("href")
                if (title.isBlank()) null else Triple(title, snippet, link)
            }.take(num.coerceIn(1, 20))
            if (results.isEmpty()) return err("No search results for: $query")
            FileLogger.d("AIToolSet", "searchWeb: ${results.size} results")
            results.joinToString("\n---\n") { (title, snippet, link) ->
                buildString {
                    appendLine("**$title**")
                    if (snippet.isNotBlank()) appendLine(snippet)
                    if (link.isNotBlank()) appendLine(link)
                }
            }
        } catch (e: Exception) {
            Log.e("AIToolSet", "searchWeb failed: ${e.message}", e)
            FileLogger.e("AIToolSet", "searchWeb failed: ${e.message}", e)
            err("${e.message}")
        }
    }

    // ================================================================
    //  访问网页
    // ================================================================

    fun visitWeb(url: String): String {
        if (url.isBlank()) return err("URL is empty.")
        Log.d("AIToolSet", "访问网页: url=$url")
        FileLogger.d("AIToolSet", "访问网页: url=$url")
        return try {
            val doc = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                .timeout(15000)
                .followRedirects(true)
                .get()
            val title = doc.title().ifBlank { "(no title)" }
            val body = doc.body()?.text()?.trim()?.take(5000) ?: "(empty body)"
            FileLogger.d("AIToolSet", "访问网页: $title (${body.length} chars)")
            ok("Title: $title\n\n$body")
        } catch (e: Exception) {
            Log.e("AIToolSet", "访问网页失败: ${e.message}", e)
            FileLogger.e("AIToolSet", "访问网页失败: ${e.message}", e)
            err("${e.message}")
        }
    }

    // ================================================================
    //  下载文件
    // ================================================================

    fun downloadFile(url: String, destination: String): String {
        if (url.isBlank()) return err("URL is empty.")
        val fm = fileManager ?: return err("No project folder is open.")
        return try {
            val base = fm.projectDirPath.ifEmpty { fm.storageRootPath }
            val dstFile = File(base, resolvePathOrAbsolute(destination, fileManager))
            dstFile.parentFile?.mkdirs()
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 15000
            connection.readTimeout = 30000
            connection.instanceFollowRedirects = true
            connection.connect()
            if (connection.responseCode !in 200..299) {
                return err("HTTP ${connection.responseCode}: ${connection.responseMessage}")
            }
            connection.inputStream.use { input ->
                FileOutputStream(dstFile).use { output ->
                    input.copyTo(output)
                }
            }
            FileOperationEvents.notify(destination, "create")
            val size = formatSize(dstFile.length())
            FileLogger.d("AIToolSet", "下载完成: $destination ($size)")
            ok("Downloaded: $destination ($size)")
        } catch (e: Exception) {
            err("Download failed: ${e.message}")
        }
    }

    // ================================================================
    //  HTTP 请求
    // ================================================================

    fun httpRequest(url: String, method: String = "GET", headers: String = "", body: String = ""): String {
        if (url.isBlank()) return err("URL is empty.")
        Log.d("AIToolSet", "HTTP请求: method=$method url=$url")
        FileLogger.d("AIToolSet", "HTTP请求: method=$method url=$url")
        return try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = method.uppercase()
            connection.connectTimeout = 15000
            connection.readTimeout = 30000
            connection.instanceFollowRedirects = true
            if (headers.isNotBlank()) {
                try {
                    val json = org.json.JSONObject(headers)
                    for (k in json.keys()) connection.setRequestProperty(k, json.getString(k))
                } catch (_: Exception) { }
            }
            if (body.isNotBlank() && (method.uppercase() == "POST" || method.uppercase() == "PUT")) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            connection.connect()
            val code = connection.responseCode
            val response = if (code in 200..299) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $code"
            }
            val preview = response.take(3000)
            val suffix = if (response.length > 3000) "\n... (${response.length - 3000} more chars)" else ""
            FileLogger.d("AIToolSet", "HTTP $method $url -> $code (${response.length} chars)")
            ok("HTTP $code\n$preview$suffix")
        } catch (e: Exception) {
            Log.e("AIToolSet", "HTTP请求失败: ${e.message}", e)
            FileLogger.e("AIToolSet", "HTTP请求失败: ${e.message}", e)
            err("${e.message}")
        }
    }

    // ================================================================
    //  工具定义
    // ================================================================

    companion object {
        fun buildOpenAIToolsJson(): org.json.JSONArray {
            val arr = org.json.JSONArray()
            arr.put(buildSearchWebTool())
            arr.put(buildVisitWebTool())
            arr.put(buildDownloadFileTool())
            arr.put(buildHttpRequestTool())
            return arr
        }

        fun toolNames(): List<String> = listOf(
            "searchWeb", "visitWeb", "downloadFile", "httpRequest",
        )

        private fun buildSearchWebTool() = toolDef("searchWeb",
            "联网搜索最新信息。获取实时数据或超出知识范围的内容。",
            listOf("query"),
            "query" to p("string", "搜索关键词，简洁明确"),
            "num" to p("integer", "返回结果数量，1-20，默认5"),
            "lr" to p("string", "语言限制，如'lang_en'限制英文结果"),
        )
        private fun buildVisitWebTool() = toolDef("visitWeb",
            "访问网页并提取文本内容。用于查阅文档、获取网页信息等。",
            listOf("url"),
            "url" to p("string", "网页 URL"),
        )
        private fun buildDownloadFileTool() = toolDef("downloadFile",
            "从 URL 下载文件到本地路径。",
            listOf("url", "destination"),
            "url" to p("string", "文件下载 URL"),
            "destination" to p("string", "保存路径"),
        )
        private fun buildHttpRequestTool() = toolDef("httpRequest",
            "发送 HTTP 请求，返回响应内容。支持 GET/POST/PUT/DELETE。",
            listOf("url"),
            "url" to p("string", "请求 URL"),
            "method" to p("string", "HTTP 方法：GET/POST/PUT/DELETE，默认 GET"),
            "headers" to p("string", "可选的请求头，JSON 对象字符串"),
            "body" to p("string", "可选的请求体（POST/PUT 时使用）"),
        )
    }
}
