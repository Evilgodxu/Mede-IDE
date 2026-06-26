package com.medeide.jh.screens.home.cloudchat.settings

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
import androidx.compose.runtime.rememberCoroutineScope
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
import com.medeide.jh.core.data.source.remote.CloudLLMClient
import com.medeide.jh.model.chat.CloudModelProfile
import com.medeide.jh.screens.home.cloudchat.CloudChatViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private data class VendorPreset(
    val name: String,
    val apiEndpoint: String,
    val defaultModels: List<String>,
    val defaultContextWindow: Int = 128000,
)

private val vendorPresets = listOf(
    VendorPreset("DeepSeek", "https://api.deepseek.com/v1", listOf("deepseek-chat", "deepseek-reasoner"), defaultContextWindow = 1000000),
    VendorPreset("Kimi", "https://api.moonshot.cn/v1", listOf("kimi-k2.5", "kimi-k2.7-code"), defaultContextWindow = 262144),
    VendorPreset("OpenAI", "https://api.openai.com/v1", listOf("gpt-4o", "gpt-4o-mini"), defaultContextWindow = 128000),
    VendorPreset("自定义", "", listOf(), defaultContextWindow = 128000),
)

@Composable
fun CloudModelSettingsContent(
    viewModel: CloudChatViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    var showEditDialog by remember { mutableStateOf(false) }
    var editStep by remember { mutableIntStateOf(0) }
    var editId by remember { mutableStateOf("") }
    var editName by remember { mutableStateOf("") }
    var editEndpoint by remember { mutableStateOf("") }
    var editKey by remember { mutableStateOf("") }
    var editModel by remember { mutableStateOf("") }
    var editContextWindow by remember { mutableIntStateOf(128000) }
    var editMaxTokens by remember { mutableIntStateOf(16000) }
    var editMaxToolRounds by remember { mutableIntStateOf(200) }
    var showKey by remember { mutableStateOf(false) }
    var isTesting by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var isFetchingModels by remember { mutableStateOf(false) }
    var fetchedModels by remember { mutableStateOf<List<String>>(emptyList()) }

    Column(modifier = modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

        // 启用状态指示
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("云端大模型", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                }
                Text("接入 OpenAI 兼容 API，支持 DeepSeek、Kimi、OpenAI 等。配置后自动启用。",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // 添加配置按钮
        OutlinedButton(onClick = {
            editId = ""; editName = ""; editEndpoint = ""; editKey = ""
            editModel = ""; editContextWindow = 128000; editMaxTokens = 16000; editMaxToolRounds = 200
            editStep = 0; testResult = null; fetchedModels = emptyList(); showEditDialog = true
        }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Add, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp))
            Text("添加云端配置")
        }

        // 配置文件列表
        state.cloudModelProfiles.forEach { profile ->
            val isActive = profile.id == state.activeCloudProfileId
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
                            Spacer(Modifier.width(4.dp)); Text("当前", style = MaterialTheme.typography.labelSmall, color = Color(0xFF4CAF50))
                        }
                    }
                    Text("${profile.apiEndpoint}  |  ${profile.modelName}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (!isActive) {
                            OutlinedButton(onClick = { viewModel.switchCloudProfile(profile.id) }, modifier = Modifier.height(28.dp)) {
                                Text("切换", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        OutlinedButton(onClick = {
                            editId = profile.id; editName = profile.name; editEndpoint = profile.apiEndpoint
                            editKey = profile.apiKey; editModel = profile.modelName
                            editContextWindow = profile.contextWindow; editMaxTokens = profile.maxTokens; editMaxToolRounds = profile.maxToolRounds
                            editStep = 1; testResult = null; fetchedModels = emptyList(); showEditDialog = true
                        }, modifier = Modifier.height(28.dp)) {
                            Text("编辑", style = MaterialTheme.typography.labelSmall)
                        }
                        IconButton(onClick = { viewModel.removeCloudProfile(profile.id) }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Delete, "删除", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }

        // 验证连接
        if (state.cloudModelProfiles.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { viewModel.verifyCloudConnection() }) {
                    Text("验证当前连接", style = MaterialTheme.typography.labelSmall)
                }
                val verifyMsg = when {
                    state.engineStatus == com.medeide.jh.model.chat.EngineStatus.Loading && state.engineErrorMessage == "验证中…" -> "验证中…"
                    state.engineErrorMessage == "ok" -> "ok"
                    state.engineErrorMessage.isNotBlank() -> state.engineErrorMessage
                    else -> ""
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

    // 添加/编辑对话框
    if (showEditDialog) {
        val dialogMaxHeight = with(LocalConfiguration.current) { (screenHeightDp.dp * 0.75f).coerceAtLeast(200.dp) }
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text(if (editId.isNotEmpty()) "编辑配置" else "添加云端配置") },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = dialogMaxHeight).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (editStep == 0 && editId.isEmpty()) {
                        vendorPresets.forEach { vendor ->
                            Card(modifier = Modifier.fillMaxWidth().clickable {
                                editContextWindow = vendor.defaultContextWindow
                                if (vendor.name == "自定义") { editEndpoint = ""; editModel = "" }
                                else { editName = vendor.name; editEndpoint = vendor.apiEndpoint; editModel = vendor.defaultModels.firstOrNull() ?: "" }
                                editStep = 1
                            }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))) {
                                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(vendor.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                        if (vendor.apiEndpoint.isNotEmpty()) Text(vendor.apiEndpoint, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    } else {
                        OutlinedTextField(value = editName, onValueChange = { editName = it }, label = { Text("配置名称") }, placeholder = { Text("例如: DeepSeek") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        OutlinedTextField(value = editEndpoint, onValueChange = { editEndpoint = it; fetchedModels = emptyList() }, label = { Text("API 端点") }, placeholder = { Text("https://api.deepseek.com/v1") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(value = editModel, onValueChange = { editModel = it }, label = { Text("模型名称") }, placeholder = { Text("deepseek-chat") }, modifier = Modifier.weight(1f), singleLine = true)
                            val allOptions = (vendorPresets.find { editEndpoint.contains(it.apiEndpoint.removeSuffix("/v1").removeSuffix("/v1/"), ignoreCase = true) }?.defaultModels ?: emptyList()) + fetchedModels
                            Box {
                                var showDropdown by remember { mutableStateOf(false) }
                                IconButton(onClick = { if (allOptions.isNotEmpty()) showDropdown = true }) {
                                    Icon(Icons.Default.ArrowDropDown, "选择", tint = MaterialTheme.colorScheme.primary)
                                }
                                if (showDropdown && allOptions.isNotEmpty()) {
                                    DropdownMenu(expanded = true, onDismissRequest = { showDropdown = false }, modifier = Modifier.heightIn(max = 300.dp)) {
                                        allOptions.distinct().forEach { m ->
                                            DropdownMenuItem(text = { Text(m, style = MaterialTheme.typography.bodySmall) }, onClick = { editModel = m; showDropdown = false })
                                        }
                                    }
                                }
                            }
                        }
                        OutlinedTextField(value = editKey, onValueChange = { editKey = it }, label = { Text("API Key") }, placeholder = { Text("sk-...") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                            visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = { IconButton(onClick = { showKey = !showKey }) { Icon(if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility, null) } })
                        OutlinedTextField(value = if (editContextWindow <= 0) "" else editContextWindow.toString(), onValueChange = { v -> editContextWindow = v.filter { it.isDigit() }.take(7).toIntOrNull() ?: 128000 }, label = { Text("上下文窗口 (token)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        OutlinedTextField(value = if (editMaxTokens <= 0) "" else editMaxTokens.toString(), onValueChange = { v -> editMaxTokens = v.filter { it.isDigit() }.take(7).toIntOrNull() ?: 16000 }, label = { Text("输出最大 token") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        OutlinedTextField(value = editMaxToolRounds.toString(), onValueChange = { v -> editMaxToolRounds = v.filter { it.isDigit() }.take(4).toIntOrNull() ?: 200 }, label = { Text("工具调用轮次上限") }, modifier = Modifier.fillMaxWidth(), singleLine = true)

                        val context = LocalContext.current
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = {
                                isTesting = true; testResult = null
                                kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                                    val client = CloudLLMClient()
                                    val config = CloudModelProfile(apiEndpoint = editEndpoint, apiKey = editKey, modelName = editModel.ifEmpty { "gpt-3.5-turbo" })
                                    val result = client.verifyConnection(config)
                                    isTesting = false; testResult = result.getOrNull() ?: result.exceptionOrNull()?.message ?: "失败"
                                }
                            }, enabled = editEndpoint.isNotBlank() && !isTesting, modifier = Modifier.weight(1f)) {
                                if (isTesting) { CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp); Spacer(Modifier.width(4.dp)) }
                                else { Icon(Icons.Default.NetworkCheck, null, Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)) }
                                Text("测试连接", style = MaterialTheme.typography.labelSmall)
                            }
                            OutlinedButton(onClick = {
                                isFetchingModels = true; fetchedModels = emptyList()
                                kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                                    val client = CloudLLMClient()
                                    val result = client.fetchModels(editEndpoint, editKey)
                                    isFetchingModels = false
                                    fetchedModels = result.getOrNull() ?: emptyList()
                                }
                            }, enabled = editEndpoint.isNotBlank() && !isFetchingModels, modifier = Modifier.weight(1f)) {
                                if (isFetchingModels) { CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp); Spacer(Modifier.width(4.dp)) }
                                else { Icon(Icons.Default.Refresh, null, Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)) }
                                Text("获取模型", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        testResult?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = if (it == "ok") Color(0xFF4CAF50) else MaterialTheme.colorScheme.error) }

                        if (editId.isEmpty()) {
                            TextButton(onClick = { editStep = 0 }) { Text("← 返回选择厂商") }
                        }
                    }
                }
            },
            confirmButton = {
                if (editStep == 1 || editId.isNotEmpty()) {
                    Button(onClick = {
                        if (editEndpoint.isNotBlank() && editModel.isNotBlank()) {
                            if (editId.isNotEmpty()) {
                                viewModel.updateCloudProfile(CloudModelProfile(id = editId, name = editName, apiEndpoint = editEndpoint, apiKey = editKey, modelName = editModel, contextWindow = editContextWindow, maxTokens = editMaxTokens, maxToolRounds = editMaxToolRounds))
                            } else {
                                viewModel.addCloudProfile(editName, editEndpoint, editKey, editModel, editContextWindow, editMaxTokens, editMaxToolRounds)
                            }
                            showEditDialog = false
                        }
                    }, enabled = editEndpoint.isNotBlank() && editModel.isNotBlank()) { Text("保存") }
                }
            },
            dismissButton = { OutlinedButton(onClick = { showEditDialog = false }) { Text("取消") } },
        )
    }
}
