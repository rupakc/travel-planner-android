package io.github.rupakc.travelplanner.feature.home

import io.github.rupakc.travelplanner.core.domain.feature.FeatureId
import io.github.rupakc.travelplanner.core.domain.place.airportCodesIn
import io.github.rupakc.travelplanner.core.domain.place.placeName
import io.github.rupakc.travelplanner.core.model.plan.Plan
import io.github.rupakc.travelplanner.core.model.preferences.RecentSearch
import io.github.rupakc.travelplanner.core.model.preferences.TasteProfile
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Why a feature is suggested. Every reason is read straight from a recent search
 * (`/api/recent-searches`) or the Taste Graph (`/api/taste-profile`); nothing is guessed.
 * Declaration order = priority when several features are boosted.
 */
enum class TileReason {
    /** The search had a budget → Budget planner. */
    SEARCHED_WITH_BUDGET,

    /** The search had accessibility needs → Accessibility info. */
    SEARCHED_WITH_ACCESSIBILITY,

    /** Children or infants in the search → Packing list. */
    SEARCHED_WITH_KIDS,

    /** Stops on the way → Itinerary editor. */
    SEARCHED_MULTI_CITY,

    /** Two or more different destinations across recent searches → Compare. */
    SEARCHED_SEVERAL_DESTINATIONS,

    /** A destination was searched → Price alerts for it. */
    SEARCHED_DESTINATION,

    /** Taste Graph interests (culture, nightlife, art) → Events. */
    TASTE_EVENTS,

    /** Taste Graph interests (history, culture) → Free walking tours. */
    TASTE_WALKING,
}

/** A Home tile: one visible feature plus the reason it moved up, if any. */
data class FeatureTile(val feature: FeatureId, val reason: TileReason? = null, val iconName: String? = null)

/** "Because you searched {destination}": the features the latest search points at. */
data class BecauseYouSearched(val recentSearchId: String, val destination: String, val tiles: List<FeatureTile>)

/** The nearest saved trip (from `PlansRepository`); dates are the plan's own search dates. */
data class UpcomingTrip(
    val planId: Long,
    val name: String,
    val destination: String,
    val departure: LocalDate,
    val returnDate: LocalDate?,
    /** Days from today to departure (0 = today); negative while the trip is under way. */
    val daysUntil: Long,
) {
    val ongoing: Boolean get() = daysUntil < 0
}

/** Pure Home personalisation (spec §6.3 + brief item 8). */
internal object HomePersonalisation {
    private val eventInterests = setOf("culture", "nightlife", "art")
    private val walkingInterests = setOf("history", "culture")
    private const val INTEREST_SIGNAL = "interest"

    /** Reasons from one recent search. */
    fun reasonsFor(search: RecentSearch): Map<FeatureId, TileReason> =
        buildMap {
            val s = search.search
            if ((s.budgetUsd ?: 0.0) > 0.0) put(FeatureId.BUDGET_PLANNER, TileReason.SEARCHED_WITH_BUDGET)
            if (s.accessibilityNeeds.isNotEmpty()) put(FeatureId.ACCESSIBILITY_INFO, TileReason.SEARCHED_WITH_ACCESSIBILITY)
            if (s.children + s.infants > 0) put(FeatureId.PACKING_LIST, TileReason.SEARCHED_WITH_KIDS)
            if (s.stops.isNotEmpty()) put(FeatureId.ITINERARY_EDITOR, TileReason.SEARCHED_MULTI_CITY)
            if (s.destination.isNotBlank()) put(FeatureId.DEAL_ALERTS, TileReason.SEARCHED_DESTINATION)
        }

    /** Reasons from all recent searches plus the Taste Graph (first reason per feature wins, by priority). */
    fun reasons(recent: List<RecentSearch>, taste: TasteProfile?): Map<FeatureId, TileReason> {
        val all = mutableMapOf<FeatureId, TileReason>()

        fun offer(feature: FeatureId, reason: TileReason) {
            val existing = all[feature]
            if (existing == null || reason.ordinal < existing.ordinal) all[feature] = reason
        }
        recent.forEach { search -> reasonsFor(search).forEach { (f, r) -> offer(f, r) } }
        val destinations = recent.map { it.search.destination.trim().lowercase() }.filter(String::isNotEmpty).toSet()
        if (destinations.size >= 2) offer(FeatureId.COMPARE, TileReason.SEARCHED_SEVERAL_DESTINATIONS)
        val interests = taste?.signals?.get(INTEREST_SIGNAL).orEmpty().mapNotNull { it.value?.lowercase() }.toSet()
        if (interests.any { it in eventInterests }) offer(FeatureId.EVENTS, TileReason.TASTE_EVENTS)
        if (interests.any { it in walkingInterests }) offer(FeatureId.WALKING_TOURS, TileReason.TASTE_WALKING)
        return all
    }

    /** Visible features as tiles: boosted ones first (by reason priority), then catalogue order. */
    fun orderTiles(visible: Set<FeatureId>, reasons: Map<FeatureId, TileReason>): List<FeatureTile> =
        FeatureId.entries
            .filter { it in visible }
            .map { FeatureTile(it, reasons[it]) }
            .sortedWith(compareBy<FeatureTile> { it.reason?.ordinal ?: Int.MAX_VALUE }.thenBy { it.feature.ordinal })

    /** The "Because you searched …" row for the latest search, limited to visible features; null when nothing applies. */
    fun becauseYouSearched(recent: List<RecentSearch>, visible: Set<FeatureId>): BecauseYouSearched? {
        val latest = recent.firstOrNull() ?: return null
        val destination = latest.search.destination.trim().takeIf(String::isNotEmpty) ?: return null
        val tiles =
            reasonsFor(latest)
                .filterKeys { it in visible }
                .map { (f, r) -> FeatureTile(f, r) }
                .sortedBy { it.reason?.ordinal }
        return tiles.takeIf { it.isNotEmpty() }?.let { BecauseYouSearched(latest.id, destination, it) }
    }

    /** Top Taste Graph values (highest count first), at most [limit]; only what the API returned. */
    fun tasteHighlights(taste: TasteProfile?, limit: Int = 4): List<String> =
        taste?.signals?.values.orEmpty()
            .flatten()
            .filter { !it.value.isNullOrBlank() }
            .sortedByDescending { it.count ?: 0 }
            .mapNotNull { it.value }
            .distinctBy { it.lowercase() }
            .take(limit)
}

/** Picks the trip on Home: a trip under way first, else the nearest future departure. */
internal object UpcomingTrips {
    fun nearest(plans: List<Plan>, today: LocalDate, names: Map<String, String> = emptyMap()): UpcomingTrip? =
        plans.mapNotNull { plan -> toTrip(plan, today, names) }
            .filter { trip -> !trip.departure.isBefore(today) || (trip.returnDate != null && !trip.returnDate.isBefore(today)) }
            .minWithOrNull(compareBy<UpcomingTrip> { if (it.ongoing) 0 else 1 }.thenBy { it.departure }.thenBy { it.planId })

    private fun toTrip(plan: Plan, today: LocalDate, names: Map<String, String>): UpcomingTrip? {
        val request = plan.searchRequest ?: return null
        val departure = parseDate(request.departureDate) ?: return null
        val destination = placeName(request.destinations?.lastOrNull()?.takeIf(String::isNotBlank) ?: request.destination, names)
        return UpcomingTrip(
            planId = plan.id,
            name = plan.name.ifBlank { destination },
            destination = destination,
            departure = departure,
            returnDate = request.returnDate?.let(::parseDate),
            daysUntil = ChronoUnit.DAYS.between(today, departure),
        )
    }

    private fun parseDate(text: String): LocalDate? = runCatching { LocalDate.parse(text.take(ISO_DATE_LENGTH)) }.getOrNull()

    private const val ISO_DATE_LENGTH = 10
}

/** The places Home shows (saved trips' and recent searches' origins, stops and destinations), for the city lookup. */
internal object HomePlaces {
    fun of(plans: List<Plan>, recent: List<RecentSearch>): List<String> =
        airportCodesIn(
            plans.flatMap { p -> p.searchRequest?.let { listOf(it.origin, it.destination) + it.destinations.orEmpty() }.orEmpty() } +
                recent.flatMap { r -> listOf(r.search.origin, r.search.destination) + r.search.stops.map { it.city } },
        )
}
