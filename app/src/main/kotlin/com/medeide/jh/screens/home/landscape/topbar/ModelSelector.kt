package com.medeide.jh.screens.home.landscape.topbar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.medeide.jh.R
import com.medeide.jh.model.chat.CloudModelProfile
import com.medeide.jh.model.chat.EngineStatus
import com.medeide.jh.model.chat.ModelInfo

@Composable
fun ModelSelector(
    engineStatus: EngineStatus,
    modelName: String,
    availableModels: List<ModelInfo>,
    cloudProfiles: List<CloudModelProfile>,
    activeCloudProfileId: String,
    cloudModelEnabled: Boolean,
    onScanModels: () -> Unit,
    onLoadModel: (String) -> Unit,
    onBrowseModelFile: () -> Unit,
    onSwitchCloudProfile: (String) -> Unit,
    dropdownMaxHeight: Dp,
    modifier: Modifier = Modifier,
) {
    var modelMenuExpanded by remember { mutableStateOf(false) }
    val isCloudActive = cloudModelEnabled && activeCloudProfileId.isNotBlank()
    val activeCloudProfile = if (isCloudActive) cloudProfiles.find { it.id == activeCloudProfileId } else null
    val activeModelLabel = when {
        isCloudActive && activeCloudProfile != null -> activeCloudProfile.name.ifEmpty { activeCloudProfile.modelName }
        modelName.isNotBlank() -> modelName.take(16)
        else -> stringResource(R.string.model_no_model)
    }

    Box(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clickable { modelMenuExpanded = true; onScanModels() }
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            if (isCloudActive) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(Color(0xFF4CAF50)))
            } else {
                ModelStatusDot(engineStatus)
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = activeModelLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        DropdownMenu(
            expanded = modelMenuExpanded,
            onDismissRequest = { modelMenuExpanded = false },
            modifier = Modifier.heightIn(max = dropdownMaxHeight)
        ) {
            // 头部：当前模型状态
            DropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        if (isCloudActive) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF4CAF50)))
                        } else {
                            ModelStatusDot(engineStatus)
                        }
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                text = activeModelLabel,
                                style = MaterialTheme.typography.labelMedium,
                            )
                            Text(
                                text = when {
                                    isCloudActive -> "云端 · ${activeCloudProfile?.modelName ?: ""}"
                                    else -> modelStatusText(engineStatus)
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isCloudActive) Color(0xFF4CAF50) else modelStatusColor(engineStatus),
                            )
                        }
                    }
                },
                onClick = { },
                enabled = false,
            )
            HorizontalDivider()
            DropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.FolderOpen, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.model_browse_file_btn), style = MaterialTheme.typography.labelMedium)
                    }
                },
                onClick = {
                    modelMenuExpanded = false
                    onBrowseModelFile()
                },
            )
            // 可用本地模型列表
            if (availableModels.isNotEmpty()) {
                HorizontalDivider()
                Text("本地模型", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                availableModels.forEach { model ->
                    val isActive = !isCloudActive && modelName == model.name
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = model.name,
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        onClick = {
                            modelMenuExpanded = false
                            onLoadModel(model.path)
                        },
                        trailingIcon = {
                            if (isActive) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        },
                    )
                }
            } else if (engineStatus != EngineStatus.Loading) {
                HorizontalDivider()
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(R.string.model_no_models_found),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    onClick = { },
                    enabled = false,
                )
            }
            // 云端模型配置列表
            if (cloudProfiles.isNotEmpty()) {
                HorizontalDivider()
                Text("云端模型", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                cloudProfiles.forEach { profile ->
                    val isActiveCloudItem = isCloudActive && profile.id == activeCloudProfileId
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(
                                    text = profile.name.ifEmpty { profile.modelName },
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = profile.modelName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        },
                        onClick = {
                            modelMenuExpanded = false
                            onSwitchCloudProfile(profile.id)
                        },
                        trailingIcon = {
                            if (isActiveCloudItem) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        },
                    )
                }
            }
        }
    }
}

private fun modelStatusText(status: EngineStatus): String = when (status) {
    EngineStatus.Idle -> "未加载"
    EngineStatus.Loading -> "加载中…"
    EngineStatus.Ready -> "就绪"
    EngineStatus.Error -> "错误"
}

@Composable
private fun modelStatusColor(status: EngineStatus): Color = when (status) {
    EngineStatus.Ready -> Color(0xFF4CAF50)
    EngineStatus.Loading -> MaterialTheme.colorScheme.primary
    EngineStatus.Error -> MaterialTheme.colorScheme.error
    EngineStatus.Idle -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun ModelStatusDot(status: EngineStatus) {
    when (status) {
        EngineStatus.Ready ->
            Box(Modifier.size(6.dp).clip(CircleShape).background(Color(0xFF4CAF50)))
        EngineStatus.Loading ->
            CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 1.5.dp)
        EngineStatus.Error ->
            Box(Modifier.size(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.error))
        EngineStatus.Idle ->
            Box(Modifier.size(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurfaceVariant))
    }
}
