package com.medeide.jh.screens.home.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun SettingsTerminal(viewModel: com.medeide.jh.screens.home.HomeViewModel) {
    val terminalHeight by viewModel.terminalHeight.collectAsState()
    var localHeight by remember { mutableStateOf(terminalHeight) }

    Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text(text = "终端高度", style = MaterialTheme.typography.titleSmall)
        Text(text = "当前高度: $terminalHeight dp", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))

        Slider(
            value = localHeight.toFloat(),
            onValueChange = { localHeight = it.toInt() },
            onValueChangeFinished = { viewModel.setTerminalHeight(localHeight) },
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
