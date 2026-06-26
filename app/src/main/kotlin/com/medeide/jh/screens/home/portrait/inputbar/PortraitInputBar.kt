package com.medeide.jh.screens.home.portrait.inputbar

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medeide.jh.screens.home.cloudchat.AttachmentsPreview
import com.medeide.jh.screens.home.cloudchat.CloudChatViewModel
import com.medeide.jh.screens.home.portrait.inputbar.components.PortraitInputField
import com.medeide.jh.ui.components.VoiceInputButton

@Composable
fun PortraitInputBar(
    viewModel: CloudChatViewModel,
    onOpenSettings: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()

    val imagePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? -> uri?.let { viewModel.attachImage(it) } }

    val filePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? -> uri?.let { viewModel.attachFile(it.toString()) } }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 20.dp),
    ) {
        ModelSelectorRow(
            modelName = state.activeProfileName,
            isEnabled = state.cloudModelEnabled,
            profiles = state.cloudModelProfiles.map { it.id to it.name.ifEmpty { it.modelName } },
            activeProfileId = state.activeCloudProfileId,
            onSelectProfile = { viewModel.switchCloudProfile(it) },
            onOpenSettings = onOpenSettings,
            modifier = Modifier.fillMaxWidth(),
        )

        // 附件预览
        AttachmentsPreview(
            filePaths = state.attachedFilePaths,
            imageUris = state.attachedImageUris,
            onDetachFile = { viewModel.detachFile(it) },
            onDetachImage = { viewModel.detachImage(it) },
            modifier = Modifier.padding(bottom = 6.dp),
        )

        HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
            modifier = Modifier.padding(vertical = 6.dp),
        )

        PortraitInputField(
            inputText = state.inputText,
            onInputChange = { viewModel.setInputText(it) },
        )

        HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
            modifier = Modifier.padding(vertical = 6.dp),
        )

        ActionButtonRow(
            inputText = state.inputText,
            onInputChange = { viewModel.setInputText(it) },
            onOptimize = { viewModel.optimizeInput() },
            onFilePick = { filePickerLauncher.launch(arrayOf("*/*")) },
            onImagePick = { imagePickerLauncher.launch("image/*") },
            onSend = { viewModel.sendStreamingMessage() },
            onStop = { viewModel.cancelSend() },
            isEnabled = state.cloudModelEnabled && state.inputText.isNotBlank() && !state.isLoading,
            isSending = state.isSending,
            isOptimizing = state.isOptimizing,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ModelSelectorRow(
    modelName: String,
    isEnabled: Boolean,
    profiles: List<Pair<String, String>>,
    activeProfileId: String,
    onSelectProfile: (String) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .clip(MaterialTheme.shapes.small)
                .clickable { expanded = true }
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = if (isEnabled) modelName else "未配置模型",
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                color = if (isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Icon(Icons.Default.ArrowDropDown, null, Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (profiles.isEmpty()) {
                DropdownMenuItem(
                    text = { Text("暂无模型配置", style = MaterialTheme.typography.bodySmall) },
                    onClick = { expanded = false },
                )
            } else {
                profiles.forEach { (id, name) ->
                    val selected = id == activeProfileId
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = name,
                                fontWeight = if (selected) androidx.compose.ui.text.font.FontWeight.SemiBold
                                    else androidx.compose.ui.text.font.FontWeight.Normal,
                                color = if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface,
                            )
                        },
                        onClick = {
                            expanded = false
                            onSelectProfile(id)
                        },
                    )
                }
            }
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            DropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Settings, null, Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(6.dp))
                        Text("模型配置", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                onClick = { expanded = false; onOpenSettings() },
            )
        }
    }
}

@Composable
private fun ActionButtonRow(
    inputText: String = "",
    onInputChange: (String) -> Unit = {},
    onOptimize: () -> Unit = {},
    onFilePick: () -> Unit = {},
    onImagePick: () -> Unit = {},
    onSend: () -> Unit = {},
    onStop: () -> Unit = {},
    isEnabled: Boolean = false,
    isSending: Boolean = false,
    isOptimizing: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onOptimize, enabled = !isOptimizing, modifier = Modifier.size(40.dp)) {
                if (isOptimizing) {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(Icons.Default.AutoAwesome, "优化内容", Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            IconButton(onClick = onFilePick, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.AttachFile, "添加文件", Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onImagePick, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.AddPhotoAlternate, "添加图片", Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            VoiceInputButton(
                currentInput = inputText,
                onInputChange = onInputChange,
            )
        }

        if (isSending) {
            IconButton(onClick = onStop, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.Stop, "停止发送", Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.error)
            }
        } else {
            IconButton(onClick = onSend, enabled = isEnabled, modifier = Modifier.size(40.dp)) {
                Icon(Icons.AutoMirrored.Filled.Send, "发送消息", Modifier.size(24.dp),
                    tint = if (isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
            }
        }
    }
}
