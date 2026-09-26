package com.ivor.openstream.presentation.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.ivor.openstream.ui.theme.ExpressiveShapes

/**
 * A slow highlight sweeping across placeholder shapes, so loading reads as "content is coming"
 * rather than a blank page. Placeholders are hidden from screen readers.
 */
fun Modifier.shimmer(): Modifier = composed {
    val base = MaterialTheme.colorScheme.surfaceContainerHighest
    val highlight = MaterialTheme.colorScheme.surfaceContainerHigh
    val progress by rememberInfiniteTransition(label = "shimmer").animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(1_300, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmerProgress"
    )
    background(
        Brush.linearGradient(
            colors = listOf(base, highlight, base),
            start = Offset(progress * 1_000f, 0f),
            end = Offset(progress * 1_000f + 600f, 600f)
        )
    )
}

@Composable
fun SkeletonBox(modifier: Modifier, shape: Shape = ExpressiveShapes.medium) {
    Box(
        modifier = modifier
            .clip(shape)
            .shimmer()
            .clearAndSetSemantics { }
    )
}
