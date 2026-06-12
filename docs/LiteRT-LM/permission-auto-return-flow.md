# 权限申请自动返回应用流程

## 机制概述

当应用需要用户前往系统设置手动开启特殊权限（如无障碍服务、悬浮窗、使用情况统计等）时，启动后台轮询监控权限状态。用户完成授权后，系统自动将应用带回前台，无需用户手动返回。

## 核心组件

| 组件 | 职责 |
|------|------|
| `PermissionMonitor` | 提供权限检查与轮询 Flow |
| `ViewModel` | 启动/停止监控，处理授权后的页面跳转 |
| `Activity` | 提供上下文与启动目标 Intent |

## 完整实现

### 1. 权限监控器

```kotlin
import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

enum class PermissionType {
    OVERLAY,
    NOTIFICATION,
    BATTERY_OPTIMIZATION,
    USAGE_STATS,
    QUERY_ALL_PACKAGES,
    ACCESSIBILITY,
    WRITE_SETTINGS
}

class PermissionMonitor(private val context: Context) {

    fun isGranted(type: PermissionType): Boolean = when (type) {
        PermissionType.OVERLAY -> Settings.canDrawOverlays(context)
        PermissionType.NOTIFICATION -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else true
        PermissionType.BATTERY_OPTIMIZATION -> {
            val pm = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            pm.isIgnoringBatteryOptimizations(context.packageName)
        }
        PermissionType.USAGE_STATS -> {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName) == AppOpsManager.MODE_ALLOWED
        }
        PermissionType.QUERY_ALL_PACKAGES -> {
            try {
                val apps = context.packageManager.getInstalledApplications(0)
                apps.isNotEmpty() && apps.any { it.packageName != context.packageName }
            } catch (_: Exception) { false }
        }
        PermissionType.ACCESSIBILITY -> {
            val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            enabled?.contains(context.packageName) == true
        }
        PermissionType.WRITE_SETTINGS -> Settings.System.canWrite(context)
    }

    /** 轮询指定权限，授权后 emit true 并结束 Flow */
    fun monitor(type: PermissionType, intervalMs: Long = 500): Flow<Boolean> = flow {
        while (true) {
            val granted = isGranted(type)
            emit(granted)
            if (granted) break
            delay(intervalMs)
        }
    }
}
```

### 2. ViewModel 控制层

```kotlin
import android.app.Activity
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class PermissionViewModel(application: Application) : AndroidViewModel(application) {

    private val context get() = getApplication<Application>().applicationContext
    private val monitor = PermissionMonitor(context)
    private var monitorJob: Job? = null

    private val _waiting = MutableStateFlow<PermissionType?>(null)
    val waiting: StateFlow<PermissionType?> = _waiting

    /** 启动监控：权限授权后自动返回应用 */
    fun startMonitor(type: PermissionType, activity: Activity) {
        monitorJob?.cancel()
        _waiting.value = type

        monitorJob = viewModelScope.launch {
            monitor.monitor(type).collect { granted ->
                if (granted) {
                    _waiting.value = null
                    bringToFront(activity)
                    monitorJob?.cancel()
                }
            }
        }
    }

    fun stopMonitor() {
        monitorJob?.cancel()
        monitorJob = null
        _waiting.value = null
    }

    private fun bringToFront(activity: Activity) {
        val intent = activity.packageManager.getLaunchIntentForPackage(activity.packageName)
        intent?.apply {
            flags = android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    android.content.Intent.FLAG_ACTIVITY_NEW_TASK
            activity.startActivity(this)
        }
    }
}
```

### 3. UI 触发层

```kotlin
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun PermissionScreen(viewModel: PermissionViewModel) {
    val context = LocalContext.current
    val activity = context as? android.app.Activity
    val waiting by viewModel.waiting.collectAsStateWithLifecycle()

    PermissionList(
        onRequestOverlay = {
            activity?.let { viewModel.startMonitor(PermissionType.OVERLAY, it) }
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )
            context.startActivity(intent)
        },
        onRequestAccessibility = {
            activity?.let { viewModel.startMonitor(PermissionType.ACCESSIBILITY, it) }
            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        },
        onRequestUsageStats = {
            activity?.let { viewModel.startMonitor(PermissionType.USAGE_STATS, it) }
            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            context.startActivity(intent)
        }
    )
}
```

## 关键设计要点

- **轮询间隔**：默认 500ms，兼顾响应速度与电量消耗。
- **生命周期绑定**：监控任务由 `viewModelScope` 托管，页面销毁时自动取消。
- **返回标志**：`FLAG_ACTIVITY_CLEAR_TOP or FLAG_ACTIVITY_SINGLE_TOP` 复用已有 Activity 实例，避免重建。
- **适用范围**：仅用于无 Activity Result API 的特殊权限（无障碍、悬浮窗、使用情况统计等）。运行时权限（如通知）应继续使用 `ActivityResultContracts.RequestPermission()`。
