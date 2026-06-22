package com.medeide.jh.screens.home.model

/** 按文件分组的搜索结果 */
data class FileSearchResult(
    val filePath: String,
    val matches: List<SearchResultItem>,
)
