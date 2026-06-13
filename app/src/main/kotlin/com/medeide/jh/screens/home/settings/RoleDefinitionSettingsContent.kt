package com.medeide.jh.screens.home.settings

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.medeide.jh.R
import com.medeide.jh.model.DEFAULT_ROLE_ID
import com.medeide.jh.model.Rule

// 角色定义设置内容
@Composable
fun RoleDefinitionSettingsContent(
    rules: List<Rule>,
    activeRoleId: String,
    onSetRules: (List<Rule>) -> Unit,
    onSetActiveRoleId: (String) -> Unit,
) {
    var editingRole by remember { mutableStateOf<Rule?>(null) }
    var editName by remember { mutableStateOf("") }
    var editContent by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            stringResource(R.string.rules_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // 添加角色按钮
        OutlinedButton(
            onClick = {
                editName = ""
                editContent = ""
                editingRole = null
                showAddDialog = true
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.Add, null, Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.rules_add))
        }

        // 角色列表（使用 RadioButton 选择）
        Column(Modifier.selectableGroup()) {
            rules.forEach { rule ->
                RoleCard(
                    rule = rule,
                    isSelected = rule.id == activeRoleId,
                    onSelect = { onSetActiveRoleId(rule.id) },
                    onEdit = {
                        editName = rule.name
                        editContent = rule.content
                        editingRole = rule
                        showAddDialog = true
                    },
                    onDelete = if (rule.isDefault) null else {
                        { onSetRules(rules.filter { it.id != rule.id }) }
                    },
                )
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    // 添加/编辑对话框
    if (showAddDialog) {
        val dialogMaxHeight = with(LocalConfiguration.current) { (screenHeightDp.dp * 0.75f).coerceAtLeast(200.dp) }
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = {
                Text(
                    if (editingRole != null) stringResource(R.string.rules_edit)
                    else stringResource(R.string.rules_add)
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = dialogMaxHeight)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text(stringResource(R.string.rules_name_hint)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = editContent,
                        onValueChange = { editContent = it },
                        label = { Text(stringResource(R.string.rules_content_hint)) },
                        modifier = Modifier.fillMaxWidth().height(150.dp),
                        maxLines = 10,
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (editName.isNotBlank() && editContent.isNotBlank()) {
                        val updated = if (editingRole != null) {
                            rules.map { if (it.id == editingRole!!.id) it.copy(name = editName, content = editContent) else it }
                        } else {
                            rules + Rule(name = editName, content = editContent)
                        }
                        onSetRules(updated)
                        showAddDialog = false
                    }
                }) { Text(stringResource(R.string.rules_save)) }
            },
            dismissButton = {
                OutlinedButton(onClick = { showAddDialog = false }) { Text(stringResource(R.string.rules_cancel)) }
            },
        )
    }
}

// 单个角色卡片
@Composable
private fun RoleCard(
    rule: Rule,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = isSelected,
                onClick = onSelect,
                role = Role.RadioButton,
            ),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Row(
            modifier = Modifier.padding(start = 4.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(
                selected = isSelected,
                onClick = null, // handled by selectable
            )
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (rule.isDefault) {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = null,
                            Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        rule.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    if (rule.isDefault) stringResource(R.string.rules_default_role_desc)
                    else rule.content.take(60) + if (rule.content.length > 60) "…" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (!rule.isDefault) {
                IconButton(onClick = onEdit, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Refresh, stringResource(R.string.rules_edit), Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (onDelete != null) {
                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Delete, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
