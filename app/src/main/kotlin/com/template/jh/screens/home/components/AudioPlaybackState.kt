package com.template.jh.screens.home.components

import android.media.MediaPlayer
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File

data class AudioTrack(
    val path: String,
    val name: String,
)

/** 音频播放状态，在 HomeScreen 层级 remember，切换标签不丢失 */
class AudioPlaybackState {
    var mediaPlayer: MediaPlayer? by mutableStateOf(null)
    var isPlaying by mutableStateOf(false)
    var duration by mutableFloatStateOf(0f)
    var currentPosition by mutableFloatStateOf(0f)
    var errorMsg by mutableStateOf<String?>(null)
    var isPrepared by mutableStateOf(false)
    var playlist by mutableStateOf<List<AudioTrack>>(emptyList())
    var currentIndex by mutableIntStateOf(-1)
    var currentAudioPath by mutableStateOf("")

    fun release() {
        mediaPlayer?.apply {
            if (isPlaying) stop()
            release()
        }
        mediaPlayer = null
        isPlaying = false
        isPrepared = false
    }
}
