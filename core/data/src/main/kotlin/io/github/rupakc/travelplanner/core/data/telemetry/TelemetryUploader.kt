package io.github.rupakc.travelplanner.core.data.telemetry

import io.github.rupakc.travelplanner.core.common.AppBuildInfo
import io.github.rupakc.travelplanner.core.common.AppClock
import io.github.rupakc.travelplanner.core.common.AppLanguage
import io.github.rupakc.travelplanner.core.datastore.TelemetryPrefsStore
import io.github.rupakc.travelplanner.core.domain.i18n.AppLanguages
import io.github.rupakc.travelplanner.core.domain.telemetry.QueuedEvent
import io.github.rupakc.travelplanner.core.domain.telemetry.SendOutcome
import io.github.rupakc.travelplanner.core.domain.telemetry.SendPolicy
import io.github.rupakc.travelplanner.core.domain.telemetry.TelemetryProps
import io.github.rupakc.travelplanner.core.model.json.TravelJson
import io.github.rupakc.travelplanner.core.model.telemetry.TelemetryBatchDto
import io.github.rupakc.travelplanner.core.model.telemetry.TelemetryClientDto
import io.github.rupakc.travelplanner.core.model.telemetry.TelemetryEventDto
import io.github.rupakc.travelplanner.core.network.api.TelemetryApi
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okio.Buffer
import okio.GzipSink
import okio.buffer

/** Posts one session's events to `POST /api/telemetry`, gzipped. */
@Singleton
internal class TelemetryUploader
    @Inject
    constructor(
        private val api: TelemetryApi,
        private val prefs: TelemetryPrefsStore,
        private val buildInfo: AppBuildInfo,
        private val language: AppLanguage,
        private val clock: AppClock,
    ) : TelemetrySender {
        override suspend fun send(
            sessionId: String,
            events: List<QueuedEvent>,
        ): SendOutcome {
            val batch =
                TelemetryBatchDto(
                    client =
                        TelemetryClientDto(
                            anonId = prefs.anonId(),
                            sessionId = sessionId,
                            platform = "android",
                            appVersion = TelemetryProps.cleanVersion(buildInfo.versionName),
                            locale = AppLanguages.intlTag(language.current()),
                        ),
                    events = events.map { TelemetryEventDto(it.event, it.ts, it.screen, it.props.toJsonProps()) },
                )
            return try {
                val json = TravelJson.encodeToString(TelemetryBatchDto.serializer(), batch)
                val response = api.send("gzip", gzip(json).toRequestBody(JSON))
                SendPolicy.classify(response.code(), response.headers()["Retry-After"], clock.now().toEpochMilli())
            } catch (e: CancellationException) {
                throw e
            } catch (_: IOException) {
                SendOutcome.Retry
            }
        }

        private fun gzip(text: String): ByteArray {
            val out = Buffer()
            GzipSink(out).buffer().use { it.writeUtf8(text) }
            return out.readByteArray()
        }

        private companion object {
            val JSON = "application/json".toMediaType()
        }
    }
