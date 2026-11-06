package io.github.rupakc.travelplanner.feature.nearby

import androidx.compose.runtime.Immutable
import io.github.rupakc.travelplanner.core.domain.live.safeWebUri
import io.github.rupakc.travelplanner.core.model.nearby.NearbyPlace
import io.github.rupakc.travelplanner.core.model.nearby.NearbyResponse
import io.github.rupakc.travelplanner.core.model.nearby.NearbySectionIds

/** One place card: [mapsUri] is already checked (http/https only) or null. */
@Immutable
data class NearbyCard(val key: String, val name: String, val summary: String?, val mapsUri: String?)

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
                NearbyCard(key, name, item.summary?.takeIf(String::isNotBlank), mapsUri)
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
