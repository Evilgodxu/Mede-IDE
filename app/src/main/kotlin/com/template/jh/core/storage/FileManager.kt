package com.template.jh.core.storage

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import java.io.File

/**
 * 统一的文件管理器，使用 DocumentFile 公共 API 实现 SAF 操作
 * 兼容所有 Android 设备，避免使用 DocumentsContract 私有 API
 */
class FileManager(private val context: Context) {

    private var rootDocFile: DocumentFile? = null

    var projectUri: Uri? = null
        private set

    private val searchableExtensions = setOf(
        "kt", "kts", "java", "xml", "json", "yml", "yaml", "properties",
        "txt", "md", "gradle", "toml", "cfg", "conf", "ini",
        "html", "css", "js", "ts", "sql", "sh", "bat", "py",
    )

    // 设置项目根目录
    fun setProjectUri(uri: Uri) {
        // 持久化权限
        val takeFlags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        context.contentResolver.takePersistableUriPermission(uri, takeFlags)
        projectUri = uri
        rootDocFile = DocumentFile.fromTreeUri(context, uri)
    }

    // 清除项目根目录
    fun clearProjectUri() {
        projectUri = null
        rootDocFile = null
    }

    /**
     * 根据相对路径获取 DocumentFile
     * 优先使用 findFile（标准 API），失败时降级到遍历 listFiles 匹配名称
     */
    private fun resolvePath(relativePath: String): DocumentFile? {
        val root = rootDocFile ?: return null
        val path = relativePath.trim()
        if (path.isBlank()) return root

        return try {
            var current = root
            for (segment in path.trim('/').split('/')) {
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
        val targetDoc = resolvePath(subPath.trim('/'))
            ?: return if (subPath.isBlank()) "No project folder is open." else "Directory not found: $subPath"

        if (!targetDoc.isDirectory) return "Not a directory: $subPath"

        return try {
            val children = targetDoc.listFiles()
            if (children.isEmpty()) return "Empty directory."

            val sorted = children.sortedWith(
                compareByDescending<DocumentFile> { it.isDirectory }
                    .thenBy { it.name?.lowercase() ?: "" }
            )

            val displayPath = if (subPath.isBlank()) "project root" else subPath.trim('/')
            buildString {
                appendLine("$displayPath/")
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
        val visibleChildren = children.filter { c -> !(c.name?.startsWith(".") == true) }
        val lastIdx = visibleChildren.size - 1
        var idx = -1

        for (doc in visibleChildren) {
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
     * 读取文件内容（带行号）
     * @param path 相对于项目根目录的文件路径
     * @param offset 起始行号（1-based）
     * @param limit 最大读取行数
     * @return 带行号的文件内容
     */
    fun readFileWithLineNumbers(path: String, offset: Int = 1, limit: Int = 1000): String {
        val text = readFileRaw(path) ?: return "File not found: $path"
        val allLines = text.lines()
        val startIdx = (offset - 1).coerceIn(0, allLines.size)
        val endIdx = (startIdx + limit).coerceAtMost(allLines.size)
        val lines = allLines.subList(startIdx, endIdx)
        
        val lineNumWidth = allLines.size.toString().length
        return buildString {
            if (startIdx > 0 || endIdx < allLines.size) {
                appendLine("// Showing lines ${startIdx + 1}-$endIdx of ${allLines.size}")
            }
            lines.forEachIndexed { i, line ->
                val lineNum = startIdx + i + 1
                appendLine("${lineNum.toString().padStart(lineNumWidth)}: $line")
            }
        }.trimEnd()
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
     * 写入文件内容
     * @param path 相对于项目根目录的文件路径
     * @param content 要写入的内容
     * @return 操作结果描述
     */
    fun writeFile(path: String, content: String): String {
        val root = rootDocFile ?: return "No project folder is open."

        return try {
            val cleanPath = path.trim()
            val trimmedPath = cleanPath.trim('/')
            val fileName = trimmedPath.substringAfterLast('/')
            val parentPath = trimmedPath.substringBeforeLast('/', "")

            val parentDoc = if (parentPath.isEmpty()) {
                root
            } else {
                ensureDirectory(parentPath) ?: return "Failed to create parent directory: $parentPath"
            }

            // 查找或创建文件（兼容 findFile 失效的存储后端，兼容名称含空白）
            var existingFile = parentDoc.findFile(fileName)
            if (existingFile == null) {
                existingFile = parentDoc.listFiles().firstOrNull {
                    it.name.equals(fileName, ignoreCase = true)
                }
            }
            val targetDoc = existingFile ?: parentDoc.createFile("application/octet-stream", fileName)
            ?: return "Failed to create file: $path"

            context.contentResolver.openOutputStream(targetDoc.uri, "wt")?.use { out ->
                out.write(content.toByteArray(Charsets.UTF_8))
            } ?: return "Failed to write file: $path"

            val opName = if (existingFile != null) "overwrite" else "create"
            notifyFileSystemChange(path)
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
            val results = mutableListOf<GrepResult>()
            val searchedFiles = mutableListOf<String>()
            val regex = if (ignoreCase) {
                Regex(pattern, RegexOption.IGNORE_CASE)
            } else {
                Regex(pattern)
            }
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

        for (doc in children) {
            val name = doc.name ?: continue
            if (name.startsWith(".")) continue

            val currentPath = if (relativePath.isEmpty()) name else "$relativePath/$name"

            if (glob.isNotBlank() && !matchesGlob(name, glob)) continue

            if (doc.isDirectory) {
                if (currentPath.count { it == '/' } < 10) {
                    grepRecursive(doc, currentPath, regex, extLower, glob, results, searchedFiles, contextLines)
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

                    val lines = text.lines()
                    lines.forEachIndexed { idx, line ->
                        if (regex.containsMatchIn(line)) {
                            val lineNum = idx + 1
                            val startContext = maxOf(0, idx - contextLines)
                            val endContext = minOf(lines.size - 1, idx + contextLines)
                            
                            val context = (startContext..endContext).map { i ->
                                Triple(i + 1, lines[i].take(120), i == idx)
                            }
                            
                            results.add(GrepResult(currentPath, lineNum, context))
                        }
                    }
                } catch (_: Exception) { }

                if (results.size >= 50) return
            }
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

    private fun extractSearchTerms(query: String): List<String> {
        val stopWords = setOf(
            "the", "a", "an", "is", "are", "was", "were", "be", "been", "being",
            "have", "has", "had", "do", "does", "did", "will", "would", "could",
            "should", "may", "might", "must", "shall", "can", "need", "dare",
            "ought", "used", "to", "of", "in", "for", "on", "with", "at", "by",
            "from", "as", "into", "through", "during", "before", "after", "above",
            "below", "between", "under", "again", "further", "then", "once", "here",
            "there", "when", "where", "why", "how", "all", "each", "few", "more",
            "most", "other", "some", "such", "no", "nor", "not", "only", "own",
            "same", "so", "than", "too", "very", "just", "and", "but", "if", "or",
            "because", "until", "while", "what", "which", "who", "whom", "this",
            "that", "these", "those", "am", "it", "its", "i", "me", "my", "myself",
            "we", "our", "you", "your", "he", "him", "his", "she", "her", "they",
            "them", "their", "where", "how", "does", "work", "implemented", "find",
            "look", "search", "get", "set", "use", "using"
        )

        return query.lowercase()
            .replace(Regex("[^a-z0-9_\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length > 2 && it !in stopWords }
            .distinct()
    }

    private fun calculateRelevanceScore(content: String, query: String, terms: List<String>): Double {
        val contentLower = content.lowercase()
        var score = 0.0

        if (contentLower.contains(query.lowercase())) {
            score += 0.5
        }

        terms.forEach { term ->
            val count = contentLower.split(term).size - 1
            score += count * 0.1

            val patterns = listOf(
                "fun\\s+$term",
                "class\\s+$term",
                "interface\\s+$term",
                "object\\s+$term",
                "val\\s+$term",
                "var\\s+$term",
                "def\\s+$term",
                "function\\s+$term"
            )
            patterns.forEach { pattern ->
                if (Regex(pattern, RegexOption.IGNORE_CASE).containsMatchIn(content)) {
                    score += 0.3
                }
            }
        }

        return (score / (1 + contentLower.length / 1000.0)).coerceIn(0.0, 1.0)
    }

    private fun extractRelevantSnippet(content: String, terms: List<String>): String {
        val lines = content.lines()
        if (terms.isEmpty()) return lines.take(3).joinToString("\n")

        val scoredLines = lines.mapIndexed { idx, line ->
            val lineLower = line.lowercase()
            val score = terms.count { lineLower.contains(it) }
            Triple(idx, line, score)
        }.filter { it.third > 0 }
            .sortedByDescending { it.third }

        if (scoredLines.isEmpty()) return lines.take(3).joinToString("\n")

        val bestLine = scoredLines.first()
        val start = maxOf(0, bestLine.first - 2)
        val end = minOf(lines.size, bestLine.first + 3)
        return lines.subList(start, end).joinToString("\n")
    }

    // 通知系统刷新文件，使文件管理器等外部应用可见
    private fun notifyFileSystemChange(path: String) {
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
