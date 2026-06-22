package com.medeide.jh.screens.home.landscape.collab.chat.inputbar

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.medeide.jh.model.chat.AttachedFile
import com.medeide.jh.model.chat.EngineStatus
import com.medeide.jh.screens.home.landscape.collab.chat.inputbar.attachment.CollabAttachmentBar
import com.medeide.jh.screens.home.landscape.collab.chat.inputbar.field.CollabInputField
import com.medeide.jh.screens.home.landscape.collab.chat.inputbar.toolbar.CollabToolBar

// 协作区消息输入框 - 编排层
@Composable
fun CollabInputBar(
    inputText: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    isLoading: Boolean,
    isOptimizing: Boolean,
    engineStatus: EngineStatus,
    cloudModelEnabled: Boolean = false,
    activeCloudProfileId: String = "",
    onCancel: () -> Unit,
    onOptimize: () -> Unit,
    onImagePick: () -> Unit,
    attachedImageUris: List<android.net.Uri> = emptyList(),
    onDetachImage: (android.net.Uri) -> Unit = {},
    attachedFileRefs: List<AttachedFile> = emptyList(),
    onDetachFile: (Int) -> Unit = {},
    contextUsedTokens: Int = 0,
    contextMaxTokens: Int = 128000,
    isContextCompressed: Boolean = false,
    contextCompressedTokens: Int = 0,
    contextCompressedCount: Int = 0,
    memoryEntryCount: Int = 0,
    memoryTotalTokens: Int = 0,
    onContextInfoClick: () -> Unit = {},
    optimizeMode: com.medeide.jh.screens.home.aitools.InputOptimizer.Mode = com.medeide.jh.screens.home.aitools.InputOptimizer.Mode.CODE,
    onOptimizeModeChange: (com.medeide.jh.screens.home.aitools.InputOptimizer.Mode) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // 本地模型就绪 或 云端模型已配置 均可交互
    val canInteract = engineStatus == EngineStatus.Ready ||
            (cloudModelEnabled && activeCloudProfileId.isNotBlank())

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        // ③-1 文本输入框
        CollabInputField(
            inputText = inputText,
            onInputChange = onInputChange,
            enabled = canInteract,
        )

        // ③-2 附件区
        CollabAttachmentBar(
            attachedFileRefs = attachedFileRefs,
            onDetachFile = onDetachFile,
            attachedImageUris = attachedImageUris,
            onDetachImage = onDetachImage,
        )

        // ③-3 功能工具栏
        CollabToolBar(
            inputText = inputText,
            onInputChange = onInputChange,
            isLoading = isLoading,
            isOptimizing = isOptimizing,
            enabled = canInteract,
            onCancel = onCancel,
            onOptimize = onOptimize,
            onSend = onSend,
            onImagePick = onImagePick,
            hasAttachments = attachedImageUris.isNotEmpty() || attachedFileRefs.isNotEmpty(),
            contextUsedTokens = contextUsedTokens,
            contextMaxTokens = contextMaxTokens,
            isContextCompressed = isContextCompressed,
            contextCompressedTokens = contextCompressedTokens,
            contextCompressedCount = contextCompressedCount,
            memoryEntryCount = memoryEntryCount,
            memoryTotalTokens = memoryTotalTokens,
            onContextInfoClick = onContextInfoClick,
            optimizeMode = optimizeMode,
            onOptimizeModeChange = onOptimizeModeChange,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
