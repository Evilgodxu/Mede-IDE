package com.template.jh.di

import com.template.jh.core.ai.AIToolSet
import com.template.jh.core.ai.ToolCallHandler
import com.template.jh.core.memory.ContextManager
import com.template.jh.core.memory.ConversationMemory
import com.template.jh.core.storage.FileManager
import com.template.jh.core.utils.ImageProcessor
import com.template.jh.data.repository.ConversationRepository
import com.template.jh.data.repository.UsageAnalyticsRepository
import com.template.jh.data.repository.UserPreferencesRepository
import com.template.jh.data.source.local.LiteRTManager
import com.template.jh.data.source.remote.CloudLLMClient
import com.template.jh.screens.home.ChatViewModel
import com.template.jh.screens.home.HomeViewModel
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
    single { AIToolSet(androidContext(), get(), get()) }
    single { ImageProcessor(androidContext()) }
    single { ContextManager(get()) }
    single { ToolCallHandler(get(), get()) }

    viewModel { HomeViewModel(androidContext() as android.app.Application, get(), get()) }
    viewModel {
        ChatViewModel(
            androidContext() as android.app.Application,
            get(), get(), get(), get(),
            get(), get(), get(), get(),
            get(), get(), get(),
        )
    }
}
