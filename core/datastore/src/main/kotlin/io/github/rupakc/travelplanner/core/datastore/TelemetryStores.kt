package io.github.rupakc.travelplanner.core.datastore

import io.github.rupakc.travelplanner.core.model.settings.ConsentChoice
import kotlinx.coroutines.flow.Flow

/** The telemetry session persisted between processes: its id and the last activity time (epoch ms). */
data class StoredSession(val id: String, val lastMs: Long)

/**
 * Device-only telemetry identity and consent. Not cleared on sign-out (web parity: `tp_anon` and
 * `tp_consent` survive); excluded from cloud backup in the app's backup rules.
 */
interface TelemetryPrefsStore {
    /** Random UUID, created and persisted on the first call; an invalid stored value is replaced. */
    suspend fun anonId(): String

    /** The user's explicit choice, or null until they choose. */
    val consent: Flow<ConsentChoice?>

    suspend fun setConsent(choice: ConsentChoice)

    suspend fun session(): StoredSession?

    suspend fun setSession(session: StoredSession)
}

/** File name of the telemetry DataStore (`datastore/telemetry.preferences_pb`). */
object TelemetryDataStoreFiles {
    const val TELEMETRY = "telemetry"
}
