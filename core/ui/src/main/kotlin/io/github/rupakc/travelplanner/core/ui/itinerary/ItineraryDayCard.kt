package io.github.rupakc.travelplanner.core.ui.itinerary

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelShape
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.domain.format.formatPrice
import io.github.rupakc.travelplanner.core.domain.itinerary.DayRow
import io.github.rupakc.travelplanner.core.domain.itinerary.ItineraryLayouts
import io.github.rupakc.travelplanner.core.domain.itinerary.ItinerarySchedule
import io.github.rupakc.travelplanner.core.domain.itinerary.ScheduleWarning
import io.github.rupakc.travelplanner.core.domain.itinerary.ScheduledDay
import io.github.rupakc.travelplanner.core.domain.itinerary.ScheduledSlot
import io.github.rupakc.travelplanner.core.domain.itinerary.SlotEdit
import io.github.rupakc.travelplanner.core.domain.itinerary.Transit
import io.github.rupakc.travelplanner.core.model.section.ItineraryData
import io.github.rupakc.travelplanner.core.model.section.ItineraryDay
import io.github.rupakc.travelplanner.core.model.section.ItinerarySlot
import io.github.rupakc.travelplanner.core.model.section.SectionId
import io.github.rupakc.travelplanner.core.model.settings.ThemeMode
import io.github.rupakc.travelplanner.core.ui.PriceText
import io.github.rupakc.travelplanner.core.ui.R

/** Layout constants of the shared itinerary UI (defined once). */
object ItineraryDimens {
    /** Width of the time column (label + derived time range); text wraps at large font scales. */
    val TimeColumn = 92.dp
    val Icon = 14.dp
    val Border = 1.dp
}

/**
 * One itinerary day, read-only by default: a header (day number, date, city, theme, daily estimate),
 * the "Tight schedule" warnings, and [rows] (slots plus optional free-time placeholders) with the
 * travel line between stops. Reused by Results (with editing controls via [slotRow]) and My Plan.
 *
 * [showTransit] is false for days listed out of timed order (a travel line must come from the row above).
 */
@Composable
fun ItineraryDayCard(
    day: ScheduledDay,
    modifier: Modifier = Modifier,
    rows: List<DayRow> = day.slots.map { DayRow.Slot(it) },
    edits: Map<String, SlotEdit> = emptyMap(),
    notes: Map<String, String> = emptyMap(),
    showTransit: Boolean = true,
    slotRow: @Composable (ScheduledSlot) -> Unit = { slot ->
        ItinerarySlotRow(slot, edit = edits[slot.key] ?: SlotEdit.NONE, note = notes[slot.key])
    },
    freeRow: @Composable (timeOfDay: String) -> Unit = { ItineraryFreeTimeRow(it) },
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = TravelShape.ItemCard,
        border = BorderStroke(ItineraryDimens.Border, TravelTheme.colors.outlineVariant),
        color = TravelTheme.colors.surface,
    ) {
        Column {
            DayHeader(day.day)
            if (day.warnings.isNotEmpty()) ScheduleWarnings(day.warnings)
            rows.forEachIndexed { index, row ->
                if (index > 0) HorizontalDivider(color = TravelTheme.colors.outlineVariant)
                when (row) {
                    is DayRow.Free -> {
                        freeRow(row.timeOfDay)
                    }

                    is DayRow.Slot -> {
                        if (showTransit) row.slot.transitFromPrev?.let { TransitLine(it) }
                        slotRow(row.slot)
                    }
                }
            }
            val extras = day.day.linkExtras.mapNotNull { it.name?.takeIf(String::isNotBlank) }
            if (extras.isNotEmpty()) {
                Text(
                    pluralStringResource(R.plurals.itinerary_link_extras, extras.size, extras.joinToString(", ")),
                    style = TravelTheme.typography.bodySmall,
                    color = TravelTheme.extendedColors.onWarningContainer,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .background(TravelTheme.extendedColors.warningContainer)
                            .padding(TravelTheme.spacing.md),
                )
            }
        }
    }
}

@Composable
private fun DayHeader(day: ItineraryDay) {
    val accent = TravelTheme.sectionAccents[SectionId.ITINERARY]
    Row(
        Modifier
            .fillMaxWidth()
            .background(accent.tint)
            .padding(horizontal = TravelTheme.spacing.lg, vertical = TravelTheme.spacing.md),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.md),
    ) {
        Text(
            day.dayNumber?.let { stringResource(R.string.itinerary_day, it) } ?: stringResource(R.string.itinerary_day_unknown),
            style = TravelTheme.typography.headlineSmall,
            color = accent.text,
            modifier = Modifier.semantics { heading() },
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xxs)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
                day.date?.let { Text(it, style = TravelTheme.typography.labelMedium, color = TravelTheme.colors.onSurfaceVariant) }
                day.city?.takeIf { it.isNotBlank() }?.let {
                    Text(it.substringBefore(',').trim(), style = TravelTheme.typography.labelMedium, color = TravelTheme.colors.onSurfaceVariant)
                }
            }
            day.theme?.takeIf { it.isNotBlank() }?.let { Text(it, style = TravelTheme.typography.titleSmall) }
        }
        day.dailyEstimatedCostUsd?.takeIf { it > 0 }?.let { cost ->
            val formatted = formatPrice(cost)
            Text(
                stringResource(R.string.itinerary_day_cost, formatted),
                style = TravelTheme.typography.labelMedium,
                color = TravelTheme.colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ScheduleWarnings(warnings: List<ScheduleWarning>) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(TravelTheme.extendedColors.warningContainer)
            .padding(horizontal = TravelTheme.spacing.lg, vertical = TravelTheme.spacing.sm),
        verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xxs),
    ) {
        Text(
            stringResource(R.string.itinerary_tight_schedule),
            style = TravelTheme.typography.labelLarge,
            color = TravelTheme.extendedColors.onWarningContainer,
        )
        warnings.forEach { Text(warningText(it), style = TravelTheme.typography.bodySmall, color = TravelTheme.extendedColors.onWarningContainer) }
    }
}

/** A schedule warning in the current language. */
@Composable
fun warningText(warning: ScheduleWarning): String =
    when (warning) {
        is ScheduleWarning.LateStart -> {
            stringResource(R.string.itinerary_warning_late, warning.activity ?: stringResource(R.string.itinerary_warning_an_activity), warning.time)
        }

        is ScheduleWarning.RunsLate -> {
            warning.dayNumber?.let { stringResource(R.string.itinerary_warning_runs_late, it.toString()) } ?: warning.english
        }
    }

/** "🚶 12 min walk · 0.9 km" / "🚇 ~25 min by transit" (web `transitLabel`). */
@Composable
fun transitText(transit: Transit): String =
    when {
        transit.mode != Transit.Mode.WALK -> stringResource(R.string.itinerary_transit_ride, transit.minutes)
        transit.km >= MIN_KM_LABEL -> stringResource(R.string.itinerary_transit_walk, transit.minutes, ItinerarySchedule.formatKm(transit.km))
        else -> stringResource(R.string.itinerary_transit_walk_short, transit.minutes)
    }

private const val MIN_KM_LABEL = 0.1

@Composable
private fun TransitLine(transit: Transit) {
    Text(
        transitText(transit),
        style = TravelTheme.typography.labelSmall.copy(fontFeatureSettings = TABULAR),
        color = TravelTheme.colors.onSurfaceVariant,
        modifier =
            Modifier
                .fillMaxWidth()
                .background(TravelTheme.colors.surfaceContainerLow)
                .padding(
                    start = TravelTheme.spacing.lg + ItineraryDimens.TimeColumn,
                    end = TravelTheme.spacing.lg,
                    top = TravelTheme.spacing.xs,
                    bottom = TravelTheme.spacing.xs,
                ),
    )
}

internal const val TABULAR = "tnum"

/** Localised label of a time of day; non-standard times show as sent, capitalised. */
@Composable
fun timeOfDayLabel(timeOfDay: String?): String =
    when (timeOfDay) {
        "morning" -> stringResource(R.string.itinerary_time_morning)
        "afternoon" -> stringResource(R.string.itinerary_time_afternoon)
        "evening" -> stringResource(R.string.itinerary_time_evening)
        else -> timeOfDay.orEmpty().replaceFirstChar { it.uppercaseChar() }
    }

/** Web SLOT_ICONS (decorative). */
fun timeOfDayEmoji(timeOfDay: String?): String =
    when (timeOfDay) {
        "morning" -> "🌅"
        "afternoon" -> "☀️"
        "evening" -> "🌙"
        else -> ""
    }

/**
 * One slot: time label + derived time range, activity (edited name when set), location, the
 * original description (hidden once the activity is renamed), the user's note and the estimate.
 * [trailing] hosts actions (Results editing) under the cost.
 */
@Composable
fun ItinerarySlotRow(
    slot: ScheduledSlot,
    modifier: Modifier = Modifier,
    edit: SlotEdit = SlotEdit.NONE,
    note: String? = null,
    trailing: @Composable ColumnScope.() -> Unit = {},
) {
    val s = slot.slot
    val activity = edit.activityOr(s.activity).orEmpty()
    val location = edit.locationOr(s.location)
    val cost = edit.costOr(s.estimatedCostUsd)
    val range = ItinerarySchedule.timeRange(slot.startTime, slot.endTime)
    val tod = timeOfDayLabel(s.timeOfDay)
    val rangeA11y = if (range.isNotEmpty()) stringResource(R.string.itinerary_time_range_a11y, slot.startTime.orEmpty(), slot.endTime.orEmpty()) else ""
    Row(
        modifier.fillMaxWidth().padding(horizontal = TravelTheme.spacing.lg, vertical = TravelTheme.spacing.md),
        horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.md),
    ) {
        Column(
            Modifier.width(ItineraryDimens.TimeColumn).semantics { contentDescription = listOf(tod, rangeA11y).filter { it.isNotEmpty() }.joinToString(", ") },
        ) {
            Text("${timeOfDayEmoji(s.timeOfDay)} $tod".trim(), style = TravelTheme.typography.labelLarge, color = TravelTheme.colors.onSurfaceVariant)
            if (range.isNotEmpty()) {
                Text(range, style = TravelTheme.typography.labelMedium.copy(fontFeatureSettings = TABULAR), color = TravelTheme.colors.onSurface)
            }
        }
        Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xxs)) {
            Text(activity, style = TravelTheme.typography.titleSmall)
            if (s.fromLink == true) FromLinkTag(s.linkQuote)
            location?.takeIf { it.isNotEmpty() }?.let {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Place,
                        contentDescription = null,
                        modifier = Modifier.size(ItineraryDimens.Icon),
                        tint = TravelTheme.colors.onSurfaceVariant,
                    )
                    Text(it, style = TravelTheme.typography.bodySmall, color = TravelTheme.colors.onSurfaceVariant)
                }
            }
            s.notes?.takeIf { it.isNotEmpty() && edit.activity.isNullOrEmpty() }?.let {
                Text(it, style = TravelTheme.typography.bodySmall, fontStyle = FontStyle.Italic, color = TravelTheme.colors.onSurfaceVariant)
            }
            note?.takeIf { it.isNotEmpty() }?.let {
                Text(
                    stringResource(R.string.itinerary_user_note, it),
                    style = TravelTheme.typography.bodySmall,
                    fontStyle = FontStyle.Italic,
                    color = TravelTheme.colors.primary,
                )
            }
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xs)) {
            if (cost != null) PriceText(cost, style = TravelTheme.typography.labelLarge, hideWhenUnknown = true)
            trailing()
        }
    }
}

@Composable
private fun FromLinkTag(quote: String?) {
    Column(
        Modifier
            .background(TravelTheme.extendedColors.warningContainer, TravelShape.Input)
            .padding(horizontal = TravelTheme.spacing.sm, vertical = TravelTheme.spacing.xxs),
    ) {
        Text(stringResource(R.string.itinerary_from_link), style = TravelTheme.typography.labelSmall, color = TravelTheme.extendedColors.onWarningContainer)
        quote?.takeIf { it.isNotBlank() }?.let {
            Text("“$it”", style = TravelTheme.typography.labelSmall, fontStyle = FontStyle.Italic, color = TravelTheme.extendedColors.onWarningContainer)
        }
    }
}

/** "Free time" placeholder for an empty standard time (editing on). */
@Composable
fun ItineraryFreeTimeRow(timeOfDay: String, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = TravelTheme.spacing.lg, vertical = TravelTheme.spacing.md),
        horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "${timeOfDayEmoji(timeOfDay)} ${timeOfDayLabel(timeOfDay)}".trim(),
            style = TravelTheme.typography.labelLarge,
            color = TravelTheme.colors.onSurfaceVariant,
            modifier = Modifier.width(ItineraryDimens.TimeColumn),
        )
        Text(
            stringResource(R.string.itinerary_free_time),
            style = TravelTheme.typography.bodyMedium,
            fontStyle = FontStyle.Italic,
            color = TravelTheme.colors.onSurfaceVariant,
        )
    }
}

private val previewItinerary =
    ItineraryData(
        days =
            listOf(
                ItineraryDay(
                    dayNumber = 1,
                    date = "2027-05-01",
                    city = "Lisbon, Portugal",
                    theme = "Old town and river",
                    dailyEstimatedCostUsd = 45.0,
                    slots =
                        listOf(
                            ItinerarySlot(
                                timeOfDay = "morning",
                                activity = "São Jorge Castle",
                                location = "Alfama",
                                lat = 38.7139,
                                lng = -9.1335,
                                estimatedCostUsd = 15.0,
                            ),
                            ItinerarySlot(
                                timeOfDay = "afternoon",
                                activity = "Tram 28",
                                location = "Martim Moniz",
                                lat = 38.7167,
                                lng = -9.1356,
                                estimatedCostUsd = 3.0,
                            ),
                            ItinerarySlot(timeOfDay = "evening", activity = "Fado dinner", durationHours = 5.0, estimatedCostUsd = 27.0),
                        ),
                ),
            ),
    )

@Composable
private fun PreviewDay(mode: ThemeMode) {
    TravelTheme(themeMode = mode) {
        val day = ItinerarySchedule.scheduleDays(ItineraryLayouts.arrange(previewItinerary, null).days).single()
        ItineraryDayCard(day, notes = mapOf("1-evening" to "Book a table by the window"))
    }
}

@Preview(name = "Itinerary day – light")
@Composable
private fun ItineraryDayLightPreview() = PreviewDay(ThemeMode.LIGHT)

@Preview(name = "Itinerary day – dark")
@Composable
private fun ItineraryDayDarkPreview() = PreviewDay(ThemeMode.DARK)
