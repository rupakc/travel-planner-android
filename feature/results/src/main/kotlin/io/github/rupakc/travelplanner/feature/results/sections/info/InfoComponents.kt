package io.github.rupakc.travelplanner.feature.results.sections.info

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import io.github.rupakc.travelplanner.core.designsystem.component.TpButton
import io.github.rupakc.travelplanner.core.designsystem.component.TpButtonVariant
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelShape
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.model.section.SectionId
import io.github.rupakc.travelplanner.core.model.settings.ThemeMode
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Layout constants of the info sections (defined once, spec §5: no ad-hoc dp in feature code). */
internal object InfoDimens {
    val WeatherCardWidth = 120.dp
    val TwoColumnMinWidth = 600.dp
    val TypeTagMinWidth = 52.dp
    val TourImageHeight = 140.dp
    val ScoreRing = 72.dp
    val ScoreStroke = 7.dp
    val SmallIcon = 16.dp
    val NumberTileMinHeight = 72.dp
    val Border = 1.dp
}

/**
 * Semantic tone of a pill/callout ([Violet] = the web's purple chips: Tourist Police, Accessible, ATM);
 * colour is never the only signal (each use carries text).
 */
internal enum class Tone { Neutral, Info, Success, Warning, Danger, Accent, Violet }

@Immutable
internal data class ToneColors(val container: Color, val content: Color)

@Composable
internal fun toneColors(tone: Tone, section: SectionId? = null): ToneColors {
    val ext = TravelTheme.extendedColors
    val c = TravelTheme.colors
    return when (tone) {
        Tone.Neutral -> {
            ToneColors(c.surfaceContainerHigh, c.onSurfaceVariant)
        }

        Tone.Info -> {
            ToneColors(ext.infoContainer, ext.info)
        }

        Tone.Success -> {
            ToneColors(ext.successContainer, ext.onSuccessContainer)
        }

        Tone.Warning -> {
            ToneColors(ext.warningContainer, ext.onWarningContainer)
        }

        Tone.Danger -> {
            ToneColors(c.errorContainer, c.onErrorContainer)
        }

        Tone.Violet -> {
            TravelTheme.sectionAccents[SectionId.HOTELS].let { ToneColors(it.tint, it.icon) }
        }

        Tone.Accent -> {
            section?.let { TravelTheme.sectionAccents[it] }?.let { ToneColors(it.tint, it.icon) }
                ?: ToneColors(c.primaryContainer, c.onPrimaryContainer)
        }
    }
}

/** Small rounded label (category, rating, severity…). */
@Composable
internal fun InfoPill(text: String, modifier: Modifier = Modifier, tone: Tone = Tone.Neutral, section: SectionId? = null) {
    val colors = toneColors(tone, section)
    Text(
        text,
        style = TravelTheme.typography.labelMedium,
        color = colors.content,
        modifier = modifier.background(colors.container, TravelShape.Pill).padding(horizontal = TravelTheme.spacing.sm, vertical = TravelTheme.spacing.xxs),
    )
}

/** Tinted message box (web info/amber/red boxes). */
@Composable
internal fun Callout(text: String, modifier: Modifier = Modifier, tone: Tone = Tone.Info, icon: ImageVector? = null) {
    val colors = toneColors(tone)
    Row(
        modifier.fillMaxWidth().background(colors.container, TravelShape.Input).padding(TravelTheme.spacing.md),
        horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = colors.content, modifier = Modifier.size(InfoDimens.SmallIcon))
        Text(text, style = TravelTheme.typography.bodySmall, color = colors.content, modifier = Modifier.weight(1f))
    }
}

/** A sub-heading inside a section (semantic heading for TalkBack navigation). */
@Composable
internal fun SubHeading(text: String, modifier: Modifier = Modifier, color: Color = TravelTheme.colors.onSurface) {
    Text(text, style = TravelTheme.typography.titleSmall, color = color, modifier = modifier.semantics { heading() })
}

/** "Label: value" line (Coverage:, Price:, Fees:…). Hidden when [value] is blank. */
@Composable
internal fun LabeledLine(label: String, value: String?, modifier: Modifier = Modifier, valueColor: Color = TravelTheme.colors.onSurfaceVariant) {
    if (value.isNullOrBlank()) return
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = TravelTheme.colors.onSurface)) { append(label) }
            append(" ")
            append(value)
        },
        style = TravelTheme.typography.bodySmall,
        color = valueColor,
        modifier = modifier,
    )
}

/** City chip (web CityChip): first part of the name, multi-city arrays joined with " · ". */
@Composable
internal fun CityChip(city: String?, modifier: Modifier = Modifier) {
    if (city.isNullOrBlank()) return
    Row(
        modifier.background(
            TravelTheme.colors.secondaryContainer,
            TravelShape.Pill,
        ).padding(horizontal = TravelTheme.spacing.sm, vertical = TravelTheme.spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Place, contentDescription = null, tint = TravelTheme.colors.onSecondaryContainer, modifier = Modifier.size(InfoDimens.SmallIcon))
        Text(city, style = TravelTheme.typography.labelMedium, color = TravelTheme.colors.onSecondaryContainer)
    }
}

/** Opens [url] through [onOpen] (Custom Tab via SectionActions) — only for http(s) links; hidden otherwise. */
@Composable
internal fun LinkButton(label: String, url: String?, onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
    val safe = safeHttpUrl(url) ?: return
    TpButton(label, onClick = { onOpen(safe) }, modifier = modifier, variant = TpButtonVariant.Text, leadingIcon = Icons.AutoMirrored.Filled.ExitToApp)
}

/**
 * Lays [items] out in one column on compact widths and two from [InfoDimens.TwoColumnMinWidth] (web `md:grid-cols-2`).
 * Each cell gets a `cellModifier` carrying its share of the row width.
 */
@Composable
internal fun <T> AdaptiveGrid(items: List<T>, modifier: Modifier = Modifier, content: @Composable (item: T, cellModifier: Modifier) -> Unit) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val columns = if (maxWidth >= InfoDimens.TwoColumnMinWidth) 2 else 1
        Column(verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.md)) {
            items.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.md)) {
                    row.forEach { content(it, Modifier.weight(1f)) }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** First part of a place name ("Lisbon, Portugal" → "Lisbon"), web `cityName`. */
internal fun cityName(city: String?): String = city.orEmpty().substringBefore(',').trim()

/** An item's `city` from its original JSON: string or array (multi-city), web CityChip rules. */
internal fun cityOf(raw: JsonObject): String? =
    when (val c: JsonElement? = raw["city"]) {
        is JsonPrimitive -> {
            c.takeIf { it.isString }?.content?.let(::cityName)?.takeIf { it.isNotEmpty() }
        }

        is JsonArray -> {
            c.mapNotNull {
                (it as? JsonPrimitive)?.takeIf { p ->
                    p.isString
                }?.content?.let(::cityName)
            }.filter { it.isNotEmpty() }.joinToString(" · ").ifEmpty { null }
        }

        else -> {
            null
        }
    }

/** http(s) URLs only (web `safeUrl`); anything else is dropped rather than opened. */
internal fun safeHttpUrl(url: String?): String? {
    val u = url?.trim().orEmpty()
    return u.takeIf { it.startsWith("https://", ignoreCase = true) || it.startsWith("http://", ignoreCase = true) }
}

/** Numbers like the web prints them: 4.0 → "4", 4.5 → "4.5". */
internal fun Double.plain(): String = if (this == Math.floor(this) && !isInfinite()) toLong().toString() else toString()

/** Preview frame: theme + background + section padding. */
@Composable
internal fun PreviewSurface(mode: ThemeMode, content: @Composable () -> Unit) {
    TravelTheme(themeMode = mode) {
        Surface(color = TravelTheme.colors.surface) { Box(Modifier.padding(TravelTheme.spacing.lg)) { content() } }
    }
}

/** The UI locale, observed (recomposes on a locale change). */
@Composable
@ReadOnlyComposable
internal fun currentLocale(): java.util.Locale = LocalConfiguration.current.locales[0]
