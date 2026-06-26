package com.medeide.jh.screens.home.domain.usecase

import com.medeide.jh.screens.home.data.repository.HomeRepository

class SetThemeModeUseCase(private val repository: HomeRepository) {
    suspend operator fun invoke(mode: String) {
        repository.setThemeMode(mode)
    }
}
