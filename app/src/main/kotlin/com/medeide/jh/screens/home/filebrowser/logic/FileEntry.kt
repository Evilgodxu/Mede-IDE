package com.medeide.jh.screens.home.filebrowser.logic

import java.io.File

/**
 * 文件条目抽象：真实文件或压缩包内虚拟条目
 * 替代直接继承 java.io.File 的错误做法
 */
sealed class FileEntry {
    abstract val name: String
    abstract val absolutePath: String
    abstract val isDirectory: Boolean

    /** 真实文件系统条目 */
    data class Real(val file: File) : FileEntry() {
        override val name: String get() = file.name
        override val absolutePath: String get() = file.absolutePath
        override val isDirectory: Boolean get() = file.isDirectory
    }

    /** 压缩包内虚拟条目 */
    data class Archive(
        override val name: String,
        override val absolutePath: String,
        override val isDirectory: Boolean,
        val realArchivePath: String,
        val entryFullPath: String,
    ) : FileEntry()
}

/** 获取真实 File（仅 Real 类型有效） */
fun FileEntry.toFile(): File? = (this as? FileEntry.Real)?.file

/** 尝试将 FileEntry 列表转为 File 列表（跳过 Archive 条目） */
fun List<FileEntry>.toRealFiles(): List<File> = mapNotNull { it.toFile() }
