package com.medeide.jh.screens.home.landscape.topbar.audioplayer

import android.content.Context
import android.net.Uri
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

// LRC 歌词解析
object LyricsParser {

    private val LINE_REGEX = "\\[(\\d{2}):(\\d{2})\\.(\\d{2,3})\\](.*)".toRegex()

    fun parse(text: String): List<LyricLine> {
        val lines = mutableListOf<LyricLine>()
        text.lines().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isBlank()) return@forEach
            if (line.startsWith("[ti:") || line.startsWith("[ar:") ||
                line.startsWith("[al:") || line.startsWith("[by:") ||
                line.startsWith("[offset:") || line.startsWith("[re:") ||
                line.startsWith("[ve:")
            ) return@forEach

            LINE_REGEX.find(line)?.let { match ->
                val min = match.groupValues[1].toLongOrNull() ?: return@let
                val sec = match.groupValues[2].toLongOrNull() ?: return@let
                val milStr = match.groupValues[3]
                val text = match.groupValues[4].trim()
                if (text.isEmpty()) return@let

                var mil = milStr.toLongOrNull() ?: return@let
                if (milStr.length == 2) mil *= 10

                val timeMs = min * 60_000L + sec * 1_000L + mil
                lines.add(LyricLine(timeMs, text))
            }
        }
        return lines.sortedBy { it.timeMs }
    }

    fun loadFromFile(context: Context, audioPath: String): List<LyricLine> {
        if (!audioPath.startsWith("content://")) {
            val lrcFile = File(audioPath).let { f ->
                File(f.parent, f.nameWithoutExtension + ".lrc")
            }
            if (lrcFile.exists()) {
                return parse(lrcFile.readText())
            }
            val txtFile = File(audioPath).let { f ->
                File(f.parent, f.nameWithoutExtension + ".txt")
            }
            if (txtFile.exists()) {
                return parse(txtFile.readText())
            }
            return emptyList()
        }

        return try {
            val uri = Uri.parse(audioPath)
            val displayName = getDisplayName(context, uri) ?: return emptyList()
            val lrcName = displayName.substringBeforeLast('.') + ".lrc"

            val parentUri = uri.buildUpon().encodedPath(
                uri.encodedPath?.substringBeforeLast('/')
            ).build()
            val lrcUri = Uri.withAppendedPath(parentUri, lrcName)
            context.contentResolver.openInputStream(lrcUri)?.use { input ->
                BufferedReader(InputStreamReader(input)).readText().let { parse(it) }
            } ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun getDisplayName(context: Context, uri: Uri): String? {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        return cursor?.use {
            if (it.moveToFirst()) {
                val idx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) it.getString(idx) else null
            } else null
        }
    }
}
