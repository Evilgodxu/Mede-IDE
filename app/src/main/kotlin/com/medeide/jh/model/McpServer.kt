package com.medeide.jh.model

import java.util.UUID

// MCP 服务器配置
data class McpServer(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val command: String = "",
    val args: String = "",
    val enabled: Boolean = false,
)
