package com.template.jh.model

import java.util.UUID

data class Rule(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val content: String = "",
    val type: RuleType = RuleType.Global,
)

enum class RuleType(val label: String) {
    Global("全局规则"),
    Project("项目规则"),
}
