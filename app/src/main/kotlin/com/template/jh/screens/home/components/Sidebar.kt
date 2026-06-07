package com.template.jh.screens.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Source
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
import com.template.jh.R

enum class SidebarTab {
    Explorer, Tasks, Search, SourceControl, Preview, Extensions
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

            // 任务清单侧边栏项已移除

            SidebarItem(
                icon = Icons.Default.Search,
                contentDescription = stringResource(R.string.global_search_title),
                isSelected = selectedTab == SidebarTab.Search,
                onClick = { onTabClick(SidebarTab.Search) }
            )

            SidebarItem(
                icon = Icons.Default.Source,
                contentDescription = stringResource(R.string.source_control),
                isSelected = selectedTab == SidebarTab.SourceControl,
                onClick = { onTabClick(SidebarTab.SourceControl) }
            )

            SidebarItem(
                icon = Icons.Default.PlayArrow,
                contentDescription = stringResource(R.string.preview),
                isSelected = selectedTab == SidebarTab.Preview,
                onClick = { onTabClick(SidebarTab.Preview) }
            )

            SidebarItem(
                icon = Icons.Default.Extension,
                contentDescription = stringResource(R.string.extensions),
                isSelected = selectedTab == SidebarTab.Extensions,
                onClick = { onTabClick(SidebarTab.Extensions) }
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
