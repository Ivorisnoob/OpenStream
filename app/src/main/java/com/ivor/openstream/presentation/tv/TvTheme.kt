package com.ivor.openstream.presentation.tv

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp

private val TvTypography = Typography(
    displayLarge = TextStyle(fontWeight = FontWeight.Black, fontSize = 40.sp, lineHeight = 46.sp),
    displayMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 40.sp),
    displaySmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 36.sp),
    headlineLarge = TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = 32.sp, lineHeight = 38.sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 30.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 24.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 18.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 15.sp)
)

@Composable
fun TvTheme(content: @Composable () -> Unit) {
    val density = LocalDensity.current
    val pinned = Density(density = density.density, fontScale = 1f)
    CompositionLocalProvider(LocalDensity provides pinned) {
        val colors = MaterialTheme.colorScheme
        MaterialTheme(
            colorScheme = colors,
            typography = TvTypography,
            shapes = MaterialTheme.shapes
        ) {
            // Compose for TV components (drawer, cards, buttons) read their own theme: same colors.
            androidx.tv.material3.MaterialTheme(
                colorScheme = androidx.tv.material3.darkColorScheme(
                    primary = colors.primary,
                    onPrimary = colors.onPrimary,
                    primaryContainer = colors.primaryContainer,
                    onPrimaryContainer = colors.onPrimaryContainer,
                    inversePrimary = colors.inversePrimary,
                    secondary = colors.secondary,
                    onSecondary = colors.onSecondary,
                    secondaryContainer = colors.secondaryContainer,
                    onSecondaryContainer = colors.onSecondaryContainer,
                    tertiary = colors.tertiary,
                    onTertiary = colors.onTertiary,
                    tertiaryContainer = colors.tertiaryContainer,
                    onTertiaryContainer = colors.onTertiaryContainer,
                    background = colors.background,
                    onBackground = colors.onBackground,
                    surface = colors.surfaceContainer,
                    onSurface = colors.onSurface,
                    surfaceVariant = colors.surfaceContainerHigh,
                    onSurfaceVariant = colors.onSurfaceVariant,
                    surfaceTint = colors.surfaceTint,
                    inverseSurface = colors.inverseSurface,
                    inverseOnSurface = colors.inverseOnSurface,
                    error = colors.error,
                    onError = colors.onError,
                    errorContainer = colors.errorContainer,
                    onErrorContainer = colors.onErrorContainer,
                    border = colors.outline,
                    borderVariant = colors.outlineVariant,
                    scrim = colors.scrim
                ),
                content = content
            )
        }
    }
}