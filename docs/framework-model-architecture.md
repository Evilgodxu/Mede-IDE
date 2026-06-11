# 框架-模型职责分离与运行流程

## 设计原则

```
框架能做的框架做，模型只需知道它能干什么和怎么输出。
                            —— 不让模型操心框架责任
```

| 原则 | 说明 |
|------|------|
| **职责分离** | 工具执行、调用策略、重试、沙箱由框架处理，不注入系统提示词 |
| **上下文纯净** | 仅写入模型无法自取的信息（IDE 状态、项目结构），不写入框架自动处理的信息 |
| **不污染推理** | 每一条上下文都必须有唯一归属，避免模型在不同角色间混乱 |

---

## 架构总览

```
┌─────────────────────────────────────────────────────────┐
│                       ChatViewModel                      │
│  sendMessage() → 选择路径 → processLocal / processCloud │
├─────────────────────────────────────────────────────────┤
│                                                          │
│  ┌──────────────┐  ┌──────────────┐  ┌───────────────┐ │
│  │ 系统指令层    │  │ 实时感知层    │  │ 工具执行层     │ │
│  │ (静态)       │  │ (每轮刷新)   │  │ (框架自动)    │ │
│  │              │  │              │  │               │ │
│  │ ContextMgr   │  │ ContextMgr   │  │ AIToolSet     │ │
│  │ .buildSystem │  │ .buildEditor │  │ ToolCallHandler│ │
│  │ Instruction()│  │ Context()    │  │               │ │
│  └──────┬───────┘  └──────┬───────┘  └───────┬───────┘ │
│         │                 │                   │         │
└─────────┼─────────────────┼───────────────────┼─────────┘
          │                 │                   │
          ▼                 ▼                   ▼
    [模型推理]          [User消息前缀]       [后台执行]
```

---

## 三层数据结构

### Layer 1: 系统指令 (System Instruction)

**注入方式**: LiteRT `ConversationConfig.systemInstruction` / 云端 `messages[0].role=system`

**生命周期**: 会话创建时设置，缓存命中返回同一份，不随对话变化

**内容**:
```
You are an AI coding assistant running on-device via LiteRT-LM. Reply in 简体中文.

## 身份定位
- 你是配编程协作者（pair programmer），不是问答机器人
- 主动推断用户意图，模糊指令直接执行不追问

## 执行准则
- 最小修改范围：编辑已有文件优先（用 replaceInFile / batchReplaceInFile）
- 禁止直接输出代码到对话框，代码修改必须通过工具执行
- 禁止整文件重写，优先点对点精确编辑

## 输出规范
- 最小有效长度：不问候/不感谢/不道歉/不总结，仅输出必要信息
- 格式优先：列表 > 代码块 > 键值对 > 段落；禁止重复同一条信息

## 可用工具 (15个工具名 + 参数 + 行为描述)
```

**决策边界**: 模型行为指令（框架无法替代）→ 写入系统指令；框架能自动处理的 → 不写入

| 写入 | 不写入（原因） |
|------|--------------|
| 身份定位 | 沙箱机制（`requireProject` 工具拦截） |
| 编辑行为（用 replaceInFile） | 工具调用策略（框架自动） |
| 输出格式 | 重试逻辑（`MAX_TOOL_RETRY_ROUNDS`） |
| 工具列表 | 路径格式（`resolvePathOrAbsolute` 兼容） |
| | 设备信息（与任务无关） |

### Layer 2: 实时感知 (Real-time Context)

**注入方式**: 拼接到 User 消息前缀（非 system role）

**生命周期**: 每次 `sendMessage()` 动态构建，反映当前 IDE 状态

**内容**:
```
[实时感知]
时间: 2026-06-11 15:30 周四
项目: MyProject → /storage/emulated/0/MyProject
活动文件: MainActivity.kt  路径: /.../MainActivity.kt  第42行: val ctx = ...
未保存文件:
  - MainActivity.kt → /.../MainActivity.kt
已打开文件:
  - MainActivity.kt → /.../MainActivity.kt [未保存] ←
  - SettingsViewModel.kt → /.../SettingsViewModel.kt
```

**字段用途**:
| 字段 | 模型用途 |
|------|---------|
| 时间 | 时间戳相关操作 |
| 项目路径 | 构建绝对路径 |
| 活动文件 | 默认操作目标 |
| 光标行 | 精确定位编辑点 |
| 已打开/未保存 | 上下文感知，避免重复读取 |

#### 附加投递（按需）

除编辑器上下文外，以下信息也在 User 消息中按需拼接：

| 内容 | 条件 | 来源 |
|------|------|------|
| 对话记忆摘要 | 存在压缩记录 | `ConversationMemory.getMemoryContext()` |
| 关键事实 | 存在保活事实 | `ConversationMemory.getKeyFactsContext()` |
| 用户规则 | 有启用的规则 | `sysPromptRules` |
| 已启用技能 | 有启用的技能 | `sysPromptSkills` |
| 文件附件引用 | 用户拖入文件 | `buildFileAttachmentBlock()` |

### Layer 3: 工具执行层 (Tool Execution)

**触发方式**: 框架自动，模型无需感知

- **本地模型**: LiteRT C++ 框架通过 `msg.toolCalls` 结构化提取
- **云端模型**: OpenAI `tool_calls` delta 提取 / 文本回退解析

**工具执行流**:
```
模型输出 → 框架拦截工具调用 → ToolCallHandler.executeAiTool() → 结果回注 → 模型继续
```

---

## 完整运行流程

### 本地模型路径

```
1. sendMessage()
   │
   ├─ ensureConversation()
   │   ├─ 旧会话 close()
   │   ├─ ConversationConfig {
   │   │     systemInstruction = buildSystemInstruction()  ← Layer 1
   │   │     tools = listOf(tool(aiToolSet))               ← 结构化工具注册
   │   │     initialMessages = 历史消息
   │   │   }
   │   └─ liteRTManager.createConversation(config)
   │
   ├─ processLocalMessage(text, msgId)
   │   │
   │   ├─ 构建实时上下文 (Layer 2)
   │   │   ├─ memoryCtx (如有)
   │   │   ├─ keyFactsCtx (如有)
   │   │   ├─ editorCtx = buildEditorContext()             ← 动态刷新
   │   │   ├─ rules (如有)
   │   │   └─ skills (如有)
   │   │
   │   ├─ 拼接: 上下文 + text → userText
   │   │
   │   ├─ conv.sendMessageAsync(userText)                  ← 框架层
   │   │   └─ collect { msg → 流式输出到 UI }
   │   │
   │   └─ if toolCalls:
   │       ├─ executeAiTool() → 结果拼接
   │       ├─ UI 显示 [工具调用: name]
   │       └─ Message.tool(结果) → 下一轮推理
   │
   └─ finalizeModelMessage() → 保存对话历史 + 记忆入库
```

### 云端模型路径

```
1. sendMessage()
   │
   ├─ processWithCloudTools(text, msgId)
   │   │
   │   ├─ 构建 historyMessages (含 editorCtx 作为 System 消息)
   │   │
   │   ├─ cloudLLMClient.sendMessage(
   │   │     systemPrompt = buildSystemInstruction(),       ← Layer 1
   │   │     messages = historyMessages,
   │   │     toolsJson = buildOpenAIToolsJson(),            ← 结构化工具
   │   │   )
   │   │
   │   └─ while (toolCalls 存在):
   │       ├─ executeAiTool() → 结果
   │       ├─ Lint 自动注入 (如果有修改操作)
   │       ├─ historyMessages += [Model, Tool]
   │       ├─ 每 3 轮重新注入 editorCtx
   │       └─ 继续下一轮 API 调用
   │
   └─ recordUsage() → 用量统计
```

---

## 沙箱机制

### 入口点

```
用户长按文件夹 → setProjectRoot(uri) → FileManager.projectUri = uri
                                         ChatUiState.projectRootName = name
```

### 执行点

所有写操作入口统一调用 `requireProject()`:

```
writeFile / replaceInFile / batchReplaceInFile / deleteFile / createDirectory / runCommand
    │
    └─ requireProject(action)
        ├─ FileManager.projectUri == null → [ERROR] 沙箱保护：未打开项目
        └─ projectUri != null → 通过
```

**决策边界**: 沙箱逻辑完全在工具层，不写入系统提示词

---

## 未写入上下文的信息（及归属）

| 信息 | 为什么不需要模型知道 | 谁处理 |
|------|-------------------|--------|
| 工具调用策略（并行/串行） | LiteRT/OAI 框架自动管理 | 框架 |
| 重试次数 | `MAX_TOOL_RETRY_ROUNDS` 框架控制 | 框架 |
| 沙箱拦截条件 | `requireProject()` 工具返回错误 | 工具 |
| 路径格式兼容性 | `resolvePathOrAbsolute` 两路径都接受 | 工具 |
| 设备型号 / Android 版本 | 与代码任务无关 | 无 |
| 模型名称 / 后端 | 与推理任务无关 | 无 |
| Token 预算 | 压缩逻辑由框架触发 | ContextManager |
