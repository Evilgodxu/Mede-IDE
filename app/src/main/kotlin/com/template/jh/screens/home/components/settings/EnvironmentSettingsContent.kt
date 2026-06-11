package com.template.jh.screens.home.components.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.template.jh.core.utils.TermuxShell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun EnvironmentSettingsContent() {
    val scope = rememberCoroutineScope()

    // Node.js 环境
    var isNodeInstalled by remember { mutableStateOf(false) }
    var isPnpmInstalled by remember { mutableStateOf(false) }
    var showNodeJsDetail by remember { mutableStateOf(false) }
    var isInstallingNodeJs by remember { mutableStateOf(false) }

    // Python 环境
    var isPythonInstalled by remember { mutableStateOf(false) }
    var isPipInstalled by remember { mutableStateOf(false) }
    var showPythonDetail by remember { mutableStateOf(false) }
    var isInstallingPython by remember { mutableStateOf(false) }

    // Java 环境
    var isOpenJdk17Installed by remember { mutableStateOf(false) }
    var isGradleInstalled by remember { mutableStateOf(false) }
    var showJavaDetail by remember { mutableStateOf(false) }
    var isInstallingJava by remember { mutableStateOf(false) }

    var isChecking by remember { mutableStateOf(false) }
    var installMessage by remember { mutableStateOf<String?>(null) }

    // Termux 可用性
    val termuxAvailable = TermuxShell.isAvailable

    fun checkEnvironment() {
        scope.launch(Dispatchers.IO) {
            isChecking = true
            try {
                isNodeInstalled = TermuxShell.hasCommand("node")
                isPnpmInstalled = TermuxShell.hasCommand("pnpm")
                isPythonInstalled = TermuxShell.hasCommand("python") || TermuxShell.hasCommand("python3")
                isPipInstalled = TermuxShell.hasCommand("pip") || TermuxShell.hasCommand("pip3")
                isOpenJdk17Installed = TermuxShell.hasJavaVersion(17)
                isGradleInstalled = TermuxShell.hasCommand("gradle")
            } finally {
                isChecking = false
            }
        }
    }

    // 执行安装命令
    fun runInstall(commands: List<String>, onDone: () -> Unit) {
        scope.launch(Dispatchers.IO) {
            installMessage = "安装中..."
            try {
                for (cmd in commands) {
                    val (exitCode, output) = TermuxShell.exec(cmd)
                    if (exitCode != 0) {
                        withContext(Dispatchers.Main) {
                            installMessage = "安装失败: $cmd\n$output"
                        }
                        return@launch
                    }
                }
                withContext(Dispatchers.Main) {
                    installMessage = null
                    onDone()
                    checkEnvironment()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    installMessage = "安装出错: ${e.message}"
                }
            }
        }
    }

    LaunchedEffect(Unit) { checkEnvironment() }

    val nodeJsReady = isNodeInstalled && isPnpmInstalled
    val pythonReady = isPythonInstalled && isPipInstalled
    val javaReady = isOpenJdk17Installed && isGradleInstalled

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Termux 未安装提示
        if (!termuxAvailable) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Warning,
                        null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            "未检测到 Termux",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            "请安装 Termux 以使用开发环境自动配置功能",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }

        // 安装消息提示
        val context = LocalContext.current
        installMessage?.let { msg ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = {},
                        onLongClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("安装日志", msg))
                            Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
                        }
                    ),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f))
            ) {
                Text(
                    msg,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }

        // Node.js 环境卡片
        EnvGroupCard(
            title = "Node.js 环境",
            subtitle = "Node.js 和前端开发环境",
            isAllReady = nodeJsReady,
            allReadyText = "Node.js 环境已就绪",
            notReadyText = if (isNodeInstalled || isPnpmInstalled) "部分组件已安装" else "需要配置 Node.js 环境",
            expanded = showNodeJsDetail,
            onToggle = { showNodeJsDetail = !showNodeJsDetail },
            isInstalling = isInstallingNodeJs,
            showInstall = !nodeJsReady,
            installEnabled = termuxAvailable,
            onInstall = {
                isInstallingNodeJs = true
                showNodeJsDetail = true
                val cmds = mutableListOf<String>()
                if (!isNodeInstalled) cmds.add("pkg install -y nodejs")
                if (!isPnpmInstalled) cmds.add("npm install -g pnpm")
                runInstall(cmds) { isInstallingNodeJs = false }
            }
        ) {
            EnvCheckItem("Node.js", "JavaScript 运行时", isNodeInstalled)
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            EnvCheckItem("PNPM", "快速的包管理器和 TypeScript", isPnpmInstalled)
        }

        // Python 环境卡片
        EnvGroupCard(
            title = "Python 环境",
            subtitle = "Python 开发环境",
            isAllReady = pythonReady,
            allReadyText = "Python 环境已就绪",
            notReadyText = if (isPythonInstalled || isPipInstalled) "部分组件已安装" else "需要配置 Python 环境",
            expanded = showPythonDetail,
            onToggle = { showPythonDetail = !showPythonDetail },
            isInstalling = isInstallingPython,
            showInstall = !pythonReady,
            installEnabled = termuxAvailable,
            onInstall = {
                isInstallingPython = true
                showPythonDetail = true
                val cmds = mutableListOf<String>()
                if (!isPythonInstalled) cmds.add("pkg install -y python")
                if (!isPipInstalled) cmds.add("pkg install -y python-pip")
                runInstall(cmds) { isInstallingPython = false }
            }
        ) {
            EnvCheckItem("Python", "Python 解释器", isPythonInstalled)
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            EnvCheckItem("Pip", "Python 包管理器", isPipInstalled)
        }

        // Java 环境卡片
        EnvGroupCard(
            title = "Java 环境",
            subtitle = "Java 开发环境",
            isAllReady = javaReady,
            allReadyText = "Java 环境已就绪",
            notReadyText = if (isOpenJdk17Installed || isGradleInstalled) "部分组件已安装" else "需要配置 Java 环境",
            expanded = showJavaDetail,
            onToggle = { showJavaDetail = !showJavaDetail },
            isInstalling = isInstallingJava,
            showInstall = !javaReady,
            installEnabled = termuxAvailable,
            onInstall = {
                isInstallingJava = true
                showJavaDetail = true
                val cmds = mutableListOf<String>()
                if (!isOpenJdk17Installed) cmds.add("pkg install -y openjdk-17")
                if (!isGradleInstalled) cmds.add("pkg install -y gradle")
                runInstall(cmds) { isInstallingJava = false }
            }
        ) {
            EnvCheckItem("OpenJDK 17", "Java 开发工具包", isOpenJdk17Installed)
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            EnvCheckItem("Gradle", "构建自动化工具", isGradleInstalled)
        }

        // 刷新按钮
        OutlinedButton(
            onClick = { checkEnvironment() },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isChecking
        ) {
            if (isChecking) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("检测中...")
            } else {
                Icon(Icons.Default.Refresh, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("刷新环境状态")
            }
        }
    }
}

/** 可展开的环境分组卡片 */
@Composable
private fun EnvGroupCard(
    title: String,
    subtitle: String,
    isAllReady: Boolean,
    allReadyText: String,
    notReadyText: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    isInstalling: Boolean = false,
    showInstall: Boolean = false,
    installEnabled: Boolean = true,
    onInstall: () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 标题栏
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(
                    onClick = onToggle,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(if (expanded) "收起" else "展开")
                }
            }

            Spacer(Modifier.height(8.dp))

            // 状态行
            val statusColor = when {
                isAllReady -> MaterialTheme.colorScheme.tertiary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isAllReady) Icons.Default.CheckCircle else Icons.Default.Info,
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (isAllReady) allReadyText else notReadyText,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    color = statusColor
                )
            }

            // 展开内容
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    Spacer(Modifier.height(8.dp))
                    content()

                    // 一键安装按钮
                    if (showInstall && !isAllReady) {
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = onInstall,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = installEnabled && !isInstalling,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            if (isInstalling) {
                                CircularProgressIndicator(
                                    Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("安装中...")
                            } else {
                                Icon(Icons.Default.Terminal, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("一键安装")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EnvCheckItem(name: String, description: String, isInstalled: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isInstalled) Icons.Default.CheckCircle else Icons.Default.Info,
            contentDescription = null,
            tint = if (isInstalled) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = if (isInstalled) MaterialTheme.colorScheme.tertiary
                    else MaterialTheme.colorScheme.onSurface
            )
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
