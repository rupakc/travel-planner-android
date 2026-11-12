package io.github.rupakc.travelplanner.core.network

import io.github.rupakc.travelplanner.core.common.AppClock
import io.github.rupakc.travelplanner.core.common.AppLogger
import io.github.rupakc.travelplanner.core.datastore.SessionStore
import io.github.rupakc.travelplanner.core.model.auth.RefreshRequest
import io.github.rupakc.travelplanner.core.model.auth.RefreshResponse
import io.github.rupakc.travelplanner.core.model.json.TravelJson
import io.github.rupakc.travelplanner.core.model.settings.Session
import io.github.rupakc.travelplanner.core.network.api.AccountApi
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import retrofit2.HttpException

/** Result of [SessionManager.refresh]. */
sealed interface RefreshOutcome {
    /** A valid session is stored (rotated now, or already by another caller). */
    data class Refreshed(val session: Session) : RefreshOutcome

    /** The refresh token is dead: the session was cleared and [AuthEvent.SessionExpired] emitted. */
    data object Invalid : RefreshOutcome

    /** Network error, 429 or 5xx: nothing changed, the user stays signed in. */
    data object Transient : RefreshOutcome
}

/** Calls `POST /auth/refresh` on a client that carries no bearer token. */
fun interface RefreshCall {
    suspend fun refresh(refreshToken: String): RefreshResponse
}

/** Retrofit-backed [RefreshCall]. */
class ApiRefreshCall(private val api: AccountApi) : RefreshCall {
    override suspend fun refresh(refreshToken: String): RefreshResponse = api.refresh(RefreshRequest(refreshToken))
}

/**
 * Single-flight access-token refresh (Accounts-API "Mobile flow" 4). Every caller passes the access token
 * that was rejected; once the stored token differs, the refresh already happened and no second call is made.
 * The rotated pair is persisted before the lock is released, so retries always use the new token.
 */
@Singleton
class SessionManager
    @Inject
    constructor(
        private val sessionStore: SessionStore,
        private val authEvents: AuthEvents,
        private val refreshCall: RefreshCall,
        private val clock: AppClock,
    ) {
        private val mutex = Mutex()

        /** True when [session] carries a refresh token and its access token expires within [PREEMPTIVE_MS]. */
        fun needsPreemptiveRefresh(session: Session): Boolean {
            val exp = session.expiresAtEpochMs ?: return false
            return session.refreshToken != null && exp - clock.now().toEpochMilli() <= PREEMPTIVE_MS
        }

        suspend fun refresh(rejectedAccessToken: String? = null): RefreshOutcome =
            mutex.withLock {
                val session = sessionStore.current() ?: return@withLock RefreshOutcome.Invalid
                if (rejectedAccessToken != null && session.token != rejectedAccessToken) {
                    return@withLock RefreshOutcome.Refreshed(session)
                }
                val refreshToken = session.refreshToken ?: return@withLock RefreshOutcome.Invalid
                when (val first = attempt(refreshToken)) {
                    is Attempt.Ok -> persist(session, refreshToken, first.response)
                    Attempt.Transient -> RefreshOutcome.Transient
                    Attempt.Dead -> invalidate()
                    is Attempt.Stale -> retryAfterStale(session)
                    is Attempt.RateLimited -> retryAfterLimit(session, refreshToken, first.waitSeconds)
                }
            }

        private suspend fun retryAfterStale(session: Session): RefreshOutcome {
            // Another process may have rotated: use the newest stored token once, else the family is gone.
            val newest = sessionStore.current() ?: session
            val token = newest.refreshToken ?: return invalidate()
            return when (val second = attempt(token)) {
                is Attempt.Ok -> persist(newest, token, second.response)
                Attempt.Transient, is Attempt.RateLimited -> RefreshOutcome.Transient
                Attempt.Dead, is Attempt.Stale -> invalidate()
            }
        }

        private suspend fun retryAfterLimit(session: Session, token: String, waitSeconds: Long?): RefreshOutcome {
            if (waitSeconds == null || waitSeconds > MAX_WAIT_SECONDS) return RefreshOutcome.Transient
            delay(waitSeconds * MILLIS)
            return when (val second = attempt(token)) {
                is Attempt.Ok -> persist(session, token, second.response)
                Attempt.Dead -> invalidate()
                else -> RefreshOutcome.Transient
            }
        }

        private suspend fun persist(old: Session, usedToken: String, r: RefreshResponse): RefreshOutcome {
            val updated =
                old.copy(
                    token = r.accessToken,
                    refreshToken = r.refreshToken ?: usedToken,
                    expiresAtEpochMs = r.expiresIn?.let { clock.now().toEpochMilli() + it * MILLIS },
                )
            sessionStore.save(updated)
            return RefreshOutcome.Refreshed(updated)
        }

        private suspend fun invalidate(): RefreshOutcome {
            sessionStore.clear()
            authEvents.emit(AuthEvent.SessionExpired)
            return RefreshOutcome.Invalid
        }

        private sealed interface Attempt {
            data class Ok(val response: RefreshResponse) : Attempt

            data object Dead : Attempt

            data object Stale : Attempt

            data object Transient : Attempt

            data class RateLimited(val waitSeconds: Long?) : Attempt
        }

        @Suppress("TooGenericExceptionCaught")
        private suspend fun attempt(refreshToken: String): Attempt =
            try {
                Attempt.Ok(refreshCall.refresh(refreshToken))
            } catch (e: CancellationException) {
                throw e
            } catch (e: HttpException) {
                classify(e)
            } catch (e: Exception) {
                // IOException (offline) and anything unexpected: never sign out on a transient failure.
                AppLogger.w("SessionManager", "Refresh failed: ${e::class.java.simpleName}")
                Attempt.Transient
            }

        private fun classify(e: HttpException): Attempt =
            when (e.code()) {
                HTTP_UNAUTHORIZED -> {
                    if (errorCode(e.response()?.errorBody()?.string()) == "refresh_stale") Attempt.Stale else Attempt.Dead
                }

                HTTP_TOO_MANY -> {
                    Attempt.RateLimited(HttpErrorMapper.parseRetryAfter(e.response()?.headers()?.get("Retry-After")))
                }

                else -> {
                    Attempt.Transient
                }
            }

        companion object {
            const val PREEMPTIVE_MS = 60_000L
            private const val MAX_WAIT_SECONDS = 5L
            private const val MILLIS = 1000L
            private const val HTTP_UNAUTHORIZED = 401
            private const val HTTP_TOO_MANY = 429

            /** `error_code` of a JSON error body, or null. */
            fun errorCode(body: String?): String? =
                runCatching {
                    val obj: JsonObject = TravelJson.parseToJsonElement(body.orEmpty()).jsonObject
                    (obj["error_code"] as? JsonPrimitive)?.content
                }.getOrNull()
        }
    }
