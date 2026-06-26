package com.medeide.jh.screens.home.filebrowser

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.medeide.jh.screens.home.filebrowser.components.FileContextMenu
import com.medeide.jh.screens.home.filebrowser.components.FileListRow
import com.medeide.jh.screens.home.filebrowser.components.NormalToolbar
import com.medeide.jh.screens.home.filebrowser.components.SelectionToolbar
import com.medeide.jh.screens.home.filebrowser.dialogs.CreateDialog
import com.medeide.jh.screens.home.filebrowser.dialogs.DeleteConfirmDialog
import com.medeide.jh.screens.home.filebrowser.dialogs.InfoDialog
import com.medeide.jh.screens.home.filebrowser.dialogs.RenameDialog
import com.medeide.jh.screens.home.filebrowser.logic.ConflictMode
import com.medeide.jh.screens.home.filebrowser.logic.FileEntry
import com.medeide.jh.screens.home.filebrowser.logic.compressItemsAsync
import com.medeide.jh.screens.home.filebrowser.logic.copyToClipboard
import com.medeide.jh.screens.home.filebrowser.logic.createItem
import com.medeide.jh.screens.home.filebrowser.logic.deleteItems
import com.medeide.jh.screens.home.filebrowser.logic.extractArchiveEntryToAsync
import com.medeide.jh.screens.home.filebrowser.logic.extractToAsync
import com.medeide.jh.screens.home.filebrowser.logic.renameItem
import com.medeide.jh.screens.home.filebrowser.logic.toFile
import com.medeide.jh.screens.home.filebrowser.logic.toRealFiles
import com.medeide.jh.screens.home.landscape.workspace.model.isArchiveFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun FileBrowserPanel(
    rootPath: String,
    holder: FileBrowserStateHolder,
    panelSide: PanelSide,
    isActive: Boolean,
    onActivate: () -> Unit,
    onSync: () -> Unit,
    onOpenFile: (String) -> Unit,
    onCopyToOtherSide: (List<FileEntry>) -> Unit,
    onMoveToOtherSide: (List<FileEntry>) -> Unit,
    onFilesDeleted: (Set<String>) -> Unit = {},
    onExtractToLeft: (List<FileEntry>) -> Unit = {},
    onExtractToRight: (List<FileEntry>) -> Unit = {},
    onAddToConversation: (String) -> Unit = {},
    onOpenAsProject: (String) -> Unit = {},
    onExitProjectMode: () -> Unit = {},
    isProjectModeActive: Boolean = false,
    singleMode: Boolean = false,
    showToolbar: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 进度状态
    var showProgress by remember { mutableStateOf(false) }
    var progressMsg by remember { mutableStateOf("") }
    var progressCurrent by remember { mutableIntStateOf(0) }
    var progressTotal by remember { mutableIntStateOf(0) }
    val animatedProgress by animateFloatAsState(
        targetValue = if (progressTotal > 0) progressCurrent.toFloat() / progressTotal else 0f,
        label = "progress",
    )

    // 监听当前目录文件变更
    DisposableEffect(holder.currentPath) {
        holder.startWatching()
        onDispose { holder.stopWatching() }
    }

    // 点击面板激活
    val currentState = holder.snapshot()

    BackHandler(enabled = currentState.isSelectionMode || holder.currentPath != rootPath) {
        when {
            currentState.isSelectionMode -> holder.cancelSelection()
            holder.currentPath != rootPath -> holder.navigateUp()
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // 路径栏
        PathBar(
            displayPath = currentState.displayPath,
            canGoUp = holder.currentPath != rootPath,
            isActive = isActive,
            onNavigateUp = {
                onActivate()
                holder.navigateUp()
            },
            onActivate = onActivate,
        )

        HorizontalDivider(
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        )

        // ── 进度条 ──
        if (showProgress) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            ) {
                Text(
                    text = progressMsg,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                )
            }
        }

        // 文件列表
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.changes.any { it.pressed }) {
                                onActivate()
                            }
                        }
                    }
                }
                .then(
                    if (isActive) Modifier.background(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.10f)
                    ) else Modifier
                ),
        ) {
            // 活动列左边框色条
            if (isActive) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(3.dp)
                        .background(MaterialTheme.colorScheme.primary)
                        .align(Alignment.CenterStart),
                )
            }
            val listState = remember(currentState.currentPath) { LazyListState() }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
            ) {
                if (currentState.files.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "空文件夹",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    items(currentState.files, key = { it.absolutePath }) { file ->
                        val isArchiveEntry = file is FileEntry.Archive
                        FileListRow(
                            file = file,
                            isSelected = file.absolutePath in currentState.selectedItems,
                            isSelectionMode = currentState.isSelectionMode,
                            onClick = {
                                onActivate()
                                if (currentState.isSelectionMode) {
                                    holder.toggleSelect(file)
                                } else if (file.isDirectory) {
                                    if (isArchiveEntry) {
                                        holder.navigateArchiveTo(file.name)
                                    } else {
                                        holder.navigateTo(file.absolutePath)
                                    }
                                } else if (isArchiveEntry) {
                                    Toast.makeText(context, "不支持直接打开压缩包内文件", Toast.LENGTH_SHORT).show()
                                } else if (isArchiveFile(file.name)) {
                                    holder.navigateTo(file.absolutePath)
                                } else {
                                    onOpenFile(file.absolutePath)
                                }
                            },
                            onSwipe = {
                                onActivate()
                                holder.toggleSelect(file, isSwipe = true)
                            },
                            onLongClick = {
                                onActivate()
                                holder.showMenu(file)
                            },
                        )
                    }
                }
            }
        }

        if (showToolbar) {
            HorizontalDivider(
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            )

            if (currentState.isSelectionMode) {
                SelectionToolbar(
                    onSelectAll = { holder.selectAll(currentState.files.map { it.absolutePath }) },
                    onInvert = { holder.invertSelection(currentState.files.map { it.absolutePath }) },
                    onCancel = { holder.cancelSelection() },
                )
            } else {
                NormalToolbar(
                    canGoUp = currentState.canGoUp,
                    canGoForward = currentState.canGoForward,
                    onBack = { onActivate(); holder.navigateUp() },
                    onForward = { onActivate(); holder.navigateForward() },
                    onCreate = { onActivate(); holder.requestCreate() },
                    onSync = { onActivate(); onSync() },
                    onReset = { onActivate(); holder.navigateTo(rootPath) },
                    showSync = !singleMode,
                )
            }
        }
    }

    // 长按菜单
    currentState.menuTarget?.let { target ->
        val isInsideArchive = holder.isInArchiveMode
        val isRealArchive = !isInsideArchive && isArchiveFile(target.name)
        val targetsForAction = if (currentState.selectedItems.isNotEmpty()) {
            currentState.files.filter { it.absolutePath in currentState.selectedItems }
        } else listOf(target)

        FileContextMenu(
            expanded = true,
            panelSide = panelSide,
            isArchive = isRealArchive || isInsideArchive,
            isInsideArchive = isInsideArchive,
            isDirectory = target.isDirectory,
            isProjectModeActive = isProjectModeActive,
            selectedCount = currentState.selectedItems.size,
            onDismiss = { holder.dismissMenu() },
            onAddToConversation = { onAddToConversation(target.absolutePath) },
            onOpenAsProject = { onOpenAsProject(target.absolutePath) },
            onExitProjectMode = { onExitProjectMode() },
            onCopyName = { copyToClipboard(context, "fileName", target.name) },
            onCopyPath = { copyToClipboard(context, "path", target.absolutePath) },
            onRename = {
                if (!isInsideArchive) {
                    holder.dismissMenu()
                    target.toFile()?.let { holder.requestRename(target) }
                }
            },
            onCompress = {
                if (!isInsideArchive) {
                    holder.dismissMenu()
                    val realFiles = targetsForAction.toRealFiles()
                    if (realFiles.isEmpty()) return@FileContextMenu
                    scope.launch {
                        showProgress = true
                        progressMsg = "压缩中…"
                        compressItemsAsync(rootPath, realFiles,
                            onProgress = { cur, tot ->
                                progressCurrent = cur; progressTotal = tot
                                progressMsg = "压缩中 $cur/$tot"
                            },
                        )
                            .onSuccess { name ->
                                showProgress = false
                                holder.cancelSelection()
                                Toast.makeText(context, "已压缩到 $name", Toast.LENGTH_SHORT).show()
                            }
                            .onFailure { e ->
                                showProgress = false
                                Toast.makeText(context, "压缩失败: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        holder.refresh()
                    }
                }
            },
            onExtract = {
                holder.dismissMenu()
                scope.launch {
                    showProgress = true
                    val destDir = holder.currentPath
                    if (isInsideArchive && target is FileEntry.Archive) {
                        progressMsg = "解压到当前目录…"
                        extractArchiveEntryToAsync(
                            File(target.realArchivePath), target.entryFullPath, destDir,
                            conflictMode = ConflictMode.Rename,
                            onProgress = { cur, tot ->
                                progressCurrent = cur; progressTotal = tot
                                progressMsg = "解压中 $cur/$tot"
                            },
                        )
                            .onSuccess { n ->
                                showProgress = false
                                Toast.makeText(context, "已解压 $n 项到当前目录", Toast.LENGTH_SHORT).show()
                            }
                            .onFailure { e ->
                                showProgress = false
                                Toast.makeText(context, "解压失败: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                    } else {
                        val realFile = target.toFile() ?: return@launch
                        progressMsg = "解压到当前目录…"
                        extractToAsync(
                            destDir, realFile,
                            conflictMode = ConflictMode.Rename,
                            onProgress = { cur, tot ->
                                progressCurrent = cur; progressTotal = tot
                                progressMsg = "解压中 $cur/$tot"
                            },
                        )
                            .onSuccess { n ->
                                showProgress = false
                                Toast.makeText(context, "已解压 $n 项到当前目录", Toast.LENGTH_SHORT).show()
                            }
                            .onFailure { e ->
                                showProgress = false
                                Toast.makeText(context, "解压失败: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                    }
                    holder.refresh()
                }
            },
            onExtractToLeft = {
                holder.dismissMenu()
                onExtractToLeft(targetsForAction)
            },
            onExtractToRight = {
                holder.dismissMenu()
                onExtractToRight(targetsForAction)
            },
            onInfo = { holder.dismissMenu(); holder.requestInfo(target) },
            onDelete = {
                if (!isInsideArchive) {
                    holder.dismissMenu()
                    holder.requestDelete(targetsForAction)
                }
            },
            onMoveToOtherSide = {
                holder.dismissMenu()
                onMoveToOtherSide(targetsForAction)
            },
            onCopyToOtherSide = {
                holder.dismissMenu()
                onCopyToOtherSide(targetsForAction)
            },
            showCrossPanelActions = !singleMode,
        )
    }

    // 对话框
    if (currentState.showCreateDialog) {
        CreateDialog(
            onDismiss = { holder.dismissCreate() },
            onConfirm = { name, isDir ->
                createItem(holder.currentPath, name, isDir)
                    .onSuccess { holder.refresh() }
                    .onFailure { Toast.makeText(context, "创建失败: ${it.message}", Toast.LENGTH_SHORT).show() }
            },
        )
    }

    currentState.showRenameDialog?.let { file ->
        RenameDialog(
            oldName = file.name,
            onDismiss = { holder.dismissRename() },
            onConfirm = { newName ->
                val realFile = file.toFile()
                if (realFile != null) {
                    renameItem(realFile, newName)
                        .onSuccess { holder.refresh() }
                        .onFailure { Toast.makeText(context, "重命名失败: ${it.message}", Toast.LENGTH_SHORT).show() }
                }
            },
        )
    }

    currentState.showInfoDialog?.let { file ->
        InfoDialog(file = file, onDismiss = { holder.dismissInfo() })
    }

    currentState.showDeleteDialog?.let { targets ->
        DeleteConfirmDialog(
            count = targets.size,
            onDismiss = { holder.dismissDelete() },
            onConfirm = {
                val realFiles = targets.toRealFiles()
                if (realFiles.isNotEmpty()) {
                    holder.dismissDelete()
                    scope.launch {
                        showProgress = true
                        progressMsg = "删除中…"
                        val result = withContext(Dispatchers.IO) {
                            deleteItems(realFiles)
                        }
                        showProgress = false
                        result
                            .onSuccess {
                                holder.cancelSelection()
                                holder.refresh()
                                onFilesDeleted(realFiles.map { it.absolutePath }.toSet())
                            }
                            .onFailure { e ->
                                Toast.makeText(context, "删除失败: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                    }
                }
            },
        )
    }
}

@Composable
private fun PathBar(
    displayPath: String,
    canGoUp: Boolean,
    isActive: Boolean,
    onNavigateUp: () -> Unit,
    onActivate: () -> Unit,
) {
    val bgColor = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
    else Color.Transparent
    val hasUpButton = canGoUp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
            .padding(horizontal = 12.dp, vertical = if (hasUpButton) 4.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (hasUpButton) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回上级",
                modifier = Modifier
                    .size(18.dp)
                    .clickable(onClick = onNavigateUp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = displayPath,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}
