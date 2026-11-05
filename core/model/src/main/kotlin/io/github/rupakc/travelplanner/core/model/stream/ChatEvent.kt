package io.github.rupakc.travelplanner.core.model.stream

import io.github.rupakc.travelplanner.core.model.chat.ChatItinerary
import io.github.rupakc.travelplanner.core.model.chat.TripMapCity
import io.github.rupakc.travelplanner.core.model.section.DataSource
import io.github.rupakc.travelplanner.core.model.section.SectionData
import io.github.rupakc.travelplanner.core.model.section.SectionId
import kotlinx.serialization.json.JsonObject

/** One `data:` event of `POST /api/chat` (api-contract §4.2). Terminal: [Done] or [PlanningDone]. */
sealed interface ChatEvent {
    /** Append [text] (markdown) to the assistant message. */
    data class Delta(val text: String) : ChatEvent

    /** Up to 4 follow-up chips. */
    data class Suggestions(val chips: List<String>) : ChatEvent

    /** Store [context] and resend it as `session_context` next turn. */
    data class SessionContextUpdate(val context: JsonObject) : ChatEvent

    /** Mutate My Plan (web `applyPlanAction`). [field] is the raw wire name; [data] may be null. */
    data class PlanAction(val action: PlanActionType?, val field: String, val data: JsonObject?) : ChatEvent {
        val fieldKind: PlanActionField?
            get() = PlanActionField.fromWire(this.field)
    }

    data class PlanningStart(val destination: String?) : ChatEvent

    data class TripMap(
        val origin: String?,
        val originLat: Double?,
        val originLng: Double?,
        val cities: List<TripMapCity>,
        val departureDate: String?,
        val returnDate: String?,
        val numTravelers: Int?,
    ) : ChatEvent

    data object ItineraryGenerating : ChatEvent

    /** The planning result, or [error] when the chat-itinerary agent failed. */
    data class ComprehensiveItinerary(val data: ChatItinerary?, val error: String?, val raw: JsonObject) : ChatEvent

    /** Specialist fallback progress (`status:"loading"`). */
    data class AgentStatus(val agent: String, val status: String) : ChatEvent

    /** Specialist fallback/refinement section. [section] null when the name is unknown. No verification in chat. */
    data class SectionResult(
        val sectionName: String,
        val section: SectionId?,
        val source: DataSource?,
        val data: SectionData?,
        val raw: JsonObject,
    ) : ChatEvent

    /** "Open Plan from a link" with [url]; [href] is a web route, ignore on Android. */
    data class ImportLink(val url: String, val href: String?) : ChatEvent

    /** Stream failure text (followed by `done`). */
    data class Error(val text: String?) : ChatEvent

    /** Legacy `message` / `result`: use [text] only if no delta arrived. */
    data class Message(val text: String) : ChatEvent

    data object PlanningDone : ChatEvent

    data object Done : ChatEvent

    /** Unknown or ignored types (`plan_clear`, `plan_ready`, `search_context`, future ones). */
    data class Unknown(val type: String?, val raw: JsonObject) : ChatEvent

    val isTerminal: Boolean get() = this is Done || this is PlanningDone
}

enum class PlanActionType(val wire: String) {
    SET("set"),
    ADD("add"),
    REMOVE("remove"),
    CLEAR("clear"),
    ;

    companion object {
        fun fromWire(value: String?): PlanActionType? = entries.firstOrNull { it.wire == value }
    }
}

enum class PlanActionField(val wire: String, val isList: Boolean) {
    FLIGHT("flight", false),
    HOTEL("hotel", false),
    SIM("sim", false),
    ACTIVITIES("activities", true),
    TIPS("tips", true),
    GETTING_AROUND("getting_around", true),
    ITINERARY_SLOTS("itinerary_slots", true),
    ALL("all", false),
    ;

    companion object {
        fun fromWire(value: String?): PlanActionField? = entries.firstOrNull { it.wire == value }
    }
}
