package com.ivor.openstream.data.settings

import androidx.annotation.StringRes
import com.ivor.openstream.R
import kotlinx.serialization.Serializable

/**
 * User-configurable caption (subtitle) appearance.
 *
 * Kept intentionally small ("basic" controls): text size, the opacity of the background pill
 * drawn behind the text, its colour and how high it sits. Persisted as JSON in SharedPreferences.
 */
@Serializable
data class CaptionStyleSettings(
    val textSizeSp: Float = DEFAULT_TEXT_SIZE_SP,
    val backgroundOpacity: Float = DEFAULT_BACKGROUND_OPACITY,
    /** The text's own colour; the plate behind it is what keeps either one readable. */
    val textColor: CaptionTextColor = CaptionTextColor.WHITE,
    /**
     * How far captions are raised above their default spot, as a fraction of the player's height.
     * 0 keeps the default position; raising them clears subtitles burnt into the video.
     */
    val verticalOffset: Float = DEFAULT_VERTICAL_OFFSET
) {
    companion object {
        const val DEFAULT_TEXT_SIZE_SP = 16f
        const val DEFAULT_BACKGROUND_OPACITY = 0.6f

        const val MIN_TEXT_SIZE_SP = 12f
        const val MAX_TEXT_SIZE_SP = 32f

        const val DEFAULT_VERTICAL_OFFSET = 0f
        const val MAX_VERTICAL_OFFSET = 0.4f

        /** Slider stops between 0 and the maximum: 0%, 5%, ... 40%. */
        const val VERTICAL_OFFSET_STEPS = 7
    }
}

/**
 * Video-safe caption foregrounds, paired with the background plate.
 *
 * White is the neutral default; yellow is the colour broadcast captions have always used, and it
 * holds up better over a bright picture, which is what the plate is there to fix.
 */
@Serializable
enum class CaptionTextColor(val argb: Int, @StringRes val labelRes: Int) {
    WHITE(0xFFFFFFFF.toInt(), R.string.caption_color_white),
    YELLOW(0xFFFFEB3B.toInt(), R.string.caption_color_yellow)
}
