package io.github.rupakc.travelplanner.core.model.config

import io.github.rupakc.travelplanner.core.model.json.LenientInt
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Response of `GET /api/config`; unknown keys are ignored. */
@Serializable
data class ClientConfigDto(
    @SerialName("public_base_url") val publicBaseUrl: String? = null,
    val telemetry: TelemetryConfigDto? = null,
    /** Feature flags (Phase 1/2); absent on older backends, which decode to all-false. */
    val features: FeaturesDto? = null,
    /** Accounts block (Phase 1); absent on older backends. */
    val auth: AuthConfigDto? = null,
)

/** `features` of `GET /api/config`; unknown flags are ignored, absent ones are false. */
@Serializable
data class FeaturesDto(
    val accounts: Boolean = false,
    val game: Boolean = false,
    val trips: Boolean = false,
    val journey: Boolean = false,
    @SerialName("offline_pack") val offlinePack: Boolean = false,
    /** Alerts API version (2 = the v2 routes exist); null or 0 on older backends. */
    @SerialName("alerts_api") val alertsApi: LenientInt = null,
    @SerialName("price_alerts_v2") val priceAlertsV2: Boolean = false,
    /** A SerpAPI key is configured server-side: one-tap watches and suggestions can work. */
    @SerialName("live_prices") val livePrices: Boolean = false,
    /** Notifications API version (1 = `/notification-settings` and the catalog exist); null or 0 on older backends. */
    @SerialName("notifications_api") val notificationsApi: LenientInt = null,
    /** Category checks, trip pushes and platform routing are active server side. */
    @SerialName("notifications_v2") val notificationsV2: Boolean = false,
    /** An Apple push key is configured (iOS only; informational on Android). */
    val apns: Boolean = false,
)

/** `auth` of `GET /api/config`. [signupMode] is `open` | `invite`. */
@Serializable
data class AuthConfigDto(
    @SerialName("signup_mode") val signupMode: String? = null,
    @SerialName("signup_available") val signupAvailable: Boolean = false,
    @SerialName("password_reset_available") val passwordResetAvailable: Boolean = false,
    @SerialName("min_password_length") val minPasswordLength: LenientInt = null,
    @SerialName("verify_ttl_s") val verifyTtlS: LenientInt = null,
)

/** The telemetry block of [ClientConfigDto]; values are clamped by the client, not here. */
@Serializable
data class TelemetryConfigDto(
    val enabled: Boolean = false,
    @SerialName("flush_interval_s") val flushIntervalS: LenientInt = null,
    @SerialName("max_batch") val maxBatch: LenientInt = null,
)
