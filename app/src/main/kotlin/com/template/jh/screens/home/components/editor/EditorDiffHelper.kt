package com.template.jh.screens.home.components.editor

import com.template.jh.core.editor.ChangeBlockStatus
import com.template.jh.core.editor.CodeReviewState
import com.template.jh.core.editor.LineChangeType

// Diff 辅助函数 - 单一职责：行级diff计算
fun computeNewLineDiffs(state: CodeReviewState): Map<Int, LineChangeType> {
    val result = mutableMapOf<Int, LineChangeType>()
    for (block in state.changeBlocks) {
        if (block.status == ChangeBlockStatus.ACCEPTED) continue
        if (block.newStartLine > 0) {
            for (i in block.newStartLine - 1 until block.newEndLine - 1) {
                result[i] = if (block.type == LineChangeType.Removed) {
                    LineChangeType.Unchanged
                } else {
                    block.type
                }
            }
        }
    }
    return result
}

// 从审查状态计算已接受的删除行索引集合
fun computeRemovedLineIndices(state: CodeReviewState): Set<Int> {
    val indices = mutableSetOf<Int>()
    for (block in state.changeBlocks) {
        if (block.status != ChangeBlockStatus.ACCEPTED) continue
        if (block.type == LineChangeType.Removed && block.oldStartLine > 0) {
            for (i in block.oldStartLine - 1 until block.oldEndLine - 1) {
                indices.add(i)
            }
        }
    }
    return indices
}
