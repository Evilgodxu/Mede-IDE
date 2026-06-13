package com.medeide.jh.screens.home.landscape.collab.settings

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.medeide.jh.data.utils.FileLogger
import com.medeide.jh.model.chat.EngineStatus
import com.medeide.jh.screens.home.landscape.collab.viewmodel.ChatUiState
import com.medeide.jh.screens.home.landscape.collab.viewmodel.ChatViewModel
import com.medeide.jh.screens.home.settings.CategoryPlaceholder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// 云端模型厂商预设数据类
private data class CloudVendorPreset(
    val name: String,
    val apiEndpoint: String,
    val defaultModels: List<String>,
    val defaultContextWindow: Int = 128000,
    val icon: @Composable () -> Unit = { Icon(Icons.Default.SmartToy, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary) }
)

// 云端模型设置内容
@Composable
fun CloudModelSettingsContent(chatViewModel: ChatViewModel?) {
    if (chatViewModel == null) { CategoryPlaceholder("云端模型配置不可用"); return }
    val chatState by chatViewModel.state.collectAsState()
    CloudModelCardContent(chatViewModel, chatState)
}

@Composable
private fun CloudModelCardContent(chatViewModel: ChatViewModel, chatState: ChatUiState) {
    var editingProfile by remember { mutableStateOf<com.medeide.jh.model.chat.CloudModelProfile?>(null) }
    var showEditDialog by remember { mutableStateOf(false) }
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
        val cloudDialogMaxHeight = with(LocalConfiguration.current) { (screenHeightDp.dp * 0.75f).coerceAtLeast(200.dp) }
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = {
                Text(when {
                    editingProfile != null -> "编辑云端配置"
                    editStep == 0 -> "选择厂商"
                    else -> "添加云端配置"
                })
            },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = cloudDialogMaxHeight)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
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
                                    val screenHeightDp = LocalConfiguration.current.screenHeightDp
                                    DropdownMenu(
                                        expanded = showModelDropdown,
                                        onDismissRequest = { showModelDropdown = false },
                                        modifier = Modifier.heightIn(max = (screenHeightDp * 0.75f).dp)
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
                            visualTransformation = if (showKey) VisualTransformation.None
                            else PasswordVisualTransformation(),
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
                        val context = LocalContext.current
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    isTesting = true
                                    testResult = null
                                    CoroutineScope(Dispatchers.IO).launch {
                                        try {
                                            val client = com.medeide.jh.data.source.remote.CloudLLMClient(context)
                                            val config = com.medeide.jh.model.chat.CloudModelConfig(
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
                                            Log.e("CloudModelCard", "测试连接失败", e)
                                            FileLogger.e("CloudModelCard", "测试连接失败: ${e.message}", e)
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
                                            val client = com.medeide.jh.data.source.remote.CloudLLMClient(context)
                                            val models = client.fetchModels(editEndpoint, editKey)
                                            isFetchingModels = false
                                            availableModels = models
                                        } catch (e: Exception) {
                                            isFetchingModels = false
                                            availableModels = emptyList()
                                            Log.e("CloudModelCard", "获取模型列表失败", e)
                                            FileLogger.e("CloudModelCard", "获取模型列表失败: ${e.message}", e)
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
