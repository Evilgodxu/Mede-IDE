package com.template.jh.screens.home

import com.template.jh.data.model.McpServer
import com.template.jh.data.model.NotificationSettings
import com.template.jh.data.model.Rule
import com.template.jh.data.model.SkillItem

// 主屏幕 UI 状态
data class HomeUiState(
    val isLoading: Boolean = true,
    val themeMode: String = "system",
    val language: String = "system",
    val openedFolderName: String? = null,
    val openedFolderUri: String? = null,
    val rules: List<Rule> = emptyList(),
    val skills: List<SkillItem> = emptyList(),
    val mcpServers: List<McpServer> = emptyList(),
    val notificationSettings: NotificationSettings = NotificationSettings(),
)
