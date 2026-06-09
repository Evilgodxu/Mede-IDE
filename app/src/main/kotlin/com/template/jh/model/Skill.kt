package com.template.jh.model

import java.util.UUID

// 自定义 AI 技能（用户手动导入/创建）
data class SkillItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val description: String = "",
    val prompt: String = "",  // 技能的系统提示词
    val enabled: Boolean = true,
    // 扩展字段：详细说明文档
    val documentation: String = "",  // 完整的使用说明文档（Markdown格式）
    val usage: String = "",          // 使用方法说明
    val parameters: List<SkillParameter> = emptyList(),  // 参数配置说明
    val examples: List<String> = emptyList(),  // 使用示例
    val author: String = "",         // 作者信息
    val version: String = "",        // 版本号
    val tags: List<String> = emptyList(),  // 标签分类
)

// 技能参数定义
data class SkillParameter(
    val name: String,
    val description: String,
    val type: String = "string",     // string, number, boolean, enum
    val required: Boolean = false,
    val defaultValue: String? = null,
    val options: List<String> = emptyList(),  // 枚举类型的选项
)
