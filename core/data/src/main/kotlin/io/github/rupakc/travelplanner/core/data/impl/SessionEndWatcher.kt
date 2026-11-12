package io.github.rupakc.travelplanner.core.data.impl

import io.github.rupakc.travelplanner.core.common.AppLogger
import io.github.rupakc.travelplanner.core.data.di.ApplicationScope
import io.github.rupakc.travelplanner.core.data.repository.SessionCleaner
import io.github.rupakc.travelplanner.core.network.AuthEvent
import io.github.rupakc.travelplanner.core.network.AuthEvents
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Wipes the signed-in user's local data (offline packs, outbox, plan draft, cached plans...) when the SERVER ends
 * the session: a dead refresh token (`refresh_invalid`), a revoked session (`session_revoked`) or a `401` on the
 * retry. The network layer has already cleared the token and emitted [AuthEvent.SessionExpired]; without this
 * the data would stay on the phone until a different user signed in.
 */
@Singleton
class SessionEndWatcher
    @Inject
    constructor(
        private val authEvents: AuthEvents,
        private val cleaner: SessionCleaner,
        @ApplicationScope private val scope: CoroutineScope,
    ) {
        private var job: Job? = null

        /** Starts collecting once per process (idempotent). */
        @Synchronized
        fun start() {
            if (job != null) return
            job =
                scope.launch {
                    authEvents.flow.collect { event ->
                        if (event is AuthEvent.SessionExpired) wipe()
                    }
                }
        }

        @Suppress("TooGenericExceptionCaught")
        private suspend fun wipe() {
            try {
                cleaner.clearUserData()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e("SessionEndWatcher", "Could not wipe local data after the session ended", e)
            }
        }
    }
