package com.medeide.jh.screens.home.aitools.tools

import android.util.Log
import com.medeide.jh.data.storage.FileManager
import com.medeide.jh.data.utils.FileLogger

class EditorTools(
    private val fileManager: FileManager?,
    private val openFileCallback: ((String) -> Unit)? = null,
    private val getCurrentFileCallback: (() -> String?)? = null,
) {
    fun openFile(
        file_path: String,
    ): String {
        Log.d("EditorTools", "openFile: file_path=$file_path")
        FileLogger.d("EditorTools", "openFile: file_path=$file_path")

        val fm = fileManager ?: return err("No project folder is open.")
        val fullPath = resolvePathOrAbsolute(file_path, fm)

        if (!fm.exists(fullPath)) {
            return err("File not found: $fullPath")
        }

        return try {
            openFileCallback?.invoke(fullPath)
            ok("File opened: $fullPath")
        } catch (e: Exception) {
            Log.e("EditorTools", "openFile failed: ${e.message}", e)
            FileLogger.e("EditorTools", "openFile failed: ${e.message}", e)
            err("Failed to open file: ${e.message}")
        }
    }

    fun getCurrentFile(): String {
        Log.d("EditorTools", "getCurrentFile")
        FileLogger.d("EditorTools", "getCurrentFile")

        return try {
            val currentFile = getCurrentFileCallback?.invoke()
            if (currentFile.isNullOrBlank()) {
                ok("No file currently open.")
            } else {
                ok("Current file: $currentFile")
            }
        } catch (e: Exception) {
            Log.e("EditorTools", "getCurrentFile failed: ${e.message}", e)
            FileLogger.e("EditorTools", "getCurrentFile failed: ${e.message}", e)
            err("Failed to get current file: ${e.message}")
        }
    }

    fun viewFile(
        file_path: String,
        line: Int = 1,
        contextLines: Int = 10,
    ): String {
        Log.d("EditorTools", "viewFile: file_path=$file_path line=$line contextLines=$contextLines")
        FileLogger.d("EditorTools", "viewFile: file_path=$file_path")

        val fm = fileManager ?: return err("No project folder is open.")
        val fullPath = resolvePathOrAbsolute(file_path, fm)

        if (!fm.exists(fullPath)) {
            return err("File not found: $fullPath")
        }

        val content = fm.readFileRaw(fullPath) ?: return err("Failed to read file: $fullPath")
        val lines = content.lines()

        if (lines.isEmpty()) {
            return ok("File $file_path is empty.")
        }

        val lineNum = line.coerceIn(1, lines.size)
        val startLine = (lineNum - contextLines).coerceAtLeast(1)
        val endLine = (lineNum + contextLines).coerceAtMost(lines.size)

        return buildString {
            appendLine(ok("Viewing $file_path (lines $startLine-$endLine, total ${lines.size} lines):"))
            for (i in startLine - 1 until endLine) {
                val lineNumber = i + 1
                val marker = if (lineNumber == lineNum) "▶" else " "
                appendLine("%4d %s %s".format(lineNumber, marker, lines[i]))
            }
        }.trimEnd()
    }

    fun appendToFile(
        file_path: String,
        content: String,
    ): String {
        Log.d("EditorTools", "appendToFile: file_path=$file_path")
        FileLogger.d("EditorTools", "appendToFile: file_path=$file_path")

        val fm = fileManager ?: return err("No project folder is open.")
        val fullPath = resolvePathOrAbsolute(file_path, fm)

        return try {
            val existing = fm.readFileRaw(fullPath) ?: ""
            fm.writeFile(fullPath, existing + content)
            ok("Content appended to $file_path")
        } catch (e: Exception) {
            Log.e("EditorTools", "appendToFile failed: ${e.message}", e)
            FileLogger.e("EditorTools", "appendToFile failed: ${e.message}", e)
            err("Failed to append to file: ${e.message}")
        }
    }

    fun prependToFile(
        file_path: String,
        content: String,
    ): String {
        Log.d("EditorTools", "prependToFile: file_path=$file_path")
        FileLogger.d("EditorTools", "prependToFile: file_path=$file_path")

        val fm = fileManager ?: return err("No project folder is open.")
        val fullPath = resolvePathOrAbsolute(file_path, fm)

        return try {
            val existing = fm.readFileRaw(fullPath) ?: ""
            fm.writeFile(fullPath, content + existing)
            ok("Content prepended to $file_path")
        } catch (e: Exception) {
            Log.e("EditorTools", "prependToFile failed: ${e.message}", e)
            FileLogger.e("EditorTools", "prependToFile failed: ${e.message}", e)
            err("Failed to prepend to file: ${e.message}")
        }
    }

    fun truncateFile(
        file_path: String,
        linesToKeep: Int = 0,
    ): String {
        Log.d("EditorTools", "truncateFile: file_path=$file_path linesToKeep=$linesToKeep")
        FileLogger.d("EditorTools", "truncateFile: file_path=$file_path")

        val fm = fileManager ?: return err("No project folder is open.")
        val fullPath = resolvePathOrAbsolute(file_path, fm)

        return try {
            val content = fm.readFileRaw(fullPath) ?: return err("Failed to read file")
            val lines = content.lines()

            if (linesToKeep >= lines.size) {
                return ok("File has ${lines.size} lines, nothing to truncate.")
            }

            val truncated = lines.take(linesToKeep).joinToString("\n")
            fm.writeFile(fullPath, truncated)
            ok("File truncated, kept first $linesToKeep lines")
        } catch (e: Exception) {
            Log.e("EditorTools", "truncateFile failed: ${e.message}", e)
            FileLogger.e("EditorTools", "truncateFile failed: ${e.message}", e)
            err("Failed to truncate file: ${e.message}")
        }
    }

    fun renameFile(
        old_path: String,
        new_name: String,
    ): String {
        Log.d("EditorTools", "renameFile: old_path=$old_path new_name=$new_name")
        FileLogger.d("EditorTools", "renameFile: old_path=$old_path")

        val fm = fileManager ?: return err("No project folder is open.")
        val fullPath = resolvePathOrAbsolute(old_path, fm)

        return try {
            val parentDir = fullPath.substringBeforeLast('/')
            val newPath = "$parentDir/$new_name"
            val srcFile = java.io.File(fullPath)
            val dstFile = java.io.File(newPath)
            if (dstFile.exists()) return err("Destination already exists: $newPath")
            val success = srcFile.renameTo(dstFile)
            if (success) {
                ok("File renamed: $fullPath -> $newPath")
            } else {
                err("Failed to rename file")
            }
        } catch (e: Exception) {
            Log.e("EditorTools", "renameFile failed: ${e.message}", e)
            FileLogger.e("EditorTools", "renameFile failed: ${e.message}", e)
            err("Failed to rename file: ${e.message}")
        }
    }

    fun backupFile(
        file_path: String,
        backupSuffix: String = ".bak",
    ): String {
        Log.d("EditorTools", "backupFile: file_path=$file_path")
        FileLogger.d("EditorTools", "backupFile: file_path=$file_path")

        val fm = fileManager ?: return err("No project folder is open.")
        val fullPath = resolvePathOrAbsolute(file_path, fm)

        return try {
            val backupPath = fullPath + backupSuffix
            val srcFile = java.io.File(fullPath)
            val dstFile = java.io.File(backupPath)
            srcFile.copyTo(dstFile, overwrite = false)
            ok("File backed up to: $backupPath")
        } catch (e: Exception) {
            Log.e("EditorTools", "backupFile failed: ${e.message}", e)
            FileLogger.e("EditorTools", "backupFile failed: ${e.message}", e)
            err("Failed to backup file: ${e.message}")
        }
    }

    companion object {
        fun buildOpenAIToolsJson(): org.json.JSONArray {
            val arr = org.json.JSONArray()
            arr.put(buildOpenFileTool())
            arr.put(buildGetCurrentFileTool())
            arr.put(buildViewFileTool())
            arr.put(buildAppendToFileTool())
            arr.put(buildPrependToFileTool())
            arr.put(buildTruncateFileTool())
            arr.put(buildRenameFileTool())
            arr.put(buildBackupFileTool())
            return arr
        }

        fun toolNames(): List<String> = listOf(
            "openFile", "getCurrentFile", "viewFile",
            "appendToFile", "prependToFile", "truncateFile",
            "renameFile", "backupFile",
        )

        private fun buildOpenFileTool() = toolDef("openFile",
            "在编辑器中打开文件。这是最常用的工具，用于查看和编辑代码。",
            listOf("file_path"),
            "file_path" to p("string", "文件路径，相对项目根目录，如'app/src/main/MainActivity.kt'"),
        )

        private fun buildGetCurrentFileTool() = toolDef("getCurrentFile",
            "获取当前在编辑器中打开的文件名和路径。",
            props = emptyArray(),
        )

        private fun buildViewFileTool() = toolDef("viewFile",
            "查看文件内容，支持指定行号和上下文行数。",
            listOf("file_path"),
            "file_path" to p("string", "文件路径，相对项目根目录"),
            "line" to p("integer", "中心行号，默认1"),
            "contextLines" to p("integer", "上下上下文行数，默认10"),
        )

        private fun buildAppendToFileTool() = toolDef("appendToFile",
            "在文件末尾追加内容。适合添加新代码、配置等。",
            listOf("file_path", "content"),
            "file_path" to p("string", "文件路径，相对项目根目录"),
            "content" to p("string", "要追加的内容"),
        )

        private fun buildPrependToFileTool() = toolDef("prependToFile",
            "在文件开头插入内容。适合添加头部注释、import等。",
            listOf("file_path", "content"),
            "file_path" to p("string", "文件路径，相对项目根目录"),
            "content" to p("string", "要插入的内容"),
        )

        private fun buildTruncateFileTool() = toolDef("truncateFile",
            "截断文件，保留指定行数。谨慎使用，删除的内容无法恢复。",
            listOf("file_path"),
            "file_path" to p("string", "文件路径，相对项目根目录"),
            "linesToKeep" to p("integer", "保留的行数，默认0（清空文件）"),
        )

        private fun buildRenameFileTool() = toolDef("renameFile",
            "重命名文件。只改变文件名，不改变目录。",
            listOf("old_path", "new_name"),
            "old_path" to p("string", "原文件路径，相对项目根目录"),
            "new_name" to p("string", "新文件名，不含路径"),
        )

        private fun buildBackupFileTool() = toolDef("backupFile",
            "创建文件备份。默认后缀为.bak。",
            listOf("file_path"),
            "file_path" to p("string", "文件路径，相对项目根目录"),
            "backupSuffix" to p("string", "备份后缀，默认'.bak'"),
        )
    }
}
