package com.template.jh.data.repository

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.template.jh.model.chat.ConversationEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

// 持久化存储: Gson → JSON 文件
class ConversationRepository(private val context: Context) {

    private val gson = Gson()
    private val storageFile: File
        get() = File(context.filesDir, "conversations.json")

    suspend fun load(): List<ConversationEntry> = withContext(Dispatchers.IO) {
        try {
            if (!storageFile.exists()) return@withContext emptyList()
            val json = storageFile.readText()
            val type = object : TypeToken<List<ConversationEntry>>() {}.type
            gson.fromJson<List<ConversationEntry>>(json, type) ?: emptyList()
        } catch (_: Exception) { emptyList() }
    }

    suspend fun save(conversations: List<ConversationEntry>) = withContext(Dispatchers.IO) {
        try {
            val json = gson.toJson(conversations)
            storageFile.writeText(json)
        } catch (_: Exception) {}
    }
}
