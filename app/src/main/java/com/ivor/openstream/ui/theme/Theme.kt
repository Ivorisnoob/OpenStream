package com.ivor.openstream.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OpenStreamTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    /** True-black surfaces; dark themes only. */
    amoled: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            val scheme = if (darkTheme) {
                dynamicDarkColorScheme(context)
            } else {
                dynamicLightColorScheme(context)
            }
            if (darkTheme && amoled) scheme.withAmoledSurfaces() else scheme
        }
        darkTheme -> brandDarkColorScheme().let { if (amoled) it.withAmoledSurfaces() else it }
        else -> brandLightColorScheme()
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            // Bars stay transparent for edge-to-edge; only the icon appearance follows the
            // app's resolved theme (not the system's), so forced dark/light never gets
            // invisible bar icons. Previously this set an opaque statusBarColor, which
            // fought enableEdgeToEdge(), and never set the navigation bar appearance.
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).let { controller ->
                controller.isAppearanceLightStatusBars = !darkTheme
                controller.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(LocalAmoledBlack provides (darkTheme && amoled)) {
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = ExpressiveShapes,
            motionScheme = MotionScheme.expressive(),
            content = content
        )
    }
}

/** Whether the app is drawing the AMOLED variant: a dark theme with true-black pages. */
val LocalAmoledBlack = staticCompositionLocalOf { false }

/**
 * The page behind grouped lists such as Settings. Normally a step above the background so the
 * rows on it read as one sheet; true black under AMOLED, where a grey page is what the setting
 * promises to remove. The rows themselves stay raised, so they still separate from it.
 */
@Composable
@ReadOnlyComposable
fun groupedPageColor(): Color =
    if (LocalAmoledBlack.current) MaterialTheme.colorScheme.background
    else MaterialTheme.colorScheme.surfaceContainer
