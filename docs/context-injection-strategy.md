# IDE 上下文注入策略说明

> 本项目是**Android IDE 编码助手**，非通用聊天工具。上下文注入是其核心差异化能力。

---

## 为什么需要上下文注入

通用聊天模型只有对话历史，对用户的开发环境一无所知。IDE 助手必须让模型感知到：

| 需要知道 | 否则模型会 |
|----------|-----------|
| 当前打开的哪个文件 | 不知道用户在改什么，无法提供针对性建议 |
| 项目目录结构 | 无法定位文件、理解模块关系 |
| 哪些文件刚被改过 | 推荐过时代码，忽略未保存修改 |
| 编译/Lint 状态 | 推荐已有错误的代码 |
| 用户自定义规则 | 忽略项目约定 |

这些信息不会出现在对话历史中，必须**主动构造并注入**到模型的输入上下文。

---

## 移动端约束：无光标级精细化

桌面端 IDE 助手（如 GitHub Copilot）可以向模型注入**光标所在行号 + 光标行内容**，实现精确到行的上下文感知。

本项目面向**移动端**，受限于：
- 移动端文件编辑器的 API 限制，无法稳定获取光标精确位置
- 触摸输入场景下，光标位置变化频繁且无明确语义
- 移动端模型上下文窗口有限（默认 32K，用户可配置），需要合理分配预算

因此采用**活动文件替代**策略：

```
桌面端：活动文件 + 光标行号 + 光标行内容 → 精确到行
移动端：活动文件路径 + 已打开文件列表 → 精确到文件
```

活动文件（`activeFilePath`）是移动 IDE 中语义最明确的上下文锚点：
- 用户正在查看/编辑的文件
- 工具调用的默认作用目标
- 搜索结果的首选关联文件

### 光标数据存在但不注入

`ChatUiState` 中 `cursorLine` / `cursorLineContent` 字段完整（`CodeEditor` 通过 `onCursorChange` 实时推送至 ViewModel），但 `buildEditorContext()` **故意不引用它们**：

| 原因 | 说明 |
|------|------|
| 移动端触摸光标漂移 | 手指点击和键盘导航的光标位置不可预测，注入不稳定信息会误导模型 |
| 上下文预算约束 | 光标行信息优先级低于项目结构/Lint，在有限窗口内优先保证整体视角 |
| 方案可逆 | 数据通道已就绪，若后续需要启用，只需在 `buildEditorContext()` 中加入 `s.cursorLineContent` 即可 |

这种"通道就绪但主动关闭"的设计支持未来按需开启，不引入 break change。

---

## 上下文构建方式

### 1. `buildEditorContext()` — 全量快照

**触发时机**：每次用户发送消息时（对话开始前）

```kotlin
[当前编辑器上下文]
活动文件: app/src/main/kotlin/.../MainActivity.kt
项目: MyApp
项目绝对路径: /storage/emulated/0/MyApp
工作区结构:
  [DIR] app/
  [DIR] app/src/
  [DIR] app/src/main/
  [FILE] app/build.gradle.kts (2.3KB)
已打开文件:
  - app/src/main/kotlin/.../MainActivity.kt ← 活动
  - app/src/main/res/values/strings.xml [已修改]
```

**包含字段**：
- `活动文件` — 光标所在文件
- `项目名` / `项目绝对路径` — 定位工作区
- `工作区结构` — 前 2 层目录树（最多 20 项）
- `已打开文件` — 最多 10 个，含 `[已修改]` 和 `← 活动` 标记

### 2. Lint 诊断注入

**触发时机**：工具执行写/编辑/删除等修改操作后

```
[Lint 诊断]
• UnusedImport (app/src/main/kotlin/.../Main.kt:15): Unused import
```

通过 `readLints()` 读取 Gradle Lint report 或 problems report，仅在发现 Error/Fatal 级别问题时注入。

---

## 三路注入渠道

### 渠道 A：LiteRT 本地模型（主路径）

```
用户发送消息
  ↓
sendMessageAsync(Message.system(editorCtx))  ← 全量上下文
  ↓
sendMessageAsync(Message.user(text))         ← 用户请求
  ↓
  [Conversation 自动处理工具调用，AIToolSet 回调报告状态]
  ↓
collect 完成 → 若工具有修改：
  ↓
  sendMessageAsync(Message.system(
    [Lint 诊断]\n...
  ))
```

### 渠道 B：Cloud LLM

```
构建 historyMessages
  → 插入 ChatMessage(role=System, content=editorCtx)
  → 插入 ChatMessage(role=User, content=text)
  ...（每轮 Cloud API 调用）
  → 每3轮或修改操作后插入全量 editorCtx
```

### 渠道 C：Fallback（自动工具调用兜底）

```
确保 Conversation 后：
  sendMessageAsync(Message.system(editorCtx)).collect {}
  sendMessageAsync(Message.user(text)).collect {}
```

---

## 检查结果：无错误信息注入

| 检查项 | 结果 | 说明 |
|--------|------|------|
| `activeFilePath` 为实时编辑器状态 | ✓ | `setActiveFileContext()` 由 IDE 插件实时推送 |
| `openedFilePaths` 同步 Tab 状态 | ✓ | `setOpenedFilePaths()` 在 Tab 变更时更新 |
| `modifiedFilePaths` 监听文件修改 | ✓ | `setModifiedFilePaths()` 由文件保存/编辑事件触发 |
| `projectRootName` 来自 SAF URI | ✓ | `setProjectRoot()` 取 `DocumentsContract.getTreeDocumentId` |
| 工作区树非过期缓存 | ✓ | `fileManager.buildFileTreeString()` 实时扫描磁盘 |
| Lint 诊断真实读取 Gradle 报告 | ✓ | `readLints()` 解析 `lint-results.xml` / `problems-report.html` |
| 无伪指令注入 | ✓ | 所有上下文标记为 `System` role，模型按规则不执行 |
| 无用户数据泄露 | ✓ | 仅文件路径/代码，不注入系统文件/密码等敏感数据 |
| `[OK]/[ERROR]` 旧格式残留 | ✅ 已清除 | 旧版 tool result 格式指示已从 system prompt 移除 |

---

## 与自动工具调用的兼容性

`automaticToolCalling = true` 模式下，上下文注入**不影响** Conversation 内部工具闭环：

```
system msg (editorCtx)     ← 工具执行前注入
user msg (用户请求)
    ┌─ auto: tool_call → ToolManager → tool_response ─┐
    └──────── 循环直至纯文本响应 ──────────────────────┘
system msg (Lint 诊断)  ← 修改后的 Lint 注入（影响下一轮）
```

- 开始前的 `system msg`：模型能感知当前编辑器状态
- 结束后的 `system msg`：模型在下一轮用户请求时使用
- 工具执行中：无上下文干扰，Conversation 独立管理

---

## 关键文件

| 文件 | 内容 |
|------|------|
| `ChatViewModel.buildEditorContext()` | 全量上下文构造 |
| `ChatViewModel.buildSystemInstruction()` | 系统指令（角色定义 + 规则 + 记忆） |
| `ChatViewModel.buildFileAttachmentBlock()` | 用户附件引用块 |
| `ChatViewModel.autoInjectLint()` | 修改后 Lint 诊断注入 |
| `ChatViewModel.processWithJsonTools()` — 行 1569-1571 | 本地模型初始注入 |
| `ChatViewModel.processWithJsonTools()` — 行 1634-1646 | 本地模型修改后注入 |
| `ChatViewModel.processWithCloudTools()` — 行 2086-2090 | Cloud 初始注入 |
| `ChatViewModel.processWithCloudTools()` — 行 2300-2306 | Cloud 修改后注入 |
| `ChatViewModel.fallbackToAutoToolCalling()` — 行 1809 | Fallback 注入 |
