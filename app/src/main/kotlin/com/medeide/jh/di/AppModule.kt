package com.medeide.jh.di

import com.medeide.jh.core.data.repository.ConversationRepository
import com.medeide.jh.core.data.repository.UserPreferencesRepository
import com.medeide.jh.core.data.repository.UsageAnalyticsRepository
import com.medeide.jh.core.data.source.local.LiteRTEngineManager
import com.medeide.jh.core.data.source.local.LiteRTModelRepository
import com.medeide.jh.core.data.source.remote.CloudLLMClient
import com.medeide.jh.screens.home.HomeViewModel
import com.medeide.jh.screens.home.cloudchat.CloudChatViewModel
import com.medeide.jh.screens.home.data.repository.HomeRepository
import com.medeide.jh.screens.home.domain.usecase.GetThemeModeUseCase
import com.medeide.jh.screens.home.localchat.LocalChatViewModel
import com.medeide.jh.screens.permission.PermissionGuideViewModel
import com.medeide.jh.screens.permission.data.PermissionMonitor
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single { UserPreferencesRepository(androidContext()) }
    single { HomeRepository(get()) }
    single { GetThemeModeUseCase(get()) }
    single { PermissionMonitor(androidContext()) }
    single { ConversationRepository(androidContext()) }
    single { CloudLLMClient() }
    single { UsageAnalyticsRepository(androidContext()) }

    // LiteRT-LM 本地模型服务
    single { LiteRTEngineManager(androidContext()) }
    single { LiteRTModelRepository(androidContext()) }

    viewModel { HomeViewModel(get()) }
    viewModel { PermissionGuideViewModel(get(), get()) }
    viewModel { CloudChatViewModel(get(), get(), get(), get()) }
    viewModel { LocalChatViewModel(get(), get(), get(), get()) }
}
