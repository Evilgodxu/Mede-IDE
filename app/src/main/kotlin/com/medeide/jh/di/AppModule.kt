package com.medeide.jh.di

import com.medeide.jh.screens.home.aitools.AIToolSet
import com.medeide.jh.screens.home.aitools.InputOptimizer
import com.medeide.jh.screens.home.aitools.ToolCallHandler
import com.medeide.jh.screens.home.memory.ContextManager
import com.medeide.jh.screens.home.memory.ConversationMemory
import com.medeide.jh.data.storage.FileManager
import com.medeide.jh.data.utils.ImageProcessor
import com.medeide.jh.data.repository.ConversationRepository
import com.medeide.jh.data.repository.UsageAnalyticsRepository
import com.medeide.jh.data.repository.UserPreferencesRepository
import com.medeide.jh.data.source.local.LiteRTManager
import com.medeide.jh.data.source.remote.CloudLLMClient
import com.medeide.jh.screens.home.ChatViewModel
import com.medeide.jh.screens.home.HomeViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single { UserPreferencesRepository(androidContext()) }
    single { UsageAnalyticsRepository(androidContext()) }
    single { ConversationRepository(androidContext()) }
    single { FileManager(androidContext()) }
    single { LiteRTManager(androidContext()) }
    single { ConversationMemory(androidContext()) }
    single { CloudLLMClient(androidContext()) }
    single { AIToolSet(get(), get()) }
    single { ImageProcessor(androidContext()) }
    single { ContextManager(get()) }
    single { ToolCallHandler(get(), get()) }
    single { InputOptimizer(get(), get()) }

    viewModel { HomeViewModel(androidContext() as android.app.Application, get(), get()) }
    viewModel {
        ChatViewModel(
            androidContext() as android.app.Application,
            get(), get(), get(), get(),
            get(), get(), get(), get(),
            get(), get(), get(), get(),
        )
    }
}
