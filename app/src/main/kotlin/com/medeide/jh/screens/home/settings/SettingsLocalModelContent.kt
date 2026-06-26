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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.medeide.jh.data.source.local.LiteRTManager
import com.medeide.jh.model.chat.BackendType
import com.medeide.jh.model.chat.EngineStatus
import com.medeide.jh.model.chat.ModelParams
import com.medeide.jh.screens.home.localchat.LocalChatViewModel

/** 本地模型设置 — 对接 LiteRT-LM 引擎 */
@Composable
fun SettingsLocalModelContent(
    viewModel: LocalChatViewModel? = null,
) {
    val state = viewModel?.state?.collectAsState()
    val uiState = state?.value

    var topK by remember { mutableIntStateOf(uiState?.modelParams?.topK ?: 10) }
    var topP by remember { mutableFloatStateOf((uiState?.modelParams?.topP ?: 0.7).toFloat()) }
    var temperature by remember { mutableFloatStateOf((uiState?.modelParams?.temperature ?: 0.1).toFloat()) }
    var seed by remember { mutableIntStateOf(uiState?.modelParams?.seed ?: 0) }
    var ctxTokens by remember { mutableIntStateOf(uiState?.modelParams?.contextWindowTokens ?: 32768) }
    var mtpEnabled by remember { mutableStateOf(uiState?.modelParams?.enableSpeculativeDecoding ?: false) }
    var backendType by remember { mutableStateOf(uiState?.modelParams?.backendType ?: BackendType.GPU) }
    var isParamsExpanded by remember { mutableStateOf(false) }

    val engineStatus = uiState?.engineStatus ?: EngineStatus.Idle
    val detectedModels = uiState?.detectedModels ?: emptyList()

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // ── 引擎状态 ──
        EngineStatusCard(engineStatus = engineStatus, errorMessage = uiState?.engineErrorMessage.orEmpty())

        // ── 已检测模型 ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("已检测模型 (${detectedModels.size})", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            IconButton(onClick = { viewModel?.refreshDetectedModels() }) {
                Icon(Icons.Default.Refresh, "刷新", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (detectedModels.isEmpty()) {
            Text("未找到 .litertlm 模型文件。下载后将显示在这里。",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            detectedModels.forEach { model ->
                val isActive = model.fileName == uiState?.loadedModelName
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    ),
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(model.displayName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                if (isActive) {
                                    Spacer(Modifier.width(6.dp))
                                    Text("已加载", style = MaterialTheme.typography.labelSmall, color = Color(0xFF4CAF50))
                                }
                            }
                            val sizeMb = if (model.sizeBytes > 0) "${model.sizeBytes / 1024 / 1024} MB" else ""
                            Text(sizeMb, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (isActive) {
                            OutlinedButton(onClick = { viewModel?.unloadModel() }) {
                                Text("卸载")
                            }
                        } else {
                            Button(onClick = { viewModel?.loadModel(model.path) }) {
                                Text("加载")
                            }
                        }
                        Spacer(Modifier.width(4.dp))
                        IconButton(onClick = { viewModel?.deleteModel(model.fileName) }) {
                            Icon(Icons.Default.Delete, "删除", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }

        // ── 模型参数（可折叠） ──
        Card(
            modifier = Modifier.fillMaxWidth().clickable { isParamsExpanded = !isParamsExpanded },
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("模型参数", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Icon(Icons.Default.ArrowDropDown, null, Modifier.size(24.dp))
                }

                if (isParamsExpanded) {
                    ParamSlider("Top-K", topK.toString(), { topK = it.toInt() }, topK.toFloat(), 1f..100f, 98)
                    ParamSlider("Top-P", "%.2f".format(topP), { topP = it.toFloat() }, topP, 0f..1f)
                    ParamSlider("Temperature", "%.2f".format(temperature), { temperature = it.toFloat() }, temperature, 0f..2f)
                    ParamSlider("Seed (0=随机)", seed.toString(), { seed = it.toInt() }, seed.toFloat(), 0f..9999f, 9998)

                    val ctxStr = if (ctxTokens >= 1024) "${ctxTokens / 1024}k" else "$ctxTokens"
                    ParamSlider("上下文窗口: $ctxStr tokens", ctxTokens.toString(), { ctxTokens = it.toInt() }, ctxTokens.toFloat(), 512f..32768f, 63)

                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("MTP 推测解码", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                            Text("GPU/NPU 后端效果显著", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = mtpEnabled, onCheckedChange = { mtpEnabled = it })
                    }

                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    Text("推理后端", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                    Text("切换后需重新加载模型", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        BackendType.entries.forEach { type ->
                            FilterChip(selected = backendType == type, onClick = { backendType = type },
                                label = { Text(type.displayName, style = MaterialTheme.typography.labelMedium) })
                        }
                    }

                    Button(onClick = {
                        val params = ModelParams(
                            topK = topK, topP = topP.toDouble(), temperature = temperature.toDouble(),
                            seed = seed, contextWindowTokens = ctxTokens,
                            enableSpeculativeDecoding = mtpEnabled, backendType = backendType,
                        )
                        viewModel?.setModelParams(params)
                        // 如果已有模型加载，自动重载
                        if (uiState?.isModelLoaded == true && uiState.loadedModelPath.isNotEmpty()) {
                            viewModel?.loadModel(uiState.loadedModelPath)
                        }
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text("应用参数")
                    }
                }
            }
        }

        // ── 推荐下载模型 ──
        Text("推荐下载模型", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        LiteRTManager.RECOMMENDED_MODELS.forEach { model ->
            DownloadModelCard(
                name = model.name,
                size = model.size,
                description = model.description,
                fileName = model.fileName,
                url = model.url,
                isAlreadyDownloaded = detectedModels.any { it.fileName == model.fileName },
                onDownload = { viewModel?.downloadModel(url = model.url, fileName = model.fileName) },
                onLoad = { detectedModels.find { it.fileName == model.fileName }?.path?.let { viewModel?.loadModel(it) } },
            )
        }
    }
}

@Composable
private fun EngineStatusCard(engineStatus: EngineStatus, errorMessage: String) {
    val (color, text) = when (engineStatus) {
        EngineStatus.Idle -> Color.Gray to "空闲"
        EngineStatus.Loading -> MaterialTheme.colorScheme.primary to "加载中…"
        EngineStatus.Ready -> Color(0xFF4CAF50) to "就绪"
        EngineStatus.Error -> MaterialTheme.colorScheme.error to "错误: $errorMessage"
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f)),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            when (engineStatus) {
                EngineStatus.Loading -> CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                EngineStatus.Ready -> Icon(Icons.Default.CheckCircle, null, Modifier.size(18.dp), tint = Color(0xFF4CAF50))
                EngineStatus.Error -> Icon(Icons.Default.Error, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                EngineStatus.Idle -> Icon(Icons.Default.CheckCircle, null, Modifier.size(18.dp), tint = Color.Gray)
            }
            Spacer(Modifier.width(8.dp))
            Text(text, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun DownloadModelCard(
    name: String,
    size: String,
    description: String,
    fileName: String,
    url: String,
    isAlreadyDownloaded: Boolean,
    onDownload: () -> Unit,
    onLoad: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text("$size  |  $description", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isAlreadyDownloaded) {
                    Button(onClick = onLoad, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.PlayArrow, null, Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("加载")
                    }
                } else if (url.isNotEmpty()) {
                    Button(onClick = onDownload, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Download, null, Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("下载模型")
                    }
                } else {
                    OutlinedButton(onClick = { }, modifier = Modifier.weight(1f)) {
                        Text("自行放置")
                    }
                }
            }
        }
    }
}

@Composable
private fun ParamSlider(label: String, _value: String, onValueChange: (Float) -> Unit, sliderValue: Float, range: ClosedFloatingPointRange<Float>, steps: Int = 0) {
    Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Slider(value = sliderValue, onValueChange = onValueChange, valueRange = range, steps = steps, modifier = Modifier.fillMaxWidth())
}
