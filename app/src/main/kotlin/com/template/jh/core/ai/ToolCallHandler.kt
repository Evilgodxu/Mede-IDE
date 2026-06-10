package com.template.jh.core.ai

import android.util.Log
import com.template.jh.core.config.ChatConfig
import com.template.jh.core.storage.FileManager
import com.template.jh.core.utils.FileLogger
import com.template.jh.model.chat.ModelActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 工具调用处理器：解析模型输出中的工具调用、执行工具、管理回调。
 *
 * 职责：
 * - 将 aiToolSet.callback 转发暴露
 * - 直接执行工具（云端路径）
 * - 解析多种格式的工具调用 JSON
 * - 剥离工具调用 JSON 保留自然语言
 * - Lint 自动注入检测
 */
class ToolCallHandler(
    private val aiToolSet: AIToolSet,
    private val fileManager: FileManager,
) {
    /** 工具执行状态回调 — 转发到 aiToolSet */
    var callback: ToolExecutionCallback?
        get() = aiToolSet.callback
        set(value) { aiToolSet.callback = value }

    // ============================================================
    // 工具执行
    // ============================================================

    /** 直接执行工具（云端路径使用） */
    fun executeAiTool(name: String, args: Map<String, String>): String = try {
        Log.d("ToolCallHandler", "executeAiTool: name=$name args=$args")
        FileLogger.d("ToolCallHandler", "executeAiTool: name=$name args=$args")

        fun Map<String, String>.g(key: String, vararg aliases: String): String =
            this[key] ?: aliases.firstNotNullOfOrNull { this[it] } ?: ""
        fun Map<String, String>.gInt(key: String, vararg aliases: String, default: Int = 0): Int =
            (this[key] ?: aliases.firstNotNullOfOrNull { this[it] })?.toIntOrNull() ?: default
        fun Map<String, String>.gBool(key: String, vararg aliases: String, default: Boolean = true): Boolean =
            parseBool(this[key] ?: aliases.firstNotNullOfOrNull { this[it] }, default)

        val result = when (name) {
            "listFiles" -> aiToolSet.listFiles(args.g("subPath", "sub_path", "path"))
            "readFile" -> aiToolSet.readFile(
                args.g("path"), args.gInt("offset", default = 1), args.gInt("limit", default = 1000))
            "writeFile" -> aiToolSet.writeFile(
                args.g("path"), args.g("content"), args.gBool("overwrite", default = false))
            "replaceInFile" -> aiToolSet.replaceInFile(
                args.g("path"), args.g("old_string", "oldString", "old_str"), args.g("new_string", "newString", "new_str"))
            "batchReplaceInFile" -> aiToolSet.batchReplaceInFile(
                args.g("path"), args.g("edits", "editsJson", "edits_json"))
            "grep" -> aiToolSet.grep(
                args.g("pattern", "query"), args.g("extension", "ext"), args.g("glob"),
                args.gBool("ignoreCase", "ignore_case", "caseSensitive", "case_sensitive"),
                args.gInt("contextLines", "context_lines", "context", default = 2))
            "searchInFiles" -> aiToolSet.searchInFiles(args.g("query"), args.g("extension", "ext"))
            "searchCodebase" -> aiToolSet.searchCodebase(args.g("query"), args.g("targetDirectories", "target_directories", "directories"))
            "runCommand" -> aiToolSet.runCommand(args.g("command"))
            "searchWeb" -> aiToolSet.searchWeb(args.g("query"))
            "deleteFile" -> aiToolSet.deleteFile(args.g("path"))
            "createDirectory" -> aiToolSet.createDirectory(args.g("path", "dirPath", "dir_path"))
            "readLints" -> aiToolSet.readLints()
            else -> "Unknown tool: $name"
        }
        FileLogger.d("ToolCallHandler", "executeAiTool: $name returned ${result.take(200)}")
        result
    } catch (e: Exception) {
        Log.e("ToolCallHandler", "executeAiTool failed: name=$name ${e.message}", e)
        FileLogger.e("ToolCallHandler", "executeAiTool failed: name=$name ${e.message}", e)
        "Tool error: ${e.message}"
    }

    // ============================================================
    // 工具名称 ↔ ModelActivity 映射
    // ============================================================

    fun toolNameToActivity(name: String): ModelActivity {
        return when (name) {
            "listFiles" -> ModelActivity.ListingFiles
            "readFile" -> ModelActivity.ReadingFile
            "writeFile" -> ModelActivity.WritingFile
            "replaceInFile", "batchReplaceInFile" -> ModelActivity.EditingFile
            "deleteFile" -> ModelActivity.DeletingFile
            "createDirectory" -> ModelActivity.CreatingDirectory
            "grep", "searchInFiles", "glob" -> ModelActivity.SearchingCode
            "searchCodebase" -> ModelActivity.SearchingCode
            "searchWeb" -> ModelActivity.SearchingWeb
            "searchConversationMemory", "getRecentConversationMemory" -> ModelActivity.SearchingCode
            "runCommand" -> ModelActivity.RunningCommand
            "readLints" -> ModelActivity.ReadingLints
            else -> ModelActivity.ExecutingTool
        }
    }

    // ============================================================
    // 路径工具
    // ============================================================

    fun resolveToolPath(path: String): String {
        if (path.startsWith("/")) return path
        val root = fileManager.projectDirPath.ifEmpty { fileManager.storageRootPath }
        return if (root.isNotEmpty()) "$root/$path" else path
    }

    fun extractPathFromArgs(args: Map<String, String>): String? {
        val path = args["path"]
        return if (path.isNullOrBlank()) null else path
    }

    // ============================================================
    // Lint 自动注入
    // ============================================================

    /** 工具调用后自动注入 Lint 诊断（仅当有修改操作时） */
    suspend fun autoInjectLint(toolCalls: List<Triple<String?, String, Map<String, String>>>): String? {
        val modifyingTools = setOf("writeFile", "replaceInFile", "batchReplaceInFile", "deleteFile", "createDirectory")
        if (toolCalls.none { it.second in modifyingTools }) return null
        val result = withContext(Dispatchers.IO) { aiToolSet.readLints() }
        return if (result.contains("No lint errors") || result.contains("读取诊断失败") || result.contains("No errors")) null
        else "[Lint 诊断]\n$result"
    }

    // ============================================================
    // JSON 工具调用解析
    // ============================================================

    /**
     * 从模型响应中提取所有工具调用。
     * 支持单次调用和批量调用（多个 JSON 在同一输出中）。
     */
    fun extractJsonToolCalls(text: String): List<Triple<String?, String, Map<String, String>>>? {
        val trimmed = text.trim()

        // 格式 1: <tool_call>JSON</tool_call> XML 标签
        val xmlCalls = Regex("""<tool_call[^>]*>(.*?)</tool_call>""", RegexOption.DOT_MATCHES_ALL)
            .findAll(trimmed).flatMap { m -> parseToolJson(m.groupValues[1]) }.toList()
        if (xmlCalls.isNotEmpty()) return xmlCalls

        // 格式 2: ```json ... ``` / ```tool ... ``` 围栏块
        val codeBlockCalls = Regex("""```(?:json|tool)?\s*\n?(.*?)\n?```""", RegexOption.DOT_MATCHES_ALL)
            .findAll(trimmed).flatMap { m -> parseToolJson(m.groupValues[1]) }.toList()
        if (codeBlockCalls.isNotEmpty()) return codeBlockCalls

        val toolPatterns = listOf(
            Regex("""\{\s*"name"\s*:"""), Regex("""\{\s*"function"\s*:"""), Regex("""\{\s*"tool_name"\s*:"""),
        )
        val hasAnyTool = toolPatterns.any { it.containsMatchIn(trimmed) }
        if (!hasAnyTool) {
            val lfmMatch = parseLfmFormat(trimmed)
            if (lfmMatch != null) return listOf(lfmMatch)
            val funcCall = parseFunctionCallFormat(trimmed)
            if (funcCall != null) return listOf(funcCall)
            return null
        }

        val calls = mutableListOf<Triple<String?, String, Map<String, String>>>()
        var searchStart = 0
        while (true) {
            val start = trimmed.indexOf('{', searchStart)
            if (start < 0) break
            if (isInsideThinkBlock(trimmed, start)) { searchStart = start + 1; continue }
            if (!isStandaloneJson(trimmed, start)) { searchStart = start + 1; continue }
            val end = findJsonBlockEnd(trimmed, start)
            if (end < 0) { searchStart = start + 1; continue }
            val jsonStr = trimmed.substring(start, end + 1)
            val repaired = repairJson(jsonStr)
            val parsed = parseSingleToolJson(repaired)
            if (parsed != null) {
                val prefix = if (calls.isEmpty()) trimmed.substring(0, start).trim().ifEmpty { null } else null
                calls.add(Triple(prefix, parsed.first, parsed.second))
            }
            searchStart = end + 1
        }
        return calls.ifEmpty { null }
    }

    /** 从模型回复剥离工具调用标记，仅保留自然语言部分 */
    fun stripToolCallJson(text: String): String {
        var result = text.trim()
        result = Regex("""<tool_call[^>]*>.*?</tool_call>""", RegexOption.DOT_MATCHES_ALL).replace(result, "")
        result = Regex("""```(?:json|tool)\s*\n[\s\S]*?\n```""").replace(result, "")
        result = Regex("""```\s*\{.*?"(?:name|function|tool_name)"\s*:.*?arguments\s*:.*?\}\s*```""", RegexOption.DOT_MATCHES_ALL).replace(result, "")
        val escapedTools = ChatConfig.KNOWN_TOOLS.joinToString("|") { Regex.escape(it) }
        result = Regex("""\{"(?:name|function|tool_name)"\s*:\s*"(?:$escapedTools)"\s*,\s*"arguments"\s*:\s*\{[^}]*\}\s*\}""", RegexOption.DOT_MATCHES_ALL).replace(result, "")
        result = Regex("""(?:$escapedTools)\s*\([^)]*\)""").replace(result, "")
        return result.trim()
    }

    // ============================================================
    // 布尔解析
    // ============================================================

    fun parseBool(value: String?, default: Boolean): Boolean {
        if (value == null) return default
        return when (value.trim().lowercase()) {
            "true", "1", "yes", "y", "on" -> true
            "false", "0", "no", "n", "off" -> false
            else -> default
        }
    }

    // ============================================================
    // 私有辅助方法
    // ============================================================

    /** 检查位置是否在 [think]...[/think] 块内部 */
    private fun isInsideThinkBlock(text: String, pos: Int): Boolean {
        val before = text.substring(0, pos.coerceIn(0, text.length))
        var depth = 0
        var searchFrom = 0
        while (true) {
            val openIdx = before.indexOf("[think]", searchFrom)
            val closeIdx = before.indexOf("[/think]", searchFrom)
            if (openIdx < 0 && closeIdx < 0) break
            if (closeIdx < 0 || (openIdx >= 0 && openIdx < closeIdx)) {
                depth++
                searchFrom = openIdx + 7
            } else {
                depth--
                searchFrom = closeIdx + 8
            }
        }
        return depth > 0
    }

    /** 检查 JSON 起始位置是否合理的工具调用上下文 */
    private fun isStandaloneJson(text: String, pos: Int): Boolean {
        if (isInsideThinkBlock(text, pos)) return false
        val tail = text.substring(pos + 1).take(60).trimStart()
        val knownKeys = listOf("\"name\"", "\"tool_name\"", "\"function\"", "\"arguments\"")
        return knownKeys.any { tail.startsWith(it) }
    }

    /** 查找从 start 处开始的 JSON 对象结束位置 */
    private fun findJsonBlockEnd(text: String, start: Int): Int {
        if (start >= text.length || text[start] != '{') return -1
        var depth = 0
        var i = start
        while (i < text.length) {
            val c = text[i]
            when {
                c == '"' -> {
                    i++
                    while (i < text.length) {
                        when (text[i]) {
                            '\\' -> i++
                            '"' -> break
                        }
                        i++
                    }
                }
                c == '{' -> depth++
                c == '}' -> {
                    depth--
                    if (depth == 0) return i
                }
            }
            i++
        }
        return -1
    }

    private fun isToolLikeContent(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return false
        val toolKeywords = ChatConfig.KNOWN_TOOLS
        if (toolKeywords.any { trimmed.contains(it) }) return true
        val jsonPatterns = listOf(
            Regex("""tool_call""", RegexOption.IGNORE_CASE),
            Regex("""\{[^}]*"name"\s*:\s*"[^"]+"[^}]*"arguments"\s*:"""),
        )
        return jsonPatterns.any { it.containsMatchIn(trimmed) }
    }

    /** 字符串感知的 JSON 修复 */
    private fun repairJson(json: String): String {
        val segments = mutableListOf<Pair<Boolean, String>>()
        val buf = StringBuilder()
        var idx = 0
        var inStr = false
        while (idx < json.length) {
            when {
                !inStr && json[idx] == '"' -> {
                    if (buf.isNotEmpty()) { segments.add(false to buf.toString()); buf.clear() }
                    buf.append('"'); idx++; inStr = true
                }
                inStr && json[idx] == '\\' -> {
                    buf.append(json[idx]); idx++
                    if (idx < json.length) { buf.append(json[idx]); idx++ }
                }
                inStr && json[idx] == '"' -> {
                    buf.append('"'); idx++; inStr = false
                }
                else -> { buf.append(json[idx]); idx++ }
            }
        }
        if (buf.isNotEmpty()) segments.add(inStr to buf.toString())
        return segments.joinToString("") { (isString, content) ->
            if (isString) content
            else content
                .replace(Regex(""",\s*\}"""), "}")
                .replace(Regex(""",\s*\]"""), "]")
                .replace(Regex("""([{,])\s*(\w+)\s*:""")) { "${it.groupValues[1]}\"${it.groupValues[2]}\":" }
        }
    }

    /** 解析 LFM2 格式 */
    private fun parseLfmFormat(text: String): Triple<String?, String, Map<String, String>>? {
        val knownTools = ChatConfig.KNOWN_TOOLS
        val lfmRegex = Regex("""(\w+)\s*\((.*?)\)""", RegexOption.DOT_MATCHES_ALL)
        for (m in lfmRegex.findAll(text)) {
            val name = m.groupValues[1]
            if (name !in knownTools) continue
            val rawArgs = m.groupValues[2]
            val args = mutableMapOf<String, String>()
            val argRegex = Regex("""(\w+)\s*=\s*(?:"([^"]*)"|'([^']*)'|(\S+))""")
            for (am in argRegex.findAll(rawArgs)) {
                val key = am.groupValues[1]
                val value = am.groupValues[2].ifEmpty {
                    am.groupValues[3].ifEmpty { am.groupValues[4] }
                }
                args[key] = value
            }
            return Triple(null, name, args)
        }
        return null
    }

    /** 解析 OpenAI 函数调用格式 */
    private fun parseFunctionCallFormat(text: String): Triple<String?, String, Map<String, String>>? {
        try {
            val json = org.json.JSONObject(text.trim())
            val name = json.optString("function", "").ifEmpty { return null }
            if (name !in ChatConfig.KNOWN_TOOLS) return null
            val argsRaw = json.optString("arguments", "")
            val args = mutableMapOf<String, String>()
            if (argsRaw.isNotBlank()) {
                try {
                    val argsJson = org.json.JSONObject(argsRaw)
                    for (k in argsJson.keys()) args[k] = argsJson.get(k).toString()
                } catch (_: Exception) { args["raw"] = argsRaw }
            }
            return Triple(null, name, args)
        } catch (_: Exception) { return null }
    }

    private fun parseToolJson(text: String): Sequence<Triple<String?, String, Map<String, String>>> = sequence {
        var pos = 0
        while (true) {
            val start = text.indexOf('{', pos)
            if (start < 0) break
            val end = findJsonBlockEnd(text, start)
            if (end < 0) break
            val result = parseSingleToolJson(text.substring(start, end + 1))
            if (result != null) yield(Triple(null, result.first, result.second))
            pos = end + 1
        }
    }

    private fun parseSingleToolJson(jsonStr: String): Pair<String, Map<String, String>>? {
        return try {
            val json = org.json.JSONObject(jsonStr)
            val name = json.optString("name", "")
            if (name.isNotEmpty() && name in ChatConfig.KNOWN_TOOLS && json.has("arguments")) {
                val argsJson = json.getJSONObject("arguments")
                val args = mutableMapOf<String, String>()
                for (k in argsJson.keys()) {
                    val v = argsJson.get(k)
                    args[k] = when (v) {
                        is org.json.JSONObject, is org.json.JSONArray -> v.toString()
                        else -> v.toString()
                    }
                }
                return Pair(name, args)
            }
            val toolName = json.optString("tool_name", "")
            if (toolName.isNotEmpty() && toolName in ChatConfig.KNOWN_TOOLS) {
                val argsJson = json.optJSONObject("arguments")
                    ?: json.optJSONObject("parameters")
                    ?: json.optJSONObject("params")
                    ?: org.json.JSONObject()
                val args = mutableMapOf<String, String>()
                for (k in argsJson.keys()) {
                    val v = argsJson.get(k)
                    args[k] = when (v) {
                        is org.json.JSONObject, is org.json.JSONArray -> v.toString()
                        else -> v.toString()
                    }
                }
                return Pair(toolName, args)
            }
            val func = json.optString("function", "")
            if (func.isNotEmpty() && func in ChatConfig.KNOWN_TOOLS && json.has("arguments")) {
                val argsJson = json.optJSONObject("arguments") ?: org.json.JSONObject()
                val args = mutableMapOf<String, String>()
                for (k in argsJson.keys()) {
                    val v = argsJson.get(k)
                    args[k] = when (v) {
                        is org.json.JSONObject, is org.json.JSONArray -> v.toString()
                        else -> v.toString()
                    }
                }
                return Pair(func, args)
            }
            return null
        } catch (_: Exception) {
            val repaired = repairToolArgs(jsonStr)
            if (repaired != null && repaired.first in ChatConfig.KNOWN_TOOLS) repaired else null
        }
    }

    /** 修复本地模型常见的单参 JSON 格式错误 */
    private fun repairToolArgs(raw: String): Pair<String, Map<String, String>>? {
        val args = raw.trim()
        val match = Regex("""\{\s*"([^"]+)"\s*:\s*([\s\S]*?)\s*\}""").find(args) ?: return null
        val paramName = match.groupValues[1]
        var valStr = match.groupValues[2].trim()
        when {
            valStr.startsWith("\"\"\"") && valStr.endsWith("\"\"\"") -> valStr = valStr.slice(3..valStr.length - 4)
            valStr.startsWith("'''") && valStr.endsWith("'''") -> valStr = valStr.slice(3..valStr.length - 4)
            valStr.startsWith("\"") && valStr.endsWith("\"") -> valStr = valStr.slice(1..valStr.length - 2)
                .replace("\\\"", "\"").replace("\\\\", "\\")
        }
        return Pair(paramName, mapOf(paramName to valStr))
    }
}
