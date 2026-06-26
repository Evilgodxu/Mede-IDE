package com.medeide.jh.screens.home.data.repository

import com.medeide.jh.core.data.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.Flow

class HomeRepository(private val userPreferencesRepository: UserPreferencesRepository) {
    val themeMode: Flow<String> = userPreferencesRepository.themeMode
    val language: Flow<String> = userPreferencesRepository.language

    suspend fun setThemeMode(mode: String) {
        userPreferencesRepository.setThemeMode(mode)
    }

    suspend fun setLanguage(language: String) {
        userPreferencesRepository.setLanguage(language)
    }
}
