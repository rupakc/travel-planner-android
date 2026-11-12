package io.github.rupakc.travelplanner.core.model.trips

import io.github.rupakc.travelplanner.core.model.game.GameDelta
import io.github.rupakc.travelplanner.core.model.json.LenientDouble
import io.github.rupakc.travelplanner.core.model.json.LenientInt
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Moods of a journal entry. */
object JournalMoods {
    val ALL: List<String> = listOf("great", "good", "ok", "tough")
}

/** Place kinds of a place key. */
object PlaceKinds {
    val ALL: List<String> = listOf("activity", "place", "food", "hotel", "event", "tour", "nearby", "other")
}

/** `Entry`. */
@Serializable
data class Entry(
    val id: Long,
    @SerialName("client_id") val clientId: String? = null,
    val day: String? = null,
    val body: String = "",
    val mood: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class JournalResponse(val entries: List<Entry> = emptyList())

/** `POST /trips/{id}/journal`. */
@Serializable
data class CreateEntryRequest(
    @SerialName("client_id") val clientId: String,
    val body: String,
    val day: String? = null,
    val mood: String? = null,
)

/** `PATCH /trips/{id}/journal/{eid}`: only the set fields are sent. */
@Serializable
data class UpdateEntryRequest(
    val body: String? = null,
    val day: String? = null,
    val mood: String? = null,
)

/** `TripPlace`. Read [visited] for the current state; [visitedAt] is a never-cleared first-visit marker. */
@Serializable
data class TripPlace(
    @SerialName("place_key") val placeKey: String,
    val name: String = "",
    val kind: String = "other",
    val visited: Boolean = false,
    val stars: LenientInt = null,
    val note: String? = null,
    val day: String? = null,
    val lat: LenientDouble = null,
    val lng: LenientDouble = null,
    @SerialName("visited_at") val visitedAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class PlacesResponse(val places: List<TripPlace> = emptyList())

/** `PUT /trips/{id}/places/{place_key}` (upsert; omitted fields are kept, nulls are omitted on the wire). */
@Serializable
data class PutPlaceRequest(
    val name: String,
    val kind: String,
    val visited: Boolean? = null,
    val stars: Int? = null,
    val note: String? = null,
    val day: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    /** Send an explicit `"stars": null` (the server clears the rating); wins over [stars]. Not a wire field itself. */
    @Transient val clearStars: Boolean = false,
) {
    /** The wire body: set fields only, plus `"stars": null` when [clearStars]. */
    fun toJsonObject(): JsonObject =
        buildJsonObject {
            put("name", name)
            put("kind", kind)
            visited?.let { put("visited", it) }
            if (clearStars) put("stars", JsonNull) else stars?.let { put("stars", it) }
            note?.let { put("note", it) }
            day?.let { put("day", it) }
            lat?.let { put("lat", it) }
            lng?.let { put("lng", it) }
        }
}

/** `PUT /trips/{id}/places/{place_key}` 200: [game] is set when `visited` first becomes true. */
@Serializable
data class PutPlaceResponse(val place: TripPlace, val game: GameDelta? = null)
