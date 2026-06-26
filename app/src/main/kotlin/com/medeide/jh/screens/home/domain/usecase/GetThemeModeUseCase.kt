package com.medeide.jh.screens.home.domain.usecase

import com.medeide.jh.screens.home.data.repository.HomeRepository
import kotlinx.coroutines.flow.Flow

class GetThemeModeUseCase(private val repository: HomeRepository) {
    operator fun invoke(): Flow<String> = repository.themeMode
}
