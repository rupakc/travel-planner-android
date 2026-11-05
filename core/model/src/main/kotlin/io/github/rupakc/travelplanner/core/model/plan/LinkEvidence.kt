package io.github.rupakc.travelplanner.core.model.plan

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/*
 * Port of `stripLinkEvidence` from `frontend/src/utils/verification.js`. It lives next to
 * [PlanSelectionsSerializer] so the plan store (core:data) can apply it on save, exactly like the
 * web's ResultsPage/ChatPage save paths.
 */

/** Per-item keys that describe this search's live link checks, not the item itself. */
val LINK_EVIDENCE_KEYS: Set<String> = setOf("link_verified", "link_checked")

private fun stripItem(item: JsonElement): JsonElement {
    if (item !is JsonObject || LINK_EVIDENCE_KEYS.none(item::containsKey)) return item
    return JsonObject(item - LINK_EVIDENCE_KEYS)
}

/**
 * Saved plans store the chosen items, not this search's live link evidence (`link_verified`,
 * `link_checked`), so an old plan never shows "Found online" from a stale check. Untouched values keep
 * their identity; nothing is mutated.
 */
fun stripLinkEvidence(selections: JsonObject): JsonObject =
    JsonObject(selections.mapValues { (_, value) -> if (value is JsonArray) JsonArray(value.map(::stripItem)) else stripItem(value) })

/** [stripLinkEvidence] on typed selections (round-trips through the lossless JSON form). */
fun stripLinkEvidence(selections: PlanSelections): PlanSelections =
    PlanSelectionsSerializer.fromJson(stripLinkEvidence(PlanSelectionsSerializer.toJson(selections)))
