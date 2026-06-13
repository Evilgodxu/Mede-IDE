package com.medeide.jh.screens.home.landscape.topbar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass
import com.medeide.jh.R
import com.medeide.jh.model.chat.CloudModelProfile
import com.medeide.jh.model.chat.EngineStatus
import com.medeide.jh.model.chat.ModelInfo
import com.medeide.jh.screens.home.landscape.topbar.audio.AudioControl
import com.medeide.jh.screens.home.landscape.topbar.audio.AudioPlaybackState
import com.medeide.jh.screens.home.landscape.topbar.audio.AudioTrack

// 顶栏区
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainTopBar(
    windowSizeClass: WindowSizeClass,
    engineStatus: EngineStatus = EngineStatus.Idle,
    modelName: String = "",
    availableModels: List<ModelInfo> = emptyList(),
    cloudProfiles: List<CloudModelProfile> = emptyList(),
    activeCloudProfileId: String = "",
    cloudModelEnabled: Boolean = false,
    onScanModels: () -> Unit = {},
    onLoadModel: (String) -> Unit = {},
    onBrowseModelFile: () -> Unit = {},
    onSwitchCloudProfile: (String) -> Unit = {},
    // 音频播放
    audioPlaybackState: AudioPlaybackState? = null,
    scannedAudioTracks: List<AudioTrack> = emptyList(),
    onScanMusic: () -> Unit = {},
    onPlayAudioTrack: (AudioTrack) -> Unit = {},
    onStopAudio: () -> Unit = {},
) {
    val topBarInsets = if (!windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)) {
        WindowInsets(top = 0)
    } else {
        WindowInsets(0, 0, 0, 0)
    }

    val screenHeightDp = LocalConfiguration.current.screenHeightDp
    val dropdownMaxHeight = (screenHeightDp * 0.75f).dp

    Column {
        TopAppBar(
            title = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 文件（保留按钮，移除下拉菜单）
                    Text(
                        text = stringResource(R.string.menu_file),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )

                    Text(
                        text = "丨",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    // 编辑（保留按钮，移除下拉菜单）
                    Text(
                        text = stringResource(R.string.menu_edit),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )

                    Text(
                        text = "丨",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    // 搜索（保留按钮图标，移除搜索功能）
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "搜索",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            actions = {
                // 音乐选择器 + 播放控件
                if (audioPlaybackState != null) {
                    AudioControl(
                        audioPlaybackState = audioPlaybackState,
                        scannedAudioTracks = scannedAudioTracks,
                        onScanMusic = onScanMusic,
                        onPlayAudioTrack = onPlayAudioTrack,
                        onStopAudio = onStopAudio,
                        dropdownMaxHeight = dropdownMaxHeight,
                    )

                    Text(
                        text = "丨",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }

                // 模型选择
                ModelSelector(
                    engineStatus = engineStatus,
                    modelName = modelName,
                    availableModels = availableModels,
                    cloudProfiles = cloudProfiles,
                    activeCloudProfileId = activeCloudProfileId,
                    cloudModelEnabled = cloudModelEnabled,
                    onScanModels = onScanModels,
                    onLoadModel = onLoadModel,
                    onBrowseModelFile = onBrowseModelFile,
                    onSwitchCloudProfile = onSwitchCloudProfile,
                    dropdownMaxHeight = dropdownMaxHeight,
                )
            },
            windowInsets = topBarInsets,
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
                titleContentColor = MaterialTheme.colorScheme.onBackground,
                actionIconContentColor = MaterialTheme.colorScheme.onBackground
            ),
            modifier = Modifier.height(40.dp)
        )
        HorizontalDivider(
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    }
}
