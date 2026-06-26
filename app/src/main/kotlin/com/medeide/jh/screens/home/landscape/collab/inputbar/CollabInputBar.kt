package com.medeide.jh.screens.home.landscape.collab.inputbar

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.medeide.jh.screens.home.cloudchat.AttachmentsPreview
import com.medeide.jh.screens.home.cloudchat.CloudChatViewModel
import com.medeide.jh.screens.home.landscape.collab.inputbar.field.CollabInputField
import com.medeide.jh.screens.home.landscape.collab.inputbar.toolbar.CollabToolBar

@Composable
fun CollabInputBar(
    viewModel: CloudChatViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()

    val imagePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? -> uri?.let { viewModel.attachImage(it) } }

    val filePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.attachFile(it.toString())
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        // 附件预览
        AttachmentsPreview(
            filePaths = state.attachedFilePaths,
            imageUris = state.attachedImageUris,
            onDetachFile = { viewModel.detachFile(it) },
            onDetachImage = { viewModel.detachImage(it) },
            modifier = Modifier.padding(bottom = 6.dp),
        )

        CollabInputField(
            inputText = state.inputText,
            onInputChange = { viewModel.setInputText(it) },
        )

        HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
            modifier = Modifier.padding(vertical = 4.dp),
        )

        CollabToolBar(
            inputText = state.inputText,
            onInputChange = { viewModel.setInputText(it) },
            onOptimize = { viewModel.optimizeInput() },
            onImagePick = { imagePickerLauncher.launch("image/*") },
            onFilePick = { filePickerLauncher.launch(arrayOf("*/*")) },
            onSend = { viewModel.sendStreamingMessage() },
            onStop = { viewModel.cancelSend() },
            isSending = state.isSending,
            isOptimizing = state.isOptimizing,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}
