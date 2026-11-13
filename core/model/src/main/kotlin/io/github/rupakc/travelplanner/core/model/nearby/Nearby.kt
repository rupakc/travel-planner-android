package io.github.rupakc.travelplanner.core.model.nearby

import io.github.rupakc.travelplanner.core.model.grounding.GroundingSource
import io.github.rupakc.travelplanner.core.model.grounding.GroundingSourceListSerializer
import io.github.rupakc.travelplanner.core.model.grounding.TolerantListSerializer
import io.github.rupakc.travelplanner.core.model.json.LenientDouble
import io.github.rupakc.travelplanner.core.model.json.LenientString
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `POST /api/nearby` v2 body (auth, light_ai bucket): `sections: true` asks for the four
 * Google Maps–grounded sections. [lat]/[lng] are rounded to 3 decimals (~100 m) by the repository.
 * [limit] is per section (1..10). [language] is the UI language
 * for the one-line summaries (null = the server default).
 */
@Serializable
data class NearbyRequest(
    val lat: Double,
    val lng: Double,
    val limit: Int = DEFAULT_LIMIT,
    val sections: Boolean = true,
    val language: String? = null,
) {
    companion object {
        /** Per section; the backend's own default. */
        const val DEFAULT_LIMIT = 6
    }
}

/**
 * v2 `{place, sections[], attribution, sources[]}`. Decoding is lenient: unknown keys are ignored,
 * missing or malformed `sections`/`sources` become empty, malformed items are skipped, so a
 * v1-shaped `{place, items}` body decodes to a place with no sections. Errors: 404
 * `detail:{code:"no_place"}`, 502 `agent_error`, 503 `ai_limit` / `ai_billing` / `grounding_unavailable`.
 * Never cached on the device: the Near me ViewModel holds it in memory only (Google Maps grounding terms).
 */
@Serializable
data class NearbyResponse(
    val place: NearbyPlace? = null,
    @Serializable(with = NearbySectionListSerializer::class) val sections: List<NearbySection> = emptyList(),
    val attribution: LenientString = null,
    @Serializable(with = GroundingSourceListSerializer::class) val sources: List<GroundingSource> = emptyList(),
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

/** One section: [id] is `see`, `eat`, `shop` or `stay` ([NearbySectionIds]); [title] is the server's English title. */
@Serializable
data class NearbySection(
    val id: LenientString = null,
    val title: LenientString = null,
    @Serializable(with = NearbyPlaceItemListSerializer::class) val items: List<NearbyPlaceItem> = emptyList(),
)

/** A place from a Google Maps grounding chunk; [summary] is the grounded one-liner, shown unmodified. */
@Serializable
data class NearbyPlaceItem(
    val name: LenientString = null,
    val summary: LenientString = null,
    @SerialName("maps_uri") val mapsUri: LenientString = null,
    @SerialName("place_id") val placeId: LenientString = null,
    val category: LenientString = null,
)

/** The four v2 section ids, in display order. */
object NearbySectionIds {
    const val SEE = "see"
    const val EAT = "eat"
    const val SHOP = "shop"
    const val STAY = "stay"
    val ALL: List<String> = listOf(SEE, EAT, SHOP, STAY)
}

object NearbySectionListSerializer : TolerantListSerializer<NearbySection>(NearbySection.serializer())

object NearbyPlaceItemListSerializer : TolerantListSerializer<NearbyPlaceItem>(NearbyPlaceItem.serializer())

/**
 * The last foreground location fix, rounded to the ~5 km nearby cell (0.05°), with when it was
 * taken. Device-only; gates the background "See what's near you" invitation (never sent anywhere by it).
 */
data class LocationCell(val lat: Double, val lng: Double, val atEpochMillis: Long)

/**
 * `POST /api/nearby` v1 body (no `sections` field: the non-grounded answer a background job may use). The backend adds the
 * user's saved interests and taste profile itself; [limit] is 1..20.
 */
@Serializable
data class NearbyItemsRequest(
    val lat: Double,
    val lng: Double,
    val limit: Int = 1,
)

/** v1 `{place, items[]}`: the activities agent's items for the nearest known place, best match first. Lenient like [NearbyResponse]. */
@Serializable
data class NearbyItemsResponse(
    val place: NearbyPlace? = null,
    @Serializable(with = NearbyItemListSerializer::class) val items: List<NearbyItem> = emptyList(),
)

/** A v1 item; v1 carries no coordinates, so no distance can be computed from it. */
@Serializable
data class NearbyItem(
    val name: LenientString = null,
    val category: LenientString = null,
    val location: LenientString = null,
)

object NearbyItemListSerializer : TolerantListSerializer<NearbyItem>(NearbyItem.serializer())
