package com.ivor.openanime.presentation.player

import kotlinx.serialization.Serializable

/**
 * User-configurable caption (subtitle) appearance.
 *
 * Kept intentionally small ("basic" controls): text size and the opacity of the
 * background pill drawn behind the text. Persisted as JSON in SharedPreferences.
 */
@Serializable
data class CaptionStyleSettings(
    val textSizeSp: Float = DEFAULT_TEXT_SIZE_SP,
    val backgroundOpacity: Float = DEFAULT_BACKGROUND_OPACITY
) {
    companion object {
        const val DEFAULT_TEXT_SIZE_SP = 16f
        const val DEFAULT_BACKGROUND_OPACITY = 0.6f

        const val MIN_TEXT_SIZE_SP = 12f
        const val MAX_TEXT_SIZE_SP = 32f
    }
}
