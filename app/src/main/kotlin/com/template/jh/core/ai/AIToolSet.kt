package com.template.jh.core.ai

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.google.ai.edge.litertlm.Tool
import com.google.ai.edge.litertlm.ToolParam
import com.google.ai.edge.litertlm.ToolSet
import com.template.jh.core.editor.CodeEditTool
import com.template.jh.core.storage.FileManager
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

//   单一 ToolSet 类 → @Tool 方法 → @ToolParam 参数 → 返回简单类型
class AIToolSet(
    private val context: Context,
    private val fileManager: FileManager? = null
) : ToolSet {

    // SAF 项目根 URI（用户通过文件夹选择器打开的目录）
    // 优先使用外部传入的 fileManager，否则使用内部 projectUri
    @Volatile var projectUri: Uri? = null
        set(value) {
            field = value
            value?.let { fileManager?.setProjectUri(it) }
        }

    // ---- 文件操作 ----

    @Tool(description = "List files and directories in the project folder. Returns directory contents with file sizes.")
    fun listFiles(
        @ToolParam(description = "Subdirectory path relative to project root. Leave empty to list root.") subPath: String = "",
    ): String {
        return fileManager?.listFiles(subPath) ?: "No project folder is open."
    }

    private fun readFileRaw(path: String): String? {
        return fileManager?.readFileRaw(path)
    }

    @Tool(description = "Read the content of a file in the project. Must be called before modifying a file. Returns content with line numbers. Supports pagination with offset and limit.")
    fun readFile(
        @ToolParam(description = "File path relative to project root, e.g. 'src/MainActivity.kt'") path: String,
        @ToolParam(description = "Line number to start reading from (1-based). Default 1.") offset: Int = 1,
        @ToolParam(description = "Maximum number of lines to read. Default 1000.") limit: Int = 1000,
    ): String {
        return fileManager?.readFileWithLineNumbers(path, offset, limit) 
            ?: "No project folder is open."
    }

    @Tool(description = "View a specific range of lines from a file. Optimized for viewing large files without loading entire content. Returns content with line numbers.")
    fun viewFile(
        @ToolParam(description = "File path relative to project root, e.g. 'src/MainActivity.kt'") path: String,
        @ToolParam(description = "Line number to start reading from (1-based). Default 1.") offset: Int = 1,
        @ToolParam(description = "Maximum number of lines to read. Default 100, max 500.") limit: Int = 100,
    ): String {
        val effectiveLimit = limit.coerceIn(1, 500)
        return fileManager?.viewFile(path, offset, effectiveLimit) 
            ?: "No project folder is open."
    }

    @Tool(description = "Create a new file. WARNING: For existing files, use applyPatch instead. Only use this when creating new files or completely rewriting (>80% changed).")
    fun writeFile(
        @ToolParam(description = "File path relative to project root, e.g. 'src/App.kt'") path: String,
        @ToolParam(description = "The complete text content to write to the file") content: String,
    ): String {
        val fm = fileManager ?: return "No project folder is open."
        val result = fm.writeFile(path, content)
        if (!result.startsWith("Failed") && !result.startsWith("No project")) {
            FileOperationEvents.notify(path, if (fm.exists(path)) "overwrite" else "create", content.lines().size, newContent = content)
        }
        return result
    }

    // 代码编辑工具 - 类似 SearchReplace，只修改指定内容
    // 这是唯一推荐的编辑方式：提供 old_string（要查找的代码块）和 new_string（新代码块）
    @Tool(description = "Edit an existing file by replacing a specific code block. Provide the exact code to find (old_string) and the replacement (new_string). The old_string must be unique in the file - include enough context (function signature, class name, etc.) to ensure uniqueness.")
    fun replaceInFile(
        @ToolParam(description = "File path relative to project root, e.g. 'src/MainActivity.kt'") path: String,
        @ToolParam(description = "The exact code block to find. Must be unique in the file. Include function signature or class definition for uniqueness.") old_string: String,
        @ToolParam(description = "The new code block to replace with.") new_string: String,
    ): String {
        val uri = projectUri
        if (uri == null) return "No project folder is open."
        return try {
            android.util.Log.d("AIToolSet", "replaceInFile: path=$path")
            val original = readFileRaw(path)
            if (original == null) {
                android.util.Log.w("AIToolSet", "replaceInFile: file not found: $path")
                return "Cannot replace: file not found. Use writeFile to create first."
            }

            // 使用 CodeEditTool 执行替换
            when (val result = CodeEditTool.replace(original, old_string, new_string)) {
                is CodeEditTool.ReplaceResult.Success -> {
                    val newContent = result.newText
                    // 真正写入文件
                    val writeResult = fileManager?.writeFile(path, newContent) ?: "No project folder is open."
                    if (writeResult.startsWith("Failed") || writeResult.startsWith("No project")) {
                        return "Replace succeeded but write failed: $writeResult"
                    }
                    FileOperationEvents.notify(path, "pending", computeChangedLines(original, newContent), original, newContent)
                    "Replace succeeded: $path. ${result.message}"
                }
                is CodeEditTool.ReplaceResult.Error -> {
                    "Replace failed:\n${result.message}"
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("AIToolSet", "replaceInFile failed: ${e.message}", e)
            "Replace failed: ${e.message}"
        }
    }

    private fun computeChangedLines(old: String, new: String): Int {
        val o = old.lines(); val n = new.lines()
        return (maxOf(o.size, n.size) - (o.zip(n).count { it.first == it.second })).coerceAtLeast(1)
    }

    @Tool(description = "Delete a file or directory in the project. Use with caution - this permanently removes files.")
    fun deleteFile(
        @ToolParam(description = "File or directory path relative to project root, e.g. 'src/OldFile.kt' or 'temp/'") path: String,
    ): String {
        val fm = fileManager ?: return "No project folder is open."
        return fm.deleteFile(path)
    }

    @Tool(description = "Create a new directory in the project.")
    fun createDirectory(
        @ToolParam(description = "Directory path relative to project root, e.g. 'src/utils' or 'assets/images'") path: String,
    ): String {
        val fm = fileManager ?: return "No project folder is open."
        return fm.createDirectory(path)
    }

    // ---- 终端命令 ----

    @Tool(description = "Run a shell command in the project directory. Use for adb, git, gradle commands.")
    fun runCommand(
        @ToolParam(description = "The command to execute, e.g. 'ls -la' or 'git status'") command: String,
    ): String {
        return try {
            val dir = if (projectUri != null) File(context.filesDir, "workspace").also { it.mkdirs() }
                else File(context.filesDir, "workspace").also { it.mkdirs() }
            val parts = command.split(" ")
            val pb = ProcessBuilder(parts).directory(dir).redirectErrorStream(true)
            val proc = pb.start()
            val text = proc.inputStream.bufferedReader().readText()
            proc.waitFor(30, java.util.concurrent.TimeUnit.SECONDS)
            proc.destroy()
            if (text.isBlank()) "Command completed with no output." else text.take(5000)
        } catch (e: Exception) {
            "Command failed: ${e.message}"
        }
    }

    // ---- 联网搜索 ----

    @Tool(description = "Search the internet for current information. Use when you need up-to-date data or cannot answer from knowledge.")
    fun searchWeb(
        @ToolParam(description = "Search query keywords, concise and specific") query: String,
    ): String {
        if (query.isBlank()) return "Search query is empty."
        return try {
            // 优先使用聚合搜索引擎 SearXNG
            val searxResult = searchViaSearxNG(query)
            if (searxResult.isNotBlank() && !searxResult.contains("失败") && !searxResult.contains("无结果")) {
                return searxResult
            }
            // 备选：使用 Bing
            val bingResult = searchViaBing(query)
            if (bingResult.isNotBlank() && !bingResult.contains("失败")) {
                return bingResult
            }
            // 最后的备选：DuckDuckGo
            searchViaDuckDuckGo(query)
        } catch (e: Exception) {
            "Search failed: ${e.message}"
        }
    }

    /**
     * SearXNG 搜索 - 开源搜索引擎聚合器，稳定性更高
     */
    private fun searchViaSearxNG(query: String): String {
        val encoded = URLEncoder.encode(query, "UTF-8")
        // 多个公共 SearXNG 实例，按可靠性排序
        val instances = listOf(
            "https://search.sapti.me/search?q=$encoded&format=json&language=all",
            "https://search.rhscz.eu/search?q=$encoded&format=json&language=all",
            "https://searx.be/search?q=$encoded&format=json&language=all",
            "https://search.bus-hit.me/search?q=$encoded&format=json&language=all",
        )
        
        for (url in instances) {
            try {
                val json = fetchUrl(url, timeoutMs = 10000)
                val obj = org.json.JSONObject(json)
                val results = obj.optJSONArray("results")
                
                if (results != null && results.length() > 0) {
                    val output = mutableListOf<String>()
                    for (i in 0 until minOf(results.length(), 5)) {
                        val item = results.optJSONObject(i) ?: continue
                        val title = item.optString("title", "").trim()
                        val content = item.optString("content", "").trim()
                        val link = item.optString("url", "").trim()
                        if (title.isNotBlank()) {
                            output.add("• $title${if (content.isNotBlank()) "\n  $content" else ""}${if (link.isNotBlank()) "\n  $link" else ""}")
                        }
                    }
                    if (output.isNotEmpty()) {
                        return "Found ${results.length()} results:\n\n${output.joinToString("\n---\n")}"
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("AIToolSet", "SearXNG $url failed: ${e.message}")
                continue
            }
        }
        return "SearXNG search failed or returned no results."
    }

    /**
     * Bing 搜索 - 作为备选
     */
    private fun searchViaBing(query: String): String {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = "https://www.bing.com/search?q=$encoded&setmkt=en-US&setlang=en"
        
        return try {
            val html = fetchUrl(url, timeoutMs = 15000, extraHeaders = mapOf(
                "Cookie" to "MUID=1; MUIDB=1",  // 匿名Cookie
                "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8",
            ))
            val results = parseBingResults(html)
            if (results.isNotEmpty()) {
                "Found ${results.size} results:\n\n${results.joinToString("\n---\n") { "• ${it.first}\n  ${it.second}" }}"
            } else {
                "Bing search returned no results."
            }
        } catch (e: Exception) {
            "Bing search failed: ${e.message}"
        }
    }

    private fun searchViaDuckDuckGo(query: String): String {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val urls = listOf(
            "https://lite.duckduckgo.com/lite/?q=$encoded",
            "https://html.duckduckgo.com/html/?q=$encoded",
        )

        for (searchUrl in urls) {
            try {
                val html = fetchUrl(searchUrl, timeoutMs = 15000)
                val results = parseDdgResults(html)
                if (results.isNotEmpty()) {
                    return results.joinToString("\n---\n") { "• ${it.first}\n  ${it.second}" }
                }
            } catch (_: Exception) { }
        }

        try {
            val apiUrl = "https://api.duckduckgo.com/?q=$encoded&format=json&no_html=1&skip_disambig=1"
            val json = fetchUrl(apiUrl, timeoutMs = 10000)
            val obj = org.json.JSONObject(json)
            val abstractText = obj.optString("AbstractText", "")
            val answer = obj.optString("Answer", "")
            val heading = obj.optString("Heading", "")
            val results = mutableListOf<String>()
            if (abstractText.isNotBlank()) results.add("$heading\n$abstractText")
            if (answer.isNotBlank() && answer != abstractText) results.add("Answer: $answer")
            val topics = obj.optJSONArray("RelatedTopics")
            if (topics != null) {
                for (i in 0 until minOf(topics.length(), 5)) {
                    val topic = topics.optJSONObject(i) ?: continue
                    val text = topic.optString("Text", "")
                    if (text.isNotBlank()) results.add("• ${text.take(200)}")
                }
            }
            if (results.isNotEmpty()) return results.joinToString("\n---\n")
        } catch (_: Exception) { }

        return "Search returned no results for: $query"
    }

    private fun fetchUrl(urlString: String, timeoutMs: Int = 15000, extraHeaders: Map<String, String> = emptyMap()): String {
        val url = URL(urlString)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36")
            setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en-US,en;q=0.8")
            setRequestProperty("Accept-Encoding", "gzip, deflate, br")
            setRequestProperty("DNT", "1")
            setRequestProperty("Connection", "keep-alive")
            extraHeaders.forEach { (k, v) -> setRequestProperty(k, v) }
        }
        
        val inputStream = if (conn.contentEncoding == "gzip") {
            java.util.zip.GZIPInputStream(conn.inputStream)
        } else {
            conn.inputStream
        }
        
        val html = inputStream.bufferedReader().readText()
        val code = conn.responseCode
        conn.disconnect()
        if (code != 200) throw RuntimeException("HTTP $code")
        return html
    }

    private fun parseBingResults(html: String): List<Pair<String, String>> {
        val results = mutableListOf<Pair<String, String>>()
        
        // Bing 搜索结果解析
        val patterns = listOf(
            // 新布局
            Regex("""<a[^>]*target=\"_blank\"[^>]*href=\"([^\"]+)\"[^>]*>(.*?)</a>.*?<p[^>]*>(.*?)</p>""", RegexOption.DOT_MATCHES_ALL),
            // 标准结果
            Regex("""<li class=\"b_algo[^\"]*\"[^>]*>.*?<h2[^>]*>.*?<a[^>]*href=\"([^\"]+)\"[^>]*>(.*?)</a>.*?</h2>.*?<p[^>]*>(.*?)</p>""", RegexOption.DOT_MATCHES_ALL),
            // 备选
            Regex("""<a[^>]*href=\"([^\"]+)\"[^>]*h=\"[^\"]*\"[^>]*>(.*?)</a>.*?<span[^>]*>(.*?)</span>""", RegexOption.DOT_MATCHES_ALL),
        )
        
        for (pattern in patterns) {
            for (m in pattern.findAll(html).take(5)) {
                val link = m.groupValues[1].replace(Regex("<[^>]*>"), "").trim()
                val title = m.groupValues[2].replace(Regex("<[^>]*>"), "").trim()
                val snippet = m.groupValues.getOrNull(3)?.replace(Regex("<[^>]*>"), "")?.trim() ?: ""
                if (title.isNotBlank() && title.length < 200) {
                    results.add(title to snippet)
                }
            }
            if (results.isNotEmpty()) break
        }
        return results.take(5)
    }

    private fun parseDdgResults(html: String): List<Pair<String, String>> {
        val results = mutableListOf<Pair<String, String>>()

        val litePattern = Regex(
            """class="result-link"[^>]*>\s*(.*?)\s*</a>.*?class="result-snippet"[^>]*>\s*(.*?)\s*</""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
        )
        for (m in litePattern.findAll(html).take(5)) {
            val t = m.groupValues[1].replace(Regex("<[^>]*>"), "").trim()
            val s = m.groupValues[2].replace(Regex("<[^>]*>"), "").trim()
            if (t.isNotBlank()) results.add(t to s)
        }
        if (results.isNotEmpty()) return results

        val htmlPattern = Regex(
            """class="result__a"[^>]*>\s*(.*?)\s*</a>.*?class="result__snippet"[^>]*>\s*(.*?)\s*</a>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
        )
        for (m in htmlPattern.findAll(html).take(5)) {
            val t = m.groupValues[1].replace(Regex("<[^>]*>"), "").trim()
            val s = m.groupValues[2].replace(Regex("<[^>]*>"), "").trim()
            if (t.isNotBlank()) results.add(t to s)
        }
        return results
    }

    // ---- Git 集成 ----

    @Tool(description = "Show git status (short format). Returns staged/unstaged changes.")
    fun gitStatus(): String = runGit("git status --short")

    @Tool(description = "Stage files for commit. Use '.' to stage all.")
    fun gitAdd(
        @ToolParam(description = "File paths to stage, space-separated. Use '.' for all") paths: String = ".",
    ): String = runGit("git add $paths")

    @Tool(description = "Commit staged changes with a message.")
    fun gitCommit(
        @ToolParam(description = "Commit message") message: String,
    ): String = runGit("git commit -m \"$message\"")

    @Tool(description = "Push commits to remote repository.")
    fun gitPush(
        @ToolParam(description = "Remote name, default 'origin'") remote: String = "origin",
        @ToolParam(description = "Branch name, e.g. 'main' or 'master'") branch: String = "main",
    ): String = runGit("git push $remote $branch")

    @Tool(description = "List local branches. Add '-a' to show remote branches too.")
    fun gitBranch(
        @ToolParam(description = "Extra args, e.g. '-a' for all, '-D name' to delete") args: String = "",
    ): String = runGit("git branch $args")

    @Tool(description = "Show diff of staged/unstaged changes.")
    fun gitDiff(
        @ToolParam(description = "Args: '--staged' for staged only, 'HEAD~1' for last commit") args: String = "",
    ): String = runGit("git diff $args")

    private fun runGit(cmd: String): String {
        val dir = if (projectUri != null) File(context.filesDir, "workspace").also { it.mkdirs() }
            else File(context.filesDir, "workspace").also { it.mkdirs() }
        return try {
            val pb = if (isWindows()) {
                ProcessBuilder("cmd", "/c", cmd).directory(dir).redirectErrorStream(true)
            } else {
                ProcessBuilder("sh", "-c", cmd).directory(dir).redirectErrorStream(true)
            }
            val proc = pb.start()
            val text = proc.inputStream.bufferedReader().readText()
            proc.waitFor(15, java.util.concurrent.TimeUnit.SECONDS)
            proc.destroy()
            if (text.isBlank()) "已完成，无输出。" else text.take(5000)
        } catch (e: Exception) { "命令失败: ${e.message}" }
    }

    private fun isWindows(): Boolean = System.getProperty("os.name")?.lowercase()?.contains("win") == true

    // ---- Lint/诊断读取 ----

    @Tool(description = "Read build lint or compilation errors from the project. Runs gradle lint if needed.")
    fun readLints(): String {
        return try {
            val buildDir = File(context.filesDir, "workspace")
            // 1. 尝试读取 lint 报告
            val lintFiles = listOf(
                File(buildDir, "app/build/reports/lint-results.xml"),
                File(buildDir, "app/build/reports/lint-results-release.xml"),
            )
            for (f in lintFiles) {
                if (f.exists() && f.length() > 0) {
                    val text = f.readText()
                    val issues = Regex("""<issue[^>]*severity="Error"[^>]*>""", RegexOption.IGNORE_CASE)
                        .findAll(text).take(20).joinToString("\n") { match ->
                            val id = Regex("""id="([^"]+)"""").find(match.value)?.groupValues[1] ?: "?"
                            val msg = Regex("""<message>([^<]+)</message>""").find(text, match.range.last)?.groupValues[1] ?: ""
                            "• $id: $msg"
                        }
                    if (issues.isNotBlank()) return "Lint errors:\n$issues"
                }
            }
            // 2. 尝试读取 Gradle 问题报告
            val problemFiles = listOf(
                File(buildDir, "build/reports/problems/problems-report.html"),
                File(buildDir, "app/build/reports/problems/problems-report.html"),
            )
            for (f in problemFiles) {
                if (f.exists() && f.length() > 0) {
                    val html = f.readText()
                    val errors = Regex("""<li[^>]*>(.*?)</li>""", RegexOption.DOT_MATCHES_ALL)
                        .findAll(html).take(10).map { it.groupValues[1].replace(Regex("<[^>]*>"), "").trim() }
                        .filter { it.isNotBlank() }.toList()
                    if (errors.isNotEmpty()) return "Compilation problems:\n${errors.joinToString("\n")}"
                }
            }
            // 3. 尝试运行 gradle lint（简短超时）
            val pb = if (isWindows()) ProcessBuilder("cmd", "/c", "gradlew lint")
                else ProcessBuilder("sh", "-c", "./gradlew lint")
            pb.directory(buildDir).redirectErrorStream(true)
            val proc = pb.start()
            val out = proc.inputStream.bufferedReader().readText()
            proc.waitFor(60, java.util.concurrent.TimeUnit.SECONDS)
            proc.destroy()
            // 从输出中提取错误
            val errors = out.lines().filter { it.contains("ERROR") || it.contains("error:") }.take(30)
            if (errors.isNotEmpty()) "Lint output errors:\n${errors.joinToString("\n")}" else "No lint errors found."
        } catch (e: Exception) { "读取诊断失败: ${e.message}" }
    }

    // ---- 文件搜索 ----

    @Tool(description = "Search file contents using regex pattern. Returns matching files with line numbers and context. Use for finding code patterns, function definitions, imports, etc.")
    fun grep(
        @ToolParam(description = "Regex pattern to search for, e.g. 'fun\\s+\\w+' to find function definitions") pattern: String,
        @ToolParam(description = "File extension filter, e.g. 'kt' for Kotlin files only. Leave empty for all text files.") extension: String = "",
        @ToolParam(description = "Glob pattern to filter files, e.g. '*.kt' or 'src/**/*.java'") glob: String = "",
        @ToolParam(description = "Case insensitive search, default true") ignoreCase: Boolean = true,
        @ToolParam(description = "Number of context lines to show before/after matches, default 2") contextLines: Int = 2,
    ): String {
        return fileManager?.grep(pattern, extension, glob, ignoreCase, contextLines)
            ?: "No project folder is open."
    }

    // ---- 语义搜索 ----

    @Tool(description = "Search codebase by meaning/semantics. Finds code by describing what you're looking for rather than exact text. Use for exploring unfamiliar code or finding implementations by behavior.")
    fun searchCodebase(
        @ToolParam(description = "Natural language query describing what you're looking for, e.g. 'Where is user authentication implemented?' or 'How does error handling work?'") query: String,
        @ToolParam(description = "Specific directories to search within, relative to project root. Leave empty to search entire project.") targetDirectories: String = "",
    ): String {
        return fileManager?.searchCodebase(query, targetDirectories)
            ?: "No project folder is open."
    }

    // ---- 图像生成（AnythingV5 SD1.5）----

    @Volatile var imageGenManager: com.template.jh.core.ai.ImageGenManager? = null

    @Tool(description = "Generate an image using Stable Diffusion 1.5 (AnythingV5). Provide a detailed prompt describing what you want to generate. Returns the file path of the generated image. Model must be downloaded first via Settings > 推荐下载模型 > 下载模型.")
    fun generateImage(
        @ToolParam(description = "Detailed description of the image to generate. Be specific about subject, style, lighting, colors, etc.") prompt: String,
        @ToolParam(description = "Things to avoid in the generated image, e.g. 'nsfw, low quality, blurry'. Optional.") negativePrompt: String = "",
        @ToolParam(description = "Number of denoising steps. Higher = more detail but slower. Range 10-50, default 20.") steps: Int = 20,
        @ToolParam(description = "Classifier-Free Guidance scale. Higher = follows prompt more closely. Range 1-20, default 7.") cfgScale: Float = 7.0f,
        @ToolParam(description = "Width of the output image in pixels. Default 512.") width: Int = 512,
        @ToolParam(description = "Height of the output image in pixels. Default 512.") height: Int = 512,
    ): String {
        val mgr = imageGenManager ?: return "Image generation is not available. Configure ImageGenManager first."
        try {
            // 检查模型是否已下载
            if (!mgr.isModelDownloaded(ImageGenManager.ANYTHING_V5_MODEL.fileName)) {
                return "Model not downloaded yet. Please go to Settings > 推荐下载模型 to download the image generation model first."
            }

            val params = com.template.jh.core.ai.ImageGenParams(
                prompt = prompt,
                negativePrompt = negativePrompt,
                steps = steps.coerceIn(10, 50),
                cfgScale = cfgScale.coerceIn(1f, 20f),
                seed = -1,
                width = width.coerceIn(256, 2048),
                height = height.coerceIn(256, 2048),
            )
            val result = kotlinx.coroutines.runBlocking {
                mgr.generateImage(params)
            }
            return "Image generated successfully. File path: $result"
        } catch (e: Exception) {
            return "Image generation failed: ${e.message}"
        }
    }

    @Tool(description = "List recently generated images in the output directory. Returns file names and paths.")
    fun listGeneratedImages(): String {
        val mgr = imageGenManager ?: return "ImageGenManager not available."
        val images = mgr.listGeneratedImages()
        if (images.isEmpty()) return "No generated images found."
        return images.joinToString("\n") { "• ${it.name}  (${formatSize(it.size)})  path: ${it.path}" }
    }

    private fun formatSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> "${"%.1f".format(bytes.toDouble() / (1024 * 1024))} MB"
    }

}
