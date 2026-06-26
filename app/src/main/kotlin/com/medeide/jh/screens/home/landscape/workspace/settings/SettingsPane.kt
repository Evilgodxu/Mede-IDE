package com.medeide.jh.screens.home.landscape.workspace.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.medeide.jh.core.data.repository.UserPreferencesRepository
import com.medeide.jh.model.chat.UserProfile
import com.medeide.jh.screens.home.cloudchat.CloudChatViewModel
import com.medeide.jh.screens.home.cloudchat.settings.CloudModelSettingsContent
import com.medeide.jh.screens.home.localchat.LocalChatViewModel
import com.medeide.jh.screens.home.settings.SettingsLocalModelContent
import com.medeide.jh.screens.home.settings.SettingsRoleDefinition
import com.medeide.jh.ui.components.AvatarImage
import kotlinx.coroutines.runBlocking
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

private enum class SettingsTab(val label: String) {
    General("通用设置"), Role("角色定义"), CloudModel("云端模型"), Model("本地模型"),
}

@Composable
fun SettingsPane(modifier: Modifier = Modifier) {
    val userPrefs: UserPreferencesRepository = koinInject()
    val cloudViewModel: CloudChatViewModel = koinViewModel()
    val localViewModel: LocalChatViewModel = koinViewModel()
    val themeMode by userPrefs.themeMode.collectAsState(initial = "system")
    val language by userPrefs.language.collectAsState(initial = "system")
    val userProfile by userPrefs.userProfile.collectAsState(initial = com.medeide.jh.model.chat.UserProfile())
    val rules by userPrefs.rules.collectAsState(initial = emptyList())
    val activeRoleId by userPrefs.activeRoleId.collectAsState(initial = "")

    var tab by remember { mutableStateOf(SettingsTab.General) }

    Row(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.width(140.dp).fillMaxHeight()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f))
                .padding(vertical = 8.dp),
        ) {
            SettingsTab.entries.forEach { t ->
                SettingsCategoryItem(t.label, tab == t) { tab = t }
            }
        }

        VerticalDivider(Modifier.fillMaxHeight(), thickness = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        Box(Modifier.weight(1f).fillMaxHeight()) {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(tab.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                when (tab) {
                    SettingsTab.General -> {
                        SettingsThemeCard(current = themeMode, onSelect = { runBlocking { userPrefs.setThemeMode(it) } })
                        SettingsLanguageCard(current = language, onSelect = { runBlocking { userPrefs.setLanguage(it) } })
                        SettingsProfileCard(profile = userProfile, onUpdate = { runBlocking { userPrefs.setUserProfile(it) } })
                    }
                    SettingsTab.Role -> {
                        SettingsRoleDefinition(rules = rules, activeRoleId = activeRoleId,
                            onSetRules = { runBlocking { userPrefs.setRules(it) } },
                            onSetActiveRoleId = { runBlocking { userPrefs.setActiveRoleId(it) } })
                    }
                    SettingsTab.CloudModel -> {
                        CloudModelSettingsContent(viewModel = cloudViewModel, modifier = Modifier.fillMaxWidth())
                    }
                    SettingsTab.Model -> SettingsLocalModelContent(viewModel = localViewModel)
                }
            }
        }
    }
}

@Composable
private fun SettingsCategoryItem(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else Color.Transparent)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium,
            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

// ── 外观 ──

@Composable
private fun SettingsThemeCard(current: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text("主题设置", style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            SettingsSegmentedRow(options = listOf(
                Triple("system", "跟随系统", Icons.Default.BrightnessMedium),
                Triple("light", "浅色", Icons.Default.LightMode),
                Triple("dark", "深色", Icons.Default.DarkMode),
            ), current = current, onSelect = onSelect)
        }
    }
}

@Composable
private fun SettingsLanguageCard(current: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text("语言设置", style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            SettingsSegmentedRow(options = listOf(
                Triple("system", "跟随系统", Icons.Default.Translate),
                Triple("zh", "简体中文", Icons.Default.TextFields),
                Triple("en", "English", Icons.Default.Language),
            ), current = current, onSelect = onSelect)
        }
    }
}

@Composable
private fun SettingsSegmentedRow(
    options: List<Triple<String, String, ImageVector>>,
    current: String,
    onSelect: (String) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { (value, label, icon) ->
            val sel = current == value
            Column(
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(6.dp))
                    .background(if (sel) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                    .clickable { onSelect(value) }.padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(icon, label, Modifier.size(20.dp),
                    tint = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Text(label, style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ── 用户/Agent 资料 ──

@Composable
private fun SettingsProfileCard(profile: UserProfile, onUpdate: (UserProfile) -> Unit) {
    var editing by remember { mutableStateOf(false) }
    var userName by remember { mutableStateOf(profile.userName) }
    var agentName by remember { mutableStateOf(profile.agentName) }
    var userAvatarUri by remember { mutableStateOf(profile.userAvatarUri) }
    var agentAvatarUri by remember { mutableStateOf(profile.agentAvatarUri) }
    val ctx = androidx.compose.ui.platform.LocalContext.current

    val userAvatarLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            try {
                ctx.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {
            }
            userAvatarUri = it.toString()
        }
    }
    val agentAvatarLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            try {
                ctx.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {
            }
            agentAvatarUri = it.toString()
        }
    }

    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Person, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("个人资料", style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                if (!editing) {
                    TextButton(onClick = {
                        editing = true
                        userName = profile.userName; agentName = profile.agentName
                        userAvatarUri = profile.userAvatarUri; agentAvatarUri = profile.agentAvatarUri
                    }) {
                        Text("编辑")
                    }
                }
            }

            if (editing) {
                // 用户头像 + 名称
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AvatarImage(
                        initial = if (userName.isNotEmpty()) userName.first().toString() else "?",
                        avatarUri = userAvatarUri,
                        size = 40.dp, modifier = Modifier.padding(end = 8.dp))
                    Column(Modifier.weight(1f)) {
                        OutlinedTextField(value = userName, onValueChange = { userName = it },
                            label = { Text("您的名称") }, placeholder = { Text("输入您的名称…") },
                            singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp))
                    }
                    OutlinedButton(onClick = { userAvatarLauncher.launch("image/*") },
                        modifier = Modifier.padding(start = 8.dp)) {
                        Text(if (userAvatarUri.isEmpty()) "选择" else "更换", style = MaterialTheme.typography.labelSmall)
                    }
                }
                // Agent 头像 + 名称
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AvatarImage(
                        initial = if (agentName.isNotEmpty()) agentName.first().toString() else "A",
                        avatarUri = agentAvatarUri,
                        size = 40.dp, modifier = Modifier.padding(end = 8.dp))
                    Column(Modifier.weight(1f)) {
                        OutlinedTextField(value = agentName, onValueChange = { agentName = it },
                            label = { Text("AI 助手名称") }, placeholder = { Text("默认: AI") },
                            singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp))
                    }
                    OutlinedButton(onClick = { agentAvatarLauncher.launch("image/*") },
                        modifier = Modifier.padding(start = 8.dp)) {
                        Text(if (agentAvatarUri.isEmpty()) "选择" else "更换", style = MaterialTheme.typography.labelSmall)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { editing = false }) { Text("取消") }
                    Button(onClick = {
                        editing = false
                        onUpdate(UserProfile(userName = userName, agentName = agentName.ifEmpty { "AI" },
                            userAvatarUri = userAvatarUri, agentAvatarUri = agentAvatarUri))
                    }) { Text("保存") }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AvatarImage(
                        initial = if (profile.userName.isNotEmpty()) profile.userName.first().toString() else "?",
                        avatarUri = profile.userAvatarUri,
                        size = 36.dp, modifier = Modifier.padding(end = 8.dp))
                    Column {
                        Text("用户: ${profile.userName.ifEmpty { "未设置" }}",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AvatarImage(
                        initial = if (profile.agentName.isNotEmpty()) profile.agentName.first().toString() else "A",
                        avatarUri = profile.agentAvatarUri,
                        size = 36.dp, modifier = Modifier.padding(end = 8.dp))
                    Column {
                        Text("AI 助手: ${profile.agentName}",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}


