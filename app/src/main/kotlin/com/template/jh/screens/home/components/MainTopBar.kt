package com.template.jh.screens.home.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass
import com.template.jh.R

// 主窗口标题栏组件
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainTopBar(
    windowSizeClass: WindowSizeClass,
) {
    val topBarInsets = if (!windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)) {
        WindowInsets.statusBars
    } else {
        WindowInsets(0, 0, 0, 0)
    }

    var fileMenuExpanded by remember { mutableStateOf(false) }
    var editMenuExpanded by remember { mutableStateOf(false) }
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
                            .clickable { }
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
                            contentDescription = "搜索",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "搜索",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            actions = {},
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


