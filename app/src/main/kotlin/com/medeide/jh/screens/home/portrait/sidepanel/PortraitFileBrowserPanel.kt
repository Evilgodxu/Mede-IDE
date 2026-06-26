package com.medeide.jh.screens.home.portrait.sidepanel

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.medeide.jh.screens.home.filebrowser.FileBrowserScreen

@Composable
fun PortraitFileBrowserPanel(
    rootPath: String,
    onOpenFile: (String) -> Unit = {},
    onAddToConversation: (String) -> Unit = {},
    onOpenAsProject: (String) -> Unit = {},
    onExitProjectMode: () -> Unit = {},
    isProjectModeActive: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 24.dp),
    ) {
        Text(
            text = "文件管理器",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        )
        Spacer(modifier = Modifier.height(8.dp))
        FileBrowserScreen(
            rootPath = rootPath,
            columnCount = 1,
            onOpenFile = onOpenFile,
            onAddToConversation = onAddToConversation,
            onOpenAsProject = onOpenAsProject,
            onExitProjectMode = onExitProjectMode,
            isProjectModeActive = isProjectModeActive,
            modifier = Modifier.fillMaxWidth().weight(1f),
        )
    }
}
