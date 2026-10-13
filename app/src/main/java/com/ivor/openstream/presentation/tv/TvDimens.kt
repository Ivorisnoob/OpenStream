package com.ivor.openstream.presentation.tv

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Every TV size, in one place.
 *
 * A TV is not a big phone. It is the same phone layout blown up across ten feet,
 * which is why reusing the phone's `displaySmall`/56.dp gutters felt enormous and
 * overlapped: the phone scale is tuned for a 6 inch screen held at 30 cm.
 *
 * These values assume a **1920x1080 screen at TV density** (the Android TV
 * reference device), where Compose reports a 960x536 dp window. Everything is
 * expressed as a fraction of that window so a 720p set or an overscan-limited
 * 4K one still lays out correctly: [TvDimens.of] derives them from the real
 * window size rather than trusting the nominal resolution.
 *
 * Deliberately *smaller* than the phone scale. On a 10-foot screen the eye
 * resolves far more detail, and the job of the layout is to fit a rail of
 * posters plus the hero plus the header on one screen without scrolling
 * fighting the viewer for attention.
 */
@Immutable
data class TvDimens(
    /** Horizontal and vertical safe inset, clear of overscan on every TV. */
    val gutter: Dp,
    val gutterTight: Dp,
    /** Gap between cards inside a rail. */
    val railGap: Dp,
    /** Gap between one rail (header + cards) and the next. */
    val rowGap: Dp,
    /** Height of the top bar that holds the menu and the title. */
    val topBarHeight: Dp,
    /** Poster/landscape card footprint. */
    val cardWidth: Dp,
    val cardImageHeight: Dp,
    val cardTitleHeight: Dp,
    /** Hero block above the first rail. */
    val heroHeight: Dp,
    /** Full-bleed player chrome rows. */
    val buttonHeight: Dp,
    val chipHeight: Dp,
    val focusRingWidth: Dp,
    val focusScale: Float,
    /** Minimum comfortable remote target: anything smaller is hard to hit. */
    val minTarget: Dp,
    /** Side-rail navigation column. */
    val navRailWidth: Dp,
    val navRailItemHeight: Dp,
    /** Secondary panel sliding in from the right (sources, settings, episodes). */
    val sidePanelWidth: Dp,
    val listRowHeight: Dp,
    val listRowGap: Dp,
    val paddingSmall: Dp,
    val paddingMedium: Dp,
    val paddingLarge: Dp
) {
    companion object {
        /**
         * The reference 10-foot canvas: 1920x1080 at TV density reports roughly
         * 960x536 dp. [widthDp]/[heightDp] are that window in dp.
         */
        fun of(widthDp: Int, heightDp: Int): TvDimens {
            // Scale against the reference window, but clamp: a 720p set and a
            // 4K one should differ only slightly, never become a different app.
            val scale = (widthDp / 960f).coerceIn(0.78f, 1.35f)
            // Short window (720p reports ~640x360 dp) must still fit a rail.
            val short = (heightDp / 536f).coerceIn(0.62f, 1.3f)
            val s = minOf(scale, short)

            fun v(base: Float): Dp = (base * s).dp

            return TvDimens(
                gutter = v(40f),
                gutterTight = v(24f),
                railGap = v(14f),
                rowGap = v(20f),
                topBarHeight = v(52f),
                cardWidth = v(210f),
                cardImageHeight = v(118f),
                cardTitleHeight = v(20f),
                heroHeight = v(216f),
                buttonHeight = v(44f),
                chipHeight = v(36f),
                focusRingWidth = v(3f),
                focusScale = 1.06f,
                minTarget = v(48f),
                navRailWidth = v(104f),
                navRailItemHeight = v(64f),
                sidePanelWidth = v(380f),
                listRowHeight = v(52f),
                listRowGap = v(8f),
                paddingSmall = v(8f),
                paddingMedium = v(16f),
                paddingLarge = v(24f)
            )
        }
    }
}

/** The active TV metrics. Always read sizes from here, never from a literal. */
val LocalTvDimens = staticCompositionLocalOf {
    TvDimens.of(960, 536)
}