package com.template.jh.screens.home.components

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.template.jh.R
import com.template.jh.screens.settings.SettingsViewModel
import com.template.jh.screens.settings.components.ThemeSettingsCard
import com.template.jh.screens.settings.components.LanguageSettingsCard
import org.koin.androidx.compose.koinViewModel

// 设置分类枚举
enum class SettingsCategory(
    val labelResId: Int
) {
    General(R.string.settings_category_general),
    MCP(R.string.settings_category_mcp),
    Skill(R.string.settings_category_skill),
    Model(R.string.settings_category_model),
    Conversation(R.string.settings_category_conversation),
    Rules(R.string.settings_category_rules)
}

// 双列设置面板：左分类列表 + 右内容区
@Composable
fun SettingsPane(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()
    var selectedCategory by remember { mutableStateOf(SettingsCategory.General) }

    Row(modifier = modifier.fillMaxSize()) {
        // 左侧分类列表
        Column(
            modifier = Modifier
                .width(140.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f))
                .padding(vertical = 8.dp)
        ) {
            SettingsCategory.entries.forEach { category ->
                SettingsCategoryItem(
                    category = category,
                    isSelected = selectedCategory == category,
                    onClick = { selectedCategory = category }
                )
            }
        }

        VerticalDivider(
            modifier = Modifier.fillMaxHeight(),
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )

        // 右侧内容区
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            SettingsCategoryContent(
                category = selectedCategory,
                state = state,
                onSetThemeMode = { viewModel.setThemeMode(it) },
                onSetLanguage = { viewModel.setLanguage(it) }
            )
        }
    }
}

// 左侧分类项
@Composable
private fun SettingsCategoryItem(
    category: SettingsCategory,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bgColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
    } else {
        androidx.compose.ui.graphics.Color.Transparent
    }

    val textColor = if (isSelected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(bgColor)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(category.labelResId),
            style = MaterialTheme.typography.bodyMedium,
            color = textColor,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

// 右侧分类内容
@Composable
private fun SettingsCategoryContent(
    category: SettingsCategory,
    state: com.template.jh.screens.settings.SettingsUiState,
    onSetThemeMode: (String) -> Unit,
    onSetLanguage: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 分类标题
        Text(
            text = stringResource(category.labelResId),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )

        HorizontalDivider(
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        )

        when (category) {
            SettingsCategory.General -> {
                if (state.isLoading) {
                    Text(
                        text = "加载中…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    ThemeSettingsCard(
                        state = state,
                        onSetThemeMode = onSetThemeMode,
                        modifier = Modifier.fillMaxWidth()
                    )
                    LanguageSettingsCard(
                        state = state,
                        onSetLanguage = onSetLanguage,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            SettingsCategory.MCP -> {
                CategoryPlaceholder(text = "MCP 服务器配置")
            }
            SettingsCategory.Skill -> {
                CategoryPlaceholder(text = "AI 技能管理")
            }
            SettingsCategory.Model -> {
                CategoryPlaceholder(text = "模型选择与配置")
            }
            SettingsCategory.Conversation -> {
                CategoryPlaceholder(text = "对话设置")
            }
            SettingsCategory.Rules -> {
                CategoryPlaceholder(text = "规则配置")
            }
        }
    }
}

// 分类占位内容
@Composable
private fun CategoryPlaceholder(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
