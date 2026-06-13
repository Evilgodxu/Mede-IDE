package com.medeide.jh.screens.home.landscape.collab.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medeide.jh.model.McpServer

// MCP 设置内容
@Composable
fun McpSettingsContent(
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
                    Switch(
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
        val mcpDialogMaxHeight = with(LocalConfiguration.current) { (screenHeightDp.dp * 0.75f).coerceAtLeast(200.dp) }
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(if (editingId != null) "编辑 MCP 服务器" else "添加 MCP 服务器") },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = mcpDialogMaxHeight)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
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
