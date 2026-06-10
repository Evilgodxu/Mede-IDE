# Component Index

> 组件名称与文件定位，帮助开发者快速找到需要修改的内容。

---

## 主屏幕入口层

| 文件名 | 导出声明 | 职责 |
|--------|----------|------|
| `HomeScreen.kt` | `HomeScreen()` | 主屏幕组合入口，协调侧边栏/面板/编辑器/AI |
| `HomeViewModel.kt` | `HomeViewModel` | 文件管理、搜索、替换、Tab 持久化 |
| `HomeUiState.kt` | `HomeUiState` | 主屏幕 UI 状态（主题、语言、路径等） |
| `TabItem.kt` | `TabItem`, `TabType`, `displayNameFromPath()` | 编辑器 Tab 模型和类型枚举 |
| `FileItem.kt` | `FileItem` | 文件树节点数据模型 |
| `FileTypeUtil.kt` | `FileTypeUtil` (object) | 文件扩展名分类工具（文本/图片/音频/视频/压缩） |
| `EditorScreenState.kt` | `EditorScreenState`, `rememberEditorScreenState()` | 编辑器状态管理（Tab 增删、内容读写、修改追踪） |

---

## 布局组件

| 文件 | 导出声明 | 职责 |
|------|----------|------|
| `ThreeColumnLayout.kt` | `ThreeColumnLayout()` | 三列布局容器（侧边栏 + 左面板 + 中心 + 右面板） |
| `Sidebar.kt` | `SidebarTab`, `Sidebar()` | 左侧 icon 按钮栏（资源管理器/搜索/Web 预览） |

---

## 侧边栏面板

| 文件 | 导出声明 | 职责 |
|------|----------|------|
| `ResourcePanel.kt` | `ResourcePanel()` | 文件资源管理器面板（树形文件浏览） |
| `ResourceNode.kt` | `ResourceNode`, `FileItemNode` | 资源管理器节点数据模型 |
| `FlatTreeItem.kt` | `FlatTreeItem()` | 扁平化树节点渲染 |
| `FlatTreeStateHolder.kt` | `rememberFlatTreeState()` | 扁平树的展开/收起状态管理 |
| `FileTreeIcon.kt` | `FileTreeIcon` (object) | 文件类型 → 图标/颜色映射 |
| `TreeContextMenu.kt` | `TreeContextMenu()` | 文件树右键菜单（重命名/删除/复制路径） |
| `TreeDialogs.kt` | `TreeDialogs()` | 新建文件/文件夹/重命名对话框 |
| `SearchPanel.kt` | `SearchPanel()` | 全局搜索/替换面板 |
| `PreviewPanel.kt` | `PreviewPanel()` | Web 文件预览面板（扫描项目 HTML 文件列表） |

---

## 编辑器区域

| 文件 | 导出声明 | 职责 |
|------|----------|------|
| `MainContentArea.kt` | `MainContentArea()` | 编辑器 Tab 栏 + 内容区（分发到各 TabType 处理） |
| `CodeEditor.kt` | `CodeEditor()` | 代码编辑器（语法高亮 + 行号 + 行 diff） |
| `EditorModes.kt` | `NormalEditMode()` | 常规文本编辑模式（BasicTextField 封装） |
| `EditorSyntaxHighlight.kt` | `SyntaxHighlightTransformation` | 语法高亮 VisualTransformation 包装 |
| `EditorTextOps.kt` | `TextFieldValue.indent()`, `dedent()`, `toggleComment()` | 触屏文本编辑操作（缩进/注释/撤销） |

---

## 媒体/文件查看器

| 文件 | 导出声明 | 职责 |
|------|----------|------|
| `ImagePreview.kt` | `ImagePreview()` | 图片查看器（缩放/手势/元信息） |
| `AudioPlayer.kt` | `AudioPlayer()` | 音频播放器 UI |
| `AudioPlaybackState.kt` | `AudioPlaybackState` | 音频播放状态 + 设备扫描 |
| `LyricsDisplay.kt` | `LyricsDisplay()` | 同步歌词显示 |
| `LyricsParser.kt` | `LyricsParser` (object) | LRC 歌词解析 |
| `VideoPlayer.kt` | `VideoPlayer()` | 视频播放器 UI（TextureView + MediaPlayer） |
| `ArchiveViewer.kt` | `ArchiveViewer()` | 压缩包内容查看器 |
| `WebPreview.kt` | `WebPreview()` | Web 文件实时预览/代码编辑双模式切换 |

---

## 设置/配置

| 文件 | 导出声明 | 职责 |
|------|----------|------|
| `SettingsPane.kt` | `SettingsPane()` | 设置面板（模型/主题/语言/MCP 服务器） |
| `ThemeSettingsCard.kt` | `ThemeSettingsCard()` | 主题模式切换卡片 |
| `LanguageSettingsCard.kt` | `LanguageSettingsCard()` | 语言切换卡片 |

---

## 顶部栏

| 文件 | 导出声明 | 职责 |
|------|----------|------|
| `MainTopBar.kt` | `MainTopBar()` | 主工具栏编排器（文件/编辑菜单 + 引用子组件） |
| `SearchBar.kt` | `SearchBar()` | 最近文件搜索输入框 + 下拉结果 |
| `AudioControl.kt` | `AudioControl()` | 音乐选择器 + 播放/暂停/切歌控件 |
| `ModelSelector.kt` | `ModelSelector()` | 本地/云端模型选择下拉菜单 |
| `AIChatPanel.kt` | `AIChatPanel()` | AI 聊天面板（输入框 + 消息列表 + 文件拖拽） |

---

## 核心层（core/）

| 文件 | 导出声明 | 职责 |
|------|----------|------|
| `FileManager.kt` | `FileManager` | 底层文件系统操作（读/写/列表/递归） |
| `LiteRTManager.kt` | `LiteRTManager`, `EngineStatus`, `DownloadStatus` | 本地 AI 推理引擎管理 |
| `CloudLLMClient.kt` | `CloudLLMClient` | 云端 LLM API 调用 |
| `ChatViewModel.kt` | `ChatViewModel` | AI 对话状态管理（消息/上下文/文件关联） |
| `ChatUiState.kt` | `ChatUiState`, `ChatRole`, `ModelActivity` | 聊天 UI 状态模型 |
| `ConversationDisplay.kt` | `DisplayRole`, `DisplayItem`, `toDisplayItems()` | 聊天消息 → 显示项转换 |
| `ConversationRepository.kt` | `ConversationRepository` | 聊天记录持久化（DataStore） |
| `AIToolSet.kt` | `AIToolSet` | AI 代理工具集（读/写/搜索/终端等 Tool 注册） |
| `FileOperationEvents.kt` | `FileOperationEvents` | 文件操作事件总线（跨组件通信） |
| `CodeEditTool.kt` | `CodeEditTool` | AI 代码编辑工具（搜索替换 block） |
| `TextEditTool.kt` | `TextEditTool` | AI 文本编辑工具（行级插入替换） |
| `SyntaxHighlighter.kt` | `highlightSyntax()` | Kotlin/XML/JSON 语法解析 + AnnotatedString 生成 |
| `DiffUtils.kt` | `LineChangeType`, `computeLineDiff()`, `replaceInFile()` | 行 diff 计算 + 代码块替换 |
| `LogCollector.kt` | `LogCollector` | 日志收集器（终端输出缓存） |
| `FileLogger.kt` | `FileLogger` | 文件日志记录 |
| `LanguageManager.kt` | `LanguageManager`, `ProvideLocalizedContext()` | 多语言管理 |

---

## 快速定位指南

**查找入口：** `HomeScreen.kt` → 所有子组件都在 `components/` 下聚合

| 你想修改 | 找 |
|----------|-----|
| 文件浏览器外观 | `resourcepanel/` 目录下所有文件 |
| 编辑器行号/高亮 | `editor/CodeEditor.kt` |
| 搜索逻辑（全文搜索） | `SearchPanel.kt` / `FileManager.kt` |
| 搜索逻辑（最近文件） | `SearchBar.kt` |
| AI 对话消息 | `AIChatPanel.kt` / `ChatViewModel.kt` |
| Web 预览功能 | `PreviewPanel.kt` + `WebPreview.kt` |
| 音频播放控制 | `AudioControl.kt` + `AudioPlaybackState.kt` |
| 模型选择下拉 | `ModelSelector.kt` |
| 文件打开方式判断 | `FileTypeUtil.kt` |
| 新增 Tab 类型 | `TabItem.kt` → `TabType` 枚举 |
| 布局调整 | `ThreeColumnLayout.kt` |
| 侧边栏按钮 | `Sidebar.kt` |
| 设置面板内容 | `SettingsPane.kt` + `ThemeSettingsCard.kt` + `LanguageSettingsCard.kt` |
