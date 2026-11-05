package io.github.rupakc.travelplanner.core.data.impl

import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.common.map
import io.github.rupakc.travelplanner.core.common.onSuccess
import io.github.rupakc.travelplanner.core.data.repository.PlansRepository
import io.github.rupakc.travelplanner.core.database.CachedPlanEntity
import io.github.rupakc.travelplanner.core.database.PlanDao
import io.github.rupakc.travelplanner.core.datastore.SessionStore
import io.github.rupakc.travelplanner.core.model.json.TravelJson
import io.github.rupakc.travelplanner.core.model.plan.CreatePlanRequest
import io.github.rupakc.travelplanner.core.model.plan.Plan
import io.github.rupakc.travelplanner.core.model.plan.PlanSelections
import io.github.rupakc.travelplanner.core.model.plan.SharedPlan
import io.github.rupakc.travelplanner.core.model.plan.UpdatePlanRequest
import io.github.rupakc.travelplanner.core.network.api.PlansApi
import io.github.rupakc.travelplanner.core.network.apiCall
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonObject
import javax.inject.Inject
import javax.inject.Singleton

/** Offline-first: reads come from Room; every successful network call writes through. */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class PlansRepositoryImpl @Inject constructor(
    private val api: PlansApi,
    private val dao: PlanDao,
    private val sessionStore: SessionStore,
) : PlansRepository {
    override val plans: Flow<List<Plan>> = sessionStore.session.flatMapLatest { session ->
        if (session == null) flowOf(emptyList()) else dao.observeAll(session.user.username).map { rows -> rows.mapNotNull { it.toPlan() } }
    }

    override fun plan(id: Long): Flow<Plan?> = dao.observe(id).map { it?.toPlan() }

    override suspend fun refresh(): AppResult<Unit> = apiCall { api.list() }
        .onSuccess { list ->
            val username = sessionStore.current()?.user?.username ?: return@onSuccess
            dao.replaceAll(username, list.map { it.toEntity(username) })
        }
        .map { }

    override suspend fun get(id: Long): AppResult<Plan> = apiCall { api.get(id) }.onSuccess { cache(it) }

    override suspend fun create(name: String, searchData: JsonObject, selections: PlanSelections): AppResult<Plan> =
        apiCall { api.create(CreatePlanRequest(name, searchData, selections)) }.onSuccess { cache(it) }

    override suspend fun update(id: Long, name: String?, selections: PlanSelections?): AppResult<Plan> =
        apiCall { api.update(id, UpdatePlanRequest(name, selections)) }.onSuccess { cache(it) }

    override suspend fun delete(id: Long): AppResult<Unit> = apiCall { api.delete(id) }.onSuccess { dao.delete(id) }.map { }

    override suspend fun share(id: Long): AppResult<String> = apiCall { api.share(id) }
        .map { it.shareToken }
        .onSuccess { token -> dao.get(id)?.toPlan()?.let { cache(it.copy(shareToken = token)) } }

    override suspend fun unshare(id: Long): AppResult<Unit> = apiCall { api.unshare(id) }
        .onSuccess { dao.get(id)?.toPlan()?.let { cache(it.copy(shareToken = null)) } }
        .map { }

    override suspend fun shared(token: String): AppResult<SharedPlan> = apiCall { api.shared(token) }

    private suspend fun cache(plan: Plan) {
        val username = plan.username ?: sessionStore.current()?.user?.username ?: return
        dao.upsert(plan.toEntity(username))
    }

    private fun Plan.toEntity(username: String) = CachedPlanEntity(
        id = id,
        username = username,
        name = name,
        json = TravelJson.encodeToString(Plan.serializer(), this),
        updatedAt = updatedAt.orEmpty(),
    )

    private fun CachedPlanEntity.toPlan(): Plan? = runCatching { TravelJson.decodeFromString(Plan.serializer(), json) }.getOrNull()
}
