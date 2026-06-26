package com.medeide.jh.model

import java.util.UUID

const val DEFAULT_ROLE_ID = "__default_role__"

data class Rule(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val content: String = "",
    val isDefault: Boolean = false,
) {
    companion object {
        fun defaultRole() = Rule(
            id = DEFAULT_ROLE_ID,
            name = "默认角色",
            content = "你是智能编程助手，使用内置工具协助用户完成文件操作、代码编辑、项目构建等开发任务。",
            isDefault = true,
        )
    }
}
