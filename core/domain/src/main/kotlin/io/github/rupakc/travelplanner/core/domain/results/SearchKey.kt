package io.github.rupakc.travelplanner.core.domain.results

import io.github.rupakc.travelplanner.core.model.json.TravelJson
import io.github.rupakc.travelplanner.core.model.search.TravelSearchRequest
import java.security.MessageDigest
import java.util.Locale

/**
 * Stable identity of a search: the first 12 bytes (hex) of SHA-256 over the normalised request
 * JSON (trimmed, lower-cased place/nationality text; list fields de-duplicated and sorted; blank
 * return date dropped). Two requests with the same key run the same search, so a stream for one
 * may be shown for — and stored into — the other.
 *
 * TODO(integration): identical to the foundation's `TravelSearchRequest.searchKey()` /
 * `PlanDraft.searchKey` (PR #2, `fix/foundation-r1`). Once that merges, delegate to it and delete this copy.
 */
fun searchKey(request: TravelSearchRequest?): String? {
    request ?: return null

    fun String.norm() = trim().lowercase(Locale.ROOT)

    fun List<String>.normSet() = map { it.norm() }.filter(String::isNotEmpty).distinct().sorted()
    val normalised =
        request.copy(
            origin = request.origin.norm(),
            destination = request.destination.norm(),
            nationality = request.nationality.norm(),
            interests = request.interests.normSet(),
            residencePermits = request.residencePermits.normSet(),
            existingVisas = request.existingVisas.normSet(),
            accessibilityNeeds = request.accessibilityNeeds.normSet(),
            destinations = request.destinations?.map { it.norm() },
            departureDate = request.departureDate.trim(),
            returnDate = request.returnDate?.trim()?.takeIf(String::isNotEmpty),
        )
    val canonical = TravelJson.encodeToString(TravelSearchRequest.serializer(), normalised)
    val digest = MessageDigest.getInstance("SHA-256").digest(canonical.encodeToByteArray())
    return digest.take(KEY_BYTES).joinToString("") { "%02x".format(it) }
}

private const val KEY_BYTES = 12
