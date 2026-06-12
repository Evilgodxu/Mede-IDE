package com.template.jh.data.source.remote

import android.content.Context
import com.template.jh.model.chat.ApiUsage
import com.template.jh.model.chat.ChatMessage
import com.template.jh.model.chat.ChatRole
import com.template.jh.model.chat.ToolCallInfo
import com.template.jh.model.chat.CloudModelConfig
import com.template.jh.model.chat.CloudToolCall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.util.Base64
import java.util.concurrent.TimeUnit

// 云端大模型客户端（OpenAI 兼容 API /chat/completions + SSE 流式）
// 使用 OkHttp 替代 HttpURLConnection，获得连接池复用 + Gzip 自动解压
class CloudLLMClient(private val context: Context) {

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    /** 将图片文件读为 base64 data URI */
    private fun imageFileToDataUri(path: String): String? = try {
        val file = File(path)
        if (!file.exists()) return null
        val ext = path.substringAfterLast('.', "jpg").lowercase()
        FileInputStream(file).use { input ->
            val bytes = input.readBytes()
            val b64 = Base64.getEncoder().encodeToString(bytes)
            "data:image/$ext;base64,$b64"
        }
    } catch (_: Exception) { null }

    /** 构建请求体 JSON 字符串 */
    private fun buildRequestBody(
        config: CloudModelConfig,
        systemPrompt: String,
        messages: List<ChatMessage>,
        toolsJson: String?,
        imagePaths: List<String>,
    ): String {
        val imageDataUris = imagePaths.mapNotNull { imageFileToDataUri(it) }
        val msgs = JSONArray().apply {
            put(JSONObject().apply {
                put("role", "system")
                put("content", systemPrompt)
            })
            for ((idx, msg) in messages.withIndex()) {
                if (msg.content.isBlank() && msg.role != ChatRole.Model) continue
                val obj = JSONObject()
                when (msg.role) {
                    ChatRole.User -> {
                        obj.put("role", "user")
                        val isLastUser = idx == messages.indexOfLast { it.role == ChatRole.User }
                        if (imageDataUris.isNotEmpty() && isLastUser) {
                            val contentArr = JSONArray().apply {
                                put(JSONObject().apply {
                                    put("type", "text")
                                    put("text", msg.content)
                                })
                                for (dataUri in imageDataUris) {
                                    put(JSONObject().apply {
                                        put("type", "image_url")
                                        put("image_url", JSONObject().apply { put("url", dataUri) })
                                    })
                                }
                            }
                            obj.put("content", contentArr)
                        } else {
                            obj.put("content", msg.content)
                        }
                    }
                    ChatRole.Model -> {
                        obj.put("role", "assistant")
                        // 有 tool_calls 时 content 应为 null（OpenAI 规范）
                        if (msg.toolCalls.isNotEmpty() && msg.content.isBlank()) {
                            obj.put("content", JSONObject.NULL)
                        } else {
                            obj.put("content", msg.content)
                        }
                        if (msg.toolCalls.isNotEmpty()) {
                            obj.put("tool_calls", JSONArray().apply {
                                msg.toolCalls.forEach { tc ->
                                    put(JSONObject().apply {
                                        put("id", tc.id)
                                        put("type", "function")
                                        put("function", JSONObject().apply {
                                            put("name", tc.name)
                                            // arguments 必须是合法 JSON 字符串
                                            val argsJson = try {
                                                org.json.JSONObject(tc.arguments)
                                            } catch (_: Exception) {
                                                org.json.JSONObject()
                                            }
                                            put("arguments", argsJson.toString())
                                        })
                                    })
                                }
                            })
                        }
                    }
                    ChatRole.Tool -> {
                        obj.put("role", "tool")
                        obj.put("tool_call_id", msg.toolCallId ?: "call_${msg.id.take(8)}")
                        obj.put("content", msg.content)
                    }
                    ChatRole.System -> {
                        obj.put("role", "system")
                        obj.put("content", msg.content)
                    }
                }
                put(obj)
            }
        }
        return JSONObject().apply {
            put("model", config.modelName)
            put("messages", msgs)
            put("stream", true)
            put("max_tokens", config.maxTokens)
            put("temperature", 0.7)
            if (!toolsJson.isNullOrBlank()) {
                put("tools", JSONArray(toolsJson))
            }
        }.toString()
    }

    /** 发送消息并流式收集响应，返回完整响应文本 + 用量信息 + 原生工具调用 */
    suspend fun sendMessage(
        config: CloudModelConfig,
        systemPrompt: String,
        messages: List<ChatMessage>,
        onChunk: (String) -> Unit,
        toolsJson: String? = null,
        imagePaths: List<String> = emptyList(),
    ): Triple<String, ApiUsage, List<CloudToolCall>> = withContext(Dispatchers.IO) {
        val endpoint = config.apiEndpoint.trimEnd('/') + "/chat/completions"
        val jsonBody = buildRequestBody(config, systemPrompt, messages, toolsJson, imagePaths)

        val request = Request.Builder()
            .url(endpoint)
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .header("Authorization", "Bearer ${config.apiKey}")
            .header("Accept", "text/event-stream")
            .build()

        val fullText = StringBuilder()
        var usage = ApiUsage()
        data class TcAccumulator(var id: String = "", var name: String = "", val args: StringBuilder = StringBuilder())
        val toolCallAccumulators = mutableMapOf<Int, TcAccumulator>()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errorBody = response.body.string()
                throw RuntimeException("API error ${response.code}: $errorBody")
            }
            response.body.let { body ->
                val source = body.source()
                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: continue
                    if (!line.startsWith("data: ")) continue
                    val data = line.removePrefix("data: ").trim()
                    if (data == "[DONE]") break
                    try {
                        val json = JSONObject(data)
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
                        // 原生 tool_calls
                        val tcArray = delta.optJSONArray("tool_calls")
                        if (tcArray != null) {
                            for (i in 0 until tcArray.length()) {
                                val tc = tcArray.getJSONObject(i)
                                val idx = tc.optInt("index", 0)
                                val builder = toolCallAccumulators.getOrPut(idx) { TcAccumulator() }
                                val id = tc.optString("id", "")
                                if (id.isNotEmpty()) builder.id = id
                                val func = tc.optJSONObject("function")
                                if (func != null) {
                                    val name = func.optString("name", "")
                                    if (name.isNotEmpty()) builder.name = name
                                    val args = func.optString("arguments", "")
                                    if (args.isNotEmpty()) builder.args.append(args)
                                }
                            }
                        }
                        // 文本内容
                        val content = delta.optString("content", "")
                        if (content.isNotEmpty()) {
                            fullText.append(content)
                            onChunk(content)
                        }
                    } catch (_: Exception) { }
                }
            }
        }

        val toolCalls = toolCallAccumulators.entries
            .sortedBy { it.key }
            .mapNotNull { (_, b) ->
                val args = b.args.toString()
                if (b.name.isNotEmpty()) CloudToolCall(
                    id = b.id.ifEmpty { "call_${b.name.take(8)}" },
                    functionName = b.name,
                    arguments = args.ifEmpty { "{}" },
                ) else null
            }
        Triple(fullText.toString(), usage, toolCalls)
    }

    /** 验证 API 连接是否正常（非流式短请求） */
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
        try {
            val request = Request.Builder()
                .url(endpoint)
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .header("Authorization", "Bearer ${config.apiKey}")
                .build()
            val response = client.newCall(request).execute()
            val code = response.code
            val msg = if (code == 200) "ok" else {
                "error $code: ${response.body.string().take(200)}"
            }
            response.close()
            msg
        } catch (e: Exception) {
            "连接失败: ${e.message}"
        }
    }

    /** 获取可用模型列表（OpenAI 兼容 /models 接口） */
    suspend fun fetchModels(apiEndpoint: String, apiKey: String): List<String> = withContext(Dispatchers.IO) {
        if (apiEndpoint.isBlank()) return@withContext emptyList()
        val endpoint = apiEndpoint.trimEnd('/') + "/models"
        try {
            val request = Request.Builder()
                .url(endpoint)
                .header("Authorization", "Bearer $apiKey")
                .header("Accept", "application/json")
                .build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) { response.close(); return@withContext emptyList() }
            val bodyStr = response.body.string()
            val json = JSONObject(bodyStr)
            val data = json.optJSONArray("data") ?: return@withContext emptyList()
            val models = mutableListOf<String>()
            for (i in 0 until data.length()) {
                val id = data.getJSONObject(i).optString("id", "")
                if (id.isNotEmpty()) models.add(id)
            }
            models.sorted()
        } catch (e: Exception) { emptyList() }
    }
}
