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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.medeide.jh.model.DEFAULT_ROLE_ID
import com.medeide.jh.model.Rule

@Composable
fun SettingsRoleDefinition(
    rules: List<Rule>,
    activeRoleId: String,
    onSetRules: (List<Rule>) -> Unit,
    onSetActiveRoleId: (String) -> Unit,
) {
    var editingRule by remember { mutableStateOf<Rule?>(null) }
    var editName by remember { mutableStateOf("") }
    var editContent by remember { mutableStateOf("") }
    var showDialog by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("角色决定了 AI 助手的行为方式和系统指令。可以添加多个角色并切换。",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        OutlinedButton(onClick = {
            editName = ""; editContent = ""; editingRule = null; showDialog = true
        }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Add, null, Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("添加角色")
        }

        Column(Modifier.selectableGroup()) {
            rules.forEachIndexed { idx, rule ->
                if (idx > 0) Spacer(Modifier.height(8.dp))
                RoleCard(rule = rule, isSelected = rule.id == activeRoleId,
                    onSelect = { onSetActiveRoleId(rule.id) },
                    onEdit = { editName = rule.name; editContent = rule.content; editingRule = rule; showDialog = true },
                    onDelete = if (rule.isDefault) null else { { onSetRules(rules.filter { it.id != rule.id }) } })
            }
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(if (editingRule != null) "编辑角色" else "添加角色") },
            text = {
                Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(value = editName, onValueChange = { editName = it },
                        label = { Text("角色名称") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(value = editContent, onValueChange = { editContent = it },
                        label = { Text("角色指令") }, modifier = Modifier.fillMaxWidth().height(150.dp), maxLines = 10)
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (editName.isNotBlank() && editContent.isNotBlank()) {
                        val updated = if (editingRule != null)
                            rules.map { if (it.id == editingRule!!.id) it.copy(name = editName, content = editContent) else it }
                        else rules + Rule(name = editName, content = editContent)
                        onSetRules(updated); showDialog = false
                    }
                }) { Text("保存") }
            },
            dismissButton = { OutlinedButton(onClick = { showDialog = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun RoleCard(rule: Rule, isSelected: Boolean, onSelect: () -> Unit, onEdit: () -> Unit, onDelete: (() -> Unit)?) {
    Card(
        modifier = Modifier.fillMaxWidth().selectable(selected = isSelected, onClick = onSelect, role = Role.RadioButton),
        colors = CardDefaults.cardColors(containerColor = if (isSelected)
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
    ) {
        Row(Modifier.padding(start = 4.dp, end = 12.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = isSelected, onClick = null)
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (rule.isDefault) {
                        Icon(Icons.Default.Star, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(rule.name, style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium)
                }
                Text(if (rule.isDefault) "内置默认角色" else rule.content.take(60) + if (rule.content.length > 60) "…" else "",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (!rule.isDefault) {
                IconButton(onClick = onEdit, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Edit, "编辑", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (onDelete != null) {
                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Delete, "删除", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
