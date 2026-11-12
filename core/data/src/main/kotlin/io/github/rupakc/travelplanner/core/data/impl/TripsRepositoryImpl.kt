package io.github.rupakc.travelplanner.core.data.impl

import io.github.rupakc.travelplanner.core.common.AppClock
import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.common.map
import io.github.rupakc.travelplanner.core.common.onSuccess
import io.github.rupakc.travelplanner.core.data.repository.GameEvents
import io.github.rupakc.travelplanner.core.data.repository.TripsRepository
import io.github.rupakc.travelplanner.core.data.repository.UserStateCleaner
import io.github.rupakc.travelplanner.core.datastore.SessionStore
import io.github.rupakc.travelplanner.core.domain.journey.TripsOffline
import io.github.rupakc.travelplanner.core.model.trips.CheckinRequest
import io.github.rupakc.travelplanner.core.model.trips.CheckinResponse
import io.github.rupakc.travelplanner.core.model.trips.CheckinsResponse
import io.github.rupakc.travelplanner.core.model.trips.CreateTripRequest
import io.github.rupakc.travelplanner.core.model.trips.Stats
import io.github.rupakc.travelplanner.core.model.trips.Trip
import io.github.rupakc.travelplanner.core.model.trips.TripsResponse
import io.github.rupakc.travelplanner.core.network.api.TripsApi
import io.github.rupakc.travelplanner.core.network.apiCall
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.JsonObject

/**
 * Trips over [TripsApi]. The last list of each signed-in user is kept in memory (never on disk, never shared
 * between accounts, dropped on sign-out by [SessionCleanerImpl]); when the server cannot be reached (network or
 * 5xx) [list] and [get] answer from it with phase, day counts, order and pin recomputed for the device date.
 * Auth and client errors are never masked. Only callers that already know `features.trips` may use this.
 */
@Singleton
class TripsRepositoryImpl
    @Inject
    constructor(
        private val api: TripsApi,
        private val sessionStore: SessionStore,
        private val clock: AppClock,
        private val gameEvents: GameEvents,
    ) : TripsRepository,
        UserStateCleaner {
        private val cache = ConcurrentHashMap<String, TripsResponse>()

        @Volatile private var cached = false

        private suspend fun userKey(): String? = sessionStore.current()?.let { it.userId ?: it.user.username }

        private fun reachable(error: AppError) = error is AppError.Network || error is AppError.Server

        private fun today(value: String): LocalDate =
            try {
                LocalDate.parse(value)
            } catch (_: DateTimeParseException) {
                clock.today()
            }

        override fun isShowingCachedList(): Boolean = cached

        override suspend fun list(today: String): AppResult<TripsResponse> {
            val key = userKey()
            val result = apiCall { api.list(today) }
            if (result is AppResult.Success) {
                cached = false
                key?.let { cache[it] = result.value }
                return result
            }
            val offline = key?.let { cache[it] }
            if (offline != null && reachable((result as AppResult.Failure).error)) {
                cached = true
                return AppResult.Success(TripsOffline.recompute(offline, today(today)))
            }
            cached = false
            return result
        }

        override suspend fun create(request: CreateTripRequest): AppResult<Trip> {
            val key = userKey()
            return apiCall { api.create(request) }.onSuccess { trip -> edit(key) { it + trip } }
        }

        override suspend fun get(id: Long, today: String): AppResult<Trip> {
            val key = userKey()
            val result = apiCall { api.get(id, today) }
            if (result is AppResult.Success) {
                edit(key) { list -> list.filterNot { it.id == id } + result.value }
                return result
            }
            val offline = key?.let { cache[it] }?.trips?.firstOrNull { it.id == id }
            if (offline != null && reachable((result as AppResult.Failure).error)) {
                return AppResult.Success(TripsOffline.recompute(offline, today(today)))
            }
            return result
        }

        override suspend fun update(id: Long, patch: JsonObject): AppResult<Trip> {
            val key = userKey()
            return apiCall { api.update(id, patch) }.onSuccess { trip -> edit(key) { list -> list.filterNot { it.id == id } + trip } }
        }

        override suspend fun delete(id: Long): AppResult<Unit> {
            val key = userKey()
            return apiCall { api.delete(id) }.map { }.onSuccess { edit(key) { list -> list.filterNot { it.id == id } } }
        }

        override suspend fun stats(): AppResult<Stats> = apiCall { api.stats() }

        override suspend fun checkin(id: Long, day: String): AppResult<CheckinResponse> =
            apiCall {
                api.checkin(id, CheckinRequest(day))
            }.onSuccess { gameEvents.publish(it.game) }

        override suspend fun checkins(id: Long): AppResult<CheckinsResponse> = apiCall { api.checkins(id) }

        /** Applies [change] to [key]'s cached trips (when there is a cached list). */
        private fun edit(key: String?, change: (List<Trip>) -> List<Trip>) {
            if (key == null) return
            cache.computeIfPresent(key) { _, old -> old.copy(trips = change(old.trips)) }
        }

        override suspend fun clear() {
            cache.clear()
            cached = false
        }
    }
