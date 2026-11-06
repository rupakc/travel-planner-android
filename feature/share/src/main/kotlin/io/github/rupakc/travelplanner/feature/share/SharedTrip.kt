package io.github.rupakc.travelplanner.feature.share

import androidx.compose.runtime.Immutable
import io.github.rupakc.travelplanner.core.domain.itinerary.PlanItinerary
import io.github.rupakc.travelplanner.core.domain.itinerary.ScheduledDay
import io.github.rupakc.travelplanner.core.domain.itinerary.SlotEdit
import io.github.rupakc.travelplanner.core.domain.place.airportCodesIn
import io.github.rupakc.travelplanner.core.domain.place.placeName
import io.github.rupakc.travelplanner.core.domain.place.placeRoute
import io.github.rupakc.travelplanner.core.domain.plan.computePlanCost
import io.github.rupakc.travelplanner.core.model.plan.PlanSelections
import io.github.rupakc.travelplanner.core.model.plan.SharedPlan
import io.github.rupakc.travelplanner.core.model.section.Flight
import io.github.rupakc.travelplanner.core.model.section.FlightLeg
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlinx.serialization.json.JsonArray

/** Which flight line this is (web share page tags). */
internal sealed interface FlightTag {
    data object Depart : FlightTag

    data object Return : FlightTag

    data object Single : FlightTag

    data class Leg(val number: Int) : FlightTag
}

@Immutable
internal data class SharedFlightRow(val tag: FlightTag, val leg: FlightLeg, val route: String?, val date: String?, val priceUsd: Double?)

/** A public shared plan, prepared like the web SharePage (`frontend/src/pages/SharePage.jsx`). */
@Immutable
internal data class SharedTrip(
    val name: String,
    val destination: String?,
    val origin: String?,
    val startDate: LocalDate?,
    val endDate: LocalDate?,
    val travellers: Int,
    val interests: List<String>,
    val plannedUsd: Double,
    val importTitle: String?,
    val importUrl: String?,
    val flights: List<SharedFlightRow>,
    /** Sum of leg prices when the plan has two or more legs. */
    val legsTotalUsd: Double?,
    /** The arranged, scheduled itinerary (same days as My Plan and the .ics export). */
    val itinerary: List<ScheduledDay>,
    /** True when [itinerary] is the full saved itinerary, false for individually picked slots. */
    val fullItinerary: Boolean,
    val itineraryEdits: Map<String, SlotEdit>,
    val itineraryNotes: Map<String, String>,
    val selections: PlanSelections,
) {
    companion object {
        /** The airport codes among [plan]'s origin, destination and stops (for the shared city lookup). */
        fun places(plan: SharedPlan): List<String> =
            airportCodesIn(listOf(plan.searchData.origin, plan.searchData.destination) + plan.searchData.destinations.orEmpty())

        /** [plan] as the share page shows it; airport codes [names] knows show as cities ("LIS" → "Lisbon"). */
        fun from(plan: SharedPlan, names: Map<String, String> = emptyMap()): SharedTrip {
            val sd = plan.searchData
            val sel = plan.selections
            val stops = sd.destinations.orEmpty()
            val full = sel.itinerarySnapshot?.get("days") is JsonArray && (sel.itinerarySnapshot?.get("days") as JsonArray).isNotEmpty()
            return SharedTrip(
                name = plan.name,
                destination = if (stops.size > 1) placeRoute(stops, names) else sd.destination?.let { placeName(it, names) },
                origin = sd.origin?.let { placeName(it, names) },
                startDate = sd.departureDate?.let(::date),
                endDate = sd.returnDate?.let(::date),
                travellers = sd.numTravelers?.takeIf { it > 0 } ?: 1,
                interests = sd.interests,
                plannedUsd = computePlanCost(sel, sd.departureDate, sd.returnDate),
                importTitle = sd.importSource?.title,
                importUrl = sd.importSource?.url,
                flights = flights(sel),
                legsTotalUsd = sel.flights.takeIf { it.size > 1 }?.sumOf { it.priceUsd ?: 0.0 },
                itinerary = PlanItinerary.days(sel),
                fullItinerary = full,
                itineraryEdits = SlotEdit.fromSelections(sel.itineraryEdits),
                itineraryNotes = sel.itineraryNotes.filterValues(String::isNotBlank),
                selections = sel,
            )
        }

        private fun date(value: String): LocalDate? = runCatching { LocalDate.parse(value.take(ISO_DATE_LENGTH)) }.getOrNull()

        private fun city(value: String?): String? = value?.substringBefore(',')?.trim()?.ifEmpty { null }

        private fun flights(sel: PlanSelections): List<SharedFlightRow> {
            if (sel.flights.isNotEmpty()) {
                return sel.flights.mapIndexed { i, f ->
                    val leg = f.outbound ?: FlightLeg()
                    val from = city(f.legFrom) ?: leg.origin
                    val to = city(f.legTo) ?: leg.destination
                    SharedFlightRow(FlightTag.Leg((f.legIndex ?: i) + 1), leg, route(from, to), f.legDate ?: leg.departureDate, f.priceUsd)
                }
            }
            val f: Flight = sel.flight ?: return emptyList()
            val out = f.outbound ?: return listOf(SharedFlightRow(FlightTag.Single, FlightLeg(), null, null, f.priceUsd))
            return listOfNotNull(
                SharedFlightRow(FlightTag.Depart, out, route(out.origin, out.destination), out.departureDate, f.priceUsd),
                f.returnLeg?.let { SharedFlightRow(FlightTag.Return, it, route(it.origin, it.destination), it.departureDate, null) },
            )
        }

        private fun route(from: String?, to: String?): String? = if (from != null && to != null) "$from → $to" else from ?: to

        private const val ISO_DATE_LENGTH = 10
    }
}

/** Share-link parsing: `travelplanner://share/{token}` and `http(s)://<host>/share/{token}` (web route). */
internal object ShareLinks {
    private val TOKEN = Regex("^[A-Za-z0-9_-]{1,128}$")

    fun tokenFrom(uri: String?): String? {
        val parsed = runCatching { java.net.URI(uri?.trim() ?: return null) }.getOrNull() ?: return null
        val segments = parsed.path.orEmpty().split('/').filter(String::isNotEmpty)
        val token =
            when (parsed.scheme?.lowercase()) {
                "travelplanner" -> if (parsed.host == "share") segments.singleOrNull() else null
                "http", "https" -> segments.takeIf { it.size >= 2 && it[it.size - 2] == "share" }?.last()
                else -> null
            }
        return token?.takeIf(::isValidToken)
    }

    /** Share tokens are URL-safe base64 (22 chars today); anything else never reaches the network. */
    fun isValidToken(token: String): Boolean = TOKEN.matches(token)
}
