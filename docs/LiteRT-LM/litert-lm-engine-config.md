# LiteRT-LM Engine 配置与后端选择

## 创建 Engine

`Engine` 是 API 入口，通过 `EngineConfig` 配置模型路径与后端。

```kotlin
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig

val engineConfig = EngineConfig(
    modelPath = "/path/to/model.litertlm",
    backend = Backend.CPU(),
    visionBackend = Backend.GPU(),
    audioBackend = Backend.CPU(),
    cacheDir = "/tmp/cache",       // 可选，可加速第二次加载
    maxNumTokens = 4096,           // 可选，最大输入+输出 token 总数
    maxNumImages = 4,              // 可选，最大处理图片数
)

val engine = Engine(engineConfig)
engine.initialize()
// ...
engine.close()
```

**注意**：`initialize()` 可能耗时数秒，必须在后台线程/协程中调用，避免阻塞 UI。

## 后端类型

`Backend` 为密封类，三选一：

| 后端 | 构造方式 | 说明 |
|------|---------|------|
| CPU | `Backend.CPU(threadCount = 4)` | `threadCount` 为线程数，`null` 或 `0` 使用引擎默认值 |
| GPU | `Backend.GPU()` | Android 需在 `AndroidManifest.xml` 中声明原生库依赖 |
| NPU | `Backend.NPU(nativeLibraryDir = "...")` | 需指定 NPU 库所在目录 |

### CPU 线程数

使用 `threadCount`，**废弃属性 `numOfThreads` 不应再使用**。

```kotlin
Backend.CPU(threadCount = 4)
```

### GPU（Android）

在 `AndroidManifest.xml` 的 `<application>` 内添加：

```xml
<uses-native-library android:name="libvndksupport.so" android:required="false"/>
<uses-native-library android:name="libOpenCL.so" android:required="false"/>
```

### NPU（Android）

若 NPU 库随应用打包，指定应用原生库目录：

```kotlin
Backend.NPU(nativeLibraryDir = context.applicationInfo.nativeLibraryDir)
```

## 缓存目录

`cacheDir` 用于存放缓存文件，需有写入权限：

- 未设置：默认使用模型所在目录
- `""`：使用当前目录（与未设置行为类似，视平台而定）
- `":nocache"`：完全禁用缓存

## 资源管理

`Engine` 实现 `AutoCloseable`，推荐用 `use` 块确保释放：

```kotlin
Engine(engineConfig).use { engine ->
    engine.initialize()
    // 创建 Conversation 或 Session
}
```
