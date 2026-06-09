package com.template.jh.di

import com.template.jh.data.repository.ConversationRepository
import com.template.jh.screens.home.ChatViewModel
import com.template.jh.core.storage.FileManager
import com.template.jh.data.repository.UsageAnalyticsRepository
import com.template.jh.data.repository.UserPreferencesRepository
import com.template.jh.screens.home.HomeViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single { UserPreferencesRepository(androidContext()) }
    single { UsageAnalyticsRepository(androidContext()) }
    single { ConversationRepository(androidContext()) }
    single { FileManager(androidContext()) }
    viewModel { HomeViewModel(androidContext() as android.app.Application, get(), get()) }
    viewModel { ChatViewModel(androidContext() as android.app.Application, get(), get(), get(), get()) }
}
