package com.medeide.jh.screens.home.landscape.sidebar.searchreplacepanel

// 单条搜索匹配结果
data class SearchResultItem(
    val filePath: String,
    val lineNumber: Int,
    val matchText: String,
    val contextLines: List<MatchLine> = emptyList(),
)

data class MatchLine(val text: String, val isMatch: Boolean)
