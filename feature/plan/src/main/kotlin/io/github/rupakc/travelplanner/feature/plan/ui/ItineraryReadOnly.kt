package io.github.rupakc.travelplanner.feature.plan.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import io.github.rupakc.travelplanner.core.designsystem.component.TpCard
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.ui.PriceText
import io.github.rupakc.travelplanner.feature.plan.R
import io.github.rupakc.travelplanner.feature.plan.model.ItineraryDayUi
import io.github.rupakc.travelplanner.feature.plan.model.ItinerarySlotUi

// TODO(integration): replace with core/ui itinerary composables (Team E creates `ItineraryDayCard` and
// `ItinerarySlotRow` in core/ui/.../itinerary/). Same names so the swap is an import change plus the
// model mapping; these render the arranged, scheduled itinerary read-only.

/** Width of the time column (layout constant, defined once). */
private val TimeColumnWidth = 96.dp

/** "Full Itinerary (N days)" heading + one card per day, as lazy items. */
internal fun LazyListScope.itineraryItems(days: List<ItineraryDayUi>) {
    if (days.isEmpty()) return
    item(key = "itinerary-heading", contentType = "itinerary-heading") {
        val count = pluralStringResource(R.plurals.plan_days, days.size, days.size)
        SectionTitle(stringResource(R.string.plan_full_itinerary, count), Modifier.padding(top = TravelTheme.spacing.lg))
    }
    days.forEachIndexed { index, day ->
        item(key = "day-$index-${day.dayNumber}", contentType = "itinerary-day") {
            ItineraryDayCard(day, Modifier.padding(top = TravelTheme.spacing.sm))
        }
    }
}

/** One itinerary day, read-only. */
@Composable
internal fun ItineraryDayCard(day: ItineraryDayUi, modifier: Modifier = Modifier) {
    TpCard(modifier.fillMaxWidth(), accent = TravelTheme.sectionAccents[io.github.rupakc.travelplanner.core.model.section.SectionId.ITINERARY].bar) {
        val heading = listOfNotNull(day.dayNumber?.let { stringResource(R.string.plan_day, it) }, day.date?.let(::isoDateLabel), day.city)
        Text(
            heading.joinToString(", "),
            style = TravelTheme.typography.titleSmall,
            color = TravelTheme.colors.primary,
            modifier = Modifier.semantics { heading() },
        )
        day.theme?.let {
            Text(it, style = TravelTheme.typography.bodySmall, fontStyle = FontStyle.Italic, color = TravelTheme.colors.onSurfaceVariant)
        }
        Column(Modifier.padding(top = TravelTheme.spacing.sm), verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
            day.slots.forEach { ItinerarySlotRow(it) }
        }
    }
}

/** One slot: time of day + derived time range, activity, place, notes. */
@Composable
internal fun ItinerarySlotRow(slot: ItinerarySlotUi, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
        Column(Modifier.widthIn(min = TimeColumnWidth)) {
            timeOfDayLabel(slot.timeOfDay)?.let { Text(it, style = TravelTheme.typography.labelMedium, color = TravelTheme.colors.onSurfaceVariant) }
            slot.timeRange?.let { Text(it, style = TravelTheme.typography.labelSmall, color = TravelTheme.colors.onSurfaceVariant) }
        }
        Column(Modifier.weight(1f)) {
            Text(slot.activity ?: stringResource(R.string.plan_item_unnamed), style = TravelTheme.typography.bodyMedium)
            slot.location?.let { Text(it, style = TravelTheme.typography.bodySmall, color = TravelTheme.colors.onSurfaceVariant) }
            if (slot.fromLink) {
                Text(
                    stringResource(R.string.plan_from_link),
                    style = TravelTheme.typography.labelSmall,
                    color = TravelTheme.extendedColors.warning,
                )
            }
            slot.slotNotes?.let { Text(it, style = TravelTheme.typography.bodySmall, color = TravelTheme.colors.onSurfaceVariant) }
            slot.note?.let { Text(it, style = TravelTheme.typography.bodySmall, fontStyle = FontStyle.Italic, color = TravelTheme.colors.primary) }
            slot.costUsd?.let { PriceText(it, isEstimate = true, style = TravelTheme.typography.labelMedium) }
        }
    }
}

private fun isoDateLabel(iso: String): String = runCatching { java.time.LocalDate.parse(iso).medium() }.getOrDefault(iso)
