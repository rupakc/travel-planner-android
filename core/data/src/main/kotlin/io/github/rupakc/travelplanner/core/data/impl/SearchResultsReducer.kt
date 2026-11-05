package io.github.rupakc.travelplanner.core.data.impl

import io.github.rupakc.travelplanner.core.model.search.SearchResultsState
import io.github.rupakc.travelplanner.core.model.search.SectionResult
import io.github.rupakc.travelplanner.core.model.search.StreamPhase
import io.github.rupakc.travelplanner.core.model.section.DataSource
import io.github.rupakc.travelplanner.core.model.section.SectionId
import io.github.rupakc.travelplanner.core.model.section.SectionStatus
import io.github.rupakc.travelplanner.core.model.stream.SearchEvent
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject

/** Pure fold of search events into [SearchResultsState] (web ResultsPage rules, api-contract §3.2). */
object SearchResultsReducer {
    fun reduce(state: SearchResultsState, event: SearchEvent): SearchResultsState =
        when (event) {
            is SearchEvent.Section -> {
                onSection(state, event).advanceDependents()
            }

            is SearchEvent.Confidence -> {
                state.copy(confidence = event.data)
            }

            is SearchEvent.PricingAdvisor -> {
                state.copy(pricingAdvisor = event.data)
            }

            is SearchEvent.AgentStatus -> {
                if (event.agent == SectionId.STRESS_TEST.wire && event.status == "searching") {
                    state.withStatus(SectionId.STRESS_TEST, SectionStatus.LOADING)
                } else {
                    state
                }
            }

            SearchEvent.Done -> {
                finish(state, StreamPhase.DONE)
            }

            is SearchEvent.Unknown -> {
                state
            }
        }

    /** Stream end: every section not done becomes done (web parity). */
    fun finish(state: SearchResultsState, phase: StreamPhase): SearchResultsState =
        state.copy(statuses = state.statuses.mapValues { SectionStatus.DONE }, phase = phase)

    /** Sections as `{"flights":{…},…}` — the chat `search_results` / plan draft `search_data` shape. */
    fun sectionsJson(state: SearchResultsState): JsonObject =
        buildJsonObject {
            state.sections.values.forEach { put(it.id.wire, it.raw) }
        }

    private fun onSection(state: SearchResultsState, event: SearchEvent.Section): SearchResultsState {
        val existing = state.sections[event.id]
        val keepOld = event.data.isError && event.source != DataSource.STATIC && existing != null && !existing.data.isError
        val sections =
            if (keepOld) {
                state.sections
            } else {
                state.sections +
                    (event.id to SectionResult(event.id, event.data, event.raw, event.source))
            }
        val current = state.statuses[event.id]
        val status =
            when {
                event.source == DataSource.STATIC && current != SectionStatus.DONE -> SectionStatus.ENHANCING
                event.source == DataSource.STATIC -> SectionStatus.DONE
                else -> SectionStatus.DONE
            }
        return state.copy(sections = sections).withStatus(event.id, status)
    }

    private fun SearchResultsState.advanceDependents(): SearchResultsState {
        fun ready(id: SectionId) = statuses[id] == SectionStatus.DONE || statuses[id] == SectionStatus.ENHANCING
        var next = this
        if (statuses[SectionId.ITINERARY] == SectionStatus.WAITING && ready(SectionId.ACTIVITIES) && ready(SectionId.HOTELS)) {
            next = next.withStatus(SectionId.ITINERARY, SectionStatus.LOADING)
        }
        if (next.statuses[SectionId.STRESS_TEST] == SectionStatus.WAITING && next.statuses[SectionId.ITINERARY] == SectionStatus.DONE) {
            next = next.withStatus(SectionId.STRESS_TEST, SectionStatus.LOADING)
        }
        if (next.statuses[SectionId.PACKING_LIST] == SectionStatus.WAITING && ready(SectionId.ACTIVITIES) && ready(SectionId.WEATHER)) {
            next = next.withStatus(SectionId.PACKING_LIST, SectionStatus.LOADING)
        }
        return next
    }

    private fun SearchResultsState.withStatus(id: SectionId, status: SectionStatus) = copy(statuses = statuses + (id to status))
}
