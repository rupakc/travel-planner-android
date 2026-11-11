package io.github.rupakc.travelplanner.core.model.config

import io.github.rupakc.travelplanner.core.model.json.LenientInt
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Response of `GET /api/config`; unknown keys are ignored. */
@Serializable
data class ClientConfigDto(
    @SerialName("public_base_url") val publicBaseUrl: String? = null,
    val telemetry: TelemetryConfigDto? = null,
)

/** The telemetry block of [ClientConfigDto]; values are clamped by the client, not here. */
@Serializable
data class TelemetryConfigDto(
    val enabled: Boolean = false,
    @SerialName("flush_interval_s") val flushIntervalS: LenientInt = null,
    @SerialName("max_batch") val maxBatch: LenientInt = null,
)
