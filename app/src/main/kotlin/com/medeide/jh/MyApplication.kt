package com.medeide.jh

import android.app.Application
import com.medeide.jh.core.utils.FileLogger
import com.medeide.jh.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

// 应用入口
class MyApplication : Application() {

    companion object {
        lateinit var instance: MyApplication
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        FileLogger.init(this)

        startKoin {
            androidLogger()
            androidContext(this@MyApplication)
            modules(appModule)
        }
    }
}
