# LiteRT-LM Channels 与思考模式

Channels 用于将模型输出中特定片段（如思考过程）独立提取，与主回复分离。

## 定义 Channel

`Channel` 通过起始/结束标记界定内容区间：

```kotlin
import com.google.ai.edge.litertlm.Channel

val thinkingChannel = Channel(
    channelName = "thinking",
    start = "<thinking>",
    end = "</thinking>",
)
```

- `channelName`：提取后写入 `Message.channels` 的键名。
- `start` / `end`：模型输出中标识该 channel 开始与结束的字符串。

## 配置 Conversation

在 `ConversationConfig` 中传入 channels 列表：

```kotlin
import com.google.ai.edge.litertlm.ConversationConfig

val config = ConversationConfig(
    channels = listOf(thinkingChannel),
)

val conversation = engine.createConversation(config)
```

- `channels = null`：使用模型元数据中的默认 channel 配置。
- `channels = emptyList()`：禁用 channel 功能。

## 读取 Channel 内容

模型返回的 `Message.channels` 为 `Map<String, String>`，键为 `channelName`，值为提取出的内容：

```kotlin
val response = conversation.sendMessage("解这道数学题。")

// 主回复（已剔除 channel 标记区间）
println(response)

// 提取的思考内容
val thinking = response.channels["thinking"]
if (thinking != null) {
    println("思考过程: $thinking")
}
```

## 完整示例

```kotlin
val engineConfig = EngineConfig(
    modelPath = "/path/to/model.litertlm",
    backend = Backend.CPU(),
)

Engine(engineConfig).use { engine ->
    engine.initialize()

    val conversationConfig = ConversationConfig(
        channels = listOf(
            Channel("thinking", "<thinking>", "</thinking>")
        ),
    )

    engine.createConversation(conversationConfig).use { conversation ->
        val response = conversation.sendMessage("请解释量子力学。")
        println("回复: $response")
        println("思考: ${response.channels["thinking"]}")
    }
}
```
