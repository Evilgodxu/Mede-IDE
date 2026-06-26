package com.medeide.jh.screens.home.portrait.sidepanel

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.medeide.jh.core.data.logging.LogCollector
import com.medeide.jh.core.data.repository.UserPreferencesRepository
import com.medeide.jh.model.DEFAULT_ROLE_ID
import com.medeide.jh.model.Rule
import com.medeide.jh.screens.home.cloudchat.CloudChatViewModel
import com.medeide.jh.screens.home.cloudchat.settings.CloudModelSettingsContent
import com.medeide.jh.screens.home.localchat.LocalChatViewModel
import com.medeide.jh.screens.home.settings.SettingsLocalModelContent
import com.medeide.jh.screens.home.settings.SettingsRoleDefinition
import com.medeide.jh.ui.components.AvatarImage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

private sealed class SettingsGroup(val label: String, val icon: ImageVector) {
    data object General : SettingsGroup("通用设置", Icons.Default.BrightnessMedium)
    data object Role : SettingsGroup("角色定义", Icons.Default.Person)
    data object CloudModel : SettingsGroup("云端模型", Icons.Default.Memory)
    data object LocalModel : SettingsGroup("本地模型", Icons.Default.Memory)
}

@Composable
fun PortraitSettingsPanel(
    chatViewModel: CloudChatViewModel? = null,
    modifier: Modifier = Modifier,
) {
    val userPrefs: UserPreferencesRepository = koinInject()
    val localChatViewModel: LocalChatViewModel = koinViewModel()
    val scope = remember { kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main) }
    
    // 直接用 collectAsState 收集，但初始值设为一秒后才触发
    // 这样 UI 先渲染默认值，不阻塞
    var themeMode by remember { mutableStateOf("system") }
    var language by remember { mutableStateOf("system") }
    var userProfile by remember { mutableStateOf(com.medeide.jh.model.chat.UserProfile()) }
    var rules by remember { mutableStateOf(listOf(Rule.defaultRole())) }
    var activeRoleId by remember { mutableStateOf(DEFAULT_ROLE_ID) }
    
    // 用 LaunchedEffect 在 Compose 渲染完成后才开始收集
    androidx.compose.runtime.LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(100)
        themeMode = userPrefs.themeMode.first()
        language = userPrefs.language.first()
        userProfile = userPrefs.userProfile.first()
        rules = userPrefs.rules.first()
        activeRoleId = userPrefs.activeRoleId.first()
    }

    var currentGroup by remember { mutableStateOf<SettingsGroup?>(null) }
    val ctx = LocalContext.current

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (currentGroup != null) {
                IconButton(onClick = { currentGroup = null }) {
                    Icon(Icons.Default.ArrowBack, "返回")
                }
            }
            Text(
                text = currentGroup?.label ?: "设置",
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        if (currentGroup == null) {
            listOf(
                SettingsGroup.General,
                SettingsGroup.Role,
                SettingsGroup.CloudModel,
                SettingsGroup.LocalModel,
            ).forEach { group ->
                EntryCard(label = group.label, icon = group.icon, onClick = { currentGroup = group })
            }
        } else {
            when (currentGroup) {
                SettingsGroup.General -> {
                    Card(shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            SegmentedRow("主题", listOf("system" to "跟随系统", "light" to "浅色", "dark" to "深色"),
                                current = themeMode) { themeMode = it; scope.launch { userPrefs.setThemeMode(it) } }
                            SegmentedRow("语言", listOf("system" to "跟随系统", "zh" to "中文", "en" to "English"),
                                current = language) { language = it; scope.launch { userPrefs.setLanguage(it) } }

                            var editing by remember { mutableStateOf(false) }
                            var eUserName by remember { mutableStateOf(userProfile.userName) }
                            var eAgentName by remember { mutableStateOf(userProfile.agentName) }
                            var eUserAvatarUri by remember { mutableStateOf(userProfile.userAvatarUri) }
                            var eAgentAvatarUri by remember { mutableStateOf(userProfile.agentAvatarUri) }
                            
                            val userAvatarLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
                                uri?.let {
                                    try {
                                        ctx.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    } catch (_: Exception) {
                                    }
                                    eUserAvatarUri = it.toString()
                                }
                            }
                            val agentAvatarLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
                                uri?.let {
                                    try {
                                        ctx.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    } catch (_: Exception) {
                                    }
                                    eAgentAvatarUri = it.toString()
                                }
                            }
                            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                            if (!editing) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            AvatarImage(
                                                initial = if (userProfile.userName.isNotEmpty()) userProfile.userName.first().toString() else "?",
                                                avatarUri = userProfile.userAvatarUri,
                                                size = 28.dp, modifier = Modifier.padding(end = 6.dp))
                                            Text("用户: ${userProfile.userName.ifEmpty { "未设置" }}",
                                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            AvatarImage(
                                                initial = if (userProfile.agentName.isNotEmpty()) userProfile.agentName.first().toString() else "A",
                                                avatarUri = userProfile.agentAvatarUri,
                                                size = 28.dp, modifier = Modifier.padding(end = 6.dp))
                                            Text("AI: ${userProfile.agentName}",
                                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                    TextButton(onClick = {
                                        editing = true
                                        eUserName = userProfile.userName; eAgentName = userProfile.agentName
                                        eUserAvatarUri = userProfile.userAvatarUri; eAgentAvatarUri = userProfile.agentAvatarUri
                                    }) {
                                        Text("编辑", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            AvatarImage(
                                                initial = if (eUserName.isNotEmpty()) eUserName.first().toString() else "?",
                                                avatarUri = eUserAvatarUri,
                                                size = 36.dp, modifier = Modifier.padding(end = 8.dp))
                                            OutlinedTextField(value = eUserName, onValueChange = { eUserName = it },
                                                label = { Text("您的名称") }, singleLine = true,
                                                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp))
                                        }
                                        Spacer(Modifier.height(6.dp))
                                        Row {
                                            Spacer(Modifier.width(44.dp))
                                            OutlinedButton(onClick = { userAvatarLauncher.launch("image/*") }) {
                                                Text(if (eUserAvatarUri.isEmpty()) "选择头像" else "更换头像", style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                    }
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            AvatarImage(
                                                initial = if (eAgentName.isNotEmpty()) eAgentName.first().toString() else "A",
                                                avatarUri = eAgentAvatarUri,
                                                size = 36.dp, modifier = Modifier.padding(end = 8.dp))
                                            OutlinedTextField(value = eAgentName, onValueChange = { eAgentName = it },
                                                label = { Text("AI 助手名称") }, singleLine = true,
                                                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp))
                                        }
                                        Spacer(Modifier.height(6.dp))
                                        Row {
                                            Spacer(Modifier.width(44.dp))
                                            OutlinedButton(onClick = { agentAvatarLauncher.launch("image/*") }) {
                                                Text(if (eAgentAvatarUri.isEmpty()) "选择头像" else "更换头像", style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(onClick = { editing = false }) { Text("取消") }
                                        Button(onClick = {
                                            editing = false
                                            val newProfile = com.medeide.jh.model.chat.UserProfile(
                                                userName = eUserName, agentName = eAgentName.ifEmpty { "AI" },
                                                userAvatarUri = eUserAvatarUri, agentAvatarUri = eAgentAvatarUri)
                                            scope.launch {
                                                userPrefs.setUserProfile(newProfile)
                                            }
                                        }) { Text("保存") }
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))

                            var isSharing by remember { mutableStateOf(false) }
                            Button(
                                onClick = {
                                    isSharing = true
                                    scope.launch {
                                        LogCollector.collectAndShareLogs(ctx)
                                        isSharing = false
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !isSharing,
                            ) {
                                if (isSharing) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                                } else {
                                    Icon(Icons.Default.Share, null, Modifier.size(16.dp))
                                }
                                Spacer(Modifier.width(8.dp))
                                Text(if (isSharing) "正在收集日志…" else "发送日志")
                            }
                        }
                    }
                }
                SettingsGroup.Role -> {
                    SettingsRoleDefinition(rules = rules, activeRoleId = activeRoleId,
                        onSetRules = { scope.launch { userPrefs.setRules(it) } },
                        onSetActiveRoleId = { scope.launch { userPrefs.setActiveRoleId(it) } })
                }
                SettingsGroup.CloudModel -> {
                    if (chatViewModel != null) {
                        CloudModelSettingsContent(viewModel = chatViewModel, modifier = Modifier.fillMaxWidth())
                    } else {
                        Text("需要初始化聊天视图", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                SettingsGroup.LocalModel -> {
                    SettingsLocalModelContent(viewModel = localChatViewModel)
                }
                null -> {}
            }
        }
    }
}

@Composable
private fun EntryCard(label: String, icon: ImageVector, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun SegmentedRow(label: String, options: List<Pair<String, String>>, current: String, onSelect: (String) -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { (key, labelText) ->
                val sel = current == key
                Column(
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(6.dp))
                        .clickable { onSelect(key) }.padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(labelText, style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
