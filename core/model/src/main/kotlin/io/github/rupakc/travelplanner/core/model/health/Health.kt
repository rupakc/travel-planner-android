package io.github.rupakc.travelplanner.core.model.health

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** `GET /health` — capability flags (fare calendar / watches / email). */
@Serializable
data class Health(
    val status: String? = null,
    val service: String? = null,
    @SerialName("serpapi_configured") val serpapiConfigured: Boolean = false,
    @SerialName("fare_watch_available") val fareWatchAvailable: Boolean = false,
    @SerialName("email_alerts_available") val emailAlertsAvailable: Boolean = false,
    /** FCM push sender configured on the server. */
    @SerialName("push_configured") val pushConfigured: Boolean = false,
)

/** `GET /health/agents` (makes an LLM call; cached 5 min server-side). */
@Serializable
data class AgentsHealth(
    /** "ok" | "empty-response" | "error: <ExcType>" */
    val llm: String? = null,
    @SerialName("serpapi_configured") val serpapiConfigured: Boolean = false,
    @SerialName("backup_configured") val backupConfigured: Boolean = false,
)
