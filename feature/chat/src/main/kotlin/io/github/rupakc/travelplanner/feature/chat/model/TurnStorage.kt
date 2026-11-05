package io.github.rupakc.travelplanner.feature.chat.model

import io.github.rupakc.travelplanner.core.data.repository.StoredChatMessage
import io.github.rupakc.travelplanner.core.model.chat.ChatItinerary
import io.github.rupakc.travelplanner.core.model.chat.TripMapCity
import io.github.rupakc.travelplanner.core.model.json.TravelJson
import io.github.rupakc.travelplanner.core.model.section.SectionDecoder
import io.github.rupakc.travelplanner.core.model.section.SectionId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * What an assistant turn stores in `chat_messages.extras_json` beside its text — the
 * structured fields the web keeps in `chat_history_<user>` (sections, statuses,
 * suggestions, planning flags, trip map, itinerary, import link, error). Raw server JSON
 * is kept so nothing is lost and decoding can improve later.
 */
@Serializable
internal data class TurnExtras(
    val sections: List<StoredSection> = emptyList(),
    @SerialName("pending_agents") val pendingAgents: List<String> = emptyList(),
    val suggestions: List<String> = emptyList(),
    val planning: Boolean = false,
    @SerialName("planning_done") val planningDone: Boolean = false,
    @SerialName("plan_destination") val planDestination: String? = null,
    @SerialName("trip_map") val tripMap: StoredTripMap? = null,
    val itinerary: JsonObject? = null,
    @SerialName("itinerary_error") val itineraryError: String? = null,
    @SerialName("import_link") val importLink: String? = null,
    @SerialName("session_context") val sessionContext: JsonObject? = null,
    val error: TurnError? = null,
    @SerialName("error_detail") val errorDetail: String? = null,
    val stopped: Boolean = false,
)

@Serializable
internal data class StoredSection(val name: String, val data: JsonObject, val status: ChatSectionStatus)

@Serializable
internal data class StoredTripMap(
    val origin: String? = null,
    @SerialName("origin_lat") val originLat: Double? = null,
    @SerialName("origin_lng") val originLng: Double? = null,
    val cities: List<TripMapCity> = emptyList(),
    @SerialName("departure_date") val departureDate: String? = null,
    @SerialName("return_date") val returnDate: String? = null,
    @SerialName("num_travelers") val numTravelers: Int? = null,
)

/** Serialises [turn]'s structured fields, or null for a plain-text turn. */
internal fun encodeExtras(turn: ChatTurn): String? {
    val extras =
        TurnExtras(
            sections = turn.sectionOrder.mapNotNull { name -> turn.sections[name]?.let { StoredSection(name, it.raw, it.status) } },
            pendingAgents = turn.pendingAgents,
            suggestions = turn.suggestions,
            planning = turn.planning,
            planningDone = turn.planningDone,
            planDestination = turn.planDestination,
            tripMap =
                turn.tripMap?.let {
                    StoredTripMap(it.origin, it.originLat, it.originLng, it.cities, it.departureDate, it.returnDate, it.numTravelers)
                },
            itinerary = turn.itineraryRaw,
            itineraryError = turn.itineraryError,
            importLink = turn.importLink,
            sessionContext = turn.sessionContext,
            error = turn.error,
            errorDetail = turn.errorDetail,
            stopped = turn.stopped,
        )
    if (extras == TurnExtras()) return null
    return TravelJson.encodeToString(TurnExtras.serializer(), extras)
}

/** Rebuilds a turn from Room (decodes sections and itinerary once). Unreadable extras degrade to text only. */
internal fun StoredChatMessage.toTurn(): ChatTurn {
    val extras =
        extrasJson?.let { runCatching { TravelJson.decodeFromString(TurnExtras.serializer(), it) }.getOrNull() } ?: TurnExtras()
    val sections =
        extras.sections.associate { s ->
            val id = SectionId.fromWire(s.name)
            s.name to ChatSection(s.name, id, id?.let { SectionDecoder.decode(it, s.data, TravelJson) }, s.data, s.status)
        }
    return ChatTurn(
        id = id,
        role = role,
        content = content,
        sections = sections,
        sectionOrder = extras.sections.map { it.name },
        pendingAgents = extras.pendingAgents,
        suggestions = extras.suggestions,
        planning = extras.planning,
        planningDone = extras.planningDone,
        planDestination = extras.planDestination,
        tripMap =
            extras.tripMap?.let {
                ChatTripMap(it.origin, it.originLat, it.originLng, it.cities, it.departureDate, it.returnDate, it.numTravelers)
            },
        itinerary = extras.itinerary?.let(::decodeItinerary),
        itineraryRaw = extras.itinerary,
        itineraryError = extras.itineraryError,
        importLink = extras.importLink,
        sessionContext = extras.sessionContext,
        error = extras.error,
        errorDetail = extras.errorDetail,
        stopped = extras.stopped,
    )
}

internal fun decodeItinerary(raw: JsonObject): ChatItinerary? = runCatching { TravelJson.decodeFromJsonElement(ChatItinerary.serializer(), raw) }.getOrNull()
