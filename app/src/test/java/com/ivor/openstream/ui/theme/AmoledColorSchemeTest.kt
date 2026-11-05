package com.ivor.openstream.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/**
 * AMOLED is only worth having if it is actually black and still readable. These checks pin the
 * two things that regress first: a "true black" layer that quietly keeps a gray background, and
 * light text roles that stop clearing WCAG AA once the surfaces under them turn black.
 */
class AmoledColorSchemeTest {

    private val base = brandDarkColorScheme()
    private val scheme = base.withAmoledSurfaces()

    @Test
    fun pageAndLowestSurfaceAreTrueBlack() {
        listOf(
            scheme.background,
            scheme.surface,
            scheme.surfaceDim,
            scheme.surfaceContainerLowest
        ).forEach { assertEquals(Color.Black, it) }
    }

    @Test
    fun raisedSurfacesStepUpWithoutReachingGray() {
        val layers = listOf(
            scheme.surfaceContainerLowest,
            scheme.surfaceContainerLow,
            scheme.surfaceContainer,
            scheme.surfaceContainerHigh,
            scheme.surfaceContainerHighest,
            scheme.surfaceBright
        )
        layers.zipWithNext().forEach { (lower, higher) ->
            assertTrue(
                "Each raised surface must be brighter than the one below it",
                luminance(higher) > luminance(lower)
            )
        }
        assertTrue(
            "Even the brightest surface should still read as black",
            luminance(layers.last()) < 0.03
        )
    }

    @Test
    fun neutralTextClearsAaOnEverySurface() {
        val surfaces = listOf(
            scheme.surface,
            scheme.surfaceContainerLowest,
            scheme.surfaceContainerLow,
            scheme.surfaceContainer,
            scheme.surfaceContainerHigh,
            scheme.surfaceContainerHighest,
            scheme.surfaceBright,
            scheme.surfaceVariant
        )
        surfaces.forEach { surface ->
            assertContrast(scheme.onSurface, surface, "onSurface")
            assertContrast(scheme.onSurfaceVariant, surface, "onSurfaceVariant")
        }
        assertContrast(scheme.outline, scheme.surfaceContainerHighest, "outline")
    }

    @Test
    fun accentPairsStayReadable() {
        assertContrast(scheme.primary, scheme.surface, "primary")
        assertContrast(scheme.error, scheme.surface, "error")
        assertContrast(scheme.onPrimary, scheme.primary, "onPrimary")
        assertContrast(scheme.onSecondary, scheme.secondary, "onSecondary")
        assertContrast(scheme.onTertiary, scheme.tertiary, "onTertiary")
        assertContrast(scheme.onError, scheme.error, "onError")
        assertContrast(scheme.onPrimaryContainer, scheme.primaryContainer, "onPrimaryContainer")
        assertContrast(scheme.onSecondaryContainer, scheme.secondaryContainer, "onSecondaryContainer")
        assertContrast(scheme.onTertiaryContainer, scheme.tertiaryContainer, "onTertiaryContainer")
        assertContrast(scheme.onErrorContainer, scheme.errorContainer, "onErrorContainer")
        assertContrast(scheme.inverseOnSurface, scheme.inverseSurface, "inverseOnSurface")
    }

    @Test
    fun keepsTheAccentColorsOfTheBaseScheme() {
        assertEquals(base.primary, scheme.primary)
        assertEquals(base.secondaryContainer, scheme.secondaryContainer)
        assertEquals(base.tertiary, scheme.tertiary)
        assertEquals(base.error, scheme.error)
    }

    private fun assertContrast(foreground: Color, background: Color, role: String) {
        val ratio = contrastRatio(foreground, background)
        assertTrue("$role contrast is %.2f:1, below 4.5:1".format(ratio), ratio >= 4.5)
    }

    private fun contrastRatio(a: Color, b: Color): Double {
        val lighter = maxOf(luminance(a), luminance(b))
        val darker = minOf(luminance(a), luminance(b))
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun luminance(color: Color): Double {
        fun channel(value: Float): Double {
            val v = value.toDouble()
            return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(color.red) +
            0.7152 * channel(color.green) +
            0.0722 * channel(color.blue)
    }
}
