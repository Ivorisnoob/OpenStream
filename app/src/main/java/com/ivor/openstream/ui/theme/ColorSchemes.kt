package com.ivor.openstream.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/** The brand palette used when dynamic color is off or unavailable. */
internal fun brandDarkColorScheme(): ColorScheme = darkColorScheme(
    primary = VibrantPrimaryDark,
    onPrimary = OnPrimaryDark,
    primaryContainer = VibrantPrimaryContainerDark,
    onPrimaryContainer = OnPrimaryContainerDark,
    secondary = VibrantSecondaryDark,
    onSecondary = OnSecondaryDark,
    secondaryContainer = VibrantSecondaryContainerDark,
    onSecondaryContainer = OnSecondaryContainerDark,
    tertiary = VibrantTertiaryDark,
    onTertiary = OnTertiaryDark,
    tertiaryContainer = VibrantTertiaryContainerDark,
    onTertiaryContainer = OnTertiaryContainerDark,
    error = ErrorDark,
    onError = OnErrorDark,
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = OutlineDark
)

internal fun brandLightColorScheme(): ColorScheme = lightColorScheme(
    primary = VibrantPrimaryLight,
    onPrimary = OnPrimaryLight,
    primaryContainer = VibrantPrimaryContainerLight,
    onPrimaryContainer = OnPrimaryContainerLight,
    secondary = VibrantSecondaryLight,
    onSecondary = OnSecondaryLight,
    secondaryContainer = VibrantSecondaryContainerLight,
    onSecondaryContainer = OnSecondaryContainerLight,
    tertiary = VibrantTertiaryLight,
    onTertiary = OnTertiaryLight,
    tertiaryContainer = VibrantTertiaryContainerLight,
    onTertiaryContainer = OnTertiaryContainerLight,
    error = ErrorLight,
    onError = OnErrorLight,
    errorContainer = ErrorContainerLight,
    onErrorContainer = OnErrorContainerLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    outline = OutlineLight
)

/**
 * The AMOLED variant of a dark scheme: the page is true black, so an OLED panel can switch those
 * pixels off, while raised surfaces step up in tiny increments so cards, sheets and settings rows
 * still read as separate layers.
 *
 * Accent roles are left untouched, which keeps wallpaper colors when dynamic color is on. The
 * neutral text and outline roles are pinned here instead of being inherited: a wallpaper-derived
 * scheme can return tinted neutrals, and those are exactly the colors that go unreadable once the
 * surface under them becomes black.
 */
internal fun ColorScheme.withAmoledSurfaces(): ColorScheme = copy(
    background = AmoledBackground,
    onBackground = AmoledOnSurface,
    surface = AmoledSurface,
    onSurface = AmoledOnSurface,
    surfaceVariant = AmoledSurfaceVariant,
    onSurfaceVariant = AmoledOnSurfaceVariant,
    // Elevation is expressed by the container steps below, not by a tinted overlay; a tint here
    // would put a purple cast back on a surface that is supposed to be black.
    surfaceTint = AmoledBackground,
    surfaceDim = AmoledSurfaceDim,
    surfaceBright = AmoledSurfaceBright,
    surfaceContainerLowest = AmoledSurfaceContainerLowest,
    surfaceContainerLow = AmoledSurfaceContainerLow,
    surfaceContainer = AmoledSurfaceContainer,
    surfaceContainerHigh = AmoledSurfaceContainerHigh,
    surfaceContainerHighest = AmoledSurfaceContainerHighest,
    inverseSurface = AmoledInverseSurface,
    inverseOnSurface = AmoledInverseOnSurface,
    outline = AmoledOutline,
    outlineVariant = AmoledOutlineVariant,
    scrim = Color.Black
)

/**
 * Text and icons drawn on artwork, video or a scrim. Light in every theme: the scrim is black in
 * light and dark alike, which is why the theme's inverse colours (dark in a dark theme) cannot be
 * used there.
 */
val ColorScheme.onScrim: Color get() = Color.White
