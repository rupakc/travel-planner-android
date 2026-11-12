package io.github.rupakc.travelplanner.core.model.trips

import io.github.rupakc.travelplanner.core.model.game.GameDelta
import io.github.rupakc.travelplanner.core.model.json.LenientDouble
import io.github.rupakc.travelplanner.core.model.json.LenientInt
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Trip phase; an unknown wire value decodes to null (the field default). */
@Serializable
enum class Phase(val wire: String) {
    @SerialName("dreaming")
    DREAMING("dreaming"),

    @SerialName("planning")
    PLANNING("planning"),

    @SerialName("ready")
    READY("ready"),

    @SerialName("on_the_road")
    ON_THE_ROAD("on_the_road"),

    @SerialName("memories")
    MEMORIES("memories"),
    ;

    companion object {
        /** The phase for a wire value, or null. */
        fun fromWire(value: String?): Phase? = entries.firstOrNull { it.wire == value }
    }
}

/** Prep summary of a trip or of `GET /trips/{id}/prep`. */
@Serializable
data class PrepSummary(
    val done: Int = 0,
    val total: Int = 0,
    val percent: Int = 0,
    val complete: Boolean = false,
)

/** `Trip` (Trips-API "Shared objects"). Dates are `YYYY-MM-DD`, timestamps ISO 8601 UTC. */
@Serializable
data class Trip(
    val id: Long,
    val title: String = "",
    val destination: String? = null,
    val destinations: List<String> = emptyList(),
    val origin: String? = null,
    @SerialName("start_date") val startDate: String? = null,
    @SerialName("end_date") val endDate: String? = null,
    val nights: LenientInt = null,
    val phase: Phase? = null,
    /** `auto` | `manual`. */
    @SerialName("phase_source") val phaseSource: String? = null,
    @SerialName("days_to_start") val daysToStart: LenientInt = null,
    @SerialName("day_index") val dayIndex: LenientInt = null,
    @SerialName("num_travelers") val numTravelers: LenientInt = null,
    @SerialName("budget_usd") val budgetUsd: LenientDouble = null,
    val countries: List<String> = emptyList(),
    val prep: PrepSummary = PrepSummary(),
    @SerialName("has_itinerary") val hasItinerary: Boolean = false,
    @SerialName("recap_completed_at") val recapCompletedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

/** `GET /trips`. */
@Serializable
data class TripsResponse(
    val trips: List<Trip> = emptyList(),
    @SerialName("pinned_id") val pinnedId: Long? = null,
    val today: String? = null,
)

/** `POST /trips`. Nulls are omitted. */
@Serializable
data class CreateTripRequest(
    val title: String,
    val destination: String? = null,
    @SerialName("start_date") val startDate: String? = null,
    @SerialName("end_date") val endDate: String? = null,
)

/** One continent-or-country entry of `GET /trips/stats`. [source] is `checkin` | `recap`. */
@Serializable
data class CountryStat(
    val code: String,
    val name: String? = null,
    @SerialName("first_trip_id") val firstTripId: Long? = null,
    @SerialName("visited_at") val visitedAt: String? = null,
    val source: String? = null,
)

/** `GET /trips/stats` ([Stats] in the plan's naming). */
@Serializable
data class Stats(
    val countries: List<CountryStat> = emptyList(),
    val continents: List<String> = emptyList(),
    @SerialName("bucket_goal") val bucketGoal: LenientInt = null,
    @SerialName("trips_by_phase") val tripsByPhase: Map<String, Int> = emptyMap(),
)

/** `POST /trips/{id}/checkin`. */
@Serializable
data class CheckinRequest(val day: String)

/** `POST /trips/{id}/checkin` 200. */
@Serializable
data class CheckinResponse(
    val day: String? = null,
    @SerialName("day_index") val dayIndex: LenientInt = null,
    val city: String? = null,
    @SerialName("country_code") val countryCode: String? = null,
    @SerialName("new_country") val newCountry: Boolean = false,
    val streak: Streak? = null,
    val game: GameDelta? = null,
)

/** Check-in streak (same shape as the game streak). */
@Serializable
data class Streak(
    val current: Int = 0,
    val longest: Int = 0,
    @SerialName("last_day") val lastDay: String? = null,
    val freezes: Int = 0,
)

/** `GET /trips/{id}/checkins`. */
@Serializable
data class CheckinsResponse(
    val days: List<String> = emptyList(),
    val streak: Streak? = null,
)
