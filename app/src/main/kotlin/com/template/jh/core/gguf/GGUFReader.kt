package com.template.jh.core.gguf

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * GGUF 文件元数据读取器。
 *
 * 纯 Kotlin 实现，无需 JNI。解析 GGUF v3 文件头中的 metadata KV 对，
 * 获取 context_length、chat_template 等信息。
 *
 * GGUF v3 文件结构:
 *   [4]  magic            "GGUF"
 *   [4]  version          uint32
 *   [8]  tensor_count     uint64
 *   [8]  metadata_kv_count uint64
 *   --- metadata KV pairs（变长）---
 */
class GGUFReader(private val file: File) {

    data class Metadata(
        val contextLength: Int? = null,
        val chatTemplate: String? = null,
        val modelName: String? = null,
        val description: String? = null,
        val embeddingLength: Int? = null,
        val blockCount: Int? = null,
        val headCount: Int? = null,
        val layerCount: Int? = null,
        val fileType: Int? = null,
        val tokenizerChatTemplate: String? = null,
    )

    /** 读取全部元数据 */
    fun readMetadata(): Metadata {
        if (!file.exists() || file.length() < 4) return Metadata()
        return try {
            RandomAccessFile(file, "r").use { raf ->
                val magic = ByteArray(4)
                raf.readFully(magic)
                if (!magic.contentEquals(GGUF_MAGIC)) return Metadata()

                readU32(raf)  // version, skip
                readU64(raf)  // tensor_count, skip
                val kvCount = readU64(raf)  // metadata_kv_count

                var ctxLen: Int? = null
                var chatTmpl: String? = null
                var modelName: String? = null
                var desc: String? = null
                var embedLen: Int? = null
                var blockCount: Int? = null
                var headCount: Int? = null
                var layerCount: Int? = null
                var fileType: Int? = null
                var tokenizerChatTmpl: String? = null

                val maxKv = kvCount.toInt().coerceAtMost(500)
                for (i in 0 until maxKv) {
                    val key = readGGUFString(raf) ?: break
                    val kvType = readU32(raf)
                    val value = readTypedValue(raf, kvType.toInt())

                    when (key) {
                        "llama.context_length", "context_length" -> {
                            ctxLen = toInt(value)
                        }
                        "llama.rope.scaling.type", "rope.scaling.type" -> {}
                        "tokenizer.chat_template", "chat_template" -> {
                            if (value is String) tokenizerChatTmpl = value
                        }
                        "general.name", "llama.model.name" -> {
                            if (value is String) modelName = value
                        }
                        "general.description" -> {
                            if (value is String) desc = value
                        }
                        "llama.embedding_length" -> {
                            embedLen = toInt(value)
                        }
                        "llama.block_count" -> {
                            blockCount = toInt(value)
                        }
                        "llama.attention.head_count" -> {
                            headCount = toInt(value)
                        }
                        "llama.layer_count" -> {
                            layerCount = toInt(value)
                        }
                        "general.file_type" -> {
                            fileType = toInt(value)
                        }
                    }
                }

                Metadata(
                    contextLength = ctxLen,
                    chatTemplate = chatTmpl ?: tokenizerChatTmpl,
                    modelName = modelName,
                    description = desc,
                    embeddingLength = embedLen,
                    blockCount = blockCount,
                    headCount = headCount,
                    layerCount = layerCount,
                    fileType = fileType,
                    tokenizerChatTemplate = tokenizerChatTmpl,
                )
            }
        } catch (_: Exception) {
            Metadata()
        }
    }

    /** 只读取 context_length（高效，读到即返回） */
    fun readContextLength(): Int {
        if (!file.exists() || file.length() < 4) return 0
        return try {
            RandomAccessFile(file, "r").use { raf ->
                val magic = ByteArray(4)
                raf.readFully(magic)
                if (!magic.contentEquals(GGUF_MAGIC)) return@use 0
                readU32(raf)  // version
                readU64(raf)  // tensor_count
                val kvCount = readU64(raf)
                for (i in 0 until kvCount.toInt().coerceAtMost(200)) {
                    val key = readGGUFString(raf) ?: break
                    val kvType = readU32(raf)
                    val value = readTypedValue(raf, kvType.toInt())
                    if ((key == "llama.context_length" || key == "context_length") && value != null) {
                        val ctx = toInt(value)
                        if (ctx != null && ctx > 0) return@use ctx
                    }
                }
                0
            }
        } catch (_: Exception) { 0 }
    }

    /** 只读取 chat_template */
    fun readChatTemplate(): String? {
        if (!file.exists() || file.length() < 4) return null
        return try {
            RandomAccessFile(file, "r").use { raf ->
                val magic = ByteArray(4)
                raf.readFully(magic)
                if (!magic.contentEquals(GGUF_MAGIC)) return@use null
                readU32(raf)
                readU64(raf)
                val kvCount = readU64(raf)
                for (i in 0 until kvCount.toInt().coerceAtMost(200)) {
                    val key = readGGUFString(raf) ?: break
                    val kvType = readU32(raf)
                    val value = readTypedValue(raf, kvType.toInt())
                    if ((key == "tokenizer.chat_template" || key == "chat_template") && value is String) {
                        return@use value
                    }
                }
                null
            }
        } catch (_: Exception) { null }
    }

    /** 验证 GGUF 魔数 */
    fun isValid(): Boolean {
        if (!file.exists() || file.length() < 4) return false
        return try {
            RandomAccessFile(file, "r").use { raf ->
                val magic = ByteArray(4)
                raf.readFully(magic)
                magic.contentEquals(GGUF_MAGIC)
            }
        } catch (_: Exception) { false }
    }

    // ===== 私有解析方法 =====

    private fun readU32(raf: RandomAccessFile): Long {
        val bytes = ByteArray(4)
        raf.readFully(bytes)
        return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getInt().toLong() and 0xFFFFFFFFL
    }

    private fun readU64(raf: RandomAccessFile): Long {
        val bytes = ByteArray(8)
        raf.readFully(bytes)
        return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).long
    }

    private fun readF64(raf: RandomAccessFile): Double {
        val bytes = ByteArray(8)
        raf.readFully(bytes)
        return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).double
    }

    private fun readGGUFString(raf: RandomAccessFile): String? {
        val len = readU64(raf)
        if (len <= 0 || len > 1024 * 1024) return null
        val bytes = ByteArray(len.toInt())
        raf.readFully(bytes)
        return String(bytes, Charsets.UTF_8)
    }

    private fun readTypedValue(raf: RandomAccessFile, type: Int): Any? {
        return when (type) {
            0, 1 -> { raf.readByte().toInt() }
            2, 3 -> {
                val b = ByteArray(2); raf.readFully(b)
                ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).short.toInt()
            }
            4, 5 -> {
                val b = ByteArray(4); raf.readFully(b)
                ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).int
            }
            6 -> readU64(raf)
            7 -> {
                val b = ByteArray(1); raf.readFully(b)
                b[0] != 0.toByte()
            }
            8 -> readF64(raf)
            10 -> { raf.skipBytes(4); null }
            11 -> {
                val elemType = readU32(raf)
                val len = readU64(raf)
                val arr = mutableListOf<Any?>()
                for (j in 0 until len) {
                    arr.add(readTypedValue(raf, elemType.toInt()))
                }
                arr
            }
            12 -> readGGUFString(raf)
            else -> { raf.skipBytes(8); null }
        }
    }

    /** 将 Any? 转为 Int?，处理 Long/Double/String 多种 GGUF 数值类型 */
    private fun toInt(value: Any?): Int? = when (value) {
        is Int -> value
        is Long -> value.toInt().takeIf { it >= 0 }
        is Double -> value.toInt()
        is Float -> value.toInt()
        is String -> value.toIntOrNull()
        else -> null
    }

    companion object {
        private val GGUF_MAGIC = byteArrayOf(0x47, 0x47, 0x55, 0x46)
    }
}
