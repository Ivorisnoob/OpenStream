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
import androidx.navigation.NavBackStackEntry

/**
 * Screen-level motion. Switching tabs is a lateral move between peers, so it uses a quick fade
 * through; opening a screen goes deeper, so it slides in from the end and back out on return.
 *
 * Durations track md.sys.motion.duration tokens (slide = medium3 350ms, fades = short4 200ms /
 * short2 100ms) with the emphasized easing. Pass animated = false when the user enabled
 * "Remove animations" (see ExpressiveMotion.reducedMotion): every transition becomes None.
 */
internal object NavTransitions {
    private val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    private const val SLIDE_MS = 350 // md.sys.motion.duration.medium3
    private const val FADE_IN_MS = 200 // md.sys.motion.duration.short4
    private const val FADE_OUT_MS = 100 // md.sys.motion.duration.short2

    private val tabRoutes = setOf(
        Screen.Home.route,
        Screen.Search.route,
        Screen.WatchLater.route,
        Screen.Downloads.route,
        Screen.History.route
    )

    private fun AnimatedContentTransitionScope<NavBackStackEntry>.betweenTabs(): Boolean =
        initialState.destination.route in tabRoutes && targetState.destination.route in tabRoutes

    private val fadeThroughIn: EnterTransition =
        fadeIn(tween(FADE_IN_MS, delayMillis = FADE_OUT_MS)) +
            scaleIn(tween(FADE_IN_MS, delayMillis = FADE_OUT_MS), initialScale = 0.96f)
    private val fadeThroughOut: ExitTransition = fadeOut(tween(FADE_OUT_MS))

    fun AnimatedContentTransitionScope<NavBackStackEntry>.enter(animated: Boolean): EnterTransition {
        if (!animated) return EnterTransition.None
        return if (betweenTabs()) {
            fadeThroughIn
        } else {
            slideInHorizontally(tween(SLIDE_MS, easing = Emphasized)) { it / 4 } + fadeIn(tween(FADE_IN_MS))
        }
    }

    fun AnimatedContentTransitionScope<NavBackStackEntry>.exit(animated: Boolean): ExitTransition {
        if (!animated) return ExitTransition.None
        return if (betweenTabs()) {
            fadeThroughOut
        } else {
            slideOutHorizontally(tween(SLIDE_MS, easing = Emphasized)) { -it / 10 } + fadeOut(tween(FADE_IN_MS))
        }
    }

    fun AnimatedContentTransitionScope<NavBackStackEntry>.popEnter(animated: Boolean): EnterTransition {
        if (!animated) return EnterTransition.None
        return if (betweenTabs()) {
            fadeThroughIn
        } else {
            slideInHorizontally(tween(SLIDE_MS, easing = Emphasized)) { -it / 10 } + fadeIn(tween(FADE_IN_MS))
        }
    }

    fun AnimatedContentTransitionScope<NavBackStackEntry>.popExit(animated: Boolean): ExitTransition {
        if (!animated) return ExitTransition.None
        return if (betweenTabs()) {
            fadeThroughOut
        } else {
            slideOutHorizontally(tween(SLIDE_MS, easing = Emphasized)) { it / 4 } + fadeOut(tween(FADE_IN_MS))
        }
    }
}
