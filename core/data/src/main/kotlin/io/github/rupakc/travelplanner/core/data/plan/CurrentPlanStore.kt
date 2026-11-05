package io.github.rupakc.travelplanner.core.data.plan

import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.model.plan.Plan
import io.github.rupakc.travelplanner.core.model.plan.PlanDraft
import io.github.rupakc.travelplanner.core.model.plan.PlanItem
import io.github.rupakc.travelplanner.core.model.plan.PlanSelections
import io.github.rupakc.travelplanner.core.model.search.TravelSearchRequest
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.JsonObject

/**
 * The shared My Plan draft consumed by results, chat and plan features.
 * Hilt singleton; persisted to Room so it survives process death.
 */
interface CurrentPlanStore {
    val draft: StateFlow<PlanDraft>

    /** Single-choice categories (flight, hotel, sim, one flight per leg) replace; lists add/remove. */
    fun toggle(item: PlanItem)

    fun isSelected(item: PlanItem): Boolean

    fun setSelections(selections: PlanSelections)

    fun rename(name: String)

    /** Records the search behind the draft and the latest sections (`{"flights":{…}}`); sets `searchKey`. */
    fun attachSearch(request: TravelSearchRequest, data: JsonObject?)

    /** Empties the draft (new, unsaved plan). */
    fun newPlan()

    /** Empties the draft and deletes it from storage (sign-out / user change; called by `SessionCleaner`). */
    suspend fun reset()

    /**
     * Loads a saved plan into the draft (via PlansRepository): `searchRequest` and `searchKey` from the plan's
     * `search_data`, and `searchData` with whatever sections the plan holds (normally just the `itinerary`
     * snapshot) so Results can render it without a stream.
     */
    suspend fun load(planId: Long): AppResult<PlanDraft>

    /**
     * Creates or updates the plan via PlansRepository. Calls are serialised (a double tap creates one plan).
     * On success only `planId`/`name` are applied; edits made while saving are kept and leave `dirty = true`.
     */
    suspend fun save(name: String? = null): AppResult<Plan>

    /** For undo: capture, then [restore]. */
    fun snapshot(): PlanDraft

    fun restore(snapshot: PlanDraft)
}
