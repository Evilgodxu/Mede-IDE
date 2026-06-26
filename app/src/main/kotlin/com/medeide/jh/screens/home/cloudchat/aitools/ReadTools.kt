package com.medeide.jh.screens.home.cloudchat.aitools

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class ReadTools(private val projectRootCallback: () -> String) {

    private val root: String get() = projectRootCallback()

    fun listFiles(path: String = "", ignore: String = ""): String {
        val dir = resolvePath(path)
        val file = File(dir)
        if (!file.exists() || !file.isDirectory) return err("目录不存在: $dir")
        val children = file.listFiles() ?: return ok("空目录")
        val ignorePatterns = if (ignore.isNotBlank()) ignore.split(",").map { Regex(it.trim()) } else emptyList()
        val sb = StringBuilder()
        children.filter { n -> ignorePatterns.none { it.matches(n.name) } }
            .sortedBy { if (it.isDirectory) 0 else 1 }
            .forEach { n ->
                val prefix = if (n.isDirectory) "[DIR]" else "[FILE]"
                val size = if (n.isFile) " (${formatSize(n.length())})" else ""
                sb.appendLine("  $prefix ${n.name}/$size")
            }
        return ok("${file.name}/ (${children.size} items)\n$sb".trimEnd())
    }

    fun readFile(file_path: String, offset: Int = 0, limit: Int = 1000): String {
        val f = File(resolvePath(file_path))
        if (!f.exists() || !f.isFile) return err("文件不存在: $file_path")
        val lines = f.readLines()
        val total = lines.size
        if (total == 0) return ok("空文件: $file_path")
        val start = offset.coerceIn(0, total)
        val end = (start + limit).coerceAtMost(total)
        val sb = StringBuilder()
        if (start > 0 || end < total) sb.appendLine("// lines ${start + 1}-$end of $total")
        lines.subList(start, end).forEach { sb.appendLine(it) }
        if (end < total) sb.appendLine("// ... 还有 ${total - end} 行, 用 readFile offset=$end 继续读取")
        return ok(sb.toString().trimEnd())
    }

    fun grep(
        pattern: String, path: String = "", glob: String = "",
        `-i`: Boolean = true, head_limit: Int = 100,
        `-C`: Int = 2,
    ): String {
        val dir = if (path.isNotBlank()) resolvePath(path) else root
        val dirFile = File(dir)
        if (!dirFile.exists()) return err("目录不存在: $dir")
        val regex = try {
            Regex(pattern, if (`-i`) setOf(RegexOption.IGNORE_CASE) else emptySet())
        } catch (e: Exception) { return err("正则无效: ${e.message}") }
        val globRegex = if (glob.isNotBlank()) {
            try { Regex(glob.replace(".", "\\.").replace("*", ".*").replace("?", ".")) } catch (_: Exception) { null }
        } else null
        val matches = mutableListOf<String>()
        dirFile.walkTopDown().forEach { f ->
            if (matches.size >= head_limit) return@forEach
            if (globRegex != null && !globRegex.matches(f.name)) return@forEach
            if (f.isFile) {
                try {
                    f.readLines().forEachIndexed { idx, line ->
                        if (matches.size >= head_limit) return@forEach
                        if (regex.containsMatchIn(line)) {
                            val ctx = buildString {
                                appendLine("${f.relativeTo(dirFile)}:${idx + 1}: $line")
                                if (`-C` > 0) {
                                    val lines = f.readLines()
                                    val ctxStart = (idx - `-C`).coerceAtLeast(0)
                                    val ctxEnd = (idx + `-C` + 1).coerceAtMost(lines.size)
                                    for (ci in ctxStart until ctxEnd) {
                                        if (ci != idx) appendLine("  ${lines[ci]}")
                                    }
                                }
                            }
                            matches.add(ctx.trimEnd())
                        }
                    }
                } catch (_: Exception) {}
            }
        }
        if (matches.isEmpty()) return ok("无匹配结果")
        return ok("${matches.size} 个匹配:\n${matches.joinToString("\n---\n")}")
    }

    fun glob(pattern: String, path: String = "", maxResults: Int = 100): String {
        val dir = if (path.isNotBlank()) resolvePath(path) else root
        val globRegex = try {
            Regex(pattern.replace(".", "\\.").replace("**/", "(.*?/)?")
                .replace("*", "[^/]*").replace("?", "[^/]"))
        } catch (e: Exception) { return err("Glob 模式无效: ${e.message}") }
        val results = mutableListOf<String>()
        File(dir).walkTopDown().forEach { f ->
            if (results.size >= maxResults) return@forEach
            if (f.isFile && globRegex.matches(f.name)) results.add(relativePath(f.absolutePath))
        }
        if (results.isEmpty()) return ok("无匹配文件")
        return ok("${results.size} 个文件:\n${results.joinToString("\n")}")
    }

    // ── 内部 ──

    private fun resolvePath(path: String): String {
        return if (path.startsWith("/")) path else "${root}/${path.trimStart('/')}".trimEnd('/')
    }

    private fun relativePath(abs: String): String {
        return abs.removePrefix(root).trimStart('/').ifEmpty { "/" }
    }

    companion object {
        fun buildOpenAIToolsJson(): JSONArray {
            val arr = JSONArray()
            arr.put(buildListFilesTool())
            arr.put(buildReadFileTool())
            arr.put(buildGrepTool())
            arr.put(buildGlobTool())
            return arr
        }

        private fun buildListFilesTool() = toolDef("listFiles",
            "列出目录内容，显示[FILE]/[DIR]前缀和文件大小。路径支持绝对或相对，留空表示项目根目录。",
            props = arrayOf(
                "path" to p("string", "目录路径，支持绝对路径或相对项目根目录，留空表示根目录"),
                "ignore" to p("string", "忽略模式，逗号分隔，如'*.class,*.jar'"),
            ),
        )
        private fun buildReadFileTool() = toolDef("readFile",
            "读取文件内容，返回纯文本。支持分页：offset从0开始，limit默认1000。超长文件自动截断。",
            required = listOf("file_path"),
            "file_path" to p("string", "文件路径 — 绝对路径或相对项目根目录"),
            "offset" to p("integer", "起始行号，从0开始计数，默认0"),
            "limit" to p("integer", "最大读取行数，默认1000"),
        )
        private fun buildGrepTool() = toolDef("grep",
            "正则搜索文件内容，返回匹配行及其上下文。",
            required = listOf("pattern"),
            "pattern" to p("string", "正则表达式，如'fun\\\\s+\\\\w+'查找函数定义"),
            "path" to p("string", "限定搜索目录，相对项目根目录，留空搜整个项目"),
            "glob" to p("string", "文件名过滤，如'*.kt'"),
            "-i" to p("boolean", "忽略大小写，默认true"),
            "head_limit" to p("integer", "最大返回结果行数，默认100"),
            "-C" to p("integer", "匹配前后上下文行数，默认2"),
        )
        private fun buildGlobTool() = toolDef("glob",
            "按文件名glob模式递归搜索目录。适合知道文件名但不知路径时使用。",
            required = listOf("pattern"),
            "pattern" to p("string", "Glob模式，如'*.kt'、'Main*'"),
            "path" to p("string", "限定搜索目录，留空从根目录搜索"),
            "maxResults" to p("integer", "最大返回结果数，默认100"),
        )
    }
}
