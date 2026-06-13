package com.medeide.jh.screens.home.landscape.topbar

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.window.core.layout.WindowSizeClass
import com.medeide.jh.R
import com.medeide.jh.data.repository.RecentEntry
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
    // 编辑菜单回调
    onUndo: () -> Unit = {},
    onRedo: () -> Unit = {},
    onCopyAll: () -> Unit = {},
    onFindReplace: () -> Unit = {},
    // 最近打开搜索
    recentItems: List<RecentEntry> = emptyList(),
    onOpenRecentItem: (RecentEntry) -> Unit = {},
) {
    val topBarInsets = if (!windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)) {
        WindowInsets(top = 0)
    } else {
        WindowInsets(0, 0, 0, 0)
    }

    val screenHeightDp = LocalConfiguration.current.screenHeightDp
    val dropdownMaxHeight = (screenHeightDp * 0.75f).dp
    // 搜索下拉列表高度限制为屏幕高度的 50%
    val searchDropdownMaxHeight = (screenHeightDp * 0.5f).dp

    var editMenuExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchDropdownExpanded by remember { mutableStateOf(false) }

    // 搜索过滤结果
    val searchResults = remember(searchQuery, recentItems) {
        if (searchQuery.isBlank()) {
            recentItems
        } else {
            val q = searchQuery.lowercase()
            recentItems.filter {
                it.name.lowercase().contains(q) || it.path.lowercase().contains(q)
            }
        }
    }

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

                    // 编辑下拉菜单
                    Box {
                        Text(
                            text = stringResource(R.string.menu_edit),
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
                                onClick = { editMenuExpanded = false; onUndo() },
                            )
                            DropdownMenuItem(
                                text = { Text("恢复") },
                                onClick = { editMenuExpanded = false; onRedo() },
                            )
                            DropdownMenuItem(
                                text = { Text("全文复制") },
                                onClick = { editMenuExpanded = false; onCopyAll() },
                            )
                            DropdownMenuItem(
                                text = { Text("查找替换") },
                                onClick = { editMenuExpanded = false; onFindReplace() },
                            )
                        }
                    }

                    Text(
                        text = "丨",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    // 搜索输入框
                    Box {
                        Row(
                            modifier = Modifier
                                .width(180.dp)
                                .height(28.dp)
                                .border(
                                    width = 1.dp,
                                    color = if (searchDropdownExpanded) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                                    shape = RoundedCornerShape(4.dp),
                                )
                                .padding(horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "搜索",
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Box(modifier = Modifier.weight(1f)) {
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = "搜索最近打开…",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                BasicTextField(
                                    value = searchQuery,
                                    onValueChange = {
                                        searchQuery = it
                                        // 输入时如果下拉未展开且有结果则展开
                                        if (it.isNotEmpty() && !searchDropdownExpanded) {
                                            searchDropdownExpanded = true
                                        }
                                    },
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    ),
                                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                    keyboardActions = KeyboardActions(
                                        onSearch = {
                                            searchDropdownExpanded = searchResults.isNotEmpty()
                                        }
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }

                        // 搜索下拉列表
                        DropdownMenu(
                            expanded = searchDropdownExpanded,
                            onDismissRequest = {
                                searchDropdownExpanded = false
                            },
                            modifier = Modifier
                                .width(320.dp)
                                .heightIn(max = searchDropdownMaxHeight),
                        ) {
                            // 标题
                            Text(
                                text = "最近打开（${searchResults.size}）",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            )

                            if (searchResults.isEmpty()) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            "无匹配结果",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                        )
                                    },
                                    onClick = { searchDropdownExpanded = false },
                                    enabled = false,
                                )
                            } else {
                                searchResults.forEach { entry ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(
                                                    text = entry.name,
                                                    style = MaterialTheme.typography.bodySmall.copy(
                                                        fontWeight = FontWeight.Medium,
                                                    ),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                                Text(
                                                    text = entry.path,
                                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                        },
                                        onClick = {
                                            searchDropdownExpanded = false
                                            searchQuery = ""
                                            onOpenRecentItem(entry)
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
