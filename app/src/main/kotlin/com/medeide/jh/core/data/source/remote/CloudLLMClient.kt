package com.medeide.jh.core.data.source.remote

import com.medeide.jh.core.data.logging.FileLogger
import com.medeide.jh.model.chat.ApiUsage
import com.medeide.jh.model.chat.ChatMessage
import com.medeide.jh.model.chat.ChatRole
import com.medeide.jh.model.chat.CloudModelProfile
import com.medeide.jh.model.chat.CloudToolCall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class CloudLLMClient {

    @Volatile private var currentToken: ModelCancellationToken? = null

    fun cancelCurrentCall() {
        currentToken?.cancel()
        currentToken = null
    }

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    /** 使用令牌发送流式请求（不含重试，上层控制重试） */
    suspend fun sendMessageStream(
        profile: CloudModelProfile,
        systemPrompt: String,
        messages: List<ChatMessage>,
        token: ModelCancellationToken,
        onText: (String) -> Unit,
        onReasoning: (String) -> Unit = {},
        onToolCalls: (List<CloudToolCall>) -> Unit = {},
        toolsJson: String? = null,
    ): Triple<String, String, ApiUsage> = withContext(Dispatchers.IO) {
        val modelName = profile.modelName
        val endpoint = profile.apiEndpoint.trimEnd('/') + "/chat/completions"
        val jsonBody = buildRequestBody(profile, systemPrompt, messages, toolsJson)

        FileLogger.i("LLM", "→ $modelName msgs=${messages.size} tools=${if (toolsJson != null) "Y" else "N"}")

        val request = Request.Builder()
            .url(endpoint)
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .header("Authorization", "Bearer ${profile.apiKey}")
            .header("Accept", "text/event-stream")
            .build()

        val fullText = StringBuilder()
        val fullReasoning = StringBuilder()
        var usage = ApiUsage()

        data class TcAcc(val id: String = "", val name: String = "", val args: StringBuilder = StringBuilder())
        val tcs = mutableMapOf<Int, TcAcc>()

        val call = client.newCall(request)
        currentToken = token

        // 令牌取消 → 断开 HTTP
        token.onCancel(Runnable { call.cancel() })

        try {
            call.execute().use { response ->
                if (token.isCancelled) return@withContext Triple("", "", usage)
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: ""
                    FileLogger.e("LLM", "← $modelName HTTP ${response.code}: ${errorBody.take(200)}")
                    throw RuntimeException("API ${response.code}: $errorBody")
                }
                FileLogger.d("LLM", "← $modelName 200 OK (streaming)")
                response.body?.let { body ->
                    val source = body.source()
                    while (!source.exhausted() && !token.isCancelled) {
                        val line = source.readUtf8Line() ?: continue
                        if (!line.startsWith("data: ")) continue
                        val data = line.removePrefix("data: ").trim()
                        if (data == "[DONE]") break
                        try {
                            val json = JSONObject(data)
                            // usage（通常出现在最后一个 chunk）
                            json.optJSONObject("usage")?.let { u ->
                                usage = ApiUsage(
                                    promptTokens = u.optInt("prompt_tokens", 0),
                                    completionTokens = u.optInt("completion_tokens", 0),
                                )
                            }
                            val choices = json.optJSONArray("choices")
                            if (choices == null || choices.length() == 0) continue
                            val delta = choices.getJSONObject(0).optJSONObject("delta") ?: continue

                            // reasoning_content（DeepSeek R1 等）
                            delta.optString("reasoning_content", "").takeIf { it.isNotEmpty() }?.let { rc ->
                                fullReasoning.append(rc); onReasoning(rc)
                            }

                            // tool_calls
                            delta.optJSONArray("tool_calls")?.let { tcArr ->
                                for (i in 0 until tcArr.length()) {
                                    val tc = tcArr.getJSONObject(i)
                                    val idx = tc.optInt("index", 0)
                                    val b = tcs.getOrPut(idx) { TcAcc() }
                                    // 不可变 data class → 用 var 局部替代
                                    var id = b.id; var name = b.name
                                    tc.optString("id", "").takeIf { it.isNotEmpty() }?.let { id = it }
                                    tc.optJSONObject("function")?.let { fn ->
                                        fn.optString("name", "").takeIf { it.isNotEmpty() }?.let { name = it }
                                        fn.optString("arguments", "").takeIf { it.isNotEmpty() }?.let { b.args.append(it) }
                                    }
                                    tcs[idx] = TcAcc(id, name, b.args)
                                }
                            }

                            // content
                            delta.optString("content", "").takeIf { it.isNotEmpty() }?.let { c ->
                                fullText.append(c); onText(c)
                            }
                        } catch (_: Exception) { }
                    }
                }
                FileLogger.i("LLM", "← $modelName done tok=${usage.promptTokens}/${usage.completionTokens} text=${fullText.length} reasoning=${fullReasoning.length} tools=${tcs.size}")
            }
        } finally {
            if (currentToken === token) currentToken = null
        }

        // 构建 tool_calls
        val toolCalls = tcs.entries.sortedBy { it.key }.mapNotNull { (_, b) ->
            if (b.name.isNotEmpty()) CloudToolCall(
                id = b.id.ifEmpty { "call_${System.nanoTime()}" },
                functionName = b.name,
                arguments = b.args.toString(),
            ) else null
        }
        if (toolCalls.isNotEmpty()) onToolCalls(toolCalls)

        Triple(fullText.toString(), fullReasoning.toString(), usage)
    }

    /** 带重试的发送 */
    suspend fun sendMessage(
        profile: CloudModelProfile,
        systemPrompt: String,
        messages: List<ChatMessage>,
        token: ModelCancellationToken,
        onText: (String) -> Unit,
        onReasoning: (String) -> Unit = {},
        onToolCalls: (List<CloudToolCall>) -> Unit = {},
        toolsJson: String? = null,
    ): Triple<String, String, ApiUsage> {
        val retryCfg = RetryConfig()
        val modelName = profile.modelName
        var lastErr: Throwable? = null
        for (attempt in 0 until retryCfg.maxRetries) {
            try {
                return sendMessageStream(profile, systemPrompt, messages, token,
                    onText, onReasoning, onToolCalls, toolsJson)
            } catch (e: Exception) {
                if (token.isCancelled) throw e
                lastErr = e
                if (!shouldRetry(e, attempt, retryCfg)) {
                    FileLogger.e("LLM", "✗ $modelName 放弃重试 after ${attempt + 1}次", e)
                    throw e
                }
                FileLogger.w("LLM", "↻ $modelName 重试 ${attempt + 1}/$retryCfg.maxRetries: ${e.message?.take(120)}")
                delay(calcRetryDelay(attempt, retryCfg))
            }
        }
        throw lastErr ?: RuntimeException("请求失败")
    }

    // ── 非流式（验证/获取模型列表） ──

    suspend fun verifyConnection(profile: CloudModelProfile): Result<String> = withContext(Dispatchers.IO) {
        FileLogger.i("LLM", "verify ${profile.modelName} @ ${profile.apiEndpoint.take(60)}")
        try {
            val body = JSONObject().apply {
                put("model", profile.modelName)
                put("messages", JSONArray().put(JSONObject().apply { put("role", "user"); put("content", "hi") }))
                put("max_tokens", 1); put("stream", false)
            }.toString()
            val req = Request.Builder().url(profile.apiEndpoint.trimEnd('/') + "/chat/completions")
                .post(body.toRequestBody("application/json".toMediaType()))
                .header("Authorization", "Bearer ${profile.apiKey}")
                .header("Accept", "application/json").build()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    FileLogger.i("LLM", "verify ${profile.modelName} OK")
                    Result.success("ok")
                } else {
                    val msg = "HTTP ${resp.code}: ${resp.body?.string()}"
                    FileLogger.e("LLM", "verify ${profile.modelName} FAIL: $msg")
                    Result.failure(Exception(msg))
                }
            }
        } catch (e: Exception) {
            FileLogger.e("LLM", "verify ${profile.modelName} EXCEPTION", e)
            Result.failure(e)
        }
    }

    suspend fun fetchModels(apiEndpoint: String, apiKey: String): Result<List<String>> = withContext(Dispatchers.IO) {
        FileLogger.i("LLM", "fetchModels")
        try {
            val req = Request.Builder().url(apiEndpoint.trimEnd('/') + "/models")
                .header("Authorization", "Bearer $apiKey")
                .header("Accept", "application/json").build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    FileLogger.e("LLM", "fetchModels HTTP ${resp.code}")
                    return@withContext Result.failure(Exception("HTTP ${resp.code}"))
                }
                val data = JSONObject(resp.body?.string() ?: "{}").optJSONArray("data") ?: JSONArray()
                val models = mutableListOf<String>()
                for (i in 0 until data.length()) {
                    data.getJSONObject(i).optString("id", "").takeIf { it.isNotBlank() }?.let { models.add(it) }
                }
                FileLogger.i("LLM", "fetchModels → ${models.size} models")
                Result.success(models)
            }
        } catch (e: Exception) {
            FileLogger.e("LLM", "fetchModels EXCEPTION", e)
            Result.failure(e)
        }
    }

    // ── body 构建 ──

    private fun buildRequestBody(
        profile: CloudModelProfile, systemPrompt: String,
        messages: List<ChatMessage>, toolsJson: String? = null,
    ): String = JSONObject().apply {
        put("model", profile.modelName)
        put("messages", JSONArray().apply {
            if (systemPrompt.isNotBlank()) put(JSONObject().apply { put("role", "system"); put("content", systemPrompt) })
            messages.forEach { msg ->
                val obj = JSONObject()
                when (msg.role) {
                    ChatRole.User -> { obj.put("role", "user"); obj.put("content", msg.content) }
                    ChatRole.Model -> {
                        obj.put("role", "assistant")
                        obj.put("content", if (msg.toolCalls.isNotEmpty() && msg.content.isBlank()) JSONObject.NULL else msg.content)
                        if (msg.toolCalls.isNotEmpty()) {
                            obj.put("tool_calls", JSONArray().apply {
                                msg.toolCalls.forEach { tc ->
                                    put(JSONObject().apply {
                                        put("id", tc.id); put("type", "function")
                                        put("function", JSONObject().apply { put("name", tc.name); put("arguments", try { JSONObject(tc.arguments).toString() } catch (_: Exception) { "{}" }) })
                                    })
                                }
                            })
                        }
                    }
                    ChatRole.Tool -> { obj.put("role", "tool"); obj.put("tool_call_id", msg.toolCallId ?: "call_${msg.id.take(8)}"); obj.put("content", msg.content) }
                    ChatRole.System -> { obj.put("role", "system"); obj.put("content", msg.content) }
                }
                put(obj)
            }
        })
        put("stream", true)
        put("max_tokens", profile.maxTokens)
        put("temperature", 0.7)
        if (!toolsJson.isNullOrBlank()) put("tools", JSONArray(toolsJson))
    }.toString()

    private data class RetryConfig(val maxRetries: Int = 3, val baseDelayMs: Long = 1000, val maxDelayMs: Long = 30000)
    private fun calcRetryDelay(a: Int, c: RetryConfig) = (c.baseDelayMs * Math.pow(2.0, a.toDouble())).toLong().coerceAtMost(c.maxDelayMs) + (Math.random() * 1000).toLong()
    private fun shouldRetry(e: Throwable, a: Int, c: RetryConfig): Boolean {
        if (a >= c.maxRetries) return false
        val m = e.message ?: ""
        return m.contains("timeout", true) || m.contains("connection", true) ||
               m.contains("reset", true) || m.contains("refused", true) ||
               e is java.net.SocketException || e is java.net.SocketTimeoutException || e is java.io.IOException
    }
}
