package com.template.jh.screens.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.template.jh.screens.settings.SettingsUiState

// 扩展布局设置内容（平板、横屏）
@Composable
fun ExpandedSettingsContent(
    state: SettingsUiState,
    onSetThemeMode: (String) -> Unit,
    onSetLanguage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.verticalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        ThemeSettingsCard(
            state = state,
            onSetThemeMode = onSetThemeMode,
            modifier = Modifier.weight(1f),
        )
        LanguageSettingsCard(
            state = state,
            onSetLanguage = onSetLanguage,
            modifier = Modifier.weight(1f),
        )
    }
}
