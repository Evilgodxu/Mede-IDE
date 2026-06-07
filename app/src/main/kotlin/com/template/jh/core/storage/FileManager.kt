package com.template.jh.core.storage

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile

/**
 * 统一的文件管理器，使用 DocumentFile 公共 API 实现 SAF 操作
 * 兼容所有 Android 设备，避免使用 DocumentsContract 私有 API
 */
class FileManager(private val context: Context) {

    // 当前项目根目录 DocumentFile
    private var rootDocFile: DocumentFile? = null

    // 当前项目根目录 URI
    var projectUri: Uri? = null
        private set

    // 可搜索的文本文件扩展名
    private val searchableExtensions = setOf(
        "kt", "kts", "java", "xml", "json", "yml", "yaml", "properties",
        "txt", "md", "gradle", "toml", "cfg", "conf", "ini",
        "html", "css", "js", "ts", "sql", "sh", "bat", "py",
    )

    /**
     * 设置项目根目录
     */
    fun setProjectUri(uri: Uri) {
        // 持久化权限
        val takeFlags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        context.contentResolver.takePersistableUriPermission(uri, takeFlags)
        projectUri = uri
        rootDocFile = DocumentFile.fromTreeUri(context, uri)
    }

    /**
     * 清除项目根目录
     */
    fun clearProjectUri() {
        projectUri = null
        rootDocFile = null
    }

    /**
     * 根据相对路径获取 DocumentFile
     * 纯 DocumentFile API 实现，逐级 findFile
     */
    private fun resolvePath(relativePath: String): DocumentFile? {
        val root = rootDocFile ?: return null
        if (relativePath.isBlank()) return root

        return try {
            var current = root
            for (segment in relativePath.trim('/').split('/')) {
                if (segment.isEmpty()) continue
                current = current.findFile(segment) ?: return null
            }
            current
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 列出指定路径下的文件和目录
     * @param subPath 相对于项目根目录的路径，空字符串表示根目录
     * @return 文件列表字符串，包含 [DIR]/[FILE] 标记
     */
    fun listFiles(subPath: String = ""): String {
        val targetDoc = resolvePath(subPath.trim('/'))
            ?: return if (subPath.isBlank()) "No project folder is open." else "Directory not found: $subPath"

        if (!targetDoc.isDirectory) return "Not a directory: $subPath"

        return try {
            val children = targetDoc.listFiles()
            if (children.isEmpty()) return "Empty directory."

            // 排序：目录在前，按名称排序
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

    /**
     * 获取指定路径下的文件和目录列表（结构化数据）
     * @param subPath 相对于项目根目录的路径
     * @return FileNode 列表
     */
    fun listFilesAsNodes(subPath: String = ""): List<FileNode> {
        val targetDoc = resolvePath(subPath.trim('/')) ?: return emptyList()
        if (!targetDoc.isDirectory) return emptyList()

        return try {
            val children = targetDoc.listFiles()
            children.map { doc ->
                val name = doc.name ?: ""
                val relPath = if (subPath.isBlank()) name else "${subPath.trim('/')}/$name"
                FileNode(
                    name = name,
                    path = relPath,
                    isDirectory = doc.isDirectory,
                    size = doc.length(),
                    uri = doc.uri
                )
            }.sortedWith(
                compareByDescending<FileNode> { it.isDirectory }
                    .thenBy { it.name.lowercase() }
            )
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * 读取文件内容（原始内容）
     * @param path 相对于项目根目录的文件路径
     * @return 文件内容，失败返回 null
     */
    fun readFileRaw(path: String): String? {
        val doc = resolvePath(path.trim('/')) ?: return null
        if (doc.isDirectory) return null

        return try {
            context.contentResolver.openInputStream(doc.uri)
                ?.bufferedReader()
                ?.use { it.readText() }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 读取文件内容（带行号）
     * @param path 相对于项目根目录的文件路径
     * @return 带行号的文件内容
     */
    fun readFileWithLineNumbers(path: String): String {
        val text = readFileRaw(path) ?: return "File not found: $path"
        val lineNumWidth = text.lines().size.toString().length
        return text.lines().mapIndexed { i, line ->
            "${(i + 1).toString().padStart(lineNumWidth)}: $line"
        }.joinToString("\n")
    }

    /**
     * 写入文件内容
     * @param path 相对于项目根目录的文件路径
     * @param content 要写入的内容
     * @return 操作结果描述
     */
    fun writeFile(path: String, content: String): String {
        val root = rootDocFile ?: return "No project folder is open."

        return try {
            val trimmedPath = path.trim('/')
            val fileName = trimmedPath.substringAfterLast('/')
            val parentPath = trimmedPath.substringBeforeLast('/', "")

            // 确保父目录存在
            val parentDoc = if (parentPath.isEmpty()) {
                root
            } else {
                ensureDirectory(parentPath) ?: return "Failed to create parent directory: $parentPath"
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
            "File written: $path (${content.lines().size} lines, $opName)"
        } catch (e: Exception) {
            "Failed to write file: ${e.message}"
        }
    }

    /**
     * 在项目中搜索文件内容
     * 使用纯 DocumentFile API 递归遍历
     * @param query 搜索关键词
     * @param extension 可选的文件扩展名过滤
     * @return 搜索结果字符串
     */
    fun searchInFiles(query: String, extension: String = ""): String {
        val root = rootDocFile ?: return "No project folder is open."
        if (query.isBlank()) return "Search query is empty."

        return try {
            val results = mutableListOf<String>()
            val searchedFiles = mutableListOf<String>()
            val queryLower = query.lowercase()
            val extLower = extension.lowercase().trimStart('.')

            searchRecursive(root, "", queryLower, extLower, results, searchedFiles)

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
            Log.e("FileManager", "searchInFiles failed", e)
            "Search failed: ${e.message}"
        }
    }

    /**
     * 检查文件是否存在
     */
    fun exists(path: String): Boolean {
        return resolvePath(path.trim('/')) != null
    }

    /**
     * 判断路径是否为目录
     */
    fun isDirectory(path: String): Boolean {
        return resolvePath(path.trim('/'))?.isDirectory ?: false
    }

    // ---- 内部辅助方法 ----

    /**
     * 确保目录存在，不存在则逐级创建
     * @return 目标目录的 DocumentFile，失败返回 null
     */
    private fun ensureDirectory(relativePath: String): DocumentFile? {
        val root = rootDocFile ?: return null
        if (relativePath.isBlank()) return root

        return try {
            var current = root
            for (part in relativePath.trim('/').split('/')) {
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

    /**
     * 递归搜索文件内容
     * 纯 DocumentFile API 实现
     */
    private fun searchRecursive(
        dirDoc: DocumentFile,
        relativePath: String,
        queryLower: String,
        extLower: String,
        results: MutableList<String>,
        searchedFiles: MutableList<String>,
    ) {
        if (results.size >= 100) return

        val children = dirDoc.listFiles()

        for (doc in children) {
            val name = doc.name ?: continue
            if (name.startsWith(".")) continue

            val currentPath = if (relativePath.isEmpty()) name else "$relativePath/$name"

            if (doc.isDirectory) {
                // 递归搜索子目录，限制深度
                if (currentPath.count { it == '/' } < 10) {
                    searchRecursive(doc, currentPath, queryLower, extLower, results, searchedFiles)
                }
            } else {
                // 检查文件扩展名
                val fileExt = name.substringAfterLast('.', "").lowercase()
                if (extLower.isNotBlank()) {
                    if (fileExt != extLower) continue
                } else {
                    if (fileExt.isNotEmpty() && fileExt !in searchableExtensions) continue
                }

                // 跳过大文件
                if (doc.length() > 512 * 1024) continue

                searchedFiles.add(currentPath)

                // 读取并搜索文件内容
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
                } catch (_: Exception) {
                    // 跳过无法读取的文件
                }

                if (results.size >= 100) return
            }
        }
    }

    private fun formatSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> "${"%.1f".format(bytes.toDouble() / (1024 * 1024))} MB"
    }

}

/**
 * 文件节点数据类
 */
data class FileNode(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
    val uri: Uri
)
