package com.medeide.jh.screens.home.portrait.sidepanel

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun PortraitDashboardPanel(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
    ) {
        Text(
            text = "上下文仪表盘",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(24.dp))

        // 上半：圆饼图
        PieChartSection(modifier = Modifier.fillMaxWidth())

        Spacer(modifier = Modifier.height(24.dp))
        HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        )
        Spacer(modifier = Modifier.height(24.dp))

        // 下半：用量信息
        UsageSection(modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun PieChartSection(
    modifier: Modifier = Modifier,
) {
    val segments = listOf(
        PieSegment("代码", 0.45f, MaterialTheme.colorScheme.primary),
        PieSegment("文档", 0.25f, MaterialTheme.colorScheme.secondary),
        PieSegment("对话", 0.20f, MaterialTheme.colorScheme.tertiary),
        PieSegment("其他", 0.10f, MaterialTheme.colorScheme.outline),
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Canvas(modifier = Modifier.size(160.dp)) {
            var startAngle = -90f
            segments.forEach { segment ->
                val sweep = segment.fraction * 360f
                drawArc(
                    color = segment.color,
                    startAngle = startAngle,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = Offset.Zero,
                    size = Size(size.width, size.height),
                    style = Stroke(width = 28.dp.toPx(), cap = StrokeCap.Round),
                )
                startAngle += sweep
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            segments.forEach { segment ->
                LegendItem(segment)
            }
        }
    }
}

@Composable
private fun LegendItem(
    segment: PieSegment,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(modifier = Modifier.size(8.dp)) {
            drawCircle(segment.color)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = segment.label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "${(segment.fraction * 100).toInt()}%",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun UsageSection(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        UsageItem(label = "今日请求", value = "1,248")
        UsageItem(label = "Token 消耗", value = "42.6K")
        UsageItem(label = "上下文长度", value = "8,192")
        UsageItem(label = "平均响应", value = "1.2s")
    }
}

@Composable
private fun UsageItem(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private data class PieSegment(
    val label: String,
    val fraction: Float,
    val color: Color,
)
