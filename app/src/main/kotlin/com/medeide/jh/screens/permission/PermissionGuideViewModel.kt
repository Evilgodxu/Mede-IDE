package com.medeide.jh.screens.permission

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medeide.jh.core.data.repository.UserPreferencesRepository
import com.medeide.jh.model.chat.UserProfile
import com.medeide.jh.screens.permission.data.PermissionMonitor
import com.medeide.jh.screens.permission.data.PermissionType
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PermissionGuideViewModel(
    private val permissionMonitor: PermissionMonitor,
    private val preferencesRepo: UserPreferencesRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PermissionGuideUiState())
    val uiState: StateFlow<PermissionGuideUiState> = _uiState.asStateFlow()

    private var monitorJob: Job? = null

    fun checkPermissions(context: Context) {
        val hasStorage = permissionMonitor.isGranted(PermissionType.STORAGE)
        val hasBattery = permissionMonitor.isGranted(PermissionType.BATTERY_OPTIMIZATION)
        _uiState.update {
            it.copy(
                hasStoragePermission = hasStorage,
                hasBatteryOptimizationExemption = hasBattery,
                allBasicPermissionsGranted = hasStorage && hasBattery,
            )
        }
    }

    fun setUserName(name: String) {
        _uiState.update { it.copy(userName = name) }
    }

    fun startPermissionMonitor(permissionType: PermissionType, activity: Activity) {
        monitorJob?.cancel()
        monitorJob = viewModelScope.launch {
            permissionMonitor.monitorPermission(permissionType).collect { granted ->
                if (granted) {
                    checkPermissions(activity)
                    bringAppToFront(activity)
                    monitorJob?.cancel()
                }
            }
        }
    }

    fun stopPermissionMonitor() {
        monitorJob?.cancel()
        monitorJob = null
    }

    fun completeGuide() {
        _uiState.update { it.copy(isCompleted = true) }
        viewModelScope.launch {
            preferencesRepo.setUserProfile(
                UserProfile(userName = _uiState.value.userName)
            )
        }
    }

    fun openStorageSettings(activity: Activity, packageName: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            startPermissionMonitor(PermissionType.STORAGE, activity)
            if (!tryOpenIntent(activity, Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, packageName)) {
                if (!tryOpenIntent(activity, Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION, packageName)) {
                    tryOpenApplicationDetails(activity, packageName)
                }
            }
        }
    }

    fun openBatteryOptimizationSettings(activity: Activity, packageName: String) {
        startPermissionMonitor(PermissionType.BATTERY_OPTIMIZATION, activity)
        if (!tryOpenIntent(activity, Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageName)) {
            if (!tryOpenIntent(activity, Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) {
                tryOpenApplicationDetails(activity, packageName)
            }
        }
    }

    private fun tryOpenIntent(activity: Activity, action: String, packageName: String? = null): Boolean {
        return try {
            val intent = Intent(action).apply {
                packageName?.let { data = Uri.parse("package:$it") }
            }
            activity.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun tryOpenApplicationDetails(activity: Activity, packageName: String): Boolean {
        return tryOpenIntent(
            activity,
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            packageName
        )
    }

    private fun bringAppToFront(activity: Activity) {
        val intent = activity.packageManager.getLaunchIntentForPackage(activity.packageName)
        intent?.apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_NEW_TASK
            activity.startActivity(this)
        }
    }

    override fun onCleared() {
        stopPermissionMonitor()
    }
}
