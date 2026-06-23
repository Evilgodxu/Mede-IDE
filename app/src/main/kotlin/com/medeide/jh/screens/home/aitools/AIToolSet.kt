package com.medeide.jh.screens.home.aitools

import android.content.Context
import android.net.Uri
import com.google.ai.edge.litertlm.Tool
import com.google.ai.edge.litertlm.ToolParam
import com.google.ai.edge.litertlm.ToolSet
import com.medeide.jh.screens.home.memory.ConversationMemory
import com.medeide.jh.data.storage.FileManager
import com.medeide.jh.screens.home.aitools.tools.*

/** 工具执行状态回调 — 由 ChatViewModel 注入，打破 automaticToolCalling 黑盒 */
interface ToolExecutionCallback {
    fun onToolStart(name: String, args: Map<String, String>)
    fun onToolResult(name: String, args: Map<String, String>, result: String)
}

class AIToolSet(
    private val fileManager: FileManager? = null,
    private val conversationMemory: ConversationMemory? = null,
    private val openFileCallback: ((String) -> Unit)? = null,
    private val getCurrentFileCallback: (() -> String?)? = null,
    private val context: Context? = null,
) : ToolSet {

    private val readTools = ReadTools(fileManager, conversationMemory)
    private val editTools = EditTools(fileManager)
    private val webTools = WebTools(fileManager)
    private val terminalTools = TerminalTools(fileManager)
    private val editorTools = EditorTools(fileManager, openFileCallback, getCurrentFileCallback)
    private val programmingTools = ProgrammingTools(fileManager)
    private val projectTools = ProjectTools(fileManager)
    private val systemTools = SystemTools(fileManager, context)

    /** ChatViewModel 注入的回调，每次 @Tool 方法执行前后自动调用 */
    @Volatile
    var callback: ToolExecutionCallback? = null

    /** 当前对话 ID，由 ChatViewModel 设置，记忆工具按此隔离 */
    @Volatile
    var currentConversationId: String? = null

    /** 包裹 @Tool 方法：自动触发 onToolStart / onToolResult */
    private inline fun <T> traceTool(name: String, block: () -> T): T {
        callback?.onToolStart(name, emptyMap())
        val result = block()
        callback?.onToolResult(name, emptyMap(), result?.toString() ?: "")
        return result
    }

    /** 带参数的包裹版本 */
    private inline fun <T> traceTool(name: String, vararg params: Pair<String, Any?>, block: () -> T): T {
        val args = params.associate { it.first to (it.second?.toString() ?: "") }
        callback?.onToolStart(name, args)
        val result = block()
        callback?.onToolResult(name, args, result?.toString() ?: "")
        return result
    }

    /** 获取项目根目录的绝对路径（用于上下文注入） */
    fun getProjectRootPath(): String {
        return fileManager?.let {
            it.projectDirPath.ifEmpty { it.storageRootPath }
        } ?: ""
    }

    companion object {
        /** 构建 OpenAI 兼容的 tools 定义 JSON（聚合各子工具定义） */
        fun buildOpenAIToolsJson(): String {
            val tools = org.json.JSONArray()
            val readJson = ReadTools.buildOpenAIToolsJson()
            for (i in 0 until readJson.length()) tools.put(readJson.get(i))
            val editJson = EditTools.buildOpenAIToolsJson()
            for (i in 0 until editJson.length()) tools.put(editJson.get(i))
            val webJson = WebTools.buildOpenAIToolsJson()
            for (i in 0 until webJson.length()) tools.put(webJson.get(i))
            val terminalJson = TerminalTools.buildOpenAIToolsJson()
            for (i in 0 until terminalJson.length()) tools.put(terminalJson.get(i))
            val editorJson = EditorTools.buildOpenAIToolsJson()
            for (i in 0 until editorJson.length()) tools.put(editorJson.get(i))
            val programmingJson = ProgrammingTools.buildOpenAIToolsJson()
            for (i in 0 until programmingJson.length()) tools.put(programmingJson.get(i))
            val projectJson = ProjectTools.buildOpenAIToolsJson()
            for (i in 0 until projectJson.length()) tools.put(projectJson.get(i))
            val systemJson = SystemTools.buildOpenAIToolsJson()
            for (i in 0 until systemJson.length()) tools.put(systemJson.get(i))
            return tools.toString()
        }
    }

    // ================================================================
    //  阅读工具 — 对齐 LS / Read / Grep / SearchCodebase / Glob
    // ================================================================

    @Tool(description = "列出目录内容，显示[FILE]/[DIR]前缀和文件大小。路径支持绝对或相对，留空表示项目根目录。")
    fun listFiles(
        @ToolParam(description = "目录路径，支持绝对路径或相对项目根目录，留空表示根目录") path: String = "",
        @ToolParam(description = "忽略模式，逗号分隔的glob，如'*.class,*.jar'") ignore: String = "",
    ): String = traceTool("listFiles", "path" to path, "ignore" to ignore) {
        readTools.listFiles(path, ignore)
    }

    @Tool(description = "读取文件内容，返回纯文本无行号前缀。支持分页：offset从0开始，limit默认1000。超500行自动截断为前200行。")
    fun readFile(
        @ToolParam(description = "文件路径，绝对路径或相对项目根目录，如'app/src/main.kt'") file_path: String,
        @ToolParam(description = "起始行号，从0开始计数，默认0") offset: Int = 0,
        @ToolParam(description = "最大读取行数，默认1000") limit: Int = 1000,
    ): String = traceTool("readFile", "file_path" to file_path, "offset" to offset, "limit" to limit) {
        readTools.readFile(file_path, offset, limit)
    }

    @Tool(description = "正则搜索文件内容，返回匹配行。支持过滤、上下文、分页等多种模式。")
    fun grep(
        @ToolParam(description = "正则表达式，如'fun\\\\s+\\\\w+'查找函数定义") pattern: String,
        @ToolParam(description = "限定搜索目录，相对项目根目录，留空搜整个项目") path: String = "",
        @ToolParam(description = "Glob模式过滤，如'*.kt'或'src/**/*.java'") glob: String = "",
        @ToolParam(description = "输出模式：content(默认)显示匹配行，files_with_matches仅列文件名，count仅计数") output_mode: String = "content",
        @ToolParam(description = "显示行号（默认已在输出中）") `-n`: Boolean = false,
        @ToolParam(description = "忽略大小写，默认true") `-i`: Boolean = true,
        @ToolParam(description = "文件类型过滤，如'kt'只查Kotlin，留空查所有") type: String = "",
        @ToolParam(description = "最大返回结果行数，默认100") head_limit: Int = 100,
        @ToolParam(description = "跳过的结果行数，用于分页，默认0") offset: Int = 0,
        @ToolParam(description = "启用多行模式(.匹配换行符)，默认false") multiline: Boolean = false,
        @ToolParam(description = "匹配前后上下文行数，默认2") `-C`: Int = 2,
        @ToolParam(description = "匹配前行数（覆盖-C的上前缀），默认0") `-B`: Int = 0,
        @ToolParam(description = "匹配后行数（覆盖-C的下后缀），默认0") `-A`: Int = 0,
    ): String = traceTool("grep",
        "pattern" to pattern, "path" to path, "glob" to glob, "output_mode" to output_mode,
        "-n" to `-n`, "-i" to `-i`, "type" to type, "head_limit" to head_limit,
        "offset" to offset, "multiline" to multiline, "-C" to `-C`, "-B" to `-B`, "-A" to `-A`,
    ) { readTools.grep(pattern, path, glob, output_mode, `-n`, `-i`, type, head_limit, offset, multiline, `-C`, `-B`, `-A`) }

    @Tool(description = "语义搜索代码库，按含义查找相关代码。适合探索陌生代码或用自然语言描述找实现。比grep更适合模糊查询。")
    fun searchCodebase(
        @ToolParam(description = "自然语言描述，如'用户认证在哪'或'错误处理怎么工作'") information_request: String,
        @ToolParam(description = "限定搜索目录，相对项目根目录，留空搜索整个项目") target_directories: String = "",
    ): String = traceTool("searchCodebase", "information_request" to information_request, "target_directories" to target_directories) {
        readTools.searchCodebase(information_request, target_directories)
    }

    @Tool(description = "按文件名glob模式搜索，如'*.kt'、'Main*'。递归搜索项目根目录。适合知道文件名但不知完整路径时。")
    fun glob(
        @ToolParam(description = "Glob模式，如'*.kt'所有Kotlin文件，'Main*'Main开头文件，'**/*.xml'所有XML") pattern: String,
        @ToolParam(description = "限定搜索目录，相对项目根目录，留空从根目录开始") path: String = "",
        @ToolParam(description = "最大返回结果数，默认100") maxResults: Int = 100,
    ): String = traceTool("glob", "pattern" to pattern, "path" to path, "maxResults" to maxResults) {
        readTools.glob(pattern, path, maxResults)
    }

    @Tool(description = "按关键词搜索对话历史。")
    fun searchConversationMemory(
        @ToolParam(description = "搜索内容，如'文件结构'或'错误修复'") query: String,
    ): String = readTools.searchConversationMemory(query, currentConversationId)

    @Tool(description = "获取最近对话消息全文。每次返回最近指定条数，count默认1。")
    fun getRecentConversationMemory(
        @ToolParam(description = "最近条目数，默认1") count: Int = 1,
    ): String = readTools.getRecentConversationMemory(count, currentConversationId)

    // ================================================================
    //  编辑工具 — 对齐 Write / SearchReplace / DeleteFile
    // ================================================================

    @Tool(description = "创建新文件。overwrite=false(默认)时若文件已存在则报错并提示用replaceInFile修改；overwrite=true则覆盖。")
    fun writeFile(
        @ToolParam(description = "文件路径，绝对路径或相对项目根目录，如'src/App.kt'") file_path: String,
        @ToolParam(description = "要写入的完整文本内容") content: String,
        @ToolParam(description = "是否覆盖已有文件，默认false") overwrite: Boolean = false,
    ): String = traceTool("writeFile", "file_path" to file_path, "content" to content, "overwrite" to overwrite) {
        editTools.writeFile(file_path, content, overwrite)
    }

    @Tool(description = "精确替换代码块。old_str必须在全文唯一匹配，替换为新内容。")
    fun replaceInFile(
        @ToolParam(description = "文件路径，绝对路径或相对项目根目录，如'src/MainActivity.kt'") file_path: String,
        @ToolParam(description = "要查找的精确代码块，在文件中必须唯一匹配") old_str: String,
        @ToolParam(description = "替换后的新代码块") new_str: String,
    ): String = traceTool("replaceInFile", "file_path" to file_path, "old_str" to old_str, "new_str" to new_str) {
        editTools.replaceInFile(file_path, old_str, new_str)
    }

    @Tool(description = "批量编辑，一次替换多处不重叠代码。edits基于原始文件同时匹配（非顺序应用），编辑不能重叠。单次编辑请用replaceInFile。")
    fun batchReplaceInFile(
        @ToolParam(description = "文件路径，绝对路径或相对项目根目录，如'src/MainActivity.kt'") path: String,
        @ToolParam(description = "JSON数组：[{\"old_string\":\"原代码\",\"new_string\":\"新代码\"}]") edits: String,
    ): String = traceTool("batchReplaceInFile", "path" to path, "edits" to edits) {
        editTools.batchReplaceInFile(path, edits)
    }

    @Tool(description = "删除文件或目录。支持批量删除，可传单个路径、逗号分隔路径或多个JSON字符串数组。谨慎使用，删除后无法恢复。")
    fun deleteFile(
        @ToolParam(description = "文件/目录路径或路径列表 — 可传'path'或'path1,path2'或'[\"p1\",\"p2\"]'") file_paths: String,
    ): String = traceTool("deleteFile", "file_paths" to file_paths) { editTools.deleteFile(file_paths) }

    @Tool(description = "创建目录，自动创建所有父目录。支持多级路径如'src/utils/helpers'")
    fun createDirectory(
        @ToolParam(description = "目录路径，绝对路径或相对项目根目录，如'src/utils'") path: String,
    ): String = traceTool("createDirectory", "path" to path) { editTools.createDirectory(path) }

    @Tool(description = "移动/重命名文件或目录。")
    fun moveFile(
        @ToolParam(description = "源路径") source: String,
        @ToolParam(description = "目标路径") destination: String,
    ): String = traceTool("moveFile", "source" to source, "destination" to destination) {
        editTools.moveFile(source, destination)
    }

    @Tool(description = "复制文件或目录。")
    fun copyFile(
        @ToolParam(description = "源路径") source: String,
        @ToolParam(description = "目标路径") destination: String,
    ): String = traceTool("copyFile", "source" to source, "destination" to destination) {
        editTools.copyFile(source, destination)
    }

    // ================================================================
    //  联网工具 — 对齐 WebSearch / WebFetch
    // ================================================================

    @Tool(description = "联网搜索最新信息。获取实时数据或超出知识范围的内容。")
    fun searchWeb(
        @ToolParam(description = "搜索关键词，简洁明确") query: String,
        @ToolParam(description = "返回结果数量，1-20，默认5") num: Int = 5,
        @ToolParam(description = "语言限制，如'lang_en'限制英文结果") lr: String = "",
    ): String = webTools.searchWeb(query, num, lr)

    @Tool(description = "访问网页并提取文本内容。用于查阅文档、获取网页信息等。")
    fun visitWeb(
        @ToolParam(description = "网页 URL") url: String,
    ): String = webTools.visitWeb(url)

    @Tool(description = "从 URL 下载文件到本地路径。")
    fun downloadFile(
        @ToolParam(description = "文件下载 URL") url: String,
        @ToolParam(description = "保存路径") destination: String,
    ): String = traceTool("downloadFile", "url" to url, "destination" to destination) {
        webTools.downloadFile(url, destination)
    }

    @Tool(description = "发送 HTTP 请求，返回响应内容。支持 GET/POST/PUT/DELETE。")
    fun httpRequest(
        @ToolParam(description = "请求 URL") url: String,
        @ToolParam(description = "HTTP 方法：GET/POST/PUT/DELETE，默认 GET") method: String = "GET",
        @ToolParam(description = "可选的请求头，JSON 对象字符串") headers: String = "",
        @ToolParam(description = "可选的请求体（POST/PUT 时使用）") body: String = "",
    ): String = webTools.httpRequest(url, method, headers, body)

    // ================================================================
    //  终端工具 — 执行命令、编译、Git 等
    // ================================================================

    @Tool(description = "执行任意 shell 命令。用于编译、运行、调试等操作。谨慎使用，避免执行危险命令。")
    fun executeCommand(
        @ToolParam(description = "要执行的 shell 命令，如'ls -la'或'./gradlew build'") command: String,
        @ToolParam(description = "工作目录，相对项目根目录，留空使用项目根目录") workingDir: String = "",
        @ToolParam(description = "超时时间（秒），默认60秒") timeoutSeconds: Int = 60,
    ): String = traceTool("executeCommand", "command" to command, "workingDir" to workingDir, "timeoutSeconds" to timeoutSeconds) {
        terminalTools.executeCommand(command, workingDir, timeoutSeconds)
    }

    @Tool(description = "编译项目。默认使用 Gradle 构建命令。适用于 Android 项目编译。")
    fun compileProject(
        @ToolParam(description = "项目目录，相对项目根目录，留空使用项目根目录") workingDir: String = "",
        @ToolParam(description = "构建命令，默认'./gradlew assembleDebug'") buildCommand: String = "./gradlew assembleDebug",
    ): String = traceTool("compileProject", "workingDir" to workingDir, "buildCommand" to buildCommand) {
        terminalTools.compileProject(workingDir, buildCommand)
    }

    @Tool(description = "运行项目测试。默认使用 Gradle 测试命令。")
    fun runTests(
        @ToolParam(description = "项目目录，相对项目根目录，留空使用项目根目录") workingDir: String = "",
        @ToolParam(description = "测试命令，默认'./gradlew test'") testCommand: String = "./gradlew test",
    ): String = traceTool("runTests", "workingDir" to workingDir, "testCommand" to testCommand) {
        terminalTools.runTests(workingDir, testCommand)
    }

    @Tool(description = "执行 Git 命令。用于版本控制操作，如'commit'、'push'、'status'等。")
    fun gitCommand(
        @ToolParam(description = "Git 子命令，如'status'、'log --oneline'、'commit -m \"message\"'") command: String,
        @ToolParam(description = "Git 仓库目录，相对项目根目录") workingDir: String = "",
    ): String = traceTool("gitCommand", "command" to command, "workingDir" to workingDir) {
        terminalTools.gitCommand(command, workingDir)
    }

    @Tool(description = "显示目录树结构。支持深度控制和忽略模式。")
    fun listDirectoryTree(
        @ToolParam(description = "起始目录，相对项目根目录，留空从根目录开始") path: String = "",
        @ToolParam(description = "递归深度，默认2") depth: Int = 2,
        @ToolParam(description = "忽略模式，逗号分隔，如'*.class,*.jar,.git'") ignorePatterns: String = "",
    ): String = traceTool("listDirectoryTree", "path" to path, "depth" to depth, "ignorePatterns" to ignorePatterns) {
        terminalTools.listDirectoryTree(path, depth, ignorePatterns)
    }

    @Tool(description = "检查文件或目录是否存在。")
    fun checkFileExists(
        @ToolParam(description = "文件或目录路径，相对项目根目录") path: String,
    ): String = traceTool("checkFileExists", "path" to path) {
        terminalTools.checkFileExists(path)
    }

    @Tool(description = "获取文件详细信息，包括大小、类型、修改时间等。")
    fun getFileInfo(
        @ToolParam(description = "文件或目录路径，相对项目根目录") path: String,
    ): String = traceTool("getFileInfo", "path" to path) {
        terminalTools.getFileInfo(path)
    }

    @Tool(description = "按文件名搜索文件。支持大小写不敏感匹配。")
    fun findFileByName(
        @ToolParam(description = "要查找的文件名，如'MainActivity.kt'") name: String,
        @ToolParam(description = "搜索起始目录，相对项目根目录，留空从根目录开始") startPath: String = "",
        @ToolParam(description = "是否大小写敏感，默认false") caseSensitive: Boolean = false,
    ): String = traceTool("findFileByName", "name" to name, "startPath" to startPath, "caseSensitive" to caseSensitive) {
        terminalTools.findFileByName(name, startPath, caseSensitive)
    }

    // ================================================================
    //  编辑器工具 — 打开文件、查看、编辑等
    // ================================================================

    @Tool(description = "在编辑器中打开文件。这是最常用的工具，用于查看和编辑代码。")
    fun openFile(
        @ToolParam(description = "文件路径，相对项目根目录，如'app/src/main/MainActivity.kt'") file_path: String,
    ): String = traceTool("openFile", "file_path" to file_path) {
        editorTools.openFile(file_path)
    }

    @Tool(description = "获取当前在编辑器中打开的文件名和路径。")
    fun getCurrentFile(): String = traceTool("getCurrentFile") {
        editorTools.getCurrentFile()
    }

    @Tool(description = "查看文件内容，支持指定行号和上下文行数。")
    fun viewFile(
        @ToolParam(description = "文件路径，相对项目根目录") file_path: String,
        @ToolParam(description = "中心行号，默认1") line: Int = 1,
        @ToolParam(description = "上下上下文行数，默认10") contextLines: Int = 10,
    ): String = traceTool("viewFile", "file_path" to file_path, "line" to line, "contextLines" to contextLines) {
        editorTools.viewFile(file_path, line, contextLines)
    }

    @Tool(description = "在文件末尾追加内容。适合添加新代码、配置等。")
    fun appendToFile(
        @ToolParam(description = "文件路径，相对项目根目录") file_path: String,
        @ToolParam(description = "要追加的内容") content: String,
    ): String = traceTool("appendToFile", "file_path" to file_path, "content" to content) {
        editorTools.appendToFile(file_path, content)
    }

    @Tool(description = "在文件开头插入内容。适合添加头部注释、import等。")
    fun prependToFile(
        @ToolParam(description = "文件路径，相对项目根目录") file_path: String,
        @ToolParam(description = "要插入的内容") content: String,
    ): String = traceTool("prependToFile", "file_path" to file_path, "content" to content) {
        editorTools.prependToFile(file_path, content)
    }

    @Tool(description = "截断文件，保留指定行数。谨慎使用，删除的内容无法恢复。")
    fun truncateFile(
        @ToolParam(description = "文件路径，相对项目根目录") file_path: String,
        @ToolParam(description = "保留的行数，默认0（清空文件）") linesToKeep: Int = 0,
    ): String = traceTool("truncateFile", "file_path" to file_path, "linesToKeep" to linesToKeep) {
        editorTools.truncateFile(file_path, linesToKeep)
    }

    @Tool(description = "重命名文件。只改变文件名，不改变目录。")
    fun renameFile(
        @ToolParam(description = "原文件路径，相对项目根目录") old_path: String,
        @ToolParam(description = "新文件名，不含路径") new_name: String,
    ): String = traceTool("renameFile", "old_path" to old_path, "new_name" to new_name) {
        editorTools.renameFile(old_path, new_name)
    }

    @Tool(description = "创建文件备份。默认后缀为.bak。")
    fun backupFile(
        @ToolParam(description = "文件路径，相对项目根目录") file_path: String,
        @ToolParam(description = "备份后缀，默认'.bak'") backupSuffix: String = ".bak",
    ): String = traceTool("backupFile", "file_path" to file_path, "backupSuffix" to backupSuffix) {
        editorTools.backupFile(file_path, backupSuffix)
    }

    // ================================================================
    //  编程工具 — 运行脚本、代码格式化、数据处理等
    // ================================================================

    @Tool(description = "执行 Python 脚本代码。将代码写入临时文件并执行，返回输出结果。")
    fun runPythonScript(
        @ToolParam(description = "Python 脚本代码内容") scriptContent: String,
        @ToolParam(description = "工作目录，相对项目根目录") workingDir: String = "",
    ): String = traceTool("runPythonScript", "scriptContent" to scriptContent, "workingDir" to workingDir) {
        programmingTools.runPythonScript(scriptContent, workingDir)
    }

    @Tool(description = "执行 JavaScript 代码。需要系统安装 Node.js。")
    fun runJavaScript(
        @ToolParam(description = "JavaScript 代码内容") code: String,
        @ToolParam(description = "工作目录，相对项目根目录") workingDir: String = "",
    ): String = traceTool("runJavaScript", "code" to code, "workingDir" to workingDir) {
        programmingTools.runJavaScript(code, workingDir)
    }

    @Tool(description = "执行 Shell 脚本代码。写入临时文件并执行。")
    fun runShellScript(
        @ToolParam(description = "Shell 脚本代码内容") scriptContent: String,
        @ToolParam(description = "工作目录，相对项目根目录") workingDir: String = "",
    ): String = traceTool("runShellScript", "scriptContent" to scriptContent, "workingDir" to workingDir) {
        programmingTools.runShellScript(scriptContent, workingDir)
    }

    @Tool(description = "格式化代码。支持 Kotlin、Java、Python、JavaScript、JSON。")
    fun formatCode(
        @ToolParam(description = "要格式化的代码") code: String,
        @ToolParam(description = "语言：kotlin/java/python/javascript/json，默认kotlin") language: String = "kotlin",
    ): String = traceTool("formatCode", "code" to code, "language" to language) {
        programmingTools.formatCode(code, language)
    }

    @Tool(description = "验证 JSON 字符串是否有效。")
    fun validateJson(
        @ToolParam(description = "JSON 字符串") jsonString: String,
    ): String = traceTool("validateJson", "jsonString" to jsonString) {
        programmingTools.validateJson(jsonString)
    }

    @Tool(description = "验证 XML 字符串是否有效。")
    fun validateXml(
        @ToolParam(description = "XML 字符串") xmlString: String,
    ): String = traceTool("validateXml", "xmlString" to xmlString) {
        programmingTools.validateXml(xmlString)
    }

    @Tool(description = "生成一个新的 UUID。")
    fun generateUUID(): String = traceTool("generateUUID") {
        programmingTools.generateUUID()
    }

    @Tool(description = "计算文件的校验和。支持 MD5、SHA-1、SHA-256 等算法。")
    fun calculateChecksum(
        @ToolParam(description = "文件路径，相对项目根目录") path: String,
        @ToolParam(description = "算法名称，如MD5、SHA-1、SHA-256，默认MD5") algorithm: String = "MD5",
    ): String = traceTool("calculateChecksum", "path" to path, "algorithm" to algorithm) {
        programmingTools.calculateChecksum(path, algorithm)
    }

    @Tool(description = "将字符串编码为 Base64。")
    fun encodeBase64(
        @ToolParam(description = "要编码的字符串") input: String,
    ): String = traceTool("encodeBase64", "input" to input) {
        programmingTools.encodeBase64(input)
    }

    @Tool(description = "将 Base64 字符串解码为原始字符串。")
    fun decodeBase64(
        @ToolParam(description = "Base64 编码的字符串") encoded: String,
    ): String = traceTool("decodeBase64", "encoded" to encoded) {
        programmingTools.decodeBase64(encoded)
    }

    // ================================================================
    //  项目工具 — 项目初始化、依赖管理、构建配置等
    // ================================================================

    @Tool(description = "初始化新的 Gradle 项目。支持 Android、Kotlin、Java 模板。")
    fun initGradleProject(
        @ToolParam(description = "项目名称") projectName: String,
        @ToolParam(description = "项目模板：android/kotlin/java/gradle，默认android") template: String = "android",
    ): String = traceTool("initGradleProject", "projectName" to projectName, "template" to template) {
        projectTools.initGradleProject(projectName, template)
    }

    @Tool(description = "初始化 Git 仓库。在指定目录创建新的 Git 仓库。")
    fun initGitRepository(
        @ToolParam(description = "工作目录，相对项目根目录，留空使用项目根目录") workingDir: String = "",
    ): String = traceTool("initGitRepository", "workingDir" to workingDir) {
        projectTools.initGitRepository(workingDir)
    }

    @Tool(description = "创建 .gitignore 文件。根据项目类型生成合适的忽略规则。")
    fun createGitIgnore(
        @ToolParam(description = "项目类型：android/kotlin/java/flutter/node，默认android") projectType: String = "android",
        @ToolParam(description = "目标路径，留空在项目根目录创建") targetPath: String = "",
    ): String = traceTool("createGitIgnore", "projectType" to projectType, "targetPath" to targetPath) {
        projectTools.createGitIgnore(projectType, targetPath)
    }

    @Tool(description = "分析项目结构，识别项目类型、构建文件和源码目录。")
    fun analyzeProjectStructure(): String = traceTool("analyzeProjectStructure") {
        projectTools.analyzeProjectStructure()
    }

    @Tool(description = "获取项目构建配置信息，包括版本号、SDK 版本等。")
    fun getBuildInfo(): String = traceTool("getBuildInfo") {
        projectTools.getBuildInfo()
    }

    @Tool(description = "列出项目依赖。使用 Gradle 命令获取依赖树。")
    fun listDependencies(
        @ToolParam(description = "工作目录，相对项目根目录") workingDir: String = "",
    ): String = traceTool("listDependencies", "workingDir" to workingDir) {
        projectTools.listDependencies(workingDir)
    }

    // ================================================================
    //  系统工具 — 设备信息、存储、网络等
    // ================================================================

    @Tool(description = "获取设备信息，包括制造商、型号、Android 版本等。")
    fun getDeviceInfo(): String = traceTool("getDeviceInfo") {
        systemTools.getDeviceInfo()
    }

    @Tool(description = "获取存储信息，包括总空间、可用空间、已用空间等。")
    fun getStorageInfo(): String = traceTool("getStorageInfo") {
        systemTools.getStorageInfo()
    }

    @Tool(description = "获取内存信息，包括最大内存、已用内存等。")
    fun getMemoryInfo(): String = traceTool("getMemoryInfo") {
        systemTools.getMemoryInfo()
    }

    @Tool(description = "获取 CPU 信息。读取 /proc/cpuinfo 文件内容。")
    fun getCpuInfo(): String = traceTool("getCpuInfo") {
        systemTools.getCpuInfo()
    }

    @Tool(description = "获取网络信息，包括本地 IP 地址和 DNS 服务器。")
    fun getNetworkInfo(): String = traceTool("getNetworkInfo") {
        systemTools.getNetworkInfo()
    }

    @Tool(description = "获取当前系统时间。")
    fun getCurrentTime(): String = traceTool("getCurrentTime") {
        systemTools.getCurrentTime()
    }

    @Tool(description = "获取应用版本信息。")
    fun getAppVersion(): String = traceTool("getAppVersion") {
        systemTools.getAppVersion()
    }

    @Tool(description = "检查网络连接状态。通过 ping 判断。")
    fun checkInternetConnection(): String = traceTool("checkInternetConnection") {
        systemTools.checkInternetConnection()
    }

    /**
     * 列出 `buildOpenAIToolsJson` 中声明的所有工具名称（用于系统提示注入）
     */
    fun toolNames(): List<String> = buildList {
        addAll(ReadTools.toolNames())
        addAll(EditTools.toolNames())
        addAll(WebTools.toolNames())
        addAll(TerminalTools.toolNames())
        addAll(EditorTools.toolNames())
        addAll(ProgrammingTools.toolNames())
        addAll(ProjectTools.toolNames())
        addAll(SystemTools.toolNames())
    }
}
