package com.template.jh.model

import java.util.UUID

data class Rule(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val content: String = "",
)
