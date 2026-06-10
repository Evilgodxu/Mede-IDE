# LiteRT-LM 工具调用策略说明

## 概述

本项目采用 **"官方自动工具调用 + AIToolSet 回调打破黑盒"** 的混合策略。核心流程：

```
Conversation(automaticToolCalling = true) → 闭环处理 tool_call ↔ tool_response
                                                        ↓
                                            AIToolSet.@Tool 方法执行
                                                        ↓
                                            ToolExecutionCallback → ViewModel 更新 UI + 副作用
                                                        ↓
                                            最终文本 → collect 输出到对话框（零 JSON 污染）
```

该策略兼顾了**原生框架稳定性**、**实时 UI 可见性**与**多后端兼容性**。

---

## 当前工作流程

### 1. Conversation 创建阶段

```kotlin
ensureConversation(autoToolCalling = true)
```

- 通过 `ConversationConfig` 注册工具集：`tools = listOf(tool(aiToolSet))`
- **启用**官方自动工具调用：`automaticToolCalling = true`
- 工具执行闭环完全由 Framework 管理：model → tool_call → ToolManager.execute() → tool_response → model → ... → text
- 应用层通过 `AIToolSet.callback` 插入观察点，打破黑盒

### 2. 回调注入

工具执行前，`AIToolSet.traceTool()` 自动调用 `callback.onToolStart(name, args)`，ViewModel 据此更新 `modelActivity` 状态（Thinking / ReadingFile / WritingFile 等）。

工具执行后，`callback.onToolResult(name, args, result)` 收集结果用于副作用处理（Lint 注入、文件打开、上下文刷新）。

### 3. 流式响应采集

```kotlin
conv.sendMessageAsync(currentMessage).collect { msg ->
    fullResponse.append(msg.contents.toString())  // 纯文本，零 JSON
    updateModelMessage(currentMsgId, c, true)
    // 累加思考通道（channels）内容
    if (msg.channels.isNotEmpty()) { ... }
}
```

- `automaticToolCalling = true` 确保 `collect` 只收到**纯文本响应**
- 工具调用 JSON 在 native 层已分离，**不进对话框，不污染上下文**
- 无需 `stripToolCallJson()` 事后正则剥离

### 4. 质量监控与重试

```kotlin
while (true) {
    conv.sendMessageAsync(currentMessage).collect { ... }
    val response = fullResponse.toString().trim()
    // 空空响应重试
    if (response.length < 3 && rounds > 0) { ... 注入指令 ...; continue }
    // 文本重复检测
    if (rounds > 0 && textContent == lastTextResponse) { ... 注入指令 ...; continue }
    // 最终回答
    finalizeModelMessage(currentMsgId)
    return
}
```

质量监控**降级为仅检查文本**，不再解析工具 JSON，不再手动执行工具，不再构造 tool_response。

### 5. 工具执行与状态流转

执行流程由 `Conversation` 内部管理：

```
User Message → [auto] → ToolManager.execute(AIToolSet.readFile)
                             ↓
                        callback.onToolStart("readFile")  →  UI: 正在读取文件
                             ↓
                        readFile 实际执行
                             ↓
                        callback.onToolResult("readFile") → 副作用收集
                             ↓
                        [auto] tool_response 回传模型
                             ↓
                        [auto] 模型继续推理 → 最终文本 → collect → 对话框
```

---

## 为什么要这么做

### 纯原生自动调用的局限性（无回调）

LiteRT-LM 的 `automaticToolCalling = true` 原生模式会在 `Conversation` 内部黑盒执行：

```
模型输出 tool_call → native 执行工具 → 自动构造 tool_response → 自动发回模型
```

原生模式（无回调）的问题是：

| 问题 | 说明 |
|------|------|
| UI 不可见 | 用户看到模型一直在"思考"，实际在执行多个工具，但 UI 无任何指示 |
| 无副作用扩展 | 工具执行后无法自动注入 Lint 诊断、刷新上下文、打开修改的文件 |
| 无质量监控 | 无法检测空响应、文本重复等问题并主动干预 |
| 无云端兼容 | 仅适用于 LiteRT 后端，无法复用到 Cloud LLM 流程 |

### 纯手动解析的局限性

早期实现完全依赖 `extractJsonToolCalls()` 从文本中抠出 JSON：

| 问题 | 说明 |
|------|------|
| 格式脆弱 | 需兼容 XML `<tool_call>`、代码块 ` ```json `、裸 JSON、LFM `func()` 等多种格式 |
| 解析失败率高 | 模型可能在 JSON 中混入自然语言、换行、markdown，导致正则/JSON 解析失败 |
| 维护成本高 | 每增加一种模型输出风格，都需更新解析器 |
| 无类型安全 | 手动解析后全是 `String`，需再次转换类型，容易出错 |

### 混合策略的动机

> **让框架做它最擅长的事（结构化解析），让应用层做它必须做的事（业务编排）。**

- native 层对模型输出模板最了解，解析 `tool_calls` 的准确率远高于字符串正则
- 应用层对 Android 业务最了解，工具执行、UI 反馈、副作用管理必须自己掌控
- 关闭 `automaticToolCalling` 只是关闭了"自动执行"，并未关闭"自动解析"，框架仍会输出结构化 `toolCalls`

---

## 优势

| 维度 | 优势说明 |
|------|----------|
| **上下文清洁度** | 工具调用 JSON 走元数据通道，不进入对话文本，零事后剥离 |
| **UI 实时性** | 工具执行前即触发 `onToolStart` 回调，用户看到实时 Activity 状态而非 JSON 闪烁 |
| **解析准确率** | 依赖 native 层结构化解析，规避手动 JSON 解析的 90%+ 失败场景 |
| **副作用扩展** | 回调收集工具结果后自动注入 Lint、刷新上下文、触发文件打开 |
| **质量监控** | 简化后的文本质量检查（空响应、重复文本）保持对话稳定性 |
| **跨后端兼容** | 同一套代码中 Cloud LLM 路径保留手动解析，不依赖 LiteRT Conversation |
| **维护成本** | 零 JSON 解析代码维护，不再处理多格式兼容、正则剥离 |

---

## 三种模式对比

| 特性 | 纯原生自动<br>`autoToolCalling=true`<br>无回调 | 纯手动解析<br>`extractJsonToolCalls()` | 本项目<br>`autoToolCalling=true`<br>+ 回调 |
|------|-------------------------------------------|----------------------------------------|----------------------------------------|
| **工具识别准确率** | 高（native 层） | 中低（文本正则） | **高**（native 层） |
| **UI 状态可见性** | 无（黑盒） | 中（后设，非实时） | **高**（实时回调） |
| **对话文本清洁度** | 高（无 JSON） | 低（JSON 混内容+事后剥离） | **高**（零 JSON 污染） |
| **执行可控性** | 低（闭环内） | 高（完全控制） | **中**（闭环+回调观察） |
| **质量监控** | 无 | 有 | **有**（简化，仅文本检查） |
| **副作用扩展** | 无 | 有 | **有**（回调驱动） |
| **维护成本** | 低 | 高（多格式兼容） | **低**（解析零代码） |
| **云端复用** | 不支持 | 支持 | **支持**（Cloud 路径保留手动） |
| **上下文注入** | 不支持 | 支持（轮次间） | **支持**（collect 后 system msg） |
| **适用场景** | 简单对话 | 无原生 tool_call 的后端 | **生产级 Android IDE 助手** |

---

## 关键代码位置

- `AIToolSet.traceTool()` — `@Tool` 方法包装器，自动触发 `onToolStart` / `onToolResult`
- `AIToolSet.callback: ToolExecutionCallback` — ViewModel 注入的回调，打破自动调用黑盒
- `ChatViewModel.processWithJsonTools()` — LiteRT 本地主对话流程（注入 callback + autoToolCalling + 文本质量监控）
- `ChatViewModel.processWithCloudTools()` — Cloud LLM 路径
- `AIToolSet` — 工具集定义（17 个 `@Tool` 注解方法）

---

## 结论

本策略的核心设计哲学是 **"闭环交给框架，反馈通过回调"**。通过启用 `automaticToolCalling = true` 和注入 `ToolExecutionCallback`，获得了：

1. **零 JSON 污染的对话上下文** — 工具调用不走文本管线，不进入 UI 和对话历史
2. **实时 UI 可见性** — 回调在工具方法执行前即时触发，用户看到的是"正在读取文件..."而非 JSON 闪烁
3. **极低的维护成本** — 不再维护多格式 JSON 解析器，不再需要事后正则剥离
4. **保留扩展能力** — 回调驱动的 Lint 注入、文件打开、上下文刷新
5. **向后兼容** — `processWithCloudTools()` 路径保留手动解析，确保 Cloud LLM 正常运作

这是在移动 IDE 场景下，平衡**上下文清洁度**、**用户体验**与**维护成本**的最优解。
