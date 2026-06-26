package com.medeide.jh.screens.home.cloudchat

import android.os.SystemClock

/**
 * 流式渲染节流器（参考 LineCodePro 80ms 合并策略）
 * 将高频 append 操作聚合成 STREAM_RENDER_INTERVAL_MS 间隔的单次刷新
 */
class StreamThrottle(
    private val intervalMs: Long = 80L,
) {
    private var scheduled = false
    private var lastRenderAt = 0L

    /** 请求一次渲染，若节流窗口未到则延迟执行 */
    fun request(block: () -> Unit) {
        if (scheduled) return
        val now = SystemClock.uptimeMillis()
        val elapsed = now - lastRenderAt
        if (elapsed >= intervalMs) {
            lastRenderAt = now
            block()
        } else {
            scheduled = true
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                scheduled = false
                lastRenderAt = SystemClock.uptimeMillis()
                block()
            }, intervalMs - elapsed)
        }
    }

    /** 强制立即刷新 */
    fun flush(block: () -> Unit) {
        scheduled = false
        lastRenderAt = SystemClock.uptimeMillis()
        block()
    }
}
