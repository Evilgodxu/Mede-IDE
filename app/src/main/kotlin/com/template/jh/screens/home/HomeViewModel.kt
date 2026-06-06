package com.template.jh.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.template.jh.data.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// 主屏幕 ViewModel
class HomeViewModel(
    private val userPreferencesRepository: UserPreferencesRepository,
) : ViewModel() {
    val state: StateFlow<HomeUiState> = combine(
        userPreferencesRepository.themeMode,
        userPreferencesRepository.language,
    ) { themeMode, language ->
        HomeUiState(
            isLoading = false,
            themeMode = themeMode,
            language = language,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState(isLoading = true),
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
