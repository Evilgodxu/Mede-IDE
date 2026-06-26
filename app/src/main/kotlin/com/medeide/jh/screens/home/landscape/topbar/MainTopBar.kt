package com.medeide.jh.screens.home.landscape.topbar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medeide.jh.model.chat.CloudModelProfile
import com.medeide.jh.model.chat.EngineStatus
import com.medeide.jh.screens.home.localchat.LocalModelInfo
import com.medeide.jh.screens.home.recent.RecentFileEntry
import com.medeide.jh.screens.home.components.LayoutModeToggle
import com.medeide.jh.screens.home.landscape.topbar.audioplayer.AudioControl
import com.medeide.jh.screens.home.landscape.topbar.audioplayer.AudioPlaybackState
import com.medeide.jh.screens.home.landscape.topbar.audioplayer.AudioTrack
import com.medeide.jh.screens.home.landscape.topbar.modelselector.ModelSelector

// 顶栏区
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainTopBar(
    // 布局模式
    isIdeMode: Boolean,
    onToggleLayoutMode: () -> Unit,
    // 音频播放
    audioPlaybackState: AudioPlaybackState? = null,
    scannedAudioTracks: List<AudioTrack> = emptyList(),
    onScanMusic: () -> Unit = {},
    onPlayAudioTrack: (AudioTrack) -> Unit = {},
    onStopAudio: () -> Unit = {},
    // 编辑
    onFindReplace: () -> Unit = {},
    // 终端
    onTerminal: () -> Unit = {},
    // 最近文件
    recentFiles: List<RecentFileEntry> = emptyList(),
    onOpenRecentFile: (RecentFileEntry) -> Unit = {},
    // 模型
    engineStatus: EngineStatus = EngineStatus.Idle,
    modelName: String = "",
    availableModels: List<LocalModelInfo> = emptyList(),
    cloudProfiles: List<CloudModelProfile> = emptyList(),
    activeCloudProfileId: String = "",
    cloudModelEnabled: Boolean = false,
    onScanModels: () -> Unit = {},
    onLoadModel: (String) -> Unit = {},
    onSwitchCloudProfile: (String) -> Unit = {},
    onBrowseModelFile: () -> Unit = {},
) {
    val topBarInsets = WindowInsets(0, 0, 0, 0)
    val screenHeightDp = LocalConfiguration.current.screenHeightDp
    val dropdownMaxHeight = (screenHeightDp * 0.75f).dp

    var editMenuExpanded by remember { mutableStateOf(false) }
    var recentMenuExpanded by remember { mutableStateOf(false) }

    Column {
        TopAppBar(
            title = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "文件",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )

                    Text(
                        text = "丨",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    Box {
                        Text(
                            text = "编辑",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .clickable { editMenuExpanded = true }
                        )

                        DropdownMenu(
                            expanded = editMenuExpanded,
                            onDismissRequest = { editMenuExpanded = false },
                            modifier = Modifier.heightIn(max = dropdownMaxHeight)
                        ) {
                            DropdownMenuItem(
                                text = { Text("撤销") },
                                onClick = { editMenuExpanded = false }
                            )
                            DropdownMenuItem(
                                text = { Text("恢复") },
                                onClick = { editMenuExpanded = false }
                            )
                            DropdownMenuItem(
                                text = { Text("全文复制") },
                                onClick = { editMenuExpanded = false }
                            )
                            DropdownMenuItem(
                                text = { Text("查找替换") },
                                onClick = {
                                    editMenuExpanded = false
                                    onFindReplace()
                                }
                            )
                        }
                    }

                    Text(
                        text = "丨",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    Text(
                        text = "终端",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .clickable { onTerminal() }
                    )

                    Text(
                        text = "丨",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    Box {
                        Text(
                            text = "最近",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .clickable { recentMenuExpanded = true }
                        )

                        DropdownMenu(
                            expanded = recentMenuExpanded,
                            onDismissRequest = { recentMenuExpanded = false },
                            modifier = Modifier.heightIn(max = dropdownMaxHeight)
                        ) {
                            if (recentFiles.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("暂无最近文件") },
                                    onClick = { recentMenuExpanded = false },
                                    enabled = false,
                                )
                            } else {
                                recentFiles.forEach { file ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = file.name,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        },
                                        onClick = {
                                            recentMenuExpanded = false
                                            onOpenRecentFile(file)
                                        },
                                    )
                                }
                            }
                        }
                    }
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

                LayoutModeToggle(
                    isIdeMode = isIdeMode,
                    onToggle = onToggleLayoutMode,
                    modifier = Modifier.padding(start = 8.dp, end = 8.dp)
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
