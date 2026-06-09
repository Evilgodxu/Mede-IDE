package com.template.jh.screens.home.components

import android.content.Context
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass
import com.template.jh.R
import com.template.jh.core.ai.CloudModelProfile
import com.template.jh.core.ai.EngineStatus
import com.template.jh.core.ai.ModelInfo
import com.template.jh.data.repository.RecentEntry

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
    onOpenFolder: () -> Unit = {},
    recentFiles: List<RecentEntry> = emptyList(),
    recentFolders: List<RecentEntry> = emptyList(),
    onOpenRecentFile: (String) -> Unit = {},
    onOpenRecentFolder: (String) -> Unit = {},
    onSaveAll: () -> Unit = {},
    // 音频播放
    audioPlaybackState: AudioPlaybackState? = null,
    scannedAudioTracks: List<AudioTrack> = emptyList(),
    onScanMusic: () -> Unit = {},
    onPlayAudioTrack: (AudioTrack) -> Unit = {},
    onStopAudio: () -> Unit = {},
) {
    val topBarInsets = if (!windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)) {
        WindowInsets.statusBars
    } else {
        WindowInsets(0, 0, 0, 0)
    }

    var fileMenuExpanded by remember { mutableStateOf(false) }
    var editMenuExpanded by remember { mutableStateOf(false) }
    var modelMenuExpanded by remember { mutableStateOf(false) }
    var musicMenuExpanded by remember { mutableStateOf(false) }
    var searchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchDropdownExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val screenHeightDp = LocalConfiguration.current.screenHeightDp
    val dropdownMaxHeight = (screenHeightDp * 0.75f).dp

    Column {
        TopAppBar(
            title = {
                Row(
                    modifier = Modifier.fillMaxHeight(),
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
                            // 关闭文件夹
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.file_menu_close_folder)) },
                                onClick = {
                                    try { fileMenuExpanded = false; onCloseFolder() }
                                    catch (e: Exception) { Log.e("MainTopBar", "close folder failed", e) }
                                }
                            )
                            HorizontalDivider()
                            // 打开文件
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.file_menu_open_file)) },
                                onClick = {
                                    try { fileMenuExpanded = false; onOpenFile() }
                                    catch (e: Exception) { Log.e("MainTopBar", "open file failed", e) }
                                }
                            )
                            // 打开文件夹
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.file_menu_open_folder_btn)) },
                                onClick = {
                                    try { fileMenuExpanded = false; onOpenFolder() }
                                    catch (e: Exception) { Log.e("MainTopBar", "open folder failed", e) }
                                }
                            )
                            HorizontalDivider()
                            // 全部保存
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.file_menu_save_all)) },
                                onClick = {
                                    try { fileMenuExpanded = false; onSaveAll() }
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
                            // 撤销
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.edit_menu_undo)) },
                                onClick = {
                                    try {
                                        // 撤销逻辑
                                        editMenuExpanded = false
                                    } catch (e: Exception) {
                                        Log.e("MainTopBar", "undo failed", e)
                                    }
                                }
                            )
                            // 恢复
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.edit_menu_redo)) },
                                onClick = {
                                    try {
                                        // 恢复逻辑
                                        editMenuExpanded = false
                                    } catch (e: Exception) {
                                        Log.e("MainTopBar", "redo failed", e)
                                    }
                                }
                            )
                            HorizontalDivider()
                            // 在文件中查找
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.edit_menu_find_in_files)) },
                                onClick = {
                                    try {
                                        // 在文件中查找逻辑
                                        editMenuExpanded = false
                                    } catch (e: Exception) {
                                        Log.e("MainTopBar", "find in files failed", e)
                                    }
                                }
                            )
                            // 在文件中替换
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.edit_menu_replace_in_files)) },
                                onClick = {
                                    try {
                                        // 在文件中替换逻辑
                                        editMenuExpanded = false
                                    } catch (e: Exception) {
                                        Log.e("MainTopBar", "replace in files failed", e)
                                    }
                                }
                            )
                        }
                    }

                    Text(
                        text = "丨",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    // TODO: 终端功能待实现
                    Text(
                        text = stringResource(R.string.menu_terminal),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )

                    Text(
                        text = "丨",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    // 搜索按钮 / 搜索输入框
                    Box {
                        if (searchActive) {
                            TextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it; searchDropdownExpanded = true },
                                placeholder = { Text("搜索最近文件...", style = MaterialTheme.typography.bodySmall) },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface),
                                modifier = Modifier
                                    .width(180.dp)
                                    .height(32.dp)
                                    .padding(0.dp),
                                colors = TextFieldDefaults.colors(
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    cursorColor = MaterialTheme.colorScheme.primary,
                                ),
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "关闭搜索",
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clickable {
                                                searchActive = false
                                                searchQuery = ""
                                                searchDropdownExpanded = false
                                            },
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                },
                            )
                        } else {
                            IconButton(
                                onClick = { searchActive = true; searchDropdownExpanded = true },
                                modifier = Modifier.size(28.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "搜索最近文件",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        // 搜索下拉结果
                        DropdownMenu(
                            expanded = searchDropdownExpanded,
                            onDismissRequest = { searchDropdownExpanded = false },
                            modifier = Modifier.heightIn(max = dropdownMaxHeight).widthIn(min = 220.dp),
                        ) {
                            val allRecent = recentFolders.map { it to "目录" } + recentFiles.map { it to "文件" }
                            val filtered = if (searchQuery.isBlank()) allRecent
                            else allRecent.filter { (e, _) ->
                                e.name.contains(searchQuery, ignoreCase = true) ||
                                e.path.contains(searchQuery, ignoreCase = true)
                            }

                            if (filtered.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text(if (searchQuery.isBlank()) stringResource(R.string.recent_files_empty) else "无匹配结果") },
                                    onClick = { searchDropdownExpanded = false },
                                    enabled = false,
                                )
                            } else {
                                filtered.take(10).forEach { (entry, type) ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(
                                                    text = entry.name,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                                Text(
                                                    text = entry.path,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                        },
                                        onClick = {
                                            searchDropdownExpanded = false
                                            searchQuery = ""
                                            searchActive = false
                                            if (type == "目录") onOpenRecentFolder(entry.path)
                                            else onOpenRecentFile(entry.path)
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
                    val aps = audioPlaybackState
                    // 音乐按钮 + 下拉列表
                    Box {
                        val hasAudio = aps.currentAudioPath.isNotBlank()
                        val displayText = when {
                            hasAudio && aps.lyrics.isNotEmpty() && aps.currentLyricIndex in aps.lyrics.indices ->
                                aps.lyrics[aps.currentLyricIndex].text
                            hasAudio && aps.currentSongName.isNotBlank() -> aps.currentSongName
                            else -> null
                        }

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
                            modifier = Modifier.widthIn(min = 200.dp, max = 300.dp).heightIn(max = 320.dp)
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

                    // 播放控制按钮
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(0.dp),
                    ) {
                        val hasAudio = aps.currentAudioPath.isNotBlank()
                        // 上一曲
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
                        // 播放/暂停
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
                        // 下一曲
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
                        // 关闭
                        IconButton(
                            onClick = { onStopAudio() },
                            modifier = Modifier.size(26.dp),
                            enabled = hasAudio,
                        ) { Icon(Icons.Default.Close, null, Modifier.size(14.dp),
                            tint = if (hasAudio) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)) }
                    }

                    Text(
                        text = "丨",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }

                // 模型状态指示器 + 下拉
                Box {
                    // 计算当前活跃模型信息
                    val isCloudActive = cloudModelEnabled && activeCloudProfileId.isNotBlank()
                    val activeCloudProfile = if (isCloudActive) cloudProfiles.find { it.id == activeCloudProfileId } else null
                    val activeModelLabel = when {
                        isCloudActive && activeCloudProfile != null -> activeCloudProfile.name.ifEmpty { activeCloudProfile.modelName }
                        modelName.isNotBlank() -> modelName.take(16)
                        else -> stringResource(R.string.model_no_model)
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable { modelMenuExpanded = true; onScanModels() }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        if (isCloudActive) {
                            // 云端模式：固定绿色圆点
                            Box(Modifier.size(6.dp).clip(CircleShape).background(androidx.compose.ui.graphics.Color(0xFF4CAF50)))
                        } else {
                            ModelStatusDot(engineStatus)
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = activeModelLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    DropdownMenu(
                        expanded = modelMenuExpanded,
                        onDismissRequest = { modelMenuExpanded = false },
                        modifier = Modifier.widthIn(min = 220.dp).heightIn(max = dropdownMaxHeight)
                    ) {
                        // 头部：当前模型状态
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                    if (isCloudActive) {
                                        Box(Modifier.size(8.dp).clip(CircleShape).background(androidx.compose.ui.graphics.Color(0xFF4CAF50)))
                                    } else {
                                        ModelStatusDot(engineStatus)
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = activeModelLabel,
                                            style = MaterialTheme.typography.labelMedium,
                                        )
                                        Text(
                                            text = when {
                                                isCloudActive -> "云端 · ${activeCloudProfile?.modelName ?: ""}"
                                                else -> modelStatusText(engineStatus)
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isCloudActive) androidx.compose.ui.graphics.Color(0xFF4CAF50) else modelStatusColor(engineStatus),
                                        )
                                    }
                                }
                            },
                            onClick = { },
                            enabled = false,
                        )
                        HorizontalDivider()
                        // 操作按钮
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.FolderOpen, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.model_browse_file_btn), style = MaterialTheme.typography.labelMedium)
                                }
                            },
                            onClick = {
                                modelMenuExpanded = false
                                onBrowseModelFile()
                            },
                        )
                        // 可用本地模型列表
                        if (availableModels.isNotEmpty()) {
                            HorizontalDivider()
                            Text("本地模型", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                            availableModels.forEach { model ->
                                val isActive = !isCloudActive && modelName == model.name
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = model.name,
                                            style = MaterialTheme.typography.labelMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    },
                                    onClick = {
                                        modelMenuExpanded = false
                                        onLoadModel(model.path)
                                    },
                                    trailingIcon = {
                                        if (isActive) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp),
                                                tint = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                    },
                                )
                            }
                        } else if (engineStatus != EngineStatus.Loading) {
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        stringResource(R.string.model_no_models_found),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                },
                                onClick = { },
                                enabled = false,
                            )
                        }
                        // 云端模型配置列表
                        if (cloudProfiles.isNotEmpty()) {
                            HorizontalDivider()
                            Text("云端模型", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                            cloudProfiles.forEach { profile ->
                                val isActiveCloudItem = isCloudActive && profile.id == activeCloudProfileId
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(
                                                text = profile.name.ifEmpty { profile.modelName },
                                                style = MaterialTheme.typography.labelMedium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Text(
                                                text = profile.modelName,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    },
                                    onClick = {
                                        modelMenuExpanded = false
                                        onSwitchCloudProfile(profile.id)
                                    },
                                    trailingIcon = {
                                        if (isActiveCloudItem) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp),
                                                tint = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
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

// 模型状态文字
private fun modelStatusText(status: EngineStatus): String = when (status) {
    EngineStatus.Idle -> "未加载"
    EngineStatus.Loading -> "加载中…"
    EngineStatus.Ready -> "就绪"
    EngineStatus.Error -> "错误"
}

// 模型状态颜色
@Composable
private fun modelStatusColor(status: EngineStatus): androidx.compose.ui.graphics.Color = when (status) {
    EngineStatus.Ready -> androidx.compose.ui.graphics.Color(0xFF4CAF50)
    EngineStatus.Loading -> MaterialTheme.colorScheme.primary
    EngineStatus.Error -> MaterialTheme.colorScheme.error
    EngineStatus.Idle -> MaterialTheme.colorScheme.onSurfaceVariant
}

// 模型状态圆点
@Composable
private fun ModelStatusDot(status: EngineStatus) {
    when (status) {
        EngineStatus.Ready ->
            Box(Modifier.size(6.dp).clip(CircleShape).background(androidx.compose.ui.graphics.Color(0xFF4CAF50)))
        EngineStatus.Loading ->
            CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 1.5.dp)
        EngineStatus.Error ->
            Box(Modifier.size(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.error))
        EngineStatus.Idle ->
            Box(Modifier.size(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurfaceVariant))
    }
}


