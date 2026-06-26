package com.medeide.jh.screens.home.filebrowser.logic

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import com.medeide.jh.core.data.logging.FileLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

fun createItem(path: String, name: String, isDirectory: Boolean): Result<Unit> = runCatching {
    val target = File(path, name)
    if (target.exists()) throw IllegalStateException("已存在同名文件")
    if (isDirectory) target.mkdirs() else target.createNewFile()
    FileLogger.i("FileOps", "create ${if (isDirectory) "dir" else "file"} $path/$name")
}

fun renameItem(file: File, newName: String): Result<Unit> = runCatching {
    val target = File(file.parentFile, newName)
    if (target.exists() && target.absolutePath != file.absolutePath) {
        throw IllegalStateException("名称已存在")
    }
    file.renameTo(target)
    FileLogger.i("FileOps", "rename ${file.name} → $newName")
}

fun deleteItems(files: List<File>): Result<Unit> = runCatching {
    files.forEach { it.deleteRecursively() }
    FileLogger.i("FileOps", "delete ${files.size} items")
}

fun copyToClipboard(context: Context, label: String, text: String) {
    try {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
    } catch (_: Exception) {}
}

fun compressItems(parentPath: String, files: List<File>): Result<String> = runCatching {
    val baseName = if (files.size == 1) files.first().nameWithoutExtension else "压缩"
    var target = File(parentPath, "$baseName.zip")
    var i = 1
    while (target.exists()) {
        target = File(parentPath, "$baseName ($i).zip")
        i++
    }
    ZipOutputStream(target.outputStream()).use { zos ->
        files.forEach { addFileToZip(it, it.name, zos) }
    }
    FileLogger.i("FileOps", "compress ${files.size} items → ${target.name}")
    target.name
}

fun extractItem(parentPath: String, file: File): Result<Unit> = runCatching {
    val dest = File(parentPath, file.nameWithoutExtension)
    var dir = dest
    var i = 1
    while (dir.exists()) {
        dir = File(parentPath, "${file.nameWithoutExtension} ($i)")
        i++
    }
    dir.mkdirs()
    ZipInputStream(file.inputStream()).use { zis ->
        var entry = zis.nextEntry
        while (entry != null) {
            val outFile = File(dir, entry.name)
            if (entry.isDirectory) {
                outFile.mkdirs()
            } else {
                outFile.parentFile?.mkdirs()
                outFile.outputStream().use { zis.copyTo(it) }
            }
            entry = zis.nextEntry
        }
    }
    FileLogger.i("FileOps", "extract ${file.name} → ${dir.name}")
}

private fun addFileToZip(file: File, entryName: String, zos: ZipOutputStream) {
    if (file.isDirectory) {
        file.listFiles()?.forEach { addFileToZip(it, "$entryName/${it.name}", zos) }
    } else {
        zos.putNextEntry(ZipEntry(entryName))
        file.inputStream().use { it.copyTo(zos) }
        zos.closeEntry()
    }
}

fun formatFileSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> "${"%.1f".format(bytes.toDouble() / (1024 * 1024))} MB"
}

fun loadFiles(path: String): List<FileEntry> {
    return try {
        File(path).listFiles()
            ?.filter { !it.name.startsWith('.') }
            ?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            ?.map { FileEntry.Real(it) }
            ?: emptyList()
    } catch (_: Exception) {
        emptyList()
    }
}

fun computeDisplayPath(currentPath: String, rootPath: String): String = when {
    currentPath == rootPath -> "/"
    currentPath.startsWith(rootPath) -> currentPath.removePrefix(rootPath)
    else -> currentPath
}

// ── 压缩/解压冲突处理 ──

/** 文件冲突处理模式 */
enum class ConflictMode { Rename, Overwrite }

/** 自动解决冲突：若文件存在则加 (N) 后缀或覆盖 */
private fun resolveDestFile(destDir: File, name: String, mode: ConflictMode): File {
    val target = File(destDir, name)
    if (!target.exists()) return target
    if (mode == ConflictMode.Overwrite) {
        target.delete()
        return target
    }
    val base = name.substringBeforeLast('.')
    val ext = name.substringAfterLast('.', "")
    var i = 1
    while (true) {
        val newName = if (ext.isEmpty()) "$base ($i)" else "$base ($i).$ext"
        val candidate = File(destDir, newName)
        if (!candidate.exists()) return candidate
        i++
    }
}

// ── 异步压缩/解压（协程 + 进度回调） ──

/**
 * 异步解压整个压缩包到目标目录
 * @return 成功时返回解压的文件/目录数量
 */
suspend fun extractToAsync(
    targetDir: String,
    file: File,
    conflictMode: ConflictMode = ConflictMode.Rename,
    onProgress: (current: Int, total: Int) -> Unit = { _, _ -> },
): Result<Int> = withContext(Dispatchers.IO) {
    FileLogger.i("FileOps", "extractToAsync ${file.name} → $targetDir")
    runCatching {
        val destDir = File(targetDir)
        destDir.mkdirs()

        // 先读所有条目获取总数
        val entries = mutableListOf<ZipEntry>()
        ZipInputStream(file.inputStream()).use { zis ->
            var e = zis.nextEntry
            while (e != null) { entries.add(e); e = zis.nextEntry }
        }

        val total = entries.size
        var current = 0

        // 第二次遍历执行解压
        ZipInputStream(file.inputStream()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                current++
                val outFile = resolveDestFile(destDir, entry.name, conflictMode).apply {
                    if (entry.isDirectory) mkdirs() else parentFile?.mkdirs()
                }
                if (!entry.isDirectory) {
                    outFile.outputStream().use { zis.copyTo(it) }
                }
                onProgress(current, total)
                entry = zis.nextEntry
            }
        }

        current
    }
}

/**
 * 异步解压压缩包内指定条目到目标目录
 * @return 成功时返回解压的文件/目录数量
 */
suspend fun extractArchiveEntryToAsync(
    archiveFile: File,
    entryPath: String,
    destDir: String,
    conflictMode: ConflictMode = ConflictMode.Rename,
    onProgress: (current: Int, total: Int) -> Unit = { _, _ -> },
): Result<Int> = withContext(Dispatchers.IO) {
    runCatching {
        val dest = File(destDir)
        dest.mkdirs()
        var processed = 0

        ZipFile(archiveFile).use { zip ->
            val entry = zip.getEntry(entryPath)
                ?: throw IllegalStateException("压缩包中未找到条目: $entryPath")

            if (entry.isDirectory) {
                val allEntries = zip.entries().asSequence().toList()
                val prefix = entryPath.trimEnd('/') + "/"
                val subEntries = allEntries.filter { it.name.startsWith(prefix) }
                val total = subEntries.size

                for (e in subEntries) {
                    processed++
                    val relativeName = e.name.removePrefix(prefix)
                    val outFile = resolveDestFile(dest, relativeName, conflictMode).apply {
                        if (e.isDirectory) mkdirs() else parentFile?.mkdirs()
                    }
                    if (!e.isDirectory) {
                        zip.getInputStream(e).use { it.copyTo(outFile.outputStream()) }
                    }
                    onProgress(processed, total)
                }
            } else {
                processed = 1
                val fileName = entry.name.substringAfterLast('/')
                val outFile = resolveDestFile(dest, fileName, conflictMode).apply {
                    parentFile?.mkdirs()
                }
                zip.getInputStream(entry).use { it.copyTo(outFile.outputStream()) }
                onProgress(1, 1)
            }
        }

        processed
    }
}

/**
 * 异步压缩文件/目录到 zip
 * @return 成功时返回生成的 zip 文件名
 */
suspend fun compressItemsAsync(
    parentPath: String,
    files: List<File>,
    onProgress: (current: Int, total: Int) -> Unit = { _, _ -> },
): Result<String> = withContext(Dispatchers.IO) {
    runCatching {
        val baseName = if (files.size == 1) files.first().nameWithoutExtension else "压缩"
        var target = File(parentPath, "$baseName.zip")
        var i = 1
        while (target.exists()) {
            target = File(parentPath, "$baseName ($i).zip")
            i++
        }

        val allItems = mutableListOf<Pair<File, String>>()
        files.forEach { collectItems(it, it.name, allItems) }
        val total = allItems.size

        ZipOutputStream(target.outputStream()).use { zos ->
            allItems.forEachIndexed { idx, (f, name) ->
                zos.putNextEntry(ZipEntry(name))
                f.inputStream().use { it.copyTo(zos) }
                zos.closeEntry()
                onProgress(idx + 1, total)
            }
        }

        target.name
    }
}

private fun collectItems(file: File, entryName: String, result: MutableList<Pair<File, String>>) {
    if (file.isDirectory) {
        file.listFiles()?.forEach { collectItems(it, "$entryName/${it.name}", result) }
    } else {
        result.add(file to entryName)
    }
}

// ── 压缩包内浏览支持 ──

/** 压缩包虚拟条目的路径分隔标记 */
internal const val ARCHIVE_PATH_SEPARATOR = "::archive::"

fun isArchiveEntryPath(path: String): Boolean = path.contains(ARCHIVE_PATH_SEPARATOR)

/** 构建压缩包内条目的虚拟路径（包含完整路径） */
fun buildArchiveEntryPath(archivePath: String, innerPath: String, displayName: String): String {
    val fullEntryPath = if (innerPath.isEmpty()) displayName else "$innerPath/$displayName"
    return "$archivePath$ARCHIVE_PATH_SEPARATOR$fullEntryPath"
}

/** 从虚拟路径解析出 (归档文件路径, 压缩包内完整路径) */
fun parseArchiveEntryPath(virtualPath: String): Pair<String, String>? {
    val idx = virtualPath.indexOf(ARCHIVE_PATH_SEPARATOR)
    if (idx < 0) return null
    val archiveFilePath = virtualPath.substring(0, idx)
    val entryPath = virtualPath.substring(idx + ARCHIVE_PATH_SEPARATOR.length)
    return Pair(archiveFilePath, entryPath)
}

/** 列出压缩包内指定路径下的直接子条目 */
fun listArchiveEntries(archiveFile: File, innerPath: String): List<FileEntry> {
    val result = mutableListOf<FileEntry>()
    try {
        ZipFile(archiveFile).use { zip ->
            val entries = zip.entries().asSequence().toList()

            val prefix = if (innerPath.isEmpty()) "" else "$innerPath/"
            val seen = mutableSetOf<String>()

            for (entry in entries) {
                val entryName = entry.name
                if (!entryName.startsWith(prefix)) continue
                val relativeName = entryName.removePrefix(prefix)
                if (relativeName.isEmpty()) continue

                val topName = relativeName.split("/").first()
                if (topName in seen) continue
                seen.add(topName)

                val isDir = entry.isDirectory || relativeName.endsWith("/") || relativeName.contains("/")
                val displayName = topName.trimEnd('/')
                val fullEntryPath = if (innerPath.isEmpty()) displayName else "$innerPath/$displayName"
                val virtualPath = buildArchiveEntryPath(archiveFile.absolutePath, innerPath, displayName)
                result.add(FileEntry.Archive(displayName, virtualPath, isDir, archiveFile.absolutePath, fullEntryPath))
            }
        }
    } catch (_: Exception) {}
    return result.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
}

/** 将整个压缩包解压到指定目标目录（同步版本，保留向后兼容） */
fun extractTo(targetDir: String, file: File): Result<Unit> = runCatching {
    val destDir = File(targetDir)
    destDir.mkdirs()
    ZipInputStream(file.inputStream()).use { zis ->
        var entry = zis.nextEntry
        while (entry != null) {
            val outFile = File(destDir, entry.name)
            if (entry.isDirectory) {
                outFile.mkdirs()
            } else {
                outFile.parentFile?.mkdirs()
                outFile.outputStream().use { zis.copyTo(it) }
            }
            entry = zis.nextEntry
        }
    }
}

/** 从压缩包中解压指定条目到目标目录（同步版本，保留向后兼容） */
fun extractArchiveEntryTo(archiveFile: File, entryPath: String, destDir: String): Result<Unit> = runCatching {
    val dest = File(destDir)
    dest.mkdirs()
    ZipFile(archiveFile).use { zip ->
        val entry = zip.getEntry(entryPath)
            ?: throw IllegalStateException("压缩包中未找到条目: $entryPath")
        if (entry.isDirectory) {
            val allEntries = zip.entries().asSequence().toList()
            val prefix = entryPath.trimEnd('/') + "/"
            for (e in allEntries) {
                if (!e.name.startsWith(prefix)) continue
                val outFile = File(dest, e.name.removePrefix(prefix))
                if (e.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    zip.getInputStream(e).use { it.copyTo(outFile.outputStream()) }
                }
            }
        } else {
            val fileName = entry.name.substringAfterLast('/')
            dest.parentFile?.mkdirs()
            zip.getInputStream(entry).use { input ->
                FileOutputStream(File(dest, fileName)).use { output ->
                    input.copyTo(output)
                }
            }
        }
    }
}
