package com.template.jh.data.model

import java.util.UUID

// 对话流任务项
data class TaskItem(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val status: TaskStatus = TaskStatus.Pending,
    val description: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val isSubTask: Boolean = false, // 是否为大模型生成的子任务
    val parentTaskId: String? = null, // 父任务ID
    val order: Int = 0, // 子任务顺序
)

enum class TaskStatus {
    Pending,       // 待处理
    Running,       // 执行中
    Completed,     // 已完成
    Failed,        // 失败/异常
    WaitingAuth,   // 等待用户授权
    Interrupted,   // 被中断
}

// 任务清单解析结果
data class ParsedTaskList(
    val tasks: List<String>,
    val completedCount: Int,
    val totalCount: Int,
) {
    companion object {
        fun parse(content: String): ParsedTaskList? {
            val taskRegex = Regex("""\[tasks\](.*?)\[/tasks\]""", RegexOption.DOT_MATCHES_ALL)
            val match = taskRegex.find(content) ?: return null

            val taskContent = match.groupValues[1].trim()
            val lines = taskContent.lines()

            val tasks = mutableListOf<String>()
            var completedCount = 0

            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.isEmpty()) continue

                // 匹配任务项格式: 1. [ ] 任务描述 或 1. [x] 任务描述
                val taskMatch = Regex("""^\d+\.\s*\[([ x])\]\s*(.+)$""").find(trimmed)
                if (taskMatch != null) {
                    val isCompleted = taskMatch.groupValues[1] == "x"
                    val taskDesc = taskMatch.groupValues[2].trim()
                    tasks.add(taskDesc)
                    if (isCompleted) completedCount++
                }
            }

            return if (tasks.isNotEmpty()) {
                ParsedTaskList(tasks, completedCount, tasks.size)
            } else null
        }

        // 检查是否包含任务完成标记
        fun hasCompleteMarker(content: String): Boolean {
            return content.contains("[task_complete]")
        }
    }
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
