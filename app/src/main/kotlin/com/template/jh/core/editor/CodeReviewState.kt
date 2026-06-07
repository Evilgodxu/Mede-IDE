package com.template.jh.core.editor

import androidx.compose.ui.graphics.Color

/**
 * 修改块状态
 */
enum class ChangeBlockStatus {
    PENDING,    // 未审查
    ACCEPTED,   // 已接受（保存此修改）
    REJECTED,   // 已拒绝（丢弃此修改）
}

/**
 * 修改块 - 代表一组连续的变更行
 */
data class ChangeBlock(
    val id: Int,                          // 唯一标识
    val status: ChangeBlockStatus,        // 当前状态
    val oldStartLine: Int,                // 旧文件起始行（1-based，-1表示无旧行）
    val oldEndLine: Int,                  // 旧文件结束行（1-based，exclusive）
    val newStartLine: Int,                // 新文件起始行（1-based，-1表示无新行）
    val newEndLine: Int,                  // 新文件结束行（1-based，exclusive）
    val type: LineChangeType,             // 变更类型
    val oldContent: List<String>,         // 旧代码内容
    val newContent: List<String>,         // 新代码内容
) {
    // 是否已审查（接受或拒绝）
    val isResolved: Boolean
        get() = status == ChangeBlockStatus.ACCEPTED || status == ChangeBlockStatus.REJECTED
}

/**
 * 代码审查状态 - 管理文件的所有修改块
 */
data class CodeReviewState(
    val filePath: String,
    val oldContent: String,               // 原始（旧）代码内容
    val newContent: String,               // 新代码内容
    val changeBlocks: List<ChangeBlock>,  // 所有修改块
    val currentBlockIndex: Int = 0,       // 当前聚焦的修改块索引
) {
    /**
     * 获取未审查的修改块数量
     */
    val pendingCount: Int
        get() = changeBlocks.count { it.status == ChangeBlockStatus.PENDING }

    /**
     * 获取已接受的修改块数量
     */
    val acceptedCount: Int
        get() = changeBlocks.count { it.status == ChangeBlockStatus.ACCEPTED }

    /**
     * 获取已拒绝的修改块数量
     */
    val rejectedCount: Int
        get() = changeBlocks.count { it.status == ChangeBlockStatus.REJECTED }

    /**
     * 获取总修改块数量
     */
    val totalCount: Int
        get() = changeBlocks.size

    /**
     * 是否所有修改都已处理
     */
    val isAllResolved: Boolean
        get() = pendingCount == 0 && totalCount > 0

    /**
     * 获取下一个未审查的修改块索引
     */
    fun nextPendingIndex(): Int {
        val start = currentBlockIndex
        for (i in changeBlocks.indices) {
            val idx = (start + i + 1) % changeBlocks.size
            if (changeBlocks[idx].status == ChangeBlockStatus.PENDING) {
                return idx
            }
        }
        return -1
    }

    /**
     * 获取上一个未审查的修改块索引
     */
    fun prevPendingIndex(): Int {
        val start = currentBlockIndex
        for (i in changeBlocks.indices) {
            val idx = (start - i - 1 + changeBlocks.size) % changeBlocks.size
            if (changeBlocks[idx].status == ChangeBlockStatus.PENDING) {
                return idx
            }
        }
        return -1
    }

    /**
     * 接受指定修改块（保存此修改）
     */
    fun acceptBlock(index: Int): CodeReviewState {
        if (index !in changeBlocks.indices) return this
        val updated = changeBlocks.toMutableList()
        updated[index] = updated[index].copy(status = ChangeBlockStatus.ACCEPTED)
        return copy(changeBlocks = updated)
    }

    /**
     * 拒绝指定修改块（丢弃此修改）
     */
    fun rejectBlock(index: Int): CodeReviewState {
        if (index !in changeBlocks.indices) return this
        val updated = changeBlocks.toMutableList()
        updated[index] = updated[index].copy(status = ChangeBlockStatus.REJECTED)
        return copy(changeBlocks = updated)
    }

    /**
     * 接受所有未审查的修改块
     */
    fun acceptAll(): CodeReviewState {
        val updated = changeBlocks.map { block ->
            if (block.status == ChangeBlockStatus.PENDING) {
                block.copy(status = ChangeBlockStatus.ACCEPTED)
            } else block
        }
        return copy(changeBlocks = updated)
    }

    /**
     * 拒绝所有未审查的修改块
     */
    fun rejectAll(): CodeReviewState {
        val updated = changeBlocks.map { block ->
            if (block.status == ChangeBlockStatus.PENDING) {
                block.copy(status = ChangeBlockStatus.REJECTED)
            } else block
        }
        return copy(changeBlocks = updated)
    }

    /**
     * 设置当前聚焦的修改块
     */
    fun setCurrentIndex(index: Int): CodeReviewState {
        if (index !in changeBlocks.indices) return this
        return copy(currentBlockIndex = index)
    }

    /**
     * 生成最终内容（根据接受/拒绝状态合并）
     * 已接受：使用新内容
     * 已拒绝：使用旧内容
     * 未审查：使用新内容（默认）
     */
    fun generateFinalContent(): String {
        if (changeBlocks.isEmpty()) return newContent

        val oldLines = oldContent.lines().toMutableList()
        val result = mutableListOf<String>()
        var currentLine = 0

        // 按旧文件行号排序处理修改块
        val sortedBlocks = changeBlocks.sortedBy { it.oldStartLine }

        for (block in sortedBlocks) {
            // 添加当前块之前的未变更行
            val blockStart = if (block.oldStartLine > 0) block.oldStartLine - 1 else currentLine
            while (currentLine < blockStart && currentLine < oldLines.size) {
                result.add(oldLines[currentLine])
                currentLine++
            }

            // 根据状态决定使用旧内容还是新内容
            when (block.status) {
                ChangeBlockStatus.ACCEPTED, ChangeBlockStatus.PENDING -> {
                    // 使用新内容
                    result.addAll(block.newContent)
                }
                ChangeBlockStatus.REJECTED -> {
                    // 使用旧内容
                    result.addAll(block.oldContent)
                }
            }

            // 跳过旧文件中被替换的行
            if (block.oldEndLine > 0) {
                currentLine = block.oldEndLine - 1
            }
        }

        // 添加剩余的行
        while (currentLine < oldLines.size) {
            result.add(oldLines[currentLine])
            currentLine++
        }

        return result.joinToString("\n")
    }

    /**
     * 生成应用了已接受修改的新内容
     * 用于实时预览已接受的修改
     */
    fun generateAcceptedContent(): String {
        val oldLines = oldContent.lines().toMutableList()
        val result = mutableListOf<String>()
        var currentLine = 0

        val sortedBlocks = changeBlocks.sortedBy { it.oldStartLine }

        for (block in sortedBlocks) {
            val blockStart = if (block.oldStartLine > 0) block.oldStartLine - 1 else currentLine
            while (currentLine < blockStart && currentLine < oldLines.size) {
                result.add(oldLines[currentLine])
                currentLine++
            }

            when (block.status) {
                ChangeBlockStatus.ACCEPTED -> {
                    result.addAll(block.newContent)
                    if (block.oldEndLine > 0) {
                        currentLine = block.oldEndLine - 1
                    }
                }
                ChangeBlockStatus.REJECTED -> {
                    result.addAll(block.oldContent)
                    if (block.oldEndLine > 0) {
                        currentLine = block.oldEndLine - 1
                    }
                }
                ChangeBlockStatus.PENDING -> {
                    // 未审查的也使用新内容（预览模式）
                    result.addAll(block.newContent)
                    if (block.oldEndLine > 0) {
                        currentLine = block.oldEndLine - 1
                    }
                }
            }
        }

        while (currentLine < oldLines.size) {
            result.add(oldLines[currentLine])
            currentLine++
        }

        return result.joinToString("\n")
    }
}

/**
 * 获取状态对应的颜色
 */
fun ChangeBlockStatus.color(): Color = when (this) {
    ChangeBlockStatus.PENDING -> Color(0xFFCCAA00)    // 黄色
    ChangeBlockStatus.ACCEPTED -> Color(0xFF22CC22)   // 绿色
    ChangeBlockStatus.REJECTED -> Color(0xFFCC2222)   // 红色
}

/**
 * 获取状态对应的背景色（半透明）
 */
fun ChangeBlockStatus.backgroundColor(): Color = when (this) {
    ChangeBlockStatus.PENDING -> Color(0x55CCAA00)
    ChangeBlockStatus.ACCEPTED -> Color(0x5522CC22)
    ChangeBlockStatus.REJECTED -> Color(0x55CC2222)
}

/**
 * 获取状态显示文本
 */
fun ChangeBlockStatus.displayText(): String = when (this) {
    ChangeBlockStatus.PENDING -> "待审查"
    ChangeBlockStatus.ACCEPTED -> "已接受"
    ChangeBlockStatus.REJECTED -> "已拒绝"
}
