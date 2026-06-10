package com.template.jh.core.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream

/** 图片 URI 预处理 — 转为 LiteRT 兼容的临时文件 */
class ImageProcessor(private val context: Context) {

    /** 将图片 URI 转为临时文件，非 JPEG/PNG 格式自动转 JPEG */
    fun uriToTempFile(uri: Uri): File? = try {
        var mimeType = context.contentResolver.getType(uri) ?: ""
        if (mimeType.isBlank()) {
            val ext = uri.lastPathSegment?.substringAfterLast('.', "")?.lowercase() ?: ""
            mimeType = when (ext) {
                "jpg", "jpeg" -> "image/jpeg"
                "png" -> "image/png"
                "webp" -> "image/webp"
                "gif" -> "image/gif"
                "bmp" -> "image/bmp"
                else -> ""
            }
        }
        val isDirectlySupported = mimeType in listOf("image/jpeg", "image/png")
        if (isDirectlySupported) {
            val ext = mimeType.substringAfterLast('/')
            val input = context.contentResolver.openInputStream(uri) ?: return null
            val tempFile = File.createTempFile("img_", ".$ext", context.cacheDir)
            FileOutputStream(tempFile).use { out -> input.use { it.copyTo(out) } }
            tempFile
        } else {
            val input = context.contentResolver.openInputStream(uri) ?: return null
            val bitmap = BitmapFactory.decodeStream(input)
            input.close()
            if (bitmap == null) return null
            val tempFile = File.createTempFile("img_", ".jpg", context.cacheDir)
            FileOutputStream(tempFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
            }
            bitmap.recycle()
            tempFile
        }
    } catch (_: Exception) { null }

    fun resolveUriFileName(uri: Uri): String? = try {
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (i >= 0) c.getString(i) else null
            } else null
        }
    } catch (_: Exception) { null }
}
