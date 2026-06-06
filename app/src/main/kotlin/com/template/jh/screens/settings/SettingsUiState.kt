package com.template.jh.screens.settings

// 设置页面 UI 状态
data class SettingsUiState(
    val isLoading: Boolean = true,
    val themeMode: String = "system",
    val language: String = "system",
)
