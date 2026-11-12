package io.github.rupakc.travelplanner.core.data.offline

import io.github.rupakc.travelplanner.core.common.AppClock
import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppLogger
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.data.repository.FlushResult
import io.github.rupakc.travelplanner.core.data.repository.Outbox
import io.github.rupakc.travelplanner.core.data.repository.OutboxRequest
import io.github.rupakc.travelplanner.core.data.telemetry.Telemetry
import io.github.rupakc.travelplanner.core.database.OutboxDao
import io.github.rupakc.travelplanner.core.database.OutboxEntity
import io.github.rupakc.travelplanner.core.datastore.SessionStore
import io.github.rupakc.travelplanner.core.domain.telemetry.TelemetryEvents
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Singleton
internal class OutboxImpl
    @Inject
    constructor(
        private val dao: OutboxDao,
        private val sessionStore: SessionStore,
        private val sender: OutboxSender,
        private val scheduler: OutboxFlushScheduler,
        private val telemetry: Telemetry,
        private val clock: AppClock,
    ) : Outbox {
        private val locks = ConcurrentHashMap<String, Mutex>()

        override suspend fun enqueue(request: OutboxRequest): AppResult<Unit> {
            val user = sessionStore.currentUserKey() ?: return AppResult.Failure(AppError.Unauthorized())
            return try {
                dao.enqueue(
                    OutboxEntity(
                        userId = user,
                        method = request.method.uppercase(),
                        path = request.path,
                        body = request.body,
                        key = request.key,
                        createdAt = clock.now().toEpochMilli(),
                    ),
                )
                scheduler.schedule()
                AppResult.Success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception,
            ) {
                // Includes SQLiteFullException (storage limit): the caller keeps the item on screen as unsynced.
                AppLogger.w(TAG, "Could not queue a write", e)
                AppResult.Failure(AppError.Unknown())
            }
        }

        override fun pendingKeys(): Flow<Set<String>> =
            sessionStore.session.flatMapLatest { session ->
                if (session == null) flowOf(emptySet()) else dao.observeKeys(session.userKey()).map { it.toSet() }
            }.distinctUntilChanged()

        override suspend fun flush(userId: String?): FlushResult {
            val current = sessionStore.currentUserKey() ?: return FlushResult()
            val user = userId ?: current
            // The token on the wire is the signed-in user's: another user's writes wait for that user.
            if (user != current) return FlushResult()
            val result = locks.getOrPut(user) { Mutex() }.withLock { drain(user) }
            if (result.sent + result.failed + result.dropped > 0) {
                telemetry.emit(
                    TelemetryEvents.OUTBOX_FLUSH,
                    mapOf("sent" to result.sent, "failed" to result.failed, "dropped" to result.dropped),
                )
            }
            if (dao.count(user) == 0) scheduler.cancelPeriodic()
            return result
        }

        @Suppress("CyclomaticComplexMethod")
        private suspend fun drain(user: String): FlushResult {
            var sent = 0
            var failed = 0
            var dropped = 0
            var after = 0L
            var item = dao.next(user, after)
            while (item != null) {
                after = item.id
                val outcome = send(item)
                when (outcome) {
                    Outcome.SENT -> {
                        sent++
                        remove(item.id)
                    }

                    Outcome.DROP -> {
                        dropped++
                        remove(item.id)
                    }

                    Outcome.KEEP, Outcome.KEEP_AND_STOP -> {
                        failed++
                    }
                }
                // The rest would fail the same way (offline, signed out, throttled).
                item = if (outcome == Outcome.KEEP_AND_STOP) null else dao.next(user, after)
            }
            return FlushResult(sent, failed, dropped)
        }

        private suspend fun remove(id: Long) = withContext(NonCancellable) { dao.delete(id) }

        private suspend fun send(item: OutboxEntity): Outcome =
            try {
                classify(sender.send(item.method, item.path, item.body))
            } catch (e: CancellationException) {
                throw e
            } catch (_: IOException) {
                Outcome.KEEP_AND_STOP
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception,
            ) {
                // A request that cannot even be built never succeeds.
                AppLogger.w(TAG, "Dropping an unsendable write", e)
                Outcome.DROP
            }

        private enum class Outcome { SENT, DROP, KEEP, KEEP_AND_STOP }

        private companion object {
            const val TAG = "Outbox"
            const val CONFLICT = 409
            const val UNAUTHORIZED = 401
            const val TIMEOUT = 408
            const val TOO_MANY = 429
            const val OK_FROM = 200
            const val OK_TO = 299
            const val CLIENT_FROM = 400
            const val CLIENT_TO = 499

            fun classify(status: Int): Outcome =
                when (status) {
                    in OK_FROM..OK_TO, CONFLICT -> Outcome.SENT
                    UNAUTHORIZED, TIMEOUT, TOO_MANY -> Outcome.KEEP_AND_STOP
                    in CLIENT_FROM..CLIENT_TO -> Outcome.DROP
                    else -> Outcome.KEEP
                }
        }
    }
