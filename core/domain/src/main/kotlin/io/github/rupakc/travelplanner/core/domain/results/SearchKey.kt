package io.github.rupakc.travelplanner.core.domain.results

import io.github.rupakc.travelplanner.core.model.json.TravelJson
import io.github.rupakc.travelplanner.core.model.search.TravelSearchRequest
import java.security.MessageDigest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject

/**
 * Stable identity of a search: SHA-256 (hex) of the normalised request JSON
 * (keys sorted at every level, null values dropped). Two requests with the same
 * key describe the same trip, so results streamed for one may be shown for the other.
 *
 * TODO(integration): the foundation fix (PR #2, `fix/foundation-r1`) adds `PlanDraft.searchKey`
 * with this same definition; once merged, use it for the draft side and keep this
 * function only for live requests (or delegate to the foundation's helper).
 */
fun searchKey(request: TravelSearchRequest?): String? {
    request ?: return null
    val normalised = normalise(TravelJson.encodeToJsonElement(TravelSearchRequest.serializer(), request)).toString()
    val digest = MessageDigest.getInstance("SHA-256").digest(normalised.toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { "%02x".format(it) }
}

private fun normalise(element: JsonElement): JsonElement =
    when (element) {
        is JsonObject -> JsonObject(element.filterValues { it !is JsonNull }.toSortedMap().mapValues { normalise(it.value) })
        is JsonArray -> JsonArray(element.map(::normalise))
        else -> element
    }
