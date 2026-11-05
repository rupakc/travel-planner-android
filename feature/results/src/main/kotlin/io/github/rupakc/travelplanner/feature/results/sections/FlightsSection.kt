package io.github.rupakc.travelplanner.feature.results.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import io.github.rupakc.travelplanner.core.designsystem.component.TpCard
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.domain.format.formatPrice
import io.github.rupakc.travelplanner.core.domain.results.airlineName
import io.github.rupakc.travelplanner.core.domain.results.flightKey
import io.github.rupakc.travelplanner.core.domain.results.flightNo
import io.github.rupakc.travelplanner.core.domain.results.outboundMinutes
import io.github.rupakc.travelplanner.core.domain.results.outboundStops
import io.github.rupakc.travelplanner.core.model.plan.PlanItem
import io.github.rupakc.travelplanner.core.model.section.Flight
import io.github.rupakc.travelplanner.core.model.section.FlightLeg
import io.github.rupakc.travelplanner.core.model.section.FlightLegGroup
import io.github.rupakc.travelplanner.core.model.section.FlightsData
import io.github.rupakc.travelplanner.core.model.section.SectionId
import io.github.rupakc.travelplanner.core.ui.AddToPlanButton
import io.github.rupakc.travelplanner.core.ui.BookingLinkButton
import io.github.rupakc.travelplanner.feature.results.R
import io.github.rupakc.travelplanner.feature.results.ResultsEvent
import io.github.rupakc.travelplanner.feature.results.sections.commerce.ChipRow
import io.github.rupakc.travelplanner.feature.results.sections.commerce.FareCalendarStrip
import io.github.rupakc.travelplanner.feature.results.sections.commerce.HiddenByBudgetRow
import io.github.rupakc.travelplanner.feature.results.sections.commerce.LocalCommerceContext
import io.github.rupakc.travelplanner.feature.results.sections.commerce.Pill
import io.github.rupakc.travelplanner.feature.results.sections.commerce.PillTone
import io.github.rupakc.travelplanner.feature.results.sections.commerce.PriceInsightsChip
import io.github.rupakc.travelplanner.feature.results.sections.commerce.PricingAdvisorBanner
import io.github.rupakc.travelplanner.feature.results.sections.commerce.shortCity
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Web: layovers of 5 h or more are "long" (amber) and get "Make the most of it". */
private const val LONG_LAYOVER_HOURS = 5.0
private const val MINUTES_PER_HOUR = 60
private const val DIM_ALPHA = 0.6f

/**
 * Flights section body: pricing advisor, Google price verdict, fare calendar,
 * then round-trip/one-way cards (or one group per leg on a multi-city trip).
 * Budget-planner dimming/hiding follows the web: a selected card always stays visible.
 */
@Composable
internal fun FlightsSectionContent(data: FlightsData, actions: SectionActions, modifier: Modifier = Modifier) {
    val ctx = LocalCommerceContext.current
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.md)) {
        ctx.pricingAdvisor?.let { PricingAdvisorBanner(it, ctx.daysUntilDeparture) }
        if (!data.isMultiCity) PriceInsightsChip(data.priceInsights)
        if (ctx.fareCalendar.open && ctx.fareCalendar.available) FareCalendarStrip(ctx.fareCalendar, ctx.onEvent)
        if (data.legs.isNotEmpty()) {
            MultiCityFlights(data.legs, actions)
        } else if (data.results.isEmpty()) {
            Text(stringResource(R.string.results_flights_empty), style = TravelTheme.typography.bodyMedium, color = TravelTheme.colors.onSurfaceVariant)
        } else {
            FlightList(data.results, perLeg = false, actions = actions)
        }
    }
}

@Composable
private fun FlightList(flights: List<Flight>, perLeg: Boolean, actions: SectionActions) {
    val ctx = LocalCommerceContext.current
    val budget = ctx.budget?.takeIf { it.cardsReact }
    var hidden = 0
    flights.forEachIndexed { index, flight ->
        val item = PlanItem.FlightItem(flight)
        val selected = actions.isSelected(item)
        val over = budget?.fit?.flights?.over?.contains(flightKey(flight)) == true
        if (over && budget.showOnlyFits && !selected) {
            hidden++
        } else {
            key(flightKey(flight), index) {
                FlightCard(
                    flight = flight,
                    selected = selected,
                    over = over,
                    perLeg = perLeg,
                    onToggle = { actions.onToggle(item) },
                    onLayover = { ctx.onEvent(ResultsEvent.OpenLayover(it)) },
                )
            }
        }
    }
    HiddenByBudgetRow(hidden, onShowAll = { ctx.onEvent(ResultsEvent.ShowOnlyFits(false)) })
}

@Composable
private fun MultiCityFlights(legs: List<FlightLegGroup>, actions: SectionActions) {
    val chosen = legs.flatMap { it.results }.filter { actions.isSelected(PlanItem.FlightItem(it)) }
    val total = chosen.sumOf { it.priceUsd?.takeIf(Double::isFinite) ?: 0.0 }
    Pill(
        text = stringResource(R.string.results_flights_multi_banner, chosen.size, legs.size),
        tone = PillTone.Info,
    )
    if (total > 0) Text(stringResource(R.string.results_flights_multi_total, formatPrice(total)), style = TravelTheme.typography.titleSmall)
    legs.forEachIndexed { i, leg ->
        key(leg.legIndex ?: i) {
            Column(verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
                    Pill(stringResource(R.string.results_flights_leg, (leg.legIndex ?: i) + 1), tone = PillTone.Info)
                    Text(
                        listOfNotNull("${shortCity(leg.from)} → ${shortCity(leg.to)}", leg.date).joinToString(" · "),
                        style = TravelTheme.typography.titleSmall,
                        modifier = Modifier.semantics { heading() },
                    )
                }
                if (leg.results.isEmpty()) {
                    Text(stringResource(R.string.results_flights_leg_empty), style = TravelTheme.typography.bodySmall, color = TravelTheme.colors.onSurfaceVariant)
                } else {
                    FlightList(leg.results, perLeg = true, actions = actions)
                }
            }
        }
    }
}

@Composable
private fun FlightCard(flight: Flight, selected: Boolean, over: Boolean, perLeg: Boolean, onToggle: () -> Unit, onLayover: (io.github.rupakc.travelplanner.core.model.section.FlightLayover) -> Unit) {
    val accent = TravelTheme.sectionAccents[SectionId.FLIGHTS].bar
    val oneWay = flight.tripType == "one_way" || flight.returnLeg == null
    val name = listOfNotNull(flight.airlineName, flight.flightNo).joinToString(" ").ifBlank { stringResource(R.string.results_flight_fallback_name) }
    TpCard(selected = selected, accent = accent, onClick = onToggle) {
        if (over) Pill(stringResource(R.string.results_budget_over_flight), tone = PillTone.Warning)
        Column(Modifier.alpha(if (over && !selected) DIM_ALPHA else 1f), verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
            Row(verticalAlignment = Alignment.Top) {
                ChipRow(Modifier.weight(1f)) {
                    if (!perLeg) Pill(stringResource(if (oneWay) R.string.results_flight_one_way else R.string.results_flight_round_trip), tone = PillTone.Info)
                    flight.source?.let { Pill(it.replace('_', ' ')) }
                    if (flight.isEstimate) Pill(stringResource(R.string.results_estimate), tone = PillTone.Warning)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        formatPrice(flight.priceUsd).ifEmpty { "—" },
                        style = TravelTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = TravelTheme.colors.primary,
                    )
                    Text(
                        stringResource(if (oneWay || perLeg) R.string.results_flight_per_person else R.string.results_flight_round_trip_pp),
                        style = TravelTheme.typography.labelSmall,
                        color = TravelTheme.colors.onSurfaceVariant,
                    )
                }
            }
            FlightLegRow(flight.outbound ?: flatLeg(flight), outbound = true, onLayover = onLayover)
            flight.returnLeg?.let {
                HorizontalDivider(color = TravelTheme.colors.outlineVariant)
                FlightLegRow(it, outbound = false, onLayover = onLayover)
            }
            ChipRow {
                BookingLinkButton(flight.bookingUrl)
                AddToPlanButton(selected = selected, itemName = name, onToggle = onToggle)
            }
        }
    }
}

/** Legacy flat flights carry the leg fields at the top level (web table fallback). */
private fun flatLeg(f: Flight): FlightLeg {
    fun s(k: String) = (f.raw[k] as? JsonPrimitive)?.contentOrNull
    return FlightLeg(
        airline = f.airlineName,
        flightNumber = f.flightNo,
        departureTime = s("departure_time"),
        arrivalTime = s("arrival_time"),
        durationMinutes = f.outboundMinutes,
        stops = f.outboundStops,
    )
}

@Composable
private fun FlightLegRow(leg: FlightLeg, outbound: Boolean, onLayover: (io.github.rupakc.travelplanner.core.model.section.FlightLayover) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
            Pill(stringResource(if (outbound) R.string.results_flight_depart else R.string.results_flight_return), tone = if (outbound) PillTone.Info else PillTone.Accent)
            Text(
                listOfNotNull(leg.airline ?: "—", leg.flightNumber).joinToString(" "),
                style = TravelTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            leg.stops?.let { stops ->
                if (stops == 0) {
                    Pill(stringResource(R.string.results_flight_direct), tone = PillTone.Positive)
                } else {
                    Pill(pluralStringResource(R.plurals.results_flight_stops, stops, stops), tone = PillTone.Warning)
                }
            }
        }
        val duration = leg.durationMinutes?.let { stringResource(R.string.results_duration_hm, it / MINUTES_PER_HOUR, it % MINUTES_PER_HOUR) }
        Text(
            listOfNotNull(
                "${leg.origin ?: "—"} → ${leg.destination ?: "—"}",
                "${leg.departureTime ?: "—"} – ${leg.arrivalTime ?: "—"}",
                duration,
            ).joinToString(" · "),
            style = TravelTheme.typography.bodySmall,
            color = TravelTheme.colors.onSurfaceVariant,
        )
        val layovers = leg.layovers.filter { it.city != null || it.airport != null }
        if (layovers.isNotEmpty()) {
            ChipRow {
                layovers.forEach { lv ->
                    val place = lv.city ?: lv.airport.orEmpty()
                    val long = (lv.durationHours ?: 0.0) >= LONG_LAYOVER_HOURS
                    val text =
                        lv.durationHours?.let { stringResource(R.string.results_layover_hours, formatHours(it), place) }
                            ?: stringResource(R.string.results_layover_stop, place)
                    Pill(text, tone = if (long) PillTone.Warning else PillTone.Neutral)
                    if (long) {
                        TextButton(onClick = { onLayover(lv) }) {
                            Text(stringResource(R.string.results_layover_make_most), style = TravelTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
}

/** "6" / "6.5" like the web's raw number. */
internal fun formatHours(hours: Double): String = if (hours % 1.0 == 0.0) hours.toLong().toString() else hours.toString()
