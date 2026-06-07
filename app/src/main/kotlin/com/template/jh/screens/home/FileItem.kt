package com.template.jh.screens.home

import android.net.Uri

// 文件树节点
data class FileItem(
    val name: String,
    val uri: Uri,
    val isDirectory: Boolean,
    val size: Long = 0,
    val lastModified: Long = 0,
)
