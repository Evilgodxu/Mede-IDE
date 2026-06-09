package com.template.jh.screens.home.components

import android.media.MediaPlayer
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.io.File

@Composable
fun AudioPlayer(
    audioPath: String,
    state: AudioPlaybackState,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    // 路径变化时重新初始化
    LaunchedEffect(audioPath) {
        if (audioPath == state.currentAudioPath && state.mediaPlayer != null) return@LaunchedEffect
        state.release()
        state.currentAudioPath = audioPath
        state.playlist = scanSiblingAudio(context, audioPath)
        val idx = state.playlist.indexOfFirst { it.path == audioPath }
        state.currentIndex = if (idx >= 0) idx else 0
        loadTrackInto(state, context, state.playlist.getOrNull(state.currentIndex)?.path ?: audioPath)
    }

    // 进度轮询
    LaunchedEffect(state.isPlaying) {
        while (state.isPlaying) {
            state.mediaPlayer?.let {
                if (it.isPlaying) state.currentPosition = it.currentPosition.toFloat()
            }
            delay(200)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (state.errorMsg != null) {
            Text(
                text = state.errorMsg!!,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
            return@Column
        }

        Icon(
            Icons.Default.MusicNote, null,
            Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
        )
        Spacer(Modifier.height(12.dp))

        val fileName = Uri.decode(state.playlist.getOrNull(state.currentIndex)?.name ?: audioPath.substringAfterLast('/'))
        Text(
            text = fileName,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )

        Spacer(Modifier.weight(1f))

        if (state.isPrepared && state.duration > 0) {
            Slider(
                value = (state.currentPosition / state.duration).coerceIn(0f, 1f),
                onValueChange = { f ->
                    val pos = (f * state.duration).toInt()
                    state.mediaPlayer?.seekTo(pos)
                    state.currentPosition = pos.toFloat()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                ),
            )
            Spacer(Modifier.height(2.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(formatDuration(state.currentPosition.toInt()),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(formatDuration(state.duration.toInt()),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(12.dp))
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            IconButton(onClick = {
                val pl = state.playlist; val ci = state.currentIndex
                if (pl.size > 1) {
                    val prev = (ci - 1 + pl.size) % pl.size
                    state.currentIndex = prev
                    loadTrackInto(state, context, pl[prev].path)
                }
            }, modifier = Modifier.size(44.dp), enabled = state.playlist.size > 1) {
                Icon(Icons.Default.SkipPrevious, null, Modifier.size(24.dp),
                    tint = if (state.playlist.size > 1) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
            }
            Spacer(Modifier.width(16.dp))

            IconButton(onClick = {
                state.mediaPlayer?.let {
                    if (it.isPlaying) { it.pause(); state.isPlaying = false }
                    else { it.start(); state.isPlaying = true }
                }
            }, modifier = Modifier.size(56.dp)) {
                Icon(
                    if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.width(16.dp))

            IconButton(onClick = {
                val pl = state.playlist; val ci = state.currentIndex
                if (pl.size > 1) {
                    val next = (ci + 1) % pl.size
                    state.currentIndex = next
                    loadTrackInto(state, context, pl[next].path)
                }
            }, modifier = Modifier.size(44.dp), enabled = state.playlist.size > 1) {
                Icon(Icons.Default.SkipNext, null, Modifier.size(24.dp),
                    tint = if (state.playlist.size > 1) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

private fun loadTrackInto(state: AudioPlaybackState, context: android.content.Context, path: String) {
    try {
        state.mediaPlayer?.apply {
            if (state.isPlaying) stop()
            release()
        }
        state.mediaPlayer = null
        state.isPlaying = false
        state.isPrepared = false
        state.currentPosition = 0f
        state.duration = 0f
        state.errorMsg = null

        val mp = MediaPlayer()
        val uri = if (path.startsWith("content://")) Uri.parse(path) else Uri.fromFile(File(path))
        mp.setDataSource(context, uri)
        mp.setOnPreparedListener {
            state.duration = it.duration.toFloat()
            state.isPrepared = true
            it.start()
            state.isPlaying = true
        }
        mp.setOnErrorListener { _, what, extra ->
            state.errorMsg = "播放错误: what=$what extra=$extra"; true
        }
        mp.setOnCompletionListener {
            val pl = state.playlist; val ci = state.currentIndex
            if (pl.size > 1) {
                val next = (ci + 1) % pl.size
                state.currentIndex = next
                loadTrackInto(state, context, pl[next].path)
            } else {
                state.isPlaying = false; state.currentPosition = 0f; mp.seekTo(0)
            }
        }
        mp.prepareAsync()
        state.mediaPlayer = mp
    } catch (e: Exception) {
        state.errorMsg = "加载失败: ${e.message}"
    }
}

/** 扫描同级目录中的音频文件 */
private fun scanSiblingAudio(context: android.content.Context, audioPath: String): List<AudioTrack> {
    val audioExtensions = setOf("mp3", "wav", "ogg", "aac", "flac", "wma", "m4a", "opus")
    return if (audioPath.startsWith("content://")) {
        val name = runCatching {
            val uri = Uri.parse(audioPath)
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)) else null
            }
        }.getOrNull() ?: audioPath.substringAfterLast('/')
        listOf(AudioTrack(audioPath, name))
    } else {
        val file = File(audioPath)
        val parent = file.parentFile ?: return listOf(AudioTrack(audioPath, file.name))
        parent.listFiles()
            ?.filter { it.isFile && it.name.substringAfterLast('.').lowercase() in audioExtensions }
            ?.sortedBy { it.name.lowercase() }
            ?.map { AudioTrack(it.absolutePath, it.name) }
            ?: listOf(AudioTrack(audioPath, file.name))
    }
}

private fun formatDuration(ms: Int): String {
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return "%02d:%02d".format(min, sec)
}
