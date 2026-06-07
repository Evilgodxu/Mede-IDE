# Android AI IDE

一款运行在 Android 平台上的 AI 辅助代码编辑器，采用 Jetpack Compose + Material 3 构建，集成本地大模型推理与云端 LLM API，具备完整的 IDE 基础功能。

## 功能特性

- **三栏 IDE 布局** — 侧边栏 + 文件管理 + 代码编辑器 + AI 协作面板
- **本地 AI 推理** — 基于 LiteRT-LM（Google AI Edge）运行本地大模型
- **云端 LLM 支持** — 兼容 OpenAI API 格式，支持 SSE 流式响应
- **代码编辑器** — 行号、语法高亮、差异补丁审阅（create/modify/delete）
- **文件管理** — 基于 SAF（Storage Access Framework）浏览/编辑项目文件
- **AI 工具调用** — 自动读写文件、执行终端命令、Web 搜索、Git 操作
- **Git 集成** — status / add / commit / push / branch / diff
- **MCP 服务器** — 支持扩展 AI 能力的 MCP 协议
- **对话管理** — 多轮对话历史、任务清单、文件变更追踪
- **设置面板** — 主题切换（浅色/深色/跟随系统）、语言（中文/英文）、模型管理、自定义规则

## 技术栈

| 类别 | 技术 |
|------|------|
| 语言 | Kotlin 2.4 |
| UI | Jetpack Compose + Material 3（BOM 2026.05.01） |
| 架构 | MVVM + UDF（单向数据流） |
| 依赖注入 | Koin 4.2.1 |
| 本地 AI | LiteRT-LM 0.13.0（Google AI Edge） |
| 云端 AI | OkHttp 4.12 + OpenAI 兼容 API（SSE 流式） |
| 状态管理 | DataStore 1.2 + StateFlow |
| 异步处理 | Kotlin Coroutines |
| 文件访问 | SAF（Storage Access Framework） |
| 自适应布局 | Material 3 Adaptive 1.2.0 |
| 序列化 | Gson 2.11 |

## 环境要求

- Android SDK：minSdk 32 / targetSdk 37 / compileSdk 37
- JDK 21
- Kotlin 2.4.0
- AGP 9.2.1
- Gradle 9.5.1
- 支持 arm64-v8a 架构

## 项目结构

```
app/src/main/kotlin/com/template/jh/
├── MainActivity.kt                          # 主 Activity
├── MyApplication.kt                         # Application 入口（Koin 初始化）
├── core/
│   ├── ai/                                  # AI 引擎
│   │   ├── AIToolSet.kt                     # AI 工具集（文件/Git/终端/搜索）
│   │   ├── ChatViewModel.kt                 # 聊天 ViewModel（对话循环）
│   │   ├── ChatUiState.kt                   # 聊天 UI 状态
│   │   ├── CloudLLMClient.kt                # 云端 LLM 客户端
│   │   ├── ConversationNotifier.kt          # 对话通知
│   │   ├── ConversationRepository.kt        # 对话持久化
│   │   ├── FileOperationEvents.kt           # 文件操作事件
│   │   └── LiteRTManager.kt                 # 本地模型管理器
│   ├── editor/
│   │   ├── DiffUtils.kt                     # 差异补丁引擎
│   │   └── SyntaxHighlighter.kt             # 语法高亮
│   └── utils/
│       └── localization/
│           └── LanguageManager.kt           # 多语言管理
├── data/
│   ├── model/
│   │   ├── McpServer.kt                     # MCP 服务器配置
│   │   ├── Rule.kt                          # 用户规则
│   │   ├── Skill.kt                         # AI 技能
│   │   └── TaskFlow.kt                      # 任务流/通知设置
│   └── repository/
│       └── UserPreferencesRepository.kt     # 用户偏好持久化
├── di/
│   └── AppModule.kt                         # Koin 依赖注入模块
├── screens/
│   └── home/
│       ├── components/                      # UI 组件
│       │   ├── AIChatPanel.kt               # AI 对话面板
│       │   ├── CodeEditor.kt                # 代码编辑器（带行号/高亮）
│       │   ├── MainContentArea.kt           # 中间主内容区
│       │   ├── MainTopBar.kt                # 顶部菜单栏
│       │   ├── ResourcePanel.kt             # 文件资源管理器
│       │   ├── SearchPanel.kt               # 搜索面板
│       │   ├── SettingsPane.kt              # 设置面板
│       │   ├── Sidebar.kt                   # 侧边栏
│       │   ├── TerminalPanel.kt             # 终端面板
│       │   └── ThreeColumnLayout.kt         # 三栏布局
│       ├── HomeScreen.kt                    # 主屏幕
│       ├── HomeViewModel.kt                 # 主屏幕 ViewModel
│       ├── HomeUiState.kt                   # 主屏幕 UI 状态
│       ├── FileItem.kt                      # 文件条目模型
│       └── TabItem.kt                       # 编辑器 Tab 模型
└── ui/
    ├── adaptive/
    │   └── WindowSizeClass.kt               # 窗口尺寸分类
    └── theme/
        ├── Color.kt                         # 主题色
        ├── Theme.kt                         # Material 3 主题
        └── Type.kt                          # 排版
```

## AI 工具集

编辑器内置的 AI 代理可以通过工具调用执行以下操作：

| 工具 | 说明 |
|------|------|
| `listFiles` | 列出项目文件树 |
| `readFile` | 带行号读取文件内容 |
| `writeFile` | 创建或覆盖文件 |
| `applyPatch` | 行级差异编辑（replace/insert/delete） |
| `runCommand` | 执行 shell 命令（30s 超时） |
| `searchWeb` | 搜索互联网（DuckDuckGo） |
| `gitStatus / gitAdd / gitCommit / gitPush / gitBranch / gitDiff` | 完整 Git 操作 |
| `readLints` | 读取 Lint 错误 |

## 构建配置

### 签名

发布版使用 `jh.keystore` 签名，密钥信息配置在 `local.properties`：

```properties
KEYSTORE_PASSWORD=your_password
KEY_ALIAS=jh
KEY_PASSWORD=your_password
```

### 构建命令

```bash
# 构建发布版 APK
./gradlew assembleRelease

# 构建发布版 AAB
./gradlew bundleRelease
```

### 构建类型

| 类型 | 特性 |
|------|------|
| **Release** | 代码混淆、资源压缩、PNG 优化、签名打包 |
| **Debug** | 启用 ProGuard 规则（关闭调试标志） |

## 开始使用

1. 克隆仓库

```bash
git clone https://github.com/Evilgodxu/Android-AI-IDE.git
```

2. 使用 Android Studio 打开项目

3. 同步 Gradle 后直接运行

4. （可选）加载 AI 模型：
   - 在设置 → 模型中扫描设备上的 `.gguf` 模型文件
   - 或从文件浏览器加载模型

5. （可选）配置云端模型：
   - 在设置 → 模型中启用云端模型
   - 填入 API Endpoint 和 API Key

## 许可证

GNU Affero General Public License v3.0 — 详见 [LICENSE](LICENSE)
