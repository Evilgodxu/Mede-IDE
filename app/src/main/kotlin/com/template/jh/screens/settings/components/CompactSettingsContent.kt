package com.template.jh.screens.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.template.jh.screens.settings.SettingsUiState

// 紧凑布局设置内容（手机竖屏）
@Composable
fun CompactSettingsContent(
    state: SettingsUiState,
    onSetThemeMode: (String) -> Unit,
    onSetLanguage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        ThemeSettingsCard(
            state = state,
            onSetThemeMode = onSetThemeMode,
            modifier = Modifier.fillMaxWidth(),
        )
        LanguageSettingsCard(
            state = state,
            onSetLanguage = onSetLanguage,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
