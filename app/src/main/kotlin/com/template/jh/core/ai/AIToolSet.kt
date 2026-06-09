package com.template.jh.core.ai

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.ai.edge.litertlm.Tool
import com.google.ai.edge.litertlm.ToolParam
import com.google.ai.edge.litertlm.ToolSet
import com.template.jh.core.editor.CodeEditTool
import com.template.jh.core.storage.FileManager
import com.template.jh.core.utils.FileLogger
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class AIToolSet(
    private val context: Context,
    private val fileManager: FileManager? = null
) : ToolSet {

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

    @Tool(description = "List files and directories in the project folder. Returns directory contents with file sizes. Use absolute path (like /storage/emulated/0/...) or relative path.")
    fun listFiles(
        @ToolParam(description = "Subdirectory path. Use absolute path (e.g. /storage/emulated/0/MyProject) or relative path from project root. Leave empty to list project root.") subPath: String = "",
    ): String {
        Log.d("AIToolSet", "listFiles: subPath=$subPath")
        FileLogger.d("AIToolSet", "listFiles: subPath=$subPath")
        val relPath = resolvePathOrAbsolute(subPath)
        val result = fileManager?.listFiles(relPath) ?: "No project folder is open."
        if (result.startsWith("Failed") || result.startsWith("No project")) {
            FileLogger.w("AIToolSet", "listFiles failed: $result")
        }
        return result
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
            tools.put(buildSearchCodebaseTool())
            tools.put(buildRunCommandTool())
            tools.put(buildSearchWebTool())
            tools.put(buildReadLintsTool())
            return tools.toString()
        }

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
        private fun buildWriteFileTool() = toolDef("writeFile", "Create new file only (refuses overwrite)", listOf("path", "content"),
            "path" to p("string", "File path — absolute or relative to project root"),
            "content" to p("string", "Complete file content"),
        )
        private fun buildReplaceInFileTool() = toolDef("replaceInFile", "Edit file by replacing exact code block", listOf("path", "old_string", "new_string"),
            "path" to p("string", "File path — absolute or relative to project root"),
            "old_string" to p("string", "Exact code block to find (unique in file)"),
            "new_string" to p("string", "Replacement code block"),
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
        private fun buildRunCommandTool() = toolDef("runCommand", "Execute shell command (adb, git, gradle)", listOf("command"),
            "command" to p("string", "Command to execute, e.g. 'git status'"),
        )
        private fun buildSearchWebTool() = toolDef("searchWeb", "Search internet for current information", listOf("query"),
            "query" to p("string", "Concise search query"),
        )
        private fun buildReadLintsTool() = toolDef("readLints", "Read build/lint errors")
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
    ): String {
        Log.d("AIToolSet", "readFile: path=$path offset=$offset limit=$limit")
        FileLogger.d("AIToolSet", "readFile: path=$path offset=$offset limit=$limit")
        val raw = readFileRaw(path)
        if (raw == null) {
            val err = if (fileManager == null) "No project folder is open." else "File not found: $path"
            FileLogger.w("AIToolSet", "readFile failed: $err")
            return err
        }
        val allLines = raw.lines()
        val totalLines = allLines.size

        // 上下文溢出保护：大文件且未明确指定 limit 时自动截断
        val effectiveLimit = if (limit >= MAX_AUTO_LINES && totalLines > TRUNCATE_WARNING_LINES) {
            if (offset <= 1) MAX_AUTO_LINES.coerceAtMost(limit) else limit
        } else limit

        val startIdx = (offset - 1).coerceIn(0, totalLines)
        val endIdx = (startIdx + effectiveLimit).coerceAtMost(totalLines)
        if (startIdx >= totalLines) {
            return "File $path has $totalLines lines. Cannot start at line $offset."
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

    @Tool(description = "Create a NEW file only. REFUSES to overwrite existing files — use replaceInFile or batchReplaceInFile to modify existing files.")
    fun writeFile(
        @ToolParam(description = "File path relative to project root, e.g. 'src/App.kt'") path: String,
        @ToolParam(description = "The complete text content to write to the file") content: String,
    ): String {
        Log.d("AIToolSet", "writeFile: path=$path contentLen=${content.length}")
        FileLogger.d("AIToolSet", "writeFile: path=$path contentLen=${content.length}")
        if (content.isEmpty()) {
            val msg = "Write refused — content is empty. To create an empty file, include at least a comment or valid content."
            FileLogger.w("AIToolSet", "writeFile rejected: empty content")
            return msg
        }
        val fm = fileManager ?: run {
            FileLogger.w("AIToolSet", "writeFile: no project folder open")
            return "No project folder is open."
        }
        val resolvedPath = resolvePathOrAbsolute(path)
        // Write-guard: 文件已存在时拒绝写入
        if (fm.exists(resolvedPath)) {
            val msg = ("Write refused — $resolvedPath already exists.\n" +
                "Write is for creating NEW files only.\n" +
                "To modify an existing file, use replaceInFile or batchReplaceInFile with the exact code block.\n" +
                "Read the file first if you don't know its current content.")
            FileLogger.w("AIToolSet", "writeFile blocked by write-guard: $resolvedPath exists")
            return msg
        }
        return try {
            val result = fm.writeFile(resolvedPath, content)
            if (!result.startsWith("Failed") && !result.startsWith("No project")) {
                FileOperationEvents.notify(resolvedPath, "create")
                FileLogger.d("AIToolSet", "writeFile succeeded: $result")
            } else {
                FileLogger.w("AIToolSet", "writeFile failed: $result")
            }
            result
        } catch (e: Exception) {
            val err = "Write failed: ${e.message ?: "unknown error"}"
            FileLogger.e("AIToolSet", "writeFile exception: $err", e)
            err
        }
    }

    // 代码编辑工具 - 类似 SearchReplace，只修改指定内容
    // 这是唯一推荐的编辑方式：提供 old_string（要查找的代码块）和 new_string（新代码块）
    @Tool(description = "Edit an existing file by replacing a specific code block. Provide the exact code to find (old_string) and the replacement (new_string). The old_string must be unique in the file - include enough context (function signature, class name, etc.) to ensure uniqueness.")
    fun replaceInFile(
        @ToolParam(description = "File path relative to project root, e.g. 'src/MainActivity.kt'") path: String,
        @ToolParam(description = "The exact code block to find. Must be unique in the file. Include function signature or class definition for uniqueness.") old_string: String,
        @ToolParam(description = "The new code block to replace with.") new_string: String,
    ): String {
        val fm = fileManager ?: run {
            FileLogger.w("AIToolSet", "replaceInFile: no project folder open")
            return "No project folder is open."
        }
        val resolvedPath = resolvePathOrAbsolute(path)
        return try {
            Log.d("AIToolSet", "replaceInFile: path=$resolvedPath oldLen=${old_string.length} newLen=${new_string.length}")
            FileLogger.d("AIToolSet", "replaceInFile: path=$resolvedPath oldLen=${old_string.length} newLen=${new_string.length}")
            val original = readFileRaw(resolvedPath)
            if (original == null) {
                Log.w("AIToolSet", "replaceInFile: file not found: $resolvedPath")
                FileLogger.w("AIToolSet", "replaceInFile: file not found: $resolvedPath")
                return "Cannot replace: file not found. Use writeFile to create first."
            }

            when (val result = CodeEditTool.replace(original, old_string, new_string)) {
                is CodeEditTool.ReplaceResult.Success -> {
                    val newContent = normalizeBlankLines(result.newText)
                    val writeResult = fm.writeFile(resolvedPath, newContent)
                    if (writeResult.startsWith("Failed") || writeResult.startsWith("No project")) {
                        FileLogger.e("AIToolSet", "replaceInFile: replace OK but write failed: $writeResult")
                        return "Replace succeeded but write failed: $writeResult"
                    }
                    FileOperationEvents.notify(resolvedPath, "modify")
                    FileLogger.d("AIToolSet", "replaceInFile succeeded: $resolvedPath")
                    "Replace succeeded: $resolvedPath. ${result.message}"
                }
                is CodeEditTool.ReplaceResult.Error -> {
                    FileLogger.w("AIToolSet", "replaceInFile: CodeEditTool rejected: ${result.message.take(200)}")
                    "Replace failed:\n${result.message}"
                }
            }
        } catch (e: Exception) {
            Log.e("AIToolSet", "replaceInFile failed: ${e.message}", e)
            FileLogger.e("AIToolSet", "replaceInFile failed: ${e.message}", e)
            "Replace failed: ${e.message}"
        }
    }

    // 批量编辑 — 一次调用替换多处不重叠的内容
    @Tool(description = "Edit an existing file at multiple non-overlapping locations in one call. Provide an array of edits, each with old_string and new_string. All edits are matched against the ORIGINAL file (not sequentially applied). Edits must not overlap or nest. For a single edit, use replaceInFile instead.")
    fun batchReplaceInFile(
        @ToolParam(description = "File path relative to project root, e.g. 'src/MainActivity.kt'") path: String,
        @ToolParam(description = "JSON array of edits: [{\"old_string\":\"exact code\",\"new_string\":\"replacement\"}, ...]. Each old_string must be unique in the file.") editsJson: String,
    ): String {
        val fm = fileManager ?: run {
            FileLogger.w("AIToolSet", "batchReplaceInFile: no project folder open")
            return "No project folder is open."
        }
        val resolvedPath = resolvePathOrAbsolute(path)
        return try {
            Log.d("AIToolSet", "batchReplaceInFile: path=$resolvedPath editsLen=${editsJson.length}")
            FileLogger.d("AIToolSet", "batchReplaceInFile: path=$resolvedPath editsLen=${editsJson.length}")
            val original = readFileRaw(resolvedPath)
            if (original == null) {
                Log.w("AIToolSet", "batchReplaceInFile: file not found: $resolvedPath")
                FileLogger.w("AIToolSet", "batchReplaceInFile: file not found: $resolvedPath")
                return "Cannot edit: file not found. Use writeFile to create first."
            }

            // 解析 JSON edits 数组
            val jsonArr = org.json.JSONArray(editsJson)
            val edits = mutableListOf<CodeEditTool.Edit>()
            for (i in 0 until jsonArr.length()) {
                val obj = jsonArr.getJSONObject(i)
                val oldStr = obj.optString("old_string", "") ?: ""
                val newStr = obj.optString("new_string", "") ?: ""
                if (oldStr.isEmpty()) return "Edit #${i + 1}: missing old_string"
                edits.add(CodeEditTool.Edit(oldStr, newStr))
            }

            if (edits.isEmpty()) return "No edits provided."

            when (val result = CodeEditTool.batchReplace(original, edits)) {
                is CodeEditTool.ReplaceResult.Success -> {
                    val newContent = normalizeBlankLines(result.newText)
                    val writeResult = fm.writeFile(resolvedPath, newContent)
                    if (writeResult.startsWith("Failed") || writeResult.startsWith("No project")) {
                        FileLogger.e("AIToolSet", "batchReplaceInFile: replace OK but write failed: $writeResult")
                        return "Edit succeeded but write failed: $writeResult"
                    }
                    FileOperationEvents.notify(resolvedPath, "modify")
                    FileLogger.d("AIToolSet", "batchReplaceInFile succeeded: $resolvedPath")
                    "Batch edit succeeded: $resolvedPath. ${result.message}"
                }
                is CodeEditTool.ReplaceResult.Error -> {
                    FileLogger.w("AIToolSet", "batchReplaceInFile: rejected: ${result.message.take(200)}")
                    "Batch edit failed:\n${result.message}"
                }
            }
        } catch (e: org.json.JSONException) {
            val err = "Invalid edits JSON: ${e.message}"
            Log.e("AIToolSet", "batchReplaceInFile: $err")
            FileLogger.e("AIToolSet", "batchReplaceInFile: $err")
            "Batch edit failed: $err"
        } catch (e: Exception) {
            Log.e("AIToolSet", "batchReplaceInFile failed: ${e.message}", e)
            FileLogger.e("AIToolSet", "batchReplaceInFile failed: ${e.message}", e)
            "Batch edit failed: ${e.message}"
        }
    }

    @Tool(description = "Delete a file or directory in the project. Use with caution - this permanently removes files.")
    fun deleteFile(
        @ToolParam(description = "File or directory path relative to project root, e.g. 'src/OldFile.kt' or 'temp/'") path: String,
    ): String {
        val resolvedPath = resolvePathOrAbsolute(path)
        Log.d("AIToolSet", "deleteFile: path=$resolvedPath")
        FileLogger.d("AIToolSet", "deleteFile: path=$resolvedPath")
        val fm = fileManager ?: run {
            FileLogger.w("AIToolSet", "deleteFile: no project folder open")
            return "No project folder is open."
        }
        val result = fm.deleteFile(resolvedPath)
        if (result.startsWith("Failed") || result.startsWith("No project")) {
            FileLogger.w("AIToolSet", "deleteFile failed: $result")
        } else {
            FileOperationEvents.notify(resolvedPath, "delete")
            FileLogger.d("AIToolSet", "deleteFile succeeded: $result")
        }
        return result
    }

    @Tool(description = "Batch delete multiple files or directories in one call. All paths resolved relative to project root. Use when you need to clean up several files at once.")
    fun batchDeleteFile(
        @ToolParam(description = "JSON array of file/directory paths relative to project root, e.g. '[\"src/temp.kt\",\"old/\"]'") pathsJson: String,
    ): String {
        Log.d("AIToolSet", "batchDeleteFile: $pathsJson")
        FileLogger.d("AIToolSet", "batchDeleteFile: $pathsJson")
        val fm = fileManager ?: return "No project folder is open."
        return try {
            val paths = org.json.JSONArray(pathsJson)
            val results = mutableListOf<String>()
            for (i in 0 until paths.length()) {
                val rawPath = paths.getString(i)
                val path = resolvePathOrAbsolute(rawPath)
                val result = fm.deleteFile(path)
                if (result.startsWith("Failed") || result.startsWith("No project")) {
                    results.add("$path: $result")
                } else {
                    FileOperationEvents.notify(path, "delete")
                    results.add("$path: Deleted")
                }
            }
            "Batch delete results:\n" + results.joinToString("\n")
        } catch (e: org.json.JSONException) {
            val err = "Invalid paths JSON: ${e.message}"
            Log.e("AIToolSet", "batchDeleteFile: $err")
            FileLogger.e("AIToolSet", "batchDeleteFile: $err")
            "Batch delete failed: $err"
        } catch (e: Exception) {
            Log.e("AIToolSet", "batchDeleteFile failed: ${e.message}", e)
            FileLogger.e("AIToolSet", "batchDeleteFile failed: ${e.message}", e)
            "Batch delete failed: ${e.message}"
        }
    }

    @Tool(description = "Create a new directory. Automatically creates parent directories as needed. For creating nested paths, provide the full path.")
    fun createDirectory(
        @ToolParam(description = "Directory path relative to project root, e.g. 'src/utils' or 'assets/images'") path: String,
    ): String {
        val resolvedPath = resolvePathOrAbsolute(path)
        Log.d("AIToolSet", "createDirectory: path=$resolvedPath")
        FileLogger.d("AIToolSet", "createDirectory: path=$resolvedPath")
        val fm = fileManager ?: run {
            FileLogger.w("AIToolSet", "createDirectory: no project folder open")
            return "No project folder is open."
        }
        val result = fm.createDirectory(resolvedPath)
        if (result.startsWith("Failed") || result.startsWith("No project")) {
            FileLogger.w("AIToolSet", "createDirectory failed: $result")
        } else {
            FileOperationEvents.notify(resolvedPath, "create")
            FileLogger.d("AIToolSet", "createDirectory succeeded: $result")
        }
        return result
    }

    @Tool(description = "Run a shell command in the project directory. Use for adb, git, gradle commands.")
    fun runCommand(
        @ToolParam(description = "The command to execute, e.g. 'ls -la' or 'git status'") command: String,
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
            proc.destroy()
            if (text.isBlank()) "Command completed with no output." else text.take(5000)
        } catch (e: Exception) {
            Log.e("AIToolSet", "runCommand failed: ${e.message}", e)
            FileLogger.e("AIToolSet", "runCommand failed: ${e.message}", e)
            "Command failed: ${e.message}"
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
     * 支持双引号和单引号包裹的参数（如 git commit -m "my message"）。
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
        if (query.isBlank()) return "Search query is empty."
        Log.d("AIToolSet", "searchWeb: query=$query")
        FileLogger.d("AIToolSet", "searchWeb: query=$query")
        return try {
            val ddgResult = searchViaDuckDuckGo(query)
            if (ddgResult.isNotBlank()) {
                FileLogger.d("AIToolSet", "searchWeb: DuckDuckGo returned ${ddgResult.lines().size} lines")
                return ddgResult
            }
            val searxResult = searchViaSearxNG(query)
            if (searxResult.isNotBlank() && !searxResult.contains("失败") && !searxResult.contains("无结果")) {
                FileLogger.d("AIToolSet", "searchWeb: SearXNG returned ${searxResult.lines().size} lines")
                return searxResult
            }
            val bingResult = searchViaBing(query)
            if (bingResult.isNotBlank() && !bingResult.contains("失败")) {
                FileLogger.d("AIToolSet", "searchWeb: Bing returned ${bingResult.lines().size} lines")
                return bingResult
            }
            "搜索无结果: $query"
        } catch (e: Exception) {
            Log.e("AIToolSet", "searchWeb failed: ${e.message}", e)
            FileLogger.e("AIToolSet", "searchWeb failed: ${e.message}", e)
            "Search failed: ${e.message}"
        }
    }

    // SearXNG 搜索 - 开源搜索引擎聚合器，稳定性更高
    private fun searchViaSearxNG(query: String): String {
        val encoded = URLEncoder.encode(query, "UTF-8")
        // 多个公共 SearXNG 实例，按可靠性排序
        val instances = listOf(
            "https://search.sapti.me/search?q=$encoded&format=json&language=all",
            "https://search.rhscz.eu/search?q=$encoded&format=json&language=all",
            "https://searx.be/search?q=$encoded&format=json&language=all",
            "https://search.bus-hit.me/search?q=$encoded&format=json&language=all",
        )

        for (url in instances) {
            try {
                val json = fetchUrl(url, timeoutMs = 10000)
                val obj = org.json.JSONObject(json)
                val results = obj.optJSONArray("results")

                if (results != null && results.length() > 0) {
                    val output = mutableListOf<String>()
                    for (i in 0 until minOf(results.length(), 5)) {
                        val item = results.optJSONObject(i) ?: continue
                        val title = item.optString("title", "").trim()
                        val content = item.optString("content", "").trim()
                        val link = item.optString("url", "").trim()
                        if (title.isNotBlank()) {
                            output.add("• $title${if (content.isNotBlank()) "\n  $content" else ""}${if (link.isNotBlank()) "\n  $link" else ""}")
                        }
                    }
                    if (output.isNotEmpty()) {
                        return "Found ${results.length()} results:\n\n${output.joinToString("\n---\n")}"
                    }
                }
            } catch (e: Exception) {
                Log.w("AIToolSet", "SearXNG $url failed: ${e.message}")
                FileLogger.w("AIToolSet", "SearXNG $url failed: ${e.message}")
                continue
            }
        }
        FileLogger.w("AIToolSet", "searchViaSearxNG: all instances failed for query=$query")
        return "SearXNG search failed or returned no results."
    }

    private fun searchViaBing(query: String): String {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = "https://www.bing.com/search?q=$encoded&setmkt=en-US&setlang=en"
        return try {
            val html = fetchUrl(url, timeoutMs = 15000, extraHeaders = mapOf(
                "Cookie" to "MUID=1; MUIDB=1",
                "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8",
            ))
            val results = parseBingResults(html)
            if (results.isNotEmpty()) {
                "Found ${results.size} results:\n\n${results.joinToString("\n---\n") { "• ${it.first}\n  ${it.second}" }}"
            } else {
                "Bing search returned no results."
            }
        } catch (e: Exception) {
            Log.w("AIToolSet", "Bing search failed: ${e.message}")
            FileLogger.w("AIToolSet", "searchViaBing failed: ${e.message}")
            "Bing search failed: ${e.message}"
        }
    }

    private fun searchViaDuckDuckGo(query: String): String {
        val encoded = URLEncoder.encode(query, "UTF-8")
        return try {
            val html = fetchUrl("https://html.duckduckgo.com/html/?q=$encoded", timeoutMs = 15000)
            val titleRe = Regex("""class="result__title"[^>]*>[\s\S]*?<a[^>]*href="([^"]+)"[^>]*>([\s\S]*?)</a>""", RegexOption.DOT_MATCHES_ALL)
            val snippetRe = Regex("""class="result__snippet"[^>]*>([\s\S]*?)</""", RegexOption.DOT_MATCHES_ALL)
            val stitles = titleRe.findAll(html).take(8).map {
                it.groupValues[2].replace(Regex("<[^>]+>"), "").trim() to it.groupValues[1]
            }.toList()
            val snippets = snippetRe.findAll(html).take(8).map {
                it.groupValues[1].replace(Regex("<[^>]+>"), "").trim()
            }.toList()
            if (stitles.isEmpty()) "" else
            stitles.mapIndexed { i, (title, link) ->
                "**$title**\n$link\n${snippets.getOrElse(i) { "" }}"
            }.joinToString("\n\n")
        } catch (e: Exception) {
            Log.w("AIToolSet", "DuckDuckGo failed: ${e.message}")
            FileLogger.w("AIToolSet", "DuckDuckGo failed: ${e.message}")
            ""
        }
    }

    private fun fetchUrl(urlString: String, timeoutMs: Int = 15000, extraHeaders: Map<String, String> = emptyMap()): String {
        val url = URL(urlString)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36")
            setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en-US,en;q=0.8")
            setRequestProperty("Accept-Encoding", "gzip, deflate")  // 移除 br，Android HttpURLConnection 不支持
            setRequestProperty("DNT", "1")
            setRequestProperty("Connection", "keep-alive")
            extraHeaders.forEach { (k, v) -> setRequestProperty(k, v) }
        }

        val code = conn.responseCode
        if (code != 200) {
            conn.disconnect()
            throw RuntimeException("HTTP $code")
        }

        val inputStream = if ("gzip" == conn.contentEncoding) {
            java.util.zip.GZIPInputStream(conn.inputStream)
        } else {
            conn.inputStream
        }

        return inputStream.bufferedReader().use { it.readText() }.also { conn.disconnect() }
    }

    private fun parseBingResults(html: String): List<Pair<String, String>> {
        val results = mutableListOf<Pair<String, String>>()

        // Bing 搜索结果解析
        val patterns = listOf(
            // 新布局
            Regex("""<a[^>]*target=\"_blank\"[^>]*href=\"([^\"]+)\"[^>]*>(.*?)</a>.*?<p[^>]*>(.*?)</p>""", RegexOption.DOT_MATCHES_ALL),
            // 标准结果
            Regex("""<li class=\"b_algo[^\"]*\"[^>]*>.*?<h2[^>]*>.*?<a[^>]*href=\"([^\"]+)\"[^>]*>(.*?)</a>.*?</h2>.*?<p[^>]*>(.*?)</p>""", RegexOption.DOT_MATCHES_ALL),
            // 备选
            Regex("""<a[^>]*href=\"([^\"]+)\"[^>]*h=\"[^\"]*\"[^>]*>(.*?)</a>.*?<span[^>]*>(.*?)</span>""", RegexOption.DOT_MATCHES_ALL),
        )

        for (pattern in patterns) {
            for (m in pattern.findAll(html).take(5)) {
                val link = m.groupValues[1].replace(Regex("<[^>]*>"), "").trim()
                val title = m.groupValues[2].replace(Regex("<[^>]*>"), "").trim()
                val snippet = m.groupValues.getOrNull(3)?.replace(Regex("<[^>]*>"), "")?.trim() ?: ""
                if (title.isNotBlank() && title.length < 200) {
                    results.add(title to snippet)
                }
            }
            if (results.isNotEmpty()) break
        }
        return results.take(5)
    }

    private fun parseDdgResults(html: String): List<Pair<String, String>> {
        val results = mutableListOf<Pair<String, String>>()

        val litePattern = Regex(
            """class="result-link"[^>]*>\s*(.*?)\s*</a>.*?class="result-snippet"[^>]*>\s*(.*?)\s*</""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
        )
        for (m in litePattern.findAll(html).take(5)) {
            val t = m.groupValues[1].replace(Regex("<[^>]*>"), "").trim()
            val s = m.groupValues[2].replace(Regex("<[^>]*>"), "").trim()
            if (t.isNotBlank()) results.add(t to s)
        }
        if (results.isNotEmpty()) return results

        val htmlPattern = Regex(
            """class="result__a"[^>]*>\s*(.*?)\s*</a>.*?class="result__snippet"[^>]*>\s*(.*?)\s*</a>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
        )
        for (m in htmlPattern.findAll(html).take(5)) {
            val t = m.groupValues[1].replace(Regex("<[^>]*>"), "").trim()
            val s = m.groupValues[2].replace(Regex("<[^>]*>"), "").trim()
            if (t.isNotBlank()) results.add(t to s)
        }
        return results
    }

    @Tool(description = "Read build lint or compilation errors from the project. Runs gradle lint if needed.")
    fun readLints(): String {
        Log.d("AIToolSet", "readLints")
        FileLogger.d("AIToolSet", "readLints")
        return try {
            val buildDir = File(context.filesDir, "workspace")
            val lintFiles = listOf(
                File(buildDir, "app/build/reports/lint-results.xml"),
                File(buildDir, "app/build/reports/lint-results-release.xml"),
            )
            for (f in lintFiles) {
                if (f.exists() && f.length() > 0) {
                    val text = f.readText()
                    val issues = Regex("""<issue[^>]*severity="Error"[^>]*>""", RegexOption.IGNORE_CASE)
                        .findAll(text).take(20).joinToString("\n") { match ->
                            val id = Regex("""id="([^"]+)"""").find(match.value)?.groupValues[1] ?: "?"
                            val msg = Regex("""<message>([^<]+)</message>""").find(text, match.range.last)?.groupValues[1] ?: ""
                            "• $id: $msg"
                        }
                    if (issues.isNotBlank()) {
                        FileLogger.d("AIToolSet", "readLints: found issues in ${f.name}")
                        return "Lint errors:\n$issues"
                    }
                }
            }
            val problemFiles = listOf(
                File(buildDir, "build/reports/problems/problems-report.html"),
                File(buildDir, "app/build/reports/problems/problems-report.html"),
            )
            for (f in problemFiles) {
                if (f.exists() && f.length() > 0) {
                    val html = f.readText()
                    val errors = Regex("""<li[^>]*>(.*?)</li>""", RegexOption.DOT_MATCHES_ALL)
                        .findAll(html).take(10).map { it.groupValues[1].replace(Regex("<[^>]*>"), "").trim() }
                        .filter { it.isNotBlank() }.toList()
                    if (errors.isNotEmpty()) {
                        FileLogger.d("AIToolSet", "readLints: found problems in ${f.name}")
                        return "Compilation problems:\n${errors.joinToString("\n")}"
                    }
                }
            }
            val pb = if (isWindows()) ProcessBuilder("cmd", "/c", "gradlew lint")
                else ProcessBuilder("sh", "-c", "./gradlew lint")
            pb.directory(buildDir).redirectErrorStream(true)
            val proc = pb.start()
            val out = proc.inputStream.bufferedReader().readText()
            proc.waitFor(60, java.util.concurrent.TimeUnit.SECONDS)
            proc.destroy()
            val errors = out.lines().filter { it.contains("ERROR") || it.contains("error:") }.take(30)
            if (errors.isNotEmpty()) {
                FileLogger.d("AIToolSet", "readLints: found ${errors.size} errors from gradle lint")
                "Lint output errors:\n${errors.joinToString("\n")}"
            } else {
                "No lint errors found."
            }
        } catch (e: Exception) {
            Log.e("AIToolSet", "readLints failed: ${e.message}", e)
            FileLogger.e("AIToolSet", "readLints failed: ${e.message}", e)
            "读取诊断失败: ${e.message}"
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
            ?: "No project folder is open."
        if (result.startsWith("Failed") || result.startsWith("No project")) {
            FileLogger.w("AIToolSet", "grep failed: ${result.take(200)}")
        } else {
            FileLogger.d("AIToolSet", "grep: found ${result.lines().size} lines of results")
        }
        return result
    }

    @Tool(description = "Search file contents by exact substring match (case-insensitive). Simpler than grep, use when you know the exact text to find. For regex patterns, use grep instead.")
    fun searchInFiles(
        @ToolParam(description = "Text to search for (exact match, case insensitive)") query: String,
        @ToolParam(description = "File extension filter, e.g. 'kt' for Kotlin files only. Leave empty for all text files.") extension: String = "",
    ): String {
        Log.d("AIToolSet", "searchInFiles: query=$query ext=$extension")
        FileLogger.d("AIToolSet", "searchInFiles: query=$query ext=$extension")
        val result = fileManager?.searchInFiles(query, extension)
            ?: "No project folder is open."
        if (result.startsWith("Failed") || result.startsWith("No project")) {
            FileLogger.w("AIToolSet", "searchInFiles failed: ${result.take(200)}")
        } else {
            FileLogger.d("AIToolSet", "searchInFiles: found ${result.lines().size} lines of results")
        }
        return result
    }

    @Tool(description = "Search codebase by meaning using semantic similarity. Converts query into a search vector and finds related code across the project. Use for exploring unfamiliar code or finding implementations by behavior (e.g. 'where is user authentication?' or 'how does error handling work?'). Prefer over grep when you don't know exact terms.")
    fun searchCodebase(
        @ToolParam(description = "Natural language query describing what you're looking for, e.g. 'Where is user authentication implemented?' or 'How does error handling work?'") query: String,
        @ToolParam(description = "Specific directories to search within, relative to project root. Leave empty to search entire project.") targetDirectories: String = "",
    ): String {
        Log.d("AIToolSet", "searchCodebase: query=$query dirs=$targetDirectories")
        FileLogger.d("AIToolSet", "searchCodebase: query=$query dirs=$targetDirectories")
        val result = fileManager?.searchCodebase(query, targetDirectories)
            ?: "No project folder is open."
        if (result.startsWith("Failed") || result.startsWith("No project")) {
            FileLogger.w("AIToolSet", "searchCodebase failed: ${result.take(200)}")
        } else {
            FileLogger.d("AIToolSet", "searchCodebase: found ${result.lines().size} lines of results")
        }
        return result
    }

}