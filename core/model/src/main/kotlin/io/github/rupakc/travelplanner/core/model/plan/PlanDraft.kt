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
 * @property loadedPlan true when the draft was opened from a saved plan (`CurrentPlanStore.load`) and no new
 *   search has replaced it since; saving does not set it (web `loadedPlanId`, ResultsPage.jsx:3482). Results
 *   locks itinerary editing while it is set.
 *
 * Equality compares [searchData] by reference: it is the whole stream snapshot (up to ~1.5 MB) and
 * `JsonObject.equals` walks the tree, so a structural compare on every toggle/keystroke (StateFlow's
 * `compareAndSet`) would deep-walk it on the main thread (performance review I3). Every writer replaces
 * the snapshot with a new object, so identity is the right notion of "changed".
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
    @SerialName("loaded_plan") val loadedPlan: Boolean = false,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        return other is PlanDraft &&
            planId == other.planId &&
            name == other.name &&
            dirty == other.dirty &&
            loadedPlan == other.loadedPlan &&
            searchKey == other.searchKey &&
            searchData === other.searchData &&
            selections == other.selections &&
            searchRequest == other.searchRequest
    }

    override fun hashCode(): Int {
        var h = planId.hashCode()
        h = 31 * h + name.hashCode()
        h = 31 * h + dirty.hashCode()
        h = 31 * h + loadedPlan.hashCode()
        h = 31 * h + searchKey.hashCode()
        h = 31 * h + System.identityHashCode(searchData)
        h = 31 * h + selections.hashCode()
        h = 31 * h + searchRequest.hashCode()
        return h
    }

    companion object {
        /** Web default plan name. */
        const val DEFAULT_NAME = "My Trip Plan"
    }
}
