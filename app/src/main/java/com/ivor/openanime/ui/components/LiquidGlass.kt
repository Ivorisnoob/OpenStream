package com.ivor.openanime.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * A container that applies a "Liquid Glass" visual style to the content behind it.
 *
 * Note: Due to limitations in Compose/Android View interop for backdrop blur without
 * specialized libraries or risky View recursion, this implementation focuses on the
 * safe "Glass" aesthetic using translucency.
 *
 * Future improvements could integrate Android 12+ RenderEffect on the *content*
 * container or use libraries like Toolkit Haze.
 *
 * @param modifier The modifier to be applied to the layout.
 * @param blurRadius The radius of the blur in pixels (Used for API 31+ if implemented in future).
 * @param overlayColor The color to overlay (should be semi-transparent).
 * @param content The content to be displayed on top of the glass effect.
 */
@Composable
fun LiquidGlass(
    modifier: Modifier = Modifier,
    blurRadius: Float = 30f,
    overlayColor: Color = Color.White.copy(alpha = 0.85f), // Higher alpha for glass look without blur
    content: @Composable () -> Unit
) {
    // Safe implementation: Translucent overlay background
    Box(
        modifier = modifier.background(overlayColor)
    ) {
        content()
    }
}
