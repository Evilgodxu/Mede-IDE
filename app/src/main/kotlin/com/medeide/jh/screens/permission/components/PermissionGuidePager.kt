package com.medeide.jh.screens.permission.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun PermissionGuidePager(
    hasStoragePermission: Boolean,
    hasBatteryPermission: Boolean,
    onStorageClick: () -> Unit,
    onBatteryClick: () -> Unit,
    userName: String = "",
    onUserNameChange: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    PermissionBasicPage(
        hasStoragePermission = hasStoragePermission,
        hasBatteryPermission = hasBatteryPermission,
        onStorageClick = onStorageClick,
        onBatteryClick = onBatteryClick,
        userName = userName,
        onUserNameChange = onUserNameChange,
        modifier = modifier
    )
}
