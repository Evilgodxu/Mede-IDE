# LiteRT-LM Android 工具使用全流程

## 1. 添加依赖

```kotlin
dependencies {
    implementation("com.google.ai.edge.litertlm:litertlm-android:latest.release")
}
```

## 2. 定义工具

### 方式 A：Kotlin 函数（推荐）

- 类实现 `ToolSet`
- 方法标记 `@Tool`，参数标记 `@ToolParam`
- 支持参数类型：`String`, `Int`, `Boolean`, `Float`, `Double`, `List<T>`（T 为前述类型）
- 可空类型表示可选参数，默认值也需配合可空类型

```kotlin
import com.google.ai.edge.litertlm.Tool
import com.google.ai.edge.litertlm.ToolParam
import com.google.ai.edge.litertlm.ToolSet

class WeatherToolSet : ToolSet {
    @Tool(description = "获取指定城市的当前天气")
    fun getCurrentWeather(
        @ToolParam(description = "城市名称，如：北京") city: String,
        @ToolParam(description = "温度单位，默认摄氏度") unit: String = "celsius"
    ): Map<String, Any> {
        return mapOf("temperature" to 25, "unit" to unit, "condition" to "Sunny")
    }
}
```

### 方式 B：OpenAPI 规范

- 实现 `OpenApiTool`
- 自行提供 JSON 描述与执行逻辑

```kotlin
import com.google.ai.edge.litertlm.OpenApiTool

class AdditionTool : OpenApiTool {
    override fun getToolDescriptionJsonString(): String = """
        {
          "name": "addition",
          "description": "对一组数字求和",
          "parameters": {
            "type": "object",
            "properties": {
              "numbers": {
                "type": "array",
                "items": { "type": "number" },
                "description": "待求和的数字列表"
              }
            },
            "required": ["numbers"]
          }
        }
    """.trimIndent()

    override fun execute(paramsJsonString: String): String {
        // 自行解析参数、执行逻辑、返回 JSON 字符串结果
        return """{"result": 42.0}"""
    }
}
```

## 3. 注册工具并创建对话

```kotlin
import com.google.ai.edge.litertlm.*

val engineConfig = EngineConfig(
    modelPath = "/path/to/model.litertlm",
    backend = Backend.CPU()
)
Engine(engineConfig).use { engine ->
    engine.initialize()

    val conversationConfig = ConversationConfig(
        systemInstruction = Contents.of("你是一个 helpful assistant。"),
        tools = listOf(
            tool(WeatherToolSet()),
            tool(AdditionTool())
        ),
        automaticToolCalling = true  // 默认 true，自动执行工具
    )

    engine.createConversation(conversationConfig).use { conversation ->
        // 发送消息
        val response = conversation.sendMessage("北京今天天气怎么样？")
        println(response)
    }
}
```

## 4. 自动工具调用（默认）

`automaticToolCalling = true` 时：

1. 用户发送消息给模型
2. 模型输出工具调用请求
3. LiteRT-LM 自动解析并执行对应工具
4. 工具结果自动回传给模型
5. 模型生成最终自然语言回复

无需额外代码处理工具执行与结果回传。

## 5. 手动工具调用

若需自行控制工具执行（如需要用户确认、网络请求、权限检查）：

```kotlin
val conversationConfig = ConversationConfig(
    tools = listOf(tool(WeatherToolSet())),
    automaticToolCalling = false
)

engine.createConversation(conversationConfig).use { conversation ->
    val responseMessage = conversation.sendMessage("北京今天天气怎么样？")

    if (responseMessage.toolCalls.isNotEmpty()) {
        val toolResponses = mutableListOf<Content.ToolResponse>()

        for (toolCall in responseMessage.toolCalls) {
            // 自定义执行逻辑
            val resultJson = myExecuteTool(toolCall.name, toolCall.arguments)
            toolResponses.add(Content.ToolResponse(toolCall.name, resultJson))
        }

        // 将工具结果回传模型
        val toolResponseMessage = Message.tool(Contents.of(toolResponses))
        val finalMessage = conversation.sendMessage(toolResponseMessage)
        println(finalMessage)
    }
}
```

## 6. 流式调用与工具

工具调用与流式输出兼容：

```kotlin
conversation.sendMessageAsync("北京今天天气怎么样？")
    .collect { chunk -> print(chunk.toString()) }
```

- 文本内容按流式逐块输出
- 工具调用在完整解析后一次性通过回调/Flow 发出

## 7. 完整示例

```kotlin
import com.google.ai.edge.litertlm.*
import kotlinx.coroutines.flow.collect

class CalcToolSet : ToolSet {
    @Tool(description = "计算一组数字的乘积")
    fun product(
        @ToolParam(description = "数字列表") numbers: List<Double>
    ): Double = numbers.fold(1.0) { acc, n -> acc * n }
}

suspend fun runChat(modelPath: String) {
    Engine.setNativeMinLogSeverity(LogSeverity.ERROR)

    val engineConfig = EngineConfig(modelPath = modelPath, backend = Backend.CPU())
    Engine(engineConfig).use { engine ->
        engine.initialize()

        val config = ConversationConfig(
            systemInstruction = Contents.of("你可以调用工具进行计算。"),
            tools = listOf(tool(CalcToolSet()))
        )

        engine.createConversation(config).use { conversation ->
            while (true) {
                print("\n>>> ")
                val input = readln()
                if (input.isBlank()) break

                conversation.sendMessageAsync(input).collect { print(it) }
            }
        }
    }
}
```

## 关键类速查

| 类/接口 | 作用 |
|---------|------|
| `ToolSet` | 标记一个 Kotlin 类为工具集合 |
| `@Tool` | 标记方法为可调用的工具 |
| `@ToolParam` | 为工具参数添加描述 |
| `OpenApiTool` | 通过 OpenAPI JSON 手动定义工具 |
| `tool(ToolSet)` / `tool(OpenApiTool)` | 将工具转换为 `ToolProvider` |
| `ConversationConfig.tools` | 注册工具列表 |
| `ConversationConfig.automaticToolCalling` | 是否自动执行工具 |
| `Message.toolCalls` | 模型返回的工具调用请求 |
| `Content.ToolResponse` | 封装工具执行结果 |
| `Message.tool()` | 构造工具结果消息回传模型 |

## 限制

- 仅支持具备工具调用能力的模型（如 FunctionGemma）
- 参数类型仅限 `String`, `Int`, `Boolean`, `Float`, `Double`, `List<T>`
- `Engine.initialize()` 可能耗时数秒，需在后台线程/协程执行
