package com.medeide.jh.screens.home.components

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass
import com.medeide.jh.R
import com.medeide.jh.model.chat.CloudModelProfile
import com.medeide.jh.model.chat.EngineStatus
import com.medeide.jh.model.chat.ModelInfo
import com.medeide.jh.data.repository.RecentEntry
import com.medeide.jh.screens.home.components.audio.AudioControl
import com.medeide.jh.screens.home.components.audio.AudioPlaybackState
import com.medeide.jh.screens.home.components.search.SearchBar
import com.medeide.jh.screens.home.components.audio.AudioTrack

// 主窗口顶部工具栏组件
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
    onCloseFolder: () -> Unit = {},
    onOpenFile: () -> Unit = {},
    recentFiles: List<RecentEntry> = emptyList(),
    recentFolders: List<RecentEntry> = emptyList(),
    onOpenRecentFile: (String) -> Unit = {},
    onOpenRecentFolder: (String) -> Unit = {},
    onSaveAll: () -> Unit = {},
    projectDirPath: String = "",
    // 终端
    isTerminalTabOpen: Boolean = false,
    onToggleTerminal: () -> Unit = {},
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

    var fileMenuExpanded by remember { mutableStateOf(false) }
    var editMenuExpanded by remember { mutableStateOf(false) }
    val screenHeightDp = LocalConfiguration.current.screenHeightDp
    val dropdownMaxHeight = (screenHeightDp * 0.75f).dp

    Column {
        TopAppBar(
            title = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 文件
                    Box {
                        Text(
                            text = stringResource(R.string.menu_file),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier
                                .clickable { fileMenuExpanded = true }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )

                        DropdownMenu(
                            expanded = fileMenuExpanded,
                            onDismissRequest = { fileMenuExpanded = false },
                            modifier = Modifier.heightIn(max = dropdownMaxHeight)
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.file_menu_close_folder)) },
                                onClick = {
                                    fileMenuExpanded = false
                                    try { onCloseFolder() }
                                    catch (e: Exception) { Log.e("MainTopBar", "close folder failed", e) }
                                }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.file_menu_open_file)) },
                                onClick = {
                                    fileMenuExpanded = false
                                    try { onOpenFile() }
                                    catch (e: Exception) { Log.e("MainTopBar", "open file failed", e) }
                                }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.file_menu_save_all)) },
                                onClick = {
                                    fileMenuExpanded = false
                                    try { onSaveAll() }
                                    catch (e: Exception) { Log.e("MainTopBar", "save all failed", e) }
                                }
                            )
                        }
                    }

                    Text(
                        text = "丨",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    // 编辑
                    Box {
                        Text(
                            text = stringResource(R.string.menu_edit),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier
                                .clickable { editMenuExpanded = true }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )

                        DropdownMenu(
                            expanded = editMenuExpanded,
                            onDismissRequest = { editMenuExpanded = false },
                            modifier = Modifier.heightIn(max = dropdownMaxHeight)
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.edit_menu_undo)) },
                                onClick = { editMenuExpanded = false }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.edit_menu_redo)) },
                                onClick = { editMenuExpanded = false }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.edit_menu_find_in_files)) },
                                onClick = { editMenuExpanded = false }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.edit_menu_replace_in_files)) },
                                onClick = { editMenuExpanded = false }
                            )
                        }
                    }

                    Text(
                        text = "丨",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    // 终端按钮
                    Box {
                        Text(
                            text = stringResource(R.string.menu_terminal),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (isTerminalTabOpen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier
                                .clickable { onToggleTerminal() }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    Text(
                        text = "丨",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    // 搜索
                    SearchBar(
                        recentFiles = recentFiles,
                        recentFolders = recentFolders,
                        onOpenRecentFile = onOpenRecentFile,
                        onOpenRecentFolder = onOpenRecentFolder,
                        dropdownMaxHeight = dropdownMaxHeight,
                        projectDirPath = projectDirPath,
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
