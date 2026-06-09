package com.template.jh.screens.home

import com.template.jh.data.model.McpServer
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
    /** 存储根路径（完整文件系统根） */
    val storageRootPath: String = "",
    /** 当前项目目录的显示名 */
    val projectDirName: String? = null,
    /** 当前项目目录的绝对路径 */
    val projectDirPath: String = "",
)
