package com.medeide.jh.screens.home.model

import com.medeide.jh.data.storage.FileManager

/** 搜索结果项 */
data class SearchResultItem(
    val filePath: String,
    val lineNumber: Int,
    val matchText: String,
    val contextLines: List<FileManager.MatchLine>,
)
