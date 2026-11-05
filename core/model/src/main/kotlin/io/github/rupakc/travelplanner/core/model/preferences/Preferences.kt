package io.github.rupakc.travelplanner.core.model.preferences

import io.github.rupakc.travelplanner.core.model.json.LenientInt
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `GET/PUT /api/preferences`. **PUT replaces all fields** (omitted ones reset to
 * default): always send the full object, read-modify-write.
 */
@Serializable
data class Preferences(
    val username: String? = null,
    /** low | medium | high */
    @SerialName("budget_category") val budgetCategory: String = "medium",
    val nationality: String = "",
    @SerialName("current_residence") val currentResidence: String = "",
    @SerialName("residence_permits") val residencePermits: List<String> = emptyList(),
    @SerialName("existing_visas") val existingVisas: List<String> = emptyList(),
    val interests: List<String> = emptyList(),
    @SerialName("num_travelers") val numTravelers: Int = 1,
    val adults: Int = 1,
    val children: Int = 0,
    val seniors: Int = 0,
    val infants: Int = 0,
    @SerialName("accessibility_needs") val accessibilityNeeds: List<String> = emptyList(),
    @SerialName("updated_at") val updatedAt: String? = null,
)

/** `GET /api/taste-profile`. */
@Serializable
data class TasteProfile(
    val signals: Map<String, List<TasteSignal>> = emptyMap(),
    @SerialName("total_signals") val totalSignals: LenientInt = null,
    val context: String? = null,
)

@Serializable
data class TasteSignal(val value: String? = null, val count: LenientInt = null)

/** `DELETE /api/taste-profile`. */
@Serializable
data class TasteResetResponse(val success: Boolean? = null, @SerialName("deleted_signals") val deletedSignals: LenientInt = null)

/** `GET /api/recent-searches` element. */
@Serializable
data class RecentSearch(
    val id: String,
    val search: RecentSearchIn,
    @SerialName("last_used_at") val lastUsedAt: String? = null,
)

/** `POST /api/recent-searches` body (also the stored `search`). */
@Serializable
data class RecentSearchIn(
    val origin: String,
    val destination: String,
    @SerialName("departure_date") val departureDate: String,
    @SerialName("return_date") val returnDate: String? = null,
    /** Max 3 intermediate stops. */
    val stops: List<RecentStop> = emptyList(),
    val nationality: String = "",
    @SerialName("residence_permits") val residencePermits: List<String> = emptyList(),
    @SerialName("existing_visas") val existingVisas: List<String> = emptyList(),
    val interests: List<String> = emptyList(),
    @SerialName("accessibility_needs") val accessibilityNeeds: List<String> = emptyList(),
    @SerialName("budget_usd") val budgetUsd: Double? = null,
    val adults: Int = 1,
    val children: Int = 0,
    val seniors: Int = 0,
    val infants: Int = 0,
    val serendipity: Double = 0.5,
)

@Serializable
data class RecentStop(val city: String, val nights: Int? = null)

