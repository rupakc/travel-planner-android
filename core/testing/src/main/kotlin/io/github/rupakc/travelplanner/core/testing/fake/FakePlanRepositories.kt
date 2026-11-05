package io.github.rupakc.travelplanner.core.testing.fake

import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.common.map
import io.github.rupakc.travelplanner.core.data.plan.CurrentPlanStore
import io.github.rupakc.travelplanner.core.data.repository.PlansRepository
import io.github.rupakc.travelplanner.core.data.repository.SearchRepository
import io.github.rupakc.travelplanner.core.data.search.SearchLauncher
import io.github.rupakc.travelplanner.core.data.search.SearchLauncherImpl
import io.github.rupakc.travelplanner.core.model.plan.Plan
import io.github.rupakc.travelplanner.core.model.plan.PlanDraft
import io.github.rupakc.travelplanner.core.model.plan.PlanItem
import io.github.rupakc.travelplanner.core.model.plan.PlanSelectionRules
import io.github.rupakc.travelplanner.core.model.plan.PlanSelections
import io.github.rupakc.travelplanner.core.model.plan.SharedPlan
import io.github.rupakc.travelplanner.core.model.search.TravelSearchRequest
import io.github.rupakc.travelplanner.core.model.search.searchKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.JsonObject

/** In-memory plans "server". Ids increment from 1; [failNext] makes the next call fail. */
class FakePlansRepository(initial: List<Plan> = emptyList()) : PlansRepository {
    private val store = MutableStateFlow(initial)
    private var nextId = (initial.maxOfOrNull { it.id } ?: 0) + 1
    var failNext: AppError? = null
    var sharedPlans: MutableMap<String, SharedPlan> = mutableMapOf()

    private fun <T> guard(block: () -> T): AppResult<T> {
        failNext?.let {
            failNext = null
            return AppResult.Failure(it)
        }
        return AppResult.Success(block())
    }

    override val plans: Flow<List<Plan>> = store

    override fun plan(id: Long): Flow<Plan?> = store.map { list -> list.firstOrNull { it.id == id } }

    override suspend fun refresh(): AppResult<Unit> = guard { }

    override suspend fun get(id: Long): AppResult<Plan> =
        guard { store.value.firstOrNull { it.id == id } }
            .let { r ->
                if (r is AppResult.Success &&
                    r.value == null
                ) {
                    AppResult.Failure(AppError.NotFound("Plan not found"))
                } else {
                    r.map { it!! }
                }
            }

    override suspend fun create(name: String, searchData: JsonObject, selections: PlanSelections): AppResult<Plan> =
        guard {
            Plan(id = nextId++, username = "alice", name = name, searchData = searchData, selections = selections).also { plan ->
                store.update { listOf(plan) + it }
            }
        }

    override suspend fun update(id: Long, name: String?, selections: PlanSelections?): AppResult<Plan> {
        val existing = store.value.firstOrNull { it.id == id } ?: return AppResult.Failure(AppError.NotFound("Plan not found"))
        return guard {
            existing.copy(name = name ?: existing.name, selections = selections ?: existing.selections).also { updated ->
                store.update { list -> list.map { if (it.id == id) updated else it } }
            }
        }
    }

    override suspend fun delete(id: Long): AppResult<Unit> = guard { store.update { list -> list.filterNot { it.id == id } } }

    override suspend fun share(id: Long): AppResult<String> =
        guard {
            val token = "share-token-$id"
            store.update { list -> list.map { if (it.id == id) it.copy(shareToken = token) else it } }
            token
        }

    override suspend fun unshare(id: Long): AppResult<Unit> =
        guard {
            store.update { list -> list.map { if (it.id == id) it.copy(shareToken = null) else it } }
        }

    override suspend fun shared(token: String): AppResult<SharedPlan> =
        sharedPlans[token]?.let { AppResult.Success(it) } ?: AppResult.Failure(AppError.NotFound("Shared plan not found"))
}

/** In-memory CurrentPlanStore with the real selection rules, saving through [plans]. */
class FakeCurrentPlanStore(private val plans: PlansRepository = FakePlansRepository()) : CurrentPlanStore {
    private val state = MutableStateFlow(PlanDraft())
    override val draft: StateFlow<PlanDraft> = state.asStateFlow()

    override fun toggle(item: PlanItem) =
        state.update {
            it.copy(selections = PlanSelectionRules.toggle(it.selections, item), dirty = true)
        }

    override fun isSelected(item: PlanItem): Boolean = PlanSelectionRules.isSelected(state.value.selections, item)

    override fun setSelections(selections: PlanSelections) = state.update { it.copy(selections = selections, dirty = true) }

    override fun rename(name: String) = state.update { it.copy(name = name, dirty = true) }

    override fun attachSearch(request: TravelSearchRequest, data: JsonObject?) =
        state.update { it.copy(searchRequest = request, searchData = data, searchKey = request.searchKey()) }

    override fun newPlan() {
        state.value = PlanDraft()
    }

    /** Every whole-draft replacement, in order (newPlan/reset are not recorded). */
    val replaced = mutableListOf<PlanDraft>()

    override fun replace(draft: PlanDraft) {
        replaced += draft
        state.value = draft
    }

    override fun load(plan: Plan): PlanDraft =
        PlanDraft(
            planId = plan.id,
            name = plan.name,
            searchRequest = plan.searchRequest,
            selections = plan.selections,
            searchKey = plan.searchRequest?.searchKey(),
            loadedPlan = true,
        ).also { state.value = it }

    override suspend fun reset() {
        state.value = PlanDraft()
    }

    override suspend fun load(planId: Long): AppResult<PlanDraft> =
        plans.get(planId).map {
            PlanDraft(
                planId = it.id,
                name = it.name,
                searchRequest = it.searchRequest,
                selections = it.selections,
                searchKey = it.searchRequest?.searchKey(),
                loadedPlan = true,
            ).also { d ->
                state.value = d
            }
        }

    override suspend fun save(name: String?): AppResult<Plan> {
        val d = state.value
        val planName = name ?: d.name ?: PlanDraft.DEFAULT_NAME
        val planId = d.planId
        val result =
            if (planId ==
                null
            ) {
                plans.create(planName, JsonObject(emptyMap()), d.selections)
            } else {
                plans.update(planId, planName, d.selections)
            }
        if (result is AppResult.Success) state.update { it.copy(planId = result.value.id, name = result.value.name, dirty = false) }
        return result
    }

    override fun snapshot(): PlanDraft = state.value

    override fun restore(snapshot: PlanDraft) {
        state.value = snapshot
    }
}

/** Records launches and behaves like the real launcher over the given fakes. */
class FakeSearchLauncher(
    private val plan: CurrentPlanStore = FakeCurrentPlanStore(),
    private val search: SearchRepository = FakeSearchRepository(),
) : SearchLauncher {
    val launched = mutableListOf<Pair<TravelSearchRequest, PlanSelections?>>()
    private val real = SearchLauncherImpl(plan, search)

    override fun startNewSearch(request: TravelSearchRequest, preloadSelections: PlanSelections?) {
        launched += request to preloadSelections
        real.startNewSearch(request, preloadSelections)
    }
}
