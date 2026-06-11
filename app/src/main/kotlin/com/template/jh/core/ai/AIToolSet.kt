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

    // SAF 项目根 URI（用户通过长按文件夹下拉列表设置的 workspace）
    @Volatile var projectUri: Uri? = null
        set(value) {
            field = value
            value?.let { fileManager?.setProjectUri(it) }
        }

    /** 沙箱守卫：项目未打开则拒绝操作 */
    private fun ensureProjectOpen(): Boolean {
        val fm = fileManager ?: return false
        return fm.projectUri != null
    }

    /** 沙箱拦截入口 — 项目未打开时返回错误消息，否则返回 null（通过） */
    private fun requireProject(action: String): String? {
        if (!ensureProjectOpen()) {
            val msg = "沙箱保护：未打开项目。请先长按文件夹选择项目目录（workspace），再执行 $action 操作。"
            FileLogger.w("AIToolSet", "沙箱阻止 $action: 未打开项目")
            return err(msg)
        }
        return null
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

    @Tool(description = "列出目录内容，显示[FILE]/[DIR]前缀和文件大小。路径支持绝对或相对，留空表示项目根目录。")
    fun listFiles(
        @ToolParam(description = "目录路径，支持绝对路径或相对项目根目录，留空表示根目录") subPath: String = "",
    ): String = traceTool("listFiles", "subPath" to subPath) {
        Log.d("AIToolSet", "列出文件: subPath=$subPath")
        FileLogger.d("AIToolSet", "列出文件: subPath=$subPath")
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

        /** 构建工具信息的纯文本描述（注入系统提示词）。内容与 @Tool 注解保持一致。 */
        fun buildToolInfoText(): String = buildString {
            appendLine("## 可用工具")
            appendLine()
            appendLine("readFile(path, offset?, limit?) — 读取文件内容，返回纯文本无行号前缀。offset 从 1 开始，limit 默认 1000。超 500 行自动截断为前 200 行。")
            appendLine("writeFile(path, content, overwrite?) — 创建新文件。overwrite=false(默认)时若文件已存在则报错并提示用 replaceInFile；overwrite=true 则覆盖。")
            appendLine("replaceInFile(path, old_string, new_string, lineStart?, lineEnd?) — 精确替换代码块。old_string 必须在搜索范围内唯一；可用 lineStart/lineEnd(1-based) 缩小范围，传 0 搜全文。")
            appendLine("batchReplaceInFile(path, editsJson) — 批量编辑，一次替换多处不重叠代码。edits 基于原始文件同时匹配（非顺序应用），编辑不能重叠。单次编辑请用 replaceInFile。")
            appendLine("deleteFile(path) — 删除文件或目录。谨慎使用，删除后无法恢复。")
            appendLine("createDirectory(path) — 创建目录，自动创建所有父目录。支持多级路径如 'src/utils/helpers'")
            appendLine("listFiles(subPath?) — 列出目录内容，显示 [FILE]/[DIR] 前缀和文件大小。路径支持绝对或相对，留空表示项目根目录。")
            appendLine("grep(pattern, extension?, glob?, ignoreCase?, contextLines?) — 正则搜索文件内容，返回匹配文件、行号和上下文行。适合查找函数定义、变量使用等。")
            appendLine("glob(pattern, maxResults?) — 按文件名 glob 模式搜索，如 '*.kt'、'Main*'。递归搜索项目根目录。适合知道文件名但不知完整路径时。")
            appendLine("searchCodebase(query, targetDirectories?) — 语义搜索代码库，按含义查找相关代码。适合探索陌生代码或用自然语言描述找实现。比 grep 更适合模糊查询。")
            appendLine("runCommand(command) — 执行 shell 命令，在当前项目目录下运行。超时 30 秒，输出上限 5000 字符。")
            appendLine("searchWeb(query) — 联网搜索最新信息。获取实时数据或超出知识范围的内容。")
            appendLine("readLints() — 读取构建/lint/编译错误，返回文件路径和行号。无缓存时自动运行 gradle lint。")
            appendLine("searchConversationMemory(query) — 语义搜索对话历史。需要回忆之前讨论内容时使用，如 '我们改过哪些文件'、'上次怎么修的 bug'。")
            appendLine("getRecentConversationMemory(count?) — 获取最近对话摘要，用于回顾最近几次交流内容。count 默认 5，上限 20。")
        }

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
            "语义搜索对话历史。需要回忆之前讨论内容时使用，如'我们改过哪些文件'、'上次怎么修的bug'。搜索所有记忆层包括短期记忆、摘要和向量索引。",
            listOf("query"),
            "query" to p("string", "搜索内容，如'文件结构'或'错误修复'"),
        )
        private fun buildGetRecentConversationMemoryTool() = toolDef(
            "getRecentConversationMemory",
            "获取最近对话摘要。用于回顾最近几次交流内容。默认返回最近5条。",
            props = arrayOf("count" to p("integer", "最近条目数，默认5，最大20")),
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
        private fun buildReadFileTool() = toolDef("readFile", "读取文件内容，返回纯文本无行号前缀。支持分页：offset从1开始，limit默认1000。超500行自动截断为前200行。", listOf("path"),
            "path" to p("string", "文件路径 — 绝对路径或相对项目根目录"),
            "offset" to p("integer", "起始行号，从1开始计数，默认1"),
            "limit" to p("integer", "最大读取行数，默认1000"),
        )
        private fun buildWriteFileTool() = toolDef("writeFile", "创建新文件。overwrite=false(默认)时若文件已存在则报错并提示用replaceInFile修改；overwrite=true则覆盖。", listOf("path", "content"),
            "path" to p("string", "文件路径 — 绝对路径或相对项目根目录"),
            "content" to p("string", "要写入的完整文本内容"),
            "overwrite" to p("boolean", "是否覆盖已有文件，默认false"),
        )
        private fun buildReplaceInFileTool() = toolDef("replaceInFile", "精确替换代码块。old_string必须在搜索范围内唯一；可用lineStart/lineEnd(1-based)缩小范围，传0搜全文。", listOf("path", "old_string", "new_string"),
            "path" to p("string", "文件路径 — 绝对路径或相对项目根目录"),
            "old_string" to p("string", "要查找的精确代码块，在搜索范围内必须唯一匹配"),
            "new_string" to p("string", "替换后的新代码块"),
            "lineStart" to p("integer", "搜索起始行(1-based)，0表示从文件开头"),
            "lineEnd" to p("integer", "搜索结束行(1-based)，0表示到文件结尾"),
        )
        private fun buildBatchReplaceInFileTool() = toolDef("batchReplaceInFile", "批量编辑，一次替换多处不重叠代码。edits基于原始文件同时匹配（非顺序应用），编辑不能重叠。单次编辑请用replaceInFile。", listOf("path", "edits"),
            "path" to p("string", "文件路径 — 绝对路径或相对项目根目录"),
            "edits" to p("string", "JSON数组：[{\"old_string\":\"原代码\",\"new_string\":\"新代码\"}]"),
        )
        private fun buildDeleteFileTool() = toolDef("deleteFile", "删除文件或目录。谨慎使用，删除后无法恢复。", listOf("path"),
            "path" to p("string", "文件/目录路径 — 绝对路径或相对项目根目录"),
        )
        private fun buildCreateDirectoryTool() = toolDef("createDirectory", "创建目录，自动创建所有父目录。支持多级路径如'src/utils/helpers'", listOf("path"),
            "path" to p("string", "目录路径 — 绝对路径或相对项目根目录"),
        )
        private fun buildListFilesTool() = toolDef("listFiles", "列出目录内容，显示[FILE]/[DIR]前缀和文件大小。路径支持绝对或相对，留空表示项目根目录。",
            props = arrayOf("subPath" to p("string", "目录路径，支持绝对路径或相对项目根目录，留空表示根目录")),
        )
        private fun buildGrepTool() = toolDef("grep", "正则搜索文件内容，返回匹配文件、行号和上下文行。适合查找函数定义、变量使用等。", listOf("pattern"),
            "pattern" to p("string", "正则表达式，如'fun\\s+\\w+'查找函数定义"),
            "extension" to p("string", "文件扩展名过滤，如'kt'只查Kotlin，留空查所有"),
            "glob" to p("string", "Glob模式过滤，如'*.kt'或'src/**/*.java'"),
            "ignoreCase" to p("boolean", "忽略大小写，默认true"),
            "contextLines" to p("integer", "匹配前后上下文行数，默认2"),
        )
        private fun buildSearchCodebaseTool() = toolDef("searchCodebase", "语义搜索代码库，按含义查找相关代码。适合探索陌生代码或用自然语言描述找实现。比grep更适合模糊查询。", listOf("query"),
            "query" to p("string", "自然语言描述，如'用户认证在哪'或'错误处理怎么工作'"),
            "targetDirectories" to p("string", "限定搜索目录，相对项目根目录，留空搜索整个项目"),
        )
        private fun buildRunCommandTool() = toolDef("runCommand", "执行shell命令，在当前项目目录下运行。超时30秒，输出上限5000字符。", listOf("command"),
            "command" to p("string", "要执行的命令，如'ls -la'或'./gradlew build'"),
        )
        private fun buildSearchWebTool() = toolDef("searchWeb", "联网搜索最新信息。获取实时数据或超出知识范围的内容。", listOf("query"),
            "query" to p("string", "搜索关键词，简洁明确"),
        )
        private fun buildReadLintsTool() = toolDef("readLints", "读取构建/lint/编译错误，返回文件路径和行号。无缓存时自动运行gradle lint。")
        private fun buildGlobTool() = toolDef("glob", "按文件名glob模式搜索，如'*.kt'、'Main*'。递归搜索项目根目录。适合知道文件名但不知完整路径时。", listOf("pattern"),
            "pattern" to p("string", "Glob模式，如'*.kt'所有Kotlin文件，'Main*'Main开头文件，'**/*.xml'所有XML"),
            "maxResults" to p("integer", "最大返回结果数，默认100"),
        )
    }

    /** 替换后清理多余空行：3+ 连续换行 → 2，首尾空行 */
    private fun normalizeBlankLines(text: String): String {
        return text
            .replace(Regex("""\n{3,}"""), "\n\n")
            .trimEnd('\n')
    }

    @Tool(description = "读取文本文件内容，返回纯文本无行号前缀。支持分页：offset从1开始，limit默认1000。超500行自动截断为前200行。")
    fun readFile(
        @ToolParam(description = "文件路径，相对项目根目录，如'app/src/main.kt'") path: String,
        @ToolParam(description = "起始行号，从1开始计数，默认1") offset: Int = 1,
        @ToolParam(description = "最大读取行数，默认1000") limit: Int = 1000,
    ): String = traceTool("readFile", "path" to path, "offset" to offset, "limit" to limit) {
        Log.d("AIToolSet", "读取文件: path=$path offset=$offset limit=$limit")
        FileLogger.d("AIToolSet", "读取文件: path=$path offset=$offset limit=$limit")
        val raw = readFileRaw(path)
        if (raw == null) {
            val msg = if (fileManager == null) "No project folder is open." else "File not found: $path"
            FileLogger.w("AIToolSet", "读取文件失败: $msg")
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
        FileLogger.d("AIToolSet", "读取文件: 返回 ${msg.lines().size} 行, 路径=$path")
        return msg
    }

    @Tool(description = "创建新文件。overwrite=false(默认)时若文件已存在则报错并提示用replaceInFile修改；overwrite=true则覆盖。")
    fun writeFile(
        @ToolParam(description = "文件路径，相对项目根目录，如'src/App.kt'") path: String,
        @ToolParam(description = "要写入的完整文本内容") content: String,
        @ToolParam(description = "是否覆盖已有文件，默认false") overwrite: Boolean = false,
    ): String = traceTool("writeFile", "path" to path, "content" to content, "overwrite" to overwrite) {
        Log.d("AIToolSet", "写入文件: path=$path contentLen=${content.length} overwrite=$overwrite")
        FileLogger.d("AIToolSet", "写入文件: path=$path contentLen=${content.length} overwrite=$overwrite")
        requireProject("写入文件")?.let { return it }
        if (content.isEmpty()) {
            val msg = "Write refused — content is empty."
            FileLogger.w("AIToolSet", "写入文件被拒: 内容为空")
            return err(msg)
        }
        val fm = fileManager!!  // requireProject 已保证非 null
        val resolvedPath = resolvePathOrAbsolute(path)

        if (!overwrite && fm.exists(resolvedPath)) {
            val msg = ("Write refused — $resolvedPath already exists.\n" +
                "Write is for creating NEW files only.\n" +
                "To modify an existing file, use replaceInFile or batchReplaceInFile with the exact code block.\n" +
                "Read the file first if you don't know its current content.\n" +
                "To overwrite, set overwrite=true.")
            FileLogger.w("AIToolSet", "写入文件被保护阻止: $resolvedPath 已存在")
            return err(msg)
        }
        return try {
            val result = fm.writeFile(resolvedPath, content)
            if (!result.startsWith("Failed") && !result.startsWith("No project")) {
                FileOperationEvents.notify(resolvedPath, "create")
                FileLogger.d("AIToolSet", "写入文件成功: $result")
                ok(result)
            } else {
                FileLogger.w("AIToolSet", "写入文件失败: $result")
                err(result)
            }
        } catch (e: Exception) {
            val m = "Write failed: ${e.message ?: "unknown error"}"
            FileLogger.e("AIToolSet", "写入文件异常: $m", e)
            err(m)
        }
    }

    // 代码编辑工具 - 类似 SearchReplace，只修改指定内容
    // 这是唯一推荐的编辑方式：提供 old_string（要查找的代码块）和 new_string（新代码块）
    @Tool(description = "编辑文件，精确替换代码块。old_string必须在搜索范围内唯一；可用lineStart/lineEnd(1-based)缩小范围，传0搜全文。")
    fun replaceInFile(
        @ToolParam(description = "文件路径，相对项目根目录，如'src/MainActivity.kt'") path: String,
        @ToolParam(description = "要查找的精确代码块，在搜索范围内必须唯一匹配") old_string: String,
        @ToolParam(description = "替换后的新代码块") new_string: String,
        @ToolParam(description = "搜索起始行(1-based)，0表示从文件开头") lineStart: Int = 0,
        @ToolParam(description = "搜索结束行(1-based)，0表示到文件结尾") lineEnd: Int = 0,
    ): String = traceTool("replaceInFile", "path" to path, "old_string" to old_string, "new_string" to new_string, "lineStart" to lineStart, "lineEnd" to lineEnd) {
        requireProject("编辑文件")?.let { return it }
        val fm = fileManager!!
        val resolvedPath = resolvePathOrAbsolute(path)
        return try {
            Log.d("AIToolSet", "文件内容替换: path=$resolvedPath oldLen=${old_string.length} newLen=${new_string.length} lineStart=$lineStart lineEnd=$lineEnd")
            FileLogger.d("AIToolSet", "文件内容替换: path=$resolvedPath oldLen=${old_string.length} newLen=${new_string.length} lineStart=$lineStart lineEnd=$lineEnd")
            val fullOriginal = readFileRaw(resolvedPath)
            if (fullOriginal == null) {
                Log.w("AIToolSet", "文件内容替换: 文件未找到: $resolvedPath")
                FileLogger.w("AIToolSet", "文件内容替换: 文件未找到: $resolvedPath")
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
                        FileLogger.e("AIToolSet", "文件内容替换: 替换成功但写入失败: $writeResult")
                        return err("Replace succeeded but write failed: $writeResult")
                    }
                    FileOperationEvents.notify(resolvedPath, "modify")
                    FileLogger.d("AIToolSet", "文件内容替换成功: $resolvedPath")
                    val oldLines = old_string.lines().size
                    val newLines = new_string.lines().size
                    ok("$resolvedPath$displayRange — ${result.message}. Changed $oldLines lines → $newLines lines.")
                }
                is CodeEditTool.ReplaceResult.Error -> {
                    FileLogger.w("AIToolSet", "文件内容替换: CodeEditTool 拒绝: ${result.message.take(200)}")
                    err(result.message)
                }
            }
        } catch (e: Exception) {
            Log.e("AIToolSet", "文件内容替换失败: ${e.message}", e)
            FileLogger.e("AIToolSet", "文件内容替换失败: ${e.message}", e)
            err("${e.message}")
        }
    }

    // 批量编辑 — 一次调用替换多处不重叠的内容
    @Tool(description = "批量编辑，一次替换多处不重叠代码。edits基于原始文件同时匹配（非顺序应用），编辑不能重叠。单次编辑请用replaceInFile。")
    fun batchReplaceInFile(
        @ToolParam(description = "文件路径，相对项目根目录，如'src/MainActivity.kt'") path: String,
        @ToolParam(description = "JSON数组：[{\"old_string\":\"原代码\",\"new_string\":\"新代码\"}]") editsJson: String,
    ): String = traceTool("batchReplaceInFile", "path" to path, "editsJson" to editsJson) {
        requireProject("批量编辑")?.let { return@traceTool it }
        val fm = fileManager!!
        val resolvedPath = resolvePathOrAbsolute(path)
        return@traceTool try {
            Log.d("AIToolSet", "批量替换: path=$resolvedPath editsLen=${editsJson.length}")
            FileLogger.d("AIToolSet", "批量替换: path=$resolvedPath editsLen=${editsJson.length}")
            val original = readFileRaw(resolvedPath)
            if (original == null) {
                Log.w("AIToolSet", "批量替换: 文件未找到: $resolvedPath")
                FileLogger.w("AIToolSet", "批量替换: 文件未找到: $resolvedPath")
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
                        FileLogger.e("AIToolSet", "批量替换: 替换成功但写入失败: $writeResult")
                        return err("Edit succeeded but write failed: $writeResult")
                    }
                    FileOperationEvents.notify(resolvedPath, "modify")
                    FileLogger.d("AIToolSet", "批量替换成功: $resolvedPath")
                    ok("$resolvedPath — ${edits.size} edits applied. ${result.message}")
                }
                is CodeEditTool.ReplaceResult.Error -> {
                    FileLogger.w("AIToolSet", "批量替换: CodeEditTool 拒绝: ${result.message.take(200)}")
                    err(result.message)
                }
            }
        } catch (e: org.json.JSONException) {
            val m = "Invalid edits JSON: ${e.message}"
            Log.e("AIToolSet", "批量替换: $m")
            FileLogger.e("AIToolSet", "批量替换: $m")
            err(m)
        } catch (e: Exception) {
            Log.e("AIToolSet", "批量替换失败: ${e.message}", e)
            FileLogger.e("AIToolSet", "批量替换失败: ${e.message}", e)
            err("${e.message}")
        }
    }

    @Tool(description = "删除文件或目录。谨慎使用，删除后无法恢复。")
    fun deleteFile(
        @ToolParam(description = "文件或目录路径，相对项目根目录，如'src/OldFile.kt'或'temp/'") path: String,
    ): String = traceTool("deleteFile", "path" to path) {
        requireProject("删除文件")?.let { return it }
        val fm = fileManager!!
        val resolvedPath = resolvePathOrAbsolute(path)
        Log.d("AIToolSet", "删除文件: path=$resolvedPath")
        FileLogger.d("AIToolSet", "删除文件: path=$resolvedPath")
        val result = fm.deleteFile(resolvedPath)
        return if (result.startsWith("Failed") || result.startsWith("No project")) {
            FileLogger.w("AIToolSet", "删除文件失败: $result")
            err(result)
        } else {
            FileOperationEvents.notify(resolvedPath, "delete")
            FileLogger.d("AIToolSet", "删除文件成功: $result")
            ok(result)
        }
    }


    @Tool(description = "创建目录，自动创建所有父目录。支持多级路径如'src/utils/helpers'")
    fun createDirectory(
        @ToolParam(description = "目录路径，相对项目根目录，如'src/utils'") path: String,
    ): String = traceTool("createDirectory", "path" to path) {
        requireProject("创建目录")?.let { return it }
        val fm = fileManager!!
        val resolvedPath = resolvePathOrAbsolute(path)
        Log.d("AIToolSet", "创建目录: path=$resolvedPath")
        FileLogger.d("AIToolSet", "创建目录: path=$resolvedPath")
        val result = fm.createDirectory(resolvedPath)
        return if (result.startsWith("Failed") || result.startsWith("No project")) {
            FileLogger.w("AIToolSet", "创建目录失败: $result")
            err(result)
        } else {
            FileOperationEvents.notify(resolvedPath, "create")
            FileLogger.d("AIToolSet", "创建目录成功: $result")
            ok(result)
        }
    }

    @Tool(description = "执行shell命令，在当前项目目录下运行。超时30秒，输出上限5000字符。")
    fun runCommand(
        @ToolParam(description = "要执行的命令，如'ls -la'或'./gradlew build'") command: String,
    ): String {
        Log.d("AIToolSet", "执行命令: $command")
        FileLogger.d("AIToolSet", "执行命令: $command")
        requireProject("执行命令")?.let { return it }
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
            Log.e("AIToolSet", "执行命令失败: ${e.message}", e)
            FileLogger.e("AIToolSet", "执行命令失败: ${e.message}", e)
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

    @Tool(description = "联网搜索最新信息。获取实时数据或超出知识范围的内容。")
    fun searchWeb(
        @ToolParam(description = "搜索关键词，简洁明确") query: String,
    ): String {
        if (query.isBlank()) return err("Search query is empty.")
        Log.d("AIToolSet", "搜索网络: query=$query")
        FileLogger.d("AIToolSet", "搜索网络: query=$query")
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
            FileLogger.d("AIToolSet", "搜索网络: 找到 ${results.size} 条结果")
            results.joinToString("\n---\n") { (title, snippet, link) ->
                buildString {
                    appendLine("**$title**")
                    if (snippet.isNotBlank()) appendLine(snippet)
                    if (link.isNotBlank()) appendLine(link)
                }
            }
        } catch (e: Exception) {
            Log.e("AIToolSet", "搜索网络失败: ${e.message}", e)
            FileLogger.e("AIToolSet", "搜索网络失败: ${e.message}", e)
            err("${e.message}")
        }
    }

    @Tool(description = "读取构建/lint/编译错误，返回文件路径和行号。无缓存时自动运行gradle lint。")
    fun readLints(): String {
        Log.d("AIToolSet", "读取代码检查")
        FileLogger.d("AIToolSet", "读取代码检查")
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
                        FileLogger.d("AIToolSet", "读取代码检查: 在 $name 中找到 ${issues.size} 条")
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
                        FileLogger.d("AIToolSet", "读取代码检查: 在 ${f.name} 中找到 ${errors.size} 个问题")
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
                FileLogger.d("AIToolSet", "读取代码检查: 找到 ${fileLineErrors.size} 个编译错误")
                return ok("${fileLineErrors.size} compile errors:\n${fileLineErrors.joinToString("\n")}")
            }
            val genericErrors = out.lines().filter { it.contains("ERROR") || it.contains("error:") }.take(20)
            if (genericErrors.isNotEmpty()) {
                return ok("${genericErrors.size} errors from gradle lint:\n${genericErrors.joinToString("\n")}")
            }
            ok("No lint errors found.")
        } catch (e: Exception) {
            Log.e("AIToolSet", "读取代码检查结果失败: ${e.message}", e)
            FileLogger.e("AIToolSet", "读取代码检查结果失败: ${e.message}", e)
            err("Lint scan failed: ${e.message}")
        }
    }

    private fun isWindows(): Boolean = System.getProperty("os.name")?.lowercase()?.contains("win") == true

    @Tool(description = "正则搜索文件内容，返回匹配文件、行号和上下文行。适合查找函数定义、变量使用等。")
    fun grep(
        @ToolParam(description = "正则表达式，如'fun\\s+\\w+'查找函数定义") pattern: String,
        @ToolParam(description = "文件扩展名过滤，如'kt'只查Kotlin，留空查所有") extension: String = "",
        @ToolParam(description = "Glob模式过滤，如'*.kt'或'src/**/*.java'") glob: String = "",
        @ToolParam(description = "忽略大小写，默认true") ignoreCase: Boolean = true,
        @ToolParam(description = "匹配前后上下文行数，默认2") contextLines: Int = 2,
    ): String {
        Log.d("AIToolSet", "搜索文件内容: pattern=$pattern ext=$extension glob=$glob ignoreCase=$ignoreCase context=$contextLines")
        FileLogger.d("AIToolSet", "搜索文件内容: pattern=$pattern ext=$extension glob=$glob ignoreCase=$ignoreCase context=$contextLines")
        val result = fileManager?.grep(pattern, extension, glob, ignoreCase, contextLines)
            ?: return err("No project folder is open.")
        return if (result.startsWith("Failed") || result.startsWith("No project")) {
            FileLogger.w("AIToolSet", "搜索文件内容失败: ${result.take(200)}")
            err(result)
        } else {
            FileLogger.d("AIToolSet", "搜索文件内容: 找到 ${result.lines().size} 行结果")
            result  // grep already returns structured content
        }
    }

    @Tool(description = "语义搜索代码库，按含义查找相关代码。适合探索陌生代码或用自然语言描述找实现。比grep更适合模糊查询。")
    fun searchCodebase(
        @ToolParam(description = "自然语言描述，如'用户认证在哪'或'错误处理怎么工作'") query: String,
        @ToolParam(description = "限定搜索目录，相对项目根目录，留空搜索整个项目") targetDirectories: String = "",
    ): String {
        Log.d("AIToolSet", "搜索代码: query=$query dirs=$targetDirectories")
        FileLogger.d("AIToolSet", "搜索代码: query=$query dirs=$targetDirectories")
        val result = fileManager?.searchCodebase(query, targetDirectories)
            ?: return err("No project folder is open.")
        return if (result.startsWith("Failed") || result.startsWith("No project")) {
            FileLogger.w("AIToolSet", "搜索代码失败: ${result.take(200)}")
            err(result)
        } else {
            FileLogger.d("AIToolSet", "搜索代码: 找到 ${result.lines().size} 行结果")
            result
        }
    }

    // === 文件名搜索（Glob） ===
    @Tool(description = "按文件名glob模式搜索，如'*.kt'、'Main*'。递归搜索项目根目录。适合知道文件名但不知完整路径时。")
    fun glob(
        @ToolParam(description = "Glob模式，如'*.kt'所有Kotlin文件，'Main*'Main开头文件，'**/*.xml'所有XML") pattern: String,
        @ToolParam(description = "最大返回结果数，默认100") maxResults: Int = 100,
    ): String {
        Log.d("AIToolSet", "模糊搜索文件: pattern=$pattern max=$maxResults")
        FileLogger.d("AIToolSet", "模糊搜索文件: pattern=$pattern max=$maxResults")
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

    @Tool(description = "语义搜索对话历史。需要回忆之前讨论内容时使用，如'我们改过哪些文件'、'上次怎么修的bug'。")
    fun searchConversationMemory(
        @ToolParam(description = "搜索内容，如'文件结构'或'错误修复'") query: String,
    ): String {
        val mem = conversationMemory ?: return "对话记忆系统未加载。"
        Log.d("AIToolSet", "搜索会话记忆: query=$query")
        return mem.searchFormatted(query)
    }

    @Tool(description = "获取最近对话摘要。用于回顾最近几次交流内容。")
    fun getRecentConversationMemory(
        @ToolParam(description = "最近条目数，默认5，最大20") count: Int = 5,
    ): String {
        val mem = conversationMemory ?: return "对话记忆系统未加载。"
        Log.d("AIToolSet", "获取最近会话记忆: count=$count")
        return mem.recentFormatted(count.coerceIn(1, 20))
    }
}