package com.medeide.jh.screens.home.cloudchat.aitools

import org.json.JSONArray

class AIToolSet(
    private val projectRootCallback: () -> String,
    private val isReadOnlyCallback: () -> Boolean = { false },
) {
    val readTools = ReadTools(projectRootCallback)
    val editTools = EditTools(projectRootCallback, isReadOnlyCallback)

    // ── 委托给 ReadTools ──

    fun listFiles(path: String = "", ignore: String = "") = readTools.listFiles(path, ignore)
    fun readFile(file_path: String, offset: Int = 0, limit: Int = 1000) = readTools.readFile(file_path, offset, limit)
    fun grep(pattern: String, path: String = "", glob: String = "", `-i`: Boolean = true, head_limit: Int = 100, `-C`: Int = 2) =
        readTools.grep(pattern, path, glob, `-i`, head_limit, `-C`)
    fun glob(pattern: String, path: String = "", maxResults: Int = 100) = readTools.glob(pattern, path, maxResults)

    // ── 委托给 EditTools ──

    fun writeFile(file_path: String, content: String, overwrite: Boolean = false) = editTools.writeFile(file_path, content, overwrite)
    fun replaceInFile(file_path: String, old_str: String, new_str: String) = editTools.replaceInFile(file_path, old_str, new_str)
    fun batchReplaceInFile(path: String, edits: String) = editTools.batchReplaceInFile(path, edits)
    fun deleteFile(file_paths: String) = editTools.deleteFile(file_paths)
    fun createDirectory(path: String) = editTools.createDirectory(path)
    fun moveFile(source: String, destination: String) = editTools.moveFile(source, destination)
    fun copyFile(source: String, destination: String) = editTools.copyFile(source, destination)

    companion object {
        /** 构建 OpenAI 兼容的 tools 定义 JSON */
        fun buildOpenAIToolsJson(): String {
            val tools = JSONArray()
            val readJson = ReadTools.buildOpenAIToolsJson()
            for (i in 0 until readJson.length()) tools.put(readJson.get(i))
            val editJson = EditTools.buildOpenAIToolsJson()
            for (i in 0 until editJson.length()) tools.put(editJson.get(i))
            val webJson = WebTools.buildOpenAIToolsJson()
            for (i in 0 until webJson.length()) tools.put(webJson.get(i))
            val diagJson = DiagnosticsTool.buildOpenAIToolsJson()
            for (i in 0 until diagJson.length()) tools.put(diagJson.get(i))
            return tools.toString()
        }
    }
}
