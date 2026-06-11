package com.template.jh.screens.permission

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PermissionGuideViewModel : ViewModel() {

    private val TAG = "PermissionGuideVM"

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    enum class Step {
        WELCOME,
        BASIC_PERMISSIONS
    }

    fun checkPermissions(context: Context) {
        // 存储权限
        val hasStoragePermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }

        // 电池优化豁免
        val hasBatteryOptimizationExemption = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            powerManager.isIgnoringBatteryOptimizations(context.packageName)
        } else {
            true
        }

        _uiState.update { currentState ->
            currentState.copy(
                hasStoragePermission = hasStoragePermission,
                hasBatteryOptimizationExemption = hasBatteryOptimizationExemption,
                allBasicPermissionsGranted = hasStoragePermission && hasBatteryOptimizationExemption
            )
        }
    }

    fun setCurrentStep(step: Step) {
        _uiState.update { it.copy(currentStep = step) }
    }

    fun completeGuide() {
        _uiState.update { it.copy(isCompleted = true) }
    }

    data class UiState(
        val currentStep: Step = Step.WELCOME,
        val hasStoragePermission: Boolean = false,
        val hasBatteryOptimizationExemption: Boolean = false,
        val allBasicPermissionsGranted: Boolean = false,
        val isCompleted: Boolean = false
    )
}
