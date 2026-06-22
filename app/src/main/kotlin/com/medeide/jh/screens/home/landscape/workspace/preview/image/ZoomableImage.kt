package com.medeide.jh.screens.home.landscape.workspace.preview.image

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min

private const val MAX_SCALE = 5f
private const val MIN_SCALE = 1f

@Composable
fun ZoomableImage(
    bitmap: ImageBitmap,
    contentDescription: String?,
    onScaleChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var containerWidth by remember { mutableFloatStateOf(0f) }
    var containerHeight by remember { mutableFloatStateOf(0f) }

    val bitmapWidth = bitmap.width.toFloat()
    val bitmapHeight = bitmap.height.toFloat()

    fun fitScale(): Float = if (bitmapWidth > 0f && bitmapHeight > 0f) {
        min(containerWidth / bitmapWidth, containerHeight / bitmapHeight)
    } else {
        1f
    }

    fun fittedWidth(): Float = bitmapWidth * fitScale()
    fun fittedHeight(): Float = bitmapHeight * fitScale()

    fun clampOffsets() {
        val fw = fittedWidth() * scale
        val fh = fittedHeight() * scale
        val slackX = (containerWidth - fw) / 2f
        val slackY = (containerHeight - fh) / 2f
        offsetX = offsetX.coerceIn(min(slackX, -slackX), max(slackX, -slackX))
        offsetY = offsetY.coerceIn(min(slackY, -slackY), max(slackY, -slackY))
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF1E1E1E))
            .onGloballyPositioned { coords ->
                containerWidth = coords.size.width.toFloat()
                containerHeight = coords.size.height.toFloat()
                clampOffsets()
            }
    ) {
        Image(
            bitmap = bitmap,
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offsetX
                    translationY = offsetY
                    clip = true
                }
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        val newScale = (scale * zoom).coerceIn(MIN_SCALE, MAX_SCALE)
                        val deltaScale = newScale - scale
                        val centerX = containerWidth / 2f
                        val centerY = containerHeight / 2f

                        // 以捏合中心为锚点缩放
                        offsetX -= deltaScale * (centroid.x - centerX)
                        offsetY -= deltaScale * (centroid.y - centerY)
                        scale = newScale

                        // 平移
                        offsetX += pan.x
                        offsetY += pan.y

                        clampOffsets()
                        onScaleChange(scale)
                    }
                }
        )
    }
}

@Composable
internal fun ImageErrorPlaceholder(message: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.BrokenImage,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
        }
    }
}
