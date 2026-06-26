package com.medeide.jh.core.data.source.remote

import java.util.concurrent.CopyOnWriteArrayList

/** 线程安全的取消令牌（参考 LineCodePro ModelCancellationToken） */
class ModelCancellationToken {
    private val listeners = CopyOnWriteArrayList<Runnable>()
    @Volatile private var _cancelled = false

    val isCancelled: Boolean get() = _cancelled

    fun cancel() {
        if (_cancelled) return
        _cancelled = true
        val copy = listeners.toList()
        listeners.clear()
        copy.forEach { it.run() }
    }

    /** 注册取消回调，若已取消则立即执行 */
    fun onCancel(callback: Runnable) {
        if (_cancelled) callback.run()
        else listeners.add(callback)
    }
}
