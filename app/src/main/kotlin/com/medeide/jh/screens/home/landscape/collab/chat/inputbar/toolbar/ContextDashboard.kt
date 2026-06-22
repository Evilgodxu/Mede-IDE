package com.medeide.jh.screens.home.landscape.collab.chat.inputbar.toolbar

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.medeide.jh.data.analytics.ToolUsageStats
import com.medeide.jh.data.analytics.UsageStats
import com.medeide.jh.screens.home.memory.*

@Composable
fun ContextDashboard(
    snapshot: ContextSnapshot,
    breakdown: TokenBreakdown,
    contextSummary: String,
    openedFilePaths: List<String>,
    memoryEntryCount: Int,
    memoryTotalTokens: Int,
    toolStats: Map<String, ToolUsageStats> = emptyMap(),
    onDismiss: () -> Unit,
) {
    var activeLayer by remember { mutableIntStateOf(0) } // 0=概览, 1=Token详情

    val dialogMaxHeight = with(androidx.compose.ui.platform.LocalConfiguration.current) {
        (screenHeightDp.dp * 0.75f).coerceAtLeast(300.dp)
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .widthIn(min = 340.dp, max = 440.dp)
                .heightIn(max = dialogMaxHeight),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        ) {
            Column {
                TabBar(activeLayer) { activeLayer = it }

                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    when (activeLayer) {
                        0 -> OverviewLayer(snapshot, memoryTotalTokens, toolStats)
                        1 -> TokenDetailLayer(snapshot, breakdown)
                    }

                    if (openedFilePaths.isNotEmpty()) {
                        HorizontalDivider()
                        Text("已打开文件", fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        openedFilePaths.take(8).forEach { path ->
                            val name = path.substringAfterLast('/')
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 1.dp)) {
                                Icon(Icons.Default.Description, null, Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.outline)
                                Spacer(Modifier.width(6.dp))
                                Text(name, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        if (openedFilePaths.size > 8) {
                            Text("  ... 及其他 ${openedFilePaths.size - 8} 个文件",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TabBar(active: Int, onSelect: (Int) -> Unit) {
    val tabs = listOf(
        "概览" to Icons.Default.Dashboard,
        "Token" to Icons.Default.DataUsage,
    )
    PrimaryScrollableTabRow(
        selectedTabIndex = active,
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surface,
        edgePadding = 8.dp,
        divider = { HorizontalDivider(thickness = 0.5.dp) },
    ) {
        tabs.forEachIndexed { i, (label, icon) ->
            Tab(
                selected = active == i,
                onClick = { onSelect(i) },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(icon, null, Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(label, fontSize = 12.sp)
                    }
                },
            )
        }
    }
}

// ========== L1: 概览层 ==========

@Composable
private fun OverviewLayer(snapshot: ContextSnapshot, memoryTotalTokens: Int, toolStats: Map<String, ToolUsageStats> = emptyMap()) {
    val ratio = snapshot.ratio
    val usageColor = Color(HeatColors.ratioColor(ratio))
    val usageDeg = ratio * 360f

    // 总占用 = 当前用量 + 累计压缩释放（压缩释放的 token 已不再占窗口，但展示历史总消耗）
    val hasCompressed = snapshot.isCompressed && snapshot.compressedTokens > 0
    val totalUsedTokens = if (hasCompressed) snapshot.usedTokens + snapshot.compressedTokens else snapshot.usedTokens
    val totalRatio = if (snapshot.maxTokens > 0)
        (totalUsedTokens.toFloat() / snapshot.maxTokens).coerceIn(0f, 1f) else 0f
    val totalDeg = totalRatio * 360f
    // 压缩段角度 = totalDeg 中除去 usageDeg 的部分
    val compressedDeg = (totalDeg - usageDeg).coerceAtLeast(0f)
    val showCompressedArc = hasCompressed && compressedDeg > 0.5f

    val memoryRatio = if (snapshot.maxTokens > 0 && memoryTotalTokens > 0)
        (memoryTotalTokens.toFloat() / snapshot.maxTokens).coerceIn(0f, 1f) else 0f
    val memoryDeg = (memoryRatio * 360f).coerceAtMost(360f)

    Box(
        modifier = Modifier.fillMaxWidth().height(160.dp),
        contentAlignment = Alignment.Center,
    ) {
        // 外环：记忆（紧贴内环外侧）
        Canvas(Modifier.size(112.dp)) {
            val sw = 8f
            val r = size.minDimension / 2f - sw / 2f
            val tl = Offset(sw / 2f, sw / 2f)
            val sz = Size(r * 2, r * 2)

            // 外环轨道
            drawArc(Color(0xFFE3F2FD), -90f, 360f, false,
                style = Stroke(sw, cap = StrokeCap.Butt), topLeft = tl, size = sz)

            // 外环记忆段
            if (memoryDeg > 0.5f) {
                drawArc(Color(0xFF1565C0).copy(alpha = 0.45f), -90f, memoryDeg, false,
                    style = Stroke(sw, cap = StrokeCap.Butt), topLeft = tl, size = sz)
            }
        }

        // 内环：当前用量 + 压缩释放段（分别用不同颜色）+ 剩余空间
        Canvas(Modifier.size(100.dp)) {
            val sw = 10f
            val r = size.minDimension / 2f - sw / 2f
            val tl = Offset(sw / 2f, sw / 2f)
            val sz = Size(r * 2, r * 2)

            // 轨道（剩余空间）
            drawArc(Color(0xFFE8E8E8), -90f, 360f, false,
                style = Stroke(sw, cap = StrokeCap.Butt), topLeft = tl, size = sz)

            // 当前用量段
            if (usageDeg > 0.5f) {
                drawArc(usageColor, -90f, usageDeg, false,
                    style = Stroke(sw, cap = StrokeCap.Butt), topLeft = tl, size = sz)
            }

            // 压缩释放段（紧跟在当前用量段后面，用紫色）
            if (showCompressedArc) {
                val start = -90f + usageDeg
                drawArc(Color(0xFF9C27B0), start, compressedDeg, false,
                    style = Stroke(sw, cap = StrokeCap.Butt), topLeft = tl, size = sz)
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${(ratio * 100).toInt()}%",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold, color = usageColor)
            Text("${fmt(snapshot.usedTokens)}/${fmt(snapshot.maxTokens)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (hasCompressed) {
                Text("已释放 ${fmt(snapshot.compressedTokens)}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF9C27B0))
            }
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        LegendDot(usageColor, "当前用量")
        if (hasCompressed) LegendDot(Color(0xFF9C27B0), "已释放")
        LegendDot(Color(0xFFE0E0E0), "剩余空间")
        LegendDot(Color(0xFF1565C0).copy(alpha = 0.45f), "记忆")
    }

    HorizontalDivider()

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MetricCard("消息", "${snapshot.messageCount}", Icons.AutoMirrored.Filled.Chat, Color(0xFF4CAF50),
            Modifier.weight(1f))
        MetricCard("工具", "${snapshot.toolCallCount}", Icons.Default.Build, Color(0xFFFF9800),
            Modifier.weight(1f))
        MetricCard("窗口", fmt(snapshot.maxTokens), Icons.Default.Fullscreen, Color(0xFF607D8B),
            Modifier.weight(1f))
    }

    if (toolStats.isNotEmpty()) {
        HorizontalDivider()
        Text("工具调用统计", fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        toolStats.forEach { (toolName, stat) ->
            ToolStatRow(toolName, stat.calls, stat.failed, stat.totalDurationMs)
        }
    }

    if (snapshot.isCompressed) {
        CompressedBanner(snapshot)
    }
}

// ========== L2: Token 详情层 ==========

@Composable
private fun TokenDetailLayer(snapshot: ContextSnapshot, breakdown: TokenBreakdown) {
    Text("Token 用量明细", fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.titleSmall)
    Text("${fmt(breakdown.totalTokens)} / ${fmt(snapshot.maxTokens)}  ·  ${snapshot.messageCount} 条消息",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)

    Spacer(Modifier.height(8.dp))

    val maxSeg = breakdown.segments.maxOfOrNull { it.tokens } ?: 1
    breakdown.segments.forEach { seg ->
        val segRatio = if (maxSeg > 0) seg.tokens.toFloat() / maxSeg else 0f
        TokenBar(seg.label, seg.tokens, segRatio, Color(seg.color), fmt(snapshot.maxTokens))
    }

    if (snapshot.isCompressed) {
        Spacer(Modifier.height(4.dp))
        HorizontalDivider()
        Text("压缩节省", fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.labelMedium)
        val savedRatio = if (snapshot.maxTokens > 0)
            snapshot.compressedTokens.toFloat() / snapshot.maxTokens else 0f
        TokenBar("已释放", snapshot.compressedTokens,
            if (snapshot.compressedTokens > 0) savedRatio * 3f else 0f,
            Color(0xFF9C27B0), "累计")
        Text("累计压缩 ${snapshot.compressedCount} 次",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline)
    }

    Spacer(Modifier.height(4.dp))
    HorizontalDivider()
    val remaining = (snapshot.maxTokens - breakdown.totalTokens).coerceAtLeast(0)
    val remRatio = if (snapshot.maxTokens > 0) remaining.toFloat() / snapshot.maxTokens else 0f
    TokenBar("剩余空间", remaining, remRatio, Color(0xFFE0E0E0), "可用")
}

// ========== 辅助组件 ==========

@Composable
private fun MetricCard(
    label: String, value: String, icon: ImageVector,
    color: Color, modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f)),
        shape = RoundedCornerShape(10.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(icon, null, Modifier.size(18.dp), tint = color)
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold, color = color)
            Text(label, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(color, shape = RoundedCornerShape(4.dp)))
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TokenBar(label: String, tokens: Int, ratio: Float, color: Color, maxLabel: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.width(68.dp), style = MaterialTheme.typography.bodySmall)
        Box(Modifier.weight(1f).height(12.dp).padding(end = 4.dp)) {
            Box(Modifier.fillMaxHeight().fillMaxWidth().background(
                Color(0xFFF5F5F5), RoundedCornerShape(6.dp)))
            Box(Modifier.fillMaxHeight().fillMaxWidth(ratio.coerceIn(0f, 1f)).background(
                color, RoundedCornerShape(6.dp)))
        }
        Text(fmt(tokens), Modifier.width(56.dp), style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold, color = color)
    }
}

@Composable
private fun CompressedBanner(snapshot: ContextSnapshot) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF9C27B0).copy(alpha = 0.08f)),
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Compress, null, Modifier.size(16.dp),
                tint = Color(0xFF9C27B0))
            Spacer(Modifier.width(8.dp))
            Column {
                Text("已释放 ${snapshot.compressedCount} 次",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold, color = Color(0xFF7B1FA2))
                Text("累计释放 ${fmt(snapshot.compressedTokens)} token",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF7B1FA2).copy(alpha = 0.7f))
            }
        }
    }
}

// ========== 工具调用统计行 ==========

@Composable
private fun ToolStatRow(name: String, calls: Int, failed: Int, totalDurationMs: Long) {
    val successColor = if (failed > 0) Color(0xFFE53935) else Color(0xFF4CAF50)
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Build, null, Modifier.size(12.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(4.dp))
        Text(name, Modifier.width(72.dp), style = MaterialTheme.typography.bodySmall,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.weight(1f))
        Text("×${calls}", style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
        if (failed > 0) {
            Spacer(Modifier.width(4.dp))
            Text("失败${failed}", style = MaterialTheme.typography.labelSmall,
                color = successColor)
        }
        Spacer(Modifier.width(4.dp))
        Text("${totalDurationMs / 1000}s", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline)
    }
}

// ========== 格式化 ==========

private fun fmt(n: Int): String = when {
    n >= 1_000_000 -> "${n / 1_000_000}M"
    n >= 1_000    -> "${n / 1_000}k"
    else           -> n.toString()
}
