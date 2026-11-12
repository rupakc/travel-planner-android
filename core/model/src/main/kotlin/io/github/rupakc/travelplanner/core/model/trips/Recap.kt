package io.github.rupakc.travelplanner.core.model.trips

import io.github.rupakc.travelplanner.core.model.game.GameDelta
import io.github.rupakc.travelplanner.core.model.json.LenientDouble
import io.github.rupakc.travelplanner.core.model.json.LenientInt
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RecapCountry(
    val code: String,
    val name: String? = null,
    /** True when this trip added the country to the user's visited countries. */
    val new: Boolean = false,
)

/** [planCostUsd] is always null from the server (clients compute it). Amounts are decimal strings. */
@Serializable
data class RecapSpend(
    @SerialName("budget_usd") val budgetUsd: LenientDouble = null,
    @SerialName("plan_cost_usd") val planCostUsd: LenientDouble = null,
    @SerialName("spent_usd") val spentUsd: String? = null,
    @SerialName("by_category_usd") val byCategoryUsd: Map<String, String> = emptyMap(),
    val unconverted: List<String> = emptyList(),
)

/** `GET /trips/{id}/recap`. [route] holds `[lat, lng]` pairs. */
@Serializable
data class Recap(
    @SerialName("trip_id") val tripId: Long = 0,
    val title: String = "",
    @SerialName("start_date") val startDate: String? = null,
    @SerialName("end_date") val endDate: String? = null,
    val days: LenientInt = null,
    val nights: LenientInt = null,
    @SerialName("planned_days") val plannedDays: LenientInt = null,
    val places: List<TripPlace> = emptyList(),
    @SerialName("visited_count") val visitedCount: Int = 0,
    @SerialName("rated_count") val ratedCount: Int = 0,
    @SerialName("avg_stars") val avgStars: LenientDouble = null,
    val route: List<List<Double>> = emptyList(),
    @SerialName("distance_km") val distanceKm: LenientDouble = null,
    val countries: List<RecapCountry> = emptyList(),
    val continents: List<String> = emptyList(),
    val spend: RecapSpend = RecapSpend(),
    @SerialName("journal_count") val journalCount: Int = 0,
    @SerialName("checkin_days") val checkinDays: Int = 0,
    @SerialName("completed_at") val completedAt: String? = null,
    @SerialName("can_complete") val canComplete: Boolean = false,
)

/** `POST /trips/{id}/recap/complete` 200 (a repeat returns the first [completedAt] and no new countries). */
@Serializable
data class RecapCompleteResponse(
    @SerialName("completed_at") val completedAt: String? = null,
    @SerialName("new_countries") val newCountries: List<String> = emptyList(),
    val game: GameDelta? = null,
)
