package io.github.rupakc.travelplanner.feature.chat.model

import androidx.compose.runtime.Immutable
import io.github.rupakc.travelplanner.core.domain.chat.HistoryTurn
import io.github.rupakc.travelplanner.core.domain.chat.TripRoute
import io.github.rupakc.travelplanner.core.model.chat.ChatItinerary
import io.github.rupakc.travelplanner.core.model.chat.ChatMessage
import io.github.rupakc.travelplanner.core.model.chat.TripMapCity
import io.github.rupakc.travelplanner.core.model.section.SectionData
import io.github.rupakc.travelplanner.core.model.section.SectionId
import kotlinx.serialization.json.JsonObject

/** Why an assistant turn shows the error style (web `error: true` plus its fallback copy). */
enum class TurnError {
    /** An `error` event arrived (fallback copy only when the turn has no text). */
    STREAM,

    /** The stream finished with nothing to show ("I didn't get a response there"). */
    EMPTY,

    /** The request failed before/while streaming; [ChatTurn.errorDetail] holds the backend detail if any. */
    REQUEST,
}

/** Section status in chat (web `sectionStatuses`: loading → enhancing (static) / done (ai)). */
enum class ChatSectionStatus { LOADING, ENHANCING, DONE }

/** One `section_result` kept on a turn: [raw] is persisted, [data] is decoded once for display. */
@Immutable
data class ChatSection(val name: String, val id: SectionId?, val data: SectionData?, val raw: JsonObject, val status: ChatSectionStatus)

/** The `trip_map` event, kept on its turn. Coordinates may be null. */
@Immutable
data class ChatTripMap(
    val origin: String?,
    val originLat: Double?,
    val originLng: Double?,
    val cities: List<TripMapCity>,
    val departureDate: String?,
    val returnDate: String?,
    val numTravelers: Int?,
) {
    val route: TripRoute
        get() = TripRoute(origin, cities.mapNotNull { it.city }, departureDate, returnDate, numTravelers)
}

/**
 * One message in a conversation — the web's message object, typed. User turns only use
 * [content]. Assistant turns may carry streamed sections, a trip route and the
 * comprehensive itinerary, suggestion chips and an import link.
 *
 * @property id Room id; [PENDING_ID] while the turn is still streaming and unsaved
 * @property sectionOrder wire names in arrival order (keys of [sections])
 * @property pendingAgents specialist agents announced via `agent_status` with no result yet
 */
@Immutable
data class ChatTurn(
    val id: Long,
    val role: String,
    val content: String = "",
    val sections: Map<String, ChatSection> = emptyMap(),
    val sectionOrder: List<String> = emptyList(),
    val pendingAgents: List<String> = emptyList(),
    val suggestions: List<String> = emptyList(),
    val planning: Boolean = false,
    val planningDone: Boolean = false,
    val planDestination: String? = null,
    val itineraryGenerating: Boolean = false,
    val tripMap: ChatTripMap? = null,
    val itinerary: ChatItinerary? = null,
    val itineraryRaw: JsonObject? = null,
    val itineraryError: String? = null,
    val importLink: String? = null,
    val sessionContext: JsonObject? = null,
    val error: TurnError? = null,
    val errorDetail: String? = null,
    val stopped: Boolean = false,
    val streaming: Boolean = false,
) {
    val isUser: Boolean get() = role == ChatMessage.ROLE_USER

    /** Web `hasSections`: render the structured (planning) layout. */
    val isStructured: Boolean get() = planning || tripMap != null || sections.isNotEmpty()

    fun toHistory(): HistoryTurn =
        HistoryTurn(
            role = role,
            content = content,
            itinerary = itinerary,
            tripCities = tripMap?.cities?.mapNotNull { it.city }.orEmpty(),
            tripDeparture = tripMap?.departureDate,
            tripReturn = tripMap?.returnDate,
            sectionNames = sectionOrder,
        )

    companion object {
        const val PENDING_ID = -1L
    }
}
