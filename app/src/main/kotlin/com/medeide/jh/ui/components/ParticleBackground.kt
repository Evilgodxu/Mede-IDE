package com.medeide.jh.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.sqrt
import kotlin.random.Random

private class Particle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val radius: Float,
)

@Composable
fun ParticleBackground(
    modifier: Modifier = Modifier,
    particleCount: Int = 50,
    speedMultiplier: Float = 1.2f,
    minRadius: Float = 1f,
    maxRadius: Float = 2.5f,
    connectionDistance: Float = 120f,
) {
    val isDark = isSystemInDarkTheme()
    val accent = MaterialTheme.colorScheme.primary
    val particles = remember {
        List(particleCount) {
            Particle(
                x = Random.nextFloat(),
                y = Random.nextFloat(),
                vx = (Random.nextFloat() - 0.5f) * speedMultiplier,
                vy = (Random.nextFloat() - 0.5f) * speedMultiplier,
                radius = Random.nextFloat() * (maxRadius - minRadius) + minRadius,
            )
        }
    }

    val canvasW = remember { mutableStateOf(1f) }
    val canvasH = remember { mutableStateOf(1f) }
    val tick = remember { mutableStateOf(0L) }

    LaunchedEffect(Unit) {
        while (isActive) {
            delay(16)
            val w = canvasW.value.coerceAtLeast(1f)
            val h = canvasH.value.coerceAtLeast(1f)
            for (p in particles) {
                p.x += p.vx / w
                p.y += p.vy / h
                if (p.x < 0f) { p.x = 0f; p.vx = -p.vx }
                if (p.x > 1f) { p.x = 1f; p.vx = -p.vx }
                if (p.y < 0f) { p.y = 0f; p.vy = -p.vy }
                if (p.y > 1f) { p.y = 1f; p.vy = -p.vy }
            }
            tick.value = tick.value + 1
        }
    }

    Canvas(modifier) {
        canvasW.value = size.width
        canvasH.value = size.height
        tick.value

        val dotAlpha = if (isDark) 0.25f else 0.35f
        val dotColor = accent.copy(alpha = dotAlpha)
        for (p in particles) {
            drawCircle(dotColor, p.radius, Offset(p.x * size.width, p.y * size.height))
        }

        val lineAlpha = if (isDark) 0.18f else 0.25f
        val lineColor = accent.copy(alpha = lineAlpha)
        for (i in particles.indices) {
            for (j in i + 1 until particles.size) {
                val dx = (particles[i].x - particles[j].x) * size.width
                val dy = (particles[i].y - particles[j].y) * size.height
                val dist = sqrt(dx * dx + dy * dy)
                if (dist < connectionDistance) {
                    val alpha = (1f - dist / connectionDistance)
                    drawLine(
                        color = lineColor.copy(alpha = lineAlpha * alpha),
                        start = Offset(particles[i].x * size.width, particles[i].y * size.height),
                        end = Offset(particles[j].x * size.width, particles[j].y * size.height),
                        strokeWidth = 0.5f,
                    )
                }
            }
        }
    }
}
