package com.medeide.jh.screens.home.landscape.sidebar.resourcepanel

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Alignment
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompressDialog(
    defaultName: String,
    onConfirm: (archiveName: String, format: String, level: Int, password: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var archiveName by remember { mutableStateOf("$defaultName.zip") }
    var format by remember { mutableStateOf("zip") }
    var level by remember { mutableStateOf(6) }
    var usePassword by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var formatExpanded by remember { mutableStateOf(false) }

    val formats = listOf("zip", "tar", "gz")
    val maxHeight = with(LocalConfiguration.current) { (screenHeightDp.dp * 0.75f).coerceAtLeast(200.dp) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("创建压缩文件") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxHeight)
                    .verticalScroll(rememberScrollState()),
            ) {
                OutlinedTextField(
                    value = archiveName,
                    onValueChange = { archiveName = it },
                    label = { Text("压缩包文件名") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))

                ExposedDropdownMenuBox(
                    expanded = formatExpanded,
                    onExpandedChange = { formatExpanded = it },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    OutlinedTextField(
                        value = format,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("格式") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = formatExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                    )
                    ExposedDropdownMenuBox(
                        expanded = formatExpanded,
                        onExpandedChange = { formatExpanded = it },
                    ) {
                        formats.forEach {
                            DropdownMenuItem(
                                text = { Text(it) },
                                onClick = { format = it; formatExpanded = false },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))

                Text("压缩级别: $level", style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = level.toFloat(),
                    onValueChange = { level = it.toInt() },
                    valueRange = 0f..9f,
                    steps = 8,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("密码保护", modifier = Modifier.weight(1f))
                    Switch(checked = usePassword, onCheckedChange = { usePassword = it })
                }
                if (usePassword) {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("密码") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        archiveName,
                        format,
                        level,
                        if (usePassword && password.isNotBlank()) password else null,
                    )
                },
                enabled = archiveName.isNotBlank(),
            ) {
                Text("创建")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
