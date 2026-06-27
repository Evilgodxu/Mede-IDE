package com.medeide.jh.screens.home.landscape.topbar.audioplayer

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.medeide.jh.core.model.AudioTrack

@Composable
fun AudioControl(
    audioPlaybackState: AudioPlaybackState,
    scannedAudioTracks: List<AudioTrack>,
    onScanMusic: () -> Unit,
    onPlayAudioTrack: (AudioTrack) -> Unit,
    onStopAudio: () -> Unit,
    dropdownMaxHeight: Dp,
    modifier: Modifier = Modifier,
) {
    var musicMenuExpanded by remember { mutableStateOf(false) }
    val aps = audioPlaybackState
    val hasAudio = aps.currentAudioPath.isNotBlank()
    val displayText = when {
        hasAudio && aps.lyrics.isNotEmpty() && aps.currentLyricIndex in aps.lyrics.indices ->
            aps.lyrics[aps.currentLyricIndex].text
        hasAudio && aps.currentSongName.isNotBlank() -> aps.currentSongName
        else -> null
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        Box {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clickable { musicMenuExpanded = true; onScanMusic() }
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            ) {
                if (hasAudio) {
                    if (displayText != null && displayText.length > 10) {
                        Text(
                            text = displayText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Visible,
                            modifier = Modifier.widthIn(max = 180.dp).basicMarquee(iterations = Int.MAX_VALUE),
                        )
                    } else {
                        Text(
                            text = (displayText ?: aps.currentSongName).take(12),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                } else {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = "音乐",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = if (hasAudio) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            DropdownMenu(
                expanded = musicMenuExpanded,
                onDismissRequest = { musicMenuExpanded = false },
                modifier = Modifier.widthIn(min = 200.dp, max = 300.dp).heightIn(max = dropdownMaxHeight)
            ) {
                if (scannedAudioTracks.isEmpty()) {
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 1.5.dp)
                                Spacer(Modifier.width(8.dp))
                                Text("正在扫描音乐…", style = MaterialTheme.typography.labelSmall)
                            }
                        },
                        onClick = { },
                        enabled = false,
                    )
                } else {
                    Text("本地音乐 (${scannedAudioTracks.size})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                    HorizontalDivider()
                    scannedAudioTracks.take(100).forEach { track ->
                        val isActive = track.path == aps.currentAudioPath
                        DropdownMenuItem(
                            text = {
                                Column(Modifier.widthIn(max = 260.dp)) {
                                    Text(
                                        text = track.name,
                                        style = MaterialTheme.typography.labelMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            },
                            onClick = {
                                musicMenuExpanded = false
                                onPlayAudioTrack(track)
                            },
                            trailingIcon = {
                                if (isActive) {
                                    Icon(Icons.Default.Check, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                }
                            },
                        )
                    }
                    if (scannedAudioTracks.size > 100) {
                        HorizontalDivider()
                        Text("… 还有 ${scannedAudioTracks.size - 100} 首",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                    }
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            // 播放模式切换
            val pmIcon = when (aps.playMode) {
                PlayMode.RepeatOne -> Icons.Default.RepeatOne
                PlayMode.RepeatAll -> Icons.Default.Repeat
                PlayMode.Shuffle -> Icons.Default.Shuffle
            }
            val pmLabel = when (aps.playMode) {
                PlayMode.RepeatOne -> "单曲循环"
                PlayMode.RepeatAll -> "列表循环"
                PlayMode.Shuffle -> "随机播放"
            }
            IconButton(
                onClick = {
                    aps.playMode = when (aps.playMode) {
                        PlayMode.RepeatAll -> PlayMode.RepeatOne
                        PlayMode.RepeatOne -> PlayMode.Shuffle
                        PlayMode.Shuffle -> PlayMode.RepeatAll
                    }
                },
                modifier = Modifier.size(24.dp),
            ) {
                Icon(pmIcon, pmLabel, Modifier.size(12.dp),
                    tint = if (hasAudio) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f))
            }

            IconButton(
                onClick = {
                    val pl = aps.playlist; val ci = aps.currentIndex
                    if (pl.size > 1) {
                        val prev = (ci - 1 + pl.size) % pl.size
                        onPlayAudioTrack(pl[prev])
                    }
                },
                modifier = Modifier.size(26.dp),
                enabled = hasAudio && aps.playlist.size > 1,
            ) { Icon(Icons.Default.SkipPrevious, null, Modifier.size(14.dp),
                tint = if (hasAudio) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)) }

            IconButton(
                onClick = {
                    aps.exoPlayer?.let { p ->
                        if (p.isPlaying) { p.pause(); aps.isPlaying = false }
                        else { p.play(); aps.isPlaying = true }
                    }
                },
                modifier = Modifier.size(26.dp),
                enabled = hasAudio,
            ) {
                Icon(
                    if (aps.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    null, Modifier.size(14.dp),
                    tint = if (hasAudio) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f),
                )
            }

            IconButton(
                onClick = {
                    val pl = aps.playlist; val ci = aps.currentIndex
                    if (pl.size > 1) {
                        val next = (ci + 1) % pl.size
                        onPlayAudioTrack(pl[next])
                    }
                },
                modifier = Modifier.size(26.dp),
                enabled = hasAudio && aps.playlist.size > 1,
            ) { Icon(Icons.Default.SkipNext, null, Modifier.size(14.dp),
                tint = if (hasAudio) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)) }

            IconButton(
                onClick = { onStopAudio() },
                modifier = Modifier.size(26.dp),
                enabled = hasAudio,
            ) { Icon(Icons.Default.Close, null, Modifier.size(14.dp),
                tint = if (hasAudio) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)) }
        }
    }
}
