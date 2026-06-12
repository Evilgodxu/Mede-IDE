package com.medeide.jh.screens.home.logic.utils

// 文件类型判断工具
object FileTypeUtil {
    const val MAX_TEXT_SIZE = 5 * 1024 * 1024L  // 5MB 文本上限

    // 可文本编辑的扩展名
    private val textExtensions = setOf(
        "kt", "kts", "java", "xml", "json", "yml", "yaml", "properties",
        "txt", "md", "gradle", "toml", "cfg", "conf", "ini",
        "html", "css", "js", "ts", "sql", "sh", "bat", "py",
        "rb", "go", "rs", "swift", "c", "cpp", "h", "hpp",
        "ktm", "kmap", "gradle.kts", "xml", "svg",
        "log", "env", "gitignore", "editorconfig",
    )

    // 图片扩展名
    private val imageExtensions = setOf("png", "jpg", "jpeg", "webp", "gif", "bmp", "ico", "svg")

    // 音频扩展名
    private val audioExtensions = setOf("mp3", "wav", "ogg", "aac", "flac", "wma", "m4a", "opus")

    // 视频扩展名
    private val videoExtensions = setOf("mp4", "avi", "mkv", "mov", "wmv", "flv", "3gp", "webm")

    // 压缩包扩展名
    private val archiveExtensions = setOf("zip", "tar", "gz", "bz2", "7z", "rar")

    // 已知二进制扩展名（不可文本编辑）
    private val binaryExtensions = setOf(
        "apk", "aar", "jar", "dex", "so", "a", "o", "class",
        "png", "jpg", "jpeg", "webp", "gif", "bmp", "ico",
        "mp3", "wav", "ogg", "aac", "flac", "wma", "m4a", "opus",
        "mp4", "avi", "mkv", "mov", "wmv", "flv", "3gp", "webm",
        "zip", "tar", "gz", "bz2", "7z", "rar",
        "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
        "ttf", "otf", "woff", "woff2", "eot",
        "ico", "icns", "psd", "ai", "eps",
        "db", "sqlite", "mdb",
        "keystore", "jks", "p12", "pfx",
        "dex", "oat", "vdex",
        "DS_Store", "localized",
    )

    fun isTextFile(name: String): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase()
        if (ext.isEmpty()) return true
        return ext in textExtensions
    }

    fun isImageFile(name: String): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in imageExtensions
    }

    fun isAudioFile(name: String): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in audioExtensions
    }

    fun isVideoFile(name: String): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in videoExtensions
    }

    fun isArchiveFile(name: String): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in archiveExtensions
    }

    fun isBinaryFile(name: String): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in binaryExtensions && ext !in textExtensions
    }

    fun canOpenAsText(name: String, size: Long): Boolean {
        if (size > MAX_TEXT_SIZE) return false
        return isTextFile(name)
    }

    fun openMode(name: String, size: Long): FileOpenMode = when {
        isImageFile(name) -> FileOpenMode.IMAGE
        isAudioFile(name) -> FileOpenMode.AUDIO
        isVideoFile(name) -> FileOpenMode.VIDEO
        isArchiveFile(name) -> FileOpenMode.ARCHIVE
        canOpenAsText(name, size) -> FileOpenMode.TEXT
        else -> FileOpenMode.UNSUPPORTED
    }
}

enum class FileOpenMode { TEXT, IMAGE, AUDIO, VIDEO, ARCHIVE, UNSUPPORTED }
