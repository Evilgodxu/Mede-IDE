LiteRT-LM Android API

1. 最简单的终端聊天示例

```kotlin
import com.google.ai.edge.litertlm.*

suspend fun main() {
  Engine.setNativeMinLogSeverity(LogSeverity.ERROR)

  val engineConfig = EngineConfig(modelPath = "/path/to/model.litertlm")
  Engine(engineConfig).use { engine ->
    engine.initialize()

    engine.createConversation().use { conversation ->
      while (true) {
        print("\n>>> ")
        conversation.sendMessageAsync(readln()).collect { print(it) }
      }
    }
  }
}
```

2. Gradle 依赖

```kotlin
dependencies {
    // Android
    implementation("com.google.ai.edge.litertlm:litertlm-android:latest.release")
    // JVM
    implementation("com.google.ai.edge.litertlm:litertlm-jvm:latest.release")
}
```

3. 初始化引擎

```kotlin
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig

val engineConfig = EngineConfig(
    modelPath = "/path/to/your/model.litertlm",
    backend = Backend.GPU(), // 或 Backend.NPU(nativeLibraryDir = "...")
    cacheDir = "/tmp/" // 可选，可加速二次加载
)

val engine = Engine(engineConfig)
engine.initialize()
// ... 使用引擎 ...
engine.close()
```

4. Android 清单文件权限（GPU 后端）

```xml
<application>
    <uses-native-library android:name="libvndksupport.so" android:required="false"/>
    <uses-native-library android:name="libOpenCL.so" android:required="false"/>
</application>
```

5. NPU 后端配置

```kotlin
val engineConfig = EngineConfig(
    modelPath = modelPath,
    backend = Backend.NPU(nativeLibraryDir = context.applicationInfo.nativeLibraryDir)
)
```

6. 创建会话（带配置）

```kotlin
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig

val conversationConfig = ConversationConfig(
    systemInstruction = Contents.of("You are a helpful assistant."),
    initialMessages = listOf(
        Message.user("What is the capital city of the United States?"),
        Message.model("Washington, D.C."),
    ),
    samplerConfig = SamplerConfig(topK = 10, topP = 0.95, temperature = 0.8),
)

val conversation = engine.createConversation(conversationConfig)
// 或使用默认配置：val conversation = engine.createConversation()

// 使用完毕后关闭
conversation.close()

// 自动关闭的 use 写法
engine.createConversation(conversationConfig).use { conversation ->
    // 交互
}
```

7. 发送消息（同步）

```kotlin
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Message

val response = conversation.sendMessage("What is the capital of France?")
println(response)
```

8. 发送消息（异步回调）

```kotlin
import com.google.ai.edge.litertlm.MessageCallback
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

val callback = object : MessageCallback {
    override fun onMessage(message: Message) { print(message) }
    override fun onDone() { /* 完成 */ }
    override fun onError(throwable: Throwable) { /* 错误处理 */ }
}

conversation.sendMessageAsync("What is the capital of France?", callback)
```

9. 发送消息（异步 Flow，推荐）

```kotlin
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

// 在协程作用域内
conversation.sendMessageAsync("What is the capital of France?")
    .catch { /* 错误处理 */ }
    .collect { print(it.toString()) }
```

10. 启用多 Token 预测（MTP）

```kotlin
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.ExperimentalFlags
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig

@OptIn(ExperimentalApi::class)
ExperimentalFlags.enableSpeculativeDecoding = true

val engineConfig = EngineConfig(
    modelPath = "/path/to/your/model.litertlm",
    backend = Backend.GPU(),
)

val engine = Engine(engineConfig)
engine.initialize()
// 后续创建会话、发送消息与普通流程相同
```

11. 多模态配置与发送

```kotlin
// 配置多模态后端
val engineConfig = EngineConfig(
    modelPath = "/path/to/your/model.litertlm",
    backend = Backend.CPU(),
    visionBackend = Backend.GPU(),
    audioBackend = Backend.CPU(),
)

// 发送多模态消息
conversation.sendMessage(Contents.of(
    Content.ImageFile("/path/to/image"),
    Content.AudioBytes(audioBytes), // ByteArray
    Content.Text("Describe this image and audio."),
))
```

12. 定义工具（Kotlin 函数方式）

```kotlin
import com.google.ai.edge.litertlm.Tool
import com.google.ai.edge.litertlm.ToolParam

class SampleToolSet : ToolSet {
    @Tool(description = "Get the current weather for a city")
    fun getCurrentWeather(
        @ToolParam(description = "The city name, e.g., San Francisco") city: String,
        @ToolParam(description = "Optional country code, e.g., US") country: String? = null,
        @ToolParam(description = "Temperature unit (celsius or fahrenheit). Default: celsius") unit: String = "celsius"
    ): Map<String, Any> {
        return mapOf("temperature" to 25, "unit" to unit, "condition" to "Sunny")
    }

    @Tool(description = "Get the sum of a list of numbers.")
    fun sum(
        @ToolParam(description = "The numbers, could be floating point.") numbers: List<Double>,
    ): Double {
        return numbers.sum()
    }
}
```

13. 定义工具（OpenAPI 规范方式）

```kotlin
import com.google.ai.edge.litertlm.OpenApiTool

class SampleOpenApiTool : OpenApiTool {
    override fun getToolDescriptionJsonString(): String {
        return """
        {
          "name": "addition",
          "description": "Add all numbers.",
          "parameters": {
            "type": "object",
            "properties": {
              "numbers": {
                "type": "array",
                "items": { "type": "number" }
              },
              "description": "The list of numbers to sum."
            },
            "required": ["numbers"]
          }
        }
        """.trimIndent()
    }

    override fun execute(paramsJsonString: String): String {
        // 解析并执行，返回 JSON 字符串
        return """{"result": 1.4142}"""
    }
}
```

14. 注册工具

```kotlin
val conversation = engine.createConversation(
    ConversationConfig(
        tools = listOf(
            tool(SampleToolSet()),
            tool(SampleOpenApiTool()),
        ),
        // ... 其他配置
    )
)

// 触发工具调用
conversation.sendMessageAsync("What's the weather like in London?", callback)
```

15. 手动工具调用（禁用自动调用）

```kotlin
val conversation = engine.createConversation(
    ConversationConfig(
        tools = listOf(tool(SampleOpenApiTool())),
        automaticToolCalling = false,
    )
)

// 发送消息，模型返回带有 toolCalls 的响应
val responseMessage = conversation.sendMessage("What's the weather like in London?")

if (responseMessage.toolCalls.isNotEmpty()) {
    val toolResponses = mutableListOf<Content.ToolResponse>()
    for (toolCall in responseMessage.toolCalls) {
        println("Model wants to call: ${toolCall.name} with arguments: ${toolCall.arguments}")
        val toolResponseJson = executeTool(toolCall.name, toolCall.arguments) // 自定义执行
        toolResponses.add(Content.ToolResponse(toolCall.name, toolResponseJson))
    }
    val toolResponseMessage = Message.tool(Contents.of(toolResponses))
    val finalMessage = conversation.sendMessage(toolResponseMessage)
    println("Final answer: ${finalMessage.text}")
}
```

16. 错误处理

```kotlin
// API 调用需包裹 try-catch，处理 LiteRtLmJniException 或 IllegalStateException
try {
    engine.initialize()
} catch (e: LiteRtLmJniException) {
    // 处理原生错误
} catch (e: IllegalStateException) {
    // 处理生命周期错误
}

// 异步回调中同样有 onError
```

---