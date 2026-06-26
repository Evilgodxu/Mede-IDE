package com.medeide.jh.screens.permission.portrait

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.medeide.jh.screens.permission.PermissionGuideUiState
import com.medeide.jh.screens.permission.components.PermissionGuidePager
import com.medeide.jh.ui.components.ParticleBackground

@Composable
fun PermissionGuidePortraitScreen(
    uiState: PermissionGuideUiState,
    onStorageClick: () -> Unit,
    onBatteryClick: () -> Unit,
    onComplete: () -> Unit,
    onUserNameChange: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        ParticleBackground(
            modifier = Modifier.fillMaxSize(),
            particleCount = 60,
            speedMultiplier = 1.5f,
            maxRadius = 2f,
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            PermissionGuidePager(
                hasStoragePermission = uiState.hasStoragePermission,
                hasBatteryPermission = uiState.hasBatteryOptimizationExemption,
                onStorageClick = onStorageClick,
                onBatteryClick = onBatteryClick,
                userName = uiState.userName,
                onUserNameChange = onUserNameChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )

            Button(
                onClick = onComplete,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                Text("进入")
            }
        }
    }
}
