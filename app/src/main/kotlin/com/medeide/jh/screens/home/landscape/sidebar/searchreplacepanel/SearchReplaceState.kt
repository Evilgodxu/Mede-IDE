package com.medeide.jh.screens.home.landscape.sidebar.searchreplacepanel

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

// 搜索替换共享状态（侧边栏面板 + 编辑器工具栏）
class SearchReplaceState {
    var searchQuery by mutableStateOf("")
    var replaceText by mutableStateOf("")
    var isRegex by mutableStateOf(false)
    var isCaseSensitive by mutableStateOf(false)
    var isWholeWord by mutableStateOf(false)

    var persistentSearchResults by mutableStateOf<List<SearchResultItem>>(emptyList())
    var isSearching by mutableStateOf(false)

    var isToolbarVisible by mutableStateOf(false)
    var currentSearchMatches by mutableStateOf<List<SearchResultItem>>(emptyList())
    var currentSearchMatchIndex by mutableIntStateOf(-1)

    val changedFiles = mutableStateListOf<String>()

    fun clearAll() {
        searchQuery = ""
        replaceText = ""
        persistentSearchResults = emptyList()
        clearToolbar()
    }

    fun clearToolbar() {
        isToolbarVisible = false
        currentSearchMatches = emptyList()
        currentSearchMatchIndex = -1
    }

    fun buildPattern(): String {
        var pattern = if (isRegex) searchQuery else Regex.escape(searchQuery)
        if (isWholeWord) pattern = "\\b${pattern}\\b"
        return pattern
    }
}

@Composable
fun rememberSearchReplaceState(): SearchReplaceState = remember { SearchReplaceState() }
