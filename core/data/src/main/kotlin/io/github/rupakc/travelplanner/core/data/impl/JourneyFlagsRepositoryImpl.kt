package io.github.rupakc.travelplanner.core.data.impl

import io.github.rupakc.travelplanner.core.common.AppLogger
import io.github.rupakc.travelplanner.core.data.repository.JourneyFlagsRepository
import io.github.rupakc.travelplanner.core.domain.server.JourneyFlags
import io.github.rupakc.travelplanner.core.network.BaseUrlProvider
import io.github.rupakc.travelplanner.core.network.api.ClientConfigApi
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * `GET /api/config` once per base URL per process. A 404 (older backend) is cached as everything off; any other
 * failure is not cached, so the next caller asks again.
 */
@Singleton
class JourneyFlagsRepositoryImpl
    @Inject
    constructor(
        private val api: ClientConfigApi,
        private val baseUrl: BaseUrlProvider,
    ) : JourneyFlagsRepository {
        private val mutex = Mutex()
        private var cachedFor: String? = null
        private var cached: JourneyFlags = JourneyFlags.OFF

        override suspend fun flags(): JourneyFlags =
            mutex.withLock {
                val server = baseUrl.current()?.toString() ?: return JourneyFlags.OFF
                if (cachedFor == server) return cached
                fetch()?.also {
                    cachedFor = server
                    cached = it
                } ?: JourneyFlags.OFF
            }

        @Suppress("TooGenericExceptionCaught")
        private suspend fun fetch(): JourneyFlags? =
            try {
                val response = api.config()
                when {
                    response.isSuccessful -> JourneyFlags(response.body())
                    response.code() == NOT_FOUND -> JourneyFlags.OFF
                    else -> null
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.w("JourneyFlags", "GET /api/config failed; trips features stay off", e)
                null
            }

        private companion object {
            const val NOT_FOUND = 404
        }
    }
