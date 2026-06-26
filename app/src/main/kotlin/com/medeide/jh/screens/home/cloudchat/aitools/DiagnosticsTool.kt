package com.medeide.jh.screens.home.cloudchat.aitools

import org.json.JSONArray

class DiagnosticsTool {
    companion object {
        fun buildOpenAIToolsJson(): JSONArray {
            val arr = JSONArray()
            arr.put(buildAskUserTool())
            arr.put(buildTodoWriteTool())
            return arr
        }

        private fun buildAskUserTool() = toolDef("askUser",
            "向用户提问以获取决策或澄清。当需要用户选择方案、确认操作或提供额外信息时使用。",
            required = listOf("question"),
            "question" to p("string", "要向用户提出的问题内容，清晰说明需要什么信息"),
        )

        private fun buildTodoWriteTool() = toolDef("todoWrite",
            "创建任务列表跟踪复杂工作进度。当需要多步骤操作时，创建任务清单供自己和用户追踪。",
            required = listOf("todos"),
            "todos" to p("string", "任务列表JSON，格式：[{\"content\":\"任务描述\",\"status\":\"pending\"}]"),
        )
    }
}

fun askUser(question: String): String {
    return "[INFO] 请用户确认: $question\n（用户需要在前端输入框回应）"
}

fun todoWrite(todos: String): String {
    return try {
        val arr = JSONArray(todos)
        val count = arr.length()
        "[OK] 已创建 $count 个待办任务"
    } catch (e: Exception) {
        "[ERROR] 创建任务失败: ${e.message}"
    }
}
