package io.github.rupakc.travelplanner.core.data.plan

import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.common.map
import io.github.rupakc.travelplanner.core.common.onSuccess
import io.github.rupakc.travelplanner.core.data.di.ApplicationScope
import io.github.rupakc.travelplanner.core.data.repository.PlansRepository
import io.github.rupakc.travelplanner.core.database.PlanDraftDao
import io.github.rupakc.travelplanner.core.database.PlanDraftEntity
import io.github.rupakc.travelplanner.core.model.json.EmptyJsonObject
import io.github.rupakc.travelplanner.core.model.json.TravelJson
import io.github.rupakc.travelplanner.core.model.plan.Plan
import io.github.rupakc.travelplanner.core.model.plan.PlanDraft
import io.github.rupakc.travelplanner.core.model.plan.PlanItem
import io.github.rupakc.travelplanner.core.model.plan.PlanSelectionRules
import io.github.rupakc.travelplanner.core.model.plan.PlanSelections
import io.github.rupakc.travelplanner.core.model.plan.SelectionKeys
import io.github.rupakc.travelplanner.core.model.search.TravelSearchRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hilt singleton [CurrentPlanStore]. State lives in memory; every change is
 * written (latest-wins, serialised) to the single-row `plan_draft` table and
 * restored at startup, so the draft survives process death.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class CurrentPlanStoreImpl @Inject constructor(
    private val plansRepository: PlansRepository,
    private val draftDao: PlanDraftDao,
    @param:ApplicationScope private val scope: CoroutineScope,
) : CurrentPlanStore {
    private val state = MutableStateFlow(PlanDraft())
    override val draft: StateFlow<PlanDraft> = state.asStateFlow()
    private val writes = Channel<PlanDraft>(Channel.CONFLATED)
    private val restoreJob: Job

    init {
        restoreJob = scope.launch {
            draftDao.get()?.json
                ?.let { runCatching { TravelJson.decodeFromString(PlanDraft.serializer(), it) }.getOrNull() }
                ?.let { saved -> state.compareAndSet(PlanDraft(), saved) }
        }
        scope.launch {
            writes.consumeAsFlow().collect { draftDao.save(PlanDraftEntity(json = TravelJson.encodeToString(PlanDraft.serializer(), it))) }
        }
    }

    private fun set(transform: (PlanDraft) -> PlanDraft) {
        state.update(transform)
        writes.trySend(state.value)
    }

    override fun toggle(item: PlanItem) = set { it.copy(selections = PlanSelectionRules.toggle(it.selections, item), dirty = true) }

    override fun isSelected(item: PlanItem): Boolean = PlanSelectionRules.isSelected(state.value.selections, item)

    override fun setSelections(selections: PlanSelections) = set { it.copy(selections = selections, dirty = true) }

    override fun rename(name: String) = set { it.copy(name = name, dirty = true) }

    override fun attachSearch(request: TravelSearchRequest, data: JsonObject?) =
        set { it.copy(searchRequest = request, searchData = data) }

    override fun newPlan() = set { PlanDraft() }

    override suspend fun load(planId: Long): AppResult<PlanDraft> {
        restoreJob.join()
        return plansRepository.get(planId).map { plan ->
            PlanDraft(planId = plan.id, name = plan.name, searchRequest = plan.searchRequest, selections = plan.selections, dirty = false)
        }.onSuccess { loaded -> set { loaded } }
    }

    override suspend fun save(name: String?): AppResult<Plan> {
        restoreJob.join()
        val current = state.value
        val planName = name?.takeIf(String::isNotBlank) ?: current.name?.takeIf(String::isNotBlank) ?: PlanDraft.DEFAULT_NAME
        val selections = withItinerarySnapshot(current)
        val planId = current.planId
        val result = if (planId == null) {
            val searchData = current.searchRequest
                ?.let { TravelJson.encodeToJsonElement(TravelSearchRequest.serializer(), it) as JsonObject }
                ?: EmptyJsonObject
            plansRepository.create(planName, searchData, selections)
        } else {
            plansRepository.update(planId, planName, selections)
        }
        return result.onSuccess { plan ->
            set { it.copy(planId = plan.id, name = plan.name, selections = plan.selections, dirty = false) }
        }
    }

    override fun snapshot(): PlanDraft = state.value

    override fun restore(snapshot: PlanDraft) = set { snapshot }

    /** Web parity: on save the live itinerary is snapshotted into `selections.itinerary` so a saved plan is self-contained. */
    private fun withItinerarySnapshot(draft: PlanDraft): PlanSelections {
        val live = draft.searchData?.get(SelectionKeys.ITINERARY) as? JsonObject ?: return draft.selections
        val snapshot = buildJsonObject {
            live["days"]?.let { put("days", it) }
            live["total_estimated_cost_usd"]?.let { put("total_estimated_cost_usd", it) }
        }
        return if (snapshot.isEmpty()) draft.selections else draft.selections.copy(itinerarySnapshot = snapshot)
    }
}
