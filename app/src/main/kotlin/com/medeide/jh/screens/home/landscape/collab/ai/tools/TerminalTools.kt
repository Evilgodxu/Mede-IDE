package com.medeide.jh.screens.home.landscape.collab.ai.tools

import android.content.Context
import android.util.Log
import com.medeide.jh.data.storage.FileManager
import com.medeide.jh.data.utils.FileLogger
import java.io.File

class TerminalTools(
    private val context: Context,
    private val fileManager: FileManager?,
) {
    // ================================================================
    //  执行命令  ←→ RunCommand(command, target_terminal, command_type, cwd, blocking, requires_approval, wait_ms_before_async)
    // ================================================================

    fun runCommand(
        command: String,
        target_terminal: String = "",
        command_type: String = "other",
        cwd: String = "",
        blocking: Boolean = true,
        requires_approval: Boolean = false,
        wait_ms_before_async: Int = 0,
    ): String {
        Log.d("AIToolSet", "runCommand: command=$command target_terminal=$target_terminal command_type=$command_type cwd=$cwd blocking=$blocking requires_approval=$requires_approval wait_ms_before_async=$wait_ms_before_async")
        FileLogger.d("AIToolSet", "runCommand: $command")
        return try {
            val dir = if (cwd.isNotBlank()) File(cwd) else resolveProjectDir()
            val parts = parseCommandLine(command)
            val pb = ProcessBuilder(parts).directory(dir).redirectErrorStream(true)
            val proc = pb.start()
            val text = proc.inputStream.bufferedReader().readText()
            proc.waitFor(30, java.util.concurrent.TimeUnit.SECONDS)
            val exitCode = proc.exitValue()
            proc.destroy()
            if (text.isBlank()) ok("Command completed with no output (exit code $exitCode).")
            else ok("Exit code: $exitCode\n$text".take(5000))
        } catch (e: Exception) {
            Log.e("AIToolSet", "runCommand failed: ${e.message}", e)
            FileLogger.e("AIToolSet", "runCommand failed: ${e.message}", e)
            err("${e.message}")
        }
    }

    // ================================================================
    //  读取诊断  ←→ GetDiagnostics(uri)
    //  原名 readLints → getDiagnostics
    // ================================================================

    fun getDiagnostics(uri: String = ""): String {
        Log.d("AIToolSet", "getDiagnostics: uri=$uri")
        FileLogger.d("AIToolSet", "getDiagnostics: uri=$uri")
        return try {
            val buildDir = File(context.filesDir, "workspace")
            // 如果指定了 uri，优先解析该文件路径
            val basePath = if (uri.isNotBlank()) {
                val f = File(uri)
                if (f.isAbsolute) f else File(buildDir, uri)
            } else null
            val lintFiles = listOf(
                Pair(File(buildDir, "app/build/reports/lint-results.xml"), "lint-results.xml"),
                Pair(File(buildDir, "app/build/reports/lint-results-release.xml"), "lint-results-release.xml"),
            )
            for ((f, name) in lintFiles) {
                if (f.exists() && f.length() > 0) {
                    val text = f.readText()
                    val issues = mutableListOf<String>()
                    val issueRegex = Regex("""<issue\s+[^>]*severity="(Error|Fatal)"[^>]*>""", RegexOption.IGNORE_CASE)
                    for (match in issueRegex.findAll(text).take(30)) {
                        val seg = text.substring(match.range.first, (match.range.first + 2000).coerceAtMost(text.length))
                        val id = Regex("""id="([^"]+)"""").find(match.value)?.groupValues[1] ?: "?"
                        val msg = Regex("""<message>([^<]+)</message>""").find(seg)?.groupValues[1] ?: ""
                        val location = Regex("""<location[^>]*file="([^"]+)"[^>]*(?:line="(\d+)")?""")
                            .find(seg)?.let { m ->
                                val file = m.groupValues[1].substringAfterLast("/")
                                val line = m.groupValues[2]
                                if (line.isNotBlank()) "($file:$line)" else "($file)"
                            } ?: "($name)"
                        // uri 过滤
                        if (basePath != null && !location.contains(basePath.name)) continue
                        issues.add("• $id$location: $msg")
                    }
                    if (issues.isNotEmpty()) {
                        FileLogger.d("AIToolSet", "getDiagnostics: in $name, ${issues.size} issues")
                        return ok("${issues.size} lint errors in $name:\n${issues.joinToString("\n")}")
                    }
                }
            }
            val problemFiles = listOf(
                File(buildDir, "build/reports/problems/problems-report.html"),
                File(buildDir, "app/build/reports/problems/problems-report.html"),
            )
            for (f in problemFiles) {
                if (f.exists() && f.length() > 0) {
                    val html = f.readText()
                    val errors = Regex("""<li[^>]*data-type="error"[^>]*>(.*?)</li>""", RegexOption.DOT_MATCHES_ALL)
                        .findAll(html).take(15).map {
                            it.groupValues[1].replace(Regex("<[^>]*>"), "").trim()
                        }.filter { it.isNotBlank() }.toList()
                    if (errors.isNotEmpty()) {
                        val filtered = if (basePath != null) errors.filter { it.contains(basePath.name) } else errors
                        if (filtered.isNotEmpty()) {
                            FileLogger.d("AIToolSet", "getDiagnostics: in ${f.name}, ${filtered.size} problems")
                            return ok("${filtered.size} compilation problems in ${f.name}:\n${filtered.joinToString("\n")}")
                        }
                    }
                }
            }
            val pb = if (isWindows()) ProcessBuilder("cmd", "/c", "gradlew lint")
                else ProcessBuilder("sh", "-c", "./gradlew lint")
            pb.directory(buildDir).redirectErrorStream(true)
            val proc = pb.start()
            val out = proc.inputStream.bufferedReader().readText()
            proc.waitFor(60, java.util.concurrent.TimeUnit.SECONDS)
            proc.destroy()
            var errors = out.lines().filter {
                it.contains("ERROR") && it.contains(".kt:")
            }.map { it.trim() }.take(30)
            if (basePath != null) errors = errors.filter { it.contains(basePath.name) }
            if (errors.isNotEmpty()) {
                FileLogger.d("AIToolSet", "getDiagnostics: ${errors.size} compile errors")
                return ok("${errors.size} compile errors:\n${errors.joinToString("\n")}")
            }
            val genericErrors = out.lines().filter { it.contains("ERROR") || it.contains("error:") }.take(20)
            if (basePath != null) {
                val filtered = genericErrors.filter { it.contains(basePath.name) }
                if (filtered.isNotEmpty()) return ok("${filtered.size} errors:\n${filtered.joinToString("\n")}")
            }
            if (genericErrors.isNotEmpty()) {
                return ok("${genericErrors.size} errors from gradle lint:\n${genericErrors.joinToString("\n")}")
            }
            ok("No lint errors found.")
        } catch (e: Exception) {
            Log.e("AIToolSet", "getDiagnostics failed: ${e.message}", e)
            FileLogger.e("AIToolSet", "getDiagnostics failed: ${e.message}", e)
            err("Lint scan failed: ${e.message}")
        }
    }

    // ================================================================
    //  内部辅助
    // ================================================================

    private fun resolveProjectDir(): File {
        val rootPath = fileManager?.projectDirPath?.ifEmpty { fileManager.storageRootPath }
        if (rootPath != null) {
            val dir = File(rootPath)
            if (dir.isDirectory) return dir
        }
        return File(context.filesDir, "workspace").also { it.mkdirs() }
    }

    private fun parseCommandLine(input: String): List<String> {
        val args = mutableListOf<String>()
        val current = StringBuilder()
        var inQuote: Char? = null
        var escaped = false
        for (c in input) {
            when {
                escaped -> { current.append(c); escaped = false }
                c == '\\' -> escaped = true
                inQuote != null && c == inQuote -> inQuote = null
                inQuote == null && (c == '"' || c == '\'') -> inQuote = c
                inQuote == null && c.isWhitespace() -> {
                    if (current.isNotEmpty()) { args.add(current.toString()); current.clear() }
                }
                else -> current.append(c)
            }
        }
        if (current.isNotEmpty()) args.add(current.toString())
        return args
    }

    private fun isWindows(): Boolean = System.getProperty("os.name")?.lowercase()?.contains("win") == true

    // ================================================================
    //  工具定义
    // ================================================================

    companion object {
        fun buildOpenAIToolsJson(): org.json.JSONArray {
            val arr = org.json.JSONArray()
            arr.put(buildRunCommandTool())
            arr.put(buildGetDiagnosticsTool())
            return arr
        }

        fun toolNames(): List<String> = listOf("runCommand", "getDiagnostics")

        private fun buildRunCommandTool() = toolDef("runCommand",
            "执行shell命令，在当前项目目录下运行。超时30秒，输出上限5000字符。",
            listOf("command"),
            "command" to p("string", "要执行的命令，如'ls -la'或'./gradlew build'"),
            "target_terminal" to p("string", "目标终端标识（Android环境仅一个终端），可选"),
            "command_type" to p("string", "命令类型：web_server/long_running_process/short_running_process/other，默认other"),
            "cwd" to p("string", "工作目录路径，留空使用项目根目录"),
            "blocking" to p("boolean", "是否阻塞等待完成，默认true"),
            "requires_approval" to p("boolean", "是否需要用户批准执行，默认false"),
            "wait_ms_before_async" to p("integer", "异步模式的初始等待毫秒数，默认0"),
        )
        private fun buildGetDiagnosticsTool() = toolDef("getDiagnostics",
            "读取构建/lint/编译错误诊断，返回文件路径和行号。无缓存时自动运行gradle lint。可选按uri过滤。",
            props = arrayOf(
                "uri" to p("string", "可选的文件路径过滤，仅返回该文件的诊断结果"),
            ),
        )
    }
}
