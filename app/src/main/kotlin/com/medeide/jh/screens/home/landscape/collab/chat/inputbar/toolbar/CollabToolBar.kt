package com.medeide.jh.screens.home.landscape.collab.chat.inputbar.toolbar

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medeide.jh.R
import com.medeide.jh.model.chat.EngineStatus

// 协作区消息输入框 - 功能工具栏
@Composable
fun CollabToolBar(
    inputText: String,
    onInputChange: (String) -> Unit,
    isLoading: Boolean,
    isOptimizing: Boolean,
    engineStatus: EngineStatus,
    onCancel: () -> Unit,
    onOptimize: () -> Unit,
    onSend: () -> Unit,
    onImagePick: () -> Unit,
    hasAttachments: Boolean,
    contextUsedTokens: Int = 0,
    contextMaxTokens: Int = 128000,
    isContextCompressed: Boolean = false,
    contextCompressedTokens: Int = 0,
    contextCompressedCount: Int = 0,
    memoryEntryCount: Int = 0,
    memoryTotalTokens: Int = 0,
    onContextInfoClick: () -> Unit = {},
    optimizeMode: com.medeide.jh.screens.home.landscape.collab.ai.InputOptimizer.Mode = com.medeide.jh.screens.home.landscape.collab.ai.InputOptimizer.Mode.CODE,
    onOptimizeModeChange: (com.medeide.jh.screens.home.landscape.collab.ai.InputOptimizer.Mode) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var showOptimizeModeMenu by remember { mutableStateOf(false) }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 优化按钮（长按切换模式）
        Box {
            if (isOptimizing) {
                IconButton(onClick = {}, Modifier.size(28.dp), enabled = false) {
                    CircularProgressIndicator(
                        Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .combinedClickable(
                            onClick = onOptimize,
                            onLongClick = { showOptimizeModeMenu = true },
                            enabled = inputText.isNotBlank() && !isLoading,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        stringResource(R.string.ai_optimize_input),
                        Modifier.size(16.dp),
                        tint = if (inputText.isNotBlank() && !isLoading) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        }
                    )
                }
            }
            OptimizeModeMenu(
                expanded = showOptimizeModeMenu,
                onDismiss = { showOptimizeModeMenu = false },
                optimizeMode = optimizeMode,
                onOptimizeModeChange = onOptimizeModeChange,
            )
        }

        // 添加图片按钮
        if (engineStatus == EngineStatus.Ready) {
            IconButton(onClick = onImagePick, Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.Image,
                    "添加图片",
                    Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        // 语音输入按钮
        VoiceInputButton(
            currentInput = inputText,
            onInputChange = onInputChange,
            enabled = engineStatus == EngineStatus.Ready && !isLoading,
        )

        // 上下文窗口进度按钮（分段环：用量+压缩+记忆）
        ContextProgressRing(
            contextUsedTokens = contextUsedTokens,
            contextMaxTokens = contextMaxTokens,
            isContextCompressed = isContextCompressed,
            contextCompressedTokens = contextCompressedTokens,
            memoryEntryCount = memoryEntryCount,
            memoryTotalTokens = memoryTotalTokens,
            onClick = onContextInfoClick,
        )

        // 发送/取消按钮
        if (isLoading) {
            IconButton(onClick = onCancel, Modifier.size(32.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        Modifier.size(28.dp),
                        strokeWidth = 2.5.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Icon(
                        Icons.Default.Pause,
                        stringResource(R.string.chat_cancel),
                        Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        } else {
            val canSend = (inputText.isNotBlank() || hasAttachments) && engineStatus == EngineStatus.Ready
            IconButton(onClick = onSend, Modifier.size(32.dp), enabled = canSend) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    stringResource(R.string.ai_send_message),
                    Modifier.size(20.dp),
                    tint = if (canSend) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
private fun OptimizeModeMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    optimizeMode: com.medeide.jh.screens.home.landscape.collab.ai.InputOptimizer.Mode,
    onOptimizeModeChange: (com.medeide.jh.screens.home.landscape.collab.ai.InputOptimizer.Mode) -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier.heightIn(max = 600.dp)
    ) {
        com.medeide.jh.screens.home.landscape.collab.ai.InputOptimizer.Mode.entries.forEach { mode ->
            DropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            mode.icon,
                            null,
                            Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                mode.label,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                            )
                            Text(
                                mode.description,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 9.sp,
                                maxLines = 1
                            )
                        }
                    }
                },
                onClick = {
                    onOptimizeModeChange(mode)
                    onDismiss()
                },
                leadingIcon = if (mode == optimizeMode) {
                    {
                        Icon(
                            Icons.Default.Check,
                            null,
                            Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                } else null,
            )
        }
    }
}

@Composable
private fun ContextProgressRing(
    contextUsedTokens: Int,
    contextMaxTokens: Int,
    isContextCompressed: Boolean,
    contextCompressedTokens: Int,
    memoryEntryCount: Int,
    memoryTotalTokens: Int,
    onClick: () -> Unit,
) {
    val ratio = if (contextMaxTokens > 0) (contextUsedTokens.toFloat() / contextMaxTokens).coerceIn(0f, 1f) else 0f
    val compressedRatio = if (contextMaxTokens > 0 && contextCompressedTokens > 0)
        (contextCompressedTokens.toFloat() / contextMaxTokens).coerceIn(0f, 1f) else 0f
    val memoryRatio = if (contextMaxTokens > 0 && memoryTotalTokens > 0)
        (memoryTotalTokens.toFloat() / contextMaxTokens).coerceIn(0f, 1f) else 0f
    val hasMemory = memoryEntryCount > 0

    Box(
        modifier = Modifier
            .size(22.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(20.dp)) {
            val strokeWidth = 3f
            val outerRadius = size.minDimension / 2f - strokeWidth / 2f
            val topLeft = androidx.compose.ui.geometry.Offset(strokeWidth / 2f, strokeWidth / 2f)
            val arcSize = androidx.compose.ui.geometry.Size(outerRadius * 2, outerRadius * 2)
            // Track
            drawArc(
                Color(0xFFE0E0E0), -90f, 360f, false,
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    strokeWidth,
                    cap = androidx.compose.ui.graphics.StrokeCap.Butt
                ),
                topLeft = topLeft, size = arcSize
            )
            // Segment 1: 已用 token（绿/黄/红渐变）
            val usedColor = when {
                ratio < 0.5f -> Color(0xFF4CAF50)
                ratio < 0.8f -> Color(0xFFFFA000)
                else -> Color(0xFFE53935)
            }
            var segmentStart = -90f
            if (ratio > 0f) {
                drawArc(
                    usedColor, segmentStart, ratio * 360f, false,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        strokeWidth,
                        cap = androidx.compose.ui.graphics.StrokeCap.Butt
                    ),
                    topLeft = topLeft, size = arcSize
                )
                segmentStart += ratio * 360f + 3f
            }
            // Segment 2: 已压缩 token（紫色）
            if (isContextCompressed && compressedRatio > 0.01f) {
                val compressedArc = (compressedRatio * 360f).coerceAtMost(120f)
                drawArc(
                    Color(0xFF9C27B0).copy(alpha = 0.7f), segmentStart, compressedArc, false,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        strokeWidth * 0.8f,
                        cap = androidx.compose.ui.graphics.StrokeCap.Butt
                    ),
                    topLeft = topLeft, size = arcSize
                )
                segmentStart += compressedArc + 3f
            }
            // Segment 3: 记忆系统 token（蓝色）
            if (hasMemory && memoryRatio > 0.01f) {
                val memoryArc = (memoryRatio * 360f).coerceAtMost(90f)
                drawArc(
                    Color(0xFF1565C0).copy(alpha = 0.6f), segmentStart, memoryArc, false,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        strokeWidth * 0.7f,
                        cap = androidx.compose.ui.graphics.StrokeCap.Butt
                    ),
                    topLeft = topLeft, size = arcSize
                )
            }
        }
    }
}

// 语音输入按钮组件 - 使用 SpeechRecognizer，支持连续识别
@Composable
private fun VoiceInputButton(
    currentInput: String,
    onInputChange: (String) -> Unit,
    enabled: Boolean = true,
) {
    var isListening by remember { mutableStateOf(false) }
    var hasPermission by remember { mutableStateOf(false) }
    var partialText by remember { mutableStateOf("") }
    val recognizerManager = remember { VoiceRecognizerManager() }

    val permissionContext = LocalContext.current
    LaunchedEffect(Unit) {
        hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
            permissionContext, android.Manifest.permission.RECORD_AUDIO
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    DisposableEffect(Unit) {
        onDispose { recognizerManager.destroy() }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        hasPermission = isGranted
        if (isGranted) {
            recognizerManager.start(
                isListeningState = { isListening = it },
                onFinalResult = { text ->
                    partialText = ""
                    onInputChange(if (currentInput.isBlank()) text else "$currentInput$text")
                },
                onPartialResult = { text -> partialText = text },
            )
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            onClick = {
                if (isListening) {
                    recognizerManager.stop()
                    partialText = ""
                } else if (!hasPermission) {
                    permissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                } else {
                    partialText = ""
                    recognizerManager.start(
                        isListeningState = { isListening = it },
                        onFinalResult = { text ->
                            partialText = ""
                            onInputChange(if (currentInput.isBlank()) text else "$currentInput$text")
                        },
                        onPartialResult = { text -> partialText = text },
                    )
                }
            },
            modifier = Modifier.size(32.dp),
            enabled = enabled,
        ) {
            if (isListening) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = Color(0xFFE53935),
                )
            } else {
                Icon(
                    imageVector = if (hasPermission) Icons.Default.Mic else Icons.Default.MicOff,
                    contentDescription = "语音输入",
                    modifier = Modifier.size(18.dp),
                    tint = if (enabled) {
                        if (hasPermission) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                    },
                )
            }
        }
        // 语音输入识别中显示预览文本
        if (partialText.isNotBlank()) {
            Text(
                text = partialText,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}

/**
 * 语音识别管理器
 *
 * 核心逻辑与官方 gallery 示例一致：
 * - SpeechRecognizer 一次性创建并复用（官方 init 块）
 * - Intent 参数匹配官方（en-US、MAX_RESULTS=1、无额外 EXTRA）
 * - onError 空实现（官方不处理错误）
 * - onBeginningOfSpeech / onEndOfSpeech 空实现
 * - stop() 延迟 500ms 后 stopListening（官方 delay(500)）
 *
 * 差异（UI 需求）：
 * - 支持连续识别：onResults 后若 isActive 则自动重启
 * - 通过 generation 计数器防止并发回调污染
 */
private class VoiceRecognizerManager {
    private val appContext: android.content.Context = com.medeide.jh.MyApplication.instance

    private var recognizer: android.speech.SpeechRecognizer? = null
    private val recognizerIntent: android.content.Intent

    @Volatile private var isActive = false
    @Volatile private var generation = 0L
    private var onFinalCallback: ((String) -> Unit)? = null
    private var onPartialCallback: ((String) -> Unit)? = null
    private var onListeningCallback: ((Boolean) -> Unit)? = null
    private val handler = Handler(Looper.getMainLooper())

    init {
        recognizerIntent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, "en-US")
            putExtra(android.speech.RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(android.speech.RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
    }

    fun start(
        isListeningState: (Boolean) -> Unit,
        onFinalResult: (String) -> Unit,
        onPartialResult: (String) -> Unit,
    ) {
        destroy()
        isActive = true
        generation++
        onFinalCallback = onFinalResult
        onPartialCallback = onPartialResult
        onListeningCallback = isListeningState

        val r = android.speech.SpeechRecognizer.createSpeechRecognizer(appContext)
        if (r == null) {
            onListeningCallback?.invoke(false)
            android.widget.Toast.makeText(
                appContext,
                "未找到语音识别服务",
                android.widget.Toast.LENGTH_SHORT
            ).show()
            return
        }
        recognizer = r
        val gen = generation

        r.setRecognitionListener(object : android.speech.RecognitionListener {
            override fun onReadyForSpeech(params: android.os.Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}

            override fun onError(error: Int) {
                if (gen != generation) return
                r.destroy()
                if (recognizer == r) recognizer = null
                onListeningCallback?.invoke(false)
            }

            override fun onResults(results: android.os.Bundle?) {
                if (gen != generation) return
                val matches =
                    results?.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull()?.trim()
                if (text != null && text.isNotBlank()) {
                    onFinalCallback?.invoke(text)
                }
                r.destroy()
                if (recognizer == r) recognizer = null
                // 连续识别模式：用户未点击停止时自动重启
                if (isActive) {
                    handler.postDelayed({ if (isActive) startRecognizer(generation) }, 200)
                }
            }

            override fun onPartialResults(partialResults: android.os.Bundle?) {
                if (gen != generation) return
                val matches =
                    partialResults?.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull()?.trim()
                if (text != null && text.isNotBlank()) {
                    onPartialCallback?.invoke(text)
                }
            }

            override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
        })

        r.startListening(recognizerIntent)
        onListeningCallback?.invoke(true)
    }

    fun stop() {
        isActive = false
        handler.removeCallbacksAndMessages(null)
        recognizer?.stopListening()
        recognizer?.cancel()
        recognizer?.destroy()
        recognizer = null
        onListeningCallback?.invoke(false)
    }

    fun destroy() {
        isActive = false
        handler.removeCallbacksAndMessages(null)
        recognizer?.cancel()
        recognizer?.destroy()
        recognizer = null
    }

    private fun startRecognizer(gen: Long) {
        if (!isActive || gen != generation) return
        try {
            val r = android.speech.SpeechRecognizer.createSpeechRecognizer(appContext) ?: return
            if (gen != generation) { r.destroy(); return }
            recognizer = r

            r.setRecognitionListener(object : android.speech.RecognitionListener {
                override fun onReadyForSpeech(params: android.os.Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}

                override fun onError(error: Int) {
                    if (gen != generation) return
                    r.destroy()
                    if (recognizer == r) recognizer = null
                    onListeningCallback?.invoke(false)
                }

                override fun onResults(results: android.os.Bundle?) {
                    if (gen != generation) return
                    val matches =
                        results?.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull()?.trim()
                    if (text != null && text.isNotBlank()) {
                        onFinalCallback?.invoke(text)
                    }
                    r.destroy()
                    if (recognizer == r) recognizer = null
                    if (isActive) {
                        handler.postDelayed({ if (isActive) startRecognizer(generation) }, 200)
                    }
                }

                override fun onPartialResults(partialResults: android.os.Bundle?) {
                    if (gen != generation) return
                    val matches =
                        partialResults?.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull()?.trim()
                    if (text != null && text.isNotBlank()) {
                        onPartialCallback?.invoke(text)
                    }
                }

                override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
            })

            r.startListening(recognizerIntent)
            onListeningCallback?.invoke(true)
        } catch (_: Exception) {
            onListeningCallback?.invoke(false)
        }
    }
}
