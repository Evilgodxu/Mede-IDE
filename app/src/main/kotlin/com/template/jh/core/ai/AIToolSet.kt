package com.template.jh.core.ai

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import com.google.ai.edge.litertlm.Tool
import com.google.ai.edge.litertlm.ToolParam
import com.google.ai.edge.litertlm.ToolSet
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

    @Tool(description = "Read the content of a file in the project. Must be called before modifying a file.")
    fun readFile(
        @ToolParam(description = "File path relative to project root, e.g. 'src/MainActivity.kt'") path: String,
    ): String {
        val uri = projectUri
        if (uri != null) {
            val docUri = resolveSafChild(uri, path) ?: return "File not found: $path"
            return context.contentResolver.openInputStream(docUri)?.bufferedReader()?.readText()
                ?: "Failed to read file: $path"
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
                FileOperationEvents.notify(path, "create", content.lines().size)
                return "File written: $path (${content.lines().size} lines)"
            } catch (e: Exception) {
                return "Failed to write file: ${e.message}"
            }
        }
        return "No project folder is open."
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
            val url = URL("https://lite.duckduckgo.com/lite/?q=$encoded")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000; readTimeout = 8000
                setRequestProperty("User-Agent", "Mozilla/5.0")
            }
            val html = conn.inputStream.bufferedReader().readText()
            conn.disconnect()
            val results = Regex("""class="result-snippet"[^>]*>(.*?)</td>""", RegexOption.DOT_MATCHES_ALL)
                .findAll(html).take(3).joinToString("\n") {
                    it.groupValues[1].replace(Regex("<[^>]*>"), "").trim()
                }
            if (results.isBlank()) "Search returned no results."
            else "Search results:\n$results"
        }.getOrDefault("Search failed. Answer based on your knowledge.")
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
