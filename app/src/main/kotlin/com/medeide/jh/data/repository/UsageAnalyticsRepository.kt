package com.medeide.jh.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.medeide.jh.core.analytics.LlmCallRecord
import com.medeide.jh.core.analytics.ModelUsageStats
import com.medeide.jh.core.analytics.ToolCallRecord
import com.medeide.jh.core.analytics.ToolUsageStats
import com.medeide.jh.core.analytics.UsageStats
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Context.usageStore: DataStore<Preferences> by preferencesDataStore(name = "usage_analytics")

/** 用量统计持久化仓库 */
class UsageAnalyticsRepository(private val context: Context) {
    private object Keys {
        val STATS_JSON = stringPreferencesKey("usage_stats_json")
    }

    val stats: Flow<UsageStats> = context.usageStore.data.map { prefs ->
        val json = prefs[Keys.STATS_JSON]
        if (json != null) parseStats(json) else UsageStats()
    }

    /** 记录一次 LLM 调用 */
    suspend fun recordCall(call: LlmCallRecord) {
        context.usageStore.edit { prefs ->
            val current = prefs[Keys.STATS_JSON]?.let { parseStats(it) } ?: UsageStats()
            val today = todayDate()
            val resetToday = current.lastResetDate != today
            val updated = aggregate(current, call, resetToday)
            prefs[Keys.STATS_JSON] = serializeStats(updated)
        }
    }

    /** 记录一次工具调用 */
    suspend fun recordToolCall(call: ToolCallRecord) {
        context.usageStore.edit { prefs ->
            val current = prefs[Keys.STATS_JSON]?.let { parseStats(it) } ?: UsageStats()
            val updated = aggregateToolCall(current, call)
            prefs[Keys.STATS_JSON] = serializeStats(updated)
        }
    }

    /** 重置所有统计 */
    suspend fun resetStats() {
        context.usageStore.edit { it[Keys.STATS_JSON] = serializeStats(UsageStats()) }
    }

    // ===== LLM 调用聚合 =====

    private fun aggregate(current: UsageStats, call: LlmCallRecord, resetToday: Boolean): UsageStats {
        val todayDate = todayDate()
        val todayCalls = if (resetToday) 1 else current.todayCalls + 1
        val todayTokens = if (resetToday) call.promptTokens + call.completionTokens
            else current.todayTokens + call.promptTokens + call.completionTokens

        val modelKey = "${call.provider}/${call.modelName}"
        val existingModel = current.byModel[modelKey] ?: ModelUsageStats(0, 0, 0, 0)
        val byModel = current.byModel + (modelKey to ModelUsageStats(
            calls = existingModel.calls + 1,
            promptTokens = existingModel.promptTokens + call.promptTokens,
            completionTokens = existingModel.completionTokens + call.completionTokens,
            durationMs = existingModel.durationMs + call.durationMs,
        ))

        return current.copy(
            totalCalls = current.totalCalls + 1,
            totalPromptTokens = current.totalPromptTokens + call.promptTokens,
            totalCompletionTokens = current.totalCompletionTokens + call.completionTokens,
            totalDurationMs = current.totalDurationMs + call.durationMs,
            todayCalls = todayCalls,
            todayTokens = todayTokens,
            lastResetDate = todayDate,
            byModel = byModel,
        )
    }

    // ===== 工具调用聚合 =====

    private fun aggregateToolCall(current: UsageStats, call: ToolCallRecord): UsageStats {
        val existing = current.byTool[call.toolName] ?: ToolUsageStats()
        val byTool = current.byTool + (call.toolName to ToolUsageStats(
            calls = existing.calls + 1,
            failed = existing.failed + if (call.success) 0 else 1,
            totalDurationMs = existing.totalDurationMs + call.durationMs,
        ))
        return current.copy(byTool = byTool)
    }

    // ===== 序列化 =====

    private fun serializeStats(stats: UsageStats): String = JSONObject().apply {
        put("totalCalls", stats.totalCalls)
        put("totalPromptTokens", stats.totalPromptTokens)
        put("totalCompletionTokens", stats.totalCompletionTokens)
        put("totalDurationMs", stats.totalDurationMs)
        put("todayCalls", stats.todayCalls)
        put("todayTokens", stats.todayTokens)
        put("lastResetDate", stats.lastResetDate)
        put("byModel", JSONObject(stats.byModel.mapValues { (_, v) ->
            JSONObject().apply {
                put("calls", v.calls)
                put("promptTokens", v.promptTokens)
                put("completionTokens", v.completionTokens)
                put("durationMs", v.durationMs)
            }
        }))
        put("byTool", JSONObject(stats.byTool.mapValues { (_, v) ->
            JSONObject().apply {
                put("calls", v.calls)
                put("failed", v.failed)
                put("totalDurationMs", v.totalDurationMs)
            }
        }))
    }.toString()

    private fun parseStats(json: String): UsageStats {
        return try {
            val obj = JSONObject(json)
            val byModelRaw = obj.optJSONObject("byModel") ?: JSONObject()
            val byModel = byModelRaw.keys().asSequence().associate { key ->
                val m = byModelRaw.getJSONObject(key)
                key to ModelUsageStats(
                    calls = m.optInt("calls", 0),
                    promptTokens = m.optInt("promptTokens", 0),
                    completionTokens = m.optInt("completionTokens", 0),
                    durationMs = m.optLong("durationMs", 0),
                )
            }
            val byToolRaw = obj.optJSONObject("byTool") ?: JSONObject()
            val byTool = byToolRaw.keys().asSequence().associate { key ->
                val t = byToolRaw.getJSONObject(key)
                key to ToolUsageStats(
                    calls = t.optInt("calls", 0),
                    failed = t.optInt("failed", 0),
                    totalDurationMs = t.optLong("totalDurationMs", 0),
                )
            }
            UsageStats(
                totalCalls = obj.optInt("totalCalls", 0),
                totalPromptTokens = obj.optInt("totalPromptTokens", 0),
                totalCompletionTokens = obj.optInt("totalCompletionTokens", 0),
                totalDurationMs = obj.optLong("totalDurationMs", 0),
                todayCalls = obj.optInt("todayCalls", 0),
                todayTokens = obj.optInt("todayTokens", 0),
                lastResetDate = obj.optString("lastResetDate", ""),
                byModel = byModel,
                byTool = byTool,
            )
        } catch (_: Exception) { UsageStats() }
    }

    private fun todayDate(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }
}
