# Telegram "Liquid Glass" UI Research

> **Note:** This document analyzes the "Liquid Glass" design aesthetic (translucency, blur, glassmorphism) in the Telegram Android app. While specific version numbers (e.g., v12.4.0) and dates (2026) appear in some contexts, this research focuses on the verifiable technical implementation of these effects in the current Android ecosystem.

---

## 1. Concept: "Liquid Glass"

The term "Liquid Glass" refers to a UI design language characterized by:
*   **High Translucency:** UI elements (navigation bars, panels) that allow background content to show through.
*   **Real-time Blur:** The background content is blurred dynamically as it scrolls behind the overlay.
*   **Refraction/Depth:** Subtle visual distortions or highlights that mimic physical glass.

This is an evolution of "Glassmorphism" and is implemented in Telegram to create a sense of depth and modern aesthetics.

---

## 2. Technical Implementation in Android

Achieving a high-performance "Liquid Glass" effect on Android is challenging due to the computational cost of real-time blurring. Telegram's implementation (and similar high-quality apps) typically uses the following techniques:

### A. The "Snapshot + Blur" Technique

Since standard Android `View` transparency doesn't automatically blur the content behind it (unlike iOS `UIVisualEffectView`), Telegram implements this manually:

1.  **Snapshot:** The view hierarchy *behind* the glass element (e.g., the chat list) is drawn into a `Bitmap` or `RenderNode`.
2.  **Downsample:** The bitmap is scaled down (e.g., to 1/4 or 1/8 size) to reduce the number of pixels to process.
3.  **Blur:** A blur algorithm is applied.
    *   **Android 12+:** Uses `RenderEffect.createBlurEffect()` which is hardware-accelerated.
    *   **Older Android:** Uses `ScriptIntrinsicBlur` (RenderScript) or a custom OpenGL shader (`BlurringShader`).
4.  **Draw:** The blurred bitmap is drawn as the background of the overlay view.
5.  **Invalidate:** As the content scrolls, the snapshot is updated (often on every frame or scroll event) to create the "live" effect.

### B. Relevant Components in Source Code

The official Telegram Android source code is hosted at [https://github.com/DrKLO/Telegram](https://github.com/DrKLO/Telegram). The key components for this effect are located in the `TMessagesProj` module:

*   **`org.telegram.ui.Components` Package:** This package contains most custom views. Look for classes related to blur or glass effects.
    *   *Likely Candidates:* `BlurringShader`, `RealtimeBlurView`, `BlurView`.
*   **`org.telegram.ui.ActionBar` Package:** The `ActionBar` and `Theme` classes define the top bar's appearance and the app-wide theme properties (colors, alpha values).
    *   *Path:* `TMessagesProj/src/main/java/org/telegram/ui/ActionBar/ActionBar.java`
*   **`org.telegram.ui.Theme`**: Handles the color palette, including the alpha values for glass elements (`key_actionBarDefault`, `key_windowBackgroundWhite`, etc.).

### C. Third-Party Libraries

Telegram's implementation is custom, but it shares similarities with popular open-source libraries that can be used to replicate the effect:

*   **`mmin18/RealtimeBlurView`:** A library that provides a `RealtimeBlurView` which overlays other views and blurs the content behind them. It handles the snapshot-downsample-blur loop efficiently.
*   **`Dimezis/BlurView`:** Another popular library that uses `RenderScript` (or `RenderEffect` on newer APIs) to blur the underlying views.

---

## 3. Implementing "Liquid Glass" in OpenAnime

To adopt this aesthetic in OpenAnime (Jetpack Compose + Material 3 Expressive):

### Strategy
1.  **Modern Approach (Android 12+):** Use the `Modifier.blur()` modifier on a `Box` that contains the background content, but this blurs the *content itself*, not the overlay.
2.  **Overlay Approach (The "Glass" Way):**
    *   Place the background content (e.g., `LazyVerticalGrid`).
    *   Place a `Box` (the "Glass" pane) on top.
    *   Use `Modifier.background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))` for the glass pane.
    *   *Challenge:* Compose doesn't have a built-in "backdrop blur" modifier for creating a glass effect *over* dynamic content easily without Android 12+'s `RenderEffect`.

### Recommendation
For OpenAnime, stick to **Material 3 Expressive** principles which use **Surface Tint** and **Elevation** rather than heavy blur, to ensure performance and consistency across all Android versions. If a "Liquid Glass" feel is desired, use:
*   High transparency (`alpha = 0.9f`) for top/bottom bars.
*   `WindowCompat.setDecorFitsSystemWindows(window, false)` to draw behind system bars.
*   Subtle gradients in the container colors.

---

## 4. References

*   **Telegram Source Code:** [https://github.com/DrKLO/Telegram](https://github.com/DrKLO/Telegram)
*   **Directory Structure:** `TMessagesProj/src/main/java/org/telegram/ui/`
