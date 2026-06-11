package com.template.jh.screens.home.components

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

// 三列布局组件，支持侧边栏+可展开面板
@Composable
fun ThreeColumnLayout(
    sidebar: @Composable () -> Unit,
    leftPanel: @Composable () -> Unit,
    isLeftPanelVisible: Boolean,
    isLeftPanelExpanded: Boolean = false,
    centerContent: @Composable () -> Unit,
    rightPanel: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    val leftPanelWidth by animateDpAsState(
        targetValue = if (isLeftPanelExpanded) 360.dp else 180.dp,
        animationSpec = tween(durationMillis = 200),
        label = "leftPanelWidth"
    )

    Row(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 侧边栏
        sidebar()

        // 左侧面板（可展开/收起）
        AnimatedVisibility(
            visible = isLeftPanelVisible,
            enter = expandHorizontally(
                animationSpec = tween(durationMillis = 200)
            ),
            exit = shrinkHorizontally(
                animationSpec = tween(durationMillis = 200)
            )
        ) {
            Column(
                modifier = Modifier
                    .width(leftPanelWidth)
                    .fillMaxHeight()
            ) {
                leftPanel()
            }
        }

        if (isLeftPanelVisible) {
            VerticalDivider(
                modifier = Modifier.fillMaxHeight(),
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
        }

        // 中间内容区
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            centerContent()
        }

        VerticalDivider(
            modifier = Modifier.fillMaxHeight(),
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )

        // 右侧面板（宽度 = 侧边栏48dp + 展开面板180dp = 228dp）
        Column(
            modifier = Modifier
                .width(228.dp)
                .fillMaxHeight()
        ) {
            rightPanel()
        }
    }
}
