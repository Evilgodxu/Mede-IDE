package com.template.jh.screens.home

import android.net.Uri
import java.io.File

// 文件树节点 - 同时携带 content:// URI、直接文件路径和相对路径
data class FileItem(
    val name: String,
    val uri: Uri,
    val isDirectory: Boolean,
    val relativePath: String = "",  // 相对于项目根目录的路径，如 "app/src/main.kt"
    val size: Long = 0,
    val lastModified: Long = 0,
    val filePath: String = "",      // 直接文件系统路径（MANAGE_EXTERNAL_STORAGE 模式下使用）
) {
    companion object {
        /** 从 java.io.File 创建 FileItem */
        fun fromFile(file: File, rootPath: String): FileItem = FileItem(
            name = file.name,
            uri = Uri.fromFile(file),
            isDirectory = file.isDirectory,
            relativePath = file.absolutePath.removePrefix(rootPath).trimStart('/'),
            size = if (file.isFile) file.length() else 0L,
            lastModified = file.lastModified(),
            filePath = file.absolutePath,
        )
    }
}
