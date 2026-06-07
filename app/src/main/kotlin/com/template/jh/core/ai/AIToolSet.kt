package com.template.jh.core.ai

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.documentfile.provider.DocumentFile
import com.google.ai.edge.litertlm.Tool
import com.google.ai.edge.litertlm.ToolParam
import com.google.ai.edge.litertlm.ToolSet
import com.template.jh.core.editor.applyPatches
import com.template.jh.core.editor.PatchOp
import com.template.jh.core.storage.FileManager
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

//   单一 ToolSet 类 → @Tool 方法 → @ToolParam 参数 → 返回简单类型
class AIToolSet(
    private val context: Context,
    private val fileManager: FileManager? = null
) : ToolSet {

    // SAF 项目根 URI（用户通过文件夹选择器打开的目录）
    // 优先使用外部传入的 fileManager，否则使用内部 projectUri
    @Volatile var projectUri: Uri? = null
        set(value) {
            field = value
            value?.let { fileManager?.setProjectUri(it) }
        }

    // ---- 文件操作 ----

    @Tool(description = "List files and directories in the project folder. Returns directory contents with file sizes.")
    fun listFiles(
        @ToolParam(description = "Subdirectory path relative to project root. Leave empty to list root.") subPath: String = "",
    ): String {
        // 优先使用外部传入的 FileManager（原生 DocumentFile API 实现）
        fileManager?.let { return it.listFiles(subPath) }
        // 兼容回退：使用纯 DocumentFile API
        return listFilesNative(subPath)
    }

    // 纯 DocumentFile API 实现，无需 DocumentsContract
    private fun listFilesNative(subPath: String): String {
        val uri = projectUri ?: return "No project folder is open. Please open a folder first."
        val rootDoc = DocumentFile.fromTreeUri(context, uri) ?: return "Failed to access project folder."

        val targetDoc = if (subPath.isBlank()) {
            rootDoc
        } else {
            navigatePath(rootDoc, subPath.trim('/')) ?: return "Directory not found: $subPath"
        }

        if (!targetDoc.isDirectory) return "Not a directory: $subPath"

        return try {
            val children = targetDoc.listFiles() ?: return "Empty directory."
            if (children.isEmpty()) return "Empty directory."

            val sorted = children.sortedWith(
                compareByDescending<DocumentFile> { it.isDirectory }
                    .thenBy { it.name?.lowercase() ?: "" }
            )

            val displayPath = if (subPath.isBlank()) "project root" else subPath.trim('/')
            buildString {
                appendLine("Path: $displayPath")
                appendLine("---")
                sorted.forEach { doc ->
                    val name = doc.name ?: return@forEach
                    val tag = if (doc.isDirectory) "[DIR]" else "[FILE]"
                    val sizeStr = if (!doc.isDirectory && doc.length() > 0) " (${formatSize(doc.length())})" else ""
                    appendLine("$tag $name$sizeStr")
                }
            }.trimEnd()
        } catch (e: Exception) {
            "Failed to list files: ${e.message}"
        }
    }

    // 读取原始内容（无行号，供 applyPatch 内部使用）
    private fun readFileRaw(path: String): String? {
        // 优先使用 FileManager
        fileManager?.let { return it.readFileRaw(path) }
        // 兼容回退：纯 DocumentFile API
        return readFileNativeRaw(path)
    }

    private fun readFileNativeRaw(path: String): String? {
        val uri = projectUri ?: return null
        val rootDoc = DocumentFile.fromTreeUri(context, uri) ?: return null
        val doc = navigatePath(rootDoc, path.trim('/')) ?: return null
        if (doc.isDirectory) return null

        return try {
            context.contentResolver.openInputStream(doc.uri)
                ?.bufferedReader()
                ?.use { it.readText() }
        } catch (_: Exception) {
            null
        }
    }

    @Tool(description = "Read the content of a file in the project. Must be called before modifying a file. Returns content with line numbers.")
    fun readFile(
        @ToolParam(description = "File path relative to project root, e.g. 'src/MainActivity.kt'") path: String,
    ): String {
        // 优先使用 FileManager
        fileManager?.let { return it.readFileWithLineNumbers(path) }
        // 兼容回退
        val text = readFileNativeRaw(path) ?: return "File not found: $path"
        val lineNumWidth = text.lines().size.toString().length
        return text.lines().mapIndexed { i, line ->
            "${(i + 1).toString().padStart(lineNumWidth)}: $line"
        }.joinToString("\n")
    }

    @Tool(description = "Create a new file or overwrite an existing file with the given content.")
    fun writeFile(
        @ToolParam(description = "File path relative to project root, e.g. 'src/App.kt'") path: String,
        @ToolParam(description = "The complete text content to write to the file") content: String,
    ): String {
        // 优先使用 FileManager
        fileManager?.let {
            val result = it.writeFile(path, content)
            if (!result.startsWith("Failed") && !result.startsWith("No project")) {
                FileOperationEvents.notify(path, if (it.exists(path)) "overwrite" else "create", content.lines().size, newContent = content)
            }
            return result
        }
        // 兼容回退：纯 DocumentFile API
        return writeFileNative(path, content)
    }

    private fun writeFileNative(path: String, content: String): String {
        val uri = projectUri ?: return "No project folder is open."
        val rootDoc = DocumentFile.fromTreeUri(context, uri) ?: return "Failed to access project folder."

        return try {
            val trimmedPath = path.trim('/')
            val fileName = trimmedPath.substringAfterLast('/')
            val parentPath = trimmedPath.substringBeforeLast('/', "")

            // 确保父目录存在
            val parentDoc = if (parentPath.isEmpty()) {
                rootDoc
            } else {
                ensureDirectoryNative(rootDoc, parentPath) ?: return "Failed to create parent directory: $parentPath"
            }

            // 查找或创建文件
            val existingFile = parentDoc.findFile(fileName)
            val targetDoc = existingFile ?: parentDoc.createFile("application/octet-stream", fileName)
            ?: return "Failed to create file: $path"

            // 写入内容
            context.contentResolver.openOutputStream(targetDoc.uri, "wt")?.use { out ->
                out.write(content.toByteArray(Charsets.UTF_8))
            } ?: return "Failed to write file: $path"

            val opName = if (existingFile != null) "overwrite" else "create"
            FileOperationEvents.notify(path, opName, content.lines().size, newContent = content)
            "File written: $path (${content.lines().size} lines, $opName)"
        } catch (e: Exception) {
            "Failed to write file: ${e.message}"
        }
    }

    // 行级差异编辑：对大模型友好的 patch 格式
    // patches 是 JSON 数组，每个元素: {"type":"replace"|"insert"|"delete", "startLine":int, "endLine":int, "content":"..."}
    @Tool(description = "Apply line-level changes to an existing file. Use this instead of writeFile when only parts need modification. Returns summary of changes.")
    fun applyPatch(
        @ToolParam(description = "File path relative to project root") path: String,
        @ToolParam(description = """JSON array of patches. Each: {"type":"replace"|"insert"|"delete", "startLine":int (1-based), "endLine":int (exclusive), "content":"new text"}""") patchesJson: String,
    ): String {
        val uri = projectUri
        if (uri == null) return "No project folder is open."
        return try {
            val original = readFileRaw(path)
            if (original == null) {
                return "Cannot patch: file not found or not open. Use writeFile to create first."
            }
            val patches = org.json.JSONArray(patchesJson)
            val patchOps = (0 until patches.length()).map { i ->
                val obj = patches.getJSONObject(i)
                PatchOp(
                    type = obj.optString("type", "replace"),
                    startLine = obj.optInt("startLine", 0),
                    endLine = obj.optInt("endLine", 0),
                    content = obj.optString("content", ""),
                )
            }
            val newContent = applyPatches(original, patchOps)

            // 使用 FileManager 或原生方法写入
            val result = fileManager?.writeFile(path, newContent) ?: writeFileNative(path, newContent)
            if (result.startsWith("Failed") || result.startsWith("No project")) {
                return "Patch failed: $result"
            }

            FileOperationEvents.notify(path, "modify", computeChangedLines(original, newContent), original, newContent)
            "Patched $path (${patchOps.size} ops applied)"
        } catch (e: Exception) {
            "Patch failed: ${e.message}"
        }
    }

    private fun computeChangedLines(old: String, new: String): Int {
        val o = old.lines(); val n = new.lines()
        return (maxOf(o.size, n.size) - (o.zip(n).count { it.first == it.second })).coerceAtLeast(1)
    }

    // ---- 终端命令 ----

    @Tool(description = "Run a shell command in the project directory. Use for adb, git, gradle commands.")
    fun runCommand(
        @ToolParam(description = "The command to execute, e.g. 'ls -la' or 'git status'") command: String,
    ): String {
        return try {
            val dir = if (projectUri != null) File(context.filesDir, "workspace").also { it.mkdirs() }
                else File(context.filesDir, "workspace").also { it.mkdirs() }
            val parts = command.split(" ")
            val pb = ProcessBuilder(parts).directory(dir).redirectErrorStream(true)
            val proc = pb.start()
            val text = proc.inputStream.bufferedReader().readText()
            proc.waitFor(30, java.util.concurrent.TimeUnit.SECONDS)
            proc.destroy()
            if (text.isBlank()) "Command completed with no output." else text.take(5000)
        } catch (e: Exception) {
            "Command failed: ${e.message}"
        }
    }

    // ---- 联网搜索 ----

    @Tool(description = "Search the internet for current information. Use when you need up-to-date data or cannot answer from knowledge.")
    fun searchWeb(
        @ToolParam(description = "Search query keywords, concise and specific") query: String,
    ): String {
        if (query.isBlank()) return "Search query is empty."
        return try {
            searchViaDuckDuckGo(query)
        } catch (e: Exception) {
            "Search failed: ${e.message}"
        }
    }

    private fun searchViaDuckDuckGo(query: String): String {
        val encoded = URLEncoder.encode(query, "UTF-8")

        val urls = listOf(
            "https://lite.duckduckgo.com/lite/?q=$encoded",
            "https://html.duckduckgo.com/html/?q=$encoded",
        )

        for (searchUrl in urls) {
            try {
                val html = fetchUrl(searchUrl)
                val results = parseDdgResults(html)
                if (results.isNotEmpty()) {
                    return results.joinToString("\n---\n") { "• ${it.first}\n  ${it.second}" }
                }
            } catch (_: Exception) { }
        }

        try {
            val apiUrl = "https://api.duckduckgo.com/?q=$encoded&format=json&no_html=1&skip_disambig=1"
            val json = fetchUrl(apiUrl)
            val obj = org.json.JSONObject(json)
            val abstractText = obj.optString("AbstractText", "")
            val answer = obj.optString("Answer", "")
            val heading = obj.optString("Heading", "")
            val results = mutableListOf<String>()
            if (abstractText.isNotBlank()) results.add("$heading\n$abstractText")
            if (answer.isNotBlank() && answer != abstractText) results.add("Answer: $answer")
            val topics = obj.optJSONArray("RelatedTopics")
            if (topics != null) {
                for (i in 0 until minOf(topics.length(), 5)) {
                    val topic = topics.optJSONObject(i) ?: continue
                    val text = topic.optString("Text", "")
                    if (text.isNotBlank()) results.add("• ${text.take(200)}")
                }
            }
            if (results.isNotEmpty()) return results.joinToString("\n---\n")
        } catch (_: Exception) { }

        return "Search returned no results for: $query"
    }

    private fun fetchUrl(urlString: String): String {
        val url = URL(urlString)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000; readTimeout = 15000
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36")
            setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
        }
        val html = conn.inputStream.bufferedReader().readText()
        val code = conn.responseCode
        conn.disconnect()
        if (code != 200) throw RuntimeException("HTTP $code")
        return html
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

    // ---- Git 集成 ----

    @Tool(description = "Show git status (short format). Returns staged/unstaged changes.")
    fun gitStatus(): String = runGit("git status --short")

    @Tool(description = "Stage files for commit. Use '.' to stage all.")
    fun gitAdd(
        @ToolParam(description = "File paths to stage, space-separated. Use '.' for all") paths: String = ".",
    ): String = runGit("git add $paths")

    @Tool(description = "Commit staged changes with a message.")
    fun gitCommit(
        @ToolParam(description = "Commit message") message: String,
    ): String = runGit("git commit -m \"$message\"")

    @Tool(description = "Push commits to remote repository.")
    fun gitPush(
        @ToolParam(description = "Remote name, default 'origin'") remote: String = "origin",
        @ToolParam(description = "Branch name, e.g. 'main' or 'master'") branch: String = "main",
    ): String = runGit("git push $remote $branch")

    @Tool(description = "List local branches. Add '-a' to show remote branches too.")
    fun gitBranch(
        @ToolParam(description = "Extra args, e.g. '-a' for all, '-D name' to delete") args: String = "",
    ): String = runGit("git branch $args")

    @Tool(description = "Show diff of staged/unstaged changes.")
    fun gitDiff(
        @ToolParam(description = "Args: '--staged' for staged only, 'HEAD~1' for last commit") args: String = "",
    ): String = runGit("git diff $args")

    private fun runGit(cmd: String): String {
        val dir = if (projectUri != null) File(context.filesDir, "workspace").also { it.mkdirs() }
            else File(context.filesDir, "workspace").also { it.mkdirs() }
        return try {
            val pb = if (isWindows()) {
                ProcessBuilder("cmd", "/c", cmd).directory(dir).redirectErrorStream(true)
            } else {
                ProcessBuilder("sh", "-c", cmd).directory(dir).redirectErrorStream(true)
            }
            val proc = pb.start()
            val text = proc.inputStream.bufferedReader().readText()
            proc.waitFor(15, java.util.concurrent.TimeUnit.SECONDS)
            proc.destroy()
            if (text.isBlank()) "已完成，无输出。" else text.take(5000)
        } catch (e: Exception) { "命令失败: ${e.message}" }
    }

    private fun isWindows(): Boolean = System.getProperty("os.name").lowercase().contains("win")

    // ---- Lint/诊断读取 ----

    @Tool(description = "Read build lint or compilation errors from the project. Runs gradle lint if needed.")
    fun readLints(): String {
        return try {
            val buildDir = File(context.filesDir, "workspace")
            // 1. 尝试读取 lint 报告
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
                    if (issues.isNotBlank()) return "Lint errors:\n$issues"
                }
            }
            // 2. 尝试读取 Gradle 问题报告
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
                    if (errors.isNotEmpty()) return "Compilation problems:\n${errors.joinToString("\n")}"
                }
            }
            // 3. 尝试运行 gradle lint（简短超时）
            val pb = if (isWindows()) ProcessBuilder("cmd", "/c", "gradlew lint")
                else ProcessBuilder("sh", "-c", "./gradlew lint")
            pb.directory(buildDir).redirectErrorStream(true)
            val proc = pb.start()
            val out = proc.inputStream.bufferedReader().readText()
            proc.waitFor(60, java.util.concurrent.TimeUnit.SECONDS)
            proc.destroy()
            // 从输出中提取错误
            val errors = out.lines().filter { it.contains("ERROR") || it.contains("error:") }.take(30)
            if (errors.isNotEmpty()) "Lint output errors:\n${errors.joinToString("\n")}" else "No lint errors found."
        } catch (e: Exception) { "读取诊断失败: ${e.message}" }
    }

    // ---- 文件搜索 ----

    @Tool(description = "Search file contents in the project for a query string. Returns matching files with line numbers. Use when you need to find where something is defined or used.")
    fun searchInFiles(
        @ToolParam(description = "Search query, case-insensitive. Supports plain text only (no regex).") query: String,
        @ToolParam(description = "File extension filter, e.g. 'kt' for Kotlin files only. Leave empty for all text files.") extension: String = "",
    ): String {
        // 优先使用 FileManager
        fileManager?.let { return it.searchInFiles(query, extension) }
        // 兼容回退：纯 DocumentFile API
        return searchInFilesNative(query, extension)
    }

    private fun searchInFilesNative(query: String, extension: String): String {
        val uri = projectUri ?: return "No project folder is open. Please open a folder first."
        if (query.isBlank()) return "Search query is empty."

        val rootDoc = DocumentFile.fromTreeUri(context, uri) ?: return "Failed to access project folder."

        return try {
            val results = mutableListOf<String>()
            val searchedFiles = mutableListOf<String>()
            val queryLower = query.lowercase()
            val extLower = extension.lowercase().trimStart('.')

            searchRecursiveNative(rootDoc, "", queryLower, extLower, results, searchedFiles)

            if (results.isEmpty()) {
                buildString {
                    appendLine("No matches found for \"$query\"${if (extension.isNotBlank()) " in *.$extension files" else ""}.")
                    appendLine("Searched ${searchedFiles.size} files.")
                    if (searchedFiles.isNotEmpty()) {
                        appendLine("Sample files searched:")
                        searchedFiles.take(10).forEach { appendLine("  - $it") }
                    }
                }
            } else {
                buildString {
                    appendLine("Found ${results.size} match${if (results.size != 1) "es" else ""} for \"$query\":")
                    appendLine("---")
                    results.take(50).forEach { appendLine(it) }
                    if (results.size > 50) appendLine("... and ${results.size - 50} more matches")
                }.trimEnd()
            }
        } catch (e: Exception) {
            copyErrorToClipboard(e)
            "Search failed (error copied to clipboard): ${e.message}"
        }
    }

    // ---- 纯 DocumentFile API 辅助方法 ----

    // 逐级导航路径，返回目标 DocumentFile
    private fun navigatePath(root: DocumentFile, relativePath: String): DocumentFile? {
        if (relativePath.isBlank()) return root
        return try {
            var current = root
            for (segment in relativePath.split('/')) {
                if (segment.isEmpty()) continue
                current = current.findFile(segment) ?: return null
            }
            current
        } catch (_: Exception) {
            null
        }
    }

    // 确保目录存在，不存在则创建
    private fun ensureDirectoryNative(root: DocumentFile, relativePath: String): DocumentFile? {
        if (relativePath.isBlank()) return root
        return try {
            var current = root
            for (part in relativePath.split('/')) {
                if (part.isEmpty()) continue
                val child = current.findFile(part)
                current = if (child != null) {
                    child
                } else {
                    current.createDirectory(part) ?: return null
                }
            }
            current
        } catch (_: Exception) {
            null
        }
    }

    // 递归搜索文件内容
    private fun searchRecursiveNative(
        dirDoc: DocumentFile,
        relativePath: String,
        queryLower: String,
        extLower: String,
        results: MutableList<String>,
        searchedFiles: MutableList<String>,
    ) {
        if (results.size >= 100) return

        val children = dirDoc.listFiles() ?: return
        val searchableExtensions = setOf(
            "kt", "kts", "java", "xml", "json", "yml", "yaml", "properties",
            "txt", "md", "gradle", "toml", "cfg", "conf", "ini",
            "html", "css", "js", "ts", "sql", "sh", "bat", "py",
        )

        for (doc in children) {
            val name = doc.name ?: continue
            if (name.startsWith(".")) continue

            val currentPath = if (relativePath.isEmpty()) name else "$relativePath/$name"

            if (doc.isDirectory) {
                // 限制递归深度
                if (currentPath.count { it == '/' } < 10) {
                    searchRecursiveNative(doc, currentPath, queryLower, extLower, results, searchedFiles)
                }
            } else {
                // 检查扩展名
                val fileExt = name.substringAfterLast('.', "").lowercase()
                if (extLower.isNotBlank()) {
                    if (fileExt != extLower) continue
                } else {
                    if (fileExt.isNotEmpty() && fileExt !in searchableExtensions) continue
                }

                // 跳过大文件
                if (doc.length() > 512 * 1024) continue

                searchedFiles.add(currentPath)

                // 读取并搜索
                try {
                    val text = context.contentResolver.openInputStream(doc.uri)
                        ?.bufferedReader()
                        ?.use { it.readText() }
                        ?: continue

                    text.lines().forEachIndexed { idx, line ->
                        if (line.contains(queryLower, ignoreCase = true)) {
                            val trimmed = line.trim().take(120)
                            results.add("$currentPath:${idx + 1}:  $trimmed")
                        }
                    }
                } catch (_: Exception) { }

                if (results.size >= 100) return
            }
        }
    }

    private fun formatSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> "${"%.1f".format(bytes.toDouble() / (1024 * 1024))} MB"
    }

    private fun copyErrorToClipboard(e: Exception) {
        try {
            val sw = java.io.StringWriter()
            e.printStackTrace(java.io.PrintWriter(sw))
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("error", sw.toString()))
        } catch (_: Exception) { }
    }
}
