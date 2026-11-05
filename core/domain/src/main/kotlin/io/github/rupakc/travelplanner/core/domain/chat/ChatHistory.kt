package io.github.rupakc.travelplanner.core.domain.chat

import io.github.rupakc.travelplanner.core.model.chat.ChatItinerary
import io.github.rupakc.travelplanner.core.model.chat.ChatMessage

/**
 * One on-screen chat turn as [buildApiHistory] sees it. Assistant turns produced by
 * planning flows carry their answer in structured fields ([itinerary], trip route,
 * [sectionNames]) with empty [content].
 *
 * @property tripCities the `trip_map` city names in route order
 */
data class HistoryTurn(
    val role: String,
    val content: String,
    val itinerary: ChatItinerary? = null,
    val tripCities: List<String> = emptyList(),
    val tripDeparture: String? = null,
    val tripReturn: String? = null,
    val sectionNames: List<String> = emptyList(),
)

private const val MAX_DAYS = 14
private const val MAX_ACTIVITIES_PER_DAY = 3

/**
 * Port of web `describeAssistantForApi` (frontend/src/utils/chatHistory.js): the API
 * rejects empty-content messages and the model would otherwise forget what it answered,
 * so structured turns get a text stand-in.
 *
 * Deviation (documented in MEMORY.md): the web reads `day.slots` and
 * `itinerary.destination`, which the chat-itinerary schema does not have (api-contract
 * §4.3), so its summary lists no activities. Here the activities come from the real
 * `morning/afternoon/evening` slots and the destination from `trip_summary.cities`.
 */
fun describeAssistantForApi(turn: HistoryTurn): String {
    if (turn.content.isNotBlank()) return turn.content
    val parts = mutableListOf<String>()
    val days = turn.itinerary?.days.orEmpty()
    if (days.isNotEmpty()) {
        val destination =
            turn.itinerary?.tripSummary?.cities.orEmpty().mapNotNull { it.name?.takeIf(String::isNotBlank) }.joinToString(", ")
        val summary =
            days.take(MAX_DAYS).mapIndexed { index, day ->
                val activities =
                    listOf(day.morning, day.afternoon, day.evening)
                        .mapNotNull { it?.activity?.takeIf(String::isNotBlank) }
                        .take(MAX_ACTIVITIES_PER_DAY)
                        .joinToString("; ")
                "Day ${day.dayNumber ?: (index + 1)}: $activities"
            }.joinToString(" | ")
        val forDestination = if (destination.isNotEmpty()) " for $destination" else ""
        parts += "[Shared a ${days.size}-day itinerary$forDestination — $summary]"
    }
    val cities = turn.tripCities.filter(String::isNotBlank)
    if (cities.isNotEmpty()) {
        val dates = turn.tripDeparture?.takeIf(String::isNotBlank)?.let { ", $it to ${turn.tripReturn?.takeIf(String::isNotBlank) ?: "TBD"}" }.orEmpty()
        parts += "[Trip route: ${cities.joinToString(" → ")}$dates]"
    }
    if (turn.sectionNames.isNotEmpty()) {
        parts += "[Shared search results for: ${turn.sectionNames.joinToString(", ")}]"
    }
    return parts.joinToString("\n")
}

/** Port of web `buildApiHistory`: full past context, assistant turns described, no blank entries. */
fun buildApiHistory(turns: List<HistoryTurn>): List<ChatMessage> =
    turns
        .map { turn ->
            ChatMessage(
                role = turn.role,
                content = if (turn.role == ChatMessage.ROLE_ASSISTANT) describeAssistantForApi(turn) else turn.content,
            )
        }.filter { it.content.isNotBlank() }
