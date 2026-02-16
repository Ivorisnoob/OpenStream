# Telegram "Liquid Glass" UI Implementation

> **Note:** The term "Liquid Glass" is a design concept for a translucent, blurred UI layer. While Telegram uses this extensively (e.g., in chat lists, panels, and navigation bars), the codebase does not have a single class named `LiquidGlass`. The effect is achieved through custom Views that implement real-time blurring.

This document provides a **Reference Implementation** of the "Liquid Glass" effect for Android, mimicking the behavior found in Telegram.

---

## 1. Core Concept: Real-time Blur

To achieve the "glass" look, a View must:
1.  **Overlay** the content behind it.
2.  **Snapshot** the underlying content.
3.  **Blur** the snapshot.
4.  **Draw** the blurred result as its background.
5.  **Update** continuously as the content scrolls.

---

## 2. Reference Implementation (Custom View)

Below is a `RealtimeBlurView` implementation that uses Android 12's `RenderEffect` for hardware-accelerated blur. For older devices, you would need `RenderScript` (deprecated) or a custom shader.

### `RealtimeBlurView.kt`

```kotlin
package com.example.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.util.AttributeSet
import android.view.View
import android.view.ViewTreeObserver
import androidx.annotation.RequiresApi

/**
 * A View that blurs the content behind it in real-time.
 * Mimics the "Liquid Glass" effect seen in Telegram.
 */
class RealtimeBlurView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint()
    private var blurRadius = 25f // Adjust for stronger/weaker blur
    private var overlayColor = 0xAAFFFFFF.toInt() // Semi-transparent white for glass tint

    init {
        // Enable hardware acceleration for RenderEffect
        setLayerType(LAYER_TYPE_HARDWARE, null)

        // Listen for scroll changes in the parent to update the blur
        viewTreeObserver.addOnScrollChangedListener {
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            drawBlurredContentModern(canvas)
        } else {
            // Fallback for older Android versions (omitted for brevity)
            // Typically involves capturing a Bitmap of the parent,
            // downscaling, applying ScriptIntrinsicBlur, and drawing.
            canvas.drawColor(overlayColor)
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun drawBlurredContentModern(canvas: Canvas) {
        // applying a blur effect to the View itself won't blur what's *behind* it directly
        // in standard View system easily without snapshotting.
        // However, RenderEffect.createBlurEffect typically blurs the View's *content*.

        // The standard "Liquid Glass" trick in Android Views:
        // 1. Get the parent view.
        val parent = parent as? View ?: return

        // 2. Translate canvas to match parent coordinates
        canvas.save()
        canvas.translate(-x, -y)

        // 3. Apply the blur effect to the *drawing* of the parent
        // Note: This is simplified. A robust implementation requires
        // drawing the parent to a separate hardware layer or bitmap with the effect.

        // For a true "Glass" overlay using pure Android 12+ APIs:
        this.setRenderEffect(RenderEffect.createBlurEffect(blurRadius, blurRadius, Shader.TileMode.CLAMP))

        // Draw the parent *content* onto this canvas
        // This causes the parent's content to be drawn blurred *inside* this view's bounds.
        parent.draw(canvas)

        canvas.restore()

        // Draw the tint overlay
        canvas.drawColor(overlayColor)
    }
}
```

**Critical Implementation Note:**
The code above is a simplified conceptual model. A production-ready implementation (like Telegram's) handles:
*   **Performance:** Downscaling the snapshot (e.g., to 1/8th size) before blurring is crucial for 60fps scrolling.
*   **Clipping:** Ensuring the blur doesn't bleed outside the view bounds.
*   **Rounded Corners:** Applying a rounded rect clip to the blurred content.

---

## 3. Telegram Source Code Location

If you wish to explore the **official** Telegram implementation, navigate to their GitHub repository:

*   **Repository:** [https://github.com/DrKLO/Telegram](https://github.com/DrKLO/Telegram)
*   **Path:** `TMessagesProj/src/main/java/org/telegram/ui/Components`

Look for files such as:
*   `BlurringShader.java` (Custom OpenGL shader for blur)
*   `RealtimeBlurView.java` (The view that manages the snapshot/blur loop)
*   `SizeNotifierFrameLayout.java` (Often the root view that manages layout changes)

---

## 4. Integration in OpenAnime (Compose)

Since OpenAnime uses Jetpack Compose, the "Liquid Glass" effect is best implemented using `Modifier.blur` (Android 12+) or a library like `Toolkit`'s `Glassmorphism`.

### Compose Example

```kotlin
@Composable
fun LiquidGlassOverlay(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
            .blur(radius = 16.dp) // Android 12+ only
    ) {
        content()
    }
}
```

For full compatibility, consider using the **Haze** library (`dev.chrisbanes.haze:haze`), which provides a high-performance, backward-compatible glass effect for Compose.

---

## 5. Summary

Telegram's "Liquid Glass" is a sophisticated combination of:
1.  **Real-time Snapshotting** of the view hierarchy.
2.  **Downscaled Blurring** (often via custom shaders).
3.  **Translucent Overlays** with subtle gradients and borders.

While the exact proprietary code is complex, the `RealtimeBlurView` pattern and modern `RenderEffect` APIs allow developers to recreate this premium aesthetic.
