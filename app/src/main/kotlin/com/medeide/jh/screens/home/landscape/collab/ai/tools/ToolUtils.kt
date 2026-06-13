package com.medeide.jh.screens.home.landscape.collab.ai.tools

import com.medeide.jh.data.storage.FileManager

// 统一结果格式
fun ok(msg: String) = "[OK] $msg"
fun err(msg: String) = "[ERROR] $msg"
fun isOk(result: String) = result.startsWith("[OK]")
fun isErr(result: String) = result.startsWith("[ERROR]")

// 路径解析：绝对路径转相对，非绝对路径原样返回
fun resolvePath(path: String, fileManager: FileManager?): String {
    if (path.startsWith("/storage/") || path.startsWith("/data/")) {
        val base = fileManager?.let {
            it.projectDirPath.ifEmpty { it.storageRootPath }
        } ?: return path
        return path.removePrefix(base).trimStart('/')
    }
    return path.trim('/')
}

fun resolvePathOrAbsolute(path: String, fileManager: FileManager?): String = resolvePath(path, fileManager)

fun formatSize(bytes: Long): String = when {
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024L -> "${bytes / 1024L} KB"
    else -> "${"%.1f".format(bytes.toDouble() / (1024.0 * 1024.0))} MB"
}

fun normalizeBlankLines(text: String): String {
    return text.replace(Regex("""\n{3,}"""), "\n\n").trimEnd('\n')
}

// Tool JSON 定义辅助
fun p(type: String, desc: String): org.json.JSONObject = org.json.JSONObject().apply {
    put("type", type); put("description", desc)
}

fun toolDef(
    name: String, desc: String,
    required: List<String> = emptyList(),
    vararg props: Pair<String, org.json.JSONObject>
): org.json.JSONObject = org.json.JSONObject().apply {
    put("type", "function")
    put("function", org.json.JSONObject().apply {
        put("name", name); put("description", desc)
        put("parameters", org.json.JSONObject().apply {
            put("type", "object")
            put("properties", org.json.JSONObject().apply { props.forEach { (k, v) -> put(k, v) } })
            if (required.isNotEmpty()) put("required", org.json.JSONArray(required))
        })
    })
}
