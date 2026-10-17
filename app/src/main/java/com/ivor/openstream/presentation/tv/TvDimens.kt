package com.ivor.openstream.presentation.tv

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Every TV size, in one place.
 *
 * A TV is not a big phone. The phone scale is tuned for a 6 inch panel held at 30 cm; blown up
 * across ten feet it is oversized and its fixed dp literals collide. These values assume the
 * Android TV reference canvas (a 1080p set at TV density reports a 960x540 dp window) and derive
 * every size from the real window instead of the nominal resolution, so a 720p set and an
 * overscan-limited 4K one lay out the same way.
 *
 * The horizontal maths is what makes a rail read as a rail: the gutter is exactly 5% of the
 * window on each side (the overscan-safe band), and a card is sized so that **six** portrait
 * posters (or **four** landscape cards) fit inside that band with the next card peeking in from
 * the edge. That peek is the affordance that tells the viewer there is more to the right.
 *
 * Nothing on a TV screen should ever write a raw `dp` or `sp` literal; screens read these fields.
 * Type sizes live in [TvTheme]; shape radii come from the M3 Expressive shape scale.
 */
@Immutable
data class TvDimens(
    val scale: Float,
    val gutter: Dp,
    val gutterVertical: Dp,
    val gutterTight: Dp,
    val railGap: Dp,
    val rowGap: Dp,
    val topBarHeight: Dp,
    /** Portrait poster card (2:3). Six fit in the rails. */
    val posterCardWidth: Dp,
    val posterCardHeight: Dp,
    /** Landscape card (16:9). Four fit in the rails. */
    val landscapeCardWidth: Dp,
    val landscapeCardHeight: Dp,
    val cardTitleHeight: Dp,
    /** Home hero: a fraction of the window rather than a fixed block. */
    val heroHeight: Dp,
    /** Details backdrop: shorter than the home hero, because it scrolls. */
    val backdropHeroHeight: Dp,
    val buttonHeight: Dp,
    val chipHeight: Dp,
    val focusRingWidth: Dp,
    val focusScale: Float,
    val minTarget: Dp,
    val navRailWidth: Dp,
    val navRailItemHeight: Dp,
    val sidePanelWidth: Dp,
    val listRowHeight: Dp,
    val listRowGap: Dp,
    val paddingSmall: Dp,
    val paddingMedium: Dp,
    val paddingLarge: Dp,
    val cardElevation: Dp,
    /** On-screen D-pad keyboard key box. */
    val keyboardKeyWidth: Dp,
    val keyboardKeyHeight: Dp,
    val progressBarHeight: Dp,
    val avatarSize: Dp,
    val dividerThickness: Dp,
    /** Episode list thumbnails (16:9) in the details rows. */
    val episodeThumbWidth: Dp,
    val episodeThumbHeight: Dp
) {
    companion object {
        /** How many full cards a rail shows before the next one peeks in. */
        const val POSTERS_PER_RAIL = 6
        const val LANDSCAPE_PER_RAIL = 4

        /** The peek: the fraction of a card that stays visible past the last full one. */
        private const val PEEK = 0.4f

        fun of(widthDp: Int, heightDp: Int): TvDimens {
            val scale = (widthDp / 960f).coerceIn(0.78f, 1.35f)
            val short = (heightDp / 540f).coerceIn(0.62f, 1.3f)
            val s = minOf(scale, short)

            fun v(base: Float): Dp = (base * s).dp

            // 5% of the window on each side is the overscan-safe band.
            val gutter = (widthDp * 0.05f).dp
            val railGap = v(12f)
            val safeWidth = widthDp.dp - gutter * 2
            // n full cards, n gaps (the last one sits before the peek) and the peek itself.
            val posterCardWidth = (safeWidth - railGap * POSTERS_PER_RAIL) / (POSTERS_PER_RAIL + PEEK)
            val landscapeCardWidth = (safeWidth - railGap * LANDSCAPE_PER_RAIL) / (LANDSCAPE_PER_RAIL + PEEK)
            val listRowHeight = v(64f)

            return TvDimens(
                scale = s,
                gutter = gutter,
                gutterVertical = (heightDp * 0.05f).dp,
                gutterTight = v(24f),
                railGap = railGap,
                rowGap = v(24f),
                topBarHeight = v(56f),
                posterCardWidth = posterCardWidth,
                posterCardHeight = posterCardWidth * 3 / 2,
                landscapeCardWidth = landscapeCardWidth,
                landscapeCardHeight = landscapeCardWidth * 9 / 16,
                cardTitleHeight = v(24f),
                heroHeight = (heightDp * 0.56f).dp,
                backdropHeroHeight = (heightDp * 0.42f).dp,
                buttonHeight = v(48f),
                chipHeight = v(40f),
                focusRingWidth = v(3f),
                focusScale = 1.09f,
                minTarget = v(48f),
                navRailWidth = v(104f),
                navRailItemHeight = v(64f),
                sidePanelWidth = v(420f),
                listRowHeight = listRowHeight,
                listRowGap = v(10f),
                paddingSmall = v(8f),
                paddingMedium = v(16f),
                paddingLarge = v(24f),
                cardElevation = v(16f),
                keyboardKeyWidth = v(64f),
                keyboardKeyHeight = v(52f),
                progressBarHeight = v(4f),
                avatarSize = v(72f),
                dividerThickness = v(1f),
                episodeThumbWidth = listRowHeight * 16 / 9,
                episodeThumbHeight = listRowHeight
            )
        }
    }
}

val LocalTvDimens = staticCompositionLocalOf { TvDimens.of(960, 540) }
