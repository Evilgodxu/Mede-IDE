package com.medeide.jh.screens.home.landscape.collab.chat.inputbar.field

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.medeide.jh.R
import com.medeide.jh.model.chat.EngineStatus

// 协作区消息输入框 - 文本输入字段
@Composable
fun CollabInputField(
    inputText: String,
    onInputChange: (String) -> Unit,
    engineStatus: EngineStatus,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = inputText,
        onValueChange = onInputChange,
        placeholder = {
            Text(
                stringResource(R.string.chat_with_agent),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        },
        modifier = modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            focusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        ),
        textStyle = MaterialTheme.typography.bodySmall,
        maxLines = 3,
        enabled = engineStatus == EngineStatus.Ready,
    )
}
