package com.ivor.openstream.presentation.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.navigation.NavBackStackEntry

/**
 * Screen-level motion. Switching tabs is a lateral move between peers, so it uses a quick fade
 * through; opening a screen goes deeper, so it slides in from the end and back out on return.
 *
 * Durations track md.sys.motion.duration tokens (slide = medium3 350ms, fades = short4 200ms /
 * short2 100ms) with the emphasized easing. Pass animated = false when the user enabled
 * "Remove animations" (see ExpressiveMotion.reducedMotion): every transition becomes None.
 *
 * The player behaves like a sheet over the app: opened from the mini player it rises from the
 * bottom edge over a screen that stays put (emphasized decelerate, long1 450ms), and leaving it
 * always drops it back down (emphasized accelerate, medium1 250ms) so it lands
 * where the mini player reappears. Nothing underneath fades, which keeps the frame cost to one
 * translating layer while the player's video view takes over the surface.
 */
internal object NavTransitions {
    private val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    private const val SLIDE_MS = 350 // md.sys.motion.duration.medium3
    private const val FADE_IN_MS = 200 // md.sys.motion.duration.short4
    private const val FADE_OUT_MS = 100 // md.sys.motion.duration.short2
    private val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    private val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
    private const val SHEET_IN_MS = 450 // md.sys.motion.duration.long1
    private const val SHEET_OUT_MS = 250 // md.sys.motion.duration.medium1

    private val tabRoutes = setOf(
        Screen.Home.route,
        Screen.Search.route,
        Screen.WatchLater.route,
        Screen.Downloads.route,
        Screen.History.route
    )

    private fun AnimatedContentTransitionScope<NavBackStackEntry>.openingPlayer(): Boolean =
        targetState.destination.route == Screen.Player.route && initialState.destination.route != Screen.Player.route

    private fun AnimatedContentTransitionScope<NavBackStackEntry>.closingPlayer(): Boolean =
        initialState.destination.route == Screen.Player.route && targetState.destination.route != Screen.Player.route

    private fun AnimatedContentTransitionScope<NavBackStackEntry>.betweenTabs(): Boolean =
        initialState.destination.route in tabRoutes && targetState.destination.route in tabRoutes

    private val fadeThroughIn: EnterTransition =
        fadeIn(tween(FADE_IN_MS, delayMillis = FADE_OUT_MS)) +
            scaleIn(tween(FADE_IN_MS, delayMillis = FADE_OUT_MS), initialScale = 0.96f)
    private val fadeThroughOut: ExitTransition = fadeOut(tween(FADE_OUT_MS))

    fun AnimatedContentTransitionScope<NavBackStackEntry>.enter(
        animated: Boolean,
        playerFromMini: Boolean = false
    ): EnterTransition {
        if (!animated) return EnterTransition.None
        if (playerFromMini && openingPlayer()) {
            return slideInVertically(tween(SHEET_IN_MS, easing = EmphasizedDecelerate)) { it }
        }
        if (targetState.destination.route == Screen.Player.route) {
            return fadeIn(tween(FADE_IN_MS)) + scaleIn(tween(FADE_IN_MS, easing = Emphasized), initialScale = 0.98f)
        }
        return if (betweenTabs()) {
            fadeThroughIn
        } else {
            slideInHorizontally(tween(SLIDE_MS, easing = Emphasized)) { it / 4 } + fadeIn(tween(FADE_IN_MS))
        }
    }

    fun AnimatedContentTransitionScope<NavBackStackEntry>.exit(
        animated: Boolean,
        playerFromMini: Boolean = false
    ): ExitTransition {
        if (!animated) return ExitTransition.None
        // Stays still under the rising player instead of fading to the window background.
        if (playerFromMini && openingPlayer()) return ExitTransition.KeepUntilTransitionsFinished
        if (targetState.destination.route == Screen.Player.route) {
            return fadeOut(tween(FADE_OUT_MS))
        }
        return if (betweenTabs()) {
            fadeThroughOut
        } else {
            slideOutHorizontally(tween(SLIDE_MS, easing = Emphasized)) { -it / 10 } + fadeOut(tween(FADE_IN_MS))
        }
    }

    fun AnimatedContentTransitionScope<NavBackStackEntry>.popEnter(animated: Boolean): EnterTransition {
        if (!animated) return EnterTransition.None
        // Already in place under the player as it drops away.
        if (closingPlayer()) return EnterTransition.None
        return if (betweenTabs()) {
            fadeThroughIn
        } else {
            slideInHorizontally(tween(SLIDE_MS, easing = Emphasized)) { -it / 10 } + fadeIn(tween(FADE_IN_MS))
        }
    }

    fun AnimatedContentTransitionScope<NavBackStackEntry>.popExit(animated: Boolean): ExitTransition {
        if (!animated) return ExitTransition.None
        if (closingPlayer()) {
            return slideOutVertically(tween(SHEET_OUT_MS, easing = EmphasizedAccelerate)) { it }
        }
        return if (betweenTabs()) {
            fadeThroughOut
        } else {
            slideOutHorizontally(tween(SLIDE_MS, easing = Emphasized)) { it / 4 } + fadeOut(tween(FADE_IN_MS))
        }
    }
}
