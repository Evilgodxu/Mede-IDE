package com.medeide.jh.screens.home.filebrowser

import com.medeide.jh.screens.home.filebrowser.logic.FileEntry

enum class PanelSide { Left, Right }

data class FileBrowserState(
    val currentPath: String = "",
    val files: List<FileEntry> = emptyList(),
    val displayPath: String = "",
    val canGoForward: Boolean = false,
    val canGoUp: Boolean = false,
    val isSelectionMode: Boolean = false,
    val selectedItems: Set<String> = emptySet(),
    val menuTarget: FileEntry? = null,
    val showCreateDialog: Boolean = false,
    val showRenameDialog: FileEntry? = null,
    val showInfoDialog: FileEntry? = null,
    val showDeleteDialog: List<FileEntry>? = null,
)

data class DualFileBrowserState(
    val left: FileBrowserState = FileBrowserState(),
    val right: FileBrowserState = FileBrowserState(),
    val activeSide: PanelSide = PanelSide.Left,
)
