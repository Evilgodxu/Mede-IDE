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
- **对话管理** — 多轮对话历史、文件变更追踪
- **上下文记忆** — 语义搜索对话历史、最近对话摘要
- **设置面板** — 主题切换（浅色/深色/跟随系统）、语言（中文/英文）、模型管理、自定义规则
- **多媒体预览** — 图片、视频、音频播放（含歌词显示）
- **压缩/解压** — 支持带密码的 ZIP 压缩解压

## 技术栈

| 类别 | 技术 |
|------|------|
| 语言 | Kotlin 2.4.0 |
| UI | Jetpack Compose + Material 3（BOM 2026.05.01） |
| 架构 | MVVM + UDF（单向数据流） |
| 依赖注入 | Koin 4.2.1 |
| 本地 AI | LiteRT-LM 0.13.1（Google AI Edge） |
| 云端 AI | OkHttp 5.4.0 + OpenAI 兼容 API（SSE 流式） |
| 状态管理 | DataStore 1.2.1 + StateFlow |
| 异步处理 | Kotlin Coroutines |
| 文件访问 | SAF（Storage Access Framework） |
| 自适应布局 | Material 3 Adaptive 1.2.0 |
| 序列化 | Gson 2.14.0 |
| 音频播放 | Media3 ExoPlayer 1.6.1 |
| 压缩解压 | Zip4j 2.11.5 |
| Markdown | CommonMark 0.21.0 |

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
│   │   ├── FileOperationEvents.kt           # 文件操作事件
│   │   ├── InputOptimizer.kt                # 输入优化器
│   │   └── ToolCallHandler.kt               # 工具调用处理器
│   ├── editor/
│   │   ├── CodeEditTool.kt                  # 代码编辑工具
│   │   ├── DiffUtils.kt                     # 差异补丁引擎
│   │   ├── EditorSyntaxHighlight.kt         # 编辑器语法高亮
│   │   ├── SyntaxHighlighter.kt             # 语法高亮
│   │   └── TextEditTool.kt                  # 文本编辑工具
│   ├── memory/
│   │   ├── ConversationMemory.kt            # 对话记忆管理
│   │   ├── ContextManager.kt                # 上下文管理器
│   │   └── MemoryVisualizer.kt              # 记忆可视化
│   ├── storage/
│   │   └── FileManager.kt                   # 文件管理器
│   └── utils/
│       ├── localization/
│       │   └── LanguageManager.kt           # 多语言管理
│       ├── CpuFeatureDetector.kt            # CPU 特性检测
│       ├── FileLogger.kt                    # 文件日志
│       ├── ImageProcessor.kt                # 图片处理
│       └── LogCollector.kt                  # 日志收集
├── data/
│   ├── model/
│   │   ├── McpServer.kt                     # MCP 服务器配置
│   │   ├── Rule.kt                          # 用户规则
│   │   ├── Skill.kt                         # AI 技能
│   │   └── TabItem.kt                       # Tab 模型
│   ├── repository/
│   │   ├── ConversationRepository.kt        # 对话持久化
│   │   ├── UsageAnalyticsRepository.kt      # 使用分析
│   │   └── UserPreferencesRepository.kt     # 用户偏好持久化
│   └── source/
│       ├── local/
│       │   └── LiteRTManager.kt             # 本地模型管理器
│       └── remote/
│           └── CloudLLMClient.kt            # 云端 LLM 客户端
├── di/
│   └── AppModule.kt                         # Koin 依赖注入模块
├── model/
│   ├── chat/
│   │   └── ChatModels.kt                    # 聊天数据模型
│   ├── FileItem.kt                          # 文件条目模型
│   ├── Rule.kt                              # 规则模型
│   ├── Skill.kt                             # 技能模型
│   └── McpServer.kt                         # MCP 服务器模型
├── screens/
│   └── home/
│       ├── components/                      # UI 组件
│       │   ├── chat/
│       │   │   ├── AIChatPanel.kt           # AI 对话面板
│       │   │   ├── ContextDashboard.kt      # 上下文仪表板
│       │   │   └── ConversationDisplay.kt   # 对话显示
│       │   ├── editor/
│       │   │   ├── CodeEditor.kt            # 代码编辑器
│       │   │   ├── EditorModes.kt           # 编辑器模式
│       │   │   └── EditorTextOps.kt         # 编辑器文本操作
│       │   ├── resourcepanel/
│       │   │   ├── CompressDialog.kt        # 压缩对话框
│       │   │   ├── FileInfoDialog.kt        # 文件信息对话框
│       │   │   ├── FileTreeIcon.kt          # 文件树图标
│       │   │   ├── FlatTreeItem.kt          # 扁平树项
│       │   │   ├── FlatTreeStateHolder.kt   # 树状态持有者
│       │   │   ├── ResourceNode.kt          # 资源节点
│       │   │   ├── ResourcePanel.kt         # 资源面板
│       │   │   ├── TreeContextMenu.kt       # 树上下文菜单
│       │   │   └── TreeDialogs.kt           # 树对话框
│       │   ├── search/
│       │   │   ├── SearchBar.kt             # 搜索栏
│       │   │   └── SearchPanel.kt           # 搜索面板
│       │   ├── settings/
│       │   │   ├── LanguageSettingsCard.kt  # 语言设置卡片
│       │   │   ├── SettingsPane.kt          # 设置面板
│       │   │   └── ThemeSettingsCard.kt     # 主题设置卡片
│       │   ├── preview/
│       │   │   ├── ImagePreview.kt          # 图片预览
│       │   │   └── PreviewPanel.kt          # 预览面板
│       │   ├── viewer/
│       │   │   ├── ArchiveViewer.kt         # 压缩文件查看器
│       │   │   ├── VideoPlayer.kt           # 视频播放器
│       │   │   └── WebPreview.kt            # Web 预览
│       │   ├── audio/
│       │   │   ├── AudioControl.kt          # 音频控制
│       │   │   ├── AudioPlaybackState.kt    # 音频播放状态
│       │   │   ├── AudioPlayer.kt           # 音频播放器
│       │   │   ├── LyricsDisplay.kt         # 歌词显示
│       │   │   └── LyricsParser.kt          # 歌词解析
│       │   ├── MainContentArea.kt           # 主内容区
│       │   ├── MainTopBar.kt                # 顶部菜单栏
│       │   ├── ModelSelector.kt             # 模型选择器
│       │   ├── Sidebar.kt                   # 侧边栏
│       │   └── ThreeColumnLayout.kt         # 三栏布局
│       ├── logic/
│       │   ├── EditorScreenState.kt         # 编辑器屏幕状态
│       │   └── utils/
│       │       └── FileTypeUtil.kt          # 文件类型工具
│       ├── HomeScreen.kt                    # 主屏幕
│       ├── HomeViewModel.kt                 # 主屏幕 ViewModel
│       └── HomeUiState.kt                   # 主屏幕 UI 状态
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
| `listFiles` | 列出目录内容，显示[FILE]/[DIR]前缀和文件大小 |
| `readFile` | 读取文件内容，支持分页（offset/limit） |
| `writeFile` | 创建新文件，支持覆盖选项 |
| `replaceInFile` | 精确替换代码块，支持行范围限定 |
| `batchReplaceInFile` | 批量编辑，一次替换多处不重叠代码 |
| `deleteFile` | 删除文件或目录 |
| `createDirectory` | 创建目录，自动创建父目录 |
| `grep` | 正则搜索文件内容，返回匹配文件、行号和上下文 |
| `glob` | 按文件名 glob 模式搜索 |
| `searchCodebase` | 语义搜索代码库，按含义查找相关代码 |
| `runCommand` | 执行 shell 命令（30s 超时，5000 字符上限） |
| `searchWeb` | 联网搜索（DuckDuckGo） |
| `readLints` | 读取构建/lint/编译错误 |
| `searchConversationMemory` | 语义搜索对话历史 |
| `getRecentConversationMemory` | 获取最近对话摘要 |

## 工具调用策略

本项目采用 **"官方自动工具调用 + AIToolSet 回调打破黑盒"** 的混合策略：

- `automaticToolCalling = true` 确保工具调用闭环由 Framework 管理
- `ToolExecutionCallback` 回调提供实时 UI 状态更新和副作用扩展
- 工具调用 JSON 走元数据通道，零污染对话上下文
- 支持 LiteRT 本地模型和 Cloud LLM 双后端

详见 [docs/tool-calling-strategy.md](docs/tool-calling-strategy.md)

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
./gradlew clean app:assembleRelease

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
   - 在设置 → 模型中加载本地模型文件

5. （可选）配置云端模型：
   - 在设置 → 模型中启用云端模型
   - 填入 API Endpoint 和 API Key

## 许可证

GNU Affero General Public License v3.0 — 详见 [LICENSE](LICENSE)
