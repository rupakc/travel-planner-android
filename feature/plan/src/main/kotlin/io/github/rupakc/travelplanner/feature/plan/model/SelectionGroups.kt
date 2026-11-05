package io.github.rupakc.travelplanner.feature.plan.model

import androidx.compose.runtime.Immutable
import io.github.rupakc.travelplanner.core.domain.export.PlanIcs
import io.github.rupakc.travelplanner.core.model.plan.PlanItem
import io.github.rupakc.travelplanner.core.model.plan.PlanSelections
import io.github.rupakc.travelplanner.core.model.plan.PlanSelectionsSerializer
import io.github.rupakc.travelplanner.core.model.section.Flight
import io.github.rupakc.travelplanner.core.model.section.SectionId
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull

/**
 * The My Plan groups, in the web drawer's order. [section] picks the accent colour and the
 * shared section title where one exists.
 */
internal enum class PlanCategory(val section: SectionId) {
    FLIGHT(SectionId.FLIGHTS),
    FLIGHT_LEGS(SectionId.FLIGHTS),
    HOTEL(SectionId.HOTELS),
    ACTIVITIES(SectionId.ACTIVITIES),
    PLACES(SectionId.PLACES_TO_SEE),
    WALKING_TOURS(SectionId.WALKING_TOURS),
    SIM(SectionId.SIM),
    GETTING_AROUND(SectionId.GETTING_AROUND),
    TIPS(SectionId.TIPS),
    EVENTS(SectionId.EVENTS),
    NOTES(SectionId.ITINERARY),
    ITINERARY_SLOTS(SectionId.ITINERARY),
    PACKING_LIST(SectionId.PACKING_LIST),
}

/** How an entry leaves the plan (null = it cannot be removed on its own). */
internal sealed interface Removal {
    data class Item(val item: PlanItem) : Removal

    data class Note(val slotKey: String) : Removal

    data object PackingList : Removal
}

/** One selected thing, already reduced to what the UI shows. Every value comes from the plan JSON. */
@Immutable
internal data class SelectionEntry(
    val key: String,
    val title: String?,
    val details: List<String> = emptyList(),
    val priceUsd: Double? = null,
    val isEstimate: Boolean = false,
    val perNight: Boolean = false,
    /** Place with `admission_fee_usd == 0` (web "Free entry"). */
    val isFree: Boolean = false,
    val url: String? = null,
    /** Multi-city leg number (1-based) for flight legs. */
    val legNumber: Int? = null,
    /** Itinerary slot / note position. */
    val dayNumber: Int? = null,
    val timeOfDay: String? = null,
    /** Packing list progress. */
    val packedCount: Int? = null,
    val packTotal: Int? = null,
    val removal: Removal? = null,
)

@Immutable
internal data class SelectionGroup(val category: PlanCategory, val entries: List<SelectionEntry>)

internal object SelectionGroups {
    @Suppress("LongMethod") // One declarative mapping per web drawer category; splitting it hides the order.
    fun of(selections: PlanSelections): List<SelectionGroup> =
        listOf(
            group(PlanCategory.FLIGHT, listOfNotNull(selections.flight).map { flightEntry(it, null) }),
            group(PlanCategory.FLIGHT_LEGS, selections.flights.mapIndexed { i, f -> flightEntry(f, (f.legIndex ?: i) + 1) }),
            group(
                PlanCategory.HOTEL,
                listOfNotNull(selections.hotel).map { h ->
                    val total = h.totalPriceUsd?.takeIf { it > 0 }
                    SelectionEntry(
                        key = "hotel|${h.name}",
                        title = h.name,
                        details = listOfNotNull(h.location, h.city),
                        priceUsd = total ?: h.pricePerNightUsd?.takeIf { it > 0 },
                        perNight = total == null && h.pricePerNightUsd != null,
                        isEstimate = h.source == ESTIMATE,
                        url = h.bookingUrl,
                        removal = Removal.Item(PlanItem.HotelItem(h)),
                    )
                },
            ),
            group(
                PlanCategory.ACTIVITIES,
                selections.activities.map { a ->
                    SelectionEntry(
                        key = "activity|${a.name}",
                        title = a.name,
                        details = listOfNotNull(a.location ?: a.city),
                        priceUsd = a.priceUsd?.takeIf { it > 0 },
                        url = a.bookingUrl,
                        removal = Removal.Item(PlanItem.ActivityItem(a)),
                    )
                },
            ),
            group(
                PlanCategory.PLACES,
                selections.placesToSee.map { p ->
                    SelectionEntry(
                        key = "place|${p.name}",
                        title = p.name,
                        details = listOfNotNull(p.neighbourhood ?: p.address),
                        priceUsd = p.admissionFeeUsd?.takeIf { it > 0 },
                        isFree = p.admissionFeeUsd == 0.0,
                        url = p.infoUrl,
                        removal = Removal.Item(PlanItem.PlaceItem(p)),
                    )
                },
            ),
            group(
                PlanCategory.WALKING_TOURS,
                selections.walkingTours.map { t ->
                    SelectionEntry(
                        key = "tour|${t.url}",
                        title = t.name,
                        details = listOfNotNull(t.provider, t.city),
                        url = t.url,
                        removal = Removal.Item(PlanItem.WalkingTourItem(t)),
                    )
                },
            ),
            group(
                PlanCategory.SIM,
                listOfNotNull(selections.sim).map { s ->
                    SelectionEntry(
                        key = "sim|${s.provider}|${s.planName}",
                        title = listOfNotNull(s.provider, s.planName).joinToString(" — ").ifEmpty { null },
                        details = listOfNotNull(s.dataGb?.let { "$it GB" }, s.purchaseLocation),
                        priceUsd = s.priceUsd?.takeIf { it > 0 },
                        url = s.url,
                        removal = Removal.Item(PlanItem.SimItem(s)),
                    )
                },
            ),
            group(
                PlanCategory.GETTING_AROUND,
                selections.gettingAround.map { o ->
                    SelectionEntry(
                        key = "transport|${o.name}",
                        title = o.name,
                        details = listOfNotNull(o.type?.replace('_', ' '), o.priceInfo),
                        url = o.bookingUrl,
                        removal = Removal.Item(PlanItem.TransportItem(o)),
                    )
                },
            ),
            group(
                PlanCategory.TIPS,
                selections.tips.map { t ->
                    SelectionEntry(
                        key = "tip|${t.title}",
                        title = t.title,
                        details = listOfNotNull(t.body),
                        url = t.sourceUrl,
                        removal = Removal.Item(PlanItem.TipItem(t)),
                    )
                },
            ),
            group(
                PlanCategory.EVENTS,
                selections.events.map { e ->
                    val dates = listOfNotNull(e.startDate, e.endDate?.takeIf { it != e.startDate }).joinToString(" – ")
                    SelectionEntry(
                        key = "event|${e.name}",
                        title = e.name,
                        details = listOfNotNull(dates.ifEmpty { null }, e.location, e.price),
                        removal = Removal.Item(PlanItem.EventItem(e)),
                    )
                },
            ),
            group(
                PlanCategory.NOTES,
                selections.itineraryNotes.filterValues(String::isNotBlank).map { (key, text) ->
                    val (day, tod) = parseSlotKey(key)
                    SelectionEntry(key = "note|$key", title = text, dayNumber = day, timeOfDay = tod, removal = Removal.Note(key))
                },
            ),
            group(
                PlanCategory.ITINERARY_SLOTS,
                selections.itinerarySlots.map { s ->
                    SelectionEntry(
                        key = "slot|${s.key}",
                        title = s.activity,
                        details = listOfNotNull(s.location),
                        priceUsd = s.estimatedCostUsd?.takeIf { it > 0 },
                        isEstimate = s.estimatedCostUsd != null,
                        dayNumber = s.dayNumber,
                        timeOfDay = s.timeOfDay,
                        removal = Removal.Item(PlanItem.SlotItem(s)),
                    )
                },
            ),
            group(
                PlanCategory.PACKING_LIST,
                listOfNotNull(selections.packingList).map { p ->
                    SelectionEntry(
                        key = "packing",
                        title = null,
                        packedCount = p.int("checked_count"),
                        packTotal = p.int("total_items"),
                        removal = Removal.PackingList,
                    )
                },
            ),
        ).filter { it.entries.isNotEmpty() }

    /** [selections] without the entry [removal] points at. */
    fun remove(selections: PlanSelections, removal: Removal): PlanSelections =
        when (removal) {
            is Removal.Item -> {
                if (io.github.rupakc.travelplanner.core.model.plan.PlanSelectionRules.isSelected(selections, removal.item)) {
                    io.github.rupakc.travelplanner.core.model.plan.PlanSelectionRules.toggle(selections, removal.item)
                } else {
                    selections
                }
            }

            is Removal.Note -> {
                selections.copy(itineraryNotes = selections.itineraryNotes - removal.slotKey)
            }

            Removal.PackingList -> {
                selections.copy(packingList = null)
            }
        }

    /** "2-afternoon" (or "2-afternoon#2") → (2, "afternoon"). */
    fun parseSlotKey(key: String): Pair<Int?, String?> {
        val base = key.substringBefore('#')
        val day = base.substringBefore('-', "").toIntOrNull()
        return day to base.substringAfter('-', "").ifEmpty { null }
    }

    private fun flightEntry(f: Flight, legNumber: Int?): SelectionEntry {
        val leg = f.outbound
        val route =
            if (leg?.origin != null && leg.destination != null) {
                "${leg.origin} → ${leg.destination}"
            } else {
                listOfNotNull(f.legFrom, f.legTo).joinToString(" → ") { it.substringBefore(',').trim() }.ifEmpty { null }
            }
        val departs = listOfNotNull(leg?.departureDate ?: f.legDate, leg?.departureTime).joinToString(" ").ifEmpty { null }
        return SelectionEntry(
            key = "flight|${PlanItem.FlightItem(f).key}",
            title = listOfNotNull(leg?.airline, leg?.flightNumber).joinToString(" ").ifEmpty { null },
            details = listOfNotNull(route, departs),
            priceUsd = f.priceUsd?.takeIf { it > 0 },
            isEstimate = f.isEstimate,
            url = f.bookingUrl,
            legNumber = legNumber,
            removal = Removal.Item(PlanItem.FlightItem(f)),
        )
    }

    private fun group(category: PlanCategory, entries: List<SelectionEntry>) = SelectionGroup(category, entries)

    private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull

    private const val ESTIMATE = "estimate"
}

/** One itinerary slot as the read-only itinerary shows it (edits and notes applied). */
@Immutable
internal data class ItinerarySlotUi(
    val key: String,
    val timeOfDay: String?,
    val timeRange: String?,
    val activity: String?,
    val location: String?,
    val note: String?,
    val slotNotes: String?,
    val costUsd: Double?,
    val fromLink: Boolean,
)

@Immutable
internal data class ItineraryDayUi(
    val dayNumber: Int?,
    val date: String?,
    val city: String?,
    val theme: String?,
    val slots: List<ItinerarySlotUi>,
)

/** The plan's arranged, scheduled itinerary (same days the .ics export uses). */
internal object ItineraryUi {
    fun of(selections: PlanSelections, liveItinerary: JsonObject? = null): List<ItineraryDayUi> {
        val selectionsJson = PlanSelectionsSerializer.toJson(selections)
        return PlanIcs.planItineraryDays(selectionsJson, liveItinerary).map { day ->
            ItineraryDayUi(
                dayNumber = day["day_number"].prim()?.intOrNull,
                date = day["date"].text(),
                city = day["city"].text(),
                theme = day["theme"].text(),
                slots =
                    (day["slots"] as? kotlinx.serialization.json.JsonArray).orEmpty().filterIsInstance<JsonObject>().map { slot ->
                        val key = slot["key"].text().orEmpty()
                        val edit = selections.itineraryEdits[key]
                        val start = slot["start_time"].text()
                        val end = slot["end_time"].text()
                        ItinerarySlotUi(
                            key = key,
                            timeOfDay = slot["time_of_day"].text(),
                            timeRange = if (start != null && end != null) "$start–$end" else null,
                            activity = edit?.get("activity").text() ?: slot["activity"].text(),
                            location = (edit?.get("location") ?: slot["location"]).text(),
                            note = selections.itineraryNotes[key]?.takeIf(String::isNotBlank),
                            slotNotes = slot["notes"].text(),
                            costUsd = ((edit?.get("cost") ?: slot["estimated_cost_usd"]).prim()?.content?.toDoubleOrNull())?.takeIf { it > 0 },
                            fromLink = slot["from_link"].prim()?.booleanOrNull == true,
                        )
                    },
            )
        }
    }

    private fun JsonElement?.prim(): JsonPrimitive? = (this as? JsonPrimitive)?.takeIf { it !is kotlinx.serialization.json.JsonNull }

    private fun JsonElement?.text(): String? = prim()?.content?.takeIf(String::isNotBlank)
}
