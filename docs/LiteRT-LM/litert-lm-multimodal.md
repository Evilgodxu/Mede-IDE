# LiteRT-LM 多模态输入

多模态仅支持具备该能力的模型（如 Gemma3n）。

## 启用多模态后端

在 `EngineConfig` 中指定 `visionBackend` 和/或 `audioBackend`，与主 `backend` 独立：

```kotlin
val engineConfig = EngineConfig(
    modelPath = "/path/to/model.litertlm",
    backend = Backend.CPU(),
    visionBackend = Backend.GPU(),
    audioBackend = Backend.CPU(),
)
```

若 `visionBackend` 或 `audioBackend` 为 `null`，对应执行器不会初始化。

## Content 类型

`Message` 由 `Content` 列表组成，支持以下类型：

| 类型 | 构造方式 | 说明 |
|------|---------|------|
| 文本 | `Content.Text("...")` | 普通文本 |
| 图片文件 | `Content.ImageFile("/abs/path/to/image")` | 通过绝对路径引用图片 |
| 图片字节 | `Content.ImageBytes(byteArray)` | 通过 `ByteArray` 传入图片 |
| 音频文件 | `Content.AudioFile("/abs/path/to/audio")` | 通过绝对路径引用音频 |
| 音频字节 | `Content.AudioBytes(byteArray)` | 通过 `ByteArray` 传入音频 |

## 发送多模态消息

使用 `Contents.of(...)` 组合多种内容：

```kotlin
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents

val imagePath = "/path/to/image.jpg"
val audioBytes: ByteArray = ...

conversation.sendMessage(Contents.of(
    Content.ImageFile(imagePath),
    Content.AudioBytes(audioBytes),
    Content.Text("描述这张图片和这段音频。"),
))
```

## 注意事项

- 图片/音频路径必须为**绝对路径**。
- `Content.ImageBytes` / `Content.AudioBytes` 在内部使用 Base64 编码传输，大文件建议优先使用文件路径版本以减少内存拷贝。
- 发送前确认模型支持对应模态，否则行为未定义。
