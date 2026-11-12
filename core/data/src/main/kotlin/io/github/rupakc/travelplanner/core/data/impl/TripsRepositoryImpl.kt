package io.github.rupakc.travelplanner.core.data.impl

import io.github.rupakc.travelplanner.core.common.AppClock
import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppLogger
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.common.map
import io.github.rupakc.travelplanner.core.common.onSuccess
import io.github.rupakc.travelplanner.core.data.repository.GameEvents
import io.github.rupakc.travelplanner.core.data.repository.TripsRepository
import io.github.rupakc.travelplanner.core.data.repository.UserStateCleaner
import io.github.rupakc.travelplanner.core.database.CachedTripsEntity
import io.github.rupakc.travelplanner.core.database.TripCacheDao
import io.github.rupakc.travelplanner.core.datastore.SessionStore
import io.github.rupakc.travelplanner.core.domain.journey.TripsOffline
import io.github.rupakc.travelplanner.core.model.json.TravelJson
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
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject

/**
 * Trips over [TripsApi]. The last list of each signed-in user is kept in Room (`cached_trips`, never shared
 * between accounts, wiped on sign-out by [SessionCleanerImpl]) so it survives a cold start; when the server cannot be reached (network or
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
        private val cacheDao: TripCacheDao,
    ) : TripsRepository,
        UserStateCleaner {
        private val cacheLock = Mutex()

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
                key?.let { store(it, result.value) }
                return result
            }
            val offline = key?.let { load(it) }
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
            val offline = key?.let { load(it) }?.trips?.firstOrNull { it.id == id }
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
        private suspend fun edit(key: String?, change: (List<Trip>) -> List<Trip>) {
            if (key == null) return
            cacheLock.withLock {
                val old = readCache(key) ?: return
                writeCache(key, old.copy(trips = change(old.trips)))
            }
        }

        private suspend fun store(key: String, value: TripsResponse) = cacheLock.withLock { writeCache(key, value) }

        private suspend fun load(key: String): TripsResponse? = cacheLock.withLock { readCache(key) }

        @Suppress("TooGenericExceptionCaught")
        private suspend fun readCache(key: String): TripsResponse? =
            try {
                cacheDao.trips(key)?.let { TravelJson.decodeFromString(TripsResponse.serializer(), it.json) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.w(TAG, "Could not read the cached trips", e)
                null
            }

        @Suppress("TooGenericExceptionCaught")
        private suspend fun writeCache(key: String, value: TripsResponse) {
            try {
                cacheDao.upsertTrips(CachedTripsEntity(key, TravelJson.encodeToString(TripsResponse.serializer(), value), clock.now().toEpochMilli()))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.w(TAG, "Could not cache the trips", e)
            }
        }

        override suspend fun clear() {
            cacheLock.withLock { cacheDao.clearTrips() }
            cached = false
        }

        private companion object {
            const val TAG = "TripsRepository"
        }
    }
