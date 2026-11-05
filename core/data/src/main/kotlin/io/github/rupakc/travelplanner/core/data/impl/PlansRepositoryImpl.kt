package io.github.rupakc.travelplanner.core.data.impl

import io.github.rupakc.travelplanner.core.common.AppDispatchers
import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.common.map
import io.github.rupakc.travelplanner.core.common.onFailure
import io.github.rupakc.travelplanner.core.common.onSuccess
import io.github.rupakc.travelplanner.core.data.di.ApplicationScope
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
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.serialization.json.JsonObject

/**
 * Offline-first: reads come from Room; every successful network call writes through. Rows are read and
 * decoded on the IO dispatcher (never on a collector's Main thread), unchanged rows are not decoded again,
 * and every collector shares one decoded [plans] flow (performance review I4).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class PlansRepositoryImpl
    @Inject
    constructor(
        private val api: PlansApi,
        private val dao: PlanDao,
        private val sessionStore: SessionStore,
        private val dispatchers: AppDispatchers,
        @param:ApplicationScope private val scope: CoroutineScope,
    ) : PlansRepository {
        override val plans: Flow<List<Plan>> =
            sessionStore.session
                .map { it?.user?.username }
                .distinctUntilChanged()
                .flatMapLatest { username ->
                    if (username == null) {
                        flowOf(emptyList())
                    } else {
                        dao.observeAll(username).distinctUntilChanged().map { rows -> rows.mapNotNull { it.toPlan() } }
                    }
                }.flowOn(dispatchers.io)
                // Replay is dropped when the last collector leaves, so a later subscriber never sees a stale list first.
                .shareIn(scope, SharingStarted.WhileSubscribed(SHARE_STOP_MILLIS, 0), replay = 1)

        override fun plan(id: Long): Flow<Plan?> =
            sessionStore.session
                .flatMapLatest { session ->
                    if (session == null) flowOf(null) else dao.observeFor(id, session.user.username).distinctUntilChanged().map { it?.toPlan() }
                }.flowOn(dispatchers.io)

        /** Rewrites the cache only when the server's list differs from it, so observers don't decode every plan again. */
        override suspend fun refresh(): AppResult<Unit> =
            apiCall { api.list() }
                .onSuccess { list ->
                    val username = sessionStore.current()?.user?.username ?: return@onSuccess
                    val fresh = list.map { it.toEntity(username) }
                    val cached = dao.observeAll(username).first()
                    if (fresh.associateBy { it.id } != cached.associateBy { it.id }) dao.replaceAll(username, fresh)
                }
                .map { }

        /** A 404 means the plan is gone (deleted elsewhere or not this user's): its cached copy is evicted. */
        override suspend fun get(id: Long): AppResult<Plan> =
            apiCall { api.get(id) }
                .onSuccess { cache(it) }
                .onFailure { if (it is AppError.NotFound) dao.delete(id) }

        override suspend fun create(name: String, searchData: JsonObject, selections: PlanSelections): AppResult<Plan> =
            apiCall { api.create(CreatePlanRequest(name, searchData, selections)) }.onSuccess { cache(it) }

        override suspend fun update(id: Long, name: String?, selections: PlanSelections?): AppResult<Plan> =
            apiCall { api.update(id, UpdatePlanRequest(name, selections)) }.onSuccess { cache(it) }

        override suspend fun delete(id: Long): AppResult<Unit> = apiCall { api.delete(id) }.onSuccess { dao.delete(id) }.map { }

        override suspend fun share(id: Long): AppResult<String> =
            apiCall { api.share(id) }
                .map { it.shareToken }
                .onSuccess { token -> dao.get(id)?.toPlan()?.let { cache(it.copy(shareToken = token)) } }

        override suspend fun unshare(id: Long): AppResult<Unit> =
            apiCall { api.unshare(id) }
                .onSuccess { dao.get(id)?.toPlan()?.let { cache(it.copy(shareToken = null)) } }
                .map { }

        override suspend fun shared(token: String): AppResult<SharedPlan> = apiCall { api.shared(token) }

        private suspend fun cache(plan: Plan) {
            val username = plan.username ?: sessionStore.current()?.user?.username ?: return
            dao.upsert(plan.toEntity(username))
        }

        private fun Plan.toEntity(username: String) =
            CachedPlanEntity(
                id = id,
                username = username,
                name = name,
                json = TravelJson.encodeToString(Plan.serializer(), this),
                updatedAt = updatedAt.orEmpty(),
            )

        private fun CachedPlanEntity.toPlan(): Plan? = runCatching { TravelJson.decodeFromString(Plan.serializer(), json) }.getOrNull()

        private companion object {
            const val SHARE_STOP_MILLIS = 5_000L
        }
    }
