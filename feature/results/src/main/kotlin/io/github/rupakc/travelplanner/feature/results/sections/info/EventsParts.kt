package io.github.rupakc.travelplanner.feature.results.sections.info

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import io.github.rupakc.travelplanner.core.designsystem.component.EmptyState
import io.github.rupakc.travelplanner.core.designsystem.component.TpCard
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelShape
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.model.plan.PlanItem
import io.github.rupakc.travelplanner.core.model.section.EventsData
import io.github.rupakc.travelplanner.core.model.section.SectionId
import io.github.rupakc.travelplanner.core.model.section.TravelEvent
import io.github.rupakc.travelplanner.core.model.settings.ThemeMode
import io.github.rupakc.travelplanner.core.ui.AddToPlanButton
import io.github.rupakc.travelplanner.feature.results.R

/** Web EVENT_CATEGORY_STYLE emoji (unknown → "other" ✨). */
internal fun eventEmoji(category: String?): String =
    when (category) {
        "festival" -> "🎉"
        "music" -> "🎵"
        "exhibition" -> "🖼️"
        "sports" -> "🏟️"
        "market" -> "🛍️"
        "seasonal" -> "🌸"
        "holiday" -> "📅"
        else -> "✨"
    }

/** Web INTEREST_LABELS; unknown ids show as sent. */
@StringRes
internal fun interestLabel(id: String): Int? =
    when (id) {
        "food" -> R.string.interest_food
        "history" -> R.string.interest_history
        "adventure" -> R.string.interest_adventure
        "culture" -> R.string.interest_culture
        "nature" -> R.string.interest_nature
        "shopping" -> R.string.interest_shopping
        "nightlife" -> R.string.interest_nightlife
        "wellness" -> R.string.interest_wellness
        "art" -> R.string.interest_art
        "family" -> R.string.interest_family
        else -> null
    }

/** "May 3" or "May 3 – May 6" (web `formatEventDates`); null without a start date. */
@Composable
internal fun eventDates(start: String?, end: String?): String? {
    val locale = currentLocale()
    val s = shortDate(start, locale) ?: return null
    if (end.isNullOrBlank() || end == start) return s
    return stringResource(R.string.events_date_range, s, shortDate(end, locale).orEmpty())
}

@Composable
internal fun EventsBody(
    data: EventsData,
    isSelected: (PlanItem) -> Boolean,
    onToggle: (PlanItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (data.isError) {
        EmptyState(stringResource(R.string.events_error), modifier)
        return
    }
    if (data.results.isEmpty()) {
        EmptyState(stringResource(R.string.events_empty), modifier)
        return
    }
    val highlights = data.results.filter { it.impact != "consider" }
    val disruptions = data.results.filter { it.impact == "consider" }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.lg)) {
        AdaptiveGrid(highlights) { event, cell ->
            val item = PlanItem.EventItem(event)
            EventCard(event, isSelected(item), { onToggle(item) }, cell)
        }
        if (disruptions.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
                SubHeading(stringResource(R.string.events_plan_around), color = TravelTheme.extendedColors.onWarningContainer)
                disruptions.forEach { DisruptionRow(it) }
            }
        }
    }
}

@Composable
private fun EventCard(event: TravelEvent, selected: Boolean, onToggle: () -> Unit, modifier: Modifier) {
    val accent = TravelTheme.sectionAccents[SectionId.EVENTS]
    TpCard(modifier.fillMaxWidth(), selected = selected, accent = accent.bar) {
        Column(verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm), itemVerticalAlignment = Alignment.CenterVertically) {
                InfoPill("${eventEmoji(event.category)} ${event.category.orEmpty()}".trim(), tone = Tone.Accent, section = SectionId.EVENTS)
                EventDates(event, accent.icon)
            }
            Text(event.name.orEmpty(), style = TravelTheme.typography.titleMedium)
            event.description?.let { Text(it, style = TravelTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis) }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm),
                verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xs),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                CityChip(cityOf(event.raw))
                event.location?.takeIf { it.isNotBlank() }?.let { MetaText("📍 $it") }
                event.price?.takeIf { it.isNotBlank() }?.let { MetaText(it) }
                event.interestMatch.forEach { id ->
                    InfoPill(interestLabel(id)?.let { stringResource(it) } ?: id, tone = Tone.Accent, section = SectionId.ITINERARY)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { AddToPlanButton(selected, event.name.orEmpty(), onToggle) }
        }
    }
}

/** Dates with the "~" typical-season marker; TalkBack reads the explanation instead of "~". */
@Composable
private fun EventDates(event: TravelEvent, color: androidx.compose.ui.graphics.Color) {
    val dates = eventDates(event.startDate, event.endDate) ?: return
    val typical = event.dateCertainty == "typical_season"
    val a11y = if (typical) stringResource(R.string.events_dates_typical_a11y, dates) else dates
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clearAndSetSemantics { contentDescription = a11y }) {
        Icon(Icons.Filled.DateRange, contentDescription = null, tint = color, modifier = Modifier.size(InfoDimens.SmallIcon))
        Text(dates, style = TravelTheme.typography.labelMedium, color = color)
        if (typical) Text(" ~", style = TravelTheme.typography.labelMedium, color = TravelTheme.colors.onSurfaceVariant)
    }
}

@Composable
private fun DisruptionRow(event: TravelEvent) {
    val colors = toneColors(Tone.Warning)
    Row(
        Modifier.fillMaxWidth().background(colors.container, TravelShape.ItemCard).padding(TravelTheme.spacing.md),
        horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm),
    ) {
        Icon(Icons.Filled.Warning, contentDescription = null, tint = colors.content, modifier = Modifier.size(InfoDimens.SmallIcon))
        Column(Modifier.weight(1f)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
                Text(event.name.orEmpty(), style = TravelTheme.typography.titleSmall, color = colors.content)
                eventDates(event.startDate, event.endDate)?.let { Text(it, style = TravelTheme.typography.labelMedium, color = colors.content) }
            }
            event.description?.let { Text(it, style = TravelTheme.typography.bodySmall, color = colors.content) }
        }
    }
}

private val previewEvents =
    EventsData(
        results =
            listOf(
                TravelEvent(
                    name = "Festas de Lisboa",
                    category = "festival",
                    description = "Month-long street parties with grilled sardines and marchas.",
                    startDate = "2027-06-01",
                    endDate = "2027-06-30",
                    dateCertainty = "typical_season",
                    location = "Alfama",
                    price = "Free",
                    interestMatch = listOf("food", "culture"),
                ),
                TravelEvent(
                    name = "Marathon road closures",
                    impact = "consider",
                    startDate = "2027-06-12",
                    description = "Central avenues closed until 14:00.",
                ),
            ),
    )

@Preview(name = "Events – light")
@Composable
private fun EventsLightPreview() = PreviewSurface(ThemeMode.LIGHT) { EventsBody(previewEvents, { false }, {}) }

@Preview(name = "Events – dark")
@Composable
private fun EventsDarkPreview() = PreviewSurface(ThemeMode.DARK) { EventsBody(previewEvents, { true }, {}) }
