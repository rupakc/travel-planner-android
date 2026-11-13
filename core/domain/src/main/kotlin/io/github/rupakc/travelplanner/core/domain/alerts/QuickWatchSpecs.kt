package io.github.rupakc.travelplanner.core.domain.alerts

import io.github.rupakc.travelplanner.core.domain.results.WatchSpecs
import io.github.rupakc.travelplanner.core.model.alerts.ActivityWatchSpec
import io.github.rupakc.travelplanner.core.model.alerts.FareWatchMatchRequest
import io.github.rupakc.travelplanner.core.model.alerts.HotelWatchSpec
import io.github.rupakc.travelplanner.core.model.alerts.PriceWatchKinds
import io.github.rupakc.travelplanner.core.model.alerts.TripWatchSpec
import io.github.rupakc.travelplanner.core.model.json.TravelJson
import io.github.rupakc.travelplanner.core.model.plan.PlanItem
import io.github.rupakc.travelplanner.core.model.search.TravelSearchRequest
import java.time.LocalDate
import kotlinx.serialization.json.JsonObject

/** What a one-tap `POST /alerts/quick-watch` needs for one item. */
data class QuickWatchDraft(val kind: String, val title: String, val subtitle: String?, val spec: JsonObject)

/**
 * Builds quick-watch bodies for items outside Results (chat cards, plan detail), from the same spec builders the
 * Results buttons use. Null when the item has nothing watchable (no dates, past departure, multi-city flights...).
 */
object QuickWatchSpecs {
    fun forItem(item: PlanItem, search: TravelSearchRequest?, today: LocalDate): QuickWatchDraft? =
        when (item) {
            is PlanItem.FlightItem -> {
                val trip = WatchTrip.from(search)
                if (trip == null || !WatchSpecs.isWatchableTrip(search, today)) {
                    null
                } else {
                    QuickWatchDraft(
                        "flight",
                        listOf(trip.origin, trip.destination).joinToString(" → "),
                        null,
                        TravelJson.encodeToJsonElement(FareWatchMatchRequest.serializer(), trip.toMatchRequest()) as JsonObject,
                    )
                }
            }

            is PlanItem.HotelItem -> {
                val name = item.hotel.name
                val spec = WatchSpecs.hotelWatchSpec(item.hotel, search, today)
                if (name == null || spec == null) {
                    null
                } else {
                    QuickWatchDraft(
                        PriceWatchKinds.HOTEL,
                        name,
                        item.hotel.city,
                        TravelJson.encodeToJsonElement(HotelWatchSpec.serializer(), spec) as JsonObject,
                    )
                }
            }

            is PlanItem.ActivityItem -> {
                val name = item.activity.name
                val spec = WatchSpecs.activitySpec(item.activity, search, null, today)
                if (name == null || spec == null) {
                    null
                } else {
                    QuickWatchDraft(
                        PriceWatchKinds.ACTIVITY,
                        name,
                        item.activity.city,
                        TravelJson.encodeToJsonElement(ActivityWatchSpec.serializer(), spec) as JsonObject,
                    )
                }
            }

            else -> {
                null
            }
        }

    /**
     * True when [item] belongs to [search]'s trip: its city (hotel, activity) or arrival (flight) is the trip's destination or one of
     * its stops. Chat cards can come from another question than the shared plan draft's trip, so the button is hidden when unsure.
     */
    fun matchesTrip(item: PlanItem, search: TravelSearchRequest?): Boolean {
        if (search == null) return false
        val places = (listOf(search.destination) + search.destinations.orEmpty()).map { WatchSpecs.foldedCityKey(it) }.filter { it.isNotEmpty() }
        val place =
            when (item) {
                is PlanItem.HotelItem -> item.hotel.city
                is PlanItem.ActivityItem -> item.activity.city
                is PlanItem.FlightItem -> item.flight.outbound?.destination
                else -> null
            }
        val key = WatchSpecs.foldedCityKey(place)
        return key.isNotEmpty() && key in places
    }

    /** The whole saved trip (kind `trip`, spec `{plan_id}`). */
    fun forTrip(planId: Long, name: String): QuickWatchDraft =
        QuickWatchDraft(PriceWatchKinds.TRIP, name, null, TravelJson.encodeToJsonElement(TripWatchSpec.serializer(), TripWatchSpec(planId)) as JsonObject)
}
