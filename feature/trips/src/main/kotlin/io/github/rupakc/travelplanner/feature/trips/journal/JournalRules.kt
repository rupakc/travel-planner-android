package io.github.rupakc.travelplanner.feature.trips.journal

import io.github.rupakc.travelplanner.core.domain.itinerary.ScheduledDay
import io.github.rupakc.travelplanner.core.domain.journey.PlaceKey
import io.github.rupakc.travelplanner.core.model.trips.Entry
import io.github.rupakc.travelplanner.core.model.trips.TripPlace

/** Pure rules of the Journal tab (Trips-API "Journal and places"). */
object JournalRules {
    const val MAX_BODY = 4000
    const val MAX_NOTE = 500
    const val MAX_NAME = 120
    const val MIN_STARS = 1
    const val MAX_STARS = 5

    /** Why a write was refused, from the server's error code. */
    enum class Problem { JournalLimit, PlacesLimit, Other }

    fun problemOf(code: String?): Problem =
        when (code) {
            "journal_limit" -> Problem.JournalLimit
            "places_limit" -> Problem.PlacesLimit
            else -> Problem.Other
        }

    /** The trimmed body when it is 1..[MAX_BODY] characters, else null. */
    fun cleanBody(text: String): String? = text.trim().takeIf { it.isNotEmpty() && it.length <= MAX_BODY }

    fun validStars(stars: Int?): Boolean = stars == null || stars in MIN_STARS..MAX_STARS

    /**
     * The stars after tapping star [tapped] while [current] is set: the same star again clears the rating (null),
     * anything else sets it. A tap outside 1..5 changes nothing.
     */
    fun starsAfterTap(current: Int?, tapped: Int): Int? =
        when {
            tapped !in MIN_STARS..MAX_STARS -> current
            tapped == current -> null
            else -> tapped
        }

    /** A journal entry as the list shows it; [pending] entries are still in the outbox. */
    data class Row(val entry: Entry, val pending: Boolean)

    /**
     * Rows ordered by `day` (nulls last), then synced before queued, then `created_at` (queued ones by their local
     * creation order, which is the order given). Stable for equal keys.
     */
    fun sortEntries(entries: List<Entry>, pendingClientIds: Set<String>): List<Row> {
        val rows = entries.map { Row(it, it.clientId != null && it.clientId in pendingClientIds) }
        return rows.sortedWith(
            compareBy<Row, String?>(nullsLast()) { it.entry.day }
                .thenBy { if (it.pending) 1 else 0 }
                .thenBy { if (it.pending) "" else it.entry.createdAt.orEmpty() },
        )
    }

    /** A place of the trip as the list shows it. */
    data class PlaceRow(
        val key: String,
        val name: String,
        val kind: String,
        val visited: Boolean,
        val stars: Int?,
        val note: String?,
        val day: String?,
        val lat: Double?,
        val lng: Double?,
        val pending: Boolean,
    )

    /**
     * Itinerary places (kind `activity`, key from kind + name through [PlaceKey]) merged with the server's places
     * (which include "I'm here" `nearby` ones). The server's state wins; blank names are skipped and a name is
     * cut to [MAX_NAME] before its key is built.
     */
    fun placeRows(days: List<ScheduledDay>, server: List<TripPlace>, pendingKeys: Set<String>): List<PlaceRow> {
        val byKey = LinkedHashMap<String, PlaceRow>()
        for (day in days) {
            for (scheduled in day.slots) {
                val name = scheduled.slot.activity?.trim().orEmpty().take(MAX_NAME).trim()
                if (name.isEmpty()) continue
                val key = PlaceKey.of("activity", name)
                byKey.getOrPut(key) {
                    PlaceRow(key, name, "activity", false, null, null, day.day.date, scheduled.slot.lat, scheduled.slot.lng, key in pendingKeys)
                }
            }
        }
        for (p in server) {
            byKey[p.placeKey] =
                PlaceRow(
                    p.placeKey,
                    p.name.ifBlank { p.placeKey.substringAfter(':') },
                    p.kind,
                    p.visited,
                    p.stars,
                    p.note,
                    p.day,
                    p.lat,
                    p.lng,
                    p.placeKey in pendingKeys,
                )
        }
        return byKey.values.toList()
    }
}
