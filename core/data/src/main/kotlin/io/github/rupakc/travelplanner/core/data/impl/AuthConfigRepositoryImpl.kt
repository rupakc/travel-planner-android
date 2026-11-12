package io.github.rupakc.travelplanner.core.data.impl

import io.github.rupakc.travelplanner.core.common.AppLogger
import io.github.rupakc.travelplanner.core.data.repository.AuthConfigRepository
import io.github.rupakc.travelplanner.core.data.repository.AuthFlags
import io.github.rupakc.travelplanner.core.domain.server.JourneyFlags
import io.github.rupakc.travelplanner.core.network.BaseUrlProvider
import io.github.rupakc.travelplanner.core.network.api.ClientConfigApi
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** `GET /api/config` once per base URL per process (a failure is not cached, so the next screen asks again). */
@Singleton
class AuthConfigRepositoryImpl
    @Inject
    constructor(
        private val api: ClientConfigApi,
        private val baseUrl: BaseUrlProvider,
    ) : AuthConfigRepository {
        private val mutex = Mutex()
        private var cachedFor: String? = null
        private var cached: AuthFlags = AuthFlags()

        override suspend fun flags(): AuthFlags =
            mutex.withLock {
                val server = baseUrl.current()?.toString() ?: return AuthFlags()
                if (cachedFor == server) return cached
                fetch()?.also {
                    cachedFor = server
                    cached = it
                } ?: AuthFlags()
            }

        @Suppress("TooGenericExceptionCaught")
        private suspend fun fetch(): AuthFlags? =
            try {
                val response = api.config()
                val config = response.body()
                if (!response.isSuccessful || config == null) {
                    null
                } else {
                    val journey = JourneyFlags(config)
                    AuthFlags(
                        signupAvailable = journey.signupAvailable,
                        resetAvailable = journey.resetAvailable,
                        inviteRequired = config.auth?.signupMode == "invite",
                        minPasswordLength = config.auth?.minPasswordLength?.takeIf { it > 0 } ?: AuthFlags.DEFAULT_MIN_PASSWORD_LENGTH,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.w("AuthConfig", "GET /api/config failed; sign-up and reset stay hidden", e)
                null
            }
    }
