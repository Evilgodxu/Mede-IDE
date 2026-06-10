package com.template.jh.core.ai

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.ai.edge.litertlm.Tool
import com.google.ai.edge.litertlm.ToolParam
import com.google.ai.edge.litertlm.ToolSet
import com.template.jh.core.editor.CodeEditTool
import com.template.jh.core.memory.ConversationMemory
import com.template.jh.core.storage.FileManager
import com.template.jh.core.utils.FileLogger
import java.io.File
import java.net.URLEncoder
import org.jsoup.Jsoup

/** 工具执行状态回调 — 由 ChatViewModel 注入，打破 automaticToolCalling 黑盒 */
interface ToolExecutionCallback {
    fun onToolStart(name: String, args: Map<String, String>)
    fun onToolResult(name: String, args: Map<String, String>, result: String)
}

class AIToolSet(
    private val context: Context,
    private val fileManager: FileManager? = null,
    private val conversationMemory: ConversationMemory? = null,
) : ToolSet {

    /** ChatViewModel 注入的回调，每次 @Tool 方法执行前后自动调用 */
    @Volatile
    var callback: ToolExecutionCallback? = null

    /** 包裹 @Tool 方法：自动触发 onToolStart / onToolResult */
    private inline fun <T> traceTool(name: String, block: () -> T): T {
        callback?.onToolStart(name, emptyMap())
        val result = block()
        callback?.onToolResult(name, emptyMap(), result?.toString() ?: "")
        return result
    }

    /** 带参数的包裹版本 */
    private inline fun <T> traceTool(name: String, vararg params: Pair<String, Any?>, block: () -> T): T {
        val args = params.associate { it.first to (it.second?.toString() ?: "") }
        callback?.onToolStart(name, args)
        val result = block()
        callback?.onToolResult(name, args, result?.toString() ?: "")
        return result
    }

    // === 统一结果格式 ===
    private fun ok(msg: String) = "[OK] $msg"
    private fun err(msg: String) = "[ERROR] $msg"
    private fun isOk(result: String) = result.startsWith("[OK]")
    private fun isErr(result: String) = result.startsWith("[ERROR]")

    // SAF 项目根 URI（用户通过文件夹选择器打开的目录）
    // 优先使用外部传入的 fileManager，否则使用 internal projectUri
    @Volatile var projectUri: Uri? = null
        set(value) {
            field = value
            value?.let { fileManager?.setProjectUri(it) }
        }

    /** 将路径转为相对路径：接收绝对路径或相对路径，统一转为相对路径 */
    private fun resolvePath(path: String): String {
        if (path.startsWith("/storage/") || path.startsWith("/data/")) {
            val base = fileManager?.let {
                it.projectDirPath.ifEmpty { it.storageRootPath }
            } ?: return path
            return path.removePrefix(base).trimStart('/')
        }
        return path.trim('/')
    }

    private fun resolvePathOrAbsolute(path: String): String {
        // 返回相对路径（如果是绝对路径则转换），供现有 FileManager 方法使用
        return resolvePath(path)
    }

    @Tool(description = "List files and directories with [FILE]/[DIR] prefixes. Shows file sizes for files. Use absolute path (like /storage/emulated/0/...) or relative path. Leave empty to list project root.")
    fun listFiles(
        @ToolParam(description = "Subdirectory path. Use absolute path (e.g. /storage/emulated/0/MyProject) or relative path from project root. Leave empty to list project root.") subPath: String = "",
    ): String = traceTool("listFiles", "subPath" to subPath) {
        Log.d("AIToolSet", "listFiles: subPath=$subPath")
        FileLogger.d("AIToolSet", "listFiles: subPath=$subPath")
        val fm = fileManager ?: return@traceTool err("No project folder is open.")
        val relPath = resolvePathOrAbsolute(subPath)
        val nodes = fm.listFilesAsNodes(relPath)
        if (nodes.isEmpty()) return@traceTool ok("Empty directory: ${subPath.ifBlank { "/" }}")
        val displayPath = if (relPath.isBlank()) "项目根目录/" else "$relPath/"
        buildString {
            appendLine(ok("$displayPath (${nodes.size} items)"))
            nodes.forEach { node ->
                if (node.isDirectory) {
                    appendLine("  [DIR] ${node.name}/")
                } else {
                    val size = if (node.size > 0) " (${formatSize(node.size)})" else ""
                    appendLine("  [FILE] ${node.name}$size")
                }
            }
        }.trimEnd()
    }

    private fun formatSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> "${"%.1f".format(bytes.toDouble() / (1024 * 1024))} MB"
    }

    private fun readFileRaw(path: String): String? {
        return fileManager?.readFileRaw(resolvePathOrAbsolute(path))
    }

    /** 获取项目根目录的绝对路径（用于上下文注入） */
    fun getProjectRootPath(): String {
        return fileManager?.let {
            it.projectDirPath.ifEmpty { it.storageRootPath }
        } ?: ""
    }

    companion object {
        // 自动截断阈值：超过此行数且未明确指定 limit 时，仅显示前 N 行
        private const val MAX_AUTO_LINES = 200
        private const val TRUNCATE_WARNING_LINES = 500

        /** 构建 OpenAI 兼容的 tools 定义 JSON */
        fun buildOpenAIToolsJson(): String {
            val tools = org.json.JSONArray()
            tools.put(buildReadFileTool())
            tools.put(buildWriteFileTool())
            tools.put(buildReplaceInFileTool())
            tools.put(buildBatchReplaceInFileTool())
            tools.put(buildDeleteFileTool())
            tools.put(buildCreateDirectoryTool())
            tools.put(buildListFilesTool())
            tools.put(buildGrepTool())
            tools.put(buildGlobTool())
            tools.put(buildSearchCodebaseTool())
            tools.put(buildRunCommandTool())
            tools.put(buildSearchWebTool())
            tools.put(buildReadLintsTool())
            tools.put(buildSearchConversationMemoryTool())
            tools.put(buildGetRecentConversationMemoryTool())
            return tools.toString()
        }

        // ... memory tool definitions
        private fun buildSearchConversationMemoryTool() = toolDef(
            "searchConversationMemory",
            "Search past conversation history by semantic meaning. Use this when you need to recall what was discussed earlier (e.g. 'what files did we modify?', 'what error did we fix?', 'what was the user's requirement?'). Searches all memory layers including short-term, summaries, and vector index.",
            listOf("query"),
            "query" to p("string", "What to search for, e.g. 'file structure decision' or 'error fix'"),
        )
        private fun buildGetRecentConversationMemoryTool() = toolDef(
            "getRecentConversationMemory",
            "Get summaries of the most recent conversation turns. Useful for recalling the context of the last few exchanges without needing a full search. Default returns last 5 entries.",
            props = arrayOf("count" to p("integer", "Number of recent entries to return (default 5, max 20)")),
        )

        private fun p(type: String, desc: String): org.json.JSONObject = org.json.JSONObject().apply {
            put("type", type); put("description", desc)
        }
        private fun toolDef(name: String, desc: String, required: List<String> = emptyList(), vararg props: Pair<String, org.json.JSONObject>): org.json.JSONObject = org.json.JSONObject().apply {
            put("type", "function")
            put("function", org.json.JSONObject().apply {
                put("name", name); put("description", desc)
                put("parameters", org.json.JSONObject().apply {
                    put("type", "object")
                    put("properties", org.json.JSONObject().apply { props.forEach { (k, v) -> put(k, v) } })
                    if (required.isNotEmpty()) put("required", org.json.JSONArray(required))
                })
            })
        }
        private fun buildReadFileTool() = toolDef("readFile", "Read file content with pagination", listOf("path"),
            "path" to p("string", "File path — absolute (e.g. /storage/emulated/0/...) or relative to project root"),
            "offset" to p("integer", "Starting line (1-based, default 1)"),
            "limit" to p("integer", "Max lines (default 1000)"),
        )
        private fun buildWriteFileTool() = toolDef("writeFile", "Create file. Set overwrite=true to overwrite existing. Default refuses overwrite.", listOf("path", "content"),
            "path" to p("string", "File path — absolute or relative to project root"),
            "content" to p("string", "Complete file content"),
            "overwrite" to p("boolean", "Overwrite existing file? Default false"),
        )
        private fun buildReplaceInFileTool() = toolDef("replaceInFile", "Edit file by replacing exact code block. Optional line range (lineStart/lineEnd) limits search scope.", listOf("path", "old_string", "new_string"),
            "path" to p("string", "File path — absolute or relative to project root"),
            "old_string" to p("string", "Exact code block to find (unique in file or line range)"),
            "new_string" to p("string", "Replacement code block"),
            "lineStart" to p("integer", "Limit search to lines from this line (1-based). 0 = whole file."),
            "lineEnd" to p("integer", "Limit search to lines up to this line (1-based). 0 = whole file."),
        )
        private fun buildBatchReplaceInFileTool() = toolDef("batchReplaceInFile", "Edit file at multiple non-overlapping locations", listOf("path", "edits"),
            "path" to p("string", "File path — absolute or relative to project root"),
            "edits" to p("string", "JSON array: [{\"old_string\":\"...\",\"new_string\":\"...\"}]"),
        )
        private fun buildDeleteFileTool() = toolDef("deleteFile", "Delete file or directory (permanent)", listOf("path"),
            "path" to p("string", "File/directory path — absolute or relative to project root"),
        )
        private fun buildCreateDirectoryTool() = toolDef("createDirectory", "Create directory (auto-creates parents)", listOf("path"),
            "path" to p("string", "Directory path — absolute or relative to project root"),
        )
        private fun buildListFilesTool() = toolDef("listFiles", "List directory contents with file sizes",
            props = arrayOf("subPath" to p("string", "Subdirectory path — absolute (e.g. /storage/emulated/0/...) or empty for root")),
        )
        private fun buildGrepTool() = toolDef("grep", "Search file contents by regex", listOf("pattern"),
            "pattern" to p("string", "Regex pattern"),
            "extension" to p("string", "File extension filter (e.g. 'kt')"),
            "glob" to p("string", "Glob pattern filter (e.g. '*.kt')"),
            "ignoreCase" to p("boolean", "Case insensitive (default true)"),
            "contextLines" to p("integer", "Context lines before/after match (default 2)"),
        )
        private fun buildSearchCodebaseTool() = toolDef("searchCodebase", "Semantic code search by meaning", listOf("query"),
            "query" to p("string", "Natural language query (e.g. 'where is auth?')"),
            "targetDirectories" to p("string", "Comma-separated directories to search"),
        )
        private fun buildRunCommandTool() = toolDef("runCommand", "Execute shell command", listOf("command"),
            "command" to p("string", "Command to execute, e.g. 'ls -la'"),
        )
        private fun buildSearchWebTool() = toolDef("searchWeb", "Search internet for current information", listOf("query"),
            "query" to p("string", "Concise search query"),
        )
        private fun buildReadLintsTool() = toolDef("readLints", "Read build/lint/compilation errors with file locations")
        private fun buildGlobTool() = toolDef("glob", "Find files by name pattern (glob syntax)", listOf("pattern"),
            "pattern" to p("string", "Glob pattern, e.g. '*.kt', '**/*.xml', 'Main*'"),
            "maxResults" to p("integer", "Max results (default 100)"),
        )
    }

    /** 替换后清理多余空行：3+ 连续换行 → 2，首尾空行 */
    private fun normalizeBlankLines(text: String): String {
        return text
            .replace(Regex("""\n{3,}"""), "\n\n")
            .trimEnd('\n')
    }

    @Tool(description = "Read content of any text file in the project. Returns exact file content without line numbers — you can copy code directly for use in replaceInFile. Supports pagination: use offset to start from a specific line, limit to set max lines (default 1000). Large files over 500 lines auto-show first 200 lines with a warning — use grep to search relevant sections first.")
    fun readFile(
        @ToolParam(description = "File path relative to project root, e.g. 'app/src/main.kt' or 'build.gradle.kts'") path: String,
        @ToolParam(description = "Starting line number (1-based). Default 1.") offset: Int = 1,
        @ToolParam(description = "Maximum number of lines to read. Default 1000.") limit: Int = 1000,
    ): String = traceTool("readFile", "path" to path, "offset" to offset, "limit" to limit) {
        Log.d("AIToolSet", "readFile: path=$path offset=$offset limit=$limit")
        FileLogger.d("AIToolSet", "readFile: path=$path offset=$offset limit=$limit")
        val raw = readFileRaw(path)
        if (raw == null) {
            val msg = if (fileManager == null) "No project folder is open." else "File not found: $path"
            FileLogger.w("AIToolSet", "readFile failed: $msg")
            return err(msg)
        }
        val allLines = raw.lines()
        val totalLines = allLines.size

        if (totalLines == 0) return ok("File $path is empty.")

        // 上下文溢出保护：大文件且未明确指定 limit 时自动截断
        val effectiveLimit = if (limit >= MAX_AUTO_LINES && totalLines > TRUNCATE_WARNING_LINES) {
            if (offset <= 1) MAX_AUTO_LINES.coerceAtMost(limit) else limit
        } else limit

        val startIdx = (offset - 1).coerceIn(0, totalLines)
        val endIdx = (startIdx + effectiveLimit).coerceAtMost(totalLines)
        if (startIdx >= totalLines) {
            return err("File $path has $totalLines lines. Cannot start at line $offset.")
        }
        val msg = buildString {
            if (startIdx > 0 || endIdx < totalLines) {
                appendLine("// lines ${startIdx + 1}-$endIdx of $totalLines")
            }
            allLines.subList(startIdx, endIdx).forEach { appendLine(it) }
            if (endIdx < totalLines) {
                appendLine()
                appendLine("// Warning: File has $totalLines lines — only showing first ${endIdx - startIdx}.")
                appendLine("// Use grep to search by content, or readFile with offset/limit for pagination.")
            }
        }.trimEnd()
        FileLogger.d("AIToolSet", "readFile: returned ${msg.lines().size} lines for $path")
        return msg
    }

    @Tool(description = "Create a NEW file. Set overwrite=true to overwrite existing file. When overwrite=false (default) and file exists, returns error with instructions to use replaceInFile instead.")
    fun writeFile(
        @ToolParam(description = "File path relative to project root, e.g. 'src/App.kt'") path: String,
        @ToolParam(description = "The complete text content to write to the file") content: String,
        @ToolParam(description = "Set to true to overwrite existing file. Default false — refuses if file exists.") overwrite: Boolean = false,
    ): String = traceTool("writeFile", "path" to path, "content" to content, "overwrite" to overwrite) {
        Log.d("AIToolSet", "writeFile: path=$path contentLen=${content.length} overwrite=$overwrite")
        FileLogger.d("AIToolSet", "writeFile: path=$path contentLen=${content.length} overwrite=$overwrite")
        if (content.isEmpty()) {
            val msg = "Write refused — content is empty."
            FileLogger.w("AIToolSet", "writeFile rejected: empty content")
            return err(msg)
        }
        val fm = fileManager ?: run {
            FileLogger.w("AIToolSet", "writeFile: no project folder open")
            return err("No project folder is open.")
        }
        val resolvedPath = resolvePathOrAbsolute(path)

        if (!overwrite && fm.exists(resolvedPath)) {
            val msg = ("Write refused — $resolvedPath already exists.\n" +
                "Write is for creating NEW files only.\n" +
                "To modify an existing file, use replaceInFile or batchReplaceInFile with the exact code block.\n" +
                "Read the file first if you don't know its current content.\n" +
                "To overwrite, set overwrite=true.")
            FileLogger.w("AIToolSet", "writeFile blocked by write-guard: $resolvedPath exists")
            return err(msg)
        }
        return try {
            val result = fm.writeFile(resolvedPath, content)
            if (!result.startsWith("Failed") && !result.startsWith("No project")) {
                FileOperationEvents.notify(resolvedPath, "create")
                FileLogger.d("AIToolSet", "writeFile succeeded: $result")
                ok(result)
            } else {
                FileLogger.w("AIToolSet", "writeFile failed: $result")
                err(result)
            }
        } catch (e: Exception) {
            val m = "Write failed: ${e.message ?: "unknown error"}"
            FileLogger.e("AIToolSet", "writeFile exception: $m", e)
            err(m)
        }
    }

    // 代码编辑工具 - 类似 SearchReplace，只修改指定内容
    // 这是唯一推荐的编辑方式：提供 old_string（要查找的代码块）和 new_string（新代码块）
    @Tool(description = "Edit an existing file by replacing a specific code block. Provide the exact code to find (old_string) and the replacement (new_string). The old_string must be unique in the file - include enough context (function signature, class name, etc.) to ensure uniqueness. Optionally limit search to a line range (lineStart/lineEnd, 1-based). Returns OK with a summary of what was replaced.")
    fun replaceInFile(
        @ToolParam(description = "File path relative to project root, e.g. 'src/MainActivity.kt'") path: String,
        @ToolParam(description = "The exact code block to find. Must be unique in the file. Include function signature or class definition for uniqueness.") old_string: String,
        @ToolParam(description = "The new code block to replace with.") new_string: String,
        @ToolParam(description = "Limit search to lines starting from this line (1-based). 0 = search entire file.") lineStart: Int = 0,
        @ToolParam(description = "Limit search to lines up to this line (1-based). 0 = search entire file.") lineEnd: Int = 0,
    ): String = traceTool("replaceInFile", "path" to path, "old_string" to old_string, "new_string" to new_string, "lineStart" to lineStart, "lineEnd" to lineEnd) {
        val fm = fileManager ?: run {
            FileLogger.w("AIToolSet", "replaceInFile: no project folder open")
            return err("No project folder is open.")
        }
        val resolvedPath = resolvePathOrAbsolute(path)
        return try {
            Log.d("AIToolSet", "replaceInFile: path=$resolvedPath oldLen=${old_string.length} newLen=${new_string.length} lineStart=$lineStart lineEnd=$lineEnd")
            FileLogger.d("AIToolSet", "replaceInFile: path=$resolvedPath oldLen=${old_string.length} newLen=${new_string.length} lineStart=$lineStart lineEnd=$lineEnd")
            val fullOriginal = readFileRaw(resolvedPath)
            if (fullOriginal == null) {
                Log.w("AIToolSet", "replaceInFile: file not found: $resolvedPath")
                FileLogger.w("AIToolSet", "replaceInFile: file not found: $resolvedPath")
                return err("Cannot replace: file not found. Use writeFile to create first.")
            }
            // 行范围过滤：仅搜索指定行范围内的内容
            val allLines = fullOriginal.lines()
            val hasRange = lineStart > 0 || lineEnd > 0
            val start = if (hasRange) (lineStart - 1).coerceIn(0, allLines.size) else 0
            val end = if (hasRange) {
                if (lineEnd > 0) lineEnd.coerceAtMost(allLines.size) else allLines.size
            } else allLines.size
            if (hasRange && start >= allLines.size) return err("lineStart $lineStart exceeds file length ${allLines.size}")
            // 搜索内容：全文或行范围子集
            val searchContent = if (hasRange) allLines.subList(start, end).joinToString("\n") else fullOriginal
            val displayRange = if (hasRange) " (lines $lineStart-${end})" else ""

            when (val result = CodeEditTool.replace(searchContent, old_string, new_string)) {
                is CodeEditTool.ReplaceResult.Success -> {
                    // 行范围模式：拼接完整文件（前段 + 替换后范围 + 后段）
                    val finalContent = if (hasRange) {
                        val before = allLines.subList(0, start).joinToString("\n")
                        val after = if (end < allLines.size) "\n" + allLines.subList(end, allLines.size).joinToString("\n") else ""
                        val mid = if (before.isEmpty()) result.newText else "\n" + result.newText
                        normalizeBlankLines(before + mid + after)
                    } else {
                        normalizeBlankLines(result.newText)
                    }
                    val writeResult = fm.writeFile(resolvedPath, finalContent)
                    if (writeResult.startsWith("Failed") || writeResult.startsWith("No project")) {
                        FileLogger.e("AIToolSet", "replaceInFile: replace OK but write failed: $writeResult")
                        return err("Replace succeeded but write failed: $writeResult")
                    }
                    FileOperationEvents.notify(resolvedPath, "modify")
                    FileLogger.d("AIToolSet", "replaceInFile succeeded: $resolvedPath")
                    val oldLines = old_string.lines().size
                    val newLines = new_string.lines().size
                    ok("$resolvedPath$displayRange — ${result.message}. Changed $oldLines lines → $newLines lines.")
                }
                is CodeEditTool.ReplaceResult.Error -> {
                    FileLogger.w("AIToolSet", "replaceInFile: CodeEditTool rejected: ${result.message.take(200)}")
                    err(result.message)
                }
            }
        } catch (e: Exception) {
            Log.e("AIToolSet", "replaceInFile failed: ${e.message}", e)
            FileLogger.e("AIToolSet", "replaceInFile failed: ${e.message}", e)
            err("${e.message}")
        }
    }

    // 批量编辑 — 一次调用替换多处不重叠的内容
    @Tool(description = "Edit an existing file at multiple non-overlapping locations in one call. Provide an array of edits, each with old_string and new_string. All edits are matched against the ORIGINAL file (not sequentially applied). Edits must not overlap or nest. For a single edit, use replaceInFile instead.")
    fun batchReplaceInFile(
        @ToolParam(description = "File path relative to project root, e.g. 'src/MainActivity.kt'") path: String,
        @ToolParam(description = "JSON array of edits: [{\"old_string\":\"exact code\",\"new_string\":\"replacement\"}, ...]. Each old_string must be unique in the file.") editsJson: String,
    ): String = traceTool("batchReplaceInFile", "path" to path, "editsJson" to editsJson) {
        val fm = fileManager ?: run {
            FileLogger.w("AIToolSet", "batchReplaceInFile: no project folder open")
            return@traceTool err("No project folder is open.")
        }
        val resolvedPath = resolvePathOrAbsolute(path)
        return@traceTool try {
            Log.d("AIToolSet", "batchReplaceInFile: path=$resolvedPath editsLen=${editsJson.length}")
            FileLogger.d("AIToolSet", "batchReplaceInFile: path=$resolvedPath editsLen=${editsJson.length}")
            val original = readFileRaw(resolvedPath)
            if (original == null) {
                Log.w("AIToolSet", "batchReplaceInFile: file not found: $resolvedPath")
                FileLogger.w("AIToolSet", "batchReplaceInFile: file not found: $resolvedPath")
                return err("Cannot edit: file not found. Use writeFile to create first.")
            }

            // 解析 JSON edits 数组
            val jsonArr = org.json.JSONArray(editsJson)
            val edits = mutableListOf<CodeEditTool.Edit>()
            for (i in 0 until jsonArr.length()) {
                val obj = jsonArr.getJSONObject(i)
                val oldStr = obj.optString("old_string", "") ?: ""
                val newStr = obj.optString("new_string", "") ?: ""
                if (oldStr.isEmpty()) return err("Edit #${i + 1}: missing old_string")
                edits.add(CodeEditTool.Edit(oldStr, newStr))
            }

            if (edits.isEmpty()) return err("No edits provided.")

            when (val result = CodeEditTool.batchReplace(original, edits)) {
                is CodeEditTool.ReplaceResult.Success -> {
                    val newContent = normalizeBlankLines(result.newText)
                    val writeResult = fm.writeFile(resolvedPath, newContent)
                    if (writeResult.startsWith("Failed") || writeResult.startsWith("No project")) {
                        FileLogger.e("AIToolSet", "batchReplaceInFile: replace OK but write failed: $writeResult")
                        return err("Edit succeeded but write failed: $writeResult")
                    }
                    FileOperationEvents.notify(resolvedPath, "modify")
                    FileLogger.d("AIToolSet", "batchReplaceInFile succeeded: $resolvedPath")
                    ok("$resolvedPath — ${edits.size} edits applied. ${result.message}")
                }
                is CodeEditTool.ReplaceResult.Error -> {
                    FileLogger.w("AIToolSet", "batchReplaceInFile: rejected: ${result.message.take(200)}")
                    err(result.message)
                }
            }
        } catch (e: org.json.JSONException) {
            val m = "Invalid edits JSON: ${e.message}"
            Log.e("AIToolSet", "batchReplaceInFile: $m")
            FileLogger.e("AIToolSet", "batchReplaceInFile: $m")
            err(m)
        } catch (e: Exception) {
            Log.e("AIToolSet", "batchReplaceInFile failed: ${e.message}", e)
            FileLogger.e("AIToolSet", "batchReplaceInFile failed: ${e.message}", e)
            err("${e.message}")
        }
    }

    @Tool(description = "Delete a file or directory in the project. Use with caution — this permanently removes files.")
    fun deleteFile(
        @ToolParam(description = "File or directory path relative to project root, e.g. 'src/OldFile.kt' or 'temp/'") path: String,
    ): String = traceTool("deleteFile", "path" to path) {
        val resolvedPath = resolvePathOrAbsolute(path)
        Log.d("AIToolSet", "deleteFile: path=$resolvedPath")
        FileLogger.d("AIToolSet", "deleteFile: path=$resolvedPath")
        val fm = fileManager ?: run {
            FileLogger.w("AIToolSet", "deleteFile: no project folder open")
            return err("No project folder is open.")
        }
        val result = fm.deleteFile(resolvedPath)
        return if (result.startsWith("Failed") || result.startsWith("No project")) {
            FileLogger.w("AIToolSet", "deleteFile failed: $result")
            err(result)
        } else {
            FileOperationEvents.notify(resolvedPath, "delete")
            FileLogger.d("AIToolSet", "deleteFile succeeded: $result")
            ok(result)
        }
    }


    @Tool(description = "Create a new directory. Automatically creates parent directories as needed. For creating nested paths, provide the full path.")
    fun createDirectory(
        @ToolParam(description = "Directory path relative to project root, e.g. 'src/utils' or 'assets/images'") path: String,
    ): String = traceTool("createDirectory", "path" to path) {
        val resolvedPath = resolvePathOrAbsolute(path)
        Log.d("AIToolSet", "createDirectory: path=$resolvedPath")
        FileLogger.d("AIToolSet", "createDirectory: path=$resolvedPath")
        val fm = fileManager ?: run {
            FileLogger.w("AIToolSet", "createDirectory: no project folder open")
            return err("No project folder is open.")
        }
        val result = fm.createDirectory(resolvedPath)
        return if (result.startsWith("Failed") || result.startsWith("No project")) {
            FileLogger.w("AIToolSet", "createDirectory failed: $result")
            err(result)
        } else {
            FileOperationEvents.notify(resolvedPath, "create")
            FileLogger.d("AIToolSet", "createDirectory succeeded: $result")
            ok(result)
        }
    }

    @Tool(description = "Run a shell command in the project directory.")
    fun runCommand(
        @ToolParam(description = "The command to execute, e.g. 'ls -la'") command: String,
    ): String {
        Log.d("AIToolSet", "runCommand: $command")
        FileLogger.d("AIToolSet", "runCommand: $command")
        return try {
            val dir = resolveProjectDir()
            val parts = parseCommandLine(command)
            val pb = ProcessBuilder(parts).directory(dir).redirectErrorStream(true)
            val proc = pb.start()
            val text = proc.inputStream.bufferedReader().readText()
            proc.waitFor(30, java.util.concurrent.TimeUnit.SECONDS)
            val exitCode = proc.exitValue()
            proc.destroy()
            if (text.isBlank()) ok("Command completed with no output (exit code $exitCode).")
            else ok("Exit code: $exitCode\n$text".take(5000))
        } catch (e: Exception) {
            Log.e("AIToolSet", "runCommand failed: ${e.message}", e)
            FileLogger.e("AIToolSet", "runCommand failed: ${e.message}", e)
            err("${e.message}")
        }
    }

    // 从 SAF URI 解析项目根目录的真实文件路径，失败则回退到内部 workspace
    private fun resolveProjectDir(): File {
        // 尝试从 fileManager 的 projectUri 提取真实路径
        val safUri = projectUri
        if (safUri != null) {
            try {
                val docId = android.provider.DocumentsContract.getTreeDocumentId(safUri)
                if (docId.startsWith("primary:")) {
                    val realPath = docId.removePrefix("primary:")
                    val dir = File(android.os.Environment.getExternalStorageDirectory(), realPath)
                    if (dir.isDirectory) return dir
                }
                // 部分设备或二级存储（SD 卡）可能包含 /tree/ 路径格式
                if (docId.startsWith("/tree/")) {
                    val realPath = docId.removePrefix("/tree/").substringBefore(':')
                    val dir = File(realPath)
                    if (dir.isDirectory) return dir
                }
            } catch (_: Exception) { }
        }
        // 回退到应用内部目录
        return File(context.filesDir, "workspace").also { it.mkdirs() }
    }

    /**
     * 解析命令行字符串，尊重引号分组。
     * 支持双引号和单引号包裹的参数（如 app/build.gradle.kts）。
     */
    private fun parseCommandLine(input: String): List<String> {
        val args = mutableListOf<String>()
        val current = StringBuilder()
        var inQuote: Char? = null
        var escaped = false
        for (c in input) {
            when {
                escaped -> { current.append(c); escaped = false }
                c == '\\' -> escaped = true
                inQuote != null && c == inQuote -> inQuote = null
                inQuote == null && (c == '"' || c == '\'') -> inQuote = c
                inQuote == null && c.isWhitespace() -> {
                    if (current.isNotEmpty()) { args.add(current.toString()); current.clear() }
                }
                else -> current.append(c)
            }
        }
        if (current.isNotEmpty()) args.add(current.toString())
        return args
    }

    @Tool(description = "Search the internet for current information. Use when you need up-to-date data or cannot answer from knowledge.")
    fun searchWeb(
        @ToolParam(description = "Search query keywords, concise and specific") query: String,
    ): String {
        if (query.isBlank()) return err("Search query is empty.")
        Log.d("AIToolSet", "searchWeb: query=$query")
        FileLogger.d("AIToolSet", "searchWeb: query=$query")
        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val doc = Jsoup.connect("https://html.duckduckgo.com/html/?q=$encoded")
                .userAgent("Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                .timeout(15000)
                .get()
            val results = doc.select(".result").mapNotNull { el ->
                val titleEl = el.selectFirst(".result__title a") ?: return@mapNotNull null
                val title = titleEl.text().trim()
                val snippet = el.selectFirst(".result__snippet")?.text()?.trim() ?: ""
                val link = titleEl.attr("href")
                if (title.isBlank()) null else Triple(title, snippet, link)
            }.take(8)
            if (results.isEmpty()) return err("No search results for: $query")
            FileLogger.d("AIToolSet", "searchWeb: found ${results.size} results")
            results.joinToString("\n---\n") { (title, snippet, link) ->
                buildString {
                    appendLine("**$title**")
                    if (snippet.isNotBlank()) appendLine(snippet)
                    if (link.isNotBlank()) appendLine(link)
                }
            }
        } catch (e: Exception) {
            Log.e("AIToolSet", "searchWeb failed: ${e.message}", e)
            FileLogger.e("AIToolSet", "searchWeb failed: ${e.message}", e)
            err("${e.message}")
        }
    }

    @Tool(description = "Read build lint or compilation errors from the project. Runs gradle lint if needed. Returns file paths and line numbers when available.")
    fun readLints(): String {
        Log.d("AIToolSet", "readLints")
        FileLogger.d("AIToolSet", "readLints")
        return try {
            val buildDir = File(context.filesDir, "workspace")
            // 1. Check lint XML reports
            val lintFiles = listOf(
                Pair(File(buildDir, "app/build/reports/lint-results.xml"), "lint-results.xml"),
                Pair(File(buildDir, "app/build/reports/lint-results-release.xml"), "lint-results-release.xml"),
            )
            for ((f, name) in lintFiles) {
                if (f.exists() && f.length() > 0) {
                    val text = f.readText()
                    val issues = mutableListOf<String>()
                    val issueRegex = Regex("""<issue\s+[^>]*severity="(Error|Fatal)"[^>]*>""", RegexOption.IGNORE_CASE)
                    for (match in issueRegex.findAll(text).take(30)) {
                        val seg = text.substring(match.range.first, (match.range.first + 2000).coerceAtMost(text.length))
                        val id = Regex("""id="([^"]+)"""").find(match.value)?.groupValues[1] ?: "?"
                        val msg = Regex("""<message>([^<]+)</message>""").find(seg)?.groupValues[1] ?: ""
                        val location = Regex("""<location[^>]*file="([^"]+)"[^>]*(?:line="(\d+)")?""")
                            .find(seg)?.let { m ->
                                val file = m.groupValues[1].substringAfterLast("/")
                                val line = m.groupValues[2]
                                if (line.isNotBlank()) "($file:$line)" else "($file)"
                            } ?: ""
                        issues.add("• $id$location: $msg")
                    }
                    if (issues.isNotEmpty()) {
                        FileLogger.d("AIToolSet", "readLints: found ${issues.size} issues in $name")
                        return ok("${issues.size} lint errors in $name:\n${issues.joinToString("\n")}")
                    }
                }
            }
            // 2. Check problem reports
            val problemFiles = listOf(
                File(buildDir, "build/reports/problems/problems-report.html"),
                File(buildDir, "app/build/reports/problems/problems-report.html"),
            )
            for (f in problemFiles) {
                if (f.exists() && f.length() > 0) {
                    val html = f.readText()
                    val errors = Regex("""<li[^>]*data-type="error"[^>]*>(.*?)</li>""", RegexOption.DOT_MATCHES_ALL)
                        .findAll(html).take(15).map {
                            it.groupValues[1].replace(Regex("<[^>]*>"), "").trim()
                        }.filter { it.isNotBlank() }.toList()
                    if (errors.isNotEmpty()) {
                        FileLogger.d("AIToolSet", "readLints: found ${errors.size} problems in ${f.name}")
                        return ok("${errors.size} compilation problems in ${f.name}:\n${errors.joinToString("\n")}")
                    }
                }
            }
            // 3. Run gradle lint if no cached reports
            val pb = if (isWindows()) ProcessBuilder("cmd", "/c", "gradlew lint")
                else ProcessBuilder("sh", "-c", "./gradlew lint")
            pb.directory(buildDir).redirectErrorStream(true)
            val proc = pb.start()
            val out = proc.inputStream.bufferedReader().readText()
            proc.waitFor(60, java.util.concurrent.TimeUnit.SECONDS)
            proc.destroy()
            // Parse errors with file:line format
            val fileLineErrors = out.lines().filter {
                it.contains("ERROR") && it.contains(".kt:")
            }.map { it.trim() }.take(30)
            if (fileLineErrors.isNotEmpty()) {
                FileLogger.d("AIToolSet", "readLints: found ${fileLineErrors.size} compile errors")
                return ok("${fileLineErrors.size} compile errors:\n${fileLineErrors.joinToString("\n")}")
            }
            val genericErrors = out.lines().filter { it.contains("ERROR") || it.contains("error:") }.take(20)
            if (genericErrors.isNotEmpty()) {
                return ok("${genericErrors.size} errors from gradle lint:\n${genericErrors.joinToString("\n")}")
            }
            ok("No lint errors found.")
        } catch (e: Exception) {
            Log.e("AIToolSet", "readLints failed: ${e.message}", e)
            FileLogger.e("AIToolSet", "readLints failed: ${e.message}", e)
            err("Lint scan failed: ${e.message}")
        }
    }

    private fun isWindows(): Boolean = System.getProperty("os.name")?.lowercase()?.contains("win") == true

    @Tool(description = "Search file contents using regex pattern. Returns matching files with line numbers and context. Use for finding code patterns, function definitions, imports, etc.")
    fun grep(
        @ToolParam(description = "Regex pattern to search for, e.g. 'fun\\s+\\w+' to find function definitions") pattern: String,
        @ToolParam(description = "File extension filter, e.g. 'kt' for Kotlin files only. Leave empty for all text files.") extension: String = "",
        @ToolParam(description = "Glob pattern to filter files, e.g. '*.kt' or 'src/**/*.java'") glob: String = "",
        @ToolParam(description = "Case insensitive search, default true") ignoreCase: Boolean = true,
        @ToolParam(description = "Number of context lines to show before/after matches, default 2") contextLines: Int = 2,
    ): String {
        Log.d("AIToolSet", "grep: pattern=$pattern ext=$extension glob=$glob ignoreCase=$ignoreCase context=$contextLines")
        FileLogger.d("AIToolSet", "grep: pattern=$pattern ext=$extension glob=$glob ignoreCase=$ignoreCase context=$contextLines")
        val result = fileManager?.grep(pattern, extension, glob, ignoreCase, contextLines)
            ?: return err("No project folder is open.")
        return if (result.startsWith("Failed") || result.startsWith("No project")) {
            FileLogger.w("AIToolSet", "grep failed: ${result.take(200)}")
            err(result)
        } else {
            FileLogger.d("AIToolSet", "grep: found ${result.lines().size} lines of results")
            result  // grep already returns structured content
        }
    }

    @Tool(description = "Search codebase by meaning using semantic similarity. Converts query into a search vector and finds related code across the project. Use for exploring unfamiliar code or finding implementations by behavior (e.g. 'where is user authentication?' or 'how does error handling work?'). Prefer over grep when you don't know exact terms.")
    fun searchCodebase(
        @ToolParam(description = "Natural language query describing what you're looking for, e.g. 'Where is user authentication implemented?' or 'How does error handling work?'") query: String,
        @ToolParam(description = "Specific directories to search within, relative to project root. Leave empty to search entire project.") targetDirectories: String = "",
    ): String {
        Log.d("AIToolSet", "searchCodebase: query=$query dirs=$targetDirectories")
        FileLogger.d("AIToolSet", "searchCodebase: query=$query dirs=$targetDirectories")
        val result = fileManager?.searchCodebase(query, targetDirectories)
            ?: return err("No project folder is open.")
        return if (result.startsWith("Failed") || result.startsWith("No project")) {
            FileLogger.w("AIToolSet", "searchCodebase failed: ${result.take(200)}")
            err(result)
        } else {
            FileLogger.d("AIToolSet", "searchCodebase: found ${result.lines().size} lines of results")
            result
        }
    }

    // === 文件名搜索（Glob） ===
    @Tool(description = "Find files by name pattern using glob syntax (e.g. '*.kt', '**/*.xml', 'Main*'). Searches recursively from project root. Use this when you know the file name or extension but not the full path. For regex-based content search, use grep instead.")
    fun glob(
        @ToolParam(description = "Glob pattern to match file names. Examples: '*.kt' finds all Kotlin files, '**/*.xml' finds all XML files, 'Main*' finds files starting with 'Main', '**/build/**' finds all files in build directories.") pattern: String,
        @ToolParam(description = "Maximum number of results to return. Default 100.") maxResults: Int = 100,
    ): String {
        Log.d("AIToolSet", "glob: pattern=$pattern max=$maxResults")
        FileLogger.d("AIToolSet", "glob: pattern=$pattern max=$maxResults")
        val fm = fileManager ?: return err("No project folder is open.")

        val regex = try {
            val r = pattern
                .replace(".", "\\.")
                .replace("**/", "(.*?/)?")
                .replace("*", "[^/]*")
                .replace("?", "[^/]")
            Regex(r)
        } catch (e: Exception) {
            return err("Invalid glob pattern: ${e.message}")
        }

        // Collect all files recursively
        val matches = mutableListOf<String>()
        var dirsToScan = ArrayDeque<String>()
        dirsToScan.add("")

        while (dirsToScan.isNotEmpty() && matches.size < maxResults) {
            val dir = dirsToScan.removeFirst()
            val nodes = fm.listFilesAsNodes(dir)
            if (nodes.isEmpty() && dir.isNotEmpty()) continue
            for (node in nodes) {
                if (matches.size >= maxResults) break
                if (node.isDirectory) {
                    dirsToScan.add(node.path)
                } else if (regex.matches(node.name)) {
                    matches.add(node.path)
                }
            }
        }

        return if (matches.isEmpty()) {
            ok("No files matching '$pattern' found.")
        } else {
            ok("${matches.size} files matching '$pattern':\n" + matches.joinToString("\n"))
        }
    }

    /**
     * 列出 `buildOpenAIToolsJson` 中声明的所有工具名称（用于系统提示注入）
     */
    fun toolNames(): List<String> = listOf(
        "readFile", "writeFile", "replaceInFile", "batchReplaceInFile",
        "deleteFile", "createDirectory", "listFiles",
        "grep", "searchCodebase", "glob",
        "runCommand", "searchWeb", "readLints",
        "searchConversationMemory", "getRecentConversationMemory",
    )

    // === 对话历史记忆工具（仅本地模型可见） ===

    @Tool(description = "Search past conversation history by semantic meaning. Use when you need to recall what was discussed earlier in the conversation.")
    fun searchConversationMemory(
        @ToolParam(description = "What to search for, e.g. 'file structure' or 'error fix'") query: String,
    ): String {
        val mem = conversationMemory ?: return "对话记忆系统未加载。"
        Log.d("AIToolSet", "searchConversationMemory: query=$query")
        return mem.searchFormatted(query)
    }

    @Tool(description = "Get summaries of the most recent conversation turns.")
    fun getRecentConversationMemory(
        @ToolParam(description = "Number of recent entries (default 5, max 20)") count: Int = 5,
    ): String {
        val mem = conversationMemory ?: return "对话记忆系统未加载。"
        Log.d("AIToolSet", "getRecentConversationMemory: count=$count")
        return mem.recentFormatted(count.coerceIn(1, 20))
    }
}