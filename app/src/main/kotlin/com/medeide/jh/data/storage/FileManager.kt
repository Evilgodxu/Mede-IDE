package com.medeide.jh.data.storage

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.util.Collections
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors

/**
 * 统一的文件管理器
 *
 * 双模式：
 *  - 直接文件系统模式（MANAGE_EXTERNAL_STORAGE）：使用 java.io.File，无系统跳转
 *  - SAF 模式：使用 DocumentFile，兼容无全权限设备
 *
 * 默认优先使用直接文件系统模式，靠 fallback 到 SAF。
 */
class FileManager(private val context: Context) {

    val contentResolver = context.contentResolver

    // --- 模式状态 ---
    private var isDirectMode = false
    private var directRoot: File? = null          // 直接模式根目录（存储根）
    private var rootDocFile: DocumentFile? = null  // SAF 模式根目录

    var projectUri: Uri? = null
        private set

    /** 存储根路径（完整文件系统根） */
    var storageRootPath: String = ""
        private set

    /** 当前项目目录路径（双列文件管理器显示此目录内容，默认=存储根） */
    var projectDirPath: String = ""
        private set

    // --- 数据类 ---
    data class FileNode(
        val name: String,
        val path: String,
        val isDirectory: Boolean,
        val size: Long,
        val uri: Uri,
        val filePath: String = "",
    )

    private val searchableExtensions = setOf(
        "kt", "kts", "java", "xml", "json", "yml", "yaml", "properties",
        "txt", "md", "gradle", "toml", "cfg", "conf", "ini",
        "html", "css", "js", "ts", "sql", "sh", "bat", "py",
    )

    private val skippedDirNames = setOf(
        "build", ".gradle", ".git", "node_modules", ".idea", "target",
        "out", "captures", ".git", ".svn", ".hg", ".m2", "gradle",
    )

    // ================================================================
    //  权限与模式切换
    // ================================================================

    /** 检查是否持有 MANAGE_EXTERNAL_STORAGE 权限 */
    fun hasFullStorageAccess(): Boolean {
        return Environment.isExternalStorageManager()
    }

    /** 使用直接文件系统模式 - 设置根目录为外部存储根 */
    fun setDirectStorageRoot(root: File = Environment.getExternalStorageDirectory()) {
        isDirectMode = true
        directRoot = root
        storageRootPath = root.absolutePath
        projectDirPath = root.absolutePath  // 默认项目目录=存储根
        projectUri = null
        rootDocFile = null
    }

    /** 设置项目子目录（双列文件管理器将显示此目录内容） */
    fun setProjectDir(absolutePath: String): Boolean {
        val dir = File(absolutePath)
        if (!dir.isDirectory) return false
        projectDirPath = dir.absolutePath
        return true
    }

    /** 获取完整文件路径：将相对路径转为绝对路径 */
    fun getAbsolutePath(relativePath: String): String {
        val base = projectDirPath.ifEmpty { storageRootPath }
        if (relativePath.isBlank()) return base
        return File(base, relativePath.trim('/')).absolutePath
    }

    /** 将绝对路径转为项目相对路径 */
    fun toRelativePath(absolutePath: String): String {
        val base = projectDirPath.ifEmpty { storageRootPath }
        return absolutePath.removePrefix(base).trimStart('/')
    }

    /** 使用 SAF 模式 - 通过 URI 设置项目目录（传统方式） */
    fun setProjectUri(uri: Uri) {
        isDirectMode = false
        directRoot = null
        storageRootPath = uri.toString()
        projectDirPath = uri.toString()

        val takeFlags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        context.contentResolver.takePersistableUriPermission(uri, takeFlags)
        projectUri = uri
        rootDocFile = DocumentFile.fromTreeUri(context, uri)
    }

    /** 清除项目根目录 */
    fun clearProjectUri() {
        isDirectMode = false
        directRoot = null
        projectUri = null
        rootDocFile = null
        storageRootPath = ""
        projectDirPath = ""
    }

    /** 是否处于直接文件系统模式 */
    fun isDirectAccessMode(): Boolean = isDirectMode

    // ================================================================
    //  路径解析
    // ================================================================

    private fun resolveDirectFile(relativePath: String): File? {
        val base = projectDirPath.ifEmpty {
            directRoot?.absolutePath ?: return null
        }
        if (relativePath.isBlank()) return File(base)
        return File(base, relativePath.trim('/'))
    }

    private fun resolvePath(relativePath: String): DocumentFile? {
        if (isDirectMode) return null
        val root = rootDocFile ?: return null
        val path = relativePath.trim()
        if (path.isBlank()) return root
        return resolvePathUncached(root, path.trim('/'))
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

    // ================================================================
    //  文件列表
    // ================================================================

    fun listFiles(subPath: String = ""): String {
        return if (isDirectMode) listFilesDirect(subPath) else listFilesSaf(subPath)
    }

    fun listFilesAsNodes(subPath: String = ""): List<FileNode> {
        return if (isDirectMode) listFilesAsNodesDirect(subPath) else listFilesAsNodesSaf(subPath)
    }

    private fun listFilesDirect(subPath: String): String {
        val target = resolveDirectFile(subPath) ?: return "No storage root set."
        if (!target.isDirectory) return "Not a directory: $subPath"

        val children = target.listFiles()?.toList() ?: return "Empty directory."
        if (children.isEmpty()) return "Empty directory."

        val sorted = children.sortedWith(
            compareByDescending<File> { it.isDirectory }
                .thenBy { it.name.lowercase() }
        )

        val displayPath = if (subPath.isBlank()) "" else subPath.trim('/')
        val header = if (displayPath.isEmpty()) "存储根目录/" else "$displayPath/"
        return buildString {
            appendLine(header)
            sorted.forEach { file ->
                if (file.isDirectory) {
                    appendLine("  ${file.name}/")
                } else {
                    val sizeStr = if (file.length() > 0) " (${formatSize(file.length())})" else ""
                    appendLine("  ${file.name}$sizeStr")
                }
            }
        }.trimEnd()
    }

    private fun listFilesSaf(subPath: String): String {
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

    private fun listFilesAsNodesDirect(subPath: String): List<FileNode> {
        val target = resolveDirectFile(subPath) ?: return emptyList()
        if (!target.isDirectory) return emptyList()

        val children = target.listFiles()?.toList() ?: return emptyList()
        return children.map { file ->
            val relPath = if (subPath.isBlank()) file.name else "${subPath.trim('/')}/${file.name}"
            FileNode(
                name = file.name,
                path = relPath,
                isDirectory = file.isDirectory,
                size = if (file.isFile) file.length() else 0L,
                uri = Uri.fromFile(file),
                filePath = file.absolutePath,
            )
        }.sortedWith(
            compareByDescending<FileNode> { it.isDirectory }
                .thenBy { it.name.lowercase() }
        )
    }

    private fun listFilesAsNodesSaf(subPath: String): List<FileNode> {
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
                    uri = doc.uri,
                    filePath = safUriToFilePath(doc.uri),
                )
            }.sortedWith(
                compareByDescending<FileNode> { it.isDirectory }
                    .thenBy { it.name.lowercase() }
            )
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun safUriToFilePath(uri: Uri): String {
        return try {
            val docId = DocumentsContract.getDocumentId(uri)
            if (docId.startsWith("primary:")) {
                File(Environment.getExternalStorageDirectory(), docId.removePrefix("primary:")).absolutePath
            } else {
                val split = docId.split(":")
                if (split.size >= 2) "/storage/${split[0]}/${split[1]}" else ""
            }
        } catch (_: Exception) { "" }
    }

    // ================================================================
    //  构建目录树
    // ================================================================

    fun buildFileTreeString(maxDepth: Int = 3, maxItems: Int = 40): String {
        return if (isDirectMode) buildFileTreeDirect(maxDepth, maxItems) else buildFileTreeSaf(maxDepth, maxItems)
    }

    /** 获取项目目录的 File 对象（用于搜索/遍历） */
    private fun getProjectRootFile(): File? {
        val path = projectDirPath.ifEmpty { directRoot?.absolutePath ?: return null }
        return File(path)
    }

    private fun buildFileTreeDirect(maxDepth: Int, maxItems: Int): String {
        val root = getProjectRootFile() ?: return ""
        val result = mutableListOf<String>()
        var count = 0
        buildTreeRecursiveDirect(root, "", 0, maxDepth, maxItems) { line ->
            if (count < maxItems) { result.add(line); count++; true } else false
        }
        if (result.isEmpty()) return "(empty project)"
        return buildString {
            appendLine("${root.name}/")
            result.forEach { appendLine(it) }
        }.trimEnd()
    }

    private fun buildTreeRecursiveDirect(
        dir: File,
        prefix: String,
        depth: Int,
        maxDepth: Int,
        maxItems: Int,
        addLine: (String) -> Boolean,
    ): Boolean {
        if (depth >= maxDepth) return true

        val children = dir.listFiles()
            ?.filter { file ->
                !file.name.startsWith(".") && file.name.lowercase() != "build"
            }
            ?.sortedWith(compareByDescending<File> { it.isDirectory }.thenBy { it.name.lowercase() })
            ?: return true

        val lastIdx = children.size - 1
        children.forEachIndexed { idx, file ->
            val connector = if (idx == lastIdx) "└── " else "├── "
            val sizeSuf = if (file.isFile && file.length() > 0) " (${formatSize(file.length())})" else ""
            if (!addLine("$prefix$connector${file.name}${if (file.isDirectory) "/" else sizeSuf}")) return false
            if (file.isDirectory) {
                val ext = if (idx == lastIdx) "    " else "│   "
                if (!buildTreeRecursiveDirect(file, "$prefix$ext", depth + 1, maxDepth, maxItems, addLine)) return false
            }
        }
        return true
    }

    private fun buildFileTreeSaf(maxDepth: Int, maxItems: Int): String {
        val root = rootDocFile ?: return ""
        return try {
            val result = mutableListOf<String>()
            var count = 0
            buildTreeRecursiveSaf(root, "", 0, maxDepth, maxItems) { line ->
                if (count < maxItems) { result.add(line); count++; true } else false
            }
            if (result.isEmpty()) return "(empty project)"
            val rootName = root.name?.takeIf { it.isNotBlank() } ?: "project"
            buildString {
                appendLine("$rootName/")
                result.forEach { appendLine(it) }
            }.trimEnd()
        } catch (_: Exception) { "(error listing project structure)" }
    }

    private fun buildTreeRecursiveSaf(
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

        val lastIdx = children.size - 1
        children.forEachIndexed { idx, doc ->
            val name = doc.name ?: return@forEachIndexed
            val connector = if (idx == lastIdx) "└── " else "├── "
            val suf = if (doc.isDirectory) "" else sizeSuffixSaf(doc)
            if (!addLine("$prefix$connector$name${if (doc.isDirectory) "/" else suf}")) return false
            if (doc.isDirectory) {
                val ext = if (idx == lastIdx) "    " else "│   "
                if (!buildTreeRecursiveSaf(doc, "$prefix$ext", depth + 1, maxDepth, maxItems, addLine)) return false
            }
        }
        return true
    }

    private fun sizeSuffixSaf(doc: DocumentFile): String {
        val len = doc.length()
        return if (len > 0) " (${formatSize(len)})" else ""
    }

    // ================================================================
    //  删除 & 创建目录
    // ================================================================

    fun deleteFile(path: String): String {
        return if (isDirectMode) deleteFileDirect(path) else deleteFileSaf(path)
    }

    private fun deleteFileDirect(path: String): String {
        val file = resolveDirectFile(path.trim().trim('/')) ?: return "No storage root set."
        if (!file.exists()) return "File or directory not found: $path"
        return try {
            file.deleteRecursively()
            notifyFileSystemChange(file.absolutePath)
            "Deleted: $path"
        } catch (e: Exception) {
            "Failed to delete: ${e.message}"
        }
    }

    private fun deleteFileSaf(path: String): String {
        val root = rootDocFile ?: return "No project folder is open."
        return try {
            val target = resolvePath(path.trim('/')) ?: return "File or directory not found: $path"
            if (target.delete()) {
                notifyFileSystemChange(path)
                "Deleted: $path"
            } else {
                "Failed to delete: $path"
            }
        } catch (e: Exception) {
            "Failed to delete: ${e.message}"
        }
    }

    fun createDirectory(path: String): String {
        return if (isDirectMode) createDirectoryDirect(path) else createDirectorySaf(path)
    }

    private fun createDirectoryDirect(path: String): String {
        val file = resolveDirectFile(path.trim().trim('/')) ?: return "No storage root set."
        if (file.exists()) return "Directory already exists: $path"
        return try {
            file.mkdirs()
            notifyFileSystemChange(file.absolutePath)
            "Directory created: $path"
        } catch (e: Exception) {
            "Failed to create directory: ${e.message}"
        }
    }

    private fun createDirectorySaf(path: String): String {
        val root = rootDocFile ?: return "No project folder is open."
        return try {
            val trimmedPath = path.trim().trim('/')
            if (trimmedPath.isEmpty()) return "Cannot create root directory"
            if (resolvePath(trimmedPath) != null) return "Directory already exists: $path"

            val parentPath = trimmedPath.substringBeforeLast('/', "")
            val dirName = trimmedPath.substringAfterLast('/')

            val parentDoc = if (parentPath.isEmpty()) root
            else ensureDirectory(parentPath) ?: return "Failed to create parent directory: $parentPath"

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

    // ================================================================
    //  查询
    // ================================================================

    fun exists(path: String): Boolean {
        return if (isDirectMode) {
            resolveDirectFile(path.trim('/'))?.exists() ?: false
        } else {
            resolvePath(path.trim('/')) != null
        }
    }

    fun isDirectory(path: String): Boolean {
        return if (isDirectMode) {
            resolveDirectFile(path.trim('/'))?.isDirectory ?: false
        } else {
            resolvePath(path.trim('/'))?.isDirectory ?: false
        }
    }

    fun validatePath(relativePath: String): String? {
        val trimmed = relativePath.trim()
        if (trimmed.isEmpty() || trimmed == "/") return null
        val segments = trimmed.trim('/').split('/')
        for (seg in segments) {
            if (seg == "..") return "Path traversal detected: '$relativePath' (contains '..')"
        }
        return null
    }

    // ================================================================
    //  搜索
    // ================================================================

    fun searchInFiles(query: String, extension: String = ""): String {
        return if (isDirectMode) searchInFilesDirect(query, extension) else searchInFilesSaf(query, extension)
    }

    private fun searchInFilesDirect(query: String, extension: String = ""): String {
        val root = getProjectRootFile() ?: return "No storage root set."
        if (query.isBlank()) return "Search query is empty."

        val results = mutableListOf<String>()
        val searchedFiles = mutableListOf<String>()
        val queryLower = query.lowercase()
        val extLower = extension.lowercase().trimStart('.')

        searchRecursiveDirect(root, "", queryLower, extLower, results, searchedFiles)

        return formatSearchResults(query, extension, results, searchedFiles)
    }

    private fun searchInFilesSaf(query: String, extension: String = ""): String {
        val root = rootDocFile ?: return "No project folder is open."
        if (query.isBlank()) return "Search query is empty."

        val results = mutableListOf<String>()
        val searchedFiles = mutableListOf<String>()
        val queryLower = query.lowercase()
        val extLower = extension.lowercase().trimStart('.')

        searchRecursiveSaf(root, "", queryLower, extLower, results, searchedFiles)

        return formatSearchResults(query, extension, results, searchedFiles)
    }

    private fun searchRecursiveDirect(
        dir: File,
        relativePath: String,
        queryLower: String,
        extLower: String,
        results: MutableList<String>,
        searchedFiles: MutableList<String>,
    ) {
        if (results.size >= 100) return
        val children = dir.listFiles() ?: return
        for (file in children) {
            if (file.name.startsWith(".")) continue
            val currentPath = if (relativePath.isEmpty()) file.name else "$relativePath/${file.name}"

            if (file.isDirectory) {
                if (currentPath.count { it == '/' } < 10) {
                    searchRecursiveDirect(file, currentPath, queryLower, extLower, results, searchedFiles)
                }
            } else {
                val fileExt = file.extension.lowercase()
                if (extLower.isNotBlank() && fileExt != extLower) continue
                if (extLower.isBlank() && fileExt.isNotEmpty() && fileExt !in searchableExtensions) continue
                if (file.length() > 512 * 1024) continue

                searchedFiles.add(currentPath)
                try {
                    file.readLines(Charsets.UTF_8).forEachIndexed { idx, line ->
                        if (line.contains(queryLower, ignoreCase = true)) {
                            results.add("$currentPath:${idx + 1}:  ${line.trim().take(120)}")
                        }
                    }
                } catch (_: Exception) {}
                if (results.size >= 100) return
            }
        }
    }

    private fun searchRecursiveSaf(
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
                if (currentPath.count { it == '/' } < 10) {
                    searchRecursiveSaf(doc, currentPath, queryLower, extLower, results, searchedFiles)
                }
            } else {
                val fileExt = name.substringAfterLast('.', "").lowercase()
                if (extLower.isNotBlank() && fileExt != extLower) continue
                if (extLower.isBlank() && fileExt.isNotEmpty() && fileExt !in searchableExtensions) continue
                if (doc.length() > 512 * 1024) continue

                searchedFiles.add(currentPath)
                try {
                    val text = context.contentResolver.openInputStream(doc.uri)
                        ?.bufferedReader()?.use { it.readText() } ?: continue
                    text.lines().forEachIndexed { idx, line ->
                        if (line.contains(queryLower, ignoreCase = true)) {
                            results.add("$currentPath:${idx + 1}:  ${line.trim().take(120)}")
                        }
                    }
                } catch (_: Exception) {}
                if (results.size >= 100) return
            }
        }
    }

    private fun formatSearchResults(
        query: String, extension: String,
        results: List<String>, searchedFiles: List<String>,
    ): String {
        if (results.isEmpty()) {
            return buildString {
                appendLine("No matches found for \"$query\"${if (extension.isNotBlank()) " in *.$extension files" else ""}.")
                appendLine("Searched ${searchedFiles.size} files.")
                if (searchedFiles.isNotEmpty()) {
                    appendLine("Sample files searched:")
                    searchedFiles.take(10).forEach { appendLine("  - $it") }
                }
            }
        }
        return buildString {
            appendLine("Found ${results.size} match${if (results.size != 1) "es" else ""} for \"$query\":")
            appendLine("---")
            results.take(50).forEach { appendLine(it) }
            if (results.size > 50) appendLine("... and ${results.size - 50} more matches")
        }.trimEnd()
    }

    // ================================================================
    //  Grep 搜索
    // ================================================================

    companion object {
        private val grepPool = Executors.newWorkStealingPool(
            Runtime.getRuntime().availableProcessors().coerceIn(2, 8)
        )
    }

    /** 公有的搜索匹配行 */
    data class MatchLine(val text: String, val isMatch: Boolean)

    /** 公有的搜索匹配结果 */
    data class SearchMatch(
        val filePath: String,
        val lineNumber: Int,
        val matchText: String,
        val contextLines: List<MatchLine>,
    )

    /** 结构化 grep 搜索 */
    fun grepStructured(
        pattern: String,
        extension: String = "",
        glob: String = "",
        ignoreCase: Boolean = true,
        contextLines: Int = 2,
        maxResults: Int = 100,
    ): List<SearchMatch> {
        val root = if (isDirectMode) getProjectRootFile() else rootDocFile ?: return emptyList()
        if (pattern.isBlank()) return emptyList()
        val results = Collections.synchronizedList<GrepResult>(mutableListOf())
        val regex = if (ignoreCase) Regex(pattern, RegexOption.IGNORE_CASE) else Regex(pattern)
        val extLower = extension.lowercase().trimStart('.')

        if (isDirectMode && root is File) {
            grepRecursiveDirect(root, "", regex, extLower, glob, results, mutableListOf(), contextLines)
        } else if (!isDirectMode && root is DocumentFile) {
            grepRecursiveSaf(root, "", regex, extLower, glob, results, mutableListOf(), contextLines)
        }

        return results.take(maxResults).map { r ->
            SearchMatch(
                filePath = r.filePath,
                lineNumber = r.lineNumber,
                matchText = r.contextLines.find { it.third }?.second ?: "",
                contextLines = r.contextLines.map { (_, text, isMatch) ->
                    MatchLine(text = text, isMatch = isMatch)
                }
            )
        }
    }

    /** 在文件中搜索并替换，返回修改的文件数 */
    fun replaceInFiles(
        pattern: String,
        replacement: String,
        extension: String = "",
        ignoreCase: Boolean = true,
    ): Int {
        val root = if (isDirectMode) getProjectRootFile() else rootDocFile ?: return 0
        if (pattern.isBlank()) return 0
        val regex = if (ignoreCase) Regex(pattern, RegexOption.IGNORE_CASE) else Regex(pattern)
        val extLower = extension.lowercase().trimStart('.')
        val filesToModify = mutableListOf<String>()

        if (isDirectMode && root is File) {
            collectReplaceFilesDirect(root, "", regex, extLower, filesToModify)
        } else if (!isDirectMode && root is DocumentFile) {
            collectReplaceFilesSaf(root, "", regex, extLower, filesToModify)
        }

        var count = 0
        for (filePath in filesToModify) {
            val content = readFileRaw(filePath) ?: continue
            val newContent = content.replace(regex, replacement)
            if (newContent != content) {
                writeFile(filePath, newContent)
                count++
            }
        }
        return count
    }

    private fun collectReplaceFilesDirect(
        dir: File, relativePath: String, regex: Regex,
        extLower: String, files: MutableList<String>,
    ) {
        val children = dir.listFiles() ?: return
        for (file in children) {
            val name = file.name
            if (name.startsWith(".") && name.lowercase() !in skippedDirNames) continue
            val currentPath = if (relativePath.isEmpty()) name else "$relativePath/$name"
            if (file.isDirectory) {
                if (name.lowercase() in skippedDirNames) continue
                if (currentPath.count { it == '/' } >= 10) continue
                collectReplaceFilesDirect(file, currentPath, regex, extLower, files)
            } else {
                val fileExt = file.extension.lowercase()
                if (extLower.isNotBlank() && fileExt != extLower) continue
                if (extLower.isBlank() && fileExt.isNotEmpty() && fileExt !in searchableExtensions) continue
                if (file.length() > 512 * 1024) continue
                files.add(currentPath)
            }
        }
    }

    private fun collectReplaceFilesSaf(
        dirDoc: DocumentFile, relativePath: String, regex: Regex,
        extLower: String, files: MutableList<String>,
    ) {
        val children = dirDoc.listFiles()
        for (doc in children) {
            val name = doc.name ?: continue
            if (name.startsWith(".") && name.lowercase() !in skippedDirNames) continue
            val currentPath = if (relativePath.isEmpty()) name else "$relativePath/$name"
            if (doc.isDirectory) {
                if (name.lowercase() in skippedDirNames) continue
                if (currentPath.count { it == '/' } >= 10) continue
                collectReplaceFilesSaf(doc, currentPath, regex, extLower, files)
            } else {
                val fileExt = name.substringAfterLast('.', "").lowercase()
                if (extLower.isNotBlank() && fileExt != extLower) continue
                if (extLower.isBlank() && fileExt.isNotEmpty() && fileExt !in searchableExtensions) continue
                if (doc.length() > 512 * 1024) continue
                files.add(currentPath)
            }
        }
    }

    private data class GrepResult(
        val filePath: String,
        val lineNumber: Int,
        val contextLines: List<Triple<Int, String, Boolean>>
    )

    fun grep(
        pattern: String,
        extension: String = "",
        glob: String = "",
        ignoreCase: Boolean = true,
        contextLines: Int = 2,
    ): String {
        return if (isDirectMode) grepDirect(pattern, extension, glob, ignoreCase, contextLines)
        else grepSaf(pattern, extension, glob, ignoreCase, contextLines)
    }

    private fun grepDirect(
        pattern: String, extension: String, glob: String,
        ignoreCase: Boolean, contextLines: Int,
    ): String {
        val root = getProjectRootFile() ?: return "No storage root set."
        if (pattern.isBlank()) return "Search pattern is empty."
        val results = Collections.synchronizedList<GrepResult>(mutableListOf())
        val searchedFiles = Collections.synchronizedList<String>(mutableListOf())
        val regex = if (ignoreCase) Regex(pattern, RegexOption.IGNORE_CASE) else Regex(pattern)
        val extLower = extension.lowercase().trimStart('.')

        grepRecursiveDirect(root, "", regex, extLower, glob, results, searchedFiles, contextLines)

        return formatGrepResults(pattern, extension, results, searchedFiles)
    }

    private fun grepSaf(
        pattern: String, extension: String, glob: String,
        ignoreCase: Boolean, contextLines: Int,
    ): String {
        val root = rootDocFile ?: return "No project folder is open."
        if (pattern.isBlank()) return "Search pattern is empty."
        val results = Collections.synchronizedList<GrepResult>(mutableListOf())
        val searchedFiles = Collections.synchronizedList<String>(mutableListOf())
        val regex = if (ignoreCase) Regex(pattern, RegexOption.IGNORE_CASE) else Regex(pattern)
        val extLower = extension.lowercase().trimStart('.')

        grepRecursiveSaf(root, "", regex, extLower, glob, results, searchedFiles, contextLines)

        return formatGrepResults(pattern, extension, results, searchedFiles)
    }

    private fun grepRecursiveDirect(
        dir: File, relativePath: String, regex: Regex,
        extLower: String, glob: String,
        results: MutableList<GrepResult>, searchedFiles: MutableList<String>,
        contextLines: Int,
    ) {
        if (results.size >= 50) return
        val children = dir.listFiles() ?: return

        val subDirs = mutableListOf<File>()
        val fileTasks = mutableListOf<File>()

        for (file in children) {
            val name = file.name
            if (name.startsWith(".") && name.lowercase() !in skippedDirNames) continue
            val currentPath = if (relativePath.isEmpty()) name else "$relativePath/$name"
            if (glob.isNotBlank() && !matchesGlob(name, glob)) continue

            if (file.isDirectory) {
                if (name.lowercase() in skippedDirNames) continue
                if (currentPath.count { it == '/' } >= 10) continue
                subDirs.add(file)
            } else {
                val fileExt = file.extension.lowercase()
                if (extLower.isNotBlank() && fileExt != extLower) continue
                if (extLower.isBlank() && fileExt.isNotEmpty() && fileExt !in searchableExtensions) continue
                if (file.length() > 512 * 1024 || file.length() == 0L) continue
                fileTasks.add(file)
            }
        }

        for (file in fileTasks) {
            if (results.size >= 50) break
            val name = file.name
            val currentPath = if (relativePath.isEmpty()) name else "$relativePath/$name"
            searchedFiles.add(currentPath)
            try {
                val lines = file.readLines(Charsets.UTF_8)
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
            } catch (_: Exception) {}
        }

        for (sdir in subDirs) {
            if (results.size >= 50) continue
            val currentPath = if (relativePath.isEmpty()) sdir.name else "$relativePath/${sdir.name}"
            grepRecursiveDirect(sdir, currentPath, regex, extLower, glob, results, searchedFiles, contextLines)
        }
    }

    private fun grepRecursiveSaf(
        dirDoc: DocumentFile, relativePath: String, regex: Regex,
        extLower: String, glob: String,
        results: MutableList<GrepResult>, searchedFiles: MutableList<String>,
        contextLines: Int,
    ) {
        if (results.size >= 50) return
        val children = dirDoc.listFiles()

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
                if (extLower.isNotBlank() && fileExt != extLower) continue
                if (extLower.isBlank() && fileExt.isNotEmpty() && fileExt !in searchableExtensions) continue
                if (doc.length() > 512 * 1024 || doc.length() == 0L) continue
                fileTasks.add(doc)
            }
        }

        for (doc in fileTasks) {
            if (results.size >= 50) break
            val name = doc.name ?: continue
            val currentPath = if (relativePath.isEmpty()) name else "$relativePath/$name"
            searchedFiles.add(currentPath)
            try {
                val lines = context.contentResolver.openInputStream(doc.uri)
                    ?.bufferedReader()?.use { it.readText() }?.lines() ?: continue
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
            } catch (_: Exception) {}
        }

        for (sdir in subDirs) {
            if (results.size >= 50) continue
            val currentPath = if (relativePath.isEmpty()) sdir.name ?: "" else "$relativePath/${sdir.name ?: ""}"
            grepRecursiveSaf(sdir, currentPath, regex, extLower, glob, results, searchedFiles, contextLines)
        }
    }

    private fun formatGrepResults(
        pattern: String, extension: String,
        results: List<GrepResult>, searchedFiles: List<String>,
    ): String {
        if (results.isEmpty()) {
            return buildString {
                appendLine("No matches found for pattern \"$pattern\"${if (extension.isNotBlank()) " in *.$extension files" else ""}.")
                appendLine("Searched ${searchedFiles.size} files.")
            }
        }
        return buildString {
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

    // ================================================================
    //  代码搜索 (grep-based，替代已移除的 VectorIndex)
    // ================================================================

    fun searchCodebase(query: String, targetDirectories: String = ""): String {
        val rootContent = if (isDirectMode) {
            getProjectRootFile() ?: return "No storage root set."
        } else {
            rootDocFile ?: return "No project folder is open."
        }

        return try {
            val files = mutableListOf<FileContent>()
            if (isDirectMode && rootContent is File) {
                collectFilesForSearchDirect(rootContent, "", files)
            } else if (!isDirectMode && rootContent is DocumentFile) {
                collectFilesForSearchSaf(rootContent, "", files)
            }

            if (files.isEmpty()) return "No searchable files found."

            val queryLower = query.lowercase()
            val maxResults = 10
            val results = mutableListOf<Pair<String, String>>()

            for (file in files) {
                if (results.size >= maxResults) break
                val lines = file.content.lines()
                for (line in lines) {
                    if (results.size >= maxResults) break
                    if (line.lowercase().contains(queryLower)) {
                        results.add(file.path to line.trim())
                    }
                }
            }

            if (results.isEmpty()) {
                "未找到相关代码: $query\n搜索范围: ${files.size} 个文件\n建议：\n  1. 使用更精确的关键词\n  2. 使用 grep 搜索（正则表达式）"
            } else {
                buildString {
                    appendLine("相关代码（代码搜索）: $query")
                    appendLine("搜索范围: ${files.size} 个文件, ${results.size} 个匹配")
                    appendLine("---")
                    results.forEachIndexed { i, (path, line) ->
                        appendLine("${i + 1}. $path")
                        appendLine("   $line")
                        appendLine()
                    }
                }.trimEnd()
            }
        } catch (e: Exception) {
            "Search failed: ${e.message}"
        }
    }

    private data class FileContent(val path: String, val content: String)

    private fun collectFilesForSearchDirect(
        dir: File, relativePath: String, files: MutableList<FileContent>,
    ) {
        if (files.size >= 200) return
        val children = dir.listFiles() ?: return
        for (file in children) {
            if (file.name.startsWith(".")) continue
            val currentPath = if (relativePath.isEmpty()) file.name else "$relativePath/${file.name}"
            if (file.isDirectory) {
                if (currentPath.count { it == '/' } < 8) {
                    collectFilesForSearchDirect(file, currentPath, files)
                }
            } else {
                val fileExt = file.extension.lowercase()
                if (fileExt !in searchableExtensions) continue
                if (file.length() > 256 * 1024) continue
                try {
                    files.add(FileContent(currentPath, file.readText(Charsets.UTF_8)))
                } catch (_: Exception) {}
            }
        }
    }

    private fun collectFilesForSearchSaf(
        dirDoc: DocumentFile, relativePath: String, files: MutableList<FileContent>,
    ) {
        if (files.size >= 200) return
        dirDoc.listFiles().forEach { doc ->
            val name = doc.name ?: return@forEach
            if (name.startsWith(".")) return@forEach
            val currentPath = if (relativePath.isEmpty()) name else "$relativePath/$name"
            if (doc.isDirectory) {
                if (currentPath.count { it == '/' } < 8) {
                    collectFilesForSearchSaf(doc, currentPath, files)
                }
            } else {
                val fileExt = name.substringAfterLast('.', "").lowercase()
                if (fileExt !in searchableExtensions) return@forEach
                if (doc.length() > 256 * 1024) return@forEach
                try {
                    val text = context.contentResolver.openInputStream(doc.uri)
                        ?.bufferedReader()?.use { it.readText() } ?: return@forEach
                    files.add(FileContent(currentPath, text))
                } catch (_: Exception) {}
            }
        }
    }

    // ================================================================
    //  读写文件
    // ================================================================

    /** 读取文件内容，返回 null 表示失败 */
    fun readFileRaw(path: String): String? {
        return if (isDirectMode) {
            val file = resolveDirectFile(path.trim('/'))
            try {
                file?.readText(Charsets.UTF_8)
            } catch (_: Exception) { null }
        } else {
            val doc = resolvePath(path.trim('/'))
            try {
                doc?.let { context.contentResolver.openInputStream(it.uri)
                    ?.bufferedReader()?.use { reader -> reader.readText() } }
            } catch (_: Exception) { null }
        }
    }

    /** 写入文件内容，返回操作结果消息 */
    fun writeFile(path: String, content: String): String {
        return if (isDirectMode) {
            val file = resolveDirectFile(path.trim('/'))
                ?: return "No storage root set."
            try {
                file.parentFile?.mkdirs()
                file.writeText(content, Charsets.UTF_8)
                notifyFileSystemChange(file.absolutePath)
                "Written: $path"
            } catch (e: Exception) {
                "Failed to write file: ${e.message}"
            }
        } else {
            val root = rootDocFile ?: return "No project folder is open."
            try {
                val trimmedPath = path.trim('/')
                val existing = resolvePath(trimmedPath)
                if (existing != null) {
                    context.contentResolver.openOutputStream(existing.uri, "wt")?.use {
                        it.write(content.toByteArray(Charsets.UTF_8))
                    } ?: return "Failed to open output stream for: $path"
                } else {
                    // 文件不存在，需创建
                    val parentPath = trimmedPath.substringBeforeLast('/', "")
                    val fileName = trimmedPath.substringAfterLast('/')
                    val parentDoc = if (parentPath.isEmpty()) root
                        else ensureDirectory(parentPath) ?: return "Failed to create parent directory: $parentPath"
                    val newDoc = parentDoc.createFile("application/octet-stream", fileName)
                        ?: return "Failed to create file: $path"
                    context.contentResolver.openOutputStream(newDoc.uri, "wt")?.use {
                        it.write(content.toByteArray(Charsets.UTF_8))
                    } ?: return "Failed to open output stream for: $path"
                }
                notifyFileSystemChange(path)
                "Written: $path"
            } catch (e: Exception) {
                "Failed to write file: ${e.message}"
            }
        }
    }

    // ================================================================
    //  工具方法
    // ================================================================

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
        } catch (_: Exception) { null }
    }

    private fun notifyFileSystemChange(path: String) {
        try {
            MediaScannerConnection.scanFile(context, arrayOf(path), null, null)
        } catch (_: Exception) {}
    }

    private fun formatSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> "${"%.1f".format(bytes.toDouble() / (1024 * 1024))} MB"
    }

    private fun matchesGlob(name: String, glob: String): Boolean {
        return try {
            val regex = glob.replace(".", "\\.").replace("*", ".*")
            name.matches(Regex(regex))
        } catch (_: Exception) { false }
    }
}
