package com.medeide.jh.screens.home

import com.medeide.jh.model.DEFAULT_ROLE_ID
import com.medeide.jh.model.McpServer
import com.medeide.jh.model.Rule

// 主屏幕 UI 状态
data class HomeUiState(
    val isLoading: Boolean = true,
    val themeMode: String = "system",
    val language: String = "system",
    val openedFolderName: String? = null,
    val openedFolderUri: String? = null,
    val rules: List<Rule> = emptyList(),
    val activeRoleId: String = DEFAULT_ROLE_ID,
    val mcpServers: List<McpServer> = emptyList(),
    /** 存储根路径（完整文件系统根） */
    val storageRootPath: String = "",
    /** 存储根目录显示名称 */
    val storageRootName: String = "存储根目录",
    /** 当前项目目录的显示名 */
    val projectDirName: String? = null,
    /** 当前项目目录的绝对路径 */
    val projectDirPath: String = "",
)
