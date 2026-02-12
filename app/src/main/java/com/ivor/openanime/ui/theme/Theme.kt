package com.ivor.openanime.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.expressiveLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryDark,
    onPrimary = OnPrimaryDark,
    primaryContainer = PrimaryContainerDark,
    onPrimaryContainer = OnPrimaryContainerDark,
    secondary = SecondaryDark,
    onSecondary = OnSecondaryDark,
    secondaryContainer = SecondaryContainerDark,
    onSecondaryContainer = OnSecondaryContainerDark,
    tertiary = TertiaryDark,
    onTertiary = OnTertiaryDark,
    tertiaryContainer = TertiaryContainerDark,
    onTertiaryContainer = OnTertiaryContainerDark,
    error = ErrorDark,
    onError = OnErrorDark,
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = OutlineDark,
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OpenAnimeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> expressiveLightColorScheme().copy(
            primary = DarkColorScheme.primary,
            onPrimary = DarkColorScheme.onPrimary,
            primaryContainer = DarkColorScheme.primaryContainer,
            onPrimaryContainer = DarkColorScheme.onPrimaryContainer,
            secondary = DarkColorScheme.secondary,
            onSecondary = DarkColorScheme.onSecondary,
            secondaryContainer = DarkColorScheme.secondaryContainer,
            onSecondaryContainer = DarkColorScheme.onSecondaryContainer,
            tertiary = DarkColorScheme.tertiary,
            onTertiary = DarkColorScheme.onTertiary,
            tertiaryContainer = DarkColorScheme.tertiaryContainer,
            onTertiaryContainer = DarkColorScheme.onTertiaryContainer,
            error = DarkColorScheme.error,
            onError = DarkColorScheme.onError,
            errorContainer = DarkColorScheme.errorContainer,
            onErrorContainer = DarkColorScheme.onErrorContainer,
            surface = DarkColorScheme.surface,
            onSurface = DarkColorScheme.onSurface,
            surfaceVariant = DarkColorScheme.surfaceVariant,
            onSurfaceVariant = DarkColorScheme.onSurfaceVariant,
            outline = DarkColorScheme.outline,
        )
        else -> expressiveLightColorScheme()
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            @Suppress("DEPRECATION")
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = ExpressiveShapes,
        content = content
    )
}