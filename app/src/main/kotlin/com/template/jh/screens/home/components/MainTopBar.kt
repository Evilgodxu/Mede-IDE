package com.template.jh.screens.home.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass
import com.template.jh.R
import com.template.jh.core.ai.EngineStatus
import com.template.jh.core.ai.ModelInfo

// 主窗口标题栏组件
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainTopBar(
    windowSizeClass: WindowSizeClass,
    engineStatus: EngineStatus = EngineStatus.Idle,
    modelName: String = "",
    availableModels: List<ModelInfo> = emptyList(),
    onScanModels: () -> Unit = {},
    onLoadModel: (String) -> Unit = {},
    onBrowseModelFile: () -> Unit = {},
    onTerminalClick: () -> Unit = {},
) {
    val topBarInsets = if (!windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)) {
        WindowInsets.statusBars
    } else {
        WindowInsets(0, 0, 0, 0)
    }

    var fileMenuExpanded by remember { mutableStateOf(false) }
    var editMenuExpanded by remember { mutableStateOf(false) }
    var modelMenuExpanded by remember { mutableStateOf(false) }
    var autoSaveEnabled by remember { mutableStateOf(false) }
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
                            // 新建文件
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.file_menu_new_file)) },
                                onClick = {
                                    try {
                                        // 新建文件逻辑
                                        fileMenuExpanded = false
                                    } catch (e: Exception) {
                                        copyCrashToClipboard(context, e)
                                    }
                                }
                            )
                            // 打开文件
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.file_menu_open_file)) },
                                onClick = {
                                    try {
                                        // 打开文件逻辑
                                        fileMenuExpanded = false
                                    } catch (e: Exception) {
                                        copyCrashToClipboard(context, e)
                                    }
                                }
                            )
                            // 最近文件
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.file_menu_recent_files)) },
                                onClick = {
                                    try {
                                        // 最近文件逻辑
                                        fileMenuExpanded = false
                                    } catch (e: Exception) {
                                        copyCrashToClipboard(context, e)
                                    }
                                }
                            )
                            // 分隔线
                            HorizontalDivider()
                            // 保存
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.file_menu_save)) },
                                onClick = {
                                    try {
                                        // 保存逻辑
                                        fileMenuExpanded = false
                                    } catch (e: Exception) {
                                        copyCrashToClipboard(context, e)
                                    }
                                }
                            )
                            // 另存为
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.file_menu_save_as)) },
                                onClick = {
                                    try {
                                        // 另存为逻辑
                                        fileMenuExpanded = false
                                    } catch (e: Exception) {
                                        copyCrashToClipboard(context, e)
                                    }
                                }
                            )
                            // 全部保存
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.file_menu_save_all)) },
                                onClick = {
                                    try {
                                        // 全部保存逻辑
                                        fileMenuExpanded = false
                                    } catch (e: Exception) {
                                        copyCrashToClipboard(context, e)
                                    }
                                }
                            )
                            // 自动保存
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.file_menu_auto_save)) },
                                trailingIcon = {
                                    if (autoSaveEnabled) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                },
                                onClick = {
                                    try {
                                        autoSaveEnabled = !autoSaveEnabled
                                    } catch (e: Exception) {
                                        copyCrashToClipboard(context, e)
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
                                        copyCrashToClipboard(context, e)
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
                                        copyCrashToClipboard(context, e)
                                    }
                                }
                            )
                            HorizontalDivider()
                            // 剪切
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.edit_menu_cut)) },
                                onClick = {
                                    try {
                                        // 剪切逻辑
                                        editMenuExpanded = false
                                    } catch (e: Exception) {
                                        copyCrashToClipboard(context, e)
                                    }
                                }
                            )
                            // 复制
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.edit_menu_copy)) },
                                onClick = {
                                    try {
                                        // 复制逻辑
                                        editMenuExpanded = false
                                    } catch (e: Exception) {
                                        copyCrashToClipboard(context, e)
                                    }
                                }
                            )
                            // 粘贴
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.edit_menu_paste)) },
                                onClick = {
                                    try {
                                        // 粘贴逻辑
                                        editMenuExpanded = false
                                    } catch (e: Exception) {
                                        copyCrashToClipboard(context, e)
                                    }
                                }
                            )
                            HorizontalDivider()
                            // 查找
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.edit_menu_find)) },
                                onClick = {
                                    try {
                                        // 查找逻辑
                                        editMenuExpanded = false
                                    } catch (e: Exception) {
                                        copyCrashToClipboard(context, e)
                                    }
                                }
                            )
                            // 替换
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.edit_menu_replace)) },
                                onClick = {
                                    try {
                                        // 替换逻辑
                                        editMenuExpanded = false
                                    } catch (e: Exception) {
                                        copyCrashToClipboard(context, e)
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
                                        copyCrashToClipboard(context, e)
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
                                        copyCrashToClipboard(context, e)
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

                    // 终端
                    Text(
                        text = stringResource(R.string.menu_terminal),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier
                            .clickable { onTerminalClick() }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )

                    Text(
                        text = "丨",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    // 搜索按钮
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable { /* 搜索功能 */ }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = stringResource(R.string.global_search_search_hint),
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.global_search_search_hint),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            actions = {
                // 模型状态指示器 + 下拉
                Box {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable { modelMenuExpanded = true }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        ModelStatusDot(engineStatus)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (modelName.isNotBlank()) modelName.take(16)
                                else stringResource(R.string.model_no_model),
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
                        // 头部：状态 + 模型名
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                    ModelStatusDot(engineStatus)
                                    Spacer(Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = if (modelName.isNotBlank()) modelName
                                                else stringResource(R.string.model_no_model),
                                            style = MaterialTheme.typography.labelMedium,
                                        )
                                        Text(
                                            text = modelStatusText(engineStatus),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = modelStatusColor(engineStatus),
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
                                    Icon(Icons.Default.Refresh, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.model_scan_device), style = MaterialTheme.typography.labelMedium)
                                }
                            },
                            onClick = {
                                modelMenuExpanded = false
                                onScanModels()
                            },
                        )
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
                        // 可用模型列表
                        if (availableModels.isNotEmpty()) {
                            HorizontalDivider()
                            availableModels.forEach { model ->
                                val isActive = modelName == model.name || (modelName.isBlank() && engineStatus == EngineStatus.Loading)
                                DropdownMenuItem(
                                    text = {
                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            Text(
                                                text = model.name,
                                                style = MaterialTheme.typography.labelMedium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Text(
                                                text = model.sizeText,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
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

// 崩溃异常捕获并复制到剪贴板
private fun copyCrashToClipboard(context: Context, e: Exception) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val crashInfo = "${e.javaClass.simpleName}: ${e.message}\n${e.stackTraceToString()}"
    val clip = ClipData.newPlainText("崩溃信息", crashInfo)
    clipboard.setPrimaryClip(clip)
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


