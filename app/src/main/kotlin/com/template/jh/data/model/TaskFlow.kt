package com.template.jh.data.model

import java.util.UUID

// 对话流任务项
data class TaskItem(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val status: TaskStatus = TaskStatus.Pending,
    val description: String = "",
    val timestamp: Long = System.currentTimeMillis(),
)

enum class TaskStatus {
    Pending,       // 待处理
    Running,       // 执行中
    Completed,     // 已完成
    Failed,        // 失败/异常
    WaitingAuth,   // 等待用户授权
    Interrupted,   // 被中断
}

// 通知事件类型
enum class NotificationEventType {
    TaskCompleted,        // 任务完成
    TaskFailed,           // 任务失败/异常打断
    WaitingUserAction,    // 等待用户操作（授权）
}

// 对话流通知设置
data class NotificationSettings(
    val taskCompletedSound: Boolean = true,
    val taskCompletedPopup: Boolean = true,
    val taskFailedSound: Boolean = true,
    val taskFailedPopup: Boolean = true,
    val waitingUserActionSound: Boolean = true,
    val waitingUserActionPopup: Boolean = true,
    val deleteCardEnabled: Boolean = false, // 删除行为卡片：默认关闭(显示卡片)，开启时隐藏
)
