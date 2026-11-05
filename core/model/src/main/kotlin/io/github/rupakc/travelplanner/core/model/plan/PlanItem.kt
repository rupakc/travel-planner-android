package io.github.rupakc.travelplanner.core.model.plan

import io.github.rupakc.travelplanner.core.model.section.Activity
import io.github.rupakc.travelplanner.core.model.section.Flight
import io.github.rupakc.travelplanner.core.model.section.Hotel
import io.github.rupakc.travelplanner.core.model.section.ItinerarySlot
import io.github.rupakc.travelplanner.core.model.section.PlaceToSee
import io.github.rupakc.travelplanner.core.model.section.SimPlan
import io.github.rupakc.travelplanner.core.model.section.Tip
import io.github.rupakc.travelplanner.core.model.section.TransportOption
import io.github.rupakc.travelplanner.core.model.section.TravelEvent
import io.github.rupakc.travelplanner.core.model.section.WalkingTour

/**
 * One selectable thing in My Plan. Single-choice categories (flight, hotel,
 * sim; and one flight per multi-city leg) replace; list categories add/remove.
 * Identity ([key]) follows the web's dedupe rules (api-contract §5).
 */
sealed interface PlanItem {
    val key: String

    /** A flight. With a non-null `leg_index` it is a multi-city leg pick (stored in `flights`, one per leg). */
    data class FlightItem(val flight: Flight) : PlanItem {
        override val key: String
            get() = listOf(flight.legIndex, flight.priceUsd, flight.outbound?.airline, flight.outbound?.flightNumber).joinToString("|")
        val isLeg: Boolean get() = flight.legIndex != null
    }

    data class HotelItem(val hotel: Hotel) : PlanItem {
        override val key: String get() = hotel.name.orEmpty()
    }

    data class SimItem(val plan: SimPlan) : PlanItem {
        override val key: String get() = "${plan.provider}|${plan.planName}"
    }

    data class ActivityItem(val activity: Activity) : PlanItem {
        override val key: String get() = activity.name.orEmpty()
    }

    data class PlaceItem(val place: PlaceToSee) : PlanItem {
        override val key: String get() = place.name.orEmpty()
    }

    /** Dedupe by `url`. */
    data class WalkingTourItem(val tour: WalkingTour) : PlanItem {
        override val key: String get() = tour.url.orEmpty()
    }

    data class EventItem(val event: TravelEvent) : PlanItem {
        override val key: String get() = event.name.orEmpty()
    }

    /** Dedupe by `title`. */
    data class TipItem(val tip: Tip) : PlanItem {
        override val key: String get() = tip.title.orEmpty()
    }

    data class TransportItem(val option: TransportOption) : PlanItem {
        override val key: String get() = option.name.orEmpty()
    }

    /** Dedupe by slot `key` ("<day>-<time_of_day>"). */
    data class SlotItem(val slot: ItinerarySlot) : PlanItem {
        override val key: String get() = slot.key.orEmpty()
    }
}

/** Pure selection rules shared by [PlanItem] consumers (CurrentPlanStore, chat plan actions). */
object PlanSelectionRules {
    fun isSelected(selections: PlanSelections, item: PlanItem): Boolean =
        when (item) {
            is PlanItem.FlightItem -> {
                if (item.isLeg) {
                    selections.flights.any { it.sameAs(item.flight) }
                } else {
                    selections.flight?.sameAs(item.flight) == true
                }
            }

            is PlanItem.HotelItem -> {
                selections.hotel?.name == item.hotel.name && selections.hotel != null
            }

            is PlanItem.SimItem -> {
                selections.sim?.let { PlanItem.SimItem(it).key == item.key } == true
            }

            is PlanItem.ActivityItem -> {
                selections.activities.any { it.name == item.activity.name }
            }

            is PlanItem.PlaceItem -> {
                selections.placesToSee.any { it.name == item.place.name }
            }

            is PlanItem.WalkingTourItem -> {
                selections.walkingTours.any { it.url == item.tour.url }
            }

            is PlanItem.EventItem -> {
                selections.events.any { it.name == item.event.name }
            }

            is PlanItem.TipItem -> {
                selections.tips.any { it.title == item.tip.title }
            }

            is PlanItem.TransportItem -> {
                selections.gettingAround.any { it.name == item.option.name }
            }

            is PlanItem.SlotItem -> {
                selections.itinerarySlots.any { it.key == item.slot.key }
            }
        }

    /** Selects [item] if not selected, else deselects it. */
    @Suppress("CyclomaticComplexMethod")
    fun toggle(selections: PlanSelections, item: PlanItem): PlanSelections {
        val selected = isSelected(selections, item)
        return when (item) {
            is PlanItem.FlightItem -> {
                if (item.isLeg) {
                    val others = selections.flights.filterNot { it.legIndex == item.flight.legIndex }
                    selections.copy(flights = if (selected) others else (others + item.flight).sortedBy { it.legIndex })
                } else {
                    selections.copy(flight = if (selected) null else item.flight)
                }
            }

            is PlanItem.HotelItem -> {
                selections.copy(hotel = if (selected) null else item.hotel)
            }

            is PlanItem.SimItem -> {
                selections.copy(sim = if (selected) null else item.plan)
            }

            is PlanItem.ActivityItem -> {
                selections.copy(activities = selections.activities.toggled(item.activity, selected) { it.name })
            }

            is PlanItem.PlaceItem -> {
                selections.copy(placesToSee = selections.placesToSee.toggled(item.place, selected) { it.name })
            }

            is PlanItem.WalkingTourItem -> {
                selections.copy(walkingTours = selections.walkingTours.toggled(item.tour, selected) { it.url })
            }

            is PlanItem.EventItem -> {
                selections.copy(events = selections.events.toggled(item.event, selected) { it.name })
            }

            is PlanItem.TipItem -> {
                selections.copy(tips = selections.tips.toggled(item.tip, selected) { it.title })
            }

            is PlanItem.TransportItem -> {
                selections.copy(gettingAround = selections.gettingAround.toggled(item.option, selected) { it.name })
            }

            is PlanItem.SlotItem -> {
                selections.copy(itinerarySlots = selections.itinerarySlots.toggled(item.slot, selected) { it.key })
            }
        }
    }

    private inline fun <T> List<T>.toggled(item: T, selected: Boolean, key: (T) -> Any?): List<T> =
        if (selected) filterNot { key(it) == key(item) } else this + item
}
