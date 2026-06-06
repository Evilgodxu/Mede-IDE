package com.template.jh.screens.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.template.jh.R
import com.template.jh.core.ai.ChatViewModel
import com.template.jh.core.ai.DownloadStatus
import com.template.jh.core.ai.EngineStatus
import com.template.jh.core.ai.LiteRTManager
import com.template.jh.core.ai.ModelParams
import com.template.jh.screens.home.HomeUiState
import com.template.jh.screens.home.HomeViewModel
import org.koin.androidx.compose.koinViewModel

// 设置分类枚举
enum class SettingsCategory(val labelResId: Int) {
    General(R.string.settings_category_general),
    MCP(R.string.settings_category_mcp),
    Skill(R.string.settings_category_skill),
    Model(R.string.settings_category_model),
    Conversation(R.string.settings_category_conversation),
    Rules(R.string.settings_category_rules)
}

// 双列设置面板
@Composable
fun SettingsPane(
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = koinViewModel(),
    chatViewModel: ChatViewModel? = null,
    onBrowseModelFile: () -> Unit = {},
) {
    val state by viewModel.state.collectAsState()
    var selectedCategory by remember { mutableStateOf(SettingsCategory.General) }

    Row(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .width(140.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f))
                .padding(vertical = 8.dp)
        ) {
            SettingsCategory.entries.forEach { category ->
                SettingsCategoryItem(category, selectedCategory == category) { selectedCategory = category }
            }
        }

        VerticalDivider(modifier = Modifier.fillMaxHeight(), thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            SettingsCategoryContent(
                category = selectedCategory, state = state,
                onSetThemeMode = { viewModel.setThemeMode(it) }, onSetLanguage = { viewModel.setLanguage(it) },
                chatViewModel = chatViewModel, onBrowseModelFile = onBrowseModelFile,
            )
        }
    }
}

@Composable
private fun SettingsCategoryItem(category: SettingsCategory, isSelected: Boolean, onClick: () -> Unit) {
    val bgColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else Color.Transparent
    val textColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).background(bgColor).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = stringResource(category.labelResId), style = MaterialTheme.typography.bodyMedium, color = textColor, fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
private fun SettingsCategoryContent(
    category: SettingsCategory, state: HomeUiState, onSetThemeMode: (String) -> Unit, onSetLanguage: (String) -> Unit,
    chatViewModel: ChatViewModel?, onBrowseModelFile: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(text = stringResource(category.labelResId), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

        when (category) {
            SettingsCategory.General -> {
                if (state.isLoading) CategoryPlaceholder(stringResource(R.string.loading))
                else {
                    ThemeSettingsCard(state, onSetThemeMode, Modifier.fillMaxWidth())
                    LanguageSettingsCard(state, onSetLanguage, Modifier.fillMaxWidth())
                }
            }
            SettingsCategory.Model -> ModelSettingsContent(chatViewModel, onBrowseModelFile)
            SettingsCategory.MCP -> CategoryPlaceholder(stringResource(R.string.settings_category_mcp_desc))
            SettingsCategory.Skill -> CategoryPlaceholder(stringResource(R.string.settings_category_skill_desc))
            SettingsCategory.Conversation -> CategoryPlaceholder(stringResource(R.string.settings_category_conversation_desc))
            SettingsCategory.Rules -> CategoryPlaceholder(stringResource(R.string.settings_category_rules_desc))
        }
    }
}

@Composable
private fun ModelSettingsContent(chatViewModel: ChatViewModel?, onBrowseModelFile: () -> Unit) {
    if (chatViewModel == null) { CategoryPlaceholder("模型管理不可用"); return }
    val chatState by chatViewModel.state.collectAsState()
    var topK by remember(chatState.modelParams) { mutableIntStateOf(chatState.modelParams.topK) }
    var topP by remember(chatState.modelParams) { mutableFloatStateOf(chatState.modelParams.topP.toFloat()) }
    var temperature by remember(chatState.modelParams) { mutableFloatStateOf(chatState.modelParams.temperature.toFloat()) }
    var seed by remember(chatState.modelParams) { mutableIntStateOf(chatState.modelParams.seed) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // 当前状态
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("当前模型状态", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    when (chatState.engineStatus) {
                        EngineStatus.Idle -> {
                            Icon(Icons.Default.Error, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(8.dp))
                            Text("未加载模型", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        EngineStatus.Loading -> {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("加载中: ${chatState.modelName}", style = MaterialTheme.typography.bodyMedium)
                        }
                        EngineStatus.Ready -> {
                            Icon(Icons.Default.CheckCircle, null, Modifier.size(16.dp), tint = Color(0xFF4CAF50))
                            Spacer(Modifier.width(8.dp))
                            Text(chatState.modelName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        }
                        EngineStatus.Error -> {
                            Icon(Icons.Default.Error, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(8.dp))
                            Text(chatState.engineErrorMessage.ifEmpty { "加载失败" }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }

        // 模型参数
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("模型参数", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)

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

                Button(onClick = {
                    val params = ModelParams(topK = topK, topP = topP.toDouble(), temperature = temperature.toDouble(), seed = seed)
                    chatViewModel.setModelParams(params)
                }, modifier = Modifier.fillMaxWidth()) {
                    Text("应用参数")
                }
            }
        }

        // 操作按钮
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onBrowseModelFile, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.FolderOpen, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("浏览文件")
            }
            OutlinedButton(onClick = { chatViewModel.scanModels() }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Refresh, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("扫描设备")
            }
        }

        // 已检测模型
        if (chatState.availableModels.isNotEmpty()) {
            Text("已检测模型", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            chatState.availableModels.forEach { model ->
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { chatViewModel.loadModel(model.path) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(model.name, style = MaterialTheme.typography.bodyMedium)
                            Text(model.sizeText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Button(onClick = { chatViewModel.loadModel(model.path) }) {
                            Text("加载")
                        }
                    }
                }
            }
        }

        // 推荐下载
        Text("推荐下载模型", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)

        LiteRTManager.RECOMMENDED_MODELS.forEach { model ->
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
                    // 下载进度
                    if (chatState.downloadStatus == DownloadStatus.Downloading && chatState.downloadFileName == model.fileName) {
                        LinearProgressIndicator(progress = { chatState.downloadProgress }, modifier = Modifier.fillMaxWidth())
                        Text("下载中… ${"%.0f".format(chatState.downloadProgress * 100)}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else if (chatState.downloadStatus == DownloadStatus.Completed && chatState.downloadFileName == model.fileName) {
                        Text("下载完成 ✓", style = MaterialTheme.typography.labelSmall, color = Color(0xFF4CAF50))
                        Spacer(Modifier.height(4.dp))
                        Button(onClick = {
                            chatViewModel.resetDownload()
                            chatViewModel.scanModels()
                        }, modifier = Modifier.fillMaxWidth()) {
                            Text("扫描已下载模型")
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
                            Text("下载到系统下载目录")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryPlaceholder(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
