package io.github.rupakc.travelplanner.feature.chat.model

import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.domain.chat.extractDoneChips
import io.github.rupakc.travelplanner.core.model.section.DataSource
import io.github.rupakc.travelplanner.core.model.stream.ChatEvent

/**
 * The assistant turn being streamed plus the web's per-stream flags
 * (`structuredReceived`, `planActionsReceived`, `errorReceived`).
 */
data class StreamState(
    val turn: ChatTurn,
    val structuredReceived: Boolean = false,
    val planActionsReceived: Boolean = false,
    val errorReceived: Boolean = false,
) {
    val isFinished: Boolean get() = !turn.streaming
}

/**
 * Pure port of the web's chat event handling (ChatPage.jsx `sendMessage`): folds one
 * [ChatEvent] into the streaming assistant turn. Side effects (`plan_action` on My Plan,
 * `session_context_update` storage) are the caller's job; this only records them.
 */
object ChatStreamReducer {
    fun start(): StreamState = StreamState(ChatTurn(id = ChatTurn.PENDING_ID, role = "assistant", streaming = true))

    @Suppress("CyclomaticComplexMethod", "LongMethod")
    fun reduce(state: StreamState, event: ChatEvent): StreamState {
        val turn = state.turn
        return when (event) {
            is ChatEvent.Delta -> {
                state.copy(turn = turn.copy(content = turn.content + event.text))
            }

            is ChatEvent.Message -> {
                if (event.text.isNotEmpty() && turn.content.isEmpty()) state.copy(turn = turn.copy(content = event.text)) else state
            }

            is ChatEvent.PlanningStart -> {
                state.copy(turn = turn.copy(planning = true, planDestination = event.destination))
            }

            is ChatEvent.ItineraryGenerating -> {
                state.copy(turn = turn.copy(itineraryGenerating = true))
            }

            is ChatEvent.TripMap -> {
                state.copy(
                    structuredReceived = true,
                    turn =
                        turn.copy(
                            tripMap =
                                ChatTripMap(
                                    event.origin,
                                    event.originLat,
                                    event.originLng,
                                    event.cities,
                                    event.departureDate,
                                    event.returnDate,
                                    event.numTravelers,
                                ),
                        ),
                )
            }

            is ChatEvent.ComprehensiveItinerary -> {
                state.copy(
                    structuredReceived = true,
                    turn = turn.copy(itinerary = event.data, itineraryRaw = event.raw, itineraryError = event.error, itineraryGenerating = false),
                )
            }

            is ChatEvent.AgentStatus -> {
                val pending = if (event.agent in turn.sections || event.agent in turn.pendingAgents) turn.pendingAgents else turn.pendingAgents + event.agent
                state.copy(turn = turn.copy(planning = true, pendingAgents = pending))
            }

            is ChatEvent.SectionResult -> {
                state.copy(structuredReceived = true, turn = withSection(turn, event))
            }

            is ChatEvent.PlanAction -> {
                state.copy(planActionsReceived = true)
            }

            is ChatEvent.ImportLink -> {
                if (isHttpUrl(event.url)) state.copy(turn = turn.copy(importLink = event.url)) else state
            }

            is ChatEvent.SessionContextUpdate -> {
                state.copy(turn = turn.copy(sessionContext = event.context))
            }

            is ChatEvent.Suggestions -> {
                state.copy(turn = turn.copy(suggestions = event.chips))
            }

            is ChatEvent.Error -> {
                // The stream goes on to `done`; [finish] ends it.
                state.copy(errorReceived = true, turn = turn.copy(error = TurnError.STREAM))
            }

            is ChatEvent.PlanningDone -> {
                state.copy(turn = turn.copy(streaming = false, planningDone = true, itineraryGenerating = false))
            }

            is ChatEvent.Done -> {
                finish(withDoneChips(state))
            }

            is ChatEvent.Unknown -> {
                state
            }
        }
    }

    /**
     * Stream ended (with `done`, or without a terminal event): never leave an empty bubble.
     * Web parity: the turn is an error when it has no text, nothing structured, no plan
     * actions and no error event.
     */
    fun finish(state: StreamState): StreamState {
        val turn = state.turn
        val empty = turn.content.isBlank() && !state.structuredReceived && !state.planActionsReceived && !state.errorReceived
        return state.copy(
            turn =
                turn.copy(
                    streaming = false,
                    itineraryGenerating = false,
                    error = turn.error ?: if (empty) TurnError.EMPTY else null,
                ),
        )
    }

    /** The request failed: web replaces the turn with the error ("Sorry, something went wrong…"). */
    fun fail(state: StreamState, error: AppError): StreamState =
        state.copy(turn = state.turn.copy(streaming = false, itineraryGenerating = false, error = TurnError.REQUEST, errorDetail = error.message))

    /** The user pressed Stop: keep what arrived, mark it stopped. */
    fun stop(state: StreamState): StreamState = state.copy(turn = state.turn.copy(streaming = false, itineraryGenerating = false, stopped = true))

    private fun withDoneChips(state: StreamState): StreamState {
        val split = extractDoneChips(state.turn.content) ?: return state
        val turn = state.turn
        return state.copy(turn = turn.copy(content = split.body, suggestions = turn.suggestions.ifEmpty { split.followUps }))
    }

    /** Keeps prior good data when an AI result is an error (web + api-contract §4.2). */
    private fun withSection(turn: ChatTurn, event: ChatEvent.SectionResult): ChatTurn {
        val name = event.sectionName
        val existing = turn.sections[name]
        val existingIsGood = existing != null && existing.data?.isError != true && !existing.raw.containsKey("error")
        val hasError = event.data?.isError == true || event.raw["error"] != null
        val status = if (event.source == DataSource.STATIC) ChatSectionStatus.ENHANCING else ChatSectionStatus.DONE
        val section =
            if (hasError && existingIsGood) {
                existing.copy(status = status)
            } else {
                ChatSection(name, event.section, event.data, event.raw, status)
            }
        return turn.copy(
            sections = turn.sections + (name to section),
            sectionOrder = if (name in turn.sectionOrder) turn.sectionOrder else turn.sectionOrder + name,
            pendingAgents = turn.pendingAgents - name,
        )
    }

    private fun isHttpUrl(url: String): Boolean = url.startsWith("https://", ignoreCase = true) || url.startsWith("http://", ignoreCase = true)
}
