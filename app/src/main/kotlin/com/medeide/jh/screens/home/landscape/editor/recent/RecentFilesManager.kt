package com.medeide.jh.screens.home.landscape.editor.recent

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * 最近打开的文件记录
 */
data class RecentFile(
    val path: String,
    val name: String,
    val lastOpened: Long,
    val size: Long,
)

/**
 * 最近文件管理器
 */
class RecentFilesManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        "recent_files",
        Context.MODE_PRIVATE
    )

    private val _recentFiles = MutableStateFlow<List<RecentFile>>(emptyList())
    val recentFiles: StateFlow<List<RecentFile>> = _recentFiles.asStateFlow()

    private val maxFiles = 50

    init {
        loadRecentFiles()
    }

    /**
     * 记录文件打开
     */
    fun recordFileOpen(file: File) {
        if (!file.exists()) return

        val path = file.absolutePath
        val currentList = _recentFiles.value.toMutableList()

        // 移除已存在的同名文件
        currentList.removeAll { it.path == path }

        // 添加到列表头部
        currentList.add(0, RecentFile(
            path = path,
            name = file.name,
            lastOpened = System.currentTimeMillis(),
            size = file.length(),
        ))

        // 限制最大数量
        val trimmed = currentList.take(maxFiles)
        _recentFiles.value = trimmed

        saveRecentFiles(trimmed)
    }

    /**
     * 移除文件记录
     */
    fun removeFile(path: String) {
        val currentList = _recentFiles.value.toMutableList()
        currentList.removeAll { it.path == path }
        _recentFiles.value = currentList
        saveRecentFiles(currentList)
    }

    /**
     * 清空所有记录
     */
    fun clearAll() {
        _recentFiles.value = emptyList()
        prefs.edit().clear().apply()
    }

    /**
     * 获取分组显示的最近文件
     */
    fun getGroupedFiles(): Map<String, List<RecentFile>> {
        val now = System.currentTimeMillis()
        val oneDay = 24 * 60 * 60 * 1000L

        return _recentFiles.value.groupBy { file ->
            val daysAgo = (now - file.lastOpened) / oneDay
            when {
                daysAgo == 0L -> "今天"
                daysAgo == 1L -> "昨天"
                daysAgo < 7L -> "$daysAgo 天前"
                daysAgo < 30L -> "${daysAgo / 7} 周前"
                else -> "更早"
            }
        }
    }

    private fun loadRecentFiles() {
        val json = prefs.getString("recent_files_json", null) ?: return
        try {
            val files = json.split("|||").mapNotNull { entry ->
                val parts = entry.split(":::")
                if (parts.size >= 4) {
                    RecentFile(
                        path = parts[0],
                        name = parts[1],
                        lastOpened = parts[2].toLong(),
                        size = parts[3].toLong(),
                    )
                } else null
            }
            _recentFiles.value = files
        } catch (_: Exception) {
            _recentFiles.value = emptyList()
        }
    }

    private fun saveRecentFiles(files: List<RecentFile>) {
        val json = files.joinToString("|||") { entry ->
            "${entry.path}:::${entry.name}:::${entry.lastOpened}:::${entry.size}"
        }
        prefs.edit().putString("recent_files_json", json).apply()
    }
}
