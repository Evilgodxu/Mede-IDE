package com.template.jh.core.utils.localization

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import com.template.jh.data.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Locale

// 语言管理器，处理应用语言切换
class LanguageManager(
    private val context: Context,
    private val userPreferencesRepository: UserPreferencesRepository,
) {
    val languageFlow: Flow<String> = userPreferencesRepository.language

    val localeFlow: Flow<Locale> = languageFlow.map { languageCode ->
        resolveLocale(languageCode)
    }

    fun resolveLocale(languageCode: String): Locale {
        return when (languageCode) {
            "zh" -> Locale.SIMPLIFIED_CHINESE
            "en" -> Locale.ENGLISH
            else -> Locale.getDefault()
        }
    }

    fun createLocalizedContext(locale: Locale): Context {
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }
}

// 提供本地化 Context 的 Composable
@Composable
fun ProvideLocalizedContext(
    languageManager: LanguageManager,
    content: @Composable () -> Unit,
) {
    val locale by languageManager.localeFlow.collectAsState(
        initial = LocalLocale.current.platformLocale,
    )
    val localizedContext = languageManager.createLocalizedContext(locale)

    CompositionLocalProvider(LocalContext provides localizedContext) {
        content()
    }
}
