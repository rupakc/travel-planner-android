package io.github.rupakc.travelplanner.core.data.offline

import android.database.sqlite.SQLiteFullException
import io.github.rupakc.travelplanner.core.common.AppClock
import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppLogger
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.data.repository.OfflinePackRepository
import io.github.rupakc.travelplanner.core.data.telemetry.Telemetry
import io.github.rupakc.travelplanner.core.database.OfflinePackDao
import io.github.rupakc.travelplanner.core.database.OfflinePackEntity
import io.github.rupakc.travelplanner.core.database.OutboxDao
import io.github.rupakc.travelplanner.core.datastore.SessionStore
import io.github.rupakc.travelplanner.core.domain.telemetry.TelemetryEvents
import io.github.rupakc.travelplanner.core.model.json.TravelJson
import io.github.rupakc.travelplanner.core.model.trips.OfflinePack
import io.github.rupakc.travelplanner.core.network.api.TripsApi
import io.github.rupakc.travelplanner.core.network.apiCall
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject

/**
 * Offline packs in Room under `userId:tripId`. The viewer reads [saved] only, so it works with the network off.
 * `404 offline_pack_disabled` means the feature is off on this server: the packs and queued snapshot uploads
 * are purged (queued expenses and journal writes stay).
 */
@Singleton
internal class OfflinePackRepositoryImpl
    @Inject
    constructor(
        private val api: TripsApi,
        private val dao: OfflinePackDao,
        private val outboxDao: OutboxDao,
        private val sessionStore: SessionStore,
        private val telemetry: Telemetry,
        private val clock: AppClock,
    ) : OfflinePackRepository {
        private fun key(user: String, tripId: Long) = "$user:$tripId"

        override fun saved(tripId: Long): Flow<OfflinePack?> =
            sessionStore.session.flatMapLatest { session ->
                if (session == null) flowOf(null) else dao.observe(key(session.userKey(), tripId)).map { decode(it?.json) }
            }

        override suspend fun save(tripId: Long, today: String): AppResult<OfflinePack> {
            val user = sessionStore.currentUserKey() ?: return AppResult.Failure(AppError.Unauthorized())
            val existed = dao.get(key(user, tripId)) != null
            val action = if (existed) "refresh" else "save"
            val result = apiCall { api.offlinePack(tripId, today) }
            if (result is AppResult.Failure) {
                val error = result.error
                val disabled = error is AppError.NotFound && error.code == DISABLED
                if (disabled) purgeAll()
                report(action, if (disabled) "unsupported" else "error", 0)
                return result
            }
            val pack = (result as AppResult.Success).value
            val json = TravelJson.encodeToString(OfflinePack.serializer(), pack)
            return try {
                dao.upsert(OfflinePackEntity(key(user, tripId), user, tripId, json, clock.now().toEpochMilli()))
                report(action, "ok", json.length / BYTES_PER_KB)
                result
            } catch (e: SQLiteFullException) {
                AppLogger.w(TAG, "Storage is full; the pack was not saved", e)
                report(action, "quota", 0)
                AppResult.Failure(AppError.Unknown(OfflinePackRepository.STORAGE_FULL))
            }
        }

        override suspend fun remove(tripId: Long): AppResult<Unit> {
            val user = sessionStore.currentUserKey() ?: return AppResult.Failure(AppError.Unauthorized())
            dao.delete(key(user, tripId))
            report("remove", "ok", 0)
            return AppResult.Success(Unit)
        }

        override suspend fun putSnapshot(tripId: Long, sections: JsonObject): AppResult<Unit> =
            when (val r = apiCall { api.putSnapshot(tripId, sections) }) {
                is AppResult.Success -> AppResult.Success(Unit)
                is AppResult.Failure -> r
            }

        override suspend fun purgeAll() {
            val user = sessionStore.currentUserKey()
            if (user != null) {
                dao.deleteAllFor(user)
                // Only pack-related writes: queued expenses and journal entries are the user's own data.
                outboxDao.deleteSnapshotsFor(user)
            }
        }

        private fun report(action: String, result: String, kb: Int) {
            telemetry.emit(TelemetryEvents.OFFLINE_PACK, mapOf("action" to action, "result" to result, "kb" to kb))
        }

        private fun decode(json: String?): OfflinePack? =
            json?.let {
                try {
                    TravelJson.decodeFromString(OfflinePack.serializer(), it)
                } catch (_: SerializationException) {
                    null
                }
            }

        private companion object {
            const val TAG = "OfflinePack"
            const val DISABLED = "offline_pack_disabled"
            const val BYTES_PER_KB = 1024
        }
    }
