package com.template.jh.core.analytics

/** 单次 LLM 调用记录 */
data class LlmCallRecord(
    val modelName: String,
    val provider: String,      // "cloud" | "local"
    val promptTokens: Int,
    val completionTokens: Int,
    val durationMs: Long,
    val success: Boolean,
    val errorMessage: String?,
    val timestamp: Long = System.currentTimeMillis(),
)

/** 单次工具调用记录 */
data class ToolCallRecord(
    val toolName: String,
    val success: Boolean,
    val durationMs: Long,
    val timestamp: Long = System.currentTimeMillis(),
)

/** 按模型聚合统计 */
data class ModelUsageStats(
    val calls: Int,
    val promptTokens: Int,
    val completionTokens: Int,
    val durationMs: Long,
)

/** 按工具聚合统计 */
data class ToolUsageStats(
    val calls: Int = 0,
    val failed: Int = 0,
    val totalDurationMs: Long = 0,
) {
    val successRate: Float
        get() = if (calls > 0) (calls - failed).toFloat() / calls else 1f
}

/** 总体用量统计 */
data class UsageStats(
    val totalCalls: Int = 0,
    val totalPromptTokens: Int = 0,
    val totalCompletionTokens: Int = 0,
    val totalDurationMs: Long = 0,
    val todayCalls: Int = 0,
    val todayTokens: Int = 0,
    val lastResetDate: String = "",
    val byModel: Map<String, ModelUsageStats> = emptyMap(),
    val byTool: Map<String, ToolUsageStats> = emptyMap(),
)
