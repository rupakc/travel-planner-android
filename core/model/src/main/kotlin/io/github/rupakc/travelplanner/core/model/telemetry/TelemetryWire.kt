package io.github.rupakc.travelplanner.core.model.telemetry

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** Body of `POST /api/telemetry`: one client block and the events of a single session. */
@Serializable
data class TelemetryBatchDto(val client: TelemetryClientDto, val events: List<TelemetryEventDto>)

/** Who is sending: a random install id, the session, and app facts. */
@Serializable
data class TelemetryClientDto(
    @SerialName("anon_id") val anonId: String,
    @SerialName("session_id") val sessionId: String,
    val platform: String = "android",
    @SerialName("app_version") val appVersion: String,
    val locale: String,
)

/** One event on the wire; [props] values are tokens, integers, finite numbers or booleans. */
@Serializable
data class TelemetryEventDto(
    val event: String,
    val ts: Long,
    val screen: String? = null,
    val props: JsonObject = JsonObject(emptyMap()),
)
