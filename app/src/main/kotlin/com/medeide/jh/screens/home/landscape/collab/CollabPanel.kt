package com.medeide.jh.screens.home.landscape.collab

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.medeide.jh.screens.home.cloudchat.CloudChatViewModel
import com.medeide.jh.screens.home.landscape.collab.inputbar.CollabInputBar
import com.medeide.jh.screens.home.landscape.collab.messagelist.CollabMessageList
import com.medeide.jh.screens.home.landscape.collab.topbar.CollabTopBar

@Composable
fun CollabPanel(
    viewModel: CloudChatViewModel,
    onSettings: () -> Unit = {},
    userName: String = "",
    agentName: String = "AI",
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        CollabTopBar(
            cloudModelEnabled = state.cloudModelEnabled,
            conversations = state.conversations,
            isHistoryOpen = state.isHistoryOpen,
            onNewConversation = { viewModel.newConversation() },
            onHistoryClick = { viewModel.toggleHistory() },
            onSettings = onSettings,
            onSwitchConversation = { viewModel.switchConversation(it) },
            onDeleteConversation = { viewModel.deleteConversation(it) },
            onDismissHistory = { viewModel.closeHistory() },
        )

        HorizontalDivider(
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        )

        CollabMessageList(
            viewModel = viewModel,
            userName = userName,
            agentName = agentName,
            modifier = Modifier.weight(1f),
        )

        HorizontalDivider(
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        )

        CollabInputBar(
            viewModel = viewModel,
        )
    }
}
