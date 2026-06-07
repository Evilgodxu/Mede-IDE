package com.template.jh.core.ai

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

// 文件操作事件：create/modify/delete 成功后通知 UI
object FileOperationEvents {
    private val _events = MutableSharedFlow<FileEvent>(extraBufferCapacity = 10)
    val events: SharedFlow<FileEvent> = _events.asSharedFlow()

    fun notify(path: String, operation: String, lineChanges: Int = 0, originalContent: String = "", newContent: String = "") {
        _events.tryEmit(FileEvent(path, operation, lineChanges, originalContent, newContent))
    }
}

data class FileEvent(
    val path: String,
    val operation: String, // create / modify / delete
    val lineChanges: Int = 0, // 行数变化
    val originalContent: String = "", // 修改前内容（用于 diff 和高亮）
    val newContent: String = "", // 修改后内容（用于 diff 和高亮）
)
