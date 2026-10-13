package com.ivor.openstream.presentation.tv

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp

/**
 * The 10-foot type scale.
 *
 * Two things make the phone scale unusable on a TV:
 *
 * 1. **Size.** The phone scale is tuned for a 6 inch panel at 30 cm. The same
 *    `displaySmall` (36 sp) on a 55 inch panel at 3 m is not "bigger text", it
 *    is a banner. A TV app has to fit a header, a hero and several rails on
 *    one screen, so its scale starts well below the phone's.
 * 2. **The system font-size setting.** TVs ship with (and users raise) a larger
 *    `font_scale`, which multiplies every `sp` value. At 1.3x the rails stop
 *    fitting and rows collide. [TvTheme] pins the scale instead of letting a
 *    phone-shaped accessibility setting wreck a 10-foot layout.
 *
 * Weight still carries the hierarchy: TV is viewed from across a room, where
 * contrast matters more than size, so headings stay heavy while getting smaller.
 */
private val TvTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Black,
        fontSize = 34.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.25).sp
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp
    ),
    displaySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 19.sp,
        lineHeight = 25.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 17.sp,
        lineHeight = 23.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        lineHeight = 20.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 17.sp
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 15.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        lineHeight = 13.sp
    )
)

/**
 * Wraps TV content in the dense 10-foot theme and pins the font scale.
 *
 * Must sit above every TV screen, including the phone screens TV still reuses,
 * which is the point: one override re-scales the whole 10-foot app at once
 * instead of patching each screen's dp literals.
 */
@Composable
fun TvTheme(content: @Composable () -> Unit) {
    val density = LocalDensity.current
    // Density is kept (so dp still means the same physical size on any panel);
    // only the accessibility multiplier on sp is neutralised.
    val pinned = Density(density = density.density, fontScale = 1f)
    CompositionLocalProvider(LocalDensity provides pinned) {
        MaterialTheme(typography = TvTypography, content = content)
    }
}