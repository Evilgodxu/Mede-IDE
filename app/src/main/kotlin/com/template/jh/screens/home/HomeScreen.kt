package com.template.jh.screens.home

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.window.core.layout.WindowSizeClass
import com.template.jh.R
import com.template.jh.ui.adaptive.rememberWindowSizeClass
import com.template.jh.screens.home.components.CompactHomeContent
import com.template.jh.screens.home.components.ExpandedHomeContent
import org.koin.androidx.compose.koinViewModel

// 主屏幕，根据窗口宽度自适应显示紧凑或扩展布局
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToSettings: () -> Unit,
    viewModel: HomeViewModel = koinViewModel(),
) {
    val windowSizeClass = rememberWindowSizeClass()

    val topBarInsets = if (!windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)) {
        WindowInsets.statusBars
    } else {
        WindowInsets(0, 0, 0, 0)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.home_title)) },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.settings_title),
                        )
                    }
                },
                windowInsets = topBarInsets,
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { innerPadding ->
        when {
            !windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND) -> {
                CompactHomeContent(
                    modifier = Modifier
                        .fillMaxSize()
                        .consumeWindowInsets(innerPadding)
                        .padding(innerPadding)
                )
            }
            else -> {
                ExpandedHomeContent(
                    modifier = Modifier
                        .fillMaxSize()
                        .consumeWindowInsets(innerPadding)
                        .padding(innerPadding)
                )
            }
        }
    }
}
