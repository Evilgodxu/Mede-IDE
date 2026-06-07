package com.template.jh.di

import com.template.jh.core.ai.ChatViewModel
import com.template.jh.core.ai.ConversationRepository
import com.template.jh.data.repository.UserPreferencesRepository
import com.template.jh.screens.home.HomeViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single { UserPreferencesRepository(androidContext()) }
    single { ConversationRepository(androidContext()) }
    viewModel { HomeViewModel(androidContext() as android.app.Application, get()) }
    viewModel { ChatViewModel(androidContext() as android.app.Application, get()) }
}
