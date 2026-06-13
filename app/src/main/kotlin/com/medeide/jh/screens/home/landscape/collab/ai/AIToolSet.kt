package com.medeide.jh.screens.home.landscape.collab.ai

import android.content.Context
import android.net.Uri
import com.google.ai.edge.litertlm.Tool
import com.google.ai.edge.litertlm.ToolParam
import com.google.ai.edge.litertlm.ToolSet
import com.medeide.jh.screens.home.landscape.collab.memory.ConversationMemory
import com.medeide.jh.data.storage.FileManager
import com.medeide.jh.screens.home.landscape.collab.ai.tools.*

/** 工具执行状态回调 — 由 ChatViewModel 注入，打破 automaticToolCalling 黑盒 */
interface ToolExecutionCallback {
    fun onToolStart(name: String, args: Map<String, String>)
    fun onToolResult(name: String, args: Map<String, String>, result: String)
}

class AIToolSet(
    private val context: Context,
    private val fileManager: FileManager? = null,
    private val conversationMemory: ConversationMemory? = null,
) : ToolSet {

    private val readTools = ReadTools(fileManager, conversationMemory)
    private val editTools = EditTools(fileManager)
    private val terminalTools = TerminalTools(context, fileManager) { projectUri }
    private val webTools = WebTools(fileManager)

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

    // SAF 项目根 URI（用户通过长按文件夹下拉列表设置的 workspace）
    @Volatile var projectUri: Uri? = null
        set(value) {
            field = value
            value?.let { fileManager?.setProjectUri(it) }
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
            val terminalJson = TerminalTools.buildOpenAIToolsJson()
            for (i in 0 until terminalJson.length()) tools.put(terminalJson.get(i))
            val webJson = WebTools.buildOpenAIToolsJson()
            for (i in 0 until webJson.length()) tools.put(webJson.get(i))
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
    //  终端工具 — 对齐 RunCommand / GetDiagnostics
    // ================================================================

    @Tool(description = "执行shell命令，在当前项目目录下运行。超时30秒，输出上限5000字符。")
    fun runCommand(
        @ToolParam(description = "要执行的命令，如'ls -la'或'./gradlew build'") command: String,
        @ToolParam(description = "目标终端标识（Android环境仅一个终端），可选") target_terminal: String = "",
        @ToolParam(description = "命令类型：web_server/long_running_process/short_running_process/other，默认other") command_type: String = "other",
        @ToolParam(description = "工作目录路径，留空使用项目根目录") cwd: String = "",
        @ToolParam(description = "是否阻塞等待完成，默认true") blocking: Boolean = true,
        @ToolParam(description = "是否需要用户批准执行，默认false") requires_approval: Boolean = false,
        @ToolParam(description = "异步模式的初始等待毫秒数，默认0") wait_ms_before_async: Int = 0,
    ): String = terminalTools.runCommand(command, target_terminal, command_type, cwd, blocking, requires_approval, wait_ms_before_async)

    @Tool(description = "读取构建/lint/编译错误诊断，返回文件路径和行号。无缓存时自动运行gradle lint。可选按uri过滤。")
    fun getDiagnostics(
        @ToolParam(description = "可选的文件路径过滤，仅返回该文件的诊断结果") uri: String = "",
    ): String = terminalTools.getDiagnostics(uri)

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

    /**
     * 列出 `buildOpenAIToolsJson` 中声明的所有工具名称（用于系统提示注入）
     */
    fun toolNames(): List<String> = buildList {
        addAll(ReadTools.toolNames())
        addAll(EditTools.toolNames())
        addAll(TerminalTools.toolNames())
        addAll(WebTools.toolNames())
    }
}
