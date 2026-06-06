package com.template.jh.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.template.jh.data.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// 设置页面 ViewModel
class SettingsViewModel(
    private val userPreferencesRepository: UserPreferencesRepository,
) : ViewModel() {
    val state: StateFlow<SettingsUiState> = combine(
        userPreferencesRepository.themeMode,
        userPreferencesRepository.language,
    ) { themeMode, language ->
        SettingsUiState(
            isLoading = false,
            themeMode = themeMode,
            language = language,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState(isLoading = true),
    )

    fun setThemeMode(mode: String) {
        viewModelScope.launch {
            userPreferencesRepository.setThemeMode(mode)
        }
    }

    fun setLanguage(language: String) {
        viewModelScope.launch {
            userPreferencesRepository.setLanguage(language)
        }
    }
}
