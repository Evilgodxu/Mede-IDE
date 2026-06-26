package com.medeide.jh.screens.home.landscape.workspace.audioplayer

import android.content.Context
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.exoplayer.ExoPlayer

// 音频播放器 — 工作区全屏播放状态

data class AudioTrack(
    val path: String,
    val name: String,
)

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

    fun release() {
        exoPlayer?.apply {
            if (isPlaying) stop()
            release()
        }
        exoPlayer = null
        isPlaying = false
        isPrepared = false
        currentAudioPath = ""
    }

    companion object {
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
