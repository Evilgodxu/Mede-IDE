package com.template.jh.di

import com.template.jh.data.repository.UserPreferencesRepository
import com.template.jh.screens.home.HomeViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single { UserPreferencesRepository(androidContext()) }
    viewModel { HomeViewModel(get()) }
}
