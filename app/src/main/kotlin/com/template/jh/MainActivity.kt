package com.template.jh

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.template.jh.data.repository.UserPreferencesRepository
import com.template.jh.screens.home.HomeScreen
import com.template.jh.screens.permission.PermissionGuideScreen
import com.template.jh.ui.adaptive.ProvideWindowSizeClass
import com.template.jh.ui.theme.MyApplicationTheme
import com.template.jh.core.utils.localization.LanguageManager
import com.template.jh.core.utils.localization.ProvideLocalizedContext
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

// 主Activity，应用入口
class MainActivity : ComponentActivity() {
    private val userPreferencesRepository: UserPreferencesRepository by inject()
    private lateinit var languageManager: LanguageManager
    private lateinit var windowInsetsController: WindowInsetsControllerCompat

    // 是否显示权限引导界面
    private var showPermissionGuide by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setupSystemBars()

        languageManager = LanguageManager(this, userPreferencesRepository)

        // 检查是否需要显示权限引导
        checkPermissionGuideStatus()

        setContent {
            val themeMode by userPreferencesRepository.themeMode.collectAsStateWithLifecycle(initialValue = "system")

            val darkTheme = when (themeMode) {
                "light" -> false
                "dark" -> true
                else -> isSystemInDarkTheme()
            }

            // 显式提供 ActivityResultRegistryOwner（兼容 ProvideLocalizedContext 的 Context 替换）
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides this@MainActivity) {
                ProvideLocalizedContext(languageManager) {
                    ProvideWindowSizeClass {
                        MyApplicationTheme(darkTheme = darkTheme, dynamicColor = false) {
                            Surface(
                                modifier = Modifier.fillMaxSize(),
                                color = MaterialTheme.colorScheme.background,
                            ) {
                                if (showPermissionGuide) {
                                    PermissionGuideScreen(
                                        onComplete = {
                                            showPermissionGuide = false
                                            lifecycleScope.launch {
                                                userPreferencesRepository.setPermissionGuideShown(true)
                                            }
                                        }
                                    )
                                } else {
                                    HomeScreen()
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun checkPermissionGuideStatus() {
        lifecycleScope.launch {
            val guideShown = userPreferencesRepository.isPermissionGuideShown()
            showPermissionGuide = !guideShown
        }
    }

    private fun setupSystemBars() {
        windowInsetsController = WindowInsetsControllerCompat(window, window.decorView)
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        updateSystemBarsVisibility()
    }

    private fun updateSystemBarsVisibility() {
        if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateSystemBarsVisibility()
    }
}
