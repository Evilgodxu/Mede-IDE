package com.template.jh.screens.home.components.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.template.jh.core.memory.*
import kotlin.math.abs

// ========== 分层仪表板 ==========

@Composable
fun ContextDashboard(
    snapshot: ContextSnapshot,
    breakdown: TokenBreakdown,
    architecture: MemoryArchitecture,
    keyFactCategories: KeyFactCategories,
    compressionHistory: List<CompressionRecord>,
    contextSummary: String,
    openedFilePaths: List<String>,
    onDismiss: () -> Unit,
) {
    var activeLayer by remember { mutableIntStateOf(0) } // 0=概览, 1=Token详情, 2=记忆详情, 3=时间线

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
                // === 顶部标签栏 ===
                TabBar(activeLayer) { activeLayer = it }

                // === 内容区 ===
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    when (activeLayer) {
                        0 -> OverviewLayer(snapshot, architecture, keyFactCategories, contextSummary)
                        1 -> TokenDetailLayer(snapshot, breakdown, compressionHistory)
                        2 -> MemoryDetailLayer(architecture, keyFactCategories)
                        3 -> TimelineLayer(compressionHistory, snapshot)
                    }

                    // 已打开文件（所有层底部可见）
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

                // === 底部操作 ===
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text("关闭") }
                }
            }
        }
    }
}

// ========== 标签栏 ==========

@Composable
private fun TabBar(active: Int, onSelect: (Int) -> Unit) {
    val tabs = listOf(
        "概览" to Icons.Default.Dashboard,
        "Token" to Icons.Default.DataUsage,
        "记忆" to Icons.Default.Memory,
        "时间线" to Icons.Default.Timeline,
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
private fun OverviewLayer(
    snapshot: ContextSnapshot,
    architecture: MemoryArchitecture,
    keyFactCategories: KeyFactCategories,
    contextSummary: String,
) {
    val ratio = snapshot.ratio
    val color = Color(HeatColors.ratioColor(ratio))

    // === 主图表：嵌套环形图 ===
    Box(
        modifier = Modifier.fillMaxWidth().height(140.dp),
        contentAlignment = Alignment.Center,
    ) {
        // 外环：用量
        Canvas(Modifier.size(120.dp)) {
            val sw = 12f
            val r = size.minDimension / 2f - sw / 2f
            val tl = Offset(sw / 2f, sw / 2f)
            val sz = Size(r * 2, r * 2)

            // 轨道
            drawArc(Color(0xFFE8E8E8), -90f, 360f, false,
                style = Stroke(sw, cap = StrokeCap.Butt), topLeft = tl, size = sz)

            // 用量段
            if (ratio > 0.005f) {
                drawArc(color, -90f, ratio * 360f, false,
                    style = Stroke(sw, cap = StrokeCap.Butt), topLeft = tl, size = sz)
            }

            // 压缩段（紫色内嵌）
            if (snapshot.isCompressed && snapshot.compressedTokens > 0) {
                val cr = (snapshot.compressedTokens.toFloat() / snapshot.maxTokens).coerceIn(0f, 1f)
                val ca = (cr * 360f).coerceAtMost(120f)
                val start = -90f + ratio * 360f + 3f
                drawArc(Color(0xFF9C27B0).copy(alpha = 0.7f), start, ca, false,
                    style = Stroke(sw * 0.8f, cap = StrokeCap.Butt), topLeft = tl, size = sz)
            }
        }

        // 内环：记忆占比
        if (architecture.totalTokens > 0) {
            Canvas(Modifier.size(80.dp)) {
                val sw = 10f
                val r = size.minDimension / 2f - sw / 2f
                val tl = Offset(sw / 2f, sw / 2f)
                val sz = Size(r * 2, r * 2)

                drawArc(Color(0xFFE8E8E8), -90f, 360f, false,
                    style = Stroke(sw, cap = StrokeCap.Butt), topLeft = tl, size = sz)

                val mr = (architecture.totalTokens.toFloat() / snapshot.maxTokens).coerceIn(0f, 1f)
                if (mr > 0.01f) {
                    drawArc(Color(0xFF1565C0).copy(alpha = 0.6f), -90f, mr * 360f, false,
                        style = Stroke(sw, cap = StrokeCap.Butt), topLeft = tl, size = sz)
                }
            }
        }

        // 中心文字
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${(ratio * 100).toInt()}%",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold, color = color)
            Text("${fmt(snapshot.usedTokens)}/${fmt(snapshot.maxTokens)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    // 图例
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        LegendDot(color, "当前用量")
        if (snapshot.isCompressed) LegendDot(Color(0xFF9C27B0), "已压缩 ${snapshot.compressedCount}次")
        if (architecture.totalTokens > 0) LegendDot(Color(0xFF1565C0), "记忆 ${architecture.totalTokens / 1000}k")
    }

    HorizontalDivider()

    // === 关键指标卡片 ===
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MetricCard("消息", "${snapshot.messageCount}", Icons.AutoMirrored.Filled.Chat, Color(0xFF4CAF50),
            Modifier.weight(1f))
        MetricCard("工具", "${snapshot.toolCallCount}", Icons.Default.Build, Color(0xFFFF9800),
            Modifier.weight(1f))
        MetricCard("事实", "${keyFactCategories.total}", Icons.Default.Lightbulb, Color(0xFFE91E63),
            Modifier.weight(1f))
        MetricCard("窗口", fmt(snapshot.maxTokens), Icons.Default.Fullscreen, Color(0xFF607D8B),
            Modifier.weight(1f))
    }

    // === 压缩状态 ===
    if (snapshot.isCompressed) {
        CompressedBanner(snapshot)
    }

    // === 关键事实摘要 ===
    if (keyFactCategories.total > 0) {
        KeyFactsMiniChart(keyFactCategories)
    }

    // === 上下文摘要 ===
    if (contextSummary.isNotBlank()) {
        ExpandableSection("上下文摘要", icon = Icons.Default.Summarize,
            accentColor = Color(0xFF7B1FA2)) {
            Text(contextSummary.take(300), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ========== L2: Token 详情层 ==========

@Composable
private fun TokenDetailLayer(
    snapshot: ContextSnapshot,
    breakdown: TokenBreakdown,
    compressionHistory: List<CompressionRecord>,
) {
    Text("Token 用量明细", fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.titleSmall)
    Text("${fmt(breakdown.totalTokens)} / ${fmt(snapshot.maxTokens)}  ·  ${snapshot.messageCount} 条消息",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)

    Spacer(Modifier.height(8.dp))

    // === 水平堆叠条形图 ===
    val maxSeg = breakdown.segments.maxOfOrNull { it.tokens } ?: 1
    breakdown.segments.forEach { seg ->
        val segRatio = if (maxSeg > 0) seg.tokens.toFloat() / maxSeg else 0f
        TokenBar(seg.label, seg.tokens, segRatio, Color(seg.color), fmt(snapshot.maxTokens))
    }

    // === 压缩节省 ===
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
        Text("累计压缩 ${snapshot.compressedCount} 次，${compressionHistory.size} 条记录",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline)
    }

    // === 剩余空间 ===
    Spacer(Modifier.height(4.dp))
    HorizontalDivider()
    val remaining = (snapshot.maxTokens - breakdown.totalTokens).coerceAtLeast(0)
    val remRatio = if (snapshot.maxTokens > 0) remaining.toFloat() / snapshot.maxTokens else 0f
    TokenBar("剩余空间", remaining, remRatio, Color(0xFFE0E0E0), "可用")
}

@Composable
private fun TokenBar(label: String, tokens: Int, ratio: Float, color: Color, suffix: String = "") {
    Column(modifier = Modifier.padding(vertical = 2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface)
            Text("${fmt(tokens)} $suffix", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(ratio.coerceIn(0f, 1f))
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(color)
            )
        }
    }
}

// ========== L2: 记忆详情层 ==========

@Composable
private fun MemoryDetailLayer(
    architecture: MemoryArchitecture,
    keyFactCategories: KeyFactCategories,
) {
    // === 记忆架构图 ===
    Text("记忆系统架构", fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.titleSmall)

    Spacer(Modifier.height(8.dp))

    // Layer 1 卡片
    LayerCard(
        title = "Layer 1 · 关键事实",
        subtitle = "持久化存储，永不丢失",
        color = Color(0xFFE91E63),
        info = architecture.layer1,
        isActive = architecture.layer1.entryCount > 0,
    )

    Spacer(Modifier.height(6.dp))

    // 连线指示
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Icon(Icons.Default.ArrowDownward, null, Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.outline)
    }

    // Layer 2 卡片
    LayerCard(
        title = "Layer 2 · 短期记忆",
        subtitle = "最近 ${architecture.layer2.entryCount} 条 • 上限 ${architecture.layer2.maxEntries}",
        color = Color(0xFF2196F3),
        info = architecture.layer2,
        isActive = architecture.layer2.entryCount > 0,
    )

    Spacer(Modifier.height(12.dp))

    // === 关键事实分类饼图 ===
    if (keyFactCategories.total > 0) {
        HorizontalDivider()
        Text("关键事实分类", fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(4.dp))
        KeyFactsPieChart(keyFactCategories)
    }

    // === Layer 1 分类列表 ===
    if (architecture.layer1.categories.isNotEmpty()) {
        Spacer(Modifier.height(4.dp))
        architecture.layer1.categories.forEach { cat ->
            CategoryRow(cat.label, cat.count)
        }
    }

    // === 记忆 token 估算 ===
    Spacer(Modifier.height(8.dp))
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("记忆系统 token 估算",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("~${architecture.totalTokens / 1000}k",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1565C0))
        }
    }
}

@Composable
private fun LayerCard(
    title: String,
    subtitle: String,
    color: Color,
    info: MemoryArchitecture.LayerInfo,
    isActive: Boolean,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) color.copy(alpha = 0.08f)
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(12.dp),
        border = if (isActive) androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.3f)) else null,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).background(color, CircleShape))
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold, color = color)
            }
            Text(subtitle, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (isActive) {
                Spacer(Modifier.height(6.dp))
                // 进度条
                Box(
                    modifier = Modifier.fillMaxWidth().height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(color.copy(alpha = 0.15f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(info.ratio.coerceIn(0f, 1f))
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(color.copy(alpha = 0.6f))
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("${info.entryCount}/${info.maxEntries} 条",
                        style = MaterialTheme.typography.labelSmall,
                        color = color.copy(alpha = 0.8f))
                    Text("~${info.estimatedTokens / 1000}k token",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline)
                }
            } else {
                Text("暂无数据", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

// ========== L2: 时间线层 ==========

@Composable
private fun TimelineLayer(
    compressionHistory: List<CompressionRecord>,
    snapshot: ContextSnapshot,
) {
    Text("压缩时间线", fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.titleSmall)

    if (snapshot.isCompressed) {
        Spacer(Modifier.height(4.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF9C27B0).copy(alpha = 0.1f)),
            shape = RoundedCornerShape(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("压缩状态: 已激活", style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold, color = Color(0xFF7B1FA2))
                Text("${snapshot.compressedCount} 次压缩  ·  释放 ${fmt(snapshot.compressedTokens)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF7B1FA2))
            }
        }
    }

    Spacer(Modifier.height(8.dp))

    if (compressionHistory.isEmpty()) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
            shape = RoundedCornerShape(8.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.Default.CheckCircle, null, Modifier.size(32.dp),
                    tint = Color(0xFF4CAF50))
                Spacer(Modifier.height(8.dp))
                Text("暂无压缩记录", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("上下文用量低于阈值时无需压缩", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline)
            }
        }
    } else {
        // 时间线条目
        compressionHistory.takeLast(10).forEachIndexed { i, record ->
            TimelineEntry(i, record, i == compressionHistory.size - 1)
        }
    }
}

@Composable
private fun TimelineEntry(index: Int, record: CompressionRecord, isLast: Boolean) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        // 时间线指示器
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(24.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(
                        if (record.triggerType == CompressionRecord.TriggerType.ContextError)
                            Color(0xFFE53935) else Color(0xFF9C27B0),
                        CircleShape,
                    )
            )
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(40.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
            }
        }

        Spacer(Modifier.width(8.dp))

        // 内容
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (record.triggerType == CompressionRecord.TriggerType.ContextError)
                    Color(0xFFE53935).copy(alpha = 0.06f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            ),
            shape = RoundedCornerShape(8.dp),
        ) {
            Column(Modifier.padding(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        "${record.triggerType.display()} · #${index + 1}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (record.triggerType == CompressionRecord.TriggerType.ContextError)
                            Color(0xFFC62828) else Color(0xFF7B1FA2),
                    )
                    Text(
                        formatTimeAgo(record.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
                val removedK = if (record.removedTokens < 1000) "<1k"
                else "${record.removedTokens / 1000}k"
                Text("释放 $removedK token，保留 ${record.keptExchanges} 轮对话",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (record.summaryGenerated) {
                    Text("✓ 已生成摘要", style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF4CAF50))
                }
                if (record.keyFactsPreserved > 0) {
                    Text("🔑 保留 ${record.keyFactsPreserved} 条关键事实",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFE91E63))
                }
            }
        }
    }
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
private fun LegendDot(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Spacer(Modifier.width(4.dp))
        Text(text, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                Text("已压缩 ${snapshot.compressedCount} 次",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold, color = Color(0xFF7B1FA2))
                Text("累计释放 ${fmt(snapshot.compressedTokens)} token",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF7B1FA2).copy(alpha = 0.7f))
            }
        }
    }
}

@Composable
private fun KeyFactsMiniChart(categories: KeyFactCategories) {
    ExpandableSection("关键事实 (${categories.total})", icon = Icons.Default.Lightbulb,
        accentColor = Color(0xFFE91E63)) {
        categories.toList().filter { it.count > 0 }.forEach { cat ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).background(Color(cat.color), CircleShape))
                    Spacer(Modifier.width(6.dp))
                    Text(cat.label, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface)
                }
                Text("${cat.count}", style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold, color = Color(cat.color))
            }
        }
    }
}

@Composable
private fun KeyFactsPieChart(categories: KeyFactCategories) {
    val total = categories.total.toFloat()
    if (total == 0f) return

    Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(80.dp)) {
            val data = categories.toList().filter { it.count > 0 }
            var startAngle = -90f
            data.forEach { cat ->
                val sweep = (cat.count / total) * 360f
                if (sweep > 0.5f) {
                    drawArc(Color(cat.color), startAngle, sweep, true,
                        style = Stroke(8f, cap = StrokeCap.Butt),
                        topLeft = Offset(4f, 4f),
                        size = Size(size.width - 8f, size.height - 8f))
                }
                startAngle += sweep
            }
        }
    }

    // 图例
    categories.toList().filter { it.count > 0 }.forEach { cat ->
        val pct = if (total > 0) (cat.count / total * 100).toInt() else 0
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).background(Color(cat.color), CircleShape))
                Spacer(Modifier.width(6.dp))
                Text(cat.label, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface)
            }
            Text("${cat.count} ($pct%)", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CategoryRow(label: String, count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface)
        Text("$count 条", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ExpandableSection(
    title: String,
    icon: ImageVector = Icons.Default.Info,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    content: @Composable () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(containerColor = accentColor.copy(alpha = 0.05f)),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, null, Modifier.size(16.dp), tint = accentColor)
                    Spacer(Modifier.width(6.dp))
                    Text(title, style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold, color = accentColor)
                }
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    null, Modifier.size(16.dp), tint = accentColor,
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Box(Modifier.padding(start = 10.dp, end = 10.dp, bottom = 10.dp)) {
                    content()
                }
            }
        }
    }
}

// ========== 工具函数 ==========

private fun CompressionRecord.TriggerType.display(): String = when (this) {
    CompressionRecord.TriggerType.AutoThreshold -> "自动阈值"
    CompressionRecord.TriggerType.ContextError -> "上下文超限"
    CompressionRecord.TriggerType.Manual -> "手动压缩"
    CompressionRecord.TriggerType.Periodic -> "定期压缩"
}

private fun fmt(tokens: Int): String = when {
    tokens >= 1_000_000 -> "${"%.1f".format(tokens / 1_000_000f)}M"
    tokens >= 1000 -> "${tokens / 1000}k"
    else -> "$tokens"
}

private fun formatTimeAgo(ts: Long): String {
    val diff = System.currentTimeMillis() - ts
    return when {
        diff < 60_000 -> "刚刚"
        diff < 3600_000 -> "${diff / 60_000}分钟前"
        diff < 86_400_000 -> "${diff / 3_600_000}小时前"
        else -> {
            val cal = java.util.Calendar.getInstance().apply { timeInMillis = ts }
            String.format("%tH:%tM", cal, cal)
        }
    }
}
