package com.template.jh.screens.home

// 主屏幕 UI 状态
data class HomeUiState(
    val isLoading: Boolean = true,
    val themeMode: String = "system",
    val language: String = "system",
    val openedFolderName: String? = null,
    val openedFolderUri: String? = null,
)
