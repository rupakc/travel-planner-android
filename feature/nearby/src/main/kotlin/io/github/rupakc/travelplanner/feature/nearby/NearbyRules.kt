package io.github.rupakc.travelplanner.feature.nearby

import io.github.rupakc.travelplanner.core.model.nearby.NearbyPlace
import io.github.rupakc.travelplanner.core.model.section.Activity
import io.github.rupakc.travelplanner.core.model.settings.TravellerProfile
import java.util.Locale

/** The web's 10 interest ids (SearchPage `INTERESTS`); onboarding answers map onto them (spec §6.1). */
internal val WEB_INTEREST_IDS = listOf("food", "history", "adventure", "culture", "nature", "shopping", "nightlife", "wellness", "art", "family")

/**
 * Interests from the device-local traveller profile: every quiz answer that is a web interest id,
 * in the web's order. Used only when the server Preferences have none (the backend falls back
 * to "general sightseeing" otherwise).
 */
internal fun profileInterests(profile: TravellerProfile): List<String> {
    val chosen = profile.answers.values.flatten().map { it.lowercase(Locale.ROOT) }.toSet()
    return WEB_INTEREST_IDS.filter { it in chosen }
}

/**
 * "Why this?": the asked-for interests the item's own data mentions (its category, name or
 * description). Nothing is inferred beyond the API fields; empty = no reason shown.
 */
internal fun whyThis(item: Activity, interests: List<String>): List<String> {
    val category = item.category?.lowercase(Locale.ROOT)
    val text = listOfNotNull(item.name, item.description).joinToString(" ").lowercase(Locale.ROOT)
    return interests.filter { i ->
        val word = i.lowercase(Locale.ROOT)
        category == word || Regex("\\b${Regex.escape(word)}").containsMatchIn(text)
    }
}

/** "Kyoto, Japan" / "Portland, Maine, United States": region only when it adds something. */
internal fun placeLabel(place: NearbyPlace): String =
    listOfNotNull(place.name, place.region?.takeIf { it.isNotBlank() && it != place.name }, place.country)
        .filter(String::isNotBlank)
        .joinToString(", ")

/** A Maps search for the item near the resolved place (`geo:` URI, any maps app). */
internal fun mapsQuery(item: Activity, place: NearbyPlace?): String {
    val near = place?.let(::placeLabel)
    return listOfNotNull(item.name, item.location, near).filter(String::isNotBlank).joinToString(", ")
}
