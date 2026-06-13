package com.medeide.jh.screens.home.landscape.collab.ai.tools

import android.util.Log
import com.medeide.jh.data.storage.FileManager
import com.medeide.jh.data.utils.FileLogger
import com.medeide.jh.screens.home.landscape.workspace.editor.CodeEditTool
import com.medeide.jh.screens.home.ai.FileOperationEvents
import java.io.File

class EditTools(private val fileManager: FileManager?) {

    // ================================================================
    //  写入文件  ←→ Write(file_path, content)
    // ================================================================

    fun writeFile(file_path: String, content: String, overwrite: Boolean = false): String {
        Log.d("AIToolSet", "writeFile: file_path=$file_path contentLen=${content.length} overwrite=$overwrite")
        FileLogger.d("AIToolSet", "writeFile: file_path=$file_path contentLen=${content.length} overwrite=$overwrite")
        if (content.isEmpty()) {
            FileLogger.w("AIToolSet", "writeFile refused: content is empty")
            return err("Write refused — content is empty.")
        }
        val fm = fileManager ?: return err("No project folder is open.")
        val resolvedPath = resolvePathOrAbsolute(file_path, fileManager)
        if (!overwrite && fm.exists(resolvedPath)) {
            val msg = ("Write refused — $resolvedPath already exists.\n" +
                "Write is for creating NEW files only.\n" +
                "To modify an existing file, use replaceInFile or batchReplaceInFile with the exact code block.\n" +
                "Read the file first if you don't know its current content.\n" +
                "To overwrite, set overwrite=true.")
            FileLogger.w("AIToolSet", "writeFile protected: $resolvedPath exists")
            return err(msg)
        }
        return try {
            val result = fm.writeFile(resolvedPath, content)
            if (!result.startsWith("Failed") && !result.startsWith("No project")) {
                FileOperationEvents.notify(resolvedPath, "create")
                FileLogger.d("AIToolSet", "writeFile success: $result")
                ok(result)
            } else {
                FileLogger.w("AIToolSet", "writeFile failed: $result")
                err(result)
            }
        } catch (e: Exception) {
            val m = "Write failed: ${e.message ?: "unknown error"}"
            FileLogger.e("AIToolSet", "writeFile exception: $m", e)
            err(m)
        }
    }

    // ================================================================
    //  编辑文件（精确替换） ←→ SearchReplace(file_path, old_str, new_str)
    //  移除了 lineStart/lineEnd 范围参数
    // ================================================================

    fun replaceInFile(file_path: String, old_str: String, new_str: String): String {
        val fm = fileManager ?: return err("No project folder is open.")
        val resolvedPath = resolvePathOrAbsolute(file_path, fileManager)
        return try {
            Log.d("AIToolSet", "replaceInFile: file_path=$resolvedPath oldLen=${old_str.length} newLen=${new_str.length}")
            FileLogger.d("AIToolSet", "replaceInFile: file_path=$resolvedPath oldLen=${old_str.length} newLen=${new_str.length}")
            val fullOriginal = readFileRaw(fm, resolvedPath)
            if (fullOriginal == null) {
                Log.w("AIToolSet", "replaceInFile: file not found: $resolvedPath")
                FileLogger.w("AIToolSet", "replaceInFile: file not found: $resolvedPath")
                return err("Cannot replace: file not found. Use writeFile to create first.")
            }
            when (val result = CodeEditTool.replace(fullOriginal, old_str, new_str)) {
                is CodeEditTool.ReplaceResult.Success -> {
                    val finalContent = normalizeBlankLines(result.newText)
                    val writeResult = fm.writeFile(resolvedPath, finalContent)
                    if (writeResult.startsWith("Failed") || writeResult.startsWith("No project")) {
                        FileLogger.e("AIToolSet", "replaceInFile: replace succeeded but write failed: $writeResult")
                        return err("Replace succeeded but write failed: $writeResult")
                    }
                    FileOperationEvents.notify(resolvedPath, "modify")
                    FileLogger.d("AIToolSet", "replaceInFile success: $resolvedPath")
                    val oldLines = old_str.lines().size
                    val newLines = new_str.lines().size
                    ok("$resolvedPath — ${result.message}. Changed $oldLines lines → $newLines lines.")
                }
                is CodeEditTool.ReplaceResult.Error -> {
                    FileLogger.w("AIToolSet", "replaceInFile: CodeEditTool refused: ${result.message.take(200)}")
                    err(result.message)
                }
            }
        } catch (e: Exception) {
            Log.e("AIToolSet", "replaceInFile failed: ${e.message}", e)
            FileLogger.e("AIToolSet", "replaceInFile failed: ${e.message}", e)
            err("${e.message}")
        }
    }

    // ================================================================
    //  批量编辑
    // ================================================================

    fun batchReplaceInFile(path: String, edits: String): String {
        val fm = fileManager ?: return err("No project folder is open.")
        val resolvedPath = resolvePathOrAbsolute(path, fileManager)
        return try {
            Log.d("AIToolSet", "batchReplaceInFile: file_path=$resolvedPath editsLen=${edits.length}")
            FileLogger.d("AIToolSet", "batchReplaceInFile: file_path=$resolvedPath editsLen=${edits.length}")
            val original = readFileRaw(fm, resolvedPath)
            if (original == null) {
                Log.w("AIToolSet", "batchReplaceInFile: file not found: $resolvedPath")
                FileLogger.w("AIToolSet", "batchReplaceInFile: file not found: $resolvedPath")
                return err("Cannot edit: file not found. Use writeFile to create first.")
            }
            val jsonArr = org.json.JSONArray(edits)
            val editList = mutableListOf<CodeEditTool.Edit>()
            for (i in 0 until jsonArr.length()) {
                val obj = jsonArr.getJSONObject(i)
                val oldStr = obj.optString("old_string", "") ?: ""
                val newStr = obj.optString("new_string", "") ?: ""
                if (oldStr.isEmpty()) return err("Edit #${i + 1}: missing old_string")
                editList.add(CodeEditTool.Edit(oldStr, newStr))
            }
            if (editList.isEmpty()) return err("No edits provided.")
            when (val result = CodeEditTool.batchReplace(original, editList)) {
                is CodeEditTool.ReplaceResult.Success -> {
                    val newContent = normalizeBlankLines(result.newText)
                    val writeResult = fm.writeFile(resolvedPath, newContent)
                    if (writeResult.startsWith("Failed") || writeResult.startsWith("No project")) {
                        FileLogger.e("AIToolSet", "batchReplaceInFile: replace succeeded but write failed: $writeResult")
                        return err("Edit succeeded but write failed: $writeResult")
                    }
                    FileOperationEvents.notify(resolvedPath, "modify")
                    FileLogger.d("AIToolSet", "batchReplaceInFile success: $resolvedPath")
                    ok("$resolvedPath — ${editList.size} edits applied. ${result.message}")
                }
                is CodeEditTool.ReplaceResult.Error -> {
                    FileLogger.w("AIToolSet", "batchReplaceInFile: CodeEditTool refused: ${result.message.take(200)}")
                    err(result.message)
                }
            }
        } catch (e: org.json.JSONException) {
            val m = "Invalid edits JSON: ${e.message}"
            Log.e("AIToolSet", "batchReplaceInFile: $m")
            FileLogger.e("AIToolSet", "batchReplaceInFile: $m")
            err(m)
        } catch (e: Exception) {
            Log.e("AIToolSet", "batchReplaceInFile failed: ${e.message}", e)
            FileLogger.e("AIToolSet", "batchReplaceInFile failed: ${e.message}", e)
            err("${e.message}")
        }
    }

    // ================================================================
    //  删除文件  ←→ DeleteFile(file_paths)
    //  支持批量删除：逗号分隔路径或 JSON 数组字符串
    // ================================================================

    fun deleteFile(file_paths: String): String {
        val fm = fileManager ?: return err("No project folder is open.")
        // 解析路径列表：JSON 数组 > 逗号分隔
        val paths = try {
            val arr = org.json.JSONArray(file_paths)
            (0 until arr.length()).map { arr.getString(it).trim() }
        } catch (_: org.json.JSONException) {
            file_paths.split(",").map { it.trim() }.filter { it.isNotBlank() }
        }
        if (paths.isEmpty()) return err("No paths specified.")
        val results = paths.map { rawPath ->
            val resolvedPath = resolvePathOrAbsolute(rawPath, fileManager)
            Log.d("AIToolSet", "deleteFile: file_path=$resolvedPath")
            FileLogger.d("AIToolSet", "deleteFile: file_path=$resolvedPath")
            val r = fm.deleteFile(resolvedPath)
            if (r.startsWith("Failed") || r.startsWith("No project")) {
                FileLogger.w("AIToolSet", "deleteFile failed: $r")
                "$rawPath: [ERROR] $r"
            } else {
                FileOperationEvents.notify(resolvedPath, "delete")
                FileLogger.d("AIToolSet", "deleteFile success: $r")
                "$rawPath: [OK] Deleted."
            }
        }
        return results.joinToString("\n")
    }

    // ================================================================
    //  创建目录
    // ================================================================

    fun createDirectory(path: String): String {
        val fm = fileManager ?: return err("No project folder is open.")
        val resolvedPath = resolvePathOrAbsolute(path, fileManager)
        Log.d("AIToolSet", "createDirectory: path=$resolvedPath")
        FileLogger.d("AIToolSet", "createDirectory: path=$resolvedPath")
        val result = fm.createDirectory(resolvedPath)
        return if (result.startsWith("Failed") || result.startsWith("No project")) {
            FileLogger.w("AIToolSet", "createDirectory failed: $result")
            err(result)
        } else {
            FileOperationEvents.notify(resolvedPath, "create")
            FileLogger.d("AIToolSet", "createDirectory success: $result")
            ok(result)
        }
    }

    // ================================================================
    //  移动 / 复制 / 压缩
    // ================================================================

    fun moveFile(source: String, destination: String): String {
        val fm = fileManager ?: return err("No project folder is open.")
        return try {
            val base = fm.projectDirPath.ifEmpty { fm.storageRootPath }
            val srcFile = File(base, resolvePathOrAbsolute(source, fileManager))
            val dstFile = File(base, resolvePathOrAbsolute(destination, fileManager))
            if (!srcFile.exists()) return err("Source not found: $source")
            dstFile.parentFile?.mkdirs()
            if (dstFile.exists()) return err("Destination already exists: $destination")
            val success = srcFile.renameTo(dstFile)
            if (success) {
                FileOperationEvents.notify(source, "delete")
                FileOperationEvents.notify(destination, "create")
                ok("Moved: $source -> $destination")
            } else {
                err("Failed to move: $source -> $destination")
            }
        } catch (e: Exception) {
            err("Move failed: ${e.message}")
        }
    }

    fun copyFile(source: String, destination: String): String {
        val fm = fileManager ?: return err("No project folder is open.")
        return try {
            val base = fm.projectDirPath.ifEmpty { fm.storageRootPath }
            val srcFile = File(base, resolvePathOrAbsolute(source, fileManager))
            val dstFile = File(base, resolvePathOrAbsolute(destination, fileManager))
            if (!srcFile.exists()) return err("Source not found: $source")
            dstFile.parentFile?.mkdirs()
            if (dstFile.exists()) return err("Destination already exists: $destination")
            when {
                srcFile.isDirectory -> srcFile.copyRecursively(dstFile, overwrite = false)
                else -> srcFile.copyTo(dstFile, overwrite = false)
            }
            FileOperationEvents.notify(destination, "create")
            ok("Copied: $source -> $destination")
        } catch (e: Exception) {
            err("Copy failed: ${e.message}")
        }
    }

    // ================================================================
    //  内部辅助
    // ================================================================

    private fun readFileRaw(fm: FileManager, resolvedPath: String): String? {
        return fm.readFileRaw(resolvedPath)
    }

    // ================================================================
    //  工具定义
    // ================================================================

    companion object {
        fun buildOpenAIToolsJson(): org.json.JSONArray {
            val arr = org.json.JSONArray()
            arr.put(buildWriteFileTool())
            arr.put(buildReplaceInFileTool())
            arr.put(buildBatchReplaceInFileTool())
            arr.put(buildDeleteFileTool())
            arr.put(buildCreateDirectoryTool())
            arr.put(buildMoveFileTool())
            arr.put(buildCopyFileTool())

            return arr
        }

        fun toolNames(): List<String> = listOf(
            "writeFile", "replaceInFile", "batchReplaceInFile",
            "deleteFile", "createDirectory",
            "moveFile", "copyFile",
        )

        private fun buildWriteFileTool() = toolDef("writeFile",
            "创建新文件。overwrite=false(默认)时若文件已存在则报错并提示用replaceInFile修改；overwrite=true则覆盖。",
            listOf("file_path", "content"),
            "file_path" to p("string", "文件路径 — 绝对路径或相对项目根目录"),
            "content" to p("string", "要写入的完整文本内容"),
            "overwrite" to p("boolean", "是否覆盖已有文件，默认false"),
        )
        private fun buildReplaceInFileTool() = toolDef("replaceInFile",
            "精确替换代码块。old_str必须在全文唯一匹配，替换为新内容。",
            listOf("file_path", "old_str", "new_str"),
            "file_path" to p("string", "文件路径 — 绝对路径或相对项目根目录"),
            "old_str" to p("string", "要查找的精确代码块，在文件中必须唯一匹配"),
            "new_str" to p("string", "替换后的新代码块"),
        )
        private fun buildBatchReplaceInFileTool() = toolDef("batchReplaceInFile",
            "批量编辑，一次替换多处不重叠代码。edits基于原始文件同时匹配（非顺序应用），编辑不能重叠。单次编辑请用replaceInFile。",
            listOf("path", "edits"),
            "path" to p("string", "文件路径 — 绝对路径或相对项目根目录"),
            "edits" to p("string", "JSON数组：[{\"old_string\":\"原代码\",\"new_string\":\"新代码\"}]"),
        )
        private fun buildDeleteFileTool() = toolDef("deleteFile",
            "删除文件或目录。支持批量删除，可传单个路径、逗号分隔路径或多个JSON字符串数组。谨慎使用，删除后无法恢复。",
            listOf("file_paths"),
            "file_paths" to p("string", "文件/目录路径或路径列表 — 可传'path'或'path1,path2'或'[\"p1\",\"p2\"]'"),
        )
        private fun buildCreateDirectoryTool() = toolDef("createDirectory",
            "创建目录，自动创建所有父目录。支持多级路径如'src/utils/helpers'",
            listOf("path"),
            "path" to p("string", "目录路径 — 绝对路径或相对项目根目录"),
        )
        private fun buildMoveFileTool() = toolDef("moveFile",
            "移动/重命名文件或目录。",
            listOf("source", "destination"),
            "source" to p("string", "源路径"),
            "destination" to p("string", "目标路径"),
        )
        private fun buildCopyFileTool() = toolDef("copyFile",
            "复制文件或目录。",
            listOf("source", "destination"),
            "source" to p("string", "源路径"),
            "destination" to p("string", "目标路径"),
        )

    }
}
