package com.template.jh.screens.home

import android.net.Uri

// 统一的编辑器 Tab 项
data class TabItem(
    val id: String,         // 唯一标识：settings 或文件路径
    val title: String,      // 显示名称
    val type: TabType,
)

enum class TabType { File, Settings }

// 从路径提取显示名称
fun displayNameFromPath(path: String): String {
    return if (path.startsWith("content://")) {
        Uri.decode(path).substringAfterLast('/')
    } else {
        path.substringAfterLast('/')
    }.ifEmpty { path }
}
