# LiteRT-LM 采样参数详解

`SamplerConfig` 控制模型生成文本时的采样行为，可作用于 `ConversationConfig` 或 `SessionConfig`。

## 参数定义

```kotlin
import com.google.ai.edge.litertlm.SamplerConfig

val samplerConfig = SamplerConfig(
    topK = 10,
    topP = 0.95,
    temperature = 0.8,
    seed = 42,        // 可选，默认 0
)
```

| 参数 | 类型 | 约束 | 说明 |
|------|------|------|------|
| `topK` | `Int` | > 0 | 每一步仅从概率最高的 K 个 token 中采样 |
| `topP` | `Double` | 0.0 ~ 1.0 | 核采样阈值，从累积概率达到 P 的最小 token 集合中采样 |
| `temperature` | `Double` | >= 0.0 | 温度系数，>1 输出更随机，<1 更确定，0 近似贪心解码 |
| `seed` | `Int` | 任意整数 | 随机种子，默认 0，用于复现结果 |

## 使用方式

### 对话级别

```kotlin
val conversationConfig = ConversationConfig(
    samplerConfig = samplerConfig,
)
val conversation = engine.createConversation(conversationConfig)
```

### Session 级别

```kotlin
import com.google.ai.edge.litertlm.SessionConfig

val sessionConfig = SessionConfig(
    samplerConfig = samplerConfig,
)
val session = engine.createSession(sessionConfig)
```

若 `samplerConfig` 为 `null`，则使用引擎默认值。

## 参数调优建议

| 场景 | 推荐配置 | 效果 |
|------|---------|------|
| 创意写作 | `topK=50`, `topP=0.9`, `temperature=1.0` | 输出更多样、有创意 |
| 问答/事实 | `topK=10`, `topP=0.95`, `temperature=0.5` | 输出更确定、准确 |
| 代码生成 | `topK=40`, `topP=0.95`, `temperature=0.2` | 输出更稳定、连贯 |
| 完全确定性 | `topK=1`, `topP=0.0`, `temperature=0.0` | 近似贪心，结果最稳定 |

## 完整示例

```kotlin
val engineConfig = EngineConfig(
    modelPath = "/path/to/model.litertlm",
    backend = Backend.CPU(),
)

Engine(engineConfig).use { engine ->
    engine.initialize()

    val config = ConversationConfig(
        samplerConfig = SamplerConfig(
            topK = 40,
            topP = 0.95,
            temperature = 0.8,
            seed = 0,
        ),
    )

    engine.createConversation(config).use { conversation ->
        println(conversation.sendMessage("讲一个短故事。"))
    }
}
```
