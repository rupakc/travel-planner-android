package io.github.rupakc.travelplanner.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.rupakc.travelplanner.core.model.settings.ConsentChoice
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class TelemetryPrefsStoreImpl(private val store: DataStore<Preferences>) : TelemetryPrefsStore {
    private val anon = stringPreferencesKey("anon_id")
    private val consentKey = stringPreferencesKey("consent")
    private val sessionId = stringPreferencesKey("session_id")
    private val sessionLast = longPreferencesKey("session_last_ms")

    override suspend fun anonId(): String {
        store.safeData().first()[anon]?.takeIf(::isUuid)?.let { return it }
        var result = ""
        store.edit { p ->
            // Re-check inside the write so concurrent first calls agree on one id.
            result = p[anon]?.takeIf(::isUuid) ?: UUID.randomUUID().toString().also { p[anon] = it }
        }
        return result
    }

    override val consent: Flow<ConsentChoice?> =
        store.safeData().map { p -> ConsentChoice.entries.firstOrNull { it.wire == p[consentKey] } }

    override suspend fun setConsent(choice: ConsentChoice) {
        store.edit { it[consentKey] = choice.wire }
    }

    override suspend fun session(): StoredSession? {
        val p = store.safeData().first()
        val id = p[sessionId]?.takeIf(String::isNotBlank) ?: return null
        return StoredSession(id, p[sessionLast] ?: return null)
    }

    override suspend fun setSession(session: StoredSession) {
        store.edit {
            it[sessionId] = session.id
            it[sessionLast] = session.lastMs
        }
    }

    private fun isUuid(value: String): Boolean = runCatching { UUID.fromString(value) }.getOrNull()?.toString() == value.lowercase()
}
