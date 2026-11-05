package io.github.rupakc.travelplanner.core.model.stream

import io.github.rupakc.travelplanner.core.model.section.ConfidenceData
import io.github.rupakc.travelplanner.core.model.section.DataSource
import io.github.rupakc.travelplanner.core.model.section.PricingAdvisorData
import io.github.rupakc.travelplanner.core.model.section.SectionData
import io.github.rupakc.travelplanner.core.model.section.SectionId
import kotlinx.serialization.json.JsonObject

/** One `data:` event of `POST /api/search` (api-contract §3.2). */
sealed interface SearchEvent {
    /**
     * A results section (phase 0 static, phase 1 AI, re-yield after enrichment,
     * itinerary, stress test, packing list). Always replace-by-[id].
     * [raw] is the section `data` exactly as received.
     */
    data class Section(
        val id: SectionId,
        val source: DataSource?,
        val data: SectionData,
        val raw: JsonObject,
    ) : SearchEvent

    /** Travel Ease grade (Phase 0). */
    data class Confidence(val data: ConfidenceData, val source: DataSource?) : SearchEvent

    /** Flights pricing advice; may never arrive. */
    data class PricingAdvisor(val data: PricingAdvisorData, val source: DataSource?) : SearchEvent

    /** Optional progress hint: `{"agent":"flights","status":"searching"}`. */
    data class AgentStatus(val agent: String, val status: String) : SearchEvent

    /** Terminal event. */
    data object Done : SearchEvent

    /** Any other `type` (forward compatibility: ignore). */
    data class Unknown(val type: String?, val raw: JsonObject) : SearchEvent
}
