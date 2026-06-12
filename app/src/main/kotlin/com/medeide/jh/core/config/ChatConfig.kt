package com.medeide.jh.core.config

/** ChatViewModel 及相关模块的共享常量配置 */
object ChatConfig {
    const val DEFAULT_CONTEXT_WINDOW = 128000
    const val DEFAULT_LOCAL_CONTEXT_WINDOW = 32768
    const val KEEP_EXCHANGES = 5
    const val KEEP_PRIORITY_LINES = 200
    const val SUMMARIZE_INTERVAL = 10
    const val TOOL_TRUNCATE_LINES = 80
    const val UTFS_BYTES_PER_TOKEN = 3.5f
    const val MAX_TOOL_RETRY_ROUNDS = 8
    const val MAX_ATTACHED_IMAGES = 4

    val KNOWN_TOOLS = setOf(
        "listFiles", "readFile", "writeFile", "replaceInFile",
        "batchReplaceInFile", "deleteFile", "createDirectory", "runCommand",
        "searchWeb", "readLints", "grep", "searchCodebase", "glob",
        "searchConversationMemory", "getRecentConversationMemory",
        "fileExists", "fileInfo", "moveFile", "copyFile",
        "zipFiles", "unzipFiles",
        "visitWeb", "downloadFile", "httpRequest",
    )

    val MODIFYING_TOOLS = setOf(
        "writeFile", "replaceInFile", "batchReplaceInFile", "deleteFile", "createDirectory",
        "moveFile", "copyFile", "zipFiles", "unzipFiles", "downloadFile",
    )

    val OPEN_FILE_TOOLS = setOf(
        "writeFile", "replaceInFile", "batchReplaceInFile",
    )
}
