package com.template.jh.core.ai

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import com.google.ai.edge.litertlm.Tool
import com.google.ai.edge.litertlm.ToolParam
import com.google.ai.edge.litertlm.ToolSet
import com.template.jh.core.editor.applyPatches
import com.template.jh.core.editor.PatchOp
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

// 严格遵循 LiteRT-LM 官方示例 ToolMain.kt 模式：
//   单一 ToolSet 类 → @Tool 方法 → @ToolParam 参数 → 返回简单类型
class AIToolSet(private val context: Context) : ToolSet {

    // SAF 项目根 URI（用户通过文件夹选择器打开的目录）
    @Volatile var projectUri: Uri? = null

    // ---- 文件操作 ----

    @Tool(description = "List files and directories in the project folder. Returns directory contents with file sizes.")
    fun listFiles(
        @ToolParam(description = "Subdirectory path relative to project root. Leave empty to list root.") subPath: String = "",
    ): String {
        val uri = projectUri
        if (uri != null) return listViaSaf(uri, subPath)
        return "No project folder is open. Please open a folder first."
    }

    // 读取原始内容（无行号，供 applyPatch 内部使用）
    private fun readFileRaw(path: String): String? {
        val uri = projectUri ?: return null
        val docUri = resolveSafChild(uri, path) ?: return null
        return try {
            context.contentResolver.openInputStream(docUri)?.bufferedReader()?.readText()
        } catch (_: Exception) { null }
    }

    @Tool(description = "Read the content of a file in the project. Must be called before modifying a file. Returns content with line numbers.")
    fun readFile(
        @ToolParam(description = "File path relative to project root, e.g. 'src/MainActivity.kt'") path: String,
    ): String {
        val uri = projectUri
        if (uri != null) {
            val text = readFileRaw(path) ?: return "File not found: $path"
            val lineNumWidth = text.lines().size.toString().length
            return text.lines().mapIndexed { i, line ->
                "${(i + 1).toString().padStart(lineNumWidth)}: $line"
            }.joinToString("\n")
        }
        return "No project folder is open."
    }

    @Tool(description = "Create a new file or overwrite an existing file with the given content.")
    fun writeFile(
        @ToolParam(description = "File path relative to project root, e.g. 'src/App.kt'") path: String,
        @ToolParam(description = "The complete text content to write to the file") content: String,
    ): String {
        val uri = projectUri
        if (uri != null) {
            try {
                val parentPath = path.substringBeforeLast('/')
                if (parentPath.isNotEmpty() && parentPath != path) ensureSafDir(uri, parentPath)
                val existing = resolveSafChild(uri, path)
                val targetUri = if (existing != null) existing
                    else createSafFile(uri, path)
                if (targetUri == null) return "Failed to create file: $path"
                context.contentResolver.openOutputStream(targetUri, "wt")?.use { out ->
                    out.write(content.toByteArray(Charsets.UTF_8))
                } ?: return "Failed to write file: $path"
                FileOperationEvents.notify(path, "create", content.lines().size, newContent = content)
                return "File written: $path (${content.lines().size} lines)"
            } catch (e: Exception) {
                return "Failed to write file: ${e.message}"
            }
        }
        return "No project folder is open."
    }

    // 行级差异编辑：对大模型友好的 patch 格式
    // patches 是 JSON 数组，每个元素: {"type":"replace"|"insert"|"delete", "startLine":int, "endLine":int, "content":"..."}
    @Tool(description = "Apply line-level changes to an existing file. Use this instead of writeFile when only parts need modification. Returns summary of changes.")
    fun applyPatch(
        @ToolParam(description = "File path relative to project root") path: String,
        @ToolParam(description = "JSON array of patches. Each: {\"type\":\"replace\"|\"insert\"|\"delete\", \"startLine\":int (1-based), \"endLine\":int (exclusive), \"content\":\"new text\"}") patchesJson: String,
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
            val parentPath = path.substringBeforeLast('/')
            if (parentPath.isNotEmpty() && parentPath != path) ensureSafDir(uri, parentPath)
            val existing = resolveSafChild(uri, path)
            val targetUri = if (existing != null) existing else createSafFile(uri, path)
            if (targetUri == null) return "Failed to create file: $path"
            context.contentResolver.openOutputStream(targetUri, "wt")?.use { out ->
                out.write(newContent.toByteArray(Charsets.UTF_8))
            } ?: return "Failed to write file: $path"
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
        return runCatching {
            val encoded = URLEncoder.encode(query, "UTF-8")
            // 使用 DuckDuckGo HTML 搜索（非 Lite 版，结构更稳定）
            val url = URL("https://html.duckduckgo.com/html/?q=$encoded")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 10000; readTimeout = 10000
                setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            }
            val html = conn.inputStream.bufferedReader().readText()
            conn.disconnect()

            // 提取标题 + 摘要（DDG HTML 搜索使用 result__a + result__snippet）
            val pattern = Regex(
                """class="result__a"[^>]*>\s*(.*?)\s*</a>.*?class="result__snippet"[^>]*>\s*(.*?)\s*</a>""",
                setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
            )
            val results = pattern.findAll(html).take(5).map { match ->
                val title = match.groupValues[1].replace(Regex("<[^>]*>"), "").trim()
                val snippet = match.groupValues[2].replace(Regex("<[^>]*>"), "").trim()
                "• $title\n  $snippet"
            }.toList()

            if (results.isEmpty()) "Search returned no results for: $query"
            else results.joinToString("\n---\n")
        }.getOrDefault("Search failed. Answer based on your knowledge.")
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

    // ---- SAF 辅助方法 ----

    private fun listViaSaf(treeUri: Uri, subPath: String): String {
        return try {
            val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
            val childrenUri = if (subPath.isBlank()) {
                DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, rootDocId)
            } else {
                val docId = findDocId(treeUri, rootDocId, subPath.trimStart('/'))
                    ?: return "Directory not found: $subPath"
                DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
            }
            val items = mutableListOf<String>()
            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE,
            )
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { c ->
                val nameCol = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeCol = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val sizeCol = c.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                while (c.moveToNext()) {
                    val name = c.getString(nameCol) ?: continue
                    val isDir = c.getString(mimeCol) == DocumentsContract.Document.MIME_TYPE_DIR
                    val size = if (sizeCol >= 0) c.getLong(sizeCol) else 0L
                    val tag = if (isDir) "[DIR]" else "[FILE]"
                    val sizeStr = if (!isDir && size > 0) " (${formatSize(size)})" else ""
                    items.add("$tag $name$sizeStr")
                }
            }
            if (items.isEmpty()) return "Empty directory."
            val displayName = try { DocumentsContract.getTreeDocumentId(treeUri) } catch (_: Exception) { "project" }
            buildString {
                appendLine("Project root: $displayName")
                appendLine("---")
                items.sorted().forEach { appendLine(it) }
            }.trimEnd()
        } catch (e: Exception) {
            "Failed to list files: ${e.message}"
        }
    }

    private fun resolveSafChild(treeUri: Uri, path: String): Uri? {
        return try {
            val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
            val docId = findDocId(treeUri, rootDocId, path.trimStart('/')) ?: return null
            DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
        } catch (_: Exception) { null }
    }

    private fun findDocId(treeUri: Uri, parentDocId: String, path: String): String? {
        if (path.isEmpty()) return parentDocId
        val segments = path.split('/')
        var current = parentDocId
        for (seg in segments) {
            if (seg.isEmpty()) continue
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, current)
            var found: String? = null
            context.contentResolver.query(
                childrenUri,
                arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null, null, null
            )?.use { c ->
                val idCol = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameCol = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                while (c.moveToNext()) {
                    if (c.getString(nameCol) == seg) { found = c.getString(idCol); break }
                }
            }
            if (found == null) return null
            current = found
        }
        return current
    }

    private fun ensureSafDir(treeUri: Uri, path: String) {
        val parts = path.trimStart('/').split('/')
        var currentDocId = try { DocumentsContract.getTreeDocumentId(treeUri) } catch (_: Exception) { return }
        for (part in parts) {
            if (part.isEmpty()) continue
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, currentDocId)
            var found: String? = null
            context.contentResolver.query(
                childrenUri,
                arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null, null, null
            )?.use { c ->
                val idCol = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameCol = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                while (c.moveToNext()) { if (c.getString(nameCol) == part) { found = c.getString(idCol); break } }
            }
            if (found != null) { currentDocId = found; continue }
            // 创建目录
            val parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, currentDocId)
            val newDoc = DocumentsContract.createDocument(
                context.contentResolver, parentUri,
                DocumentsContract.Document.MIME_TYPE_DIR, part
            ) ?: return
            currentDocId = DocumentsContract.getDocumentId(newDoc)
        }
    }

    private fun createSafFile(treeUri: Uri, path: String): Uri? {
        try {
            val fileName = path.substringAfterLast('/')
            val parentPath = path.substringBeforeLast('/')
            if (parentPath.isNotEmpty() && parentPath != path) {
                val parentDocId = findDocId(treeUri,
                    DocumentsContract.getTreeDocumentId(treeUri), parentPath.trimStart('/'))
                    ?: return null
                val parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, parentDocId)
                return DocumentsContract.createDocument(context.contentResolver, parentUri, "application/octet-stream", fileName)
            } else {
                val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
                val rootUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, rootDocId)
                return DocumentsContract.createDocument(context.contentResolver, rootUri, "application/octet-stream", fileName)
            }
        } catch (_: Exception) { return null }
    }

    private fun formatSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> "${"%.1f".format(bytes.toDouble() / (1024 * 1024))} MB"
    }
}
