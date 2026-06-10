# 移除的本地 LiteRT-LM 功能说明

> 为匹配 LiteRT-LM 官方示例 `Main.kt` 的简洁模式，以下功能已从本地模型路径中移除。

## 1. 工具调用 (Tool Calling)

**移除内容**：
- `ConversationConfig.tools` — 不再注册 AIToolSet
- `automaticToolCalling = false` → 恢复为默认 true（但无工具可调）
- `processWithJsonTools()` 中的工具执行循环（toolCallHandler.executeAiTool）
- Lint 自动注入

**影响**：
- 本地模型不再能执行文件操作、代码搜索、工具调用
- 云端模型路径不受影响，仍保留工具调用功能

## 2. 上下文注入 (Context Injection)

**移除内容**：
- `getMemoryContext()` 对话记忆注入到用户消息前
- `getKeyFactsContext()` 关键事实注入到用户消息前
- `buildEditorContext()` 编辑器上下文注入到用户消息前

**影响**：
- 用户消息仅发送原始文本，不再附带对话记忆/关键事实/编辑器状态
- 本地模型上下文中仅包含 system instruction 和对话历史

## 3. 思考块 / Channel (Thinking Channel)

**状态**：**显式配置**。`ConversationConfig` 中通过 `Channel("thinking", "<think>", "</think>")` 指定 start/end 标记，C++ `ExtractChannelContent` 用正则匹配并提取到 `msg.channels`。

## 4. 推理取消后的会话清理 (Conversation Cleanup)

**移除内容**：
- `cancelProcess()` 调用 — 取消生成时不再停止 C++ 层推理
- `closeConversation()` 调用 — 取消/异常后不再销毁会话

**影响**：
- 匹配官方示例：一个会话贯穿整个对话生命周期，永不关闭
- 取消生成后会话状态可能不一致（C++ 端 b/450903294 已知问题）
- 当前会话若因取消进入异常状态，新消息将失败 → 需用户手动"新建对话"

## 5. 消息质量检查 (Quality Check)

**移除内容**：
- 空响应重试
- 重复文本检测
- 重试循环

**影响**：
- 模型输出空回复或重复内容时不再自动重试
- 单次 `sendMessageAsync` 调用返回即止

## 6. 单轮超时保护 (Timeout)

**移除内容**：
- `withTimeoutOrNull(300_000L)` 5 分钟超时包装

**影响**：
- 推理阻塞时用户只能通过"取消生成"打断
- 匹配官方示例：等待模型自然完成

## 未受影响的功能

- 云端模型（OpenAI 兼容 API）所有功能保留
- 引擎管理（加载/卸载/下载模型）
- 对话历史管理（新建/切换/删除对话）
- 消息显示和流式更新
- 输入优化（InputOptimizer）
- 系统指令构建
- 多模态图片输入
