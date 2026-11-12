package io.github.rupakc.travelplanner.feature.nearby

import androidx.compose.runtime.Immutable
import io.github.rupakc.travelplanner.core.domain.live.safeWebUri
import io.github.rupakc.travelplanner.core.model.nearby.NearbyPlace
import io.github.rupakc.travelplanner.core.model.nearby.NearbyResponse
import io.github.rupakc.travelplanner.core.model.nearby.NearbySectionIds
import io.github.rupakc.travelplanner.core.model.trips.Phase
import io.github.rupakc.travelplanner.core.model.trips.PutPlaceRequest
import io.github.rupakc.travelplanner.core.model.trips.Trip
import kotlin.math.roundToLong

/** One place card: [mapsUri] is already checked (http/https only) or null. */
@Immutable
data class NearbyCard(
    val key: String,
    val name: String,
    val summary: String?,
    val mapsUri: String?,
    /** True for a Google Maps grounded item (it has a place id or Maps link): never saved into a trip (web ruling R41). */
    val grounded: Boolean = false,
)

/** One of the four sections, always present (maybe empty) and always in see/eat/shop/stay order. */
@Immutable
data class NearbySectionUi(val id: String, val cards: List<NearbyCard>)

/**
 * The four sections from a v2 answer: unknown section ids are ignored, a missing section is empty,
 * nameless items are dropped, and a place appears once (same `place_id` or same Maps link), in the
 * first section that lists it (see, eat, shop, stay order). Text is kept exactly as the server sent it.
 */
internal fun sectionsOf(response: NearbyResponse): List<NearbySectionUi> {
    val seenPlaces = mutableSetOf<String>()
    val seenKeys = mutableSetOf<String>()
    return NearbySectionIds.ALL.map { id ->
        val items = response.sections.filter { it.id == id }.flatMap { it.items }
        val cards =
            items.mapIndexedNotNull { i, item ->
                val name = item.name?.trim()?.takeIf(String::isNotEmpty) ?: return@mapIndexedNotNull null
                val mapsUri = safeWebUri(item.mapsUri)
                // A place is known by its place_id and by its Maps link; either one seen before = a duplicate.
                val ids = listOfNotNull(item.placeId?.trim()?.takeIf(String::isNotEmpty)?.let { "p:$it" }, mapsUri?.let { "u:$it" })
                if (ids.any { it in seenPlaces }) return@mapIndexedNotNull null
                seenPlaces += ids
                val identity = ids.firstOrNull()
                val base = "$id-" + (identity ?: "$i-$name")
                val key = if (seenKeys.add(base)) base else "$base-$i".also { seenKeys += it }
                NearbyCard(
                    key,
                    name,
                    item.summary?.takeIf(String::isNotBlank),
                    mapsUri,
                    grounded = !item.placeId.isNullOrBlank() || !item.mapsUri.isNullOrBlank(),
                )
            }
        NearbySectionUi(id, cards)
    }
}

/** "Kyoto, Japan" / "Portland, Maine, United States": region only when it adds something. */
internal fun placeLabel(place: NearbyPlace): String =
    listOfNotNull(place.name, place.region?.takeIf { it.isNotBlank() && it != place.name }, place.country)
        .filter(String::isNotBlank)
        .joinToString(", ")

/** Fallback Maps search when the item's link can't be opened: the name near the resolved place (`geo:` URI). */
internal fun mapsQuery(name: String, place: NearbyPlace?): String = listOfNotNull(name, place?.let(::placeLabel)).filter(String::isNotBlank).joinToString(", ")

/** A trip an "I'm here" can be recorded on. */
@Immutable
data class HereTrip(val id: Long, val title: String)

/** "I'm here": recording a nearby item as a visited place of a trip (kind `nearby`). */
object ImHere {
    const val MAX_NAME = 120
    const val KIND = "nearby"
    private const val COORD_SCALE = 1000.0

    /** Shown only for a non-grounded item, and only when the user has a trip to put it on. */
    fun available(card: NearbyCard, trips: List<HereTrip>): Boolean = !card.grounded && trips.isNotEmpty()

    fun name(raw: String): String = raw.trim().take(MAX_NAME)

    /** ~100 m, like the Near me request. */
    fun round(value: Double): Double = (value * COORD_SCALE).roundToLong() / COORD_SCALE

    /** The trips to record on: the one on the road when exactly one, else null (the user picks among [pickable]). */
    fun autoTrip(trips: List<Trip>): HereTrip? =
        trips.filter { it.phase == Phase.ON_THE_ROAD }.singleOrNull()?.let { HereTrip(it.id, it.title) }

    /** Every trip with a known phase, on-the-road ones first. */
    fun pickable(trips: List<Trip>): List<HereTrip> =
        trips.filter { it.phase != null }.sortedBy { if (it.phase == Phase.ON_THE_ROAD) 0 else 1 }.map { HereTrip(it.id, it.title) }

    /** Coordinates of the item when it has any (v2 items never do), else the rounded device location. */
    fun request(card: NearbyCard, day: String, deviceLat: Double?, deviceLng: Double?): PutPlaceRequest =
        PutPlaceRequest(
            name = name(card.name),
            kind = KIND,
            visited = true,
            day = day,
            lat = deviceLat?.let(::round),
            lng = deviceLng?.let(::round),
        )
}
