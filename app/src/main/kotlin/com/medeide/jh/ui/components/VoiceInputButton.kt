package com.medeide.jh.ui.components

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

/**
 * 语音输入按钮 — 使用 SpeechRecognizer 实现，支持连续识别。
 *
 * - 无权限时显示 MicOff 图标，点击请求权限
 * - 无权限时显示 Mic 图标，点击开始语音识别
 * - 识别中显示红色旋转指示器
 * - 部分识别结果实时显示在按钮右侧
 */
@Composable
fun VoiceInputButton(
    currentInput: String,
    onInputChange: (String) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    var isListening by remember { mutableStateOf(false) }
    var hasPermission by remember { mutableStateOf(false) }
    var partialText by remember { mutableStateOf("") }
    val manager = remember { VoiceRecognizerManager() }

    val ctx = LocalContext.current
    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO)
        hasPermission = granted == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    DisposableEffect(Unit) {
        onDispose { manager.destroy() }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (granted) {
            manager.start(
                isListeningState = { isListening = it },
                onFinalResult = { text ->
                    partialText = ""
                    onInputChange(if (currentInput.isBlank()) text else "$currentInput$text")
                },
                onPartialResult = { text -> partialText = text },
            )
        }
    }

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            onClick = {
                if (isListening) {
                    manager.stop()
                    partialText = ""
                } else if (!hasPermission) {
                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                } else {
                    partialText = ""
                    manager.start(
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
        if (partialText.isNotBlank()) {
            Text(
                text = partialText,
                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}

/**
 * 语音识别管理器 — SpeechRecognizer 封装。
 *
 * 支持连续识别：onResults 后若 isActive 则自动重启。
 * 通过 generation 计数器防止并发回调污染。
 */
private class VoiceRecognizerManager {

    private var recognizer: SpeechRecognizer? = null
    private val recognizerIntent: Intent

    @Volatile private var isActive = false
    @Volatile private var generation = 0L
    private var onFinalCallback: ((String) -> Unit)? = null
    private var onPartialCallback: ((String) -> Unit)? = null
    private var onListeningCallback: ((Boolean) -> Unit)? = null
    private val handler = Handler(Looper.getMainLooper())

    init {
        recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
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
        startRecognizer(generation)
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
            val r = SpeechRecognizer.createSpeechRecognizer(
                com.medeide.jh.MyApplication.instance
            ) ?: run {
                onListeningCallback?.invoke(false)
                Toast.makeText(com.medeide.jh.MyApplication.instance, "未找到语音识别服务", Toast.LENGTH_SHORT).show()
                return
            }
            if (gen != generation) { r.destroy(); return }
            recognizer = r

            r.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
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

                override fun onResults(results: Bundle?) {
                    if (gen != generation) return
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
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

                override fun onPartialResults(partialResults: Bundle?) {
                    if (gen != generation) return
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull()?.trim()
                    if (text != null && text.isNotBlank()) {
                        onPartialCallback?.invoke(text)
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            r.startListening(recognizerIntent)
            onListeningCallback?.invoke(true)
        } catch (_: Exception) {
            onListeningCallback?.invoke(false)
        }
    }
}
