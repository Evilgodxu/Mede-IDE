package com.template.jh.screens.home

import android.net.Uri

// 文件树节点 - 同时携带 content:// URI 和相对路径
data class FileItem(
    val name: String,
    val uri: Uri,
    val isDirectory: Boolean,
    val relativePath: String = "",  // 相对于项目根目录的路径，如 "app/src/main.kt"
    val size: Long = 0,
    val lastModified: Long = 0,
)
