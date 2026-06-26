package com.medeide.jh.screens.home.landscape.workspace.model

private val imageExts = setOf("png", "jpg", "jpeg", "gif", "bmp", "webp", "svg")
private val audioExts = setOf("mp3", "wav", "flac", "ogg", "aac", "m4a", "wma")
private val videoExts = setOf("mp4", "avi", "mkv", "mov", "wmv", "flv", "webm")
private val archiveExts = setOf("zip", "rar", "7z", "tar", "gz", "bz2")
private val webExts = setOf("html", "htm", "xhtml")

// 代码文件（需语法高亮）
private val codeExts = setOf(
    "kt", "java", "xml", "json", "yml", "yaml", "properties",
    "cfg", "conf", "sh", "bat", "py", "js", "ts", "css", "gradle", "kts",
    "c", "cpp", "h", "hpp", "cs", "go", "rs", "swift", "rb", "php", "sql",
)
// 纯文本文件（无需高亮）
private val plainTextExts = setOf("txt", "log")

// 所有可编辑文本扩展（用于 isTextFile 图标判断）
private val textExts = codeExts + plainTextExts + setOf("md", "html", "htm", "xhtml")

fun isTextFile(name: String): Boolean {
    val ext = name.substringAfterLast('.', "").lowercase()
    return ext in textExts
}

fun isImageFile(name: String) = name.substringAfterLast('.', "").lowercase() in imageExts
fun isAudioFile(name: String) = name.substringAfterLast('.', "").lowercase() in audioExts
fun isVideoFile(name: String) = name.substringAfterLast('.', "").lowercase() in videoExts
fun isArchiveFile(name: String) = name.substringAfterLast('.', "").lowercase() in archiveExts

fun fileTypeForPath(path: String): TabType? {
    val name = displayNameFromPath(path)
    val ext = name.substringAfterLast('.', "").lowercase()
    return when {
        ext in imageExts -> TabType.Image
        ext in audioExts -> TabType.Audio
        ext in videoExts -> TabType.Video
        ext in archiveExts -> TabType.Archive
        ext == "md" -> TabType.Markdown
        ext in webExts -> TabType.Preview
        ext in codeExts -> TabType.File
        ext in plainTextExts -> TabType.Text
        else -> null
    }
}
