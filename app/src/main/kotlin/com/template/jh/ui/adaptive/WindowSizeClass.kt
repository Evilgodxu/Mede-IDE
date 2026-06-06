package com.template.jh.ui.adaptive

import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.window.core.layout.WindowSizeClass

val LocalWindowSizeClass = compositionLocalOf<WindowSizeClass> {
    error("WindowSizeClass not provided")
}

// 提供窗口尺寸类
@Composable
fun ProvideWindowSizeClass(content: @Composable () -> Unit) {
    val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass
    CompositionLocalProvider(LocalWindowSizeClass provides windowSizeClass) {
        content()
    }
}

// 获取当前窗口尺寸类
@Composable
fun rememberWindowSizeClass(): WindowSizeClass {
    return LocalWindowSizeClass.current
}
