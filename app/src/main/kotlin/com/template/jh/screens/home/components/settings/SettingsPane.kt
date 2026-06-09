package com.template.jh.screens.home.components.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.template.jh.R
import com.template.jh.core.utils.FileLogger
import com.template.jh.core.utils.LogCollector
import com.template.jh.data.source.local.LiteRTManager
import com.template.jh.model.McpServer
import com.template.jh.model.Rule
import com.template.jh.model.RuleType
import com.template.jh.model.SkillItem
import com.template.jh.model.chat.DownloadStatus
import com.template.jh.model.chat.EngineStatus
import com.template.jh.model.chat.ModelParams
import com.template.jh.model.chat.BackendType
import com.template.jh.screens.home.ChatViewModel
import com.template.jh.screens.home.HomeUiState
import com.template.jh.screens.home.HomeViewModel
import org.koin.androidx.compose.koinViewModel
import kotlin.math.roundToInt

// 设置分类枚举
enum class SettingsCategory(val labelResId: Int) {
    General(R.string.settings_category_general),
    MCP(R.string.settings_category_mcp),
    Skill(R.string.settings_category_skill),
    Model(R.string.settings_category_model),
    Rules(R.string.settings_category_rules)
}

// 双列设置面板
@Composable
fun SettingsPane(
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = koinViewModel(),
    chatViewModel: ChatViewModel? = null,
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
                onSetRules = { viewModel.setRules(it) }, onSetSkills = { viewModel.setSkills(it) },
                onSetMcpServers = { viewModel.setMcpServers(it) },
                chatViewModel = chatViewModel,
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
    onSetRules: (List<Rule>) -> Unit,
    onSetSkills: (List<SkillItem>) -> Unit, onSetMcpServers: (List<McpServer>) -> Unit,
    chatViewModel: ChatViewModel?,
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
                    GeneralSettingsCard(modifier = Modifier.fillMaxWidth())
                }
            }
            SettingsCategory.Model -> ModelSettingsContent(chatViewModel)
            SettingsCategory.Skill -> SkillsSettingsContent(state.skills, onSetSkills)
            SettingsCategory.MCP -> McpSettingsContent(state.mcpServers, onSetMcpServers)
            SettingsCategory.Rules -> RulesSettingsContent(state.rules, onSetRules)
        }
    }
}

@Composable
private fun ModelSettingsContent(chatViewModel: ChatViewModel?) {
    if (chatViewModel == null) { CategoryPlaceholder("模型管理不可用"); return }
    val chatState by chatViewModel.state.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val preferencesRepo = remember { com.template.jh.data.repository.UserPreferencesRepository(context) }
    val autoLoad by preferencesRepo.autoLoadLastModel.collectAsState(initial = true)
    val deepThinkEnabled by preferencesRepo.deepThinkEnabled.collectAsState(initial = true)
    val thinkingRounds by preferencesRepo.thinkingRounds.collectAsState(initial = 2)
    var topK by remember(chatState.modelParams) { mutableIntStateOf(chatState.modelParams.topK) }
    var topP by remember(chatState.modelParams) { mutableFloatStateOf(chatState.modelParams.topP.toFloat()) }
    var temperature by remember(chatState.modelParams) { mutableFloatStateOf(chatState.modelParams.temperature.toFloat()) }
    var seed by remember(chatState.modelParams) { mutableIntStateOf(chatState.modelParams.seed) }

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

        // 推理后端选择
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("推理后端", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Text("切换 CPU/GPU/NPU 后端，切换后需重新加载模型", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val currentBackend = chatState.backendType
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    BackendType.entries.forEach { type ->
                        val isSelected = currentBackend == type
                        androidx.compose.material3.FilterChip(
                            selected = isSelected,
                            onClick = { chatViewModel.setBackendType(type) },
                            label = { Text(type.displayName, style = MaterialTheme.typography.labelMedium) },
                        )
                    }
                }
                // NPU 模式下显示检测状态（由 ChatViewModel.setBackendType 自动检测并保存）
                if (currentBackend == BackendType.NPU) {
                    val ctx = LocalContext.current
                    val detectedDir = remember { LiteRTManager.detectNpuLibraryDir(ctx) }
                    val hasNpu = remember { LiteRTManager.hasNpuSupport(ctx) }
                    if (hasNpu) {
                        Text("✓ NPU 驱动已检测: $detectedDir", style = MaterialTheme.typography.labelSmall, color = Color(0xFF4CAF50))
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.Error, null, Modifier.size(14.dp), tint = Color(0xFFFFA000))
                            Text("未检测到已知 NPU 驱动，使用默认 nativeLibraryDir: $detectedDir", style = MaterialTheme.typography.labelSmall, color = Color(0xFFFFA000))
                        }
                    }
                }
            }
        }

        // 模型参数（可折叠）
        var isParamsExpanded by remember { mutableStateOf(false) }
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("思考轮数: $thinkingRounds", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    }
                    Slider(
                        value = thinkingRounds.toFloat(),
                        onValueChange = { kotlinx.coroutines.runBlocking { preferencesRepo.setThinkingRounds(it.toInt()) } },
                        valueRange = 1f..10f,
                        steps = 8,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = deepThinkEnabled,
                    )

                    Button(onClick = {
                        val params = ModelParams(topK = topK, topP = topP.toDouble(), temperature = temperature.toDouble(), seed = seed)
                        chatViewModel.setModelParams(params)
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text("应用参数")
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
            // 已下载/检测到的模型在已检测列表显示，隐藏下载卡
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
                    // 下载进度
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

        // 云端模型配置
        HorizontalDivider(thickness = 2.dp, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
        CloudModelCard(chatViewModel, chatState)
    }
}

// 云端模型厂商预设数据类
private data class CloudVendorPreset(
    val name: String,
    val apiEndpoint: String,
    val defaultModels: List<String>,
    val defaultContextWindow: Int = 128000,
    val icon: @Composable () -> Unit = { Icon(Icons.Default.SmartToy, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary) }
)

@Composable
private fun CloudModelCard(chatViewModel: ChatViewModel, chatState: com.template.jh.screens.home.ChatUiState) {
    var editingProfile by remember { mutableStateOf<com.template.jh.model.chat.CloudModelProfile?>(null) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showVendorSelector by remember { mutableStateOf(false) }
    var editStep by remember { mutableIntStateOf(0) } // 0=选择厂商, 1=填写详情
    var editName by remember { mutableStateOf("") }
    var editEndpoint by remember { mutableStateOf("") }
    var editKey by remember { mutableStateOf("") }
    var editModel by remember { mutableStateOf("") }
    var editContextWindow by remember { mutableIntStateOf(128000) }
    var showKey by remember { mutableStateOf(false) }
    var isTesting by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var isFetchingModels by remember { mutableStateOf(false) }
    var availableModels by remember { mutableStateOf<List<String>>(emptyList()) }

    val vendorPresets = listOf(
        CloudVendorPreset("OpenAI", "https://api.openai.com/v1", listOf("gpt-4o", "gpt-4-turbo", "gpt-3.5-turbo"), defaultContextWindow = 128000),
        CloudVendorPreset("DeepSeek", "https://api.deepseek.com/v1", listOf("deepseek-chat", "deepseek-reasoner"), defaultContextWindow = 128000),
        CloudVendorPreset("智谱AI", "https://open.bigmodel.cn/api/paas/v4", listOf("glm-4-flash", "glm-4-plus"), defaultContextWindow = 128000),
        CloudVendorPreset("通义千问", "https://dashscope.aliyuncs.com/compatible-mode/v1", listOf("qwen-plus", "qwen-turbo"), defaultContextWindow = 128000),
        CloudVendorPreset("Moonshot", "https://api.moonshot.cn/v1", listOf("moonshot-v1-8k", "moonshot-v1-32k"), defaultContextWindow = 128000),
        CloudVendorPreset("自定义", "", listOf(), defaultContextWindow = 128000),
    )

    val verifyMsg = when {
        chatState.engineStatus == EngineStatus.Idle && chatState.engineErrorMessage.startsWith("验证") -> chatState.engineErrorMessage
        chatState.engineErrorMessage.startsWith("error") || chatState.engineErrorMessage.startsWith("连接失败") -> chatState.engineErrorMessage
        else -> ""
    }

    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("云端大模型", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)

            Text("接入 OpenAI 兼容 API（DeepSeek、OpenAI、智谱AI 等），可添加多个配置并自由切换。在主界面顶部栏模型切换按钮中启用。", 
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            // 添加配置按钮
            OutlinedButton(onClick = {
                editName = ""
                editEndpoint = ""
                editKey = ""
                editModel = ""
                editingProfile = null
                editStep = 0
                testResult = null
                availableModels = emptyList()
                showEditDialog = true
            }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("添加云端配置")
            }

            // 配置文件列表
            chatState.cloudModelProfiles.forEach { profile ->
                val isActive = profile.id == chatState.activeCloudProfileId
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    ),
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(profile.name.ifEmpty { profile.modelName }, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                            if (isActive) {
                                Icon(Icons.Default.CheckCircle, null, Modifier.size(16.dp), tint = Color(0xFF4CAF50))
                                Spacer(Modifier.width(4.dp))
                                Text("当前", style = MaterialTheme.typography.labelSmall, color = Color(0xFF4CAF50))
                            }
                        }
                        Text("${profile.apiEndpoint}  |  ${profile.modelName}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (!isActive) {
                                OutlinedButton(onClick = { chatViewModel.switchCloudProfile(profile.id) }, modifier = Modifier.height(28.dp)) {
                                    Text("切换", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            OutlinedButton(onClick = {
                                editName = profile.name; editEndpoint = profile.apiEndpoint
                                editKey = profile.apiKey; editModel = profile.modelName
                                editContextWindow = profile.contextWindow
                                editingProfile = profile; editStep = 1
                                testResult = null
                                availableModels = emptyList()
                                showEditDialog = true
                            }, modifier = Modifier.height(28.dp)) {
                                Text("编辑", style = MaterialTheme.typography.labelSmall)
                            }
                            IconButton(onClick = { chatViewModel.removeCloudProfile(profile.id) }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Delete, "删除", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }

            // 验证连接
            if (chatState.cloudModelProfiles.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { chatViewModel.verifyCloudConnection() }) {
                        Text("验证当前连接", style = MaterialTheme.typography.labelSmall)
                    }
                    if (verifyMsg.isNotEmpty()) {
                        Text(verifyMsg, style = MaterialTheme.typography.labelSmall,
                            color = if (verifyMsg == "验证中…") MaterialTheme.colorScheme.primary
                            else if (verifyMsg == "ok") Color(0xFF4CAF50)
                            else MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }

    // 添加/编辑对话框
    if (showEditDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { 
                Text(when {
                    editingProfile != null -> "编辑云端配置"
                    editStep == 0 -> "选择厂商"
                    else -> "添加云端配置"
                }) 
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // 步骤0: 选择厂商
                    if (editStep == 0 && editingProfile == null) {
                        vendorPresets.forEach { vendor ->
                            Card(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    editContextWindow = vendor.defaultContextWindow
                                    if (vendor.name == "自定义") {
                                        editEndpoint = ""
                                        editModel = ""
                                    } else {
                                        editName = vendor.name
                                        editEndpoint = vendor.apiEndpoint
                                        editModel = vendor.defaultModels.firstOrNull() ?: ""
                                    }
                                    editStep = 1
                                },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    vendor.icon()
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(vendor.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                        if (vendor.apiEndpoint.isNotEmpty()) {
                                            Text(vendor.apiEndpoint, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // 步骤1: 填写详情
                        OutlinedTextField(
                            value = editName,
                            onValueChange = { editName = it },
                            label = { Text("配置名称") },
                            placeholder = { Text("例如: DeepSeek V4") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = editEndpoint,
                            onValueChange = { editEndpoint = it; availableModels = emptyList() },
                            label = { Text("API 端点") },
                            placeholder = { Text("https://api.deepseek.com") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = editModel,
                                onValueChange = { editModel = it },
                                label = { Text("模型名称") },
                                placeholder = { Text("deepseek-chat") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                            )
                            // 获取模型列表按钮
                            if (availableModels.isNotEmpty()) {
                                Box {
                                    var showModelDropdown by remember { mutableStateOf(false) }
                                    IconButton(onClick = { showModelDropdown = true }) {
                                        Icon(Icons.Default.ArrowDropDown, "选择模型", tint = MaterialTheme.colorScheme.primary)
                                    }
                                    DropdownMenu(
                                        expanded = showModelDropdown,
                                        onDismissRequest = { showModelDropdown = false }
                                    ) {
                                        availableModels.forEach { model ->
                                            DropdownMenuItem(
                                                text = { Text(model, style = MaterialTheme.typography.bodySmall) },
                                                onClick = {
                                                    editModel = model
                                                    showModelDropdown = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        OutlinedTextField(
                            value = editKey,
                            onValueChange = { editKey = it },
                            label = { Text("API Key") },
                            placeholder = { Text("sk-...") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            visualTransformation = if (showKey) androidx.compose.ui.text.input.VisualTransformation.None
                                else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { showKey = !showKey }) {
                                    Icon(
                                        if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = if (showKey) "隐藏" else "显示",
                                    )
                                }
                            },
                        )

                        // 上下文窗口大小
                        OutlinedTextField(
                            value = if (editContextWindow == 0) "" else editContextWindow.toString(),
                            onValueChange = { v ->
                                editContextWindow = v.filter { it.isDigit() }.take(7).toIntOrNull()
                                    ?: if (v.isEmpty()) 0 else editContextWindow
                            },
                            label = { Text("上下文窗口 (token)") },
                            placeholder = { Text("128000") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            supportingText = { Text("75% 用量自动触发上下文压缩", style = MaterialTheme.typography.labelSmall) },
                        )

                        // 测试连接和获取模型按钮
                        val context = androidx.compose.ui.platform.LocalContext.current
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    isTesting = true
                                    testResult = null
                                    CoroutineScope(Dispatchers.IO).launch {
                                        try {
                                            val client = com.template.jh.data.source.remote.CloudLLMClient(context)
                                            val config = com.template.jh.model.chat.CloudModelConfig(
                                                enabled = true,
                                                apiEndpoint = editEndpoint,
                                                apiKey = editKey,
                                                modelName = editModel.ifEmpty { "gpt-3.5-turbo" }
                                            )
                                            val result = client.verifyConnection(config)
                                            isTesting = false
                                            testResult = result
                                        } catch (e: Exception) {
                                            isTesting = false
                                            testResult = "测试失败: ${e.message}"
                                            Log.e("CloudModelCard", "Test connection error", e)
                                            FileLogger.e("CloudModelCard", "testConnection error: ${e.message}", e)
                                        }
                                    }
                                },
                                enabled = editEndpoint.isNotBlank() && !isTesting,
                                modifier = Modifier.weight(1f)
                            ) {
                                if (isTesting) {
                                    CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(4.dp))
                                } else {
                                    Icon(Icons.Default.NetworkCheck, null, Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                }
                                Text("测试连接", style = MaterialTheme.typography.labelSmall)
                            }
                            
                            OutlinedButton(
                                onClick = {
                                    isFetchingModels = true
                                    availableModels = emptyList()
                                    CoroutineScope(Dispatchers.IO).launch {
                                        try {
                                            val client = com.template.jh.data.source.remote.CloudLLMClient(context)
                                            val models = client.fetchModels(editEndpoint, editKey)
                                            isFetchingModels = false
                                            availableModels = models
                                        } catch (e: Exception) {
                                            isFetchingModels = false
                                            availableModels = emptyList()
                                            Log.e("CloudModelCard", "Fetch models error", e)
                                            FileLogger.e("CloudModelCard", "fetchModels error: ${e.message}", e)
                                        }
                                    }
                                },
                                enabled = editEndpoint.isNotBlank() && !isFetchingModels,
                                modifier = Modifier.weight(1f)
                            ) {
                                if (isFetchingModels) {
                                    CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(4.dp))
                                } else {
                                    Icon(Icons.Default.Refresh, null, Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                }
                                Text("获取模型", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        
                        // 显示测试结果
                        testResult?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (it == "ok") Color(0xFF4CAF50) else MaterialTheme.colorScheme.error
                            )
                        }
                        
                        // 返回厂商选择
                        if (editingProfile == null) {
                            TextButton(onClick = { editStep = 0 }) {
                                Text("← 返回选择厂商")
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (editStep == 1 || editingProfile != null) {
                    Button(
                        onClick = {
                            if (editEndpoint.isNotBlank() && editModel.isNotBlank()) {
                                if (editingProfile != null) {
                                    val current = editingProfile
                                    if (current != null) {
                                        val updated = current.copy(
                                            name = editName, apiEndpoint = editEndpoint,
                                            apiKey = editKey, modelName = editModel,
                                            contextWindow = editContextWindow,
                                        )
                                        chatViewModel.updateCloudProfile(updated)
                                    }
                                } else {
                                    chatViewModel.addCloudProfile(editName, editEndpoint, editKey, editModel, editContextWindow)
                                }
                                showEditDialog = false
                            }
                        },
                        enabled = editEndpoint.isNotBlank() && editModel.isNotBlank()
                    ) { Text("保存") }
                }
            },
            dismissButton = { OutlinedButton(onClick = { showEditDialog = false }) { Text("取消") } },
        )
    }
}

// 通用设置卡片
@Composable
private fun GeneralSettingsCard(
    modifier: Modifier = Modifier,
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("通用", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)

            // 发送日志按钮
            var isSharing by remember { mutableStateOf(false) }
            androidx.compose.material3.Button(
                onClick = {
                    isSharing = true
                    CoroutineScope(Dispatchers.Main).launch {
                        LogCollector.collectAndShareLogs(context)
                        isSharing = false
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSharing,
            ) {
                if (isSharing) {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(if (isSharing) "正在收集日志…" else "发送日志")
            }
        }
    }
}

@Composable
private fun RulesSettingsContent(
    rules: List<Rule>,
    onSetRules: (List<Rule>) -> Unit,
) {
    var editingRuleId by remember { mutableStateOf<String?>(null) }
    var editName by remember { mutableStateOf("") }
    var editContent by remember { mutableStateOf("") }
    var editType by remember { mutableStateOf(RuleType.Global) }
    var showAddDialog by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "创建规则作为上下文注入对话，模型对话时自动引用",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // 规则数量提示
        if (rules.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.CheckCircle, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "已启用 ${rules.size} 条规则，对话时将自动注入为上下文参考",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }

        // 添加按钮
        OutlinedButton(
            onClick = {
                editName = ""
                editContent = ""
                editType = RuleType.Global
                editingRuleId = null
                showAddDialog = true
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.Add, null, Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("添加规则")
        }

        // 规则列表
        rules.forEach { rule ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
            ) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(rule.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    if (rule.type == RuleType.Global) "全局" else "项目",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                rule.content.take(80) + if (rule.content.length > 80) "…" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        IconButton(onClick = {
                            editName = rule.name
                            editContent = rule.content
                            editType = rule.type
                            editingRuleId = rule.id
                            showAddDialog = true
                        }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Refresh, "编辑", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = {
                            onSetRules(rules.filter { it.id != rule.id })
                        }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Delete, "删除", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }

    // 添加/编辑对话框
    if (showAddDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text(if (editingRuleId != null) "编辑规则" else "添加规则") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("规则名称") },
                        placeholder = { Text("例如: 编码规范") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )

                    // 规则类型选择
                    var typeExpanded by remember { mutableStateOf(false) }
                    Box {
                        OutlinedButton(onClick = { typeExpanded = true }) {
                            Text("类型: ${if (editType == RuleType.Global) "全局规则" else "项目规则"}")
                        }
                        DropdownMenu(expanded = typeExpanded, onDismissRequest = { typeExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text("全局规则") },
                                onClick = { editType = RuleType.Global; typeExpanded = false },
                            )
                            DropdownMenuItem(
                                text = { Text("项目规则") },
                                onClick = { editType = RuleType.Project; typeExpanded = false },
                            )
                        }
                    }

                    OutlinedTextField(
                        value = editContent,
                        onValueChange = { editContent = it },
                        label = { Text("规则内容") },
                        placeholder = { Text("描述规则要求…") },
                        modifier = Modifier.fillMaxWidth().height(150.dp),
                        maxLines = 10,
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (editName.isNotBlank() && editContent.isNotBlank()) {
                        val updated = if (editingRuleId != null) {
                            rules.map { if (it.id == editingRuleId) it.copy(name = editName, content = editContent, type = editType) else it }
                        } else {
                            rules + Rule(name = editName, content = editContent, type = editType)
                        }
                        onSetRules(updated)
                        showAddDialog = false
                    }
                }) { Text("保存") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showAddDialog = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun SkillsSettingsContent(
    skills: List<SkillItem>,
    onSetSkills: (List<SkillItem>) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var importError by remember { mutableStateOf<String?>(null) }

    // 文件选择器启动器（支持 ZIP 和单个技能文件）
    val filePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            scope.launch {
                try {
                    val importedSkill = when {
                        isZipFile(context, uri) -> importSkillFromZip(context, uri)
                        else -> importSkillFromFile(context, uri)
                    }
                    importedSkill?.let { skill ->
                        onSetSkills(skills + skill)
                    } ?: run {
                        importError = "导入失败：无法解析技能文件"
                    }
                } catch (e: Exception) {
                    importError = "导入失败：${e.message}"
                }
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (skills.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
            ) {
                Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("暂无技能", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("点击下方按钮添加自定义技能", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            val enabledCount = skills.count { it.enabled }
            if (enabledCount > 0) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("已启用 $enabledCount 项技能", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }
        }

        OutlinedButton(
            onClick = { filePickerLauncher.launch("*/*") },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.Add, null, Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("导入技能")
        }

        // 导入错误提示
        importError?.let { error ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Error,
                        null,
                        Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = { importError = null },
                        modifier = Modifier.size(20.dp),
                    ) {
                        Icon(Icons.Default.Close, null, Modifier.size(14.dp))
                    }
                }
            }
        }

        skills.forEach { skill ->
            SkillCard(
                skill = skill,
                onToggleEnabled = { checked ->
                    onSetSkills(skills.map { if (it.id == skill.id) it.copy(enabled = checked) else it })
                },
                onDelete = { onSetSkills(skills.filter { it.id != skill.id }) },
            )
        }
    }

}

// 技能卡片组件（支持展开详情）
@Composable
private fun SkillCard(
    skill: SkillItem,
    onToggleEnabled: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
    ) {
        Column {
            // 头部信息（始终显示）
            Row(
                modifier = Modifier
                    .padding(12.dp)
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(skill.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        if (skill.version.isNotBlank()) {
                            Text(
                                "v${skill.version}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.padding(start = 6.dp),
                            )
                        }
                    }
                    if (skill.description.isNotBlank()) {
                        Text(
                            skill.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (skill.prompt.isNotBlank() && !expanded) {
                        Text(
                            skill.prompt.take(60) + if (skill.prompt.length > 60) "…" else "",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        )
                    }
                    // 标签
                    if (skill.tags.isNotEmpty()) {
                        Row(
                            modifier = Modifier.padding(top = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            skill.tags.take(3).forEach { tag ->
                                Text(
                                    tag,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .background(
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                            RoundedCornerShape(4.dp),
                                        )
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                            if (skill.tags.size > 3) {
                                Text(
                                    "+${skill.tags.size - 3}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ArrowDropDown else Icons.Default.ArrowDropDown,
                    contentDescription = if (expanded) "收起" else "展开",
                    modifier = Modifier
                        .size(24.dp)
                        .rotate(if (expanded) 180f else 0f),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // 展开详情
            if (expanded) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                )

                Column(
                    modifier = Modifier
                        .padding(12.dp)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // 作者信息
                    if (skill.author.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.SmartToy,
                                null,
                                Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "作者: ${skill.author}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    // 使用方法
                    if (skill.usage.isNotBlank()) {
                        Column {
                            Text(
                                "使用方法",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                skill.usage,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    // 参数配置
                    if (skill.parameters.isNotEmpty()) {
                        Column {
                            Text(
                                "参数配置",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.height(4.dp))
                            skill.parameters.forEach { param ->
                                Row(
                                    modifier = Modifier.padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.Top,
                                ) {
                                    Text(
                                        param.name,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.width(100.dp),
                                    )
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            param.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Row {
                                            Text(
                                                "类型: ${param.type}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                            )
                                            if (param.required) {
                                                Text(
                                                    " • 必填",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.error,
                                                )
                                            }
                                            param.defaultValue?.let {
                                                Text(
                                                    " • 默认: $it",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                )
                                            }
                                        }
                                        if (param.options.isNotEmpty()) {
                                            Text(
                                                "选项: ${param.options.joinToString(", ")}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 使用示例
                    if (skill.examples.isNotEmpty()) {
                        Column {
                            Text(
                                "使用示例",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.height(4.dp))
                            skill.examples.forEachIndexed { index, example ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                                    ),
                                ) {
                                    Text(
                                        example,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(8.dp),
                                    )
                                }
                                if (index < skill.examples.size - 1) {
                                    Spacer(Modifier.height(4.dp))
                                }
                            }
                        }
                    }

                    // 完整文档
                    if (skill.documentation.isNotBlank()) {
                        Column {
                            Text(
                                "详细说明",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                skill.documentation,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    // 提示词预览
                    if (skill.prompt.isNotBlank()) {
                        Column {
                            Text(
                                "提示词",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.height(4.dp))
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                                ),
                            ) {
                                Text(
                                    skill.prompt,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 12.sp,
                                    ),
                                    modifier = Modifier.padding(8.dp),
                                )
                            }
                        }
                    }

                    // 操作按钮
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (skill.enabled) "已启用" else "已禁用",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (skill.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(8.dp))
                        Switch(
                            checked = skill.enabled,
                            onCheckedChange = onToggleEnabled,
                        )
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.size(28.dp),
                        ) {
                            Icon(Icons.Default.Delete, "删除", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            } else {
                // 未展开时显示开关和删除按钮
                Row(
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Switch(
                        checked = skill.enabled,
                        onCheckedChange = onToggleEnabled,
                    )
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(Icons.Default.Delete, "删除", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

// 判断文件是否为 ZIP 格式
private fun isZipFile(context: Context, uri: Uri): Boolean {
    return try {
        context.contentResolver.getType(uri)?.let { mimeType ->
            mimeType == "application/zip" || mimeType == "application/x-zip-compressed"
        } ?: uri.lastPathSegment?.endsWith(".zip", ignoreCase = true) ?: false
    } catch (e: Exception) {
        false
    }
}

// 从单个文件导入技能（JSON 或 Markdown）
private suspend fun importSkillFromFile(context: Context, uri: Uri): SkillItem? {
    return withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val content = input.bufferedReader().use { it.readText() }
                val fileName = uri.lastPathSegment ?: ""

                when {
                    fileName.endsWith(".json", ignoreCase = true) -> {
                        // JSON 格式技能文件
                        val json = org.json.JSONObject(content)
                        SkillItem(
                            name = json.optString("name", fileName.removeSuffix(".json")),
                            description = json.optString("description", ""),
                            prompt = json.optString("prompt", ""),
                            enabled = true,
                        )
                    }
                    fileName.endsWith(".md", ignoreCase = true) ||
                    fileName.endsWith(".txt", ignoreCase = true) -> {
                        // Markdown/纯文本格式，文件名作为技能名，内容作为 prompt
                        val name = fileName.substringBeforeLast(".").replace("_", " ").replace("-", " ")
                        SkillItem(
                            name = name,
                            description = "",
                            prompt = content,
                            enabled = true,
                        )
                    }
                    else -> null
                }
            }
        } catch (e: Exception) {
            Log.e("SettingsPane", "导入技能文件失败", e)
            null
        }
    }
}

// 从 ZIP 文件导入技能（支持多级目录结构）
private suspend fun importSkillFromZip(context: Context, uri: Uri): SkillItem? {
    return withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                java.util.zip.ZipInputStream(input).use { zip ->
                    // 收集所有文件，支持多级目录
                    val files = mutableMapOf<String, String>()
                    var entry = zip.nextEntry

                    while (entry != null) {
                        if (!entry.isDirectory) {
                            // 使用小写文件名作为 key，支持大小写不敏感匹配
                            val key = entry.name.lowercase().replace("\\", "/")
                            val content = zip.bufferedReader().use { it.readText() }
                            files[key] = content
                        }
                        entry = zip.nextEntry
                    }

                    // 查找 skill.json（支持任意层级目录）
                    val skillJsonEntry = files.entries.find { it.key.endsWith("skill.json") }
                    val skillJson = skillJsonEntry?.value

                    if (skillJson != null) {
                        val json = org.json.JSONObject(skillJson)
                        val name = json.optString("name", "未命名技能")
                        val description = json.optString("description", "")
                        val author = json.optString("author", "")
                        val version = json.optString("version", "")

                        // 解析 tags 数组
                        val tags = json.optJSONArray("tags")?.let { arr ->
                            (0 until arr.length()).map { arr.getString(it) }
                        } ?: emptyList()

                        // 解析 parameters 数组
                        val parameters = json.optJSONArray("parameters")?.let { arr ->
                            (0 until arr.length()).map { i ->
                                val param = arr.getJSONObject(i)
                                com.template.jh.model.SkillParameter(
                                    name = param.optString("name", ""),
                                    description = param.optString("description", ""),
                                    type = param.optString("type", "string"),
                                    required = param.optBoolean("required", false),
                                    defaultValue = param.optString("defaultValue", "").takeIf { it.isNotBlank() },
                                    options = param.optJSONArray("options")?.let { optArr ->
                                        (0 until optArr.length()).map { optArr.getString(it) }
                                    } ?: emptyList()
                                )
                            }
                        } ?: emptyList()

                        // 解析 examples 数组
                        val examples = json.optJSONArray("examples")?.let { arr ->
                            (0 until arr.length()).map { arr.getString(it) }
                        } ?: emptyList()

                        // 优先从 skill.json 获取 prompt
                        var prompt = json.optString("prompt", "")

                        // 如果 skill.json 中没有 prompt，查找 prompt.md 或 prompt.txt
                        if (prompt.isBlank()) {
                            val promptEntry = files.entries.find {
                                it.key.endsWith("prompt.md") || it.key.endsWith("prompt.txt")
                            }
                            prompt = promptEntry?.value ?: ""
                        }

                        // 查找 docs 目录下的文档说明
                        val docsContent = files.entries
                            .filter { it.key.contains("/docs/") || it.key.startsWith("docs/") }
                            .filter { it.key.endsWith(".md") || it.key.endsWith(".txt") }
                            .map { it.value }
                            .joinToString("\n\n")

                        // 查找 usage 说明
                        val usageEntry = files.entries.find {
                            it.key.endsWith("usage.md") || it.key.endsWith("usage.txt")
                        }
                        val usage = usageEntry?.value ?: json.optString("usage", "")

                        val finalDescription = if (docsContent.isNotBlank() && description.isBlank()) {
                            docsContent.take(200) + if (docsContent.length > 200) "…" else ""
                        } else {
                            description
                        }

                        if (prompt.isNotBlank()) {
                            SkillItem(
                                name = name,
                                description = finalDescription,
                                prompt = prompt,
                                enabled = true,
                                documentation = docsContent,
                                usage = usage,
                                parameters = parameters,
                                examples = examples,
                                author = author,
                                version = version,
                                tags = tags,
                            )
                        } else null
                    } else null
                }
            }
        } catch (e: Exception) {
            Log.e("SettingsPane", "导入技能 ZIP 失败", e)
            null
        }
    }
}

@Composable
private fun McpSettingsContent(
    servers: List<McpServer>,
    onSetMcpServers: (List<McpServer>) -> Unit,
) {
    var showDialog by remember { mutableStateOf(false) }
    var editingId by remember { mutableStateOf<String?>(null) }
    var jsonInput by remember { mutableStateOf("") }
    var jsonError by remember { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "配置 MCP 服务器以扩展 AI 能力，连接后可调用服务器提供的工具",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        val enabledCount = servers.count { it.enabled }
        if (enabledCount > 0) {
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("已连接 $enabledCount 个 MCP 服务器", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }

        OutlinedButton(
            onClick = { jsonInput = ""; jsonError = null; editingId = null; showDialog = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.Add, null, Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("添加 MCP 服务器")
        }

        servers.forEach { server ->
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(server.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (server.enabled) "已连接" else "未启用",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (server.enabled) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text("${server.command} ${server.args}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    androidx.compose.material3.Switch(
                        checked = server.enabled,
                        onCheckedChange = { checked -> onSetMcpServers(servers.map { if (it.id == server.id) it.copy(enabled = checked) else it }) },
                    )
                    IconButton(
                        onClick = {
                            jsonInput = """{"name":"${server.name}","command":"${server.command}","args":"${server.args}"}"""
                            jsonError = null; editingId = server.id; showDialog = true
                        },
                        modifier = Modifier.size(28.dp),
                    ) { Icon(Icons.Default.Refresh, "编辑", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                    IconButton(
                        onClick = { onSetMcpServers(servers.filter { it.id != server.id }) },
                        modifier = Modifier.size(28.dp),
                    ) { Icon(Icons.Default.Delete, "删除", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }

    if (showDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(if (editingId != null) "编辑 MCP 服务器" else "添加 MCP 服务器") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("粘贴 JSON 配置", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value = jsonInput,
                        onValueChange = { v -> jsonInput = v; jsonError = null },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                        placeholder = { Text("{\n  \"name\": \"filesystem\",\n  \"command\": \"npx\",\n  \"args\": \"-y @modelcontextprotocol/server-filesystem\"\n}") },
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                    )
                    if (jsonError != null) {
                        Text(jsonError!!, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    try {
                        val obj = org.json.JSONObject(jsonInput.trim())
                        val name = obj.optString("name", "").trim()
                        val command = obj.optString("command", "").trim()
                        val args = obj.optString("args", "").trim()
                        if (name.isBlank()) { jsonError = "缺少 name 字段"; return@Button }
                        if (command.isBlank()) { jsonError = "缺少 command 字段"; return@Button }
                        val updated = if (editingId != null) {
                            servers.map { if (it.id == editingId) it.copy(name = name, command = command, args = args) else it }
                        } else {
                            servers + McpServer(name = name, command = command, args = args, enabled = true)
                        }
                        onSetMcpServers(updated)
                        showDialog = false
                    } catch (e: Exception) {
                        jsonError = "JSON 解析失败: ${e.message}"
                    }
                }) { Text("保存") }
            },
            dismissButton = { OutlinedButton(onClick = { showDialog = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun CategoryPlaceholder(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}



