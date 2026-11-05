package io.github.rupakc.travelplanner.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.rupakc.travelplanner.core.common.AppClock
import io.github.rupakc.travelplanner.core.common.AppLogger
import io.github.rupakc.travelplanner.core.model.auth.User
import io.github.rupakc.travelplanner.core.model.json.TravelJson
import io.github.rupakc.travelplanner.core.model.settings.Session
import java.util.Base64
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

/**
 * Session store sealed with an AEAD (Tink, Keystore-wrapped key; review I4).
 *
 * - The session (token, user, issuing base URL) is one encrypted value; nothing about the token is stored
 *   in clear. The plaintext layout of earlier builds is migrated on first read.
 * - Never crashes and never locks the user out (review R2-I1): any failure reading or decrypting a stored
 *   value reads as signed out. If no cipher can be created (broken Keystore — [cipherFactory] returns null
 *   or throws) or sealing fails, the session is kept **in memory only** for this process (the user stays
 *   signed in until the app is killed; nothing is written in clear) and the failure is logged once.
 * - The JWT `exp` is checked against the wall [clock]: an expired token reads as signed out, and a live
 *   session flips to null at expiry. The wall clock is re-read at least every [EXPIRY_TICK_MS] of awake
 *   time, so a device that slept through `exp` signs out within that interval of waking (review R2-M2).
 *   A device clock running ahead signs out early; that is accepted.
 * - Crypto and file work run on [ioDispatcher], never on the caller's (main) thread (review R2-M1).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EncryptedSessionStore(
    private val store: DataStore<Preferences>,
    cipherFactory: () -> SessionCipher?,
    private val clock: AppClock,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : SessionStore {
    private val degradedLogged = AtomicBoolean(false)
    private val cipher: SessionCipher? by lazy {
        @Suppress("TooGenericExceptionCaught")
        val created =
            try {
                cipherFactory()
            } catch (e: Exception) {
                AppLogger.e(TAG, "Session cipher unavailable", e)
                null
            }
        if (created == null) logDegraded()
        created
    }
    private val sealed = stringPreferencesKey("session_v2")
    private val legacyToken = stringPreferencesKey("token")
    private val legacyUser = stringPreferencesKey("user_json")
    private val lastUser = stringPreferencesKey("last_username")
    private val lastServerKey = stringPreferencesKey("last_server")

    /** Session held in memory when it cannot be sealed (degraded mode). Takes precedence over the file. */
    private val memory = MutableStateFlow<Session?>(null)

    @Serializable
    private data class Record(val token: String, val user: User, val baseUrl: String? = null)

    private val stored: Flow<Session?> =
        store.safeData()
            .map { readOrMigrate(it) }
            .flowOn(ioDispatcher)

    private val effective: Flow<Session?> = combine(memory, stored) { mem, file -> mem ?: file }.distinctUntilChanged()

    override val session: Flow<Session?> =
        effective.flatMapLatest { s -> if (s == null) flowOf(null) else untilExpiry(s) }.distinctUntilChanged()

    override suspend fun current(): Session? = effective.first()?.takeUnless { isExpired(it) }

    override suspend fun save(session: Session) =
        withContext(ioDispatcher) {
            val sealedValue = seal(session)
            store.edit {
                if (sealedValue != null) it[sealed] = sealedValue else it.remove(sealed)
                it[lastUser] = session.user.username
                session.baseUrl?.let { url -> it[lastServerKey] = url }
                it.remove(legacyToken)
                it.remove(legacyUser)
            }
            memory.value = if (sealedValue == null) session else null
        }

    override suspend fun updateToken(token: String) {
        current()?.let { save(it.copy(token = token)) }
    }

    override suspend fun updateUser(user: User) {
        effective.first()?.let { save(it.copy(user = user)) }
    }

    override suspend fun clear() {
        memory.value = null
        withContext(ioDispatcher) {
            store.edit {
                it.remove(sealed)
                it.remove(legacyToken)
                it.remove(legacyUser)
            }
        }
    }

    override suspend fun lastUsername(): String? = withContext(ioDispatcher) { store.safeData().first()[lastUser] }

    override suspend fun lastServer(): String? = withContext(ioDispatcher) { store.safeData().first()[lastServerKey] }

    private fun untilExpiry(session: Session): Flow<Session?> =
        flow {
            val exp = JwtExpiry.expiresAtEpochSeconds(session.token)
            if (exp == null) {
                emit(session)
                return@flow
            }
            var announced = false
            while (true) {
                val remaining = exp * MILLIS - clock.now().toEpochMilli()
                if (remaining <= 0) {
                    emit(null)
                    return@flow
                }
                if (!announced) {
                    emit(session)
                    announced = true
                }
                // delay() runs on uptime and stops in deep sleep: re-read the wall clock regularly.
                delay(minOf(remaining, EXPIRY_TICK_MS))
            }
        }

    private fun isExpired(session: Session): Boolean {
        val exp = JwtExpiry.expiresAtEpochSeconds(session.token) ?: return false
        return exp * MILLIS <= clock.now().toEpochMilli()
    }

    /** Never throws (except cancellation): anything unreadable is signed out. */
    private suspend fun readOrMigrate(p: Preferences): Session? {
        p[sealed]?.let { return readSealed(it) }
        val token = p[legacyToken] ?: return null
        val user = p[legacyUser]?.let { runCatching { TravelJson.decodeFromString(User.serializer(), it) }.getOrNull() } ?: return null
        val legacy = Session(token, user)
        // Migrate; if sealing is impossible, save() keeps it in memory and still removes the plaintext.
        safely { save(legacy) }
        return legacy
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun readSealed(value: String): Session? {
        val c = cipher ?: return null
        return try {
            val plain = c.decrypt(Base64.getDecoder().decode(value), AAD)
            val record = TravelJson.decodeFromString(Record.serializer(), plain.decodeToString())
            Session(record.token, record.user, record.baseUrl)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.w(TAG, "Stored session could not be read; signing out", e)
            safely { store.edit { it.remove(sealed) } }
            null
        }
    }

    /** Seals [session], or null when no cipher is available or encryption fails (degraded, in-memory mode). */
    @Suppress("TooGenericExceptionCaught")
    private fun seal(session: Session): String? {
        val c = cipher ?: return null
        return try {
            val json = TravelJson.encodeToString(Record.serializer(), Record(session.token, session.user, session.baseUrl))
            Base64.getEncoder().encodeToString(c.encrypt(json.encodeToByteArray(), AAD))
        } catch (e: Exception) {
            AppLogger.e(TAG, "Could not seal the session", e)
            logDegraded()
            null
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun safely(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.w(TAG, "Session storage write failed", e)
        }
    }

    private fun logDegraded() {
        if (degradedLogged.compareAndSet(false, true)) {
            AppLogger.w(TAG, "Session encryption unavailable: keeping the session in memory for this process only")
        }
    }

    companion object {
        private const val TAG = "SessionStore"
        private const val MILLIS = 1000L

        /** Longest stretch of awake time between wall-clock expiry checks. */
        const val EXPIRY_TICK_MS = 30_000L
        private val AAD = "travelplanner.session.v2".encodeToByteArray()
    }
}
