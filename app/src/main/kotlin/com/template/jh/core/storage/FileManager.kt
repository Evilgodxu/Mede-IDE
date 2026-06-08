package com.template.jh.core.storage

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.util.Collections
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * 统一的文件管理器，使用 DocumentFile 公共 API 实现 SAF 操作
 * 兼容所有 Android 设备，避免使用 DocumentsContract 私有 API
 */
class FileManager(private val context: Context) {

    val contentResolver = context.contentResolver

    private var rootDocFile: DocumentFile? = null

    var projectUri: Uri? = null
        private set

    // 文件级互斥锁，确保同一文件的读写操作完全串行
    private val fileLocks = ConcurrentHashMap<String, Any>()

    // 路径缓存：key=normalized相对路径, value=DocumentFile
    private val pathCache = ConcurrentHashMap<String, DocumentFile>()

    private val searchableExtensions = setOf(
        "kt", "kts", "java", "xml", "json", "yml", "yaml", "properties",
        "txt", "md", "gradle", "toml", "cfg", "conf", "ini",
        "html", "css", "js", "ts", "sql", "sh", "bat", "py",
    )

    // 搜索时跳过的目录名（小写）
    private val skippedDirNames = setOf(
        "build", ".gradle", ".git", "node_modules", ".idea", "target",
        "out", "captures", ".git", ".svn", ".hg", ".m2", "gradle",
    )

    // 设置项目根目录
    fun setProjectUri(uri: Uri) {
        // 持久化权限
        val takeFlags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        context.contentResolver.takePersistableUriPermission(uri, takeFlags)
        projectUri = uri
        rootDocFile = DocumentFile.fromTreeUri(context, uri)
        invalidateCache()
    }

    // 清除项目根目录
    fun clearProjectUri() {
        projectUri = null
        rootDocFile = null
        invalidateCache()
    }

    /** 使路径缓存失效 */
    private fun invalidateCache() { pathCache.clear() }

    /**
     * 根据相对路径获取 DocumentFile
     * 优先使用 findFile（标准 API），失败时降级到遍历 listFiles 匹配名称
     */
    private fun resolvePath(relativePath: String): DocumentFile? {
        val root = rootDocFile ?: return null
        val path = relativePath.trim()
        if (path.isBlank()) return root

        val normalizedPath = path.trim('/')
        return pathCache.getOrPut(normalizedPath) {
            resolvePathUncached(root, normalizedPath)
        }
    }

    private fun resolvePathUncached(root: DocumentFile, normalizedPath: String): DocumentFile? {
        return try {
            var current = root
            for (segment in normalizedPath.split('/')) {
                if (segment.isEmpty()) continue
                var found = current.findFile(segment)
                if (found == null) {
                    found = current.listFiles().firstOrNull {
                        it.name.equals(segment, ignoreCase = true)
                    }
                }
                current = found ?: return null
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
        val cleanPath = subPath.trim('/').let { if (it == "null" || it == "undefined") "" else it }
        val targetDoc = resolvePath(cleanPath)
            ?: return if (cleanPath.isBlank()) "No project folder is open." else "Directory not found: $subPath"

        if (!targetDoc.isDirectory) return "Not a directory: $subPath"

        return try {
            val children = targetDoc.listFiles()
            if (children.isEmpty()) return "Empty directory."

            val sorted = children.sortedWith(
                compareByDescending<DocumentFile> { it.isDirectory }
                    .thenBy { it.name?.lowercase() ?: "" }
            )

            val displayPath = if (cleanPath.isBlank()) "" else cleanPath.trim('/')
            val header = if (displayPath.isEmpty()) "项目根目录/" else "$displayPath/"
            buildString {
                appendLine(header)
                sorted.forEach { doc ->
                    val name = doc.name ?: return@forEach
                    if (doc.isDirectory) {
                        appendLine("  $name/")
                    } else {
                        val sizeStr = if (doc.length() > 0) " (${formatSize(doc.length())})" else ""
                        appendLine("  $name$sizeStr")
                    }
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
        val cleanPath = subPath.trim('/').let { if (it == "null" || it == "undefined") "" else it }
        val targetDoc = resolvePath(cleanPath) ?: return emptyList()
        if (!targetDoc.isDirectory) return emptyList()

        return try {
            val children = targetDoc.listFiles()
            children.map { doc ->
                val name = doc.name ?: ""
                val relPath = if (cleanPath.isBlank()) name else "$cleanPath/$name"
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
     * 生成多层目录树文本，供 AI 上下文自动注入
     * @param maxDepth 最大递归深度
     * @param maxItems 最大显示条目数（总节点数）
     * @return 树形目录结构字符串，无项目返回空字符串
     */
    fun buildFileTreeString(maxDepth: Int = 3, maxItems: Int = 40): String {
        val root = rootDocFile ?: return ""
        return try {
            val result = mutableListOf<String>()
            var count = 0
            buildTreeRecursive(root, "", 0, maxDepth, maxItems) { line ->
                if (count < maxItems) {
                    result.add(line); count++; true
                } else false
            }
            if (result.isEmpty()) return "(empty project)"
            val rootName = root.name?.takeIf { it.isNotBlank() } ?: "project"
            buildString {
                appendLine("$rootName/")
                result.forEach { appendLine(it) }
            }.trimEnd()
        } catch (_: Exception) { "(error listing project structure)" }
    }

    // 递归构建目录树行，返回 true=继续 false=已满
    private fun buildTreeRecursive(
        dirDoc: DocumentFile,
        prefix: String,
        depth: Int,
        maxDepth: Int,
        maxItems: Int,
        addLine: (String) -> Boolean,
    ): Boolean {
        if (depth >= maxDepth) return true

        val children = dirDoc.listFiles()
            .filter { doc ->
                val name = doc.name ?: return@filter false
                !name.startsWith(".") && name.lowercase() != "build"
            }
            .sortedWith(compareByDescending<DocumentFile> { it.isDirectory }.thenBy { it.name?.lowercase() ?: "" })

        // 预先收集子目录子文件列表，用于判断每个节点是否是最后一个可显示节点
        val lastIdx = children.size - 1
        var idx = -1

        for (doc in children) {
            idx++
            val name = doc.name ?: continue
            val connector = if (idx == lastIdx) "└── " else "├── "
            if (!addLine("$prefix$connector$name${if (doc.isDirectory) "/" else sizeSuffix(doc)}")) return false
            if (doc.isDirectory) {
                val ext = if (idx == lastIdx) "    " else "│   "
                if (!buildTreeRecursive(doc, "$prefix$ext", depth + 1, maxDepth, maxItems, addLine)) return false
            }
        }
        return true
    }

    private fun sizeSuffix(doc: DocumentFile): String {
        // 返回格式化文件大小后缀，如 " (1.5 MB)"
        val len = doc.length()
        return if (len > 0) " (${formatSize(len)})" else ""
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
     * 查看文件指定范围内容（优化版，适合大文件）
     * @param path 相对于项目根目录的文件路径
     * @param offset 起始行号（1-based）
     * @param limit 最大读取行数
     * @return 带行号的文件内容
     */
    fun viewFile(path: String, offset: Int = 1, limit: Int = 100): String {
        val text = readFileRaw(path) ?: return "File not found: $path"
        val allLines = text.lines()
        val totalLines = allLines.size
        val startIdx = (offset - 1).coerceIn(0, totalLines)
        val endIdx = (startIdx + limit).coerceAtMost(totalLines)
        
        if (startIdx >= totalLines) {
            return "File has $totalLines lines. Cannot start at line $offset."
        }
        
        val lines = allLines.subList(startIdx, endIdx)
        val lineNumWidth = totalLines.toString().length
        
        return buildString {
            appendLine("// File: $path")
            appendLine("// Lines ${startIdx + 1}-$endIdx of $totalLines")
            appendLine("// ---")
            lines.forEachIndexed { i, line ->
                val lineNum = startIdx + i + 1
                appendLine("${lineNum.toString().padStart(lineNumWidth)}: $line")
            }
            if (endIdx < totalLines) {
                appendLine("// ---")
                appendLine("// Use viewFile(path=\"$path\", offset=${endIdx + 1}, limit=$limit) to see more")
            }
        }.trimEnd()
    }

    /**
     * 写入文件内容（带备份回滚 + 文件互斥锁）
     * @param path 相对于项目根目录的文件路径
     * @param content 要写入的内容
     * @return 操作结果描述
     */
    fun writeFile(path: String, content: String): String {
        val root = rootDocFile ?: return "No project folder is open."

        return try {
            // 路径安全检查
            val pathErr = validatePath(path)
            if (pathErr != null) return pathErr

            val trimmedPath = path.trim().trim('/')
            val fileName = trimmedPath.substringAfterLast('/')
            val parentPath = trimmedPath.substringBeforeLast('/', "")

            val parentDoc = if (parentPath.isEmpty()) root else {
                ensureDirectory(parentPath) ?: return "Failed to create parent directory: $parentPath"
            }

            // 查找或创建文件
            var existingFile = parentDoc.findFile(fileName)
            if (existingFile == null) {
                existingFile = parentDoc.listFiles().firstOrNull {
                    it.name.equals(fileName, ignoreCase = true)
                }
            }
            val targetDoc = existingFile ?: parentDoc.createFile("application/octet-stream", fileName)
                ?: return "Failed to create file: $path"

            val opName = if (existingFile != null) "overwrite" else "create"

            // 原子写入：备份 → 写入 → 校验 → 回滚
            val normalizedKey = trimmedPath.lowercase()
            val lock = fileLocks.getOrPut(normalizedKey) { Any() }
            synchronized(lock) {
                // 1. 备份原始内容
                val backup = if (existingFile != null) {
                    try {
                        context.contentResolver.openInputStream(targetDoc.uri)
                            ?.bufferedReader()?.use { it.readText() }
                    } catch (_: Exception) { null }
                } else null

                try {
                    // 2. 写入文件
                    context.contentResolver.openOutputStream(targetDoc.uri, "wt")?.use { out ->
                        out.write(content.toByteArray(Charsets.UTF_8))
                    } ?: throw RuntimeException("Failed to open output stream")

                    // 3. 读回校验
                    val written = try {
                        context.contentResolver.openInputStream(targetDoc.uri)
                            ?.bufferedReader()?.use { it.readText() }
                    } catch (_: Exception) { null }

                    if (written == null || written.length != content.length || !written.contains(content.take(50))) {
                        if (backup != null) {
                            context.contentResolver.openOutputStream(targetDoc.uri, "wt")?.use { out ->
                                out.write(backup.toByteArray(Charsets.UTF_8))
                            }
                        }
                        throw RuntimeException("Write verification failed, rolled back")
                    }
                } catch (e: Exception) {
                    if (backup != null) {
                        try {
                            context.contentResolver.openOutputStream(targetDoc.uri, "wt")?.use { out ->
                                out.write(backup.toByteArray(Charsets.UTF_8))
                            }
                        } catch (_: Exception) { }
                    }
                    throw e
                }
            }

            notifyFileSystemChange(path)
            "File written: $path (${content.lines().size} lines, $opName)"
        } catch (e: Exception) {
            "Failed to write file: ${e.message}"
        }
    }

    /**
     * 校验相对路径是否安全（防路径遍历攻击）
     * @return 错误消息，null 表示安全
     */
    fun validatePath(relativePath: String): String? {
        val trimmed = relativePath.trim()
        if (trimmed.isEmpty() || trimmed == "/") return null
        // 拆分后逐段检查，拒绝 .. 路径遍历
        val segments = trimmed.trim('/').split('/')
        for (seg in segments) {
            if (seg == "..") return "Path traversal detected: '$relativePath' (contains '..')"
        }
        return null
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

    // 检查文件是否存在
    fun exists(path: String): Boolean {
        return resolvePath(path.trim('/')) != null
    }

    // 判断路径是否为目录
    fun isDirectory(path: String): Boolean {
        return resolvePath(path.trim('/'))?.isDirectory ?: false
    }

    // 确保目录存在，不存在则逐级创建
    private fun ensureDirectory(relativePath: String): DocumentFile? {
        val root = rootDocFile ?: return null
        if (relativePath.isBlank()) return root

        return try {
            var current = root
            for (part in relativePath.trim().trim('/').split('/')) {
                if (part.isEmpty()) continue
                var child = current.findFile(part)
                if (child == null) {
                    child = current.listFiles().firstOrNull {
                        it.name.equals(part, ignoreCase = true)
                    }
                }
                current = child ?: (current.createDirectory(part) ?: return null)
            }
            current
        } catch (_: Exception) {
            null
        }
    }

    // 纯 DocumentFile API 实现
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
                val fileExt = name.substringAfterLast('.', "").lowercase()
                if (extLower.isNotBlank()) {
                    if (fileExt != extLower) continue
                } else {
                    if (fileExt.isNotEmpty() && fileExt !in searchableExtensions) continue
                }

                if (doc.length() > 512 * 1024) continue

                searchedFiles.add(currentPath)

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

    /**
     * Grep 搜索 - 正则表达式搜索文件内容
     * @param pattern 正则表达式模式
     * @param extension 文件扩展名过滤
     * @param glob Glob 模式过滤
     * @param ignoreCase 是否忽略大小写
     * @param contextLines 上下文行数
     * @return 搜索结果字符串
     */
    fun grep(
        pattern: String,
        extension: String = "",
        glob: String = "",
        ignoreCase: Boolean = true,
        contextLines: Int = 2,
    ): String {
        val root = rootDocFile ?: return "No project folder is open."
        if (pattern.isBlank()) return "Search pattern is empty."

        return try {
            val results = Collections.synchronizedList<GrepResult>(mutableListOf())
            val searchedFiles = Collections.synchronizedList<String>(mutableListOf())
            val regex = if (ignoreCase) Regex(pattern, RegexOption.IGNORE_CASE) else Regex(pattern)
            val extLower = extension.lowercase().trimStart('.')

            grepRecursive(root, "", regex, extLower, glob, results, searchedFiles, contextLines)

            if (results.isEmpty()) {
                buildString {
                    appendLine("No matches found for pattern \"$pattern\"${if (extension.isNotBlank()) " in *.$extension files" else ""}.")
                    appendLine("Searched ${searchedFiles.size} files.")
                }
            } else {
                buildString {
                    appendLine("Found ${results.size} match${if (results.size != 1) "es" else ""} for pattern \"$pattern\":")
                    appendLine("---")
                    results.take(30).forEach { result ->
                        appendLine("${result.filePath}:${result.lineNumber}:")
                        result.contextLines.forEach { (lineNum, line, isMatch) ->
                            val marker = if (isMatch) ">>>" else "   "
                            appendLine("$marker $lineNum: $line")
                        }
                        appendLine()
                    }
                    if (results.size > 30) appendLine("... and ${results.size - 30} more matches")
                }.trimEnd()
            }
        } catch (e: Exception) {
            Log.e("FileManager", "grep failed", e)
            "Search failed: ${e.message}"
        }
    }

    companion object {
        private val grepPool = Executors.newWorkStealingPool(
            Runtime.getRuntime().availableProcessors().coerceIn(2, 8)
        )
    }

    // grep 内部结果类型
    private data class GrepResult(
        val filePath: String,
        val lineNumber: Int,
        val contextLines: List<Triple<Int, String, Boolean>>
    )

    private fun grepRecursive(
        dirDoc: DocumentFile,
        relativePath: String,
        regex: Regex,
        extLower: String,
        glob: String,
        results: MutableList<GrepResult>,
        searchedFiles: MutableList<String>,
        contextLines: Int,
    ) {
        if (results.size >= 50) return

        val children = dirDoc.listFiles()

        // 收集子目录和文件，跳过噪声目录
        val subDirs = mutableListOf<DocumentFile>()
        val fileTasks = mutableListOf<DocumentFile>()

        for (doc in children) {
            val name = doc.name ?: continue
            if (name.startsWith(".") && name.lowercase() !in skippedDirNames) continue

            val currentPath = if (relativePath.isEmpty()) name else "$relativePath/$name"

            if (glob.isNotBlank() && !matchesGlob(name, glob)) continue

            if (doc.isDirectory) {
                if (name.lowercase() in skippedDirNames) continue
                if (currentPath.count { it == '/' } >= 10) continue
                subDirs.add(doc)
            } else {
                val fileExt = name.substringAfterLast('.', "").lowercase()
                if (extLower.isNotBlank()) {
                    if (fileExt != extLower) continue
                } else {
                    if (fileExt.isNotEmpty() && fileExt !in searchableExtensions) continue
                }
                if (doc.length() > 512 * 1024 || doc.length() == 0L) continue
                fileTasks.add(doc)
            }
        }

        // 并行搜索文件
        if (fileTasks.isNotEmpty()) {
            val futures = fileTasks.map { doc ->
                CompletableFuture.runAsync({
                    if (results.size >= 50) return@runAsync
                    val name = doc.name ?: return@runAsync
                    val currentPath = if (relativePath.isEmpty()) name else "$relativePath/$name"
                    searchedFiles.add(currentPath)
                    try {
                        val text = context.contentResolver.openInputStream(doc.uri)
                            ?.bufferedReader()?.use { it.readText() } ?: return@runAsync
                        val lines = text.lines()
                        for ((idx, line) in lines.withIndex()) {
                            if (results.size >= 50) break
                            if (regex.containsMatchIn(line)) {
                                val lineNum = idx + 1
                                val startIdx = maxOf(0, idx - contextLines)
                                val endIdx = minOf(lines.size - 1, idx + contextLines)
                                val ctx = (startIdx..endIdx).map { i ->
                                    Triple(i + 1, lines[i].take(120), i == idx)
                                }
                                results.add(GrepResult(currentPath, lineNum, ctx))
                            }
                        }
                    } catch (_: Exception) { }
                }, grepPool)
            }
            CompletableFuture.allOf(*futures.toTypedArray()).join()
        }

        // 顺序递归子目录
        for (subDir in subDirs) {
            if (results.size >= 50) return
            val name = subDir.name ?: continue
            val nextPath = if (relativePath.isEmpty()) name else "$relativePath/$name"
            grepRecursive(subDir, nextPath, regex, extLower, glob, results, searchedFiles, contextLines)
        }
    }

    private fun matchesGlob(filename: String, glob: String): Boolean {
        val pattern = glob
            .replace(".", "\\.")
            .replace("**", "<<DOUBLESTAR>>")
            .replace("*", "[^/]*")
            .replace("?", "[^/]")
            .replace("<<DOUBLESTAR>>", ".*")
        return Regex(pattern).matches(filename)
    }

    // 语义向量检索 — 基于 TF-IDF + 余弦相似度
    fun searchCodebase(query: String, targetDirectories: String = ""): String {
        val root = rootDocFile ?: return "No project folder is open."
        if (query.isBlank()) return "Search query is empty."

        return try {
            val targetDirs = if (targetDirectories.isBlank()) {
                emptyList()
            } else {
                targetDirectories.split(",").map { it.trim().trim('/') }
            }

            val files = mutableListOf<FileContent>()
            val dirsToSearch = if (targetDirs.isEmpty()) {
                listOf(root to "")
            } else {
                targetDirs.mapNotNull { dir ->
                    resolvePath(dir)?.let { it to dir }
                }
            }

            dirsToSearch.forEach { (dirDoc, basePath) ->
                collectFilesForSearch(dirDoc, basePath, files)
            }

            if (files.isEmpty()) return "No searchable files found."

            val index = com.template.jh.core.search.VectorIndex()
            files.forEach { file -> index.addDocument(file.path, file.content) }

            val results = index.search(query)

            if (results.isEmpty()) {
                "未找到相关代码: $query\n搜索范围: ${files.size} 个文件, ${index.size} 个代码块\n" +
                "提示: grep 工具可进行精确正则匹配, searchCodebase 更适合按功能描述探索代码"
            } else {
                buildString {
                    appendLine("语义搜索结果: \"$query\"")
                    appendLine("找到 ${results.size} 个相关文件:")
                    appendLine("---")
                    results.forEachIndexed { i, r ->
                        val pct = (r.score * 100).toInt().coerceIn(0, 99)
                        appendLine("[$pct%] ${r.filePath} (行 ${r.startLine}-${r.endLine})")
                        if (r.snippet.isNotBlank()) {
                            appendLine("片段:")
                            r.snippet.lines().take(5).forEach { appendLine("  $it") }
                        }
                        if (i < results.size - 1) appendLine()
                    }
                }.trimEnd()
            }
        } catch (e: Exception) {
            Log.e("FileManager", "searchCodebase failed", e)
            "Search failed: ${e.message}"
        }
    }

    private data class FileContent(val path: String, val content: String)

    private fun collectFilesForSearch(dirDoc: DocumentFile, relativePath: String, files: MutableList<FileContent>) {
        if (files.size >= 200) return

        dirDoc.listFiles().forEach { doc ->
            val name = doc.name ?: return@forEach
            if (name.startsWith(".")) return@forEach

            val currentPath = if (relativePath.isEmpty()) name else "$relativePath/$name"

            if (doc.isDirectory) {
                if (currentPath.count { it == '/' } < 8) {
                    collectFilesForSearch(doc, currentPath, files)
                }
            } else {
                val fileExt = name.substringAfterLast('.', "").lowercase()
                if (fileExt !in searchableExtensions) return@forEach
                if (doc.length() > 256 * 1024) return@forEach

                try {
                    val text = context.contentResolver.openInputStream(doc.uri)
                        ?.bufferedReader()
                        ?.use { it.readText() }
                        ?: return@forEach
                    files.add(FileContent(currentPath, text))
                } catch (_: Exception) { }
            }
        }
    }

    // 通知系统刷新文件，使文件管理器等外部应用可见
    private fun notifyFileSystemChange(path: String) {
        invalidateCache()
        try {
            val filePath = try {
                // 尝试将 SAF 路径转为真实文件路径
                resolvePath(path.trim('/'))?.uri?.let { uri ->
                    val docId = android.provider.DocumentsContract.getDocumentId(uri)
                    if (docId.startsWith("primary:")) {
                        File(
                            android.os.Environment.getExternalStorageDirectory(),
                            docId.removePrefix("primary:")
                        ).absolutePath
                    } else null
                }
            } catch (_: Exception) { null }

            if (filePath != null) {
                MediaScannerConnection.scanFile(context, arrayOf(filePath), null, null)
            }
        } catch (_: Exception) { }
    }

    private fun formatSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> "${"%.1f".format(bytes.toDouble() / (1024 * 1024))} MB"
    }

    // 删除文件或目录
    fun deleteFile(path: String): String {
        val root = rootDocFile ?: return "No project folder is open."

        return try {
            val target = resolvePath(path.trim('/'))
                ?: return "File or directory not found: $path"

            val deleted = target.delete()
            if (deleted) {
                notifyFileSystemChange(path)
                "Deleted: $path"
            } else {
                "Failed to delete: $path"
            }
        } catch (e: Exception) {
            "Failed to delete: ${e.message}"
        }
    }

    // 创建目录
    fun createDirectory(path: String): String {
        val root = rootDocFile ?: return "No project folder is open."

        return try {
            val trimmedPath = path.trim().trim('/')
            if (trimmedPath.isEmpty()) return "Cannot create root directory"

            if (resolvePath(trimmedPath) != null) {
                return "Directory already exists: $path"
            }

            val parentPath = trimmedPath.substringBeforeLast('/', "")
            val dirName = trimmedPath.substringAfterLast('/')

            val parentDoc = if (parentPath.isEmpty()) {
                root
            } else {
                ensureDirectory(parentPath) ?: return "Failed to create parent directory: $parentPath"
            }

            val created = parentDoc.createDirectory(dirName)
            if (created != null) {
                notifyFileSystemChange(path)
                "Directory created: $path"
            } else {
                "Failed to create directory: $path"
            }
        } catch (e: Exception) {
            "Failed to create directory: ${e.message}"
        }
    }

}

// 文件节点数据类
data class FileNode(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
    val uri: Uri
)
