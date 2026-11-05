package io.github.rupakc.travelplanner.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import io.github.rupakc.travelplanner.core.model.section.SectionId

/**
 * One section's accent (web: 4 px left border `{c}-400`, icon `{c}-600`, tint `{c}-50`).
 * Dark theme: icon uses `{c}-300`, tint is the bar colour at low alpha.
 */
@Immutable
data class SectionAccent(val bar: Color, val icon: Color, val tint: Color)

/** Accent hues, exactly the web map (spec §5). */
enum class AccentHue(
    internal val c50: Color,
    internal val c300: Color,
    internal val c400: Color,
    internal val c600: Color,
) {
    SKY(Palette.Sky50, Palette.Sky300, Palette.Sky400, Palette.Sky600),
    VIOLET(Palette.Violet50, Palette.Violet300, Palette.Violet400, Palette.Violet600),
    EMERALD(Palette.Emerald50, Palette.Emerald300, Palette.Emerald400, Palette.Emerald600),
    LIME(Palette.Lime50, Palette.Lime300, Palette.Lime400, Palette.Lime600),
    TEAL(Palette.Teal50, Palette.Teal300, Palette.Teal400, Palette.Teal600),
    FUCHSIA(Palette.Fuchsia50, Palette.Fuchsia300, Palette.Fuchsia400, Palette.Fuchsia600),
    AMBER(Palette.Amber50, Palette.Amber300, Palette.Amber400, Palette.Amber600),
    ROSE(Palette.Rose50, Palette.Rose300, Palette.Rose400, Palette.Rose600),
    CYAN(Palette.Cyan50, Palette.Cyan300, Palette.Cyan400, Palette.Cyan600),
    RED(Palette.Red50, Palette.Red300, Palette.Red400, Palette.Red600),
    ;

    fun accent(dark: Boolean): SectionAccent =
        if (dark) SectionAccent(bar = c400, icon = c300, tint = c400.copy(alpha = DARK_TINT_ALPHA)) else SectionAccent(c400, c600, c50)

    private companion object {
        const val DARK_TINT_ALPHA = 0.12f
    }
}

/** Section → hue (spec §5). */
val SectionHues: Map<SectionId, AccentHue> = mapOf(
    SectionId.FLIGHTS to AccentHue.SKY,
    SectionId.WEATHER to AccentHue.SKY,
    SectionId.HOTELS to AccentHue.VIOLET,
    SectionId.ACTIVITIES to AccentHue.EMERALD,
    SectionId.PLACES_TO_SEE to AccentHue.LIME,
    SectionId.WALKING_TOURS to AccentHue.TEAL,
    SectionId.EVENTS to AccentHue.FUCHSIA,
    SectionId.VISA to AccentHue.AMBER,
    SectionId.SIM to AccentHue.ROSE,
    SectionId.TIPS to AccentHue.AMBER,
    SectionId.GETTING_AROUND to AccentHue.CYAN,
    SectionId.FOREX to AccentHue.EMERALD,
    SectionId.ITINERARY to AccentHue.TEAL,
    SectionId.STRESS_TEST to AccentHue.ROSE,
    SectionId.EMERGENCY_CARD to AccentHue.RED,
    SectionId.PACKING_LIST to AccentHue.TEAL,
)

/** All section accents for the current theme; read via `TravelTheme.sectionAccents[id]`. */
@Immutable
class SectionAccents internal constructor(private val dark: Boolean) {
    operator fun get(id: SectionId): SectionAccent = (SectionHues[id] ?: AccentHue.TEAL).accent(dark)
}

val LocalSectionAccents = staticCompositionLocalOf { SectionAccents(dark = false) }
