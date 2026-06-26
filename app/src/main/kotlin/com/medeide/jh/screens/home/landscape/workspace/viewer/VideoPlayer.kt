package com.medeide.jh.screens.home.landscape.workspace.viewer

import android.content.res.Configuration
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.net.Uri
import android.view.TextureView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.delay
import java.io.File

// 视频播放状态
class VideoPlaybackState {
    var mediaPlayer: MediaPlayer? by mutableStateOf(null)
    var isPlaying by mutableStateOf(false)
    var duration by mutableFloatStateOf(0f)
    var currentPosition by mutableFloatStateOf(0f)
    var errorMsg by mutableStateOf<String?>(null)
    var isPrepared by mutableStateOf(false)
    var currentVideoPath by mutableStateOf("")

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

// 视频播放器
@Composable
fun VideoPlayer(
    videoPath: String,
    state: VideoPlaybackState,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var controlsVisible by remember { mutableStateOf(true) }
    var surfaceReady by remember { mutableStateOf(false) }
    var isFullscreen by remember { mutableStateOf(false) }

    // 进度轮询
    LaunchedEffect(state.isPlaying) {
        while (state.isPlaying) {
            state.mediaPlayer?.let {
                if (it.isPlaying) state.currentPosition = it.currentPosition.toFloat()
            }
            delay(200)
        }
    }

    val needInit = videoPath != state.currentVideoPath || state.mediaPlayer == null

    // 全屏模式
    if (isFullscreen) {
        FullscreenOverlay(
            state = state,
            controlsVisible = controlsVisible,
            needInit = needInit,
            videoPath = videoPath,
            context = context,
            onToggleControls = { controlsVisible = !controlsVisible },
            onDismiss = { isFullscreen = false },
        )
        return
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF1E1E1E)),
    ) {
        if (state.errorMsg != null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Videocam, null,
                        Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        state.errorMsg!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            return@Box
        }

        // 视频渲染 Surface — 仅创建一次，切换标签不销毁
        AndroidView(
            factory = { ctx ->
                TextureView(ctx).apply {
                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(surface: SurfaceTexture, w: Int, h: Int) {
                            surfaceReady = true
                            if (!needInit) {
                                // 复用播放器，重新绑定 Surface
                                state.mediaPlayer?.setSurface(android.view.Surface(surface))
                                return
                            }
                            state.currentVideoPath = videoPath
                            try {
                                state.mediaPlayer?.apply { if (state.isPlaying) stop(); release() }
                                state.mediaPlayer = null
                                state.isPlaying = false
                                state.isPrepared = false
                                state.currentPosition = 0f
                                state.duration = 0f
                                state.errorMsg = null

                                val mp = MediaPlayer()
                                val uri = if (videoPath.startsWith("content://")) {
                                    Uri.parse(videoPath)
                                } else {
                                    Uri.fromFile(File(videoPath))
                                }
                                mp.setDataSource(context, uri)
                                mp.setSurface(android.view.Surface(surface))
                                mp.setOnPreparedListener {
                                    state.duration = it.duration.toFloat()
                                    state.isPrepared = true
                                    it.start()
                                    state.isPlaying = true
                                }
                                mp.setOnErrorListener { _, what, extra ->
                                    state.errorMsg = "播放失败: what=$what extra=$extra"
                                    true
                                }
                                mp.setOnCompletionListener {
                                    state.isPlaying = false
                                    state.currentPosition = 0f
                                    mp.seekTo(0)
                                }
                                mp.prepareAsync()
                                state.mediaPlayer = mp
                            } catch (e: Exception) {
                                state.errorMsg = "加载失败: ${e.message}"
                            }
                        }

                        override fun onSurfaceTextureSizeChanged(s: SurfaceTexture, w: Int, h: Int) {}

                        override fun onSurfaceTextureDestroyed(s: SurfaceTexture): Boolean {
                            // 不释放 MediaPlayer，仅解绑 Surface
                            state.mediaPlayer?.setSurface(null)
                            surfaceReady = false
                            return true
                        }

                        override fun onSurfaceTextureUpdated(s: SurfaceTexture) {}
                    }
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        // 点击切换控制层
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable { controlsVisible = !controlsVisible },
        )

        // 底部控制覆盖层
        if (controlsVisible) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color(0x99000000)),
            ) {
                // 进度滑块
                if (state.isPrepared && state.duration > 0) {
                    Slider(
                        value = (state.currentPosition / state.duration).coerceIn(0f, 1f),
                        onValueChange = { f ->
                            val pos = (f * state.duration).toInt()
                            state.mediaPlayer?.seekTo(pos)
                            state.currentPosition = pos.toFloat()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(20.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = Color(0x44FFFFFF),
                        ),
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 后退 15 秒
                    IconButton(
                        onClick = {
                            val pos = ((state.currentPosition - 15000).toInt()).coerceAtLeast(0)
                            state.mediaPlayer?.seekTo(pos)
                            state.currentPosition = pos.toFloat()
                        },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(Icons.Default.SkipPrevious, null, Modifier.size(20.dp), tint = Color.White)
                    }
                    Spacer(Modifier.width(2.dp))
                    // 播放/暂停
                    IconButton(
                        onClick = {
                            state.mediaPlayer?.let {
                                if (it.isPlaying) { it.pause(); state.isPlaying = false }
                                else { it.start(); state.isPlaying = true }
                            }
                        },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            null, Modifier.size(24.dp), tint = Color.White,
                        )
                    }
                    Spacer(Modifier.width(2.dp))
                    // 前进 15 秒
                    IconButton(
                        onClick = {
                            val pos = ((state.currentPosition + 15000).toInt()).coerceAtMost(state.duration.toInt())
                            state.mediaPlayer?.seekTo(pos)
                            state.currentPosition = pos.toFloat()
                        },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(Icons.Default.SkipNext, null, Modifier.size(20.dp), tint = Color.White)
                    }
                    Spacer(Modifier.width(8.dp))
                    // 时间显示
                    Text(
                        "${formatDuration(state.currentPosition.toInt())} / ${formatDuration(state.duration.toInt())}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFCCCCCC),
                    )
                    Spacer(Modifier.weight(1f))
                    // 全屏切换
                    IconButton(
                        onClick = { isFullscreen = true },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            Icons.Default.Fullscreen, null, Modifier.size(18.dp), tint = Color.White,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FullscreenOverlay(
    state: VideoPlaybackState,
    controlsVisible: Boolean,
    needInit: Boolean,
    videoPath: String,
    context: android.content.Context,
    onToggleControls: () -> Unit,
    onDismiss: () -> Unit,
) {
    val view = LocalView.current
    DisposableEffect(Unit) {
        val window = (view.context as? android.app.Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        controller?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        onDispose {
            val act = view.context as? android.app.Activity
            if (act != null && act.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                controller?.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                controller?.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            AndroidView(
                factory = { ctx ->
                    TextureView(ctx).apply {
                        surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                            override fun onSurfaceTextureAvailable(surface: SurfaceTexture, w: Int, h: Int) {
                                if (!needInit) {
                                    state.mediaPlayer?.setSurface(android.view.Surface(surface))
                                    return
                                }
                                try {
                                    state.mediaPlayer?.apply { if (state.isPlaying) stop(); release() }
                                    state.mediaPlayer = null
                                    state.isPlaying = false
                                    state.isPrepared = false
                                    state.duration = 0f
                                    state.errorMsg = null
                                    val mp = MediaPlayer()
                                    val uri = if (videoPath.startsWith("content://")) Uri.parse(videoPath)
                                    else Uri.fromFile(File(videoPath))
                                    mp.setDataSource(context, uri)
                                    mp.setSurface(android.view.Surface(surface))
                                    mp.setOnPreparedListener {
                                        state.duration = it.duration.toFloat()
                                        state.isPrepared = true
                                        state.currentPosition = state.mediaPlayer?.currentPosition?.toFloat() ?: 0f
                                        it.start()
                                        state.isPlaying = true
                                    }
                                    mp.setOnErrorListener { _, w, e ->
                                        state.errorMsg = "播放失败: what=$w extra=$e"; true
                                    }
                                    mp.setOnCompletionListener {
                                        state.isPlaying = false
                                        state.currentPosition = 0f
                                        mp.seekTo(0)
                                    }
                                    mp.prepareAsync()
                                    state.mediaPlayer = mp
                                } catch (e: Exception) {
                                    state.errorMsg = "加载失败: ${e.message}"
                                }
                            }

                            override fun onSurfaceTextureSizeChanged(s: SurfaceTexture, w: Int, h: Int) {}

                            override fun onSurfaceTextureDestroyed(s: SurfaceTexture): Boolean {
                                state.mediaPlayer?.setSurface(null)
                                return true
                            }

                            override fun onSurfaceTextureUpdated(s: SurfaceTexture) {}
                        }
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )

            Box(Modifier.fillMaxSize().clickable { onToggleControls() })

            if (controlsVisible) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (state.isPrepared && state.duration > 0) {
                        Slider(
                            value = (state.currentPosition / state.duration).coerceIn(0f, 1f),
                            onValueChange = { f ->
                                val pos = (f * state.duration).toInt()
                                state.mediaPlayer?.seekTo(pos)
                                state.currentPosition = pos.toFloat()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(20.dp),
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = Color(0x44FFFFFF),
                            ),
                        )
                    }
                    Text(
                        "${formatDuration(state.currentPosition.toInt())} / ${formatDuration(state.duration.toInt())}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFCCCCCC),
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                    Row(
                        modifier = Modifier.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        IconButton(onClick = {
                            val pos = ((state.currentPosition - 15000).toInt()).coerceAtLeast(0)
                            state.mediaPlayer?.seekTo(pos); state.currentPosition = pos.toFloat()
                        }, modifier = Modifier.size(44.dp)) {
                            Icon(Icons.Default.SkipPrevious, null, Modifier.size(28.dp), tint = Color.White)
                        }
                        Spacer(Modifier.width(8.dp))
                        IconButton(onClick = {
                            state.mediaPlayer?.let {
                                if (it.isPlaying) { it.pause(); state.isPlaying = false }
                                else { it.start(); state.isPlaying = true }
                            }
                        }, modifier = Modifier.size(56.dp)) {
                            Icon(
                                if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                null, Modifier.size(32.dp), tint = Color.White,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        IconButton(onClick = {
                            val pos = ((state.currentPosition + 15000).toInt()).coerceAtMost(state.duration.toInt())
                            state.mediaPlayer?.seekTo(pos); state.currentPosition = pos.toFloat()
                        }, modifier = Modifier.size(44.dp)) {
                            Icon(Icons.Default.SkipNext, null, Modifier.size(28.dp), tint = Color.White)
                        }
                        Spacer(Modifier.width(24.dp))
                        IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.FullscreenExit, null, Modifier.size(22.dp), tint = Color.White)
                        }
                    }
                }
            }
        }
    }
}

private fun formatDuration(ms: Int): String {
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return "%02d:%02d".format(min, sec)
}
