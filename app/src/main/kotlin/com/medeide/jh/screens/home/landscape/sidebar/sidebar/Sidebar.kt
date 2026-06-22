package com.medeide.jh.screens.home.landscape.sidebar.sidebar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.medeide.jh.R

enum class SidebarTab {
    Explorer, Search, Terminal, Snippets, Bookmarks, RecentFiles
}

@Composable
fun Sidebar(
    selectedTab: SidebarTab?,
    onTabClick: (SidebarTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .width(48.dp)
            .fillMaxHeight(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            SidebarItem(
                icon = Icons.Default.Folder,
                contentDescription = stringResource(R.string.resource_manager_title),
                isSelected = selectedTab == SidebarTab.Explorer,
                onClick = { onTabClick(SidebarTab.Explorer) }
            )
            SidebarItem(
                icon = Icons.Default.Search,
                contentDescription = "搜索替换",
                isSelected = selectedTab == SidebarTab.Search,
                onClick = { onTabClick(SidebarTab.Search) }
            )
            SidebarItem(
                icon = Icons.Default.Terminal,
                contentDescription = "终端",
                isSelected = selectedTab == SidebarTab.Terminal,
                onClick = { onTabClick(SidebarTab.Terminal) }
            )
            SidebarItem(
                icon = Icons.Default.Code,
                contentDescription = "代码片段",
                isSelected = selectedTab == SidebarTab.Snippets,
                onClick = { onTabClick(SidebarTab.Snippets) }
            )
            SidebarItem(
                icon = Icons.Default.Bookmarks,
                contentDescription = "书签",
                isSelected = selectedTab == SidebarTab.Bookmarks,
                onClick = { onTabClick(SidebarTab.Bookmarks) }
            )
            SidebarItem(
                icon = Icons.Default.History,
                contentDescription = "最近文件",
                isSelected = selectedTab == SidebarTab.RecentFiles,
                onClick = { onTabClick(SidebarTab.RecentFiles) }
            )
        }
    }

    VerticalDivider(
        modifier = Modifier.fillMaxHeight(),
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    )
}

@Composable
private fun SidebarItem(
    icon: ImageVector,
    contentDescription: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.background
    }

    val iconColor = if (isSelected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        modifier = Modifier
            .size(40.dp)
            .clickable(onClick = onClick),
        color = backgroundColor,
        shape = MaterialTheme.shapes.small
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier
                .padding(8.dp)
                .size(24.dp),
            tint = iconColor
        )
    }
}
