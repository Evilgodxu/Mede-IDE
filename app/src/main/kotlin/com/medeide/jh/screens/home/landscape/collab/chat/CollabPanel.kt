package com.medeide.jh.screens.home.landscape.collab.chat

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.medeide.jh.model.chat.EngineStatus
import com.medeide.jh.screens.home.ChatViewModel
import com.medeide.jh.screens.home.landscape.collab.chat.inputbar.CollabInputBar
import com.medeide.jh.screens.home.landscape.collab.chat.inputbar.toolbar.ContextDashboard
import com.medeide.jh.screens.home.landscape.collab.chat.messagelist.CollabMessageList
import com.medeide.jh.screens.home.landscape.collab.chat.topbar.CollabTopBar

// 协作区
@OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
@Composable
fun CollabPanel(
    onSettingsClick: () -> Unit = {},
    onNewTaskClick: () -> Unit = {},
    onHistoryClick: () -> Unit = {},
    viewModel: ChatViewModel,
) {
    val state by viewModel.state.collectAsState()
    val displayItems by viewModel.displayItems.collectAsState()
    var showContextInfoDialog by remember { mutableStateOf(false) }

    val contextUsedTokens by viewModel.contextTokenCount.collectAsState()
    val contextMaxTokens = state.contextMaxTokens

    // 图片选择启动器
    val imagePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        uri?.let { viewModel.attachImage(it) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // ① 顶部栏
        CollabTopBar(
            conversations = state.conversations,
            isHistoryOpen = state.isHistoryOpen,
            onNewTaskClick = { viewModel.newConversation(); onNewTaskClick() },
            onHistoryClick = { viewModel.toggleHistory() },
            onSettingsClick = onSettingsClick,
            onSwitchConversation = { viewModel.switchConversation(it) },
            onDeleteConversation = { viewModel.deleteConversation(it) },
            onDismissHistory = { viewModel.closeHistory() },
        )

        HorizontalDivider(
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )

        // ② 聊天消息列表
        CollabMessageList(
            displayItems = displayItems,
            isLoading = state.isLoading,
            engineStatus = state.engineStatus,
            modifier = Modifier.weight(1f),
        )

        HorizontalDivider(
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )

        // ③ 消息输入框
        CollabInputBar(
            inputText = state.inputText,
            onInputChange = { viewModel.setInputText(it) },
            onSend = { viewModel.sendMessage() },
            isLoading = state.isLoading,
            isOptimizing = state.isOptimizing,
            engineStatus = state.engineStatus,
            cloudModelEnabled = state.cloudModelEnabled,
            activeCloudProfileId = state.activeCloudProfileId,
            onCancel = { viewModel.cancelGeneration() },
            onOptimize = { viewModel.optimizeInput() },
            onImagePick = { imagePickerLauncher.launch("image/*") },
            attachedImageUris = state.attachedImageUris,
            onDetachImage = { viewModel.detachImage(it) },
            attachedFileRefs = state.attachedFileRefs,
            onDetachFile = { viewModel.detachFile(it) },
            contextUsedTokens = contextUsedTokens,
            contextMaxTokens = contextMaxTokens,
            isContextCompressed = state.isContextCompressed,
            contextCompressedTokens = state.contextCompressedTokens,
            contextCompressedCount = state.contextCompressedCount,
            memoryEntryCount = state.memoryEntryCount,
            memoryTotalTokens = state.memoryTotalTokens,
            onContextInfoClick = { showContextInfoDialog = true },
            optimizeMode = viewModel.optimizeMode,
            onOptimizeModeChange = { viewModel.setOptimizeMode(it) },
        )

        if (showContextInfoDialog) {
            val dashData = remember(
                state.messages,
                state.isContextCompressed,
                state.contextCompressedTokens,
                state.memoryEntryCount,
                state.memoryTotalTokens,
                state.contextSummary
            ) {
                viewModel.buildDashboardData()
            }
            ContextDashboard(
                snapshot = dashData.snapshot,
                breakdown = dashData.breakdown,
                contextSummary = state.contextSummary,
                openedFilePaths = state.openedFilePaths,
                memoryEntryCount = state.memoryEntryCount,
                memoryTotalTokens = state.memoryTotalTokens,
                toolStats = dashData.usageStats.byTool,
                onDismiss = { showContextInfoDialog = false },
            )
        }
    }
}
