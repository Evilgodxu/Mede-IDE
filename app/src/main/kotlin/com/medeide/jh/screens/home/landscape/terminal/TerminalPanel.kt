package com.medeide.jh.screens.home.landscape.terminal

import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.*
import java.util.concurrent.Executors

private const val TAG = "TerminalPanel"
private val TERMUX_PACKAGES = arrayOf(
    "com.termux",
    "com.termux.stable",
    "com.termux.beta",
    "com.termux.debug"
)

private fun checkTermuxInstalled(context: Context): Boolean {
    val pm = context.packageManager
    for (packageName in TERMUX_PACKAGES) {
        try {
            pm.getPackageInfo(packageName, 0)
            Log.d(TAG, "检测到 Termux: $packageName")
            return true
        } catch (_: PackageManager.NameNotFoundException) {
        }
    }
    Log.d(TAG, "未检测到 Termux")
    return false
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalPanel(
    currentPath: String,
    onNavigateToFile: (String) -> Unit,
    onClosePanel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 检测 Termux 是否安装
    var isTermuxInstalled by remember {
        mutableStateOf(checkTermuxInstalled(context))
    }

    // 刷新 Termux 检测状态
    fun refreshTermuxStatus() {
        isTermuxInstalled = checkTermuxInstalled(context)
    }

    // 监听应用安装/卸载广播
    val packageReceiver = remember {
        object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_PACKAGE_ADDED ||
                    intent?.action == Intent.ACTION_PACKAGE_REMOVED ||
                    intent?.action == Intent.ACTION_PACKAGE_CHANGED) {
                    val packageName = intent.data?.schemeSpecificPart
                    if (packageName != null && TERMUX_PACKAGES.contains(packageName)) {
                        refreshTermuxStatus()
                    }
                }
            }
        }
    }

    // 注册广播接收器
    DisposableEffect(context) {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        context.registerReceiver(packageReceiver, filter)
        onDispose {
            context.unregisterReceiver(packageReceiver)
        }
    }

    // 将开发工具脚本复制到外部存储
    fun copyToolkitToStorage(context: Context): String {
        val outputDir = File(context.getExternalFilesDir(null), "tools")
        outputDir.mkdirs()
        val outputFile = File(outputDir, "android_dev_toolkit.py")

        if (!outputFile.exists()) {
            context.assets.open("android_dev_toolkit.py").use { inputStream ->
                outputFile.outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
        }

        return outputFile.absolutePath
    }

    // 终端会话列表
    var sessions by remember { mutableStateOf(listOf<TerminalSession>()) }
    var currentSessionIndex by remember { mutableIntStateOf(0) }
    var commandInput by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    val currentSession = sessions.getOrNull(currentSessionIndex)

    // 启动开发工具脚本
    fun launchDevToolkit() {
        if (sessions.isEmpty()) {
            val newSession = TerminalSession(
                id = System.currentTimeMillis().toString(),
                title = "会话 ${sessions.size + 1}",
                createdAt = System.currentTimeMillis()
            )
            sessions = sessions + newSession
            currentSessionIndex = sessions.lastIndex
        }

        sessions = sessions.mapIndexed { index, session ->
            if (index == currentSessionIndex) {
                session.copy(output = session.output + "\n[开发工具] 正在启动 Android 开发工具箱...\n[注意] 此脚本需要 Python 3.8+\n")
            } else session
        }

        Thread {
            try {
                val toolkitPath = copyToolkitToStorage(context)
                android.os.Handler(Looper.getMainLooper()).post {
                    executeInTermux(
                        context = context,
                        command = "python3 \"$toolkitPath\"",
                        onResult = { output ->
                            sessions = sessions.mapIndexed { index, session ->
                                if (index == currentSessionIndex) {
                                    session.copy(output = session.output + output + "\n")
                                } else session
                            }
                        },
                        onError = { error ->
                            sessions = sessions.mapIndexed { index, session ->
                                if (index == currentSessionIndex) {
                                    session.copy(output = session.output + "[错误] $error\n[提示] 请先在 Termux 中执行: pkg install python\n")
                                } else session
                            }
                        }
                    )
                }
            } catch (e: Exception) {
                android.os.Handler(Looper.getMainLooper()).post {
                    sessions = sessions.mapIndexed { index, session ->
                        if (index == currentSessionIndex) {
                            session.copy(output = session.output + "[错误] 无法启动开发工具: ${e.message}\n")
                        } else session
                    }
                }
            }
        }.start()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // 顶部栏
        TopAppBar(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("终端")
                }
            },
            navigationIcon = {
                IconButton(onClick = onClosePanel) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "关闭")
                }
            },
            actions = {
                // Termux 状态指示
                if (isTermuxInstalled) {
                    Row {
                        AssistChip(
                            onClick = { },
                            label = { Text("Termux", fontSize = 12.sp) },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = Color(0xFF4CAF50)
                                )
                            }
                        )
                        IconButton(
                            onClick = { launchDevToolkit() }
                        ) {
                            Icon(Icons.Default.Build, contentDescription = "开发工具", modifier = Modifier.size(20.dp))
                        }
                    }
                } else {
                    Row {
                        AssistChip(
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    data = Uri.parse("https://f-droid.org/packages/com.termux/")
                                }
                                context.startActivity(intent)
                                Handler(Looper.getMainLooper()).postDelayed({
                                    refreshTermuxStatus()
                                }, 5000)
                            },
                            label = { Text("安装 Termux", fontSize = 12.sp) },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Warning,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = Color(0xFFFF9800)
                                )
                            }
                        )
                        IconButton(onClick = { refreshTermuxStatus() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "刷新", modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        )

        // 帮助信息
        if (!isTermuxInstalled) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFFFF3E0)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "[警告] 需要安装 Termux",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFE65100)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "为了提供真正的交互式终端体验（支持 vim、top、ssh、python3 等），" +
                        "本 App 使用 Termux 作为后端终端。\n\n" +
                        "请按以下步骤安装：\n" +
                        "1. 安装 Termux: https://f-droid.org/packages/com.termux/\n" +
                        "2. 安装 Termux:API（推荐）: https://f-droid.org/packages/com.termux.api/\n" +
                        "3. 打开 Termux 执行: pkg install python\n" +
                        "4. 返回本应用，终端即可正常使用。",
                        fontSize = 14.sp
                    )
                }
            }
        }

        // 欢迎信息（仅在没有任何会话时显示）
        if (sessions.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Mede IDE 终端",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        if (isTermuxInstalled)
                            "点击下方按钮启动新终端会话"
                        else
                            "安装 Termux 后即可使用",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(24.dp))

                    if (isTermuxInstalled) {
                        Button(
                            onClick = {
                                isLoading = true
                                val newSession = TerminalSession(
                                    id = System.currentTimeMillis().toString(),
                                    title = "会话 ${sessions.size + 1}",
                                    createdAt = System.currentTimeMillis()
                                )
                                sessions = sessions + newSession
                                currentSessionIndex = sessions.lastIndex

                                // 使用 Termux 执行欢迎命令
                                executeInTermux(
                                    context = context,
                                    command = "echo '欢迎使用 Mede IDE 终端！当前路径: $currentPath'; echo ''; echo '---'; echo '输入 help 查看可用命令'",
                                    onResult = { output ->
                                        isLoading = false
                                        // 更新会话输出
                                        sessions = sessions.mapIndexed { index, session ->
                                            if (index == sessions.lastIndex) {
                                                session.copy(output = output)
                                            } else session
                                        }
                                    },
                                    onError = { error ->
                                        isLoading = false
                                        sessions = sessions.mapIndexed { index, session ->
                                            if (index == sessions.lastIndex) {
                                                session.copy(output = "错误: $error")
                                            } else session
                                        }
                                    }
                                )
                            },
                            enabled = !isLoading
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Default.Add, contentDescription = null)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("启动新会话")
                        }
                    }
                }
            }
        } else {
            // 会话标签栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .horizontalScroll(rememberScrollState())
                    .padding(4.dp)
            ) {
                sessions.forEachIndexed { index, session ->
                    val isSelected = index == currentSessionIndex
                    Surface(
                        modifier = Modifier
                            .padding(horizontal = 2.dp)
                            .clickable { currentSessionIndex = index },
                        shape = RoundedCornerShape(4.dp),
                        color = if (isSelected)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            Color.Transparent
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                session.title,
                                fontSize = 12.sp,
                                color = if (isSelected)
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (sessions.size > 1) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "关闭会话",
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clickable {
                                            sessions = sessions.toMutableList().apply {
                                                removeAt(index)
                                            }
                                            if (currentSessionIndex >= sessions.size) {
                                                currentSessionIndex = sessions.size - 1
                                            }
                                        },
                                    tint = if (isSelected)
                                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                    else
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }

                // 添加新会话按钮
                if (isTermuxInstalled) {
                    IconButton(
                        onClick = {
                            val newSession = TerminalSession(
                                id = System.currentTimeMillis().toString(),
                                title = "会话 ${sessions.size + 1}",
                                createdAt = System.currentTimeMillis()
                            )
                            sessions = sessions + newSession
                            currentSessionIndex = sessions.lastIndex
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "新建会话",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // 终端输出区域
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color(0xFF1E1E1E))
            ) {
                val listState = rememberLazyListState()

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                ) {
                    // 显示当前会话的输出
                    currentSession?.let { session ->
                        if (session.output.isNotEmpty()) {
                            itemsIndexed(session.output.lines()) { _, line ->
                                Text(
                                    text = line,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp,
                                    color = Color(0xFFE0E0E0),
                                    lineHeight = 18.sp
                                )
                            }
                        } else {
                            item {
                                Column(
                                    modifier = Modifier.padding(8.dp)
                                ) {
                                    Text(
                                        text = "会话已就绪",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 13.sp,
                                        color = Color(0xFF4CAF50)
                                    )
                                    Text(
                                        text = "在下方输入命令并发送",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 13.sp,
                                        color = Color(0xFF9E9E9E)
                                    )
                                }
                            }
                        }
                    }
                }

                // 自动滚动到底部
                LaunchedEffect(currentSession?.output) {
                    delay(100)
                    if (sessions.isNotEmpty()) {
                        try {
                            val lineCount = sessions.getOrNull(currentSessionIndex)?.output?.lines()?.size ?: 0
                            if (lineCount > 0) {
                                listState.animateScrollToItem(lineCount - 1)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "滚动失败: ${e.message}")
                        }
                    }
                }
            }

            // 状态消息
            statusMessage?.let { message ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFFFEBEE)
                    )
                ) {
                    Text(
                        text = message,
                        modifier = Modifier.padding(8.dp),
                        color = Color(0xFFC62828),
                        fontSize = 12.sp
                    )
                }
            }

            // 输入区域
            if (isTermuxInstalled) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 当前路径显示
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    ) {
                        Text(
                            text = currentPath.takeLast(20),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // 命令输入框
                    BasicTextField(
                        value = commandInput,
                        onValueChange = { commandInput = it },
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                MaterialTheme.colorScheme.surface,
                                RoundedCornerShape(4.dp)
                            )
                            .padding(8.dp),
                        textStyle = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        singleLine = true,
                        decorationBox = { innerTextField ->
                            Box {
                                if (commandInput.isEmpty()) {
                                    Text(
                                        "输入命令...",
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 14.sp
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    // 发送按钮
                    IconButton(
                        onClick = {
                            if (commandInput.isNotBlank() && currentSession != null) {
                                val command = commandInput.trim()
                                commandInput = ""

                                // 显示输入的命令
                                sessions = sessions.mapIndexed { index, session ->
                                    if (index == currentSessionIndex) {
                                        session.copy(output = session.output + "\n$ $command\n")
                                    } else session
                                }

                                // 使用 Termux 执行命令
                                executeInTermux(
                                    context = context,
                                    command = command,
                                    onResult = { output ->
                                        sessions = sessions.mapIndexed { index, session ->
                                            if (index == currentSessionIndex) {
                                                session.copy(output = session.output + output + "\n")
                                            } else session
                                        }
                                    },
                                    onError = { error ->
                                        sessions = sessions.mapIndexed { index, session ->
                                            if (index == currentSessionIndex) {
                                                session.copy(output = session.output + "错误: $error\n")
                                            } else session
                                        }
                                    }
                                )
                            }
                        },
                        enabled = commandInput.isNotBlank() && currentSession != null
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "发送",
                            tint = if (commandInput.isNotBlank())
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }
            } else {
                // 没有 Termux 时的提示
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                "需要 Termux",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                "安装 Termux 后才能使用终端功能",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 通过 Termux API 执行命令
 * 优先使用 Termux:API 的 RunCommandService
 */
private fun executeInTermux(
    context: Context,
    command: String,
    onResult: (String) -> Unit,
    onError: (String) -> Unit
) {
    val cleanCommand = command.trim()
    Log.d(TAG, "执行命令: $cleanCommand")

    if (isTermuxApiInstalled(context)) {
        executeViaTermuxApi(context, cleanCommand, onResult, onError)
    } else {
        Log.d(TAG, "Termux:API 未安装，尝试 Termux 直接执行")
        executeViaTermuxDirect(context, cleanCommand, onResult, onError)
    }
}

/**
 * 检查 Termux:API 是否安装
 */
private fun isTermuxApiInstalled(context: Context): Boolean {
    return try {
        context.packageManager.getPackageInfo("com.termux.api", 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }
}

/**
 * 通过 Termux:API 的 RunCommandService 执行命令
 */
private fun executeViaTermuxApi(
    context: Context,
    command: String,
    onResult: (String) -> Unit,
    onError: (String) -> Unit
) {
    Thread {
        try {
            val resultReceiver = object : android.os.ResultReceiver(android.os.Handler(android.os.Looper.getMainLooper())) {
                override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                    if (resultCode == 0) {
                        val stdout = resultData?.getString("com.termux.api.RunCommandService.stdout") ?: ""
                        val stderr = resultData?.getString("com.termux.api.RunCommandService.stderr") ?: ""
                        val output = if (stderr.isNotEmpty()) "$stdout\n[Stderr]\n$stderr" else stdout
                        onResult(output)
                    } else {
                        onError("Termux:API 执行失败，错误码: $resultCode")
                    }
                }
            }
            val intent = Intent().apply {
                setClassName("com.termux.api", "com.termux.api.RunCommandService")
                putExtra("com.termux.api.RunCommandService.command", command)
                putExtra("com.termux.api.RunCommandService.background", false)
                putExtra("com.termux.api.RunCommandService.ezout", true)
                putExtra("com.termux.api.RunCommandService.label", "Mede IDE")
                putExtra("com.termux.api.RunCommandService.sessionAction", "0")
                putExtra("com.termux.api.RunCommandService.resultReceiver", resultReceiver)
            }
            context.startService(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Termux:API 执行失败: ${e.message}")
            executeViaTermuxDirect(context, command, onResult, onError)
        }
    }.start()
}

/**
 * 通过 Termux 应用的 RunCommandService 执行命令
 */
private fun executeViaTermuxDirect(
    context: Context,
    command: String,
    onResult: (String) -> Unit,
    onError: (String) -> Unit
) {
    Thread {
        try {
            val resultReceiver = object : android.os.ResultReceiver(android.os.Handler(android.os.Looper.getMainLooper())) {
                override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                    if (resultCode == 0) {
                        val stdout = resultData?.getString("com.termux.app.RunCommandService.stdout") ?: ""
                        val stderr = resultData?.getString("com.termux.app.RunCommandService.stderr") ?: ""
                        val output = if (stderr.isNotEmpty()) "$stdout\n[Stderr]\n$stderr" else stdout
                        onResult(output)
                    } else {
                        executeWithDirectProcess(command, onResult, onError)
                    }
                }
            }
            val intent = Intent().apply {
                setClassName("com.termux", "com.termux.app.RunCommandService")
                putExtra("com.termux.app.RunCommandService.command", command)
                putExtra("com.termux.app.RunCommandService.background", false)
                putExtra("com.termux.app.RunCommandService.ezout", true)
                putExtra("com.termux.app.RunCommandService.label", "Mede IDE")
                putExtra("com.termux.app.RunCommandService.sessionAction", "0")
                putExtra("com.termux.app.RunCommandService.resultReceiver", resultReceiver)
            }
            context.startService(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Termux 直接执行失败: ${e.message}")
            executeWithDirectProcess(command, onResult, onError)
        }
    }.start()
}

/**
 * 尝试使用 Termux API 执行命令
 */
private fun tryExecuteTermuxApi(
    context: Context,
    command: String,
    onResult: (String) -> Unit,
    onError: (String) -> Unit
) {
    // 委托给新的执行函数
    executeInTermux(context, command, onResult, onError)
}

/**
 * 使用 Runtime.exec 执行命令（仅在无 Termux 时使用 Android 系统 shell）
 */
private fun executeWithRuntime(
    context: Context,
    command: String,
    onResult: (String) -> Unit,
    onError: (String) -> Unit
) {
    Thread {
        try {
            val runtime = Runtime.getRuntime()
            val shells = arrayOf(
                arrayOf("sh", "-c", command),
                arrayOf("/system/bin/sh", "-c", command),
                arrayOf("/bin/sh", "-c", command)
            )

            var process: Process? = null
            for (shell in shells) {
                try {
                    process = runtime.exec(shell)
                    break
                } catch (e: Exception) {
                    Log.d(TAG, "Shell $shell 不可用: ${e.message}")
                }
            }

            if (process == null) {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    onError("无法找到可用的 shell。请安装 Termux 以获得完整终端功能。")
                }
                return@Thread
            }

            val reader = BufferedReader(InputStreamReader(process.inputStream, "UTF-8"))
            val errorReader = BufferedReader(InputStreamReader(process.errorStream, "UTF-8"))

            val output = StringBuilder()
            val errorOutput = StringBuilder()

            val outputThread = Thread {
                try {
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        if (line != null) {
                            output.append(line).append("\n")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "读取输出失败: ${e.message}")
                }
            }

            val errorThread = Thread {
                try {
                    var line: String?
                    while (errorReader.readLine().also { line = it } != null) {
                        if (line != null) {
                            errorOutput.append(line).append("\n")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "读取错误输出失败: ${e.message}")
                }
            }

            outputThread.start()
            errorThread.start()

            val exitCode = process.waitFor()
            outputThread.join(5000)
            errorThread.join(5000)

            reader.close()
            errorReader.close()

            val result = if (errorOutput.isNotEmpty()) {
                output.toString() + "\n[Stderr]\n" + errorOutput.toString()
            } else {
                output.toString()
            }

            Log.d(TAG, "命令执行完成，退出码: $exitCode, 输出长度: ${result.length}")

            android.os.Handler(android.os.Looper.getMainLooper()).post {
                if (exitCode == 0 || result.isNotEmpty()) {
                    onResult(result)
                } else {
                    onError("命令执行失败，退出码: $exitCode")
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Runtime.exec 失败: ${e.message}")
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                onError(e.message ?: "执行失败")
            }
        }
    }.start()
}

/**
 * 使用直接 Process 执行（最简单的备用方案）
 */
private fun executeWithDirectProcess(
    command: String,
    onResult: (String) -> Unit,
    onError: (String) -> Unit
) {
    val executor = Executors.newSingleThreadExecutor()

    executor.submit {
        try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))

            // 设置超时 30 秒
            val future = executor.submit<String> {
                val output = process.inputStream.bufferedReader().readText()
                val error = process.errorStream.bufferedReader().readText()
                process.waitFor()
                if (error.isNotEmpty()) "$output\n[Error]\n$error" else output
            }

            try {
                val result = future.get(30, java.util.concurrent.TimeUnit.SECONDS) as String
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    onResult(result)
                }
            } catch (e: java.util.concurrent.TimeoutException) {
                process.destroy()
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    onError("命令执行超时（30秒）")
                }
            }

        } catch (e: Exception) {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                onError(e.message ?: "执行失败")
            }
        } finally {
            executor.shutdown()
        }
    }
}

data class TerminalSession(
    val id: String,
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val output: String = ""
)
