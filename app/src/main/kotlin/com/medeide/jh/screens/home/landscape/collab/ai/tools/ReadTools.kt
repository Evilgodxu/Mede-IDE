package com.medeide.jh.screens.home.landscape.collab.ai.tools

import android.util.Log
import com.medeide.jh.data.storage.FileManager
import com.medeide.jh.data.utils.FileLogger
import com.medeide.jh.screens.home.landscape.collab.memory.ConversationMemory

class ReadTools(
    private val fileManager: FileManager?,
    private val conversationMemory: ConversationMemory?,
) {
    // ================================================================
    //  文件浏览  ←→ LS(path, ignore)
    // ================================================================

    fun listFiles(path: String = "", ignore: String = ""): String {
        Log.d("AIToolSet", "listFiles: path=$path ignore=$ignore")
        FileLogger.d("AIToolSet", "listFiles: path=$path ignore=$ignore")
        val fm = fileManager ?: return err("No project folder is open.")
        val relPath = resolvePathOrAbsolute(path, fileManager)
        val nodes = fm.listFilesAsNodes(relPath)
        if (nodes.isEmpty()) return ok("Empty directory: ${path.ifBlank { "/" }}")
        val ignorePatterns = if (ignore.isNotBlank()) ignore.split(",").map {
            it.trim().toRegex()
        } else emptyList()
        val filtered = nodes.filter { node ->
            ignorePatterns.none { it.matches(node.name) }
        }
        val displayPath = if (relPath.isBlank()) "项目根目录/" else "$relPath/"
        return buildString {
            appendLine(ok("$displayPath (${filtered.size} items)"))
            filtered.forEach { node ->
                if (node.isDirectory) {
                    appendLine("  [DIR] ${node.name}/")
                } else {
                    val size = if (node.size > 0) " (${formatSize(node.size)})" else ""
                    appendLine("  [FILE] ${node.name}$size")
                }
            }
        }.trimEnd()
    }

    // ================================================================
    //  文件读取  ←→ Read(file_path, offset, limit)
    //  offset 改为 0-based（与我一致）
    // ================================================================

    fun readFile(file_path: String, offset: Int = 0, limit: Int = 1000): String {
        Log.d("AIToolSet", "readFile: file_path=$file_path offset=$offset limit=$limit")
        FileLogger.d("AIToolSet", "readFile: file_path=$file_path offset=$offset limit=$limit")
        val raw = readFileRaw(file_path)
        if (raw == null) {
            val msg = if (fileManager == null) "No project folder is open." else "File not found: $file_path"
            FileLogger.w("AIToolSet", "readFile failed: $msg")
            return err(msg)
        }
        val allLines = raw.lines()
        val totalLines = allLines.size
        if (totalLines == 0) return ok("File $file_path is empty.")
        val effectiveLimit = if (limit >= 200 && totalLines > 500) {
            if (offset <= 0) 200.coerceAtMost(limit) else limit
        } else limit
        val startIdx = offset.coerceIn(0, totalLines)
        val endIdx = (startIdx + effectiveLimit).coerceAtMost(totalLines)
        if (startIdx >= totalLines) {
            return err("File $file_path has $totalLines lines. Cannot start at line $offset.")
        }
        val result = buildString {
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
        FileLogger.d("AIToolSet", "readFile: 返回 ${result.lines().size} 行, file_path=$file_path")
        return result
    }

    // ================================================================
    //  Grep  ←→ Grep(pattern, path, glob, output_mode, -B, -A, -C, -n, -i, type, head_limit, offset, multiline)
    // ================================================================

    fun grep(
        pattern: String,
        path: String = "",
        glob: String = "",
        output_mode: String = "content",
        `-n`: Boolean = false,
        `-i`: Boolean = true,
        type: String = "",
        head_limit: Int = 100,
        offset: Int = 0,
        multiline: Boolean = false,
        `-C`: Int = 2,
        `-B`: Int = 0,
        `-A`: Int = 0,
    ): String {
        Log.d("AIToolSet", "grep: pattern=$pattern path=$path glob=$glob output_mode=$output_mode -n=$-n -i=$-i type=$type head_limit=$head_limit offset=$offset multiline=$multiline -C=$-C -B=$-B -A=$-A")
        FileLogger.d("AIToolSet", "grep: pattern=$pattern path=$path glob=$glob ...")
        val fm = fileManager ?: return err("No project folder is open.")
        // 映射参数到 FileManager API
        val ext = type
        val ctxLines = when {
            `-B` > 0 || `-A` > 0 -> maxOf(`-B`, `-A`)
            else -> `-C`
        }
        var result = fm.grep(pattern, ext, glob, `-i`, ctxLines)
        if (result.startsWith("Failed") || result.startsWith("No project")) {
            FileLogger.w("AIToolSet", "grep failed: ${result.take(200)}")
            return err(result)
        }
        // 后处理：path 过滤、offset、head_limit、output_mode
        var lines = result.lines()
        // 移除首行 [OK] 前缀（如果有）
        val okPrefix = lines.firstOrNull()?.takeWhile { it != '\n' }?.take(4)
        if (okPrefix == "[OK] ") lines = lines.drop(1)
        // path 过滤
        if (path.isNotBlank()) {
            lines = lines.filter { it.contains(path) }
        }
        // offset 跳过
        if (offset > 0 && offset < lines.size) {
            lines = lines.drop(offset)
        }
        // head_limit 截断
        if (head_limit > 0 && head_limit < lines.size) {
            lines = lines.take(head_limit)
        }
        // output_mode 格式化
        val output = when (output_mode) {
            "files_with_matches" -> {
                val files = lines.map { it.substringBefore(":").substringBefore("(") }.distinct()
                if (files.isEmpty()) "No matches found."
                else "${files.size} files:\n${files.joinToString("\n")}"
            }
            "count" -> "${lines.size} matches found."
            else -> lines.joinToString("\n")
        }
        FileLogger.d("AIToolSet", "grep: 返回 ${if (output_mode == "count") "count" else "${lines.size} lines"}")
        return if (lines.isEmpty()) ok("No matches found.")
        else ok(output)
    }

    // ================================================================
    //  语义搜索  ←→ SearchCodebase(information_request, target_directories)
    // ================================================================

    fun searchCodebase(information_request: String, target_directories: String = ""): String {
        Log.d("AIToolSet", "searchCodebase: information_request=$information_request target_directories=$target_directories")
        FileLogger.d("AIToolSet", "searchCodebase: query=$information_request dirs=$target_directories")
        val fm = fileManager ?: return err("No project folder is open.")
        val result = fm.searchCodebase(information_request, target_directories)
        return if (result.startsWith("Failed") || result.startsWith("No project")) {
            FileLogger.w("AIToolSet", "searchCodebase failed: ${result.take(200)}")
            err(result)
        } else {
            FileLogger.d("AIToolSet", "searchCodebase: 找到 ${result.lines().size} 行结果")
            result
        }
    }

    // ================================================================
    //  Glob  ←→ Glob(pattern, path)
    // ================================================================

    fun glob(pattern: String, path: String = "", maxResults: Int = 100): String {
        Log.d("AIToolSet", "glob: pattern=$pattern path=$path max=$maxResults")
        FileLogger.d("AIToolSet", "glob: pattern=$pattern path=$path max=$maxResults")
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
        val matches = mutableListOf<String>()
        var dirsToScan = ArrayDeque<String>()
        // 如果指定了 path，从 path 开始遍历
        val rootDir = if (path.isNotBlank()) resolvePathOrAbsolute(path, fileManager) else ""
        dirsToScan.add(rootDir)
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
            ok("${matches.size} files matching '$pattern':\n${matches.joinToString("\n")}")
        }
    }

    // ================================================================
    //  对话记忆
    // ================================================================

    fun searchConversationMemory(query: String, conversationId: String?): String {
        val mem = conversationMemory ?: return "对话记忆系统未加载。"
        Log.d("AIToolSet", "searchConversationMemory: query=$query")
        return mem.searchFormatted(query, conversationId = conversationId)
    }

    fun getRecentConversationMemory(count: Int = 1, conversationId: String?): String {
        val mem = conversationMemory ?: return "对话记忆系统未加载。"
        Log.d("AIToolSet", "getRecentConversationMemory: count=$count")
        return mem.recentFormatted(count.coerceIn(1, 20), conversationId = conversationId)
    }

    // ================================================================
    //  内部辅助
    // ================================================================

    private fun readFileRaw(path: String): String? {
        return fileManager?.readFileRaw(resolvePathOrAbsolute(path, fileManager))
    }

    // ================================================================
    //  工具定义
    // ================================================================

    companion object {
        fun buildOpenAIToolsJson(): org.json.JSONArray {
            val arr = org.json.JSONArray()
            arr.put(buildListFilesTool())
            arr.put(buildReadFileTool())
            arr.put(buildGrepTool())
            arr.put(buildSearchCodebaseTool())
            arr.put(buildGlobTool())
            arr.put(buildSearchConversationMemoryTool())
            arr.put(buildGetRecentConversationMemoryTool())
            return arr
        }

        fun toolNames(): List<String> = listOf(
            "listFiles", "readFile", "grep", "searchCodebase", "glob",
            "searchConversationMemory", "getRecentConversationMemory",
        )

        private fun buildListFilesTool() = toolDef("listFiles",
            "列出目录内容，显示[FILE]/[DIR]前缀和文件大小。路径支持绝对或相对，留空表示项目根目录。",
            props = arrayOf(
                "path" to p("string", "目录路径，支持绝对路径或相对项目根目录，留空表示根目录"),
                "ignore" to p("string", "忽略模式，逗号分隔的glob，如'*.class,*.jar'"),
            ),
        )
        private fun buildReadFileTool() = toolDef("readFile",
            "读取文件内容，返回纯文本无行号前缀。支持分页：offset从0开始，limit默认1000。超500行自动截断为前200行。",
            listOf("file_path"),
            "file_path" to p("string", "文件路径 — 绝对路径或相对项目根目录"),
            "offset" to p("integer", "起始行号，从0开始计数，默认0"),
            "limit" to p("integer", "最大读取行数，默认1000"),
        )
        private fun buildGrepTool() = toolDef("grep",
            "正则搜索文件内容，返回匹配行。支持过滤、上下文、分页等多种模式。",
            listOf("pattern"),
            "pattern" to p("string", "正则表达式，如'fun\\s+\\w+'查找函数定义"),
            "path" to p("string", "限定搜索目录，相对项目根目录，留空搜整个项目"),
            "glob" to p("string", "Glob模式过滤，如'*.kt'或'src/**/*.java'"),
            "output_mode" to p("string", "输出模式：content(默认)显示匹配行，files_with_matches仅列文件名，count仅计数"),
            "-n" to p("boolean", "显示行号（默认已在输出中）"),
            "-i" to p("boolean", "忽略大小写，默认true"),
            "type" to p("string", "文件类型过滤，如'kt'只查Kotlin，留空查所有"),
            "head_limit" to p("integer", "最大返回结果行数，默认100"),
            "offset" to p("integer", "跳过的结果行数，用于分页，默认0"),
            "multiline" to p("boolean", "启用多行模式(.匹配换行符)，默认false"),
            "-C" to p("integer", "匹配前后上下文行数，默认2"),
            "-B" to p("integer", "匹配前行数（覆盖-C的上前缀），默认0"),
            "-A" to p("integer", "匹配后行数（覆盖-C的下后缀），默认0"),
        )
        private fun buildSearchCodebaseTool() = toolDef("searchCodebase",
            "语义搜索代码库，按含义查找相关代码。适合探索陌生代码或用自然语言描述找实现。比grep更适合模糊查询。",
            listOf("information_request"),
            "information_request" to p("string", "自然语言描述，如'用户认证在哪'或'错误处理怎么工作'"),
            "target_directories" to p("string", "限定搜索目录，相对项目根目录，留空搜索整个项目"),
        )
        private fun buildGlobTool() = toolDef("glob",
            "按文件名glob模式搜索，如'*.kt'、'Main*'。递归搜索项目根目录。适合知道文件名但不知完整路径时。",
            listOf("pattern"),
            "pattern" to p("string", "Glob模式，如'*.kt'所有Kotlin文件，'Main*'Main开头文件，'**/*.xml'所有XML"),
            "path" to p("string", "限定搜索目录，相对项目根目录，留空从根目录开始"),
            "maxResults" to p("integer", "最大返回结果数，默认100"),
        )
        private fun buildSearchConversationMemoryTool() = toolDef("searchConversationMemory",
            "按关键词搜索对话历史。需要回忆之前讨论内容时使用。返回匹配条目前200字符预览，最多5条。仅搜索当前对话记忆。",
            listOf("query"),
            "query" to p("string", "搜索内容，如'文件结构'或'错误修复'"),
        )
        private fun buildGetRecentConversationMemoryTool() = toolDef("getRecentConversationMemory",
            "获取最近对话消息全文。每次返回最近指定条数，count默认1。",
            props = arrayOf("count" to p("integer", "最近条目数，默认1")),
        )
    }
}
