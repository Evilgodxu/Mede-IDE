package com.medeide.jh.screens.home.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.medeide.jh.R
import com.medeide.jh.model.McpServer
import com.medeide.jh.model.Rule
import com.medeide.jh.screens.home.landscape.collab.viewmodel.ChatViewModel
import com.medeide.jh.screens.home.landscape.collab.settings.CloudModelSettingsContent
import com.medeide.jh.screens.home.landscape.collab.settings.LocalModelSettingsContent
import com.medeide.jh.screens.home.landscape.collab.settings.McpSettingsContent
import com.medeide.jh.screens.home.HomeUiState
import com.medeide.jh.screens.home.HomeViewModel
import org.koin.androidx.compose.koinViewModel

// 设置分类枚举
enum class SettingsCategory(val labelResId: Int) {
    General(R.string.settings_category_general),
    RoleDefinition(R.string.settings_category_rules),
    LocalModel(R.string.settings_category_local_model),
    CloudModel(R.string.settings_category_cloud_model),
    MCP(R.string.settings_category_mcp),
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
                onSetRules = { viewModel.setRules(it) },
                onSetActiveRoleId = { viewModel.setActiveRoleId(it) },
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
    onSetActiveRoleId: (String) -> Unit,
    onSetMcpServers: (List<McpServer>) -> Unit,
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
            SettingsCategory.RoleDefinition -> RoleDefinitionSettingsContent(
                rules = state.rules,
                activeRoleId = state.activeRoleId,
                onSetRules = onSetRules,
                onSetActiveRoleId = onSetActiveRoleId,
            )
            SettingsCategory.LocalModel -> LocalModelSettingsContent(chatViewModel)
            SettingsCategory.CloudModel -> CloudModelSettingsContent(chatViewModel)
            SettingsCategory.MCP -> McpSettingsContent(state.mcpServers, onSetMcpServers)
        }
    }
}

@Composable
internal fun CategoryPlaceholder(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
