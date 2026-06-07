package com.template.jh.data.model

import java.util.UUID

// 自定义 AI 技能（用户手动导入/创建）
data class SkillItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val description: String = "",
    val prompt: String = "",  // 技能的系统提示词
    val enabled: Boolean = true,
)

