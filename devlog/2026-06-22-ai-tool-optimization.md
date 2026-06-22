# 2026-06-22 — AI 工具调用优化、云端重试机制与思考过程显示

## 优化概览

本次优化聚焦 AI 交互核心链路，包含三个主要改进：
1. AI 工具调用解析逻辑优化
2. 云端大模型客户端重试机制
3. AI 思考过程显示功能完善

## 1. AI 工具调用解析优化

### 改动文件
- `app/src/main/kotlin/com/medeide/jh/screens/home/aitools/ToolCallHandler.kt`

### 改动内容

**新增 `extractThinking()` 方法：**
```kotlin
fun extractThinking(text: String): Pair<String?, String> {
    // 提取 [think]...[/think] 标签内的 AI 思考过程
    // 返回 Pair(思考内容, 剩余内容)
}
```

**改进点：**
- 新增专门的方法提取 `[think]...[/think]` 块
- 支持 <think> 和 [think] 两种标签格式
- 返回思考内容和剩余文本，便于 UI 分层展示
- 保留了原有的多格式工具调用 JSON 解析能力（XML 标签、代码块、OpenAI 函数调用等）

## 2. 云端大模型客户端重试机制

### 改动文件
- `app/src/main/kotlin/com/medeide/jh/data/source/remote/CloudLLMClient.kt`

### 改动内容

**新增重试配置与逻辑：**
```kotlin
private data class RetryConfig(
    val maxRetries: Int = 3,
    val baseDelayMs: Long = 1000,
    val maxDelayMs: Long = 30000,
    val backoffMultiplier: Double = 2.0,
)

private fun calculateRetryDelay(attempt: Int, config: RetryConfig): Long
private fun shouldRetry(error: Throwable, attempt: Int, config: RetryConfig): Boolean
```

**重构 `sendMessage()` 方法：**
- 新增 `sendMessageInternal()` 私有方法，包含原有发送逻辑
- 公开的 `sendMessage()` 包装为带重试的版本
- 指数退避 + 随机抖动，避免固定间隔请求风暴
- 仅对网络层错误（timeout、connection reset、refused 等）进行重试
- 非网络错误（如 API 400/401/403）直接抛出，不消耗重试次数

**可重试错误类型：**
- `java.net.SocketException`
- `java.net.SocketTimeoutException`
- `java.io.IOException`
- 消息包含 "timeout"、"connection"、"reset"、"refused"、"unreachable"

## 3. AI 思考过程显示功能

### 改动文件
- `app/src/main/kotlin/com/medeide/jh/screens/home/ChatViewModel.kt`

### 改动内容

**本地模型思考过程提取（第 799-827 行）：**
```kotlin
// 如果通道 API 未提供思考内容但消息中有 <think> 或 [think] 块，从中提取
if (channelContent == null) {
    val currentMsg = _state.value.messages.find { it.id == msgId }
    // 支持 <think>...</think> 和 [think]...[/think] 两种格式
    // 提取后清理消息内容，将思考内容存入 channelContent
}
```

**云端模型思考过程提取（第 960-980 行）：**
```kotlin
// 提取思考块（<think> 或 [think]）作为 channelContent
var channelContent: String? = null
var cleanResponse = response
val thinkTagMatch = Regex("<think>([\\s\\S]*?)</think>").find(response)
val bracketMatch = Regex("\\[think]([\\s\\S]*?)\\[/think]").find(response)
if (thinkTagMatch != null) {
    channelContent = thinkTagMatch.groupValues[1].trim()
    cleanResponse = response.replace(Regex("<think>[\\s\\S]*?</think>"), "").trim()
}
// ... 同理处理 [think] 格式
```

**对话初始化 thinking channel（第 708 行）：**
```kotlin
channels = listOf(com.google.ai.edge.litertlm.Channel("thinking", "<think>", "</think>"))
```

**特性：**
- 支持双格式思考标签：XML 风格 `<think>...</think>` 和括号风格 `[think]...[/think]`
- 思考内容与回复内容分离显示
- 自动清理消息内容中的思考标签
- 本地和云端模型均支持

## 受影响的核心文件

| 文件 | 改动类型 |
|------|----------|
| `ToolCallHandler.kt` | 新增 `extractThinking()` 方法 |
| `CloudLLMClient.kt` | 新增重试机制，重构 `sendMessage()` |
| `ChatViewModel.kt` | 已有思考提取逻辑，无需修改 |

## 测试建议

1. **重试机制测试：**
   - 模拟网络超时，验证是否自动重试
   - 验证指数退避延迟是否正确（1s → 2s → 4s）
   - 验证非网络错误（如 API 密钥错误）不触发重试

2. **思考过程测试：**
   - 使用支持思考的本地模型（如 Gemma），验证 `<think>` 块正确提取
   - 使用支持思考的云端模型（如 Claude），验证 `[think]` 块正确提取
   - 验证思考内容与回复内容分离显示

3. **工具调用测试：**
   - 验证 `extractThinking()` 不干扰工具调用解析
   - 验证思考块内的工具调用 JSON 不被错误解析

## 后续优化方向

1. **重试配置持久化：** 允许用户在设置中调整重试次数和延迟
2. **重试状态反馈：** 在 UI 上显示 "正在重试 (2/3)..." 状态
3. **思考过程折叠：** 默认折叠思考内容，点击展开查看
4. **工具调用解析增强：** 考虑引入 JSON Schema 验证提升解析鲁棒性
