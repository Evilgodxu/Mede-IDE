package com.medeide.jh.screens.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// 三区布局：侧栏区（图标导航 + 可展开面板）+ 工作区 + 协作区
@Composable
fun ThreeColumnLayout(
    sideIconBar: @Composable () -> Unit,
    sidePanel: @Composable () -> Unit,
    isSidePanelVisible: Boolean,
    isSidePanelExpanded: Boolean = false,
    workspaceContent: @Composable () -> Unit,
    collabPanel: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    val sidePanelWidth by animateDpAsState(
        targetValue = if (isSidePanelExpanded) 360.dp else 180.dp,
        animationSpec = tween(durationMillis = 200),
        label = "sidePanelWidth"
    )

    Row(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 侧栏区 - 图标导航
        sideIconBar()

        // 侧栏区 - 可展开面板
        AnimatedVisibility(
            visible = isSidePanelVisible,
            enter = expandHorizontally(
                animationSpec = tween(durationMillis = 200)
            ),
            exit = shrinkHorizontally(
                animationSpec = tween(durationMillis = 200)
            )
        ) {
            Column(
                modifier = Modifier
                    .width(sidePanelWidth)
                    .fillMaxHeight()
            ) {
                sidePanel()
            }
        }

        if (isSidePanelVisible) {
            VerticalDivider(
                modifier = Modifier.fillMaxHeight(),
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
        }

        // 工作区
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            workspaceContent()
        }

        VerticalDivider(
            modifier = Modifier.fillMaxHeight(),
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )

        // 协作区（宽度 = 侧栏图标栏48dp + 侧栏面板180dp = 228dp）
        Column(
            modifier = Modifier
                .width(228.dp)
                .fillMaxHeight()
        ) {
            collabPanel()
        }
    }
}
