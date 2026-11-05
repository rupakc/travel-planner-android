package io.github.rupakc.travelplanner.feature.results.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import io.github.rupakc.travelplanner.core.designsystem.component.TpButton
import io.github.rupakc.travelplanner.core.designsystem.component.TpButtonVariant
import io.github.rupakc.travelplanner.core.designsystem.component.TpCard
import io.github.rupakc.travelplanner.core.designsystem.component.TpCardSize
import io.github.rupakc.travelplanner.core.designsystem.component.TpChip
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelShape
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.domain.format.formatPrice
import io.github.rupakc.travelplanner.core.domain.results.BudgetCategory
import io.github.rupakc.travelplanner.core.domain.results.MetricDiff
import io.github.rupakc.travelplanner.core.domain.results.PackageConflict
import io.github.rupakc.travelplanner.core.domain.results.PackageItem
import io.github.rupakc.travelplanner.core.domain.results.PackageItemType
import io.github.rupakc.travelplanner.core.domain.results.PickReason
import io.github.rupakc.travelplanner.core.domain.results.Remix
import io.github.rupakc.travelplanner.core.domain.results.RemixMetric
import io.github.rupakc.travelplanner.core.domain.results.RemixPreset
import io.github.rupakc.travelplanner.core.domain.results.airlineName
import io.github.rupakc.travelplanner.core.domain.results.flightNo
import io.github.rupakc.travelplanner.core.model.plan.PlanItem
import io.github.rupakc.travelplanner.core.model.search.TravelSearchRequest
import io.github.rupakc.travelplanner.core.model.section.SectionId
import io.github.rupakc.travelplanner.feature.results.BestValueUi
import io.github.rupakc.travelplanner.feature.results.BudgetUi
import io.github.rupakc.travelplanner.feature.results.PlanSummary
import io.github.rupakc.travelplanner.feature.results.R
import io.github.rupakc.travelplanner.feature.results.RemixUi
import io.github.rupakc.travelplanner.feature.results.ResultsEvent
import io.github.rupakc.travelplanner.feature.results.sections.commerce.ChipRow
import io.github.rupakc.travelplanner.feature.results.sections.commerce.Pill
import io.github.rupakc.travelplanner.feature.results.sections.commerce.PillTone
import kotlin.math.abs
import kotlin.math.roundToLong

private const val MINUTES_PER_HOUR = 60
private const val BUDGET_STEP = 10f
private const val MID_RANGE_STARS = 3

// ── Best-value package ──────────────────────────────────────────────────────

/** Web "Best-value package" card: real listed items with the reason each was picked; add all with Undo. */
@Composable
internal fun BestValueCard(ui: BestValueUi, onEvent: (ResultsEvent) -> Unit, onOpenLink: (String) -> Unit, modifier: Modifier = Modifier) {
    val pkg = ui.pkg
    TpCard(modifier.fillMaxWidth(), size = TpCardSize.Large) {
        Text(stringResource(R.string.results_best_value), style = TravelTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        Text(stringResource(R.string.results_best_value_sub), style = TravelTheme.typography.bodySmall, color = TravelTheme.colors.onSurfaceVariant)
        Column(Modifier.padding(vertical = TravelTheme.spacing.sm)) {
            pkg.items.forEachIndexed { i, entry ->
                if (i > 0) HorizontalDivider(color = TravelTheme.colors.outlineVariant)
                PackageRow(entry, onOpenLink)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
            Text(stringResource(R.string.results_package_total), style = TravelTheme.typography.labelMedium, color = TravelTheme.colors.onSurfaceVariant)
            Text(formatPrice(pkg.total), style = TravelTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            pkg.budget?.let { b ->
                Pill(
                    stringResource(if (b.over) R.string.results_over_budget else R.string.results_under_budget, formatPrice(b.amount.toDouble())),
                    tone = if (b.over) PillTone.Warning else PillTone.Positive,
                )
            }
        }
        ui.planTotal?.let { Text(stringResource(R.string.results_plan_total, formatPrice(it)), style = TravelTheme.typography.bodySmall) }
        if (pkg.items.any { it.type == PackageItemType.FLIGHT || it.type == PackageItemType.FLIGHTS }) {
            Text(stringResource(R.string.results_flight_prices_pp), style = TravelTheme.typography.labelSmall, color = TravelTheme.colors.onSurfaceVariant)
        }
        ChipRow(Modifier.padding(top = TravelTheme.spacing.sm)) {
            TpButton(
                text = stringResource(if (ui.applied) R.string.results_package_added else R.string.results_package_add),
                onClick = { onEvent(ResultsEvent.ApplyPackage) },
                enabled = !ui.applied && !ui.disabled,
            )
            if (ui.applied && ui.canUndo) TpButton(stringResource(R.string.results_undo), onClick = { onEvent(ResultsEvent.UndoPackage) }, variant = TpButtonVariant.Secondary)
        }
        val note =
            when {
                ui.disabled -> stringResource(R.string.results_package_disabled)
                ui.conflicts.isNotEmpty() -> stringResource(R.string.results_package_replaces, conflictLabels(ui.conflicts))
                ui.applied -> stringResource(R.string.results_package_added)
                else -> null
            }
        Text(
            note.orEmpty(),
            style = TravelTheme.typography.bodySmall,
            color = if (ui.disabled) TravelTheme.extendedColors.warning else TravelTheme.colors.onSurfaceVariant,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

@Composable
private fun conflictLabels(conflicts: List<PackageConflict>): String {
    val labels =
        conflicts.map {
            stringResource(
                when (it) {
                    PackageConflict.FLIGHT -> R.string.results_conflict_flight
                    PackageConflict.HOTEL -> R.string.results_conflict_hotel
                    PackageConflict.SIM -> R.string.results_conflict_sim
                },
            )
        }
    return if (labels.size < 2) labels.joinToString("") else stringResource(R.string.results_join_and, labels.dropLast(1).joinToString(", "), labels.last())
}

@Composable
private fun PackageRow(entry: PackageItem, onOpenLink: (String) -> Unit) {
    val (name, href, price) = describe(entry)
    val safe = href?.takeIf { it.startsWith("https://") || it.startsWith("http://") }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = TravelTheme.spacing.minTouchTarget)
            .then(if (safe != null) Modifier.clickable(role = Role.Button, onClickLabel = stringResource(R.string.results_open_link)) { onOpenLink(safe) } else Modifier)
            .padding(vertical = TravelTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(TravelTheme.spacing.accentBar, TravelTheme.spacing.xl).background(TravelTheme.sectionAccents[entry.sectionId()].bar, TravelShape.Pill))
        Column(Modifier.weight(1f).padding(start = TravelTheme.spacing.md)) {
            ChipRow {
                Text(name, style = TravelTheme.typography.titleSmall, color = if (safe != null) TravelTheme.colors.primary else TravelTheme.colors.onSurface)
                entry.city?.let { Pill(it) }
                price?.let { Text(it, style = TravelTheme.typography.labelMedium, color = TravelTheme.colors.primary) }
            }
            Text(reasonText(entry.reason), style = TravelTheme.typography.bodySmall, color = TravelTheme.colors.onSurfaceVariant)
        }
    }
}

private fun PackageItem.sectionId(): SectionId =
    when (type) {
        PackageItemType.FLIGHT, PackageItemType.FLIGHTS -> SectionId.FLIGHTS
        PackageItemType.HOTEL -> SectionId.HOTELS
        PackageItemType.ACTIVITIES -> SectionId.ACTIVITIES
        PackageItemType.WALKING_TOURS -> SectionId.WALKING_TOURS
        PackageItemType.PLACES_TO_SEE -> SectionId.PLACES_TO_SEE
        PackageItemType.SIM -> SectionId.SIM
    }

/** Name, link and price label per item type (web `describe`). */
@Composable
private fun describe(entry: PackageItem): Triple<String, String?, String?> =
    when (val item = entry.item) {
        is PlanItem.FlightItem -> {
            Triple(
                listOfNotNull(item.flight.airlineName, item.flight.flightNo).joinToString(" ").ifBlank { stringResource(R.string.results_flight_fallback_name) },
                item.flight.bookingUrl,
                item.flight.priceUsd?.let { formatPrice(it) },
            )
        }

        is PlanItem.HotelItem -> {
            Triple(item.hotel.name.orEmpty(), item.hotel.bookingUrl, item.hotel.pricePerNightUsd?.let { stringResource(R.string.results_hotel_per_night, formatPrice(it)) })
        }

        is PlanItem.ActivityItem -> {
            Triple(
                item.activity.name.orEmpty(),
                item.activity.bookingUrl,
                item.activity.priceUsd?.let { if (it == 0.0) stringResource(R.string.results_free) else formatPrice(it) },
            )
        }

        is PlanItem.WalkingTourItem -> Triple(item.tour.name.orEmpty(), item.tour.url, null)
        is PlanItem.PlaceItem -> Triple(item.place.name.orEmpty(), item.place.infoUrl, null)
        is PlanItem.SimItem -> {
            Triple(
                listOfNotNull(item.plan.provider, item.plan.planName).joinToString(" ").ifBlank { stringResource(R.string.results_sim_fallback_name) },
                item.plan.url,
                item.plan.priceUsd?.let { formatPrice(it) },
            )
        }

        else -> Triple("", null, null)
    }

@Composable
private fun reasonText(reason: PickReason): String =
    when (reason) {
        PickReason.CheapestFare -> stringResource(R.string.results_reason_cheapest_fare)
        is PickReason.FewerStops -> stringResource(R.string.results_reason_fewer_stops, formatPrice(reason.extra.toDouble()))
        is PickReason.SavesTime -> stringResource(R.string.results_reason_saves_time, durationText(reason.minutes), formatPrice(reason.extra.toDouble()))
        is PickReason.BestReviewed -> {
            val stars = reason.stars
            val label =
                when (stars) {
                    null -> stringResource(R.string.results_reason_best_reviewed)
                    MID_RANGE_STARS -> stringResource(R.string.results_reason_mid_range)
                    else -> stringResource(R.string.results_reason_stars, stars)
                }
            reason.city?.let { "$label · $it" } ?: label
        }

        PickReason.CheaperStay -> stringResource(R.string.results_reason_cheaper_stay)
        is PickReason.ActivityPrice -> {
            val base =
                when {
                    reason.price == 0.0 -> stringResource(R.string.results_free)
                    reason.typical -> stringResource(R.string.results_reason_typical, formatPrice(reason.price.roundToLong().toDouble()))
                    else -> formatPrice(reason.price.roundToLong().toDouble())
                }
            if (reason.matchesInterests) stringResource(R.string.results_reason_interests, base) else base
        }

        is PickReason.TourNote -> reason.priceNote ?: stringResource(R.string.results_reason_free_tour)
        PickReason.FreeEntry -> stringResource(R.string.results_reason_free_entry)
        is PickReason.CoversTrip -> stringResource(R.string.results_reason_covers_trip, reason.days)
        PickReason.CheapestPlan -> stringResource(R.string.results_reason_cheapest_plan)
    }

/** "2h", "3h 20m", "45m" (web `duration`). */
@Composable
internal fun durationText(minutes: Int): String {
    val h = minutes / MINUTES_PER_HOUR
    val m = minutes % MINUTES_PER_HOUR
    return when {
        h > 0 && m > 0 -> stringResource(R.string.results_duration_hm, h, m)
        h > 0 -> stringResource(R.string.results_duration_h, h)
        else -> stringResource(R.string.results_duration_m, m)
    }
}

// ── Budget planner ──────────────────────────────────────────────────────────

/** Web "Budget planner": split the total into shares, see which loaded options fit. */
@Composable
internal fun BudgetPlannerCard(ui: BudgetUi, onEvent: (ResultsEvent) -> Unit, modifier: Modifier = Modifier) {
    var open by rememberSaveable { mutableStateOf(true) }
    val a = ui.allocation
    val total = a.total
    TpCard(modifier.fillMaxWidth(), size = TpCardSize.Large) {
        val stateText = stringResource(if (open) R.string.results_state_expanded else R.string.results_state_collapsed)
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = TravelTheme.spacing.minTouchTarget)
                .toggleable(value = open, role = Role.Button, onValueChange = { open = it })
                .semantics { stateDescription = stateText },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.results_budget_planner), style = TravelTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                Text(stringResource(R.string.results_budget_planner_sub), style = TravelTheme.typography.bodySmall, color = TravelTheme.colors.onSurfaceVariant)
            }
            Icon(if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, contentDescription = null)
        }
        if (!open) return@TpCard
        TotalField(total.roundToLong(), onCommit = { onEvent(ResultsEvent.BudgetTotal(it)) }, onReset = { onEvent(ResultsEvent.BudgetReset) })
        StackedBar(ui)
        BudgetCategory.entries.forEach { category ->
            val value = a[category]
            val label = categoryLabel(category)
            val fitText = fitText(category, ui)
            Column(Modifier.padding(top = TravelTheme.spacing.sm)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(label, style = TravelTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                    Text(formatPrice(value), style = TravelTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
                val valueText = stringResource(R.string.results_budget_slider_value, formatPrice(value), label)
                Slider(
                    value = value.toFloat(),
                    onValueChange = { onEvent(ResultsEvent.BudgetShare(category, (Math.round(it / BUDGET_STEP) * BUDGET_STEP).toDouble())) },
                    valueRange = 0f..total.toFloat().coerceAtLeast(1f),
                    modifier = Modifier.semantics { stateDescription = valueText },
                    colors = SliderDefaults.colors(thumbColor = categoryColor(category), activeTrackColor = categoryColor(category)),
                )
                Text(fitText, style = TravelTheme.typography.bodySmall, color = TravelTheme.colors.onSurfaceVariant)
                if (category == BudgetCategory.STAY && ui.offerCheaperHotels) {
                    TpButton(
                        text = stringResource(if (ui.findingCheaperHotels) R.string.results_finding_cheaper_hotels else R.string.results_find_cheaper_hotels),
                        onClick = { onEvent(ResultsEvent.FindCheaperHotels) },
                        variant = TpButtonVariant.Tonal,
                        loading = ui.findingCheaperHotels,
                    )
                }
            }
        }
        Row(Modifier.padding(top = TravelTheme.spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.results_budget_other), style = TravelTheme.typography.labelLarge)
                Text(stringResource(R.string.results_budget_other_hint), style = TravelTheme.typography.labelSmall, color = TravelTheme.colors.onSurfaceVariant)
            }
            Text(formatPrice(a.other), style = TravelTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        }
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = TravelTheme.spacing.minTouchTarget)
                .toggleable(value = ui.showOnlyFits, role = Role.Switch, onValueChange = { onEvent(ResultsEvent.ShowOnlyFits(it)) }),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.results_show_only_fits), style = TravelTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Switch(checked = ui.showOnlyFits, onCheckedChange = null)
        }
    }
}

@Composable
private fun TotalField(total: Long, onCommit: (Double) -> Unit, onReset: () -> Unit) {
    var draft by rememberSaveable(total) { mutableStateOf(total.toString()) }
    val commit = {
        draft.trim().toDoubleOrNull()?.takeIf { it > 0 && Math.round(it) != total }?.let(onCommit)
        Unit
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it.filter(Char::isDigit) },
            modifier = Modifier.weight(1f),
            singleLine = true,
            shape = TravelShape.Input,
            label = { Text(stringResource(R.string.results_budget_total)) },
            prefix = { Text(stringResource(R.string.results_usd_symbol)) },
            supportingText = { Text(stringResource(R.string.results_per_person)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { commit() }),
        )
        TpButton(stringResource(R.string.results_apply), onClick = commit, variant = TpButtonVariant.Tonal)
        TpButton(stringResource(R.string.results_reset), onClick = onReset, variant = TpButtonVariant.Text)
    }
}

@Composable
private fun categoryLabel(c: BudgetCategory): String =
    stringResource(
        when (c) {
            BudgetCategory.FLIGHTS -> R.string.results_budget_flights
            BudgetCategory.STAY -> R.string.results_budget_stay
            BudgetCategory.ACTIVITIES -> R.string.results_budget_activities
        },
    )

@Composable
private fun categoryColor(c: BudgetCategory): Color =
    when (c) {
        BudgetCategory.FLIGHTS -> TravelTheme.sectionAccents[SectionId.FLIGHTS].bar
        BudgetCategory.STAY -> TravelTheme.sectionAccents[SectionId.HOTELS].bar
        BudgetCategory.ACTIVITIES -> TravelTheme.sectionAccents[SectionId.ACTIVITIES].bar
    }

@Composable
private fun fitText(c: BudgetCategory, ui: BudgetUi): String =
    when (c) {
        BudgetCategory.FLIGHTS -> pluralStringResource(R.plurals.results_budget_flights_fit, ui.fit.flights.total, ui.fit.flights.count, ui.fit.flights.total)
        BudgetCategory.STAY -> pluralStringResource(R.plurals.results_budget_hotels_fit, ui.fit.hotels.total, ui.fit.hotels.count, ui.fit.hotels.total)
        BudgetCategory.ACTIVITIES -> {
            val n = ui.fit.activities.picked.size
            pluralStringResource(R.plurals.results_budget_activities_fit, n, n, formatPrice(ui.fit.activities.spent))
        }
    }

/** Stacked bar Flights / Stay / Activities / Everything else (role img with amounts). */
@Composable
private fun StackedBar(ui: BudgetUi) {
    val a = ui.allocation
    val total = a.total.takeIf { it > 0 } ?: return
    val parts =
        listOf(
            categoryLabel(BudgetCategory.FLIGHTS) to (a.flights to categoryColor(BudgetCategory.FLIGHTS)),
            categoryLabel(BudgetCategory.STAY) to (a.stay to categoryColor(BudgetCategory.STAY)),
            categoryLabel(BudgetCategory.ACTIVITIES) to (a.activities to categoryColor(BudgetCategory.ACTIVITIES)),
            stringResource(R.string.results_budget_other) to (a.other to TravelTheme.colors.outline),
        )
    val a11y = parts.joinToString(", ") { (label, v) -> "$label ${formatPrice(v.first)}" }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = TravelTheme.spacing.sm)
            .height(TravelTheme.spacing.sm)
            .clearAndSetSemantics { contentDescription = a11y },
    ) {
        parts.forEach { (_, v) ->
            val weight = (v.first / total).toFloat()
            if (weight > 0f) Box(Modifier.weight(weight).fillMaxWidth().height(TravelTheme.spacing.sm).background(v.second))
        }
    }
}

// ── Remix ───────────────────────────────────────────────────────────────────

/** Web "Remix this trip": what-if presets that re-plan everything. */
@Composable
internal fun RemixBar(request: TravelSearchRequest, onEvent: (ResultsEvent) -> Unit, modifier: Modifier = Modifier) {
    TpCard(modifier.fillMaxWidth(), size = TpCardSize.Large) {
        Text(stringResource(R.string.results_remix_title), style = TravelTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        Text(stringResource(R.string.results_remix_sub), style = TravelTheme.typography.bodySmall, color = TravelTheme.colors.onSurfaceVariant)
        ChipRow(Modifier.padding(top = TravelTheme.spacing.sm)) {
            RemixPreset.entries.forEach { preset ->
                val applicable = Remix.apply(request, preset) != null
                val label = remixLabel(preset)
                val description = if (applicable) remixDescription(preset) else stringResource(R.string.results_remix_not_applicable)
                TpChip(
                    label = label,
                    selected = false,
                    enabled = applicable,
                    onClick = { onEvent(ResultsEvent.Remix(preset)) },
                    modifier = Modifier.semantics { contentDescription = "$label. $description" },
                )
            }
        }
    }
}

@Composable
internal fun remixLabel(p: RemixPreset): String =
    stringResource(
        when (p) {
            RemixPreset.WEEK_LATER -> R.string.results_remix_week_later
            RemixPreset.HALF_BUDGET -> R.string.results_remix_half_budget
            RemixPreset.LUXE -> R.string.results_remix_luxe
            RemixPreset.SLOWER_PACE -> R.string.results_remix_slow
            RemixPreset.LONG_WEEKEND -> R.string.results_remix_long_weekend
        },
    )

@Composable
private fun remixDescription(p: RemixPreset): String =
    stringResource(
        when (p) {
            RemixPreset.WEEK_LATER -> R.string.results_remix_week_later_desc
            RemixPreset.HALF_BUDGET -> R.string.results_remix_half_budget_desc
            RemixPreset.LUXE -> R.string.results_remix_luxe_desc
            RemixPreset.SLOWER_PACE -> R.string.results_remix_slow_desc
            RemixPreset.LONG_WEEKEND -> R.string.results_remix_long_weekend_desc
        },
    )

/** "Remixed: {label} — here's what changed vs your original plan" with before/after rows. */
@Composable
internal fun RemixComparisonBanner(ui: RemixUi, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val rows = ui.rows ?: return
    Surface(modifier.fillMaxWidth(), shape = TravelShape.ItemCard, color = TravelTheme.colors.tertiaryContainer, contentColor = TravelTheme.colors.onTertiaryContainer) {
        Column(Modifier.padding(TravelTheme.spacing.lg).semantics { liveRegion = LiveRegionMode.Polite }, verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.results_remixed, remixLabel(ui.preset)), style = TravelTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                TpButton(stringResource(R.string.results_dismiss), onClick = onDismiss, variant = TpButtonVariant.Text)
            }
            if (rows.isEmpty()) {
                Text(stringResource(R.string.results_remix_no_data), style = TravelTheme.typography.bodySmall)
            } else {
                ChipRow { rows.forEach { MetricTile(it) } }
            }
        }
    }
}

@Composable
private fun MetricTile(row: MetricDiff) {
    val label =
        stringResource(
            when (row.metric) {
                RemixMetric.MIN_FLIGHT -> R.string.results_metric_flight
                RemixMetric.MIN_HOTEL_NIGHTLY -> R.string.results_metric_hotel
                RemixMetric.ITINERARY_TOTAL -> R.string.results_metric_itinerary
                RemixMetric.POOR_WEATHER_DAYS -> R.string.results_metric_weather
            },
        )
    fun fmt(v: Double) = if (row.metric.isMoney) formatPrice(v.roundToLong().toDouble()) else v.roundToLong().toString()
    val change =
        when (row.improved) {
            null -> stringResource(R.string.results_metric_same)
            true -> stringResource(R.string.results_metric_better, fmt(abs(row.delta)))
            false -> stringResource(R.string.results_metric_worse, fmt(abs(row.delta)))
        }
    Surface(shape = TravelShape.Input, color = TravelTheme.colors.surface) {
        Column(Modifier.padding(TravelTheme.spacing.sm).semantics(mergeDescendants = true) {}) {
            Text(label, style = TravelTheme.typography.labelSmall, color = TravelTheme.colors.onSurfaceVariant)
            Text(stringResource(R.string.results_metric_before_after, fmt(row.after), fmt(row.before)), style = TravelTheme.typography.titleSmall)
            Text(
                change,
                style = TravelTheme.typography.labelSmall,
                color =
                    when (row.improved) {
                        true -> TravelTheme.extendedColors.success
                        false -> TravelTheme.colors.error
                        null -> TravelTheme.colors.onSurfaceVariant
                    },
            )
        }
    }
}

// ── My Plan bar ─────────────────────────────────────────────────────────────

/** Sticky "My Plan" bar: item count, estimated total, budget status; opens the plan sheet. */
@Composable
internal fun PlanBar(plan: PlanSummary, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val count = pluralStringResource(R.plurals.results_plan_items, plan.count, plan.count)
    val budget = plan.budget?.let { stringResource(if (it.over) R.string.results_over_budget else R.string.results_under_budget, formatPrice(it.amount.toDouble())) }
    Surface(modifier.fillMaxWidth(), color = TravelTheme.colors.inverseSurface, contentColor = TravelTheme.colors.inverseOnSurface, tonalElevation = TravelTheme.spacing.xs) {
        Row(
            Modifier.padding(horizontal = TravelTheme.spacing.gutter, vertical = TravelTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }) {
                Text(stringResource(R.string.results_my_plan), style = TravelTheme.typography.titleSmall)
                Text(
                    listOfNotNull(count, plan.total.takeIf { it > 0 }?.let { formatPrice(it) }, budget).joinToString(" · "),
                    style = TravelTheme.typography.bodySmall,
                )
            }
            TpButton(stringResource(R.string.results_open_my_plan), onClick = onOpen, variant = TpButtonVariant.Primary)
        }
    }
}
