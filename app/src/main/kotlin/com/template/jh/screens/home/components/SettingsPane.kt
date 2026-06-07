package com.template.jh.screens.home.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
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
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.ArrowDropDown
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
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.template.jh.R
import com.template.jh.core.ai.ChatViewModel
import com.template.jh.core.utils.LogCollector
import com.template.jh.core.ai.DownloadStatus
import com.template.jh.core.ai.EngineStatus
import com.template.jh.core.ai.LiteRTManager
import com.template.jh.core.ai.ModelParams
import com.template.jh.data.model.McpServer
import com.template.jh.data.model.NotificationSettings
import com.template.jh.data.model.Rule
import com.template.jh.data.model.RuleType
import com.template.jh.data.model.SkillItem
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
                onSetModelName = { viewModel.setModelName(it) }, onSetUserName = { viewModel.setUserName(it) },
                onSetRules = { viewModel.setRules(it) }, onSetSkills = { viewModel.setSkills(it) },
                onSetMcpServers = { viewModel.setMcpServers(it) },
                onSetNotificationSettings = { viewModel.setNotificationSettings(it) },
                onSetShowToolCalls = { chatViewModel?.setShowToolCalls(it) },
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
    onSetModelName: (String) -> Unit, onSetUserName: (String) -> Unit, onSetRules: (List<Rule>) -> Unit,
    onSetSkills: (List<SkillItem>) -> Unit, onSetMcpServers: (List<McpServer>) -> Unit,
    onSetNotificationSettings: (NotificationSettings) -> Unit,
    onSetShowToolCalls: (Boolean) -> Unit,
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
                    GeneralSettingsCard(state, onSetModelName, onSetUserName, Modifier.fillMaxWidth())
                }
            }
            SettingsCategory.Model -> ModelSettingsContent(chatViewModel, onBrowseModelFile)
            SettingsCategory.Skill -> SkillsSettingsContent(state.skills, onSetSkills)
            SettingsCategory.MCP -> McpSettingsContent(state.mcpServers, onSetMcpServers)
            SettingsCategory.Conversation -> {
                val chatStateVal = chatViewModel?.let { vm -> vm.state.collectAsState().value }
                val showToolCalls = chatStateVal?.showToolCalls ?: false
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ThinkingSettingsContent(chatViewModel)
                    HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    NotificationSettingsContent(state.notificationSettings, onSetNotificationSettings, showToolCalls, onSetShowToolCalls)
                }
            }
            SettingsCategory.Rules -> RulesSettingsContent(state.rules, onSetRules)
        }
    }
}

@Composable
private fun ModelSettingsContent(chatViewModel: ChatViewModel?, onBrowseModelFile: () -> Unit) {
    if (chatViewModel == null) { CategoryPlaceholder("模型管理不可用"); return }
    val chatState by chatViewModel.state.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val preferencesRepo = remember { com.template.jh.data.repository.UserPreferencesRepository(context) }
    val autoLoad by preferencesRepo.autoLoadLastModel.collectAsState(initial = true)
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
    val icon: @Composable () -> Unit = { Icon(Icons.Default.SmartToy, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary) }
)

@Composable
private fun CloudModelCard(chatViewModel: ChatViewModel, chatState: com.template.jh.core.ai.ChatUiState) {
    var editingProfile by remember { mutableStateOf<com.template.jh.core.ai.CloudModelProfile?>(null) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showVendorSelector by remember { mutableStateOf(false) }
    var editStep by remember { mutableIntStateOf(0) } // 0=选择厂商, 1=填写详情
    var editName by remember { mutableStateOf("") }
    var editEndpoint by remember { mutableStateOf("") }
    var editKey by remember { mutableStateOf("") }
    var editModel by remember { mutableStateOf("") }
    var showKey by remember { mutableStateOf(false) }
    var isTesting by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var isFetchingModels by remember { mutableStateOf(false) }
    var availableModels by remember { mutableStateOf<List<String>>(emptyList()) }

    val vendorPresets = listOf(
        CloudVendorPreset("OpenAI", "https://api.openai.com/v1", listOf("gpt-4o", "gpt-4-turbo", "gpt-3.5-turbo")),
        CloudVendorPreset("DeepSeek", "https://api.deepseek.com/v1", listOf("deepseek-chat", "deepseek-reasoner")),
        CloudVendorPreset("智谱AI", "https://open.bigmodel.cn/api/paas/v4", listOf("glm-4-flash", "glm-4-plus")),
        CloudVendorPreset("通义千问", "https://dashscope.aliyuncs.com/compatible-mode/v1", listOf("qwen-plus", "qwen-turbo")),
        CloudVendorPreset("Moonshot", "https://api.moonshot.cn/v1", listOf("moonshot-v1-8k", "moonshot-v1-32k")),
        CloudVendorPreset("自定义", "", listOf()),
    )

    val verifyMsg = when {
        chatState.engineStatus == EngineStatus.Idle && chatState.engineErrorMessage.startsWith("验证") -> chatState.engineErrorMessage
        chatState.engineErrorMessage.startsWith("error") || chatState.engineErrorMessage.startsWith("连接失败") -> chatState.engineErrorMessage
        else -> ""
    }

    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("云端大模型", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Switch(checked = chatState.cloudModelEnabled, onCheckedChange = { chatViewModel.setCloudModelEnabled(it) })
            }

            Text("接入 OpenAI 兼容 API（DeepSeek、OpenAI、智谱AI 等），可添加多个配置并自由切换。",
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
                        
                        // 测试连接和获取模型按钮
                        val context = androidx.compose.ui.platform.LocalContext.current
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    isTesting = true
                                    testResult = null
                                    CoroutineScope(Dispatchers.IO).launch {
                                        try {
                                            val client = com.template.jh.core.ai.CloudLLMClient(context)
                                            val config = com.template.jh.core.ai.CloudModelConfig(
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
                                            // 复制崩溃信息到剪贴板
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            clipboard.setPrimaryClip(ClipData.newPlainText("CloudModel Test Error", "Test connection error: ${e.message}\n${e.stackTraceToString()}"))
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
                                            val client = com.template.jh.core.ai.CloudLLMClient(context)
                                            val models = client.fetchModels(editEndpoint, editKey)
                                            isFetchingModels = false
                                            availableModels = models
                                        } catch (e: Exception) {
                                            isFetchingModels = false
                                            availableModels = emptyList()
                                            Log.e("CloudModelCard", "Fetch models error", e)
                                            // 复制崩溃信息到剪贴板
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            clipboard.setPrimaryClip(ClipData.newPlainText("CloudModel Fetch Error", "Fetch models error: ${e.message}\n${e.stackTraceToString()}"))
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
                                    val updated = editingProfile!!.copy(
                                        name = editName, apiEndpoint = editEndpoint,
                                        apiKey = editKey, modelName = editModel,
                                    )
                                    chatViewModel.updateCloudProfile(updated)
                                } else {
                                    chatViewModel.addCloudProfile(editName, editEndpoint, editKey, editModel)
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

// 对话配置卡片 - IDE风格紧凑行内布局
@Composable
private fun GeneralSettingsCard(
    state: HomeUiState,
    onSetModelName: (String) -> Unit,
    onSetUserName: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("对话配置", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)

            var modelName by remember(state) { mutableStateOf(state.modelName) }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "AI 模型名称",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(96.dp),
                )
                OutlinedTextField(
                    value = modelName,
                    onValueChange = { modelName = it; onSetModelName(it) },
                    placeholder = { Text("例如: Gemini 2.5 Pro", style = MaterialTheme.typography.bodySmall) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                    ),
                )
            }

            var userName by remember(state) { mutableStateOf(state.userName) }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "用户名称",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(96.dp),
                )
                OutlinedTextField(
                    value = userName,
                    onValueChange = { userName = it; onSetUserName(it) },
                    placeholder = { Text("你的名字", style = MaterialTheme.typography.bodySmall) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                    ),
                )
            }

            HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

            // 发送日志按钮
            val context = androidx.compose.ui.platform.LocalContext.current
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
    var showDialog by remember { mutableStateOf(false) }
    var editingId by remember { mutableStateOf<String?>(null) }
    var editName by remember { mutableStateOf("") }
    var editDesc by remember { mutableStateOf("") }
    var editPrompt by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "导入或创建自定义 AI 技能，已启用技能的提示词将作为上下文注入对话",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

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
            onClick = { editName = ""; editDesc = ""; editPrompt = ""; editingId = null; showDialog = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.Add, null, Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("添加技能")
        }

        skills.forEach { skill ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(skill.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text(skill.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (skill.prompt.isNotBlank()) {
                            Text(
                                skill.prompt.take(60) + if (skill.prompt.length > 60) "…" else "",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            )
                        }
                    }
                    androidx.compose.material3.Switch(
                        checked = skill.enabled,
                        onCheckedChange = { checked ->
                            onSetSkills(skills.map { if (it.id == skill.id) it.copy(enabled = checked) else it })
                        },
                    )
                    IconButton(
                        onClick = {
                            editName = skill.name; editDesc = skill.description
                            editPrompt = skill.prompt; editingId = skill.id; showDialog = true
                        },
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(Icons.Default.Refresh, "编辑", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(
                        onClick = { onSetSkills(skills.filter { it.id != skill.id }) },
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(Icons.Default.Delete, "删除", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }

    if (showDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(if (editingId != null) "编辑技能" else "添加技能") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = editName, onValueChange = { editName = it },
                        label = { Text("技能名称") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                    )
                    OutlinedTextField(
                        value = editDesc, onValueChange = { editDesc = it },
                        label = { Text("技能描述") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                    )
                    OutlinedTextField(
                        value = editPrompt, onValueChange = { editPrompt = it },
                        label = { Text("提示词（注入到系统指令中）") },
                        placeholder = { Text("定义该技能的行为规则和指令…") },
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                        maxLines = 8,
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (editName.isNotBlank() && editPrompt.isNotBlank()) {
                        val updated = if (editingId != null) {
                            skills.map { if (it.id == editingId) it.copy(name = editName, description = editDesc, prompt = editPrompt) else it }
                        } else {
                            skills + SkillItem(name = editName, description = editDesc, prompt = editPrompt)
                        }
                        onSetSkills(updated)
                        showDialog = false
                    }
                }) { Text("保存") }
            },
            dismissButton = { OutlinedButton(onClick = { showDialog = false }) { Text("取消") } },
        )
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
private fun ThinkingSettingsContent(chatViewModel: ChatViewModel?) {
    val chatState = chatViewModel?.state?.collectAsState()
    val deepThink = chatState?.value?.deepThinkEnabled ?: true

    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("深度思考", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Text("模型在调用工具前进行逐步推理，提升准确性。思考内容可折叠。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = deepThink, onCheckedChange = { chatViewModel?.setDeepThinkEnabled(it) })
                Spacer(Modifier.width(8.dp))
                Text(if (deepThink) "已启用" else "已禁用", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun NotificationSettingsContent(
    settings: NotificationSettings,
    onSetNotificationSettings: (NotificationSettings) -> Unit,
    showToolCalls: Boolean,
    onSetShowToolCalls: (Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "配置对话流中的通知行为：任务完成、异常打断、等待用户操作时的音效和弹出提示",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // 任务完成
        NotificationToggleCard(
            title = "任务完成",
            description = "AI 生成内容完成时通知",
            soundChecked = settings.taskCompletedSound,
            popupChecked = settings.taskCompletedPopup,
            onSoundChange = { onSetNotificationSettings(settings.copy(taskCompletedSound = it)) },
            onPopupChange = { onSetNotificationSettings(settings.copy(taskCompletedPopup = it)) },
        )

        // 任务失败
        NotificationToggleCard(
            title = "异常打断",
            description = "任务执行异常或被打断时通知",
            soundChecked = settings.taskFailedSound,
            popupChecked = settings.taskFailedPopup,
            onSoundChange = { onSetNotificationSettings(settings.copy(taskFailedSound = it)) },
            onPopupChange = { onSetNotificationSettings(settings.copy(taskFailedPopup = it)) },
        )

        // 等待用户操作
        NotificationToggleCard(
            title = "等待用户授权",
            description = "需要用户确认操作时通知",
            soundChecked = settings.waitingUserActionSound,
            popupChecked = settings.waitingUserActionPopup,
            onSoundChange = { onSetNotificationSettings(settings.copy(waitingUserActionSound = it)) },
            onPopupChange = { onSetNotificationSettings(settings.copy(waitingUserActionPopup = it)) },
        )

        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

        // 删除行为卡片开关
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
        ) {
            Column(Modifier.padding(12.dp)) {
                Text("删除文件行为", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(
                    "开启后删除文件时不在对话中显示确认卡片，直接删除",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (settings.deleteCardEnabled) "直接删除（不显示卡片）" else "显示删除确认卡片",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.weight(1f))
                    androidx.compose.material3.Switch(
                        checked = settings.deleteCardEnabled,
                        onCheckedChange = { onSetNotificationSettings(settings.copy(deleteCardEnabled = it)) },
                    )
                }
            }
        }

        // 展示工具调用开关
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
        ) {
            Column(Modifier.padding(12.dp)) {
                Text("展示工具调用", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(
                    "开启后在对话中展示AI的工具调用JSON及执行结果",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (showToolCalls) "展示工具调用" else "隐藏工具调用",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.weight(1f))
                    androidx.compose.material3.Switch(
                        checked = showToolCalls,
                        onCheckedChange = { onSetShowToolCalls(it) },
                    )
                }
            }
        }
    }
}

@Composable
private fun NotificationToggleCard(
    title: String,
    description: String,
    soundChecked: Boolean,
    popupChecked: Boolean,
    onSoundChange: (Boolean) -> Unit,
    onPopupChange: (Boolean) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("音效", style = MaterialTheme.typography.labelSmall)
                    androidx.compose.material3.Switch(checked = soundChecked, onCheckedChange = onSoundChange)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("弹出提示", style = MaterialTheme.typography.labelSmall)
                    androidx.compose.material3.Switch(checked = popupChecked, onCheckedChange = onPopupChange)
                }
            }
        }
    }
}

@Composable
private fun CategoryPlaceholder(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
