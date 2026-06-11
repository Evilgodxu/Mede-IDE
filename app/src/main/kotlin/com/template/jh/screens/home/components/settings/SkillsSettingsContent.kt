package com.template.jh.screens.home.components.settings

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.template.jh.model.SkillItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// 技能设置内容
@Composable
fun SkillsSettingsContent(
    skills: List<SkillItem>,
    onSetSkills: (List<SkillItem>) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var importError by remember { mutableStateOf<String?>(null) }

    // 文件选择器启动器（支持 ZIP 和单个技能文件）
    val filePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            scope.launch {
                try {
                    val importedSkill = when {
                        isZipFile(context, uri) -> importSkillFromZip(context, uri)
                        else -> importSkillFromFile(context, uri)
                    }
                    importedSkill?.let { skill ->
                        onSetSkills(skills + skill)
                    } ?: run {
                        importError = "导入失败：无法解析技能文件"
                    }
                } catch (e: Exception) {
                    importError = "导入失败：${e.message}"
                }
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (skills.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
            ) {
                Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("暂无技能", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("点击下方按钮添加自定义技能", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            val enabledCount = skills.count { it.enabled }
            if (enabledCount > 0) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("已启用 $enabledCount 项技能", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }
        }

        OutlinedButton(
            onClick = { filePickerLauncher.launch("*/*") },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.Add, null, Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("导入技能")
        }

        // 导入错误提示
        importError?.let { error ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Error,
                        null,
                        Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = { importError = null },
                        modifier = Modifier.size(20.dp),
                    ) {
                        Icon(Icons.Default.Close, null, Modifier.size(14.dp))
                    }
                }
            }
        }

        skills.forEach { skill ->
            SkillCard(
                skill = skill,
                onToggleEnabled = { checked ->
                    onSetSkills(skills.map { if (it.id == skill.id) it.copy(enabled = checked) else it })
                },
                onDelete = { onSetSkills(skills.filter { it.id != skill.id }) },
            )
        }
    }
}

// 技能卡片组件（支持展开详情）
@Composable
private fun SkillCard(
    skill: SkillItem,
    onToggleEnabled: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
    ) {
        Column {
            // 头部信息（始终显示）
            Row(
                modifier = Modifier
                    .padding(12.dp)
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(skill.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        if (skill.version.isNotBlank()) {
                            Text(
                                "v${skill.version}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.padding(start = 6.dp),
                            )
                        }
                    }
                    if (skill.description.isNotBlank()) {
                        Text(
                            skill.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (skill.prompt.isNotBlank() && !expanded) {
                        Text(
                            skill.prompt.take(60) + if (skill.prompt.length > 60) "…" else "",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        )
                    }
                    // 标签
                    if (skill.tags.isNotEmpty()) {
                        Row(
                            modifier = Modifier.padding(top = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            skill.tags.take(3).forEach { tag ->
                                Text(
                                    tag,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .background(
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                            RoundedCornerShape(4.dp),
                                        )
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                            if (skill.tags.size > 3) {
                                Text(
                                    "+${skill.tags.size - 3}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ArrowDropDown else Icons.Default.ArrowDropDown,
                    contentDescription = if (expanded) "收起" else "展开",
                    modifier = Modifier
                        .size(24.dp)
                        .rotate(if (expanded) 180f else 0f),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // 展开详情
            if (expanded) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                )

                Column(
                    modifier = Modifier
                        .padding(12.dp)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // 作者信息
                    if (skill.author.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.SmartToy,
                                null,
                                Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "作者: ${skill.author}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    // 使用方法
                    if (skill.usage.isNotBlank()) {
                        Column {
                            Text(
                                "使用方法",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                skill.usage,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    // 参数配置
                    if (skill.parameters.isNotEmpty()) {
                        Column {
                            Text(
                                "参数配置",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.height(4.dp))
                            skill.parameters.forEach { param ->
                                Row(
                                    modifier = Modifier.padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.Top,
                                ) {
                                    Text(
                                        param.name,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.width(100.dp),
                                    )
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            param.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Row {
                                            Text(
                                                "类型: ${param.type}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                            )
                                            if (param.required) {
                                                Text(
                                                    " • 必填",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.error,
                                                )
                                            }
                                            param.defaultValue?.let {
                                                Text(
                                                    " • 默认: $it",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                )
                                            }
                                        }
                                        if (param.options.isNotEmpty()) {
                                            Text(
                                                "选项: ${param.options.joinToString(", ")}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 使用示例
                    if (skill.examples.isNotEmpty()) {
                        Column {
                            Text(
                                "使用示例",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.height(4.dp))
                            skill.examples.forEachIndexed { index, example ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                                    ),
                                ) {
                                    Text(
                                        example,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(8.dp),
                                    )
                                }
                                if (index < skill.examples.size - 1) {
                                    Spacer(Modifier.height(4.dp))
                                }
                            }
                        }
                    }

                    // 完整文档
                    if (skill.documentation.isNotBlank()) {
                        Column {
                            Text(
                                "详细说明",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                skill.documentation,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    // 提示词预览
                    if (skill.prompt.isNotBlank()) {
                        Column {
                            Text(
                                "提示词",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.height(4.dp))
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                                ),
                            ) {
                                Text(
                                    skill.prompt,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 12.sp,
                                    ),
                                    modifier = Modifier.padding(8.dp),
                                )
                            }
                        }
                    }

                    // 操作按钮
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (skill.enabled) "已启用" else "已禁用",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (skill.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(8.dp))
                        Switch(
                            checked = skill.enabled,
                            onCheckedChange = onToggleEnabled,
                        )
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.size(28.dp),
                        ) {
                            Icon(Icons.Default.Delete, "删除", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            } else {
                // 未展开时显示开关和删除按钮
                Row(
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Switch(
                        checked = skill.enabled,
                        onCheckedChange = onToggleEnabled,
                    )
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(Icons.Default.Delete, "删除", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

// 判断文件是否为 ZIP 格式
private fun isZipFile(context: Context, uri: Uri): Boolean {
    return try {
        context.contentResolver.getType(uri)?.let { mimeType ->
            mimeType == "application/zip" || mimeType == "application/x-zip-compressed"
        } ?: uri.lastPathSegment?.endsWith(".zip", ignoreCase = true) ?: false
    } catch (e: Exception) {
        false
    }
}

// 从单个文件导入技能（JSON 或 Markdown）
private suspend fun importSkillFromFile(context: Context, uri: Uri): SkillItem? {
    return withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val content = input.bufferedReader().use { it.readText() }
                val fileName = uri.lastPathSegment ?: ""

                when {
                    fileName.endsWith(".json", ignoreCase = true) -> {
                        // JSON 格式技能文件
                        val json = org.json.JSONObject(content)
                        SkillItem(
                            name = json.optString("name", fileName.removeSuffix(".json")),
                            description = json.optString("description", ""),
                            prompt = json.optString("prompt", ""),
                            enabled = true,
                        )
                    }
                    fileName.endsWith(".md", ignoreCase = true) ||
                    fileName.endsWith(".txt", ignoreCase = true) -> {
                        // Markdown/纯文本格式，文件名作为技能名，内容作为 prompt
                        val name = fileName.substringBeforeLast(".").replace("_", " ").replace("-", " ")
                        SkillItem(
                            name = name,
                            description = "",
                            prompt = content,
                            enabled = true,
                        )
                    }
                    else -> null
                }
            }
        } catch (e: Exception) {
            Log.e("SkillsSettings", "导入技能文件失败", e)
            null
        }
    }
}

// 从 ZIP 文件导入技能（支持多级目录结构）
private suspend fun importSkillFromZip(context: Context, uri: Uri): SkillItem? {
    return withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                java.util.zip.ZipInputStream(input).use { zip ->
                    // 收集所有文件，支持多级目录
                    val files = mutableMapOf<String, String>()
                    var entry = zip.nextEntry

                    while (entry != null) {
                        if (!entry.isDirectory) {
                            // 使用小写文件名作为 key，支持大小写不敏感匹配
                            val key = entry.name.lowercase().replace("\\", "/")
                            val content = zip.bufferedReader().use { it.readText() }
                            files[key] = content
                        }
                        entry = zip.nextEntry
                    }

                    // 查找 skill.json（支持任意层级目录）
                    val skillJsonEntry = files.entries.find { it.key.endsWith("skill.json") }
                    val skillJson = skillJsonEntry?.value

                    if (skillJson != null) {
                        val json = org.json.JSONObject(skillJson)
                        val name = json.optString("name", "未命名技能")
                        val description = json.optString("description", "")
                        val author = json.optString("author", "")
                        val version = json.optString("version", "")

                        // 解析 tags 数组
                        val tags = json.optJSONArray("tags")?.let { arr ->
                            (0 until arr.length()).map { arr.getString(it) }
                        } ?: emptyList()

                        // 解析 parameters 数组
                        val parameters = json.optJSONArray("parameters")?.let { arr ->
                            (0 until arr.length()).map { i ->
                                val param = arr.getJSONObject(i)
                                com.template.jh.model.SkillParameter(
                                    name = param.optString("name", ""),
                                    description = param.optString("description", ""),
                                    type = param.optString("type", "string"),
                                    required = param.optBoolean("required", false),
                                    defaultValue = param.optString("defaultValue", "").takeIf { it.isNotBlank() },
                                    options = param.optJSONArray("options")?.let { optArr ->
                                        (0 until optArr.length()).map { optArr.getString(it) }
                                    } ?: emptyList()
                                )
                            }
                        } ?: emptyList()

                        // 解析 examples 数组
                        val examples = json.optJSONArray("examples")?.let { arr ->
                            (0 until arr.length()).map { arr.getString(it) }
                        } ?: emptyList()

                        // 优先从 skill.json 获取 prompt
                        var prompt = json.optString("prompt", "")

                        // 如果 skill.json 中没有 prompt，查找 prompt.md 或 prompt.txt
                        if (prompt.isBlank()) {
                            val promptEntry = files.entries.find {
                                it.key.endsWith("prompt.md") || it.key.endsWith("prompt.txt")
                            }
                            prompt = promptEntry?.value ?: ""
                        }

                        // 查找 docs 目录下的文档说明
                        val docsContent = files.entries
                            .filter { it.key.contains("/docs/") || it.key.startsWith("docs/") }
                            .filter { it.key.endsWith(".md") || it.key.endsWith(".txt") }
                            .map { it.value }
                            .joinToString("\n\n")

                        // 查找 usage 说明
                        val usageEntry = files.entries.find {
                            it.key.endsWith("usage.md") || it.key.endsWith("usage.txt")
                        }
                        val usage = usageEntry?.value ?: json.optString("usage", "")

                        val finalDescription = if (docsContent.isNotBlank() && description.isBlank()) {
                            docsContent.take(200) + if (docsContent.length > 200) "…" else ""
                        } else {
                            description
                        }

                        if (prompt.isNotBlank()) {
                            SkillItem(
                                name = name,
                                description = finalDescription,
                                prompt = prompt,
                                enabled = true,
                                documentation = docsContent,
                                usage = usage,
                                parameters = parameters,
                                examples = examples,
                                author = author,
                                version = version,
                                tags = tags,
                            )
                        } else null
                    } else null
                }
            }
        } catch (e: Exception) {
            Log.e("SkillsSettings", "导入技能 ZIP 失败", e)
            null
        }
    }
}
