package com.medeide.jh.screens.home.cloudchat.aitools

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class EditTools(
    private val projectRootCallback: () -> String,
    private val isReadOnlyCallback: () -> Boolean = { false },
) {
    private val root: String get() = projectRootCallback()
    private val isReadOnly: Boolean get() = isReadOnlyCallback()

    private fun checkWritable(): String? {
        if (isReadOnly) return err("当前为只读模式，未设置工作目录。请在文件浏览器中长按目录 → 进入工作模式后即可读写文件。")
        return null
    }

    fun writeFile(file_path: String, content: String, overwrite: Boolean = false): String {
        checkWritable()?.let { return it }
        val f = File(resolvePath(file_path))
        if (f.exists() && !overwrite) return err("文件已存在: $file_path，设置 overwrite=true 可覆盖")
        if (content.isBlank()) return err("内容为空")
        try {
            f.parentFile?.mkdirs()
            f.writeText(content)
            return ok("已写入 ${f.name} (${content.length} chars)")
        } catch (e: Exception) {
            return err("写入失败: ${e.message}")
        }
    }

    fun replaceInFile(file_path: String, old_str: String, new_str: String): String {
        checkWritable()?.let { return it }
        val f = File(resolvePath(file_path))
        if (!f.exists()) return err("文件不存在: $file_path")
        val text = f.readText()
        val idx = text.indexOf(old_str)
        if (idx < 0) return err("在文件中未找到匹配的 old_str，请确保内容完全一致（含缩进）")
        val result = text.replaceFirst(old_str, new_str)
        f.writeText(result)
        val oldLines = old_str.lines().size
        val newLines = new_str.lines().size
        return ok("已替换 $file_path — $oldLines 行 → $newLines 行")
    }

    fun batchReplaceInFile(path: String, edits: String): String {
        checkWritable()?.let { return it }
        val f = File(resolvePath(path))
        if (!f.exists()) return err("文件不存在: $path")
        var text = f.readText()
        val jsonArr = try { JSONArray(edits) } catch (e: Exception) { return err("edits JSON 格式错误: ${e.message}") }
        var applied = 0
        for (i in 0 until jsonArr.length()) {
            val obj = jsonArr.getJSONObject(i)
            val oldStr = obj.optString("old_string", "")
            val newStr = obj.optString("new_string", "")
            if (oldStr.isEmpty()) continue
            if (!text.contains(oldStr)) return err("编辑 #${i + 1}: 未找到匹配内容")
            text = text.replaceFirst(oldStr, newStr)
            applied++
        }
        f.writeText(text)
        return ok("已应用 $applied 处编辑到 $path")
    }

    fun deleteFile(file_paths: String): String {
        checkWritable()?.let { return it }
        val paths = try {
            val arr = JSONArray(file_paths)
            (0 until arr.length()).map { arr.getString(it).trim() }
        } catch (_: Exception) {
            file_paths.split(",").map { it.trim() }.filter { it.isNotBlank() }
        }
        val results = paths.map { raw ->
            val f = File(resolvePath(raw))
            if (!f.exists()) "$raw: [ERROR] 不存在"
            else try {
                f.deleteRecursively()
                "$raw: [OK] 已删除"
            } catch (e: Exception) { "$raw: [ERROR] ${e.message}" }
        }
        return results.joinToString("\n")
    }

    fun createDirectory(path: String): String {
        checkWritable()?.let { return it }
        val f = File(resolvePath(path))
        return try {
            f.mkdirs()
            if (f.exists()) ok("已创建目录: $path") else err("创建目录失败: $path")
        } catch (e: Exception) { err("创建目录失败: ${e.message}") }
    }

    fun moveFile(source: String, destination: String): String {
        checkWritable()?.let { return it }
        val src = File(resolvePath(source))
        val dst = File(resolvePath(destination))
        if (!src.exists()) return err("源文件不存在: $source")
        try {
            dst.parentFile?.mkdirs()
            src.renameTo(dst)
            return ok("已移动 $source → $destination")
        } catch (e: Exception) { return err("移动失败: ${e.message}") }
    }

    fun copyFile(source: String, destination: String): String {
        checkWritable()?.let { return it }
        val src = File(resolvePath(source))
        val dst = File(resolvePath(destination))
        if (!src.exists()) return err("源文件不存在: $source")
        return try {
            dst.parentFile?.mkdirs()
            src.copyTo(dst, overwrite = true)
            ok("已复制 $source → $destination")
        } catch (e: Exception) { err("复制失败: ${e.message}") }
    }

    private fun resolvePath(path: String): String {
        return if (path.startsWith("/")) path else "${root}/${path.trimStart('/')}".trimEnd('/')
    }

    companion object {
        fun buildOpenAIToolsJson(): JSONArray {
            val arr = JSONArray()
            arr.put(buildWriteFileTool())
            arr.put(buildReplaceInFileTool())
            arr.put(batchReplaceInFileTool())
            arr.put(buildDeleteFileTool())
            arr.put(buildCreateDirectoryTool())
            arr.put(buildMoveFileTool())
            arr.put(buildCopyFileTool())
            return arr
        }

        private fun buildWriteFileTool() = toolDef("writeFile",
            "创建新文件。overwrite=false(默认)时若已存在则报错；overwrite=true则覆盖。",
            required = listOf("file_path", "content"),
            "file_path" to p("string", "文件路径，绝对或相对项目根目录"),
            "content" to p("string", "要写入的完整文本内容"),
            "overwrite" to p("boolean", "是否覆盖已有文件，默认false"),
        )
        private fun buildReplaceInFileTool() = toolDef("replaceInFile",
            "精确替换文件中的代码块。old_str必须在全文唯一匹配，替换为新内容。",
            required = listOf("file_path", "old_str", "new_str"),
            "file_path" to p("string", "文件路径，绝对或相对项目根目录"),
            "old_str" to p("string", "要查找的精确代码块，必须唯一匹配"),
            "new_str" to p("string", "替换后的新代码块"),
        )
        private fun batchReplaceInFileTool() = toolDef("batchReplaceInFile",
            "批量编辑文件，一次替换多处不重叠代码。edits 为JSON数组。",
            required = listOf("path", "edits"),
            "path" to p("string", "文件路径"),
            "edits" to p("string", "JSON数组格式：[{\"old_string\":\"...\",\"new_string\":\"...\"}]"),
        )
        private fun buildDeleteFileTool() = toolDef("deleteFile",
            "删除文件或目录。支持批量删除，逗号分隔或JSON数组。谨慎使用不可恢复。",
            required = listOf("file_paths"),
            "file_paths" to p("string", "文件路径或路径列表，可传'path'或'path1,path2'或'[\"p1\",\"p2\"]'"),
        )
        private fun buildCreateDirectoryTool() = toolDef("createDirectory",
            "创建目录，自动创建所有父目录。",
            required = listOf("path"),
            "path" to p("string", "目录路径，绝对或相对项目根目录"),
        )
        private fun buildMoveFileTool() = toolDef("moveFile",
            "移动/重命名文件或目录。",
            required = listOf("source", "destination"),
            "source" to p("string", "源路径"),
            "destination" to p("string", "目标路径"),
        )
        private fun buildCopyFileTool() = toolDef("copyFile",
            "复制文件或目录到目标路径。",
            required = listOf("source", "destination"),
            "source" to p("string", "源路径"),
            "destination" to p("string", "目标路径"),
        )
    }
}
