package com.template.jh.screens.home.components.resourcepanel

import android.net.Uri

// 扁平目录树节点 - 携带相对路径用于 FileManager 操作
data class ResourceNode(
    val uri: Uri,
    val name: String,
    val relativePath: String,  // 相对于项目根目录的路径
    val isDirectory: Boolean,
    val depth: Int,
)

data class FileItemNode(
    val name: String,
    val uri: Uri,
    val relativePath: String,
    val isDirectory: Boolean,
)
