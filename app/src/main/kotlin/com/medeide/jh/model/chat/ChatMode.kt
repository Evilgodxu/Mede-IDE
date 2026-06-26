package com.medeide.jh.model.chat

/** 对话模式（参考 LineCodePro ChatMode） */
enum class ChatMode(val key: String, val label: String) {
    Chat("chat", "问答"),
    Plan("plan", "规划"),
    Agent("agent", "执行");

    companion object {
        fun fromKey(key: String): ChatMode = entries.find { it.key == key } ?: Agent
        fun normalize(key: String): String = fromKey(key).key
    }
}

/** 模式对应的 system prompt 片段 */
fun ChatMode.promptContext(): String = when (this) {
    ChatMode.Chat -> """
## 当前会话模式
当前模式：Chat。
- Chat 是只读交流模式，只用于回答问题、解释代码、读取上下文、搜索资料。
- 禁止写入、编辑、删除文件；禁止执行 Shell。
- 如果用户要求实现、修复、迁移、执行命令或验证结果，说明需要切换到 Agent 模式。
""".trimIndent()

    ChatMode.Plan -> """
## 当前会话模式
当前模式：Plan。
- Plan 是只读规划模式。目标是理解需求、读取必要上下文、形成计划、列出风险；不要执行计划。
- 禁止调用任何会改变状态的工具（写入、编辑、删除、执行）。
- 输出应是可执行计划：先给结论，然后列步骤、涉及文件、需要的工具、验证命令和风险。
""".trimIndent()

    ChatMode.Agent -> """
## 当前会话模式
当前模式：Agent。
- Agent 是执行模式。可以主动读取上下文、调用工具、修改文件并验证。
- 执行前保持范围最小，优先读取相关文件，说明明显风险。
- 完成后总结改动、验证结果和剩余风险。
""".trimIndent()
}
