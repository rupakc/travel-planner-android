package io.github.rupakc.travelplanner.core.model.plan

import io.github.rupakc.travelplanner.core.model.search.TravelSearchRequest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * The shared, in-progress My Plan (one per app; owned by `CurrentPlanStore`).
 *
 * @property planId the saved plan this draft edits, or null when not saved yet
 * @property searchRequest the TravelSearchRequest used (becomes `search_data` on create)
 * @property searchData latest streamed sections keyed by wire name (`{"flights":{…},…}`), or null
 * @property dirty true when [selections]/[name] changed since the last load/save
 * @property searchKey `searchRequest.searchKey()` — which search this draft belongs to (null when none). Compare it
 *   with `SearchResultsState.request?.searchKey()` to tell the draft's own stream from a stale one.
 */
@Serializable
data class PlanDraft(
    @SerialName("plan_id") val planId: Long? = null,
    val name: String? = null,
    @SerialName("search_request") val searchRequest: TravelSearchRequest? = null,
    @SerialName("search_data") val searchData: JsonObject? = null,
    val selections: PlanSelections = PlanSelections(),
    val dirty: Boolean = false,
    @SerialName("search_key") val searchKey: String? = null,
) {
    companion object {
        /** Web default plan name. */
        const val DEFAULT_NAME = "My Trip Plan"
    }
}
