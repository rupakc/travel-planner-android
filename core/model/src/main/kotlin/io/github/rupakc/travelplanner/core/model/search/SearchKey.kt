package io.github.rupakc.travelplanner.core.model.search

import io.github.rupakc.travelplanner.core.model.json.TravelJson
import java.security.MessageDigest
import java.util.Locale

/**
 * Stable identity of "which search" a request describes: a SHA-256 prefix of the request after
 * normalisation (trimmed, case-folded places and nationality; set-like lists sorted; blank entries
 * dropped). Two requests that would run the same search get the same key. Used by `PlanDraft.searchKey`
 * so Results can tell a stale finished stream from the draft's own search.
 */
fun TravelSearchRequest.searchKey(): String {
    fun String.norm() = trim().lowercase(Locale.ROOT)

    fun List<String>.normSet() = map { it.norm() }.filter(String::isNotEmpty).distinct().sorted()
    val normalised =
        copy(
            origin = origin.norm(),
            destination = destination.norm(),
            nationality = nationality.norm(),
            interests = interests.normSet(),
            residencePermits = residencePermits.normSet(),
            existingVisas = existingVisas.normSet(),
            accessibilityNeeds = accessibilityNeeds.normSet(),
            destinations = destinations?.map { it.norm() },
            departureDate = departureDate.trim(),
            returnDate = returnDate?.trim()?.takeIf(String::isNotEmpty),
        )
    val canonical = TravelJson.encodeToString(TravelSearchRequest.serializer(), normalised)
    val digest = MessageDigest.getInstance("SHA-256").digest(canonical.encodeToByteArray())
    return digest.take(KEY_BYTES).joinToString("") { "%02x".format(it) }
}

private const val KEY_BYTES = 12
