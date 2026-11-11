package io.github.rupakc.travelplanner.core.data.telemetry

import io.github.rupakc.travelplanner.core.common.AppLogger
import io.github.rupakc.travelplanner.core.domain.telemetry.TelemetrySettings
import io.github.rupakc.travelplanner.core.domain.telemetry.fromServer
import io.github.rupakc.travelplanner.core.network.BaseUrlProvider
import io.github.rupakc.travelplanner.core.network.api.ClientConfigApi
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * `GET /api/config` once per base URL per process. A 404 (older backend), any error or `enabled != true`
 * is null, and that answer is cached too: no retries. A changed base URL is a new server and is fetched again.
 */
@Singleton
class ClientConfigRepositoryImpl
    @Inject
    constructor(
        private val api: ClientConfigApi,
        private val baseUrl: BaseUrlProvider,
    ) : ClientConfigRepository {
        private val mutex = Mutex()
        private var cachedFor: String? = null
        private var cached: TelemetrySettings? = null

        override suspend fun telemetrySettings(): TelemetrySettings? =
            mutex.withLock {
                val server = baseUrl.current()?.toString() ?: return null
                if (cachedFor == server) return cached
                val result = fetch()
                cachedFor = server
                cached = result
                result
            }

        private suspend fun fetch(): TelemetrySettings? =
            try {
                val response = api.config()
                val telemetry = response.body()?.telemetry.takeIf { response.isSuccessful }
                if (telemetry?.enabled == true) TelemetrySettings.fromServer(telemetry.flushIntervalS, telemetry.maxBatch) else null
            } catch (e: CancellationException) {
                throw e
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception,
            ) {
                AppLogger.w("ClientConfig", "GET /api/config failed; telemetry stays off for this server", e)
                null
            }
    }
