package com.medeide.jh.screens.home.landscape.topbar.audio

import android.content.Context
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.exoplayer.ExoPlayer

data class AudioTrack(
    val path: String,
    val name: String,
)

// 歌词行
data class LyricLine(val timeMs: Long, val text: String)

// 音频播放状态
class AudioPlaybackState {
    var exoPlayer: ExoPlayer? by mutableStateOf(null)
    var isPlaying by mutableStateOf(false)
    var duration by mutableFloatStateOf(0f)
    var currentPosition by mutableFloatStateOf(0f)
    var errorMsg by mutableStateOf<String?>(null)
    var isPrepared by mutableStateOf(false)
    var playlist by mutableStateOf<List<AudioTrack>>(emptyList())
    var currentIndex by mutableIntStateOf(-1)
    var currentAudioPath by mutableStateOf("")
    var currentSongName by mutableStateOf("")
    // 歌词列表
    var lyrics by mutableStateOf<List<LyricLine>>(emptyList())
    // 当前歌词行索引
    var currentLyricIndex by mutableIntStateOf(-1)

    fun release() {
        exoPlayer?.apply {
            if (isPlaying) stop()
            release()
        }
        exoPlayer = null
        isPlaying = false
        isPrepared = false
        lyrics = emptyList()
        currentLyricIndex = -1
        currentSongName = ""
        currentAudioPath = ""
    }

    companion object {
        // 扫描设备本地音频文件（MediaStore）
        suspend fun scanDeviceAudio(context: Context): List<AudioTrack> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val tracks = mutableListOf<AudioTrack>()
            try {
                val projection = arrayOf(
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.DATA,
                    MediaStore.Audio.Media.DURATION,
                )
                val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= 30000"
                context.contentResolver.query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    projection, selection, null,
                    "${MediaStore.Audio.Media.TITLE} ASC"
                )?.use { cursor ->
                    while (cursor.moveToNext()) {
                        val path = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.DATA)) ?: continue
                        val name = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.TITLE))
                            ?: path.substringAfterLast('/')
                        if (path.isNotBlank()) tracks.add(AudioTrack(path, name))
                    }
                }
            } catch (_: Exception) {}
            tracks
        }
    }
}
