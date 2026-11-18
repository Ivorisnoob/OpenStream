package com.ivor.openstream.data.settings

import androidx.annotation.StringRes
import com.ivor.openstream.R
import kotlinx.serialization.Serializable

/**
 * User-configurable caption (subtitle) appearance: text size, colour, font, weight and edge, the
 * opacity of the plate drawn behind the text, and how high or low it sits. Persisted as JSON in
 * SharedPreferences; fields added later have defaults, so older saved styles still load.
 */
@Serializable
data class CaptionStyleSettings(
    val textSizeSp: Float = DEFAULT_TEXT_SIZE_SP,
    val backgroundOpacity: Float = DEFAULT_BACKGROUND_OPACITY,
    /** The text's own colour; the plate behind it is what keeps either one readable. */
    val textColor: CaptionTextColor = CaptionTextColor.WHITE,
    /**
     * How far captions move from their default spot, as a fraction of the player's height.
     * Positive raises them (clearing subtitles burnt into the video), negative lowers them toward
     * the bottom edge. 0 keeps the default position.
     */
    val verticalOffset: Float = DEFAULT_VERTICAL_OFFSET,
    val edge: CaptionEdge = CaptionEdge.SHADOW,
    val font: CaptionFont = CaptionFont.DEFAULT,
    val bold: Boolean = true
) {
    companion object {
        const val DEFAULT_TEXT_SIZE_SP = 16f
        const val DEFAULT_BACKGROUND_OPACITY = 0.6f

        const val MIN_TEXT_SIZE_SP = 12f
        const val MAX_TEXT_SIZE_SP = 32f

        const val DEFAULT_VERTICAL_OFFSET = 0f
        const val MIN_VERTICAL_OFFSET = -0.1f
        const val MAX_VERTICAL_OFFSET = 0.4f

        /** Slider stops between the minimum and the maximum: -10%, -5%, 0%, 5%, ... 40%. */
        const val VERTICAL_OFFSET_STEPS = 9
    }
}

/**
 * Video-safe caption foregrounds, paired with the background plate: the colours broadcast
 * captions have always used. White is the neutral default; yellow holds up best over a bright
 * picture.
 */
@Serializable
enum class CaptionTextColor(val argb: Int, @StringRes val labelRes: Int) {
    WHITE(0xFFFFFFFF.toInt(), R.string.caption_color_white),
    YELLOW(0xFFFFEB3B.toInt(), R.string.caption_color_yellow),
    CYAN(0xFF4DD0E1.toInt(), R.string.caption_color_cyan),
    GREEN(0xFF69F0AE.toInt(), R.string.caption_color_green)
}

/** What surrounds each letter. An outline keeps captions readable with the plate turned off. */
@Serializable
enum class CaptionEdge(@StringRes val labelRes: Int) {
    SHADOW(R.string.caption_edge_shadow),
    OUTLINE(R.string.caption_edge_outline),
    NONE(R.string.caption_edge_none)
}

@Serializable
enum class CaptionFont(@StringRes val labelRes: Int) {
    DEFAULT(R.string.caption_font_default),
    SERIF(R.string.caption_font_serif),
    MONOSPACE(R.string.caption_font_monospace)
}
