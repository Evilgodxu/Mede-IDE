package com.medeide.jh.screens.home.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medeide.jh.data.source.local.LiteRTManager
import com.medeide.jh.model.chat.BackendType
import com.medeide.jh.model.chat.DownloadStatus
import com.medeide.jh.model.chat.EngineStatus
import com.medeide.jh.model.chat.ModelParams
import com.medeide.jh.screens.home.ChatViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// 本地模型设置内容
@Composable
fun LocalModelSettingsContent(chatViewModel: ChatViewModel?) {
    if (chatViewModel == null) { CategoryPlaceholder("模型管理不可用"); return }
    val chatState by chatViewModel.state.collectAsState()
    val context = LocalContext.current
    val preferencesRepo = remember { com.medeide.jh.data.repository.UserPreferencesRepository(context) }
    val autoLoad by preferencesRepo.autoLoadLastModel.collectAsState(initial = true)
    val deepThinkEnabled by preferencesRepo.deepThinkEnabled.collectAsState(initial = true)
    var topK by remember(chatState.modelParams) { mutableIntStateOf(chatState.modelParams.topK) }
    var topP by remember(chatState.modelParams) { mutableFloatStateOf(chatState.modelParams.topP.toFloat()) }
    var temperature by remember(chatState.modelParams) { mutableFloatStateOf(chatState.modelParams.temperature.toFloat()) }
    var seed by remember(chatState.modelParams) { mutableIntStateOf(chatState.modelParams.seed) }
    var ctxTokens by remember(chatState.modelParams) { mutableIntStateOf(chatState.modelParams.contextWindowTokens) }
    var mtpEnabled by remember(chatState.modelParams) { mutableStateOf(chatState.modelParams.enableSpeculativeDecoding) }
    var backendType by remember(chatState.modelParams) { mutableStateOf(chatState.modelParams.backendType) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // 启动时自动加载上次模型
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("启动时自动加载模型", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text("IDE启动时自动加载上次使用的本地模型", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = autoLoad,
                    onCheckedChange = {
                        kotlinx.coroutines.runBlocking {
                            preferencesRepo.setAutoLoadLastModel(it)
                        }
                    },
                )
            }
        }

        // 模型参数（可折叠）
        var isParamsExpanded by remember { mutableStateOf(false) }
        var isApplyingParams by remember { mutableStateOf(false) }
        Card(
            modifier = Modifier.fillMaxWidth().clickable { isParamsExpanded = !isParamsExpanded },
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("模型参数", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    Icon(
                        imageVector = if (isParamsExpanded) Icons.Default.ArrowDropDown else Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp).let { if (!isParamsExpanded) it else it }
                    )
                }

                if (isParamsExpanded) {
                    // topK
                    Text("Top-K: $topK", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Slider(value = topK.toFloat(), onValueChange = { topK = it.toInt() }, valueRange = 1f..100f, steps = 98, modifier = Modifier.fillMaxWidth())

                    // topP
                    Text("Top-P: ${"%.2f".format(topP)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Slider(value = topP, onValueChange = { topP = it }, valueRange = 0f..1f, modifier = Modifier.fillMaxWidth())

                    // temperature
                    Text("Temperature: ${"%.2f".format(temperature)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Slider(value = temperature, onValueChange = { temperature = it }, valueRange = 0f..2f, modifier = Modifier.fillMaxWidth())

                    // seed
                    Text("Seed: $seed (0=随机)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Slider(value = seed.toFloat(), onValueChange = { seed = it.toInt() }, valueRange = 0f..9999f, steps = 9998, modifier = Modifier.fillMaxWidth())

                    // contextWindowTokens
                    val ctxStr = if (ctxTokens >= 1024) "${ctxTokens / 1024}k" else "$ctxTokens"
                    Text("上下文窗口: $ctxStr tokens (KV-cache)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Slider(value = ctxTokens.toFloat(), onValueChange = { ctxTokens = it.toInt() }, valueRange = 512f..32768f, steps = 63, modifier = Modifier.fillMaxWidth())

                    // 深度思考
                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    Text("深度思考", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("启用深度思考", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        Switch(
                            checked = deepThinkEnabled,
                            onCheckedChange = {
                                kotlinx.coroutines.runBlocking { preferencesRepo.setDeepThinkEnabled(it) }
                            },
                        )
                    }

                    // MTP 推测解码
                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("MTP 推测解码", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                            Text("GPU/NPU 后端效果显著，应用后重新加载", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = mtpEnabled, onCheckedChange = { mtpEnabled = it })
                    }

                    // 推理后端
                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    Text("推理后端", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                    Text("切换后需重新加载模型", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        BackendType.entries.forEach { type ->
                            FilterChip(
                                selected = backendType == type,
                                onClick = { backendType = type },
                                label = { Text(type.displayName, style = MaterialTheme.typography.labelMedium) },
                            )
                        }
                    }

                    val scope = rememberCoroutineScope()
                    val isModelLoading = chatState.engineStatus == EngineStatus.Loading || isApplyingParams
                    Button(onClick = {
                        if (isApplyingParams) return@Button
                        isApplyingParams = true
                        val params = ModelParams(topK = topK, topP = topP.toDouble(), temperature = temperature.toDouble(), seed = seed, contextWindowTokens = ctxTokens, enableSpeculativeDecoding = mtpEnabled, backendType = backendType)
                        chatViewModel.setModelParams(params)
                        scope.launch { delay(1000); isApplyingParams = false }
                    }, modifier = Modifier.fillMaxWidth(), enabled = !isModelLoading) {
                        if (isModelLoading) {
                            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text("加载中…")
                        } else {
                            Text("应用参数")
                        }
                    }
                }
            }
        }

        // 已检测模型
        val hasDetectedModels = chatState.availableModels.isNotEmpty()
        if (hasDetectedModels) {
            Text("已检测模型", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            chatState.availableModels.forEach { model ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(model.name, style = MaterialTheme.typography.bodyMedium)
                            Text(model.sizeText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("已就绪", style = MaterialTheme.typography.labelSmall, color = Color(0xFF4CAF50))
                    }
                }
            }
        }

        // 推荐下载
        Text("推荐下载模型", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)

        LiteRTManager.RECOMMENDED_MODELS.forEach { model ->
            val isDetected = chatState.availableModels.any { it.path.contains(model.fileName, ignoreCase = true) }
            val isCompleted = chatState.downloadStatus == DownloadStatus.Completed && chatState.downloadFileName == model.fileName
            if (isDetected || isCompleted) return@forEach

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)),
                shape = RoundedCornerShape(8.dp),
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(model.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text("${model.size}  |  ${model.description}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if ((chatState.downloadStatus == DownloadStatus.Downloading || chatState.downloadStatus == DownloadStatus.Paused) && chatState.downloadFileName == model.fileName) {
                        LinearProgressIndicator(progress = { chatState.downloadProgress }, modifier = Modifier.fillMaxWidth())
                        val isPaused = chatState.downloadStatus == DownloadStatus.Paused
                        Text("${if (isPaused) "已暂停" else "下载中"}… ${"%.0f".format(chatState.downloadProgress * 100)}%",
                            style = MaterialTheme.typography.labelSmall, color = if (isPaused) Color(0xFFFFA000) else MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            if (isPaused) {
                                Button(onClick = { chatViewModel.resumeDownload() }, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Default.Download, null, Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("继续")
                                }
                            } else {
                                Button(onClick = { chatViewModel.pauseDownload() }, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Default.Pause, null, Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("暂停")
                                }
                            }
                            OutlinedButton(onClick = { chatViewModel.cancelDownload() }, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.Close, null, Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("取消")
                            }
                        }
                    } else if (chatState.downloadStatus == DownloadStatus.Error && chatState.downloadFileName == model.fileName) {
                        Text("下载失败: ${chatState.downloadErrorMessage}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                        Button(onClick = {
                            chatViewModel.resetDownload()
                            chatViewModel.downloadModel(model.url, model.fileName)
                        }, modifier = Modifier.fillMaxWidth()) {
                            Text("重试")
                        }
                    } else {
                        Button(
                            onClick = { chatViewModel.downloadModel(model.url, model.fileName) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Default.Download, null, Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("下载模型")
                        }
                    }
                }
            }
        }
    }
}
