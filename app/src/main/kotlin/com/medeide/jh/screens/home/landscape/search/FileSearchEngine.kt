package com.medeide.jh.screens.home.landscape.search

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 文件搜索结果
 */
data class SearchResult(
    val file: File,
    val line: Int,
    val column: Int,
    val content: String,
    val matchStart: Int,
    val matchEnd: Int,
)

/**
 * 全局文件搜索引擎
 */
object FileSearchEngine {

    /**
     * 搜索文件内容
     * @param rootPath 搜索根目录
     * @param query 搜索关键词
     * @param useRegex 是否使用正则表达式
     * @param caseSensitive 是否区分大小写
     * @param filePattern 文件名过滤模式（如 *.kt）
     */
    fun search(
        rootPath: String,
        query: String,
        useRegex: Boolean = false,
        caseSensitive: Boolean = false,
        filePattern: String? = null,
    ): Flow<SearchResult> = flow {
        val root = File(rootPath)
        if (!root.exists() || !root.isDirectory) return@flow

        val pattern = if (useRegex) {
            try {
                Regex(query, if (caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE))
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }

        val fileFilter: (File) -> Boolean = { file ->
            if (filePattern == null || filePattern == "*") {
                true
            } else {
                val name = file.name
                val patternParts = filePattern.split("*")
                if (patternParts.size == 1) {
                    name == filePattern
                } else {
                    val prefix = patternParts.firstOrNull()?.takeIf { it.isNotEmpty() }
                    val suffix = patternParts.lastOrNull()?.takeIf { it.isNotEmpty() }
                    (prefix == null || name.startsWith(prefix)) && (suffix == null || name.endsWith(suffix))
                }
            }
        }

        val files = root.walkTopDown()
            .filter { it.isFile }
            .filter(fileFilter)
            .toList()

        for (file in files) {
            try {
                val results = mutableListOf<SearchResult>()
                var lineIndex = 0
                file.forEachLine { line ->
                    val currentLine = lineIndex
                    lineIndex++

                    val matches = if (useRegex) {
                        pattern?.findAll(line)?.toList()
                    } else {
                        val searchQuery = if (caseSensitive) query else query.lowercase()
                        val searchContent = if (caseSensitive) line else line.lowercase()
                        val idx = searchContent.indexOf(searchQuery)
                        if (idx >= 0) listOf(idx..idx + query.length - 1) else emptyList()
                    }

                    matches?.forEach { match ->
                        val (start, end) = if (match is IntRange) {
                            match.first to match.last
                        } else if (match is MatchResult) {
                            match.range.first to match.range.last
                        } else {
                            0 to 0
                        }
                        results.add(SearchResult(
                            file = file,
                            line = currentLine + 1,
                            column = start + 1,
                            content = line,
                            matchStart = start,
                            matchEnd = end,
                        ))
                    }
                }
                for (result in results) {
                    emit(result)
                }
            } catch (_: Exception) {
                // 跳过无法读取的文件
            }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * 搜索文件（按文件名）
     */
    suspend fun searchFiles(
        rootPath: String,
        fileNamePattern: String,
        maxResults: Int = 100,
    ): List<File> = withContext(Dispatchers.IO) {
        val root = File(rootPath)
        if (!root.exists() || !root.isDirectory) return@withContext emptyList()

        val patternParts = fileNamePattern.split("*")
        root.walkTopDown()
            .filter { it.isFile }
            .filter { file ->
                val name = file.name
                if (patternParts.size == 1) {
                    name == fileNamePattern
                } else {
                    val prefix = patternParts.firstOrNull()?.takeIf { it.isNotEmpty() }
                    val suffix = patternParts.lastOrNull()?.takeIf { it.isNotEmpty() }
                    (prefix == null || name.startsWith(prefix)) && (suffix == null || name.endsWith(suffix))
                }
            }
            .take(maxResults)
            .toList()
    }

    /**
     * 统计搜索结果数量
     */
    suspend fun countResults(
        rootPath: String,
        query: String,
        useRegex: Boolean = false,
        caseSensitive: Boolean = false,
        filePattern: String? = null,
    ): Int = withContext(Dispatchers.IO) {
        var count = 0
        search(rootPath, query, useRegex, caseSensitive, filePattern).collect {
            count++
            if (count >= 1000) {
                return@collect
            }
        }
        count
    }
}
