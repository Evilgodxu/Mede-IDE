package com.medeide.jh.screens.home.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.medeide.jh.screens.home.HomeViewModel

@Composable
fun SettingsTerminal(viewModel: HomeViewModel) {
    val terminalHeight by viewModel.terminalHeight.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text(text = "终端设置", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "终端高度", style = MaterialTheme.typography.titleSmall)
                Text(text = "当前高度: $terminalHeight dp", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))

                Slider(
                    value = terminalHeight.toFloat(),
                    onValueChange = { viewModel.setTerminalHeight(it.toInt()) },
                    valueRange = 100f..500f,
                    steps = 7,
                    modifier = Modifier.padding(top = 12.dp).fillMaxWidth(),
                )

                Row(modifier = Modifier.padding(top = 8.dp)) {
                    Text("100dp", style = MaterialTheme.typography.labelSmall)
                    Spacer(modifier = Modifier.weight(1f))
                    Text("500dp", style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "使用说明", style = MaterialTheme.typography.titleSmall)
                Text(text = "• 点击左侧边栏的 Terminal 图标打开终端", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                Text(text = "• 点击终端屏幕即可弹出键盘进行输入", style = MaterialTheme.typography.bodySmall)
                Text(text = "• 点击终端右上角关闭按钮或再次点击 Terminal 图标关闭终端", style = MaterialTheme.typography.bodySmall)
                Text(text = "• 终端基于 Termux 源码构建，支持大多数 Linux 命令", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
