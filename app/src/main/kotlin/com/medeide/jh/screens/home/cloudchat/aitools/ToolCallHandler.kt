package com.medeide.jh.screens.home.cloudchat.aitools

import com.medeide.jh.core.data.logging.FileLogger
import org.json.JSONObject

/** 工具执行回调：开始/结束，用于 UI 卡片显示 */
interface ToolExecutionCallback {
    fun onToolStart(name: String, args: Map<String, String>) {}
    fun onToolResult(name: String, args: Map<String, String>, result: String) {}
}

class ToolCallHandler(private val aiToolSet: AIToolSet) {

    var callback: ToolExecutionCallback? = null

    /** 执行工具调用，返回结果字符串 */
    fun executeTool(name: String, args: Map<String, String>): String = try {
        callback?.onToolStart(name, args)
        FileLogger.i("Tool", "→ $name ${args.entries.joinToString(", ") { "${it.key}=${it.value}" }}")
        fun Map<String, String>.g(key: String, vararg aliases: String): String =
            this[key] ?: aliases.firstNotNullOfOrNull { this[it] } ?: ""
        fun Map<String, String>.gInt(key: String, vararg aliases: String, default: Int = 0): Int =
            (this[key] ?: aliases.firstNotNullOfOrNull { this[it] })?.toIntOrNull() ?: default

        val result = when (name) {
            // ReadTools
            "listFiles" -> aiToolSet.listFiles(args.g("path"), args.g("ignore"))
            "readFile" -> aiToolSet.readFile(args.g("file_path", "path"), args.gInt("offset"), args.gInt("limit", default = 1000))
            "grep" -> aiToolSet.grep(
                args.g("pattern"), args.g("path"), args.g("glob"),
                parseBool(args.g("-i", "i"), true),
                args.gInt("head_limit", default = 100),
                args.gInt("-C", "C", default = 2))
            "glob" -> aiToolSet.glob(args.g("pattern"), args.g("path"), args.gInt("maxResults", default = 100))
            // EditTools
            "writeFile" -> aiToolSet.writeFile(args.g("file_path", "path"), args.g("content"), parseBool(args.g("overwrite"), false))
            "replaceInFile" -> aiToolSet.replaceInFile(args.g("file_path", "path"), args.g("old_str", "old_string"), args.g("new_str", "new_string"))
            "batchReplaceInFile" -> aiToolSet.batchReplaceInFile(args.g("path"), args.g("edits"))
            "deleteFile" -> aiToolSet.deleteFile(args.g("file_paths", "path"))
            "createDirectory" -> aiToolSet.createDirectory(args.g("path"))
            "moveFile" -> aiToolSet.moveFile(args.g("source"), args.g("destination"))
            "copyFile" -> aiToolSet.copyFile(args.g("source"), args.g("destination"))
            // WebTools
            "webSearch" -> webSearch(args.g("query"))
            "webFetch" -> webFetch(args.g("url"))
            "askUser" -> askUser(args.g("question"))
            "todoWrite" -> todoWrite(args.g("todos"))
            else -> err("未知工具: $name")
        }
        FileLogger.i("Tool", "← $name → ${result.take(200)}")
        callback?.onToolResult(name, args, result)
        result
    } catch (e: Exception) {
        val msg = err("工具执行异常 (${e.message})")
        callback?.onToolResult(name, args, msg)
        FileLogger.e("Tool", "✗ $name", e)
        msg
    }

    private fun parseBool(value: String?, default: Boolean): Boolean = when (value?.lowercase()) {
        "true", "1", "yes" -> true
        "false", "0", "no" -> false
        else -> default
    }

    /** 解析单条工具调用的 JSON，返回 (name, args) 或 null */
    fun parseToolCall(jsonStr: String): Pair<String, Map<String, String>>? = try {
        val obj = JSONObject(jsonStr)
        val name = obj.optString("name", obj.optString("function", "").let {
            if (it.isNotEmpty() && it != "null") {
                try { JSONObject(it).optString("name", "") } catch (_: Exception) { it }
            } else ""
        })
        if (name.isEmpty()) return null
        val argsRaw = obj.optString("arguments", "{}")
        val argsJson = try { JSONObject(argsRaw) } catch (_: Exception) { JSONObject() }
        val args = mutableMapOf<String, String>()
        argsJson.keys().forEach { k -> args[k] = argsJson.optString(k, "") }
        name to args
    } catch (_: Exception) { null }

    /** 从模型回复中提取所有工具调用 */
    fun extractToolCalls(text: String): List<Pair<String, Map<String, String>>>? {
        // 尝试提取 ```json ... ``` 中的工具调用
        val blockPattern = Regex("""```(?:json|tool)?\s*\n?(\{.*?\})\n?```""", RegexOption.DOT_MATCHES_ALL)
        val blockCalls = blockPattern.findAll(text).mapNotNull { parseToolCall(it.groupValues[1]) }.toList()
        if (blockCalls.isNotEmpty()) return blockCalls

        // 尝试提取 tool_calls JSON
        val tcPattern = Regex("""\{"id":\s*"[^"]*",\s*"type":\s*"function",\s*"function":\s*\{[^}]*\}\s*\}""", RegexOption.DOT_MATCHES_ALL)
        val tcCalls = tcPattern.findAll(text).mapNotNull { parseToolCall(it.value) }.toList()
        if (tcCalls.isNotEmpty()) return tcCalls

        // 简单的单行 JSON 工具调用
        val simplePattern = Regex("""\{[^}]*"name"[^}]*"arguments"[^}]*\}""")
        val simpleCalls = simplePattern.findAll(text).mapNotNull { parseToolCall(it.value) }.toList()
        if (simpleCalls.isNotEmpty()) return simpleCalls

        return null
    }
}
