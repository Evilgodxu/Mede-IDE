package com.medeide.jh.screens.home.logic

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object FileOperationEvents {
    private val _events = MutableSharedFlow<FileEvent>(extraBufferCapacity = 10)
    val events: SharedFlow<FileEvent> = _events.asSharedFlow()

    fun notify(path: String, operation: String) {
        val event = FileEvent(path, operation)
        val sent = _events.tryEmit(event)
        android.util.Log.d("FileOperationEvents",
            "notify: path=$path, operation=$operation, sent=$sent")
    }
}

data class FileEvent(
    val path: String,
    val operation: String,
)
