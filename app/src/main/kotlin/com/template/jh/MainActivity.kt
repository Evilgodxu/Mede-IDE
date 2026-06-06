package com.template.jh

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.template.jh.data.repository.UserPreferencesRepository
import com.template.jh.ui.adaptive.ProvideWindowSizeClass
import com.template.jh.ui.navigation.AppNavHost
import com.template.jh.ui.theme.MyApplicationTheme
import com.template.jh.core.utils.localization.LanguageManager
import com.template.jh.core.utils.localization.ProvideLocalizedContext
import org.koin.android.ext.android.inject

// 主Activity，应用入口
class MainActivity : ComponentActivity() {
    private val userPreferencesRepository: UserPreferencesRepository by inject()
    private lateinit var languageManager: LanguageManager
    private lateinit var windowInsetsController: WindowInsetsControllerCompat

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setupSystemBars()

        languageManager = LanguageManager(this, userPreferencesRepository)

        setContent {
            val navController = rememberNavController()
            val themeMode by userPreferencesRepository.themeMode.collectAsStateWithLifecycle(initialValue = "system")

            val darkTheme = when (themeMode) {
                "light" -> false
                "dark" -> true
                else -> isSystemInDarkTheme()
            }

            ProvideLocalizedContext(languageManager) {
                ProvideWindowSizeClass {
                    MyApplicationTheme(darkTheme = darkTheme, dynamicColor = false) {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.background,
                        ) {
                            AppNavHost(navController = navController)
                        }
                    }
                }
            }
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
