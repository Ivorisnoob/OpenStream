package com.ivor.openanime.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope

/**
 * Animated gradient background mimicking Telegram's "Motion Background".
 */
@Composable
fun MotionBackground(
    modifier: Modifier = Modifier,
    colors: List<Color> = listOf(
        Color(0xFF648CF4),
        Color(0xFF8C69CF),
        Color(0xFFD45979),
        Color(0xFF5890C5)
    ),
    content: @Composable () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "MotionBackground")

    // Animate gradient offset
    val offsetAnim by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 10000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Offset"
    )

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawMotionGradient(colors, offsetAnim)
        }
        content()
    }
}

private fun DrawScope.drawMotionGradient(colors: List<Color>, progress: Float) {
    val width = size.width
    val height = size.height

    // Calculate dynamic start/end points based on progress
    // Move diagonal: Top-Left to Bottom-Right, shifting slightly
    val startX = width * 0.2f * progress
    val startY = height * 0.2f * progress
    val endX = width * (0.8f + 0.2f * (1 - progress))
    val endY = height * (0.8f + 0.2f * (1 - progress))

    val gradient = Brush.linearGradient(
        colors = colors,
        start = Offset(startX, startY),
        end = Offset(endX, endY)
    )

    drawRect(brush = gradient)
}
