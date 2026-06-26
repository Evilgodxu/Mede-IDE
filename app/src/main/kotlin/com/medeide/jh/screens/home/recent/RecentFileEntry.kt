package com.medeide.jh.screens.home.recent

import kotlinx.serialization.Serializable

@Serializable
data class RecentFileEntry(
    val path: String,
    val name: String,
    val isDirectory: Boolean = true,
    val lastOpenedTime: Long = System.currentTimeMillis()
)
