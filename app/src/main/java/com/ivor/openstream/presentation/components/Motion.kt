package com.ivor.openstream.presentation.components

import android.provider.Settings
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.ui.unit.IntOffset
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Shared expressive-motion vocabulary. `OpenStreamTheme` opts into
 * `MotionScheme.expressive()`; hand-rolled animations should read their specs from here
 * instead of hard-coding `tween(...)` durations, and go instant when the user enabled
 * "Remove animations" (animator scale 0).
 */
object ExpressiveMotion {
    /** True when the system animator scale is 0: motion must be instant. */
    @Composable
    fun reducedMotion(): Boolean {
        val context = LocalContext.current
        return remember {
            runCatching {
                Settings.Global.getFloat(
                    context.contentResolver,
                    Settings.Global.ANIMATOR_DURATION_SCALE, 1f
                ) == 0f
            }.getOrDefault(false)
        }
    }

    /** Effect spec (fade, scale) from the theme, or instant when reduced motion. */
    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    fun <T> effectsSpec(): AnimationSpec<T> =
        if (reducedMotion()) snap() else MaterialTheme.motionScheme.defaultEffectsSpec()

    /** Spatial spec (slide, offset) from the theme, or instant when reduced motion. */
    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    fun <T> spatialSpec(): AnimationSpec<T> =
        if (reducedMotion()) snap() else MaterialTheme.motionScheme.defaultSpatialSpec()

    /** Fast effect spec for small feedback (ticks, icon morphs). */
    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    fun <T> fastEffectsSpec(): AnimationSpec<T> =
        if (reducedMotion()) snap() else MaterialTheme.motionScheme.fastEffectsSpec()

    /**
     * Effect spec as a [FiniteAnimationSpec] for enter/exit transitions, which don't accept
     * the general [AnimationSpec]. Falls back to the md.sys.motion.spring.default.effects
     * values (stiffness 1600, damping 1) if the scheme ever returns a non-finite spec.
     */
    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    fun finiteEffectsSpec(): FiniteAnimationSpec<Float> =
        if (reducedMotion()) snap() else
            (MaterialTheme.motionScheme.defaultEffectsSpec<Float>() as? FiniteAnimationSpec<Float>
                ?: spring(stiffness = 1600f, dampingRatio = 1f))

    /**
     * Spatial spec (slides) as a [FiniteAnimationSpec] for offset transitions. Falls back to
     * the md.sys.motion.spring.default.spatial values (stiffness 700, damping 0.9).
     */
    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    fun finiteSpatialSpec(): FiniteAnimationSpec<IntOffset> =
        if (reducedMotion()) snap() else
            (MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>() as? FiniteAnimationSpec<IntOffset>
                ?: spring(stiffness = 700f, dampingRatio = 0.9f))
}
