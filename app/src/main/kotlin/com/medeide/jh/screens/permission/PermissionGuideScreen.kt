package com.medeide.jh.screens.permission

import android.Manifest
import android.app.Activity
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.window.core.layout.WindowSizeClass
import com.medeide.jh.screens.permission.landscape.PermissionGuideLandscapeScreen
import com.medeide.jh.screens.permission.portrait.PermissionGuidePortraitScreen
import com.medeide.jh.ui.adaptive.rememberWindowSizeClass
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel

val LocalActivity = staticCompositionLocalOf<Activity> {
    error("LocalActivity not provided")
}

@Composable
fun PermissionGuideScreen(
    onComplete: () -> Unit = {},
    viewModel: PermissionGuideViewModel = koinViewModel(),
) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val windowSizeClass = rememberWindowSizeClass()
    val isLandscape = windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)

    val uiState by viewModel.uiState.collectAsState()
    var showPermissionWarning by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.checkPermissions(context)
        if (viewModel.uiState.value.allBasicPermissionsGranted) {
            onComplete()
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.stopPermissionMonitor()
                viewModel.checkPermissions(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.stopPermissionMonitor()
        }
    }

    val storagePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            val readGranted = permissions[Manifest.permission.READ_EXTERNAL_STORAGE] ?: false
            val writeGranted = permissions[Manifest.permission.WRITE_EXTERNAL_STORAGE] ?: false
            if (readGranted && writeGranted) {
                viewModel.checkPermissions(context)
            }
        }
    }

    LaunchedEffect(uiState.isCompleted) {
        if (uiState.isCompleted) {
            delay(500)
            onComplete()
        }
    }

    val onStorageClick: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            viewModel.openStorageSettings(activity, context.packageName)
        } else {
            storagePermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            )
        }
    }

    val onBatteryClick: () -> Unit = {
        viewModel.openBatteryOptimizationSettings(activity, context.packageName)
    }

    val onCompleteClick: () -> Unit = {
        if (uiState.allBasicPermissionsGranted) {
            viewModel.completeGuide()
        } else {
            showPermissionWarning = true
        }
    }

    if (showPermissionWarning) {
        AlertDialog(
            onDismissRequest = { showPermissionWarning = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text(text = "权限警告", style = MaterialTheme.typography.titleMedium) },
            text = {
                Text(
                    text = "缺少必要权限可能会影响应用功能。建议授予所有权限以获得最佳体验。",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPermissionWarning = false
                        viewModel.completeGuide()
                    }
                ) { Text("继续") }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionWarning = false }) { Text("返回") }
            }
        )
    }

    if (isLandscape) {
        PermissionGuideLandscapeScreen(
            uiState = uiState,
            onStorageClick = onStorageClick,
            onBatteryClick = onBatteryClick,
            onComplete = onCompleteClick,
            onUserNameChange = { viewModel.setUserName(it) },
        )
    } else {
        PermissionGuidePortraitScreen(
            uiState = uiState,
            onStorageClick = onStorageClick,
            onBatteryClick = onBatteryClick,
            onComplete = onCompleteClick,
            onUserNameChange = { viewModel.setUserName(it) },
        )
    }
}
