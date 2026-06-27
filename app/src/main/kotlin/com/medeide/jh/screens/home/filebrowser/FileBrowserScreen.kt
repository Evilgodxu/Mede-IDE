package com.medeide.jh.screens.home.filebrowser

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.medeide.jh.screens.home.filebrowser.components.NormalToolbar
import com.medeide.jh.screens.home.filebrowser.components.SelectionToolbar
import com.medeide.jh.screens.home.filebrowser.logic.ConflictMode
import com.medeide.jh.screens.home.filebrowser.logic.FileEntry
import com.medeide.jh.screens.home.filebrowser.logic.extractArchiveEntryToAsync
import com.medeide.jh.screens.home.filebrowser.logic.extractToAsync
import com.medeide.jh.screens.home.filebrowser.logic.toFile
import com.medeide.jh.core.utils.isArchiveFile
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun FileBrowserScreen(
    rootPath: String,
    columnCount: Int,
    onOpenFile: (String) -> Unit,
    onFilesDeleted: (Set<String>) -> Unit = {},
    onAddToConversation: (String) -> Unit = {},
    onOpenAsProject: (String) -> Unit = {},
    onExitProjectMode: () -> Unit = {},
    isProjectModeActive: Boolean = false,
    modifier: Modifier = Modifier,
) {
    if (rootPath.isBlank()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "未选择路径",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    if (columnCount <= 1) {
        SingleFileBrowser(
            rootPath = rootPath,
            onOpenFile = onOpenFile,
            onFilesDeleted = onFilesDeleted,
            onAddToConversation = onAddToConversation,
            onOpenAsProject = onOpenAsProject,
            onExitProjectMode = onExitProjectMode,
            isProjectModeActive = isProjectModeActive,
            modifier = modifier,
        )
    } else {
        DualFileBrowser(
            rootPath = rootPath,
            onOpenFile = onOpenFile,
            onFilesDeleted = onFilesDeleted,
            onAddToConversation = onAddToConversation,
            onOpenAsProject = onOpenAsProject,
            onExitProjectMode = onExitProjectMode,
            isProjectModeActive = isProjectModeActive,
            modifier = modifier,
        )
    }
}

@Composable
private fun SingleFileBrowser(
    rootPath: String,
    onOpenFile: (String) -> Unit,
    onFilesDeleted: (Set<String>) -> Unit = {},
    onAddToConversation: (String) -> Unit = {},
    onOpenAsProject: (String) -> Unit = {},
    onExitProjectMode: () -> Unit = {},
    isProjectModeActive: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val holder = rememberFileBrowserStateHolder(rootPath)
    FileBrowserPanel(
        rootPath = rootPath,
        holder = holder,
        panelSide = PanelSide.Left,
        isActive = true,
        onActivate = {},
        onSync = {},
        onOpenFile = onOpenFile,
        onCopyToOtherSide = {},
        onMoveToOtherSide = {},
        onFilesDeleted = onFilesDeleted,
        onAddToConversation = onAddToConversation,
        onOpenAsProject = onOpenAsProject,
        onExitProjectMode = onExitProjectMode,
        isProjectModeActive = isProjectModeActive,
        singleMode = true,
        modifier = modifier,
    )
}

@Composable
private fun DualFileBrowser(
    rootPath: String,
    onOpenFile: (String) -> Unit,
    onFilesDeleted: (Set<String>) -> Unit = {},
    onAddToConversation: (String) -> Unit = {},
    onOpenAsProject: (String) -> Unit = {},
    onExitProjectMode: () -> Unit = {},
    isProjectModeActive: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var activeSide by remember { mutableStateOf(PanelSide.Left) }

    var leftRoot by remember { mutableStateOf(rootPath) }
    var rightRoot by remember { mutableStateOf(rootPath) }

    val leftHolder = rememberFileBrowserStateHolder(leftRoot)
    val rightHolder = rememberFileBrowserStateHolder(rightRoot)
    val activeHolder = if (activeSide == PanelSide.Left) leftHolder else rightHolder
    val activeState = activeHolder.snapshot()

    Column(modifier = modifier.fillMaxSize()) {
        Row(modifier = Modifier.weight(1f)) {
            FileBrowserPanel(
                rootPath = leftRoot,
                holder = leftHolder,
                panelSide = PanelSide.Left,
                isActive = activeSide == PanelSide.Left,
                onActivate = { activeSide = PanelSide.Left },
                onSync = {
                    leftRoot = rightRoot
                    activeSide = PanelSide.Left
                },
                onOpenFile = onOpenFile,
                onCopyToOtherSide = { files -> copyFiles(files, leftRoot, rightRoot, context) },
                onMoveToOtherSide = { files -> moveFiles(files, leftRoot, rightRoot, context) },
                onFilesDeleted = onFilesDeleted,
                onAddToConversation = onAddToConversation,
                onOpenAsProject = onOpenAsProject,
                onExitProjectMode = onExitProjectMode,
                isProjectModeActive = isProjectModeActive,
                onExtractToLeft = { files ->
                    scope.launch { extractFilesToDirAsync(files, leftHolder.currentPath, context) }
                },
                onExtractToRight = { files ->
                    scope.launch { extractFilesToDirAsync(files, rightHolder.currentPath, context) }
                },
                showToolbar = false,
                modifier = Modifier.weight(1f),
            )

            VerticalDivider(
                modifier = Modifier.fillMaxHeight(),
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            )

            FileBrowserPanel(
                rootPath = rightRoot,
                holder = rightHolder,
                panelSide = PanelSide.Right,
                isActive = activeSide == PanelSide.Right,
                onActivate = { activeSide = PanelSide.Right },
                onSync = {
                    rightRoot = leftRoot
                    activeSide = PanelSide.Right
                },
                onOpenFile = onOpenFile,
                onCopyToOtherSide = { files -> copyFiles(files, rightRoot, leftRoot, context) },
                onMoveToOtherSide = { files -> moveFiles(files, rightRoot, leftRoot, context) },
                onFilesDeleted = onFilesDeleted,
                onAddToConversation = onAddToConversation,
                onOpenAsProject = onOpenAsProject,
                onExitProjectMode = onExitProjectMode,
                isProjectModeActive = isProjectModeActive,
                onExtractToLeft = { files ->
                    scope.launch { extractFilesToDirAsync(files, leftHolder.currentPath, context) }
                },
                onExtractToRight = { files ->
                    scope.launch { extractFilesToDirAsync(files, rightHolder.currentPath, context) }
                },
                showToolbar = false,
                modifier = Modifier.weight(1f),
            )
        }

        HorizontalDivider(
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        )

        if (activeState.isSelectionMode) {
            SelectionToolbar(
                onSelectAll = { activeHolder.selectAll(activeState.files.map { it.absolutePath }) },
                onInvert = { activeHolder.invertSelection(activeState.files.map { it.absolutePath }) },
                onCancel = { activeHolder.cancelSelection() },
            )
        } else {
            NormalToolbar(
                canGoUp = activeState.canGoUp,
                canGoForward = activeState.canGoForward,
                onBack = { activeHolder.navigateUp() },
                onForward = { activeHolder.navigateForward() },
                onCreate = { activeHolder.requestCreate() },
                onSync = {
                    if (activeSide == PanelSide.Left) {
                        rightHolder.navigateTo(leftHolder.currentPath)
                    } else {
                        leftHolder.navigateTo(rightHolder.currentPath)
                    }
                },
                onReset = { activeHolder.navigateTo(if (activeSide == PanelSide.Left) leftRoot else rightRoot) },
                showSync = true,
            )
        }
    }
}

private suspend fun extractFilesToDirAsync(
    files: List<FileEntry>,
    destDir: String,
    context: android.content.Context,
) {
    var extracted = 0
    for (entry in files) {
        try {
            when (entry) {
                is FileEntry.Archive -> {
                    extractArchiveEntryToAsync(
                        File(entry.realArchivePath),
                        entry.entryFullPath,
                        destDir,
                        conflictMode = ConflictMode.Rename,
                    )
                        .onSuccess { n -> extracted += n }
                        .onFailure { throw it }
                }
                is FileEntry.Real -> {
                    val file = entry.file
                    if (file.exists() && isArchiveFile(file.name)) {
                        extractToAsync(
                            destDir, file,
                            conflictMode = ConflictMode.Rename,
                        )
                            .onSuccess { n -> extracted += n }
                            .onFailure { throw it }
                    } else if (file.isDirectory) {
                        val dest = File(destDir, file.name)
                        file.copyRecursively(dest, overwrite = false)
                        extracted++
                    } else {
                        file.copyTo(File(destDir, file.name), overwrite = false)
                        extracted++
                    }
                }
            }
        } catch (e: Exception) {
            Toast.makeText(context, "解压/复制失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    if (extracted > 0) {
        val msg = if (files.any {
                it is FileEntry.Archive || (it.toFile()?.let { f -> f.exists() && isArchiveFile(f.name) } == true)
            })
            "已解压 $extracted 项到对侧"
        else
            "已复制 $extracted 项到对侧"
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }
}

private fun copyFiles(
    files: List<FileEntry>,
    sourceRoot: String,
    destRoot: String,
    context: android.content.Context,
) {
    var copied = 0
    for (entry in files) {
        val real = entry.toFile() ?: continue
        val relativePath = real.absolutePath.removePrefix(File(sourceRoot).absolutePath)
            .trimStart('/', '\\')
        val dest = File(File(destRoot).absolutePath, relativePath)
        try {
            if (real.isDirectory) {
                dest.mkdirs()
                real.copyRecursively(dest, overwrite = false)
            } else {
                dest.parentFile?.mkdirs()
                real.copyTo(dest, overwrite = false)
            }
            copied++
        } catch (e: Exception) {
            Toast.makeText(context, "复制失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    if (copied > 0) {
        Toast.makeText(context, "已复制 $copied 项", Toast.LENGTH_SHORT).show()
    }
}

private fun moveFiles(
    files: List<FileEntry>,
    sourceRoot: String,
    destRoot: String,
    context: android.content.Context,
) {
    var moved = 0
    for (entry in files) {
        val real = entry.toFile() ?: continue
        val relativePath = real.absolutePath.removePrefix(File(sourceRoot).absolutePath)
            .trimStart('/', '\\')
        val dest = File(File(destRoot).absolutePath, relativePath)
        try {
            if (real.isDirectory) {
                dest.mkdirs()
                real.copyRecursively(dest, overwrite = false)
                real.deleteRecursively()
            } else {
                dest.parentFile?.mkdirs()
                real.copyTo(dest, overwrite = false)
                real.delete()
            }
            moved++
        } catch (e: Exception) {
            Toast.makeText(context, "移动失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    if (moved > 0) {
        Toast.makeText(context, "已移动 $moved 项", Toast.LENGTH_SHORT).show()
    }
}
