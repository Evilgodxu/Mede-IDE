package com.medeide.jh.screens.home.landscape.sidebar.resourcepanel

import android.net.Uri

// 扁平目录树节点 - 携带相对路径用于 FileManager 操作
data class ResourceNode(
    val uri: Uri,
    val name: String,
    val relativePath: String,  // 相对于项目根目录的路径
    val isDirectory: Boolean,
    val depth: Int,
    val filePath: String = "", // 绝对路径（直接文件系统模式）
)

data class FileItemNode(
    val name: String,
    val uri: Uri,
    val relativePath: String,
    val isDirectory: Boolean,
    val filePath: String = "",
)
