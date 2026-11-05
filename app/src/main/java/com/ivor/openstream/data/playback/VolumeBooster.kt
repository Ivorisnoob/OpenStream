package com.ivor.openstream.data.playback

import android.media.audiofx.LoudnessEnhancer
import android.util.Log

/**
 * Manages psychoacoustic loudness boost for ExoPlayer audio sessions using Android's
 * hardware/framework [LoudnessEnhancer].
 *
 * Unlike linear PCM multipliers that cause digital clipping and audio distortion,
 * [LoudnessEnhancer] applies target gain with a psychoacoustic limiter to safely increase
 * perceived volume (e.g. for quiet dialogue or low-output device speakers).
 *
 * - 100% = Normal volume (0 dB boost, effect disabled)
 * - 200% = +6 dB boost (600 mB)
 * - 300% = +10 dB boost (1,000 mB)
 */
class VolumeBooster {
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var currentSessionId: Int = 0
    private var currentBoostPercent: Int = 100

    /**
     * Binds this booster to an active ExoPlayer audio session ID.
     * Safe to call multiple times or with session ID 0.
     */
    fun attachSession(audioSessionId: Int) {
        if (audioSessionId <= 0 || (audioSessionId == currentSessionId && loudnessEnhancer != null)) return
        currentSessionId = audioSessionId
        releaseEnhancer()
        runCatching {
            loudnessEnhancer = LoudnessEnhancer(audioSessionId).apply {
                applyGain(currentBoostPercent)
            }
        }.onFailure { error ->
            Log.w(TAG, "Failed to initialize LoudnessEnhancer for session $audioSessionId: ${error.message}")
        }
    }

    /**
     * Sets the volume boost percentage (clamped between 100% and 300%).
     */
    fun setBoostPercentage(percent: Int) {
        currentBoostPercent = percent.coerceIn(MIN_PERCENT, MAX_PERCENT)
        applyGain(currentBoostPercent)
    }

    private fun applyGain(percent: Int) {
        val enhancer = loudnessEnhancer ?: return
        if (percent <= MIN_PERCENT) {
            runCatching { enhancer.enabled = false }
            return
        }

        // Millibels: 100 mB = 1 dB.
        // 100%..200% maps to 0..600 mB (+6 dB).
        // 200%..300% maps to 600..1000 mB (+10 dB).
        val targetGainMb = when {
            percent <= 200 -> (percent - 100) * 6
            else -> 600 + (percent - 200) * 4
        }

        runCatching {
            enhancer.setTargetGain(targetGainMb)
            enhancer.enabled = true
        }.onFailure { error ->
            Log.w(TAG, "Failed to set LoudnessEnhancer gain to $targetGainMb mB: ${error.message}")
        }
    }

    /**
     * Releases system audio effect resources when playback ends.
     */
    fun release() {
        releaseEnhancer()
        currentSessionId = 0
    }

    private fun releaseEnhancer() {
        runCatching { loudnessEnhancer?.release() }
        loudnessEnhancer = null
    }

    companion object {
        private const val TAG = "VolumeBooster"
        const val MIN_PERCENT = 100
        const val MAX_PERCENT = 300

        /** Quick preset boost options displayed in player settings. */
        val BOOST_PRESETS = listOf(100, 125, 150, 200, 250, 300)
    }
}
