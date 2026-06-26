package com.medeide.jh.screens.home.landscape.sidebar.searchreplacepanel

import java.io.File
import java.util.Collections

private val searchableExtensions = setOf(
    "kt", "kts", "java", "xml", "json", "yml", "yaml", "properties",
    "txt", "md", "gradle", "toml", "cfg", "conf", "ini",
    "html", "css", "js", "ts", "sql", "sh", "bat", "py",
    "c", "cpp", "h", "hpp", "cs", "go", "rs", "swift", "rb", "php", "log",
)

private val skippedDirNames = setOf(
    "build", ".gradle", ".git", "node_modules", ".idea", "target",
    "out", "captures", ".svn", ".hg", ".m2",
)

private data class GrepResult(
    val filePath: String,
    val lineNumber: Int,
    val contextLines: List<Triple<Int, String, Boolean>>,
)

private val grepPool = java.util.concurrent.Executors.newWorkStealingPool(
    Runtime.getRuntime().availableProcessors().coerceIn(2, 8)
)

fun grepStructured(
    rootPath: String,
    pattern: String,
    ignoreCase: Boolean = true,
    contextLines: Int = 1,
    maxResults: Int = 200,
): List<SearchResultItem> {
    val root = File(rootPath)
    if (!root.isDirectory || pattern.isBlank()) return emptyList()
    val results = Collections.synchronizedList<GrepResult>(mutableListOf())
    val regex = if (ignoreCase) Regex(pattern, RegexOption.IGNORE_CASE) else Regex(pattern)

    grepRecursive(root, root, regex, results, contextLines)

    return results.take(maxResults).map { r ->
        SearchResultItem(
            filePath = r.filePath,
            lineNumber = r.lineNumber,
            matchText = r.contextLines.find { it.third }?.second ?: "",
            contextLines = r.contextLines.map { (_, text, isMatch) -> MatchLine(text, isMatch) },
        )
    }
}

private fun grepRecursive(
    root: File,
    dir: File,
    regex: Regex,
    results: MutableList<GrepResult>,
    contextLines: Int,
) {
    if (results.size >= 200) return
    val children = dir.listFiles() ?: return
    val fileTasks = mutableListOf<File>()
    val dirTasks = mutableListOf<File>()

    for (file in children) {
        val name = file.name
        if (name.startsWith(".")) continue
        if (file.isDirectory) {
            if (name.lowercase() in skippedDirNames) continue
            dirTasks.add(file)
        } else {
            val ext = file.extension.lowercase()
            if (ext.isNotEmpty() && ext !in searchableExtensions) continue
            if (file.length() > 512 * 1024 || file.length() == 0L) continue
            fileTasks.add(file)
        }
    }

    for (file in fileTasks) {
        if (results.size >= 200) break
        val relative = file.absolutePath.removePrefix(root.absolutePath).trimStart('/', '\\')
        try {
            val lines = file.readLines(Charsets.UTF_8)
            for ((idx, line) in lines.withIndex()) {
                if (results.size >= 200) break
                if (regex.containsMatchIn(line)) {
                    val lineNum = idx + 1
                    val startIdx = maxOf(0, idx - contextLines)
                    val endIdx = minOf(lines.size - 1, idx + contextLines)
                    val ctx = (startIdx..endIdx).map { i ->
                        Triple(i + 1, lines[i].take(120), i == idx)
                    }
                    results.add(GrepResult(relative, lineNum, ctx))
                }
            }
        } catch (_: Exception) {
        }
    }

    dirTasks.forEach { grepRecursive(root, it, regex, results, contextLines) }
}

fun replaceInFiles(
    rootPath: String,
    pattern: String,
    replacement: String,
    ignoreCase: Boolean = true,
): List<String> {
    val root = File(rootPath)
    if (!root.isDirectory || pattern.isBlank()) return emptyList()
    val regex = if (ignoreCase) Regex(pattern, RegexOption.IGNORE_CASE) else Regex(pattern)
    val filesToModify = mutableListOf<File>()
    collectReplaceFiles(root, root, regex, filesToModify)

    val changedPaths = mutableListOf<String>()
    for (file in filesToModify) {
        try {
            val content = file.readText(Charsets.UTF_8)
            val newContent = content.replace(regex, replacement)
            if (newContent != content) {
                file.writeText(newContent, Charsets.UTF_8)
                changedPaths.add(file.absolutePath)
            }
        } catch (_: Exception) {
        }
    }
    return changedPaths
}

private fun collectReplaceFiles(
    root: File,
    dir: File,
    regex: Regex,
    files: MutableList<File>,
) {
    val children = dir.listFiles() ?: return
    for (file in children) {
        val name = file.name
        if (name.startsWith(".")) continue
        if (file.isDirectory) {
            if (name.lowercase() in skippedDirNames) continue
            collectReplaceFiles(root, file, regex, files)
        } else {
            val ext = file.extension.lowercase()
            if (ext.isNotEmpty() && ext !in searchableExtensions) continue
            if (file.length() > 512 * 1024 || file.length() == 0L) continue
            files.add(file)
        }
    }
}

fun relativePath(rootPath: String, absolutePath: String): String {
    val root = File(rootPath).absolutePath
    return absolutePath.removePrefix(root).trimStart('/', '\\')
}

fun resolveAbsolutePath(rootPath: String, filePath: String): String {
    if (filePath.startsWith('/')) return filePath
    return File(File(rootPath).absolutePath, filePath).absolutePath
}
