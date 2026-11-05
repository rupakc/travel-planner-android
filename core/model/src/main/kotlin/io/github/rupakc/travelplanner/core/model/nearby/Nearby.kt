package io.github.rupakc.travelplanner.core.model.nearby

import io.github.rupakc.travelplanner.core.model.json.LenientDouble
import io.github.rupakc.travelplanner.core.model.section.Activity
import io.github.rupakc.travelplanner.core.model.section.ActivityListSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** `POST /api/nearby` body (spec §7; auth, light_ai bucket). 404 `no_place` when nothing is near. */
@Serializable
data class NearbyRequest(
    val lat: Double,
    val lng: Double,
    val interests: List<String>? = null,
    val limit: Int = DEFAULT_LIMIT,
) {
    companion object {
        const val DEFAULT_LIMIT = 10
    }
}

/**
 * `{place, items[]}`; items have the activities item shape. Errors: 404
 * `detail:{code:"no_place"}`, 502 `detail:{code:"agent_error"}`. A cache miss takes 10–40 s.
 */
@Serializable
data class NearbyResponse(
    val place: NearbyPlace? = null,
    @Serializable(with = ActivityListSerializer::class) val items: List<Activity> = emptyList(),
)

@Serializable
data class NearbyPlace(
    val name: String? = null,
    val region: String? = null,
    val country: String? = null,
    @SerialName("country_code") val countryCode: String? = null,
    val lat: LenientDouble = null,
    val lng: LenientDouble = null,
    @SerialName("distance_km") val distanceKm: LenientDouble = null,
)
