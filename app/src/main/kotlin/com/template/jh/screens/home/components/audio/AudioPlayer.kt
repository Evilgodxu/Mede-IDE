package com.template.jh.screens.home.components.audio

import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lyrics
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.delay
import java.io.File

/** 参考 Echo-Music 的支持格式扩展列表 */
private val SUPPORTED_AUDIO_EXTENSIONS = setOf(
    "aac", "amr", "flac", "m4a", "m4b", "m4p", "mid", "mka",
    "mp3", "mp4", "oga", "ogg", "opus", "wav", "weba", "webm",
    "3ga", "3gp",
)

@Composable
fun AudioPlayer(
    audioPath: String,
    state: AudioPlaybackState,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var showLyrics by remember { mutableStateOf(false) }

    // 路径变化时初始化 ExoPlayer
    LaunchedEffect(audioPath) {
        if (audioPath == state.currentAudioPath && state.exoPlayer != null) return@LaunchedEffect
        state.release()
        state.currentAudioPath = audioPath

        // 创建 ExoPlayer
        val player = ExoPlayer.Builder(context).build()
        state.exoPlayer = player

        // 扫描同级音频
        state.playlist = scanSiblingAudio(context, audioPath)
        val idx = state.playlist.indexOfFirst { it.path == audioPath }
        state.currentIndex = if (idx >= 0) idx else 0

        // 加载并播放
        loadTrack(state, context, state.playlist.getOrNull(state.currentIndex)?.path ?: audioPath)

        // 监听播放状态
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                state.isPlaying = isPlaying
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_READY -> {
                        state.isPrepared = true
                        state.duration = player.duration.coerceAtLeast(0L).toFloat()
                    }
                    Player.STATE_ENDED -> {
                        // 自动下一首
                        val pl = state.playlist
                        val ci = state.currentIndex
                        if (pl.size > 1) {
                            val next = (ci + 1) % pl.size
                            state.currentIndex = next
                            loadTrack(state, context, pl[next].path)
                        } else {
                            state.isPlaying = false
                            state.currentPosition = 0f
                            player.seekTo(0)
                        }
                    }
                }
            }
        })
    }

    // 进度轮询 + 歌词同步
    LaunchedEffect(state.isPlaying) {
        while (true) {
            delay(200)
            state.exoPlayer?.let { p ->
                if (p.isPlaying) {
                    val pos = p.currentPosition.coerceAtLeast(0L).toFloat()
                    state.currentPosition = pos
                    // 同步歌词索引
                    updateLyricIndex(state, pos.toLong())
                }
            }
        }
    }

    // 清理 — 组件卸载时释放播放器
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            state.release()
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

        // 歌词 / 封面切换
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (showLyrics && state.lyrics.isNotEmpty()) {
                LyricsDisplay(
                    lyrics = state.lyrics,
                    currentLineIndex = state.currentLyricIndex,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        Icons.Default.MusicNote, null,
                        Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                    )
                    Spacer(Modifier.height(12.dp))
                    val fileName = Uri.decode(
                        state.playlist.getOrNull(state.currentIndex)?.name
                            ?: audioPath.substringAfterLast('/')
                    )
                    Text(
                        text = fileName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        // 进度条
        if (state.isPrepared && state.duration > 0) {
            Spacer(Modifier.height(8.dp))
            Slider(
                value = (state.currentPosition / state.duration).coerceIn(0f, 1f),
                onValueChange = { f ->
                    val pos = (f * state.duration).toLong()
                    state.exoPlayer?.seekTo(pos)
                    state.currentPosition = pos.toFloat()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                ),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(formatDuration(state.currentPosition.toInt()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(formatDuration(state.duration.toInt()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))
        }

        // 控制栏
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth(),
        ) {
            // 歌词切换按钮
            IconButton(
                onClick = {
                    showLyrics = !showLyrics
                    if (showLyrics && state.lyrics.isEmpty()) {
                        state.lyrics = LyricsParser.loadFromFile(context, audioPath)
                    }
                },
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    Icons.Default.Lyrics, null, Modifier.size(20.dp),
                    tint = if (showLyrics) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(4.dp))

            IconButton(onClick = {
                val pl = state.playlist; val ci = state.currentIndex
                if (pl.size > 1) {
                    val prev = (ci - 1 + pl.size) % pl.size
                    state.currentIndex = prev
                    loadTrack(state, context, pl[prev].path)
                }
            }, modifier = Modifier.size(44.dp), enabled = state.playlist.size > 1) {
                Icon(Icons.Default.SkipPrevious, null, Modifier.size(24.dp),
                    tint = if (state.playlist.size > 1) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
            }
            Spacer(Modifier.width(8.dp))

            IconButton(onClick = {
                state.exoPlayer?.let { p ->
                    if (p.isPlaying) { p.pause(); state.isPlaying = false }
                    else { p.play(); state.isPlaying = true }
                }
            }, modifier = Modifier.size(56.dp)) {
                Icon(
                    if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.width(8.dp))

            IconButton(onClick = {
                val pl = state.playlist; val ci = state.currentIndex
                if (pl.size > 1) {
                    val next = (ci + 1) % pl.size
                    state.currentIndex = next
                    loadTrack(state, context, pl[next].path)
                }
            }, modifier = Modifier.size(44.dp), enabled = state.playlist.size > 1) {
                Icon(Icons.Default.SkipNext, null, Modifier.size(24.dp),
                    tint = if (state.playlist.size > 1) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
            }
            Spacer(Modifier.width(4.dp))

            // 占位保持对称
            Spacer(Modifier.size(40.dp))
        }
        Spacer(Modifier.height(4.dp))
    }
}

/** 使用 ExoPlayer 加载并播放音频 */
private fun loadTrack(state: AudioPlaybackState, context: android.content.Context, path: String) {
    try {
        state.errorMsg = null
        state.currentPosition = 0f
        state.duration = 0f
        state.isPrepared = false
        state.lyrics = emptyList()
        state.currentLyricIndex = -1

        val uri = if (path.startsWith("content://")) Uri.parse(path) else Uri.fromFile(File(path))
        val mediaItem = MediaItem.fromUri(uri)
        state.exoPlayer?.apply {
            stop()
            clearMediaItems()
            setMediaItem(mediaItem)
            prepare()
            play()
        }

        // 异步加载歌词
        state.lyrics = LyricsParser.loadFromFile(context, path)
    } catch (e: Exception) {
        state.errorMsg = "加载失败: ${e.message}"
    }
}

/** 根据当前播放位置更新歌词索引 — 二分查找 */
private fun updateLyricIndex(state: AudioPlaybackState, positionMs: Long) {
    val lines = state.lyrics
    if (lines.isEmpty()) {
        state.currentLyricIndex = -1
        return
    }
    // 二分查找
    var lo = 0
    var hi = lines.size - 1
    var result = -1
    while (lo <= hi) {
        val mid = (lo + hi) / 2
        if (lines[mid].timeMs <= positionMs) {
            result = mid
            lo = mid + 1
        } else {
            hi = mid - 1
        }
    }
    state.currentLyricIndex = result
}

/** 扫描同级目录音频文件（扩展格式参考 Echo-Music） */
private fun scanSiblingAudio(context: android.content.Context, audioPath: String): List<AudioTrack> {
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
            ?.filter { it.isFile && it.name.substringAfterLast('.').lowercase() in SUPPORTED_AUDIO_EXTENSIONS }
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
