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
 */
internal object NavTransitions {
    private val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    private const val SLIDE_MS = 350
    private const val FADE_IN_MS = 210
    private const val FADE_OUT_MS = 90

    private val tabRoutes = setOf("home", "search", "watch_later", "downloads", "history")

    private fun AnimatedContentTransitionScope<NavBackStackEntry>.betweenTabs(): Boolean =
        initialState.destination.route in tabRoutes && targetState.destination.route in tabRoutes

    private val fadeThroughIn: EnterTransition =
        fadeIn(tween(FADE_IN_MS, delayMillis = FADE_OUT_MS)) +
            scaleIn(tween(FADE_IN_MS, delayMillis = FADE_OUT_MS), initialScale = 0.96f)
    private val fadeThroughOut: ExitTransition = fadeOut(tween(FADE_OUT_MS))

    val enter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        if (betweenTabs()) {
            fadeThroughIn
        } else {
            slideInHorizontally(tween(SLIDE_MS, easing = Emphasized)) { it / 4 } + fadeIn(tween(FADE_IN_MS))
        }
    }

    val exit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        if (betweenTabs()) {
            fadeThroughOut
        } else {
            slideOutHorizontally(tween(SLIDE_MS, easing = Emphasized)) { -it / 10 } + fadeOut(tween(FADE_IN_MS))
        }
    }

    val popEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        if (betweenTabs()) {
            fadeThroughIn
        } else {
            slideInHorizontally(tween(SLIDE_MS, easing = Emphasized)) { -it / 10 } + fadeIn(tween(FADE_IN_MS))
        }
    }

    val popExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        if (betweenTabs()) {
            fadeThroughOut
        } else {
            slideOutHorizontally(tween(SLIDE_MS, easing = Emphasized)) { it / 4 } + fadeOut(tween(FADE_OUT_MS * 2))
        }
    }
}
