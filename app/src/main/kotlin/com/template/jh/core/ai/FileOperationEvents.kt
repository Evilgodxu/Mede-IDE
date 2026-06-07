package com.template.jh.core.ai

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

// 文件操作事件：create/modify/delete 成功后通知 UI
object FileOperationEvents {
    private val _events = MutableSharedFlow<FileEvent>(extraBufferCapacity = 10)
    val events: SharedFlow<FileEvent> = _events.asSharedFlow()

    // 审查操作事件流
    private val _reviewEvents = MutableSharedFlow<ReviewEvent>(extraBufferCapacity = 10)
    val reviewEvents: SharedFlow<ReviewEvent> = _reviewEvents.asSharedFlow()

    fun notify(path: String, operation: String, lineChanges: Int = 0, originalContent: String = "", newContent: String = "") {
        val event = FileEvent(path, operation, lineChanges, originalContent, newContent)
        val sent = _events.tryEmit(event)
        android.util.Log.d("FileOperationEvents", "notify: path=$path, operation=$operation, sent=$sent, originalContent=${originalContent.length}, newContent=${newContent.length}")
    }

    // 通知接受文件的所有修改
    fun notifyAcceptAll(path: String) {
        val sent = _reviewEvents.tryEmit(ReviewEvent(path, ReviewAction.AcceptAll))
        android.util.Log.d("FileOperationEvents", "notifyAcceptAll: path=$path, sent=$sent")
    }

    // 通知拒绝文件的所有修改
    fun notifyRejectAll(path: String) {
        val sent = _reviewEvents.tryEmit(ReviewEvent(path, ReviewAction.RejectAll))
        android.util.Log.d("FileOperationEvents", "notifyRejectAll: path=$path, sent=$sent")
    }
}

data class FileEvent(
    val path: String,
    val operation: String, // create / modify / delete / overwrite
    val lineChanges: Int = 0, // 行数变化
    val originalContent: String = "", // 修改前内容（用于 diff 和高亮）
    val newContent: String = "", // 修改后内容（用于 diff 和高亮）
)

// 审查操作类型
enum class ReviewAction { AcceptAll, RejectAll }

// 审查事件
data class ReviewEvent(
    val path: String,
    val action: ReviewAction,
)
