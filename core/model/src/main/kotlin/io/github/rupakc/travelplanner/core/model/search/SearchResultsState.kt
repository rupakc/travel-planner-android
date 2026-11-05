package io.github.rupakc.travelplanner.core.model.search

import io.github.rupakc.travelplanner.core.model.section.ConfidenceData
import io.github.rupakc.travelplanner.core.model.section.DataSource
import io.github.rupakc.travelplanner.core.model.section.PricingAdvisorData
import io.github.rupakc.travelplanner.core.model.section.SectionData
import io.github.rupakc.travelplanner.core.model.section.SectionId
import io.github.rupakc.travelplanner.core.model.section.SectionStatus
import kotlinx.serialization.json.JsonObject

/** Latest good result for one section plus its raw JSON (for plan snapshots / chat `search_results`). */
data class SectionResult(val id: SectionId, val data: SectionData, val raw: JsonObject, val source: DataSource?)

enum class StreamPhase { IDLE, STREAMING, DONE, INTERRUPTED, FAILED, CANCELLED }

/**
 * The folded state of one `/api/search` run (web ResultsPage rules, api-contract §3.2):
 * replace-by-type, keep the previous good result when an AI event is an error,
 * statuses loading/waiting → enhancing/done, everything `done` at stream end.
 */
data class SearchResultsState(
    val request: TravelSearchRequest? = null,
    val sections: Map<SectionId, SectionResult> = emptyMap(),
    val statuses: Map<SectionId, SectionStatus> = initialStatuses(),
    val confidence: ConfidenceData? = null,
    val pricingAdvisor: PricingAdvisorData? = null,
    val phase: StreamPhase = StreamPhase.IDLE,
    /** Set when [phase] is FAILED (HTTP error before streaming, e.g. 429 detail). */
    val error: io.github.rupakc.travelplanner.core.common.AppError? = null,
    /** Number of automatic retries used (web: one retry when the stream ends without `done`). */
    val retries: Int = 0,
) {
    val readyCount: Int get() = statuses.count { it.value == SectionStatus.DONE || it.value == SectionStatus.ENHANCING }

    inline fun <reified T : SectionData> section(id: SectionId): T? = sections[id]?.data as? T

    companion object {
        fun initialStatuses(): Map<SectionId, SectionStatus> =
            SectionId.entries.associateWith { if (it.phaseOne) SectionStatus.LOADING else SectionStatus.WAITING }
    }
}
