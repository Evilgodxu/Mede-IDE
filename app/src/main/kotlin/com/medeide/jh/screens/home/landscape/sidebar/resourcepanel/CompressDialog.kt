package com.medeide.jh.screens.home.landscape.sidebar.resourcepanel

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompressDialog(
    defaultName: String,
    onConfirm: (
        archiveName: String,
        format: String,
        level: Int,
        password: String?,
        volumeSize: Int?,
        deleteSource: Boolean,
    ) -> Unit,
    onDismiss: () -> Unit,
) {
    var archiveName by remember { mutableStateOf("$defaultName.zip") }
    var format by remember { mutableStateOf("zip") }
    var level by remember { mutableStateOf(0) }
    var password by remember { mutableStateOf("") }
    var volumeSize by remember { mutableStateOf<Int?>(null) }
    var deleteSource by remember { mutableStateOf(false) }
    var formatExpanded by remember { mutableStateOf(false) }
    var levelExpanded by remember { mutableStateOf(false) }
    var volumeExpanded by remember { mutableStateOf(false) }

    val formats = listOf("zip", "tar", "gz")
    val levels = listOf(
        0 to "仅存储",
        1 to "最快",
        3 to "快速",
        5 to "正常",
        7 to "最大",
        9 to "极限",
    )
    val volumeOptions = listOf(
        null to "无",
        10 to "10",
        50 to "50",
        100 to "100",
        500 to "500",
        1000 to "1000",
    )

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
                    label = { Text("文件名") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ExposedDropdownMenuBox(
                        expanded = formatExpanded,
                        onExpandedChange = { formatExpanded = it },
                        modifier = Modifier.weight(1f),
                    ) {
                        OutlinedTextField(
                            value = format,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("格式") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = formatExpanded) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        androidx.compose.material3.DropdownMenu(
                            expanded = formatExpanded,
                            onDismissRequest = { formatExpanded = false },
                        ) {
                            formats.forEach {
                                DropdownMenuItem(
                                    text = { Text(it) },
                                    onClick = { format = it; formatExpanded = false },
                                )
                            }
                        }
                    }

                    Spacer(Modifier.width(8.dp))

                    ExposedDropdownMenuBox(
                        expanded = levelExpanded,
                        onExpandedChange = { levelExpanded = it },
                        modifier = Modifier.weight(1f),
                    ) {
                        OutlinedTextField(
                            value = levels.find { it.first == level }?.second ?: "仅存储",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("压缩级别") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = levelExpanded) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        androidx.compose.material3.DropdownMenu(
                            expanded = levelExpanded,
                            onDismissRequest = { levelExpanded = false },
                        ) {
                            levels.forEach { (value, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = { level = value; levelExpanded = false },
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("密码（不加密请留空）") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("分卷大小", modifier = Modifier.padding(end = 8.dp))
                    ExposedDropdownMenuBox(
                        expanded = volumeExpanded,
                        onExpandedChange = { volumeExpanded = it },
                        modifier = Modifier.weight(1f),
                    ) {
                        OutlinedTextField(
                            value = volumeOptions.find { it.first == volumeSize }?.second ?: "无",
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = volumeExpanded) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        androidx.compose.material3.DropdownMenu(
                            expanded = volumeExpanded,
                            onDismissRequest = { volumeExpanded = false },
                        ) {
                            volumeOptions.forEach { (value, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = { volumeSize = value; volumeExpanded = false },
                                )
                            }
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Text("MB")
                }
                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("压缩后删除源文件", modifier = Modifier.weight(1f))
                    Switch(checked = deleteSource, onCheckedChange = { deleteSource = it })
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
                        password.takeIf { it.isNotBlank() },
                        volumeSize,
                        deleteSource,
                    )
                },
                enabled = archiveName.isNotBlank(),
            ) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
