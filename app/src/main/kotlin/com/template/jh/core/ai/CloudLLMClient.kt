package com.template.jh.core.ai

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

// 云端大模型配置（单个配置）
data class CloudModelConfig(
    val enabled: Boolean = false,
    val apiEndpoint: String = "https://api.openai.com/v1",
    val apiKey: String = "",
    val modelName: String = "gpt-4o",
)

// 云端模型配置档案（可保存多个，自由切换）
data class CloudModelProfile(
    val id: String = "",
    val name: String = "",            // 用户自定义名称，如"DeepSeek V4"
    val apiEndpoint: String = "https://api.openai.com/v1",
    val apiKey: String = "",
    val modelName: String = "gpt-4o",
)

/** API 调用返回的用量信息 */
data class ApiUsage(
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
)

// 云端大模型客户端（OpenAI 兼容 API /chat/completions + SSE 流式）
class CloudLLMClient(private val context: Context) {

    private val connectTimeout = 30000
    private val readTimeout = 60000

    // 发送消息并流式收集响应，返回完整响应文本 + 用量信息
    suspend fun sendMessage(
        config: CloudModelConfig,
        systemPrompt: String,
        messages: List<ChatMessage>,
        onChunk: (String) -> Unit,
    ): Pair<String, ApiUsage> = withContext(Dispatchers.IO) {
        val endpoint = config.apiEndpoint.trimEnd('/') + "/chat/completions"

        val msgs = JSONArray().apply {
            put(JSONObject().apply {
                put("role", "system")
                put("content", systemPrompt)
            })
            for (msg in messages) {
                if (msg.content.isBlank() && msg.role != ChatRole.Model) continue
                val obj = JSONObject()
                when (msg.role) {
                    ChatRole.User -> {
                        obj.put("role", "user")
                        obj.put("content", msg.content)
                    }
                    ChatRole.Model -> {
                        obj.put("role", "assistant")
                        obj.put("content", msg.content)
                    }
                    ChatRole.Tool -> {
                        obj.put("role", "tool")
                        obj.put("tool_call_id", msg.toolCallId ?: "call_${msg.id.take(8)}")
                        obj.put("content", msg.content)
                    }
                    else -> continue
                }
                put(obj)
            }
        }

        val body = JSONObject().apply {
            put("model", config.modelName)
            put("messages", msgs)
            put("stream", true)
            put("max_tokens", 8192)
            put("temperature", 0.7)
        }

        val conn = URL(endpoint).openConnection() as HttpURLConnection
        conn.apply {
            requestMethod = "POST"
            connectTimeout = connectTimeout
            readTimeout = readTimeout
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer ${config.apiKey}")
            setRequestProperty("Accept", "text/event-stream")
            doOutput = true
        }

        val fullText = StringBuilder()
        var usage = ApiUsage()

        try {
            OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }

            val responseCode = conn.responseCode
            if (responseCode != 200) {
                val errorBody = try {
                    conn.errorStream?.bufferedReader()?.readText() ?: conn.responseMessage
                } catch (_: Exception) { conn.responseMessage }
                throw RuntimeException("API error $responseCode: $errorBody")
            }

            BufferedReader(InputStreamReader(conn.inputStream, "UTF-8")).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val l = line ?: continue
                    if (!l.startsWith("data: ")) continue
                    val data = l.removePrefix("data: ").trim()
                    if (data == "[DONE]") break
                    try {
                        val json = JSONObject(data)
                        // 提取 token 用量（部分 API 在最后一个 chunk 中返回）
                        val usageObj = json.optJSONObject("usage")
                        if (usageObj != null) {
                            usage = ApiUsage(
                                promptTokens = usageObj.optInt("prompt_tokens", usage.promptTokens),
                                completionTokens = usageObj.optInt("completion_tokens", usage.completionTokens),
                            )
                        }
                        val choices = json.optJSONArray("choices")
                        if (choices == null || choices.length() == 0) continue
                        val delta = choices.getJSONObject(0).optJSONObject("delta") ?: continue
                        val content = delta.optString("content", "")
                        if (content.isNotEmpty()) {
                            fullText.append(content)
                            onChunk(content)
                        }
                    } catch (_: Exception) {
                        // 跳过无法解析的 SSE 行
                    }
                }
            }
        } catch (e: Exception) {
            conn.disconnect()
            throw e
        }
        conn.disconnect()
        Pair(fullText.toString(), usage)
    }

    // 验证 API 连接是否正常（非流式短请求）
    suspend fun verifyConnection(config: CloudModelConfig): String = withContext(Dispatchers.IO) {
        val endpoint = config.apiEndpoint.trimEnd('/') + "/chat/completions"
        val body = JSONObject().apply {
            put("model", config.modelName)
            put("messages", JSONArray().apply {
                put(JSONObject().apply { put("role", "user"); put("content", "ping") })
            })
            put("stream", false)
            put("max_tokens", 5)
        }
        val conn = URL(endpoint).openConnection() as HttpURLConnection
        conn.apply {
            requestMethod = "POST"
            connectTimeout = 10000
            readTimeout = 10000
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer ${config.apiKey}")
            doOutput = true
        }
        try {
            OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }
            val code = conn.responseCode
            if (code == 200) "ok"
            else {
                val err = try { conn.errorStream?.bufferedReader()?.readText()?.take(200) } catch (_: Exception) { null }
                "error $code: ${err ?: conn.responseMessage}"
            }
        } catch (e: Exception) {
            "连接失败: ${e.message}"
        } finally {
            conn.disconnect()
        }
    }

    // 获取可用模型列表（OpenAI 兼容 /models 接口）
    suspend fun fetchModels(apiEndpoint: String, apiKey: String): List<String> = withContext(Dispatchers.IO) {
        if (apiEndpoint.isBlank()) return@withContext emptyList()
        val endpoint = apiEndpoint.trimEnd('/') + "/models"
        val conn = URL(endpoint).openConnection() as HttpURLConnection
        conn.apply {
            requestMethod = "GET"
            connectTimeout = 10000
            readTimeout = 10000
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("Accept", "application/json")
        }
        try {
            val code = conn.responseCode
            if (code != 200) return@withContext emptyList()
            val response = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(response)
            val data = json.optJSONArray("data") ?: return@withContext emptyList()
            val models = mutableListOf<String>()
            for (i in 0 until data.length()) {
                val obj = data.getJSONObject(i)
                val id = obj.optString("id", "")
                if (id.isNotEmpty()) models.add(id)
            }
            models.sorted()
        } catch (e: Exception) {
            emptyList()
        } finally {
            conn.disconnect()
        }
    }
}
