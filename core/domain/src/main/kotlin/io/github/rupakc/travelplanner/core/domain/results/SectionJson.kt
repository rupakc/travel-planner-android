package io.github.rupakc.travelplanner.core.domain.results

import io.github.rupakc.travelplanner.core.model.plan.PlanSelections
import io.github.rupakc.travelplanner.core.model.plan.PlanSelectionsSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/*
 * Ports of `frontend/src/utils/verification.js` and `orderSectionByCity.js`.
 * They work on the raw section / selections JSON so nothing the app does not
 * model is lost, exactly like the web's object spreads.
 */

private val EVIDENCE_KEYS = listOf("link_verified", "link_checked")

/** Summary the web shows when link checks never finished. */
const val GAVE_UP_SUMMARY = "Link checks did not finish — verify before booking."

/**
 * Stream ended while sections were still awaiting URL enrichment: resolve their
 * chip (`pending=false`, [GAVE_UP_SUMMARY]) instead of spinning forever.
 * Returns the same map when nothing is pending; untouched sections keep identity.
 */
fun <K> clearPendingVerification(sections: Map<K, JsonObject>): Map<K, JsonObject> {
    var changed = false
    val next =
        sections.mapValues { (_, section) ->
            val verification = section["verification"] as? JsonObject
            if ((verification?.get("pending") as? JsonPrimitive)?.contentOrNull == "true") {
                changed = true
                val cleared = JsonObject(verification + mapOf("pending" to JsonPrimitive(false), "summary" to JsonPrimitive(GAVE_UP_SUMMARY)))
                JsonObject(section + ("verification" to cleared))
            } else {
                section
            }
        }
    return if (changed) next else sections
}

private fun stripItem(item: JsonElement): JsonElement {
    if (item !is JsonObject || EVIDENCE_KEYS.none(item::containsKey)) return item
    return JsonObject(item - EVIDENCE_KEYS.toSet())
}

/** Saved plans store the chosen items, not this search's live link evidence (`link_verified`, `link_checked`). */
fun stripLinkEvidence(selections: JsonObject): JsonObject =
    JsonObject(selections.mapValues { (_, value) -> if (value is JsonArray) JsonArray(value.map(::stripItem)) else stripItem(value) })

/** [stripLinkEvidence] on typed selections (round-trips through the lossless JSON form). */
fun stripLinkEvidence(selections: PlanSelections): PlanSelections =
    PlanSelectionsSerializer.fromJson(stripLinkEvidence(PlanSelectionsSerializer.toJson(selections)))

private val CITY_LIST_FIELDS = listOf("results", "hotels", "places", "events", "options", "plans")

/** First segment of "Lisbon, Portugal", trimmed and lowercased — the web's city key. */
fun cityKey(value: String?): String = value.orEmpty().split(',').first().trim().lowercase()

private fun cityOf(item: JsonElement): String? {
    val city = (item as? JsonObject)?.get("city") ?: return null
    val raw = if (city is JsonArray) city.firstOrNull() else city
    return (raw as? JsonPrimitive)?.contentOrNull
}

/**
 * Multi-city: stable-sorts every city-tagged list of a section into the order the
 * user entered the stops. Unknown cities go last. Single-city → [data] unchanged.
 */
fun orderSectionByCity(data: JsonObject, destinations: List<String>?): JsonObject {
    if (destinations == null || destinations.size < 2) return data
    val order = destinations.withIndex().associate { (i, c) -> cityKey(c) to i }

    fun index(item: JsonElement): Int = order[cityKey(cityOf(item))] ?: destinations.size
    var out = data
    for (field in CITY_LIST_FIELDS) {
        val list = out[field] as? JsonArray ?: continue
        if (list.any { !cityOf(it).isNullOrEmpty() }) out = JsonObject(out + (field to JsonArray(list.sortedBy(::index))))
    }
    return out
}
