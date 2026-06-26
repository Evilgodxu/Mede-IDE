package com.medeide.jh.screens.home.domain.usecase

import com.medeide.jh.screens.home.data.repository.HomeRepository

class SetLanguageUseCase(private val repository: HomeRepository) {
    suspend operator fun invoke(language: String) {
        repository.setLanguage(language)
    }
}
