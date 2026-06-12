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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.medeide.jh.model.Rule

// 规则设置内容
@Composable
fun RulesSettingsContent(
    rules: List<Rule>,
    onSetRules: (List<Rule>) -> Unit,
) {
    var editingRuleId by remember { mutableStateOf<String?>(null) }
    var editName by remember { mutableStateOf("") }
    var editContent by remember { mutableStateOf("") }
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
        val ruleDialogMaxHeight = with(LocalConfiguration.current) { (screenHeightDp.dp * 0.75f).coerceAtLeast(200.dp) }
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text(if (editingRuleId != null) "编辑规则" else "添加规则") },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = ruleDialogMaxHeight)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("规则名称") },
                        placeholder = { Text("例如: 编码规范") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )

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
                            rules.map { if (it.id == editingRuleId) it.copy(name = editName, content = editContent) else it }
                        } else {
                            rules + Rule(name = editName, content = editContent)
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
