package io.github.rupakc.travelplanner.feature.results.sections

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import io.github.rupakc.travelplanner.core.domain.results.flightKey
import io.github.rupakc.travelplanner.core.model.plan.PlanItem
import io.github.rupakc.travelplanner.core.model.plan.PlanSelectionRules
import io.github.rupakc.travelplanner.core.model.plan.PlanSelections
import io.github.rupakc.travelplanner.core.model.section.ActivitiesData
import io.github.rupakc.travelplanner.core.model.section.Activity
import io.github.rupakc.travelplanner.core.model.section.Flight
import io.github.rupakc.travelplanner.core.model.section.FlightLegGroup
import io.github.rupakc.travelplanner.core.model.section.FlightsData
import io.github.rupakc.travelplanner.core.model.section.Hotel
import io.github.rupakc.travelplanner.core.model.section.HotelsData
import io.github.rupakc.travelplanner.core.model.section.SectionData
import io.github.rupakc.travelplanner.core.model.section.SectionId
import io.github.rupakc.travelplanner.feature.results.BudgetUi
import io.github.rupakc.travelplanner.feature.results.ResultsEvent
import io.github.rupakc.travelplanner.feature.results.sections.commerce.HiddenByBudgetRow
import io.github.rupakc.travelplanner.feature.results.sections.commerce.LocalCommerceContext

/**
 * The plan selections as snapshot state: cards derive their own "selected" from it, so
 * a toggle recomposes only the cards whose state changed (not every section).
 */
val LocalSelections = compositionLocalOf<State<PlanSelections>?> { null }

/** One lazy item below a commerce section's header (flat list, spec §4 Performance). */
@Immutable
internal sealed interface CommerceRow {
    /** Stable lazy key, unique across the whole results list. */
    val key: String
    val contentType: String
}

internal data class FlightRow(val flight: Flight, val perLeg: Boolean, val over: Boolean, override val key: String) : CommerceRow {
    override val contentType: String get() = "flight"
}

internal data class LegHeaderRow(val leg: FlightLegGroup, val number: Int, override val key: String) : CommerceRow {
    override val contentType: String get() = "flight_leg"
}

internal data class LegEmptyRow(override val key: String) : CommerceRow {
    override val contentType: String get() = "flight_leg_empty"
}

internal data class HotelRow(val hotel: Hotel, val over: Boolean, override val key: String) : CommerceRow {
    override val contentType: String get() = "hotel"
}

internal data class ActivityRow(val activity: Activity, val fits: Boolean, override val key: String) : CommerceRow {
    override val contentType: String get() = "activity"
}

/** Budget planner: "N hidden — show all". */
internal data class HiddenRow(val count: Int, override val key: String) : CommerceRow {
    override val contentType: String get() = "budget_hidden"
}

/**
 * Card rows of the flights / hotels / activities sections (empty for other sections).
 * Over-budget items are flagged, or hidden when "Show only what fits" is on — a selected
 * item always stays visible (web `budgetView`).
 */
internal fun commerceRows(id: SectionId, data: SectionData?, budget: BudgetUi?, selections: PlanSelections): List<CommerceRow> {
    if (data == null || data.isError) return emptyList()
    val active = budget?.takeIf { it.cardsReact }
    return when (data) {
        is FlightsData -> {
            flightRows(data, active, selections)
        }

        is HotelsData -> {
            val rows = mutableListOf<CommerceRow>()
            var hidden = 0
            data.results.forEachIndexed { i, hotel ->
                val over = hotel.name != null && active?.fit?.hotels?.over?.contains(hotel.name) == true
                if (over && active.showOnlyFits && !PlanSelectionRules.isSelected(selections, PlanItem.HotelItem(hotel))) {
                    hidden++
                } else {
                    rows += HotelRow(hotel, over, "${id.name}:${hotel.name}:$i")
                }
            }
            if (hidden > 0) rows += HiddenRow(hidden, "${id.name}:hidden")
            rows
        }

        is ActivitiesData -> {
            data.results.mapIndexed { i, a -> ActivityRow(a, active?.fit?.activities?.picked?.contains(a.name) == true, "${id.name}:${a.name}:$i") }
        }

        else -> {
            emptyList()
        }
    }
}

private fun flightRows(data: FlightsData, budget: BudgetUi?, selections: PlanSelections): List<CommerceRow> {
    val rows = mutableListOf<CommerceRow>()

    fun addFlights(flights: List<Flight>, perLeg: Boolean, prefix: String) {
        var hidden = 0
        flights.forEachIndexed { i, f ->
            val over = budget?.fit?.flights?.over?.contains(flightKey(f)) == true
            if (over && budget.showOnlyFits && !PlanSelectionRules.isSelected(selections, PlanItem.FlightItem(f))) {
                hidden++
            } else {
                rows += FlightRow(f, perLeg, over, "$prefix:${flightKey(f)}:$i")
            }
        }
        if (hidden > 0) rows += HiddenRow(hidden, "$prefix:hidden")
    }
    if (data.legs.isNotEmpty()) {
        data.legs.forEachIndexed { i, leg ->
            val number = (leg.legIndex ?: i) + 1
            val prefix = "FLIGHTS:leg$number"
            rows += LegHeaderRow(leg, number, "$prefix:header")
            if (leg.results.isEmpty()) rows += LegEmptyRow("$prefix:empty") else addFlights(leg.results, perLeg = true, prefix = prefix)
        }
    } else {
        addFlights(data.results, perLeg = false, prefix = "FLIGHTS")
    }
    return rows
}

/** The current selections: the snapshot state when provided, else via [SectionActions.isSelected]'s source. */
@Composable
internal fun currentSelections(actions: SectionActions): PlanSelections =
    LocalSelections.current?.value ?: (actions as? SelectionsSource)?.selectionsNow ?: PlanSelections()

/** Implemented by section actions that can expose the whole selection set. */
internal interface SelectionsSource {
    val selectionsNow: PlanSelections
}

/** "Selected" for one card, derived so only this card recomposes when it flips. */
@Composable
internal fun rememberSelected(item: PlanItem, actions: SectionActions): Boolean {
    val selections = LocalSelections.current ?: return actions.isSelected(item)
    val selected by remember(item, selections) { derivedStateOf { PlanSelectionRules.isSelected(selections.value, item) } }
    return selected
}

/** Renders one [CommerceRow]. */
@Composable
internal fun CommerceRowContent(row: CommerceRow, actions: SectionActions, modifier: Modifier = Modifier) {
    val ctx = LocalCommerceContext.current
    when (row) {
        is FlightRow -> {
            val item = PlanItem.FlightItem(row.flight)
            FlightCard(
                flight = row.flight,
                selected = rememberSelected(item, actions),
                over = row.over,
                perLeg = row.perLeg,
                onToggle = { actions.onToggle(item) },
                onLayover = { ctx.onEvent(ResultsEvent.OpenLayover(it)) },
                modifier = modifier,
            )
        }

        is LegHeaderRow -> {
            LegHeader(row.leg, row.number, modifier)
        }

        is LegEmptyRow -> {
            LegEmpty(modifier)
        }

        is HotelRow -> {
            HotelCard(row.hotel, rememberSelected(PlanItem.HotelItem(row.hotel), actions), row.over, actions, modifier)
        }

        is ActivityRow -> {
            ActivityCard(row.activity, rememberSelected(PlanItem.ActivityItem(row.activity), actions), row.fits, actions, modifier)
        }

        is HiddenRow -> {
            HiddenByBudgetRow(row.count, onShowAll = { ctx.onEvent(ResultsEvent.ShowOnlyFits(false)) }, modifier = modifier)
        }
    }
}
