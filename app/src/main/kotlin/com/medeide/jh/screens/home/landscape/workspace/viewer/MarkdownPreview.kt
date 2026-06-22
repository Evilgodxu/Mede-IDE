package com.medeide.jh.screens.home.landscape.workspace.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.medeide.jh.screens.home.landscape.workspace.editor.CodeEditor
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.core.MarkwonTheme
import io.noties.markwon.ext.tables.TablePlugin
import java.io.File

/**
 * Markdown 文件预览/编辑双模式组件。
 * 预览模式使用 Markwon 原生渲染 Markdown；代码模式使用 CodeEditor 编辑源码。
 */
@Composable
fun MarkdownPreview(
    filePath: String,
    isPreviewMode: Boolean,
    onToggleMode: () -> Unit,
    textFieldValue: TextFieldValue,
    onTextChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        // 模式切换栏（与 WebPreview 保持一致风格）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                .padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (isPreviewMode) Icons.Default.Language else Icons.Default.Code,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = if (isPreviewMode) "预览模式" else "代码模式",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = onToggleMode,
                modifier = Modifier.size(24.dp),
            ) {
                Icon(
                    imageVector = if (isPreviewMode) Icons.Default.Code else Icons.Default.Language,
                    contentDescription = if (isPreviewMode) "切换到代码模式" else "切换到预览模式",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = File(filePath).name,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // 内容区
        Box(modifier = Modifier.weight(1f).fillMaxSize()) {
            if (isPreviewMode) {
                MarkdownPreviewContent(
                    markdown = textFieldValue.text,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                CodeEditor(
                    text = textFieldValue,
                    onTextChange = onTextChange,
                    modifier = Modifier.fillMaxSize(),
                    onAddToChat = {},
                    onCursorChange = { _, _ -> },
                )
            }
        }
    }
}

/** 使用 Markwon 渲染 Markdown 内容 */
@Composable
private fun MarkdownPreviewContent(
    markdown: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    val textColor = MaterialTheme.colorScheme.onSurface
    val textColorArgb = textColor.toArgb()

    val markwon = remember(isDark) {
        val codeText = if (isDark) 0xFFcdd6f4.toInt() else 0xFF383838.toInt()
        val codeBg = if (isDark) 0xFF181825.toInt() else 0xFFf0f0f0.toInt()
        val quoteBarColor = if (isDark) 0x55cdd6f4.toInt() else 0xFF4a6fa5.toInt()
        Markwon.builder(context)
            .usePlugin(object : AbstractMarkwonPlugin() {
                override fun configureTheme(builder: MarkwonTheme.Builder) {
                    builder.codeTextColor(codeText)
                        .codeBackgroundColor(codeBg)
                        .codeBlockTextColor(codeText)
                        .codeBlockBackgroundColor(codeBg)
                        .blockQuoteColor(quoteBarColor)
                        .linkColor(textColorArgb)
                }
            })
            .usePlugin(TablePlugin.create(context))
            .build()
    }

    AndroidView<android.widget.TextView>(
        factory = { ctx ->
            android.widget.TextView(ctx).apply {
                textSize = 14f
                setTextColor(textColorArgb)
                setLinkTextColor(textColorArgb)
                setTextIsSelectable(true)
            }
        },
        update = { tv ->
            tv.setTextColor(textColorArgb)
            tv.setLinkTextColor(textColorArgb)
            markwon.setMarkdown(tv, markdown)
            // Markwon 渲染后强制所有可点击 Span 使用正文颜色
            val spannable = tv.text
            if (spannable is android.text.Spannable) {
                val spans = spannable.getSpans(
                    0, spannable.length,
                    android.text.style.ClickableSpan::class.java
                ).toList()
                for (span in spans) {
                    val start = spannable.getSpanStart(span)
                    val end = spannable.getSpanEnd(span)
                    val flags = spannable.getSpanFlags(span)
                    spannable.removeSpan(span)
                    spannable.setSpan(
                        object : android.text.style.ClickableSpan() {
                            override fun onClick(widget: android.view.View) = span.onClick(widget)
                            override fun updateDrawState(ds: android.text.TextPaint) {
                                ds.color = textColorArgb
                                ds.isUnderlineText = true
                            }
                        },
                        start, end, flags,
                    )
                }
            }
        },
        modifier = modifier,
    )
}
