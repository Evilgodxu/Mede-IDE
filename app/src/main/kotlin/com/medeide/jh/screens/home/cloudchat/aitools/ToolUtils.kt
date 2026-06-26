package com.medeide.jh.screens.home.cloudchat.aitools

import org.json.JSONArray
import org.json.JSONObject

fun ok(msg: String) = "[OK] $msg"
fun err(msg: String) = "[ERROR] $msg"

fun formatSize(bytes: Long): String = when {
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024L -> "${bytes / 1024L} KB"
    else -> "${"%.1f".format(bytes.toDouble() / (1024.0 * 1024.0))} MB"
}

fun p(type: String, desc: String): JSONObject = JSONObject().apply {
    put("type", type)
    put("description", desc)
}

fun toolDef(
    name: String, desc: String,
    required: List<String> = emptyList(),
    vararg props: Pair<String, JSONObject>,
): JSONObject = JSONObject().apply {
    put("type", "function")
    put("function", JSONObject().apply {
        put("name", name)
        put("description", desc)
        put("parameters", JSONObject().apply {
            put("type", "object")
            put("properties", JSONObject().apply {
                props.forEach { (k, v) -> put(k, v) }
            })
            if (required.isNotEmpty()) put("required", JSONArray(required))
        })
    })
}
