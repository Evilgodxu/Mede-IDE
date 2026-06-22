package com.medeide.jh.screens.home.landscape.editor.bookmarks

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 书签数据
 */
data class Bookmark(
    val id: String,
    val filePath: String,
    val line: Int,
    val column: Int,
    val label: String?,
    val createdAt: Long,
)

/**
 * 书签管理器
 */
class BookmarkManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        "bookmarks",
        Context.MODE_PRIVATE
    )

    private val _bookmarks = MutableStateFlow<List<Bookmark>>(emptyList())
    val bookmarks: StateFlow<List<Bookmark>> = _bookmarks.asStateFlow()

    init {
        loadBookmarks()
    }

    /**
     * 添加书签
     */
    fun addBookmark(filePath: String, line: Int, column: Int = 1, label: String? = null): Bookmark {
        // 检查是否已存在相同位置的书签
        val existing = _bookmarks.value.find {
            it.filePath == filePath && it.line == line && it.column == column
        }
        if (existing != null) return existing

        val bookmark = Bookmark(
            id = "bookmark_${System.currentTimeMillis()}_${line}_$column",
            filePath = filePath,
            line = line,
            column = column,
            label = label,
            createdAt = System.currentTimeMillis(),
        )

        val currentList = _bookmarks.value.toMutableList()
        currentList.add(bookmark)
        _bookmarks.value = currentList
        saveBookmarks(currentList)
        return bookmark
    }

    /**
     * 移除书签
     */
    fun removeBookmark(bookmarkId: String) {
        val currentList = _bookmarks.value.toMutableList()
        currentList.removeAll { it.id == bookmarkId }
        _bookmarks.value = currentList
        saveBookmarks(currentList)
    }

    /**
     * 移除指定文件的书签
     */
    fun removeBookmarksForFile(filePath: String) {
        val currentList = _bookmarks.value.toMutableList()
        currentList.removeAll { it.filePath == filePath }
        _bookmarks.value = currentList
        saveBookmarks(currentList)
    }

    /**
     * 获取文件的所有书签
     */
    fun getBookmarksForFile(filePath: String): List<Bookmark> =
        _bookmarks.value.filter { it.filePath == filePath }.sortedBy { it.line }

    /**
     * 检查指定位置是否有书签
     */
    fun hasBookmarkAt(filePath: String, line: Int): Boolean =
        _bookmarks.value.any { it.filePath == filePath && it.line == line }

    /**
     * 获取书签数量
     */
    fun getBookmarkCount(): Int = _bookmarks.value.size

    /**
     * 清空所有书签
     */
    fun clearAll() {
        _bookmarks.value = emptyList()
        prefs.edit().clear().apply()
    }

    private fun loadBookmarks() {
        val json = prefs.getString("bookmarks_json", null) ?: return
        try {
            val bookmarks = json.split("|||").mapNotNull { entry ->
                val parts = entry.split(":::")
                if (parts.size >= 5) {
                    Bookmark(
                        id = parts[0],
                        filePath = parts[1],
                        line = parts[2].toInt(),
                        column = parts[3].toInt(),
                        label = parts[4].takeIf { it != "null" },
                        createdAt = parts.getOrNull(5)?.toLongOrNull() ?: System.currentTimeMillis(),
                    )
                } else null
            }
            _bookmarks.value = bookmarks
        } catch (_: Exception) {
            _bookmarks.value = emptyList()
        }
    }

    private fun saveBookmarks(bookmarks: List<Bookmark>) {
        val json = bookmarks.joinToString("|||") { b ->
            "${b.id}:::${b.filePath}:::${b.line}:::${b.column}:::${b.label ?: "null"}:::${b.createdAt}"
        }
        prefs.edit().putString("bookmarks_json", json).apply()
    }
}
