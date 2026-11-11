package io.github.rupakc.travelplanner.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WCAG 2.2 contrast of every token pair the app draws text or a component boundary with, in both schemes.
 * Text needs 4.5:1 (SC 1.4.3); input borders and meaningful icons need 3:1 (SC 1.4.11).
 * docs/Accessibility.md quotes this test: keep the two in step.
 */
class ContrastTest {
    private data class Pair(val name: String, val fg: Color, val bg: Color, val min: Double)

    private fun ratio(a: Color, b: Color): Double {
        val la = a.luminance() + 0.05
        val lb = b.luminance() + 0.05
        return (maxOf(la, lb) / minOf(la, lb)).toDouble()
    }

    private fun textPairs(s: ColorScheme, x: ExtendedColors): List<Pair> {
        val surfaces = listOf("surface" to s.surface, "background" to s.background, "surfaceContainerLow" to s.surfaceContainerLow)

        fun onSurfaces(name: String, fg: Color, min: Double = TEXT) = surfaces.map { (n, bg) -> Pair("$name on $n", fg, bg, min) }
        return listOf(
            Pair("onPrimary on primary", s.onPrimary, s.primary, TEXT),
            Pair("onSecondary on secondary", s.onSecondary, s.secondary, TEXT),
            Pair("onTertiary on tertiary", s.onTertiary, s.tertiary, TEXT),
            // Navigation bar / rail container (M3 NavigationBar uses surfaceContainer).
            Pair("onSurface on surfaceContainer", s.onSurface, s.surfaceContainer, TEXT),
            Pair("onSurfaceVariant on surfaceContainer", s.onSurfaceVariant, s.surfaceContainer, TEXT),
            Pair("onSecondaryContainer on secondaryContainer (nav indicator)", s.onSecondaryContainer, s.secondaryContainer, TEXT),
            Pair("onPrimaryContainer on primaryContainer", s.onPrimaryContainer, s.primaryContainer, TEXT),
            Pair("onSecondaryContainer on secondaryContainer", s.onSecondaryContainer, s.secondaryContainer, TEXT),
            Pair("onTertiaryContainer on tertiaryContainer", s.onTertiaryContainer, s.tertiaryContainer, TEXT),
            Pair("onError on error", s.onError, s.error, TEXT),
            Pair("onErrorContainer on errorContainer", s.onErrorContainer, s.errorContainer, TEXT),
            Pair("onSurface on surfaceVariant", s.onSurface, s.surfaceVariant, TEXT),
            Pair("onSurfaceVariant on surfaceVariant", s.onSurfaceVariant, s.surfaceVariant, TEXT),
            Pair("onSurfaceVariant on surfaceContainerHighest", s.onSurfaceVariant, s.surfaceContainerHighest, TEXT),
            Pair("primary on primaryContainer", s.primary, s.primaryContainer, TEXT),
            Pair("inverseOnSurface on inverseSurface", s.inverseOnSurface, s.inverseSurface, TEXT),
            Pair("success on successContainer", x.success, x.successContainer, TEXT),
            Pair("onSuccessContainer on successContainer", x.onSuccessContainer, x.successContainer, TEXT),
            Pair("warning on warningContainer", x.warning, x.warningContainer, TEXT),
            Pair("onWarningContainer on warningContainer", x.onWarningContainer, x.warningContainer, TEXT),
            Pair("info on infoContainer", x.info, x.infoContainer, TEXT),
            Pair("outline on surfaceVariant", s.outline, s.surfaceVariant, NON_TEXT),
            Pair("outline on surfaceContainerHighest", s.outline, s.surfaceContainerHighest, NON_TEXT),
        ) +
            onSurfaces("onSurface", s.onSurface) +
            onSurfaces("onBackground", s.onBackground) +
            onSurfaces("onSurfaceVariant", s.onSurfaceVariant) +
            onSurfaces("primary", s.primary) +
            onSurfaces("secondary", s.secondary) +
            onSurfaces("tertiary", s.tertiary) +
            onSurfaces("error", s.error) +
            onSurfaces("success", x.success) +
            onSurfaces("warning", x.warning) +
            onSurfaces("info", x.info) +
            onSurfaces("outline (input border)", s.outline, NON_TEXT)
    }

    private fun accentPairs(dark: Boolean, surface: Color): List<Pair> =
        AccentHue.entries.flatMap { hue ->
            val a = hue.accent(dark)
            listOf(
                Pair("${hue.name} accent text on surface", a.text, surface, TEXT),
                Pair("${hue.name} accent text on its tint", a.text, a.tint.compositeOver(surface), TEXT),
                Pair("${hue.name} accent icon on surface", a.icon, surface, NON_TEXT),
            )
        }

    private fun assertAll(pairs: List<Pair>) {
        val failures = pairs.map { it to ratio(it.fg, it.bg) }.filter { (p, r) -> r < p.min }
        assertTrue(
            failures.joinToString("\n") { (p, r) -> "${p.name}: %.2f < %.1f".format(r, p.min) },
            failures.isEmpty(),
        )
    }

    @Test fun lightSchemeMeetsWcagAa() = assertAll(textPairs(LightColors, LightExtendedColors) + accentPairs(false, LightColors.surface))

    @Test fun darkSchemeMeetsWcagAa() = assertAll(textPairs(DarkColors, DarkExtendedColors) + accentPairs(true, DarkColors.surface))

    @Test fun rulingTokens() {
        // R4 ruling: filled primary = teal-700 (#0F766E, the web's hover shade), success = emerald-700.
        assertTrue(LightColors.primary == Color(0xFF0F766E))
        assertTrue(LightExtendedColors.success == Color(0xFF047857))
    }

    private companion object {
        const val TEXT = 4.5
        const val NON_TEXT = 3.0
    }
}
