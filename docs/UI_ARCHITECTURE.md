# 主界面 UI 架构

## 整体布局

四区横向排列：**顶栏区 + 侧栏区（图标导航 + 可展开面板）+ 工作区 + 协作区**

```
┌─────────────────────────────────────────────────────────────────┐
│  顶栏区 (MainTopBar)                                           │
├────┬──────────────────────────┬───────────────────┬────────────┤
│图标│  侧栏面板                │                   │            │
│导航│  (可展开 180-360dp)      │  工作区           │  协作区    │
│48dp│                         │  (权重填充)       │  228dp     │
│    │                         │                   │            │
│    │                         │                   │            │
└────┴──────────────────────────┴───────────────────┴────────────┘
```

---

## 一、顶栏区 — `MainTopBar`

- **组件**：`Scaffold.topBar` 传入 `MainTopBar`，内部使用 Material3 `TopAppBar`
- **高度**：`40.dp` + 底部 `HorizontalDivider`（1dp）
- **构成**：
  - **title 区** — `Row` 水平排列：文件按钮 | 编辑下拉菜单 | 搜索输入框
  - **actions 区** — `Row` 水平排列：音乐播放控件 | 模型选择器 `ModelSelector`
- **底部**：`HorizontalDivider`（`outlineVariant` 半透明色）

---

## 二、侧栏区 — 两级结构

### 2.1 图标导航栏 — `Sidebar`

- **组件**：`Surface` + `Column`，固定宽度 `48.dp`，全高
- **图标按钮**：`Surface(40×40.dp)` + `Icon`，选中时高亮为 `primaryContainer` 色
- **支持项**：`SidebarTab.Explorer`（文件夹图标）、`SidebarTab.Search`（搜索图标）
- **右侧**：`VerticalDivider`（1dp，`outlineVariant` 半透明色）

### 2.2 可展开侧栏面板 — `AnimatedVisibility`

- **组件**：`AnimatedVisibility` + `Column`，宽度由 `animateDpAsState` 驱动
- **展开/收起动画**：`expandHorizontally` / `shrinkHorizontally`，`200ms`
- **宽度**：默认 `180.dp`
- **内容**：根据选中 Tab 切换显示 `ResourcePanel`（文件资源管理器）或 `SearchReplacePanel`（搜索替换面板）
- **右侧**：`VerticalDivider`（条件显示，仅当面板可见时）

---

## 三、工作区 — `ThreeColumnLayout` 的权重区

- **组件**：`Box` 包裹，`Modifier.weight(1f).fillMaxHeight()`
- **内部**：`MainContentArea`（`Column` 布局）

### 3.1 Tab 栏 — `EditorTabBar`

- **组件**：`Row` + `horizontalScroll`，背景色 `surfaceVariant` 半透明
- **Tab 项**：每项为 `Row(32.dp)`，包含：
  - 类型图标（`Icon`，14×14dp）— 根据 `TabType` 映射不同图标
  - 标题文字（`labelSmall`，`maxWidth=120dp`）
  - 关闭按钮（`IconButton(24×24dp)` + `Close` 图标 12×12dp）
  - 未保存文件点击关闭时弹出 `DropdownMenu`（不保存关闭 / 保存并关闭）
- **右侧**：`MoreVert` 更多按钮，弹出 `DropdownMenu`（全部关闭 / 保存全部并关闭）

### 3.2 路径指示条

- 活动 Tab 为文件类时，在 Tab 栏下方显示一行完整路径（`labelSmall`，`surfaceVariant` 0.12 透明度背景）

### 3.3 内容区

- 根据 `TabType` 加载不同的内容组件：

| TabType | 组件 | 说明 |
|---------|------|------|
| `File` | `CodeEditor`（`tabContent` 提供） | 代码编辑器，支持右上角查找替换工具栏 |
| `Settings` | `SettingsPane` | 设置面板 |
| `Image` | `ImagePreview` | 图片预览 |
| `Audio` | `AudioPlayer` | 音频播放器 |
| `Video` | `VideoPlayer` | 视频播放器 |
| `Markdown` | `MarkdownPreview` | Markdown 预览/编辑双模式 |
| `Preview` | `WebPreview` | HTML 预览/编辑双模式 |

---

## 四、协作区 — 固定宽度右栏

- **组件**：`Column` 包裹，固定宽度 `228.dp`，全高
- **左侧**：`VerticalDivider`（1dp）
- **内部**：`CollabPanel`（`Column.fillMaxSize()`），纵向分三区：

### 4.1 协作区顶部栏 — `CollabTopBar`

- **高度**：`36.dp`
- **内容**：`Row` 水平排列 — 新建对话（`Add` 图标）| 历史记录（`History` 图标 + `DropdownMenu` 显示对话列表） | 设置（`Settings` 图标）

### 4.2 消息列表 — `CollabMessageList`

- **组件**：`Modifier.weight(1f)` 填充剩余空间
- **内容**：对话消息列表，可滚动

### 4.3 输入区 — `CollabInputBar`

- **内边距**：`horizontal=12.dp, vertical=8.dp`
- **子结构**：
  - `CollabInputField` — 文本输入区域
  - `CollabAttachmentBar` — 附件列表（图片 URI + 文件引用 + 目录引用）
  - `CollabToolBar` — 功能栏（发送/取消、优化、图片上传、上下文仪表盘入口）

---

## 五、工作区 Tab 打开方式

- **打开入口**：文件管理器 `ResourcePanel` 中点击文件 → `editorState.openTab()` / `editorState.openFileTab()`
- **Tab 数据结构**：`TabItem(id, title, type)` — `id` 为文件路径或设置页标识，`title` 为显示名，`type` 控制渲染组件
- **打开逻辑**：
  - 文本代码文件 → `TabType.File`（在 `openFileTab()` 内自动识别）
  - 图片 → `TabType.Image`
  - 音频 → `TabType.Audio`
  - 视频 → `TabType.Video`
  - 压缩包 → `TabType.Archive`
  - Markdown → `TabType.Markdown`（编辑器 + 预览双模式）
  - HTML/HTM/XHTML → `TabType.Preview`（编辑器 + 预览双模式）
  - 设置 → `TabType.Settings`（id 固定为 `"settings"`）
- **Tab 关闭时**：未保存的文件弹出确认菜单（不保存关闭 / 保存并关闭）

---

## 六、组件技术栈

| 层级 | 框架/库 |
|------|---------|
| UI 框架 | Jetpack Compose（Material3） |
| 适配方案 | `WindowSizeClass`（`rememberWindowSizeClass()`） |
| 状态管理 | `ViewModel` + `StateFlow`（`collectAsState()`） |
| 依赖注入 | Koin（`koinViewModel()` / `KoinJavaComponent.get()`） |
| 动画 | Compose Animation（`animateDpAsState`、`AnimatedVisibility`） |
| 图标 | Material Icons（`Icons.Default.*`） |
