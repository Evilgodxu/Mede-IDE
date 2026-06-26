package com.medeide.jh.screens.permission

data class PermissionGuideUiState(
    val hasStoragePermission: Boolean = false,
    val hasBatteryOptimizationExemption: Boolean = false,
    val allBasicPermissionsGranted: Boolean = false,
    val isCompleted: Boolean = false,
    val userName: String = "",
)
