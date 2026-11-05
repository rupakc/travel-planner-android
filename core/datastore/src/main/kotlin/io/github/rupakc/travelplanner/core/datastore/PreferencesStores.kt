package io.github.rupakc.travelplanner.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import io.github.rupakc.travelplanner.core.common.AppClock
import io.github.rupakc.travelplanner.core.common.AppLogger
import io.github.rupakc.travelplanner.core.model.auth.User
import io.github.rupakc.travelplanner.core.model.json.TravelJson
import io.github.rupakc.travelplanner.core.model.settings.AppSettings
import io.github.rupakc.travelplanner.core.model.settings.FeatureOverrides
import io.github.rupakc.travelplanner.core.model.settings.Session
import io.github.rupakc.travelplanner.core.model.settings.ThemeMode
import io.github.rupakc.travelplanner.core.model.settings.TravellerProfile
import java.io.IOException
import java.security.GeneralSecurityException
import java.util.Base64
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transform
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

private fun DataStore<Preferences>.safeData(): Flow<Preferences> =
    data.catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }

/**
 * Session store sealed with an AEAD (Tink, Keystore-wrapped key; review I4).
 *
 * - The session (token, user, issuing base URL) is one encrypted value; nothing about the token is
 *   stored in clear. The plaintext layout of earlier builds is migrated on first read.
 * - A value that fails to decrypt (lost Keystore key, corrupt file) reads as signed out and is removed.
 * - The JWT `exp` claim is checked against [clock]: an expired token reads as signed out, and a live
 *   session flips to null at its expiry instant, so the auth gate routes to Login without waiting for a 401.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EncryptedSessionStore(
    private val store: DataStore<Preferences>,
    cipherFactory: () -> SessionCipher,
    private val clock: AppClock,
) : SessionStore {
    private val cipher by lazy(cipherFactory)
    private val sealed = stringPreferencesKey("session_v2")
    private val legacyToken = stringPreferencesKey("token")
    private val legacyUser = stringPreferencesKey("user_json")
    private val lastUser = stringPreferencesKey("last_username")

    @Serializable
    private data class Record(val token: String, val user: User, val baseUrl: String? = null)

    private sealed interface Read {
        data class Ok(val session: Session) : Read

        data object Empty : Read

        data object Corrupt : Read

        data class Legacy(val session: Session) : Read
    }

    private val stored: Flow<Session?> =
        store.safeData()
            .map { read(it) }
            .transform { r ->
                when (r) {
                    is Read.Ok -> {
                        emit(r.session)
                    }

                    Read.Empty -> {
                        emit(null)
                    }

                    Read.Corrupt -> {
                        store.edit { it.remove(sealed) }
                        emit(null)
                    }

                    is Read.Legacy -> {
                        save(r.session)
                        emit(r.session)
                    }
                }
            }
            .distinctUntilChanged()

    override val session: Flow<Session?> = stored.flatMapLatest { s -> if (s == null) flowOf(null) else untilExpiry(s) }.distinctUntilChanged()

    override suspend fun current(): Session? = stored.first()?.takeUnless { isExpired(it) }

    override suspend fun save(session: Session) {
        val sealedValue = seal(Record(session.token, session.user, session.baseUrl))
        store.edit {
            it[sealed] = sealedValue
            it[lastUser] = session.user.username
            it.remove(legacyToken)
            it.remove(legacyUser)
        }
    }

    override suspend fun updateToken(token: String) {
        current()?.let { save(it.copy(token = token)) }
    }

    override suspend fun updateUser(user: User) {
        stored.first()?.let { save(it.copy(user = user)) }
    }

    override suspend fun clear() {
        store.edit {
            it.remove(sealed)
            it.remove(legacyToken)
            it.remove(legacyUser)
        }
    }

    override suspend fun lastUsername(): String? = store.safeData().first()[lastUser]

    private fun untilExpiry(session: Session): Flow<Session?> =
        flow {
            val exp = JwtExpiry.expiresAtEpochSeconds(session.token)
            if (exp == null) {
                emit(session)
                return@flow
            }
            val remaining = exp * MILLIS - clock.now().toEpochMilli()
            if (remaining <= 0) {
                emit(null)
                return@flow
            }
            emit(session)
            delay(remaining)
            emit(null)
        }

    private fun isExpired(session: Session): Boolean {
        val exp = JwtExpiry.expiresAtEpochSeconds(session.token) ?: return false
        return exp * MILLIS <= clock.now().toEpochMilli()
    }

    private fun read(p: Preferences): Read {
        p[sealed]?.let { value ->
            return try {
                val plain = cipher.decrypt(Base64.getDecoder().decode(value), AAD)
                val record = TravelJson.decodeFromString(Record.serializer(), plain.decodeToString())
                Read.Ok(Session(record.token, record.user, record.baseUrl))
            } catch (e: GeneralSecurityException) {
                AppLogger.w(TAG, "Stored session could not be decrypted; signing out", e)
                Read.Corrupt
            } catch (e: IllegalArgumentException) {
                AppLogger.w(TAG, "Stored session is malformed; signing out", e)
                Read.Corrupt
            }
        }
        val token = p[legacyToken] ?: return Read.Empty
        val user = p[legacyUser]?.let { runCatching { TravelJson.decodeFromString(User.serializer(), it) }.getOrNull() } ?: return Read.Empty
        return Read.Legacy(Session(token, user))
    }

    private fun seal(record: Record): String =
        Base64.getEncoder().encodeToString(cipher.encrypt(TravelJson.encodeToString(Record.serializer(), record).encodeToByteArray(), AAD))

    private companion object {
        const val TAG = "SessionStore"
        const val MILLIS = 1000L
        val AAD = "travelplanner.session.v2".encodeToByteArray()
    }
}

class PreferencesSettingsStore(private val store: DataStore<Preferences>) : SettingsStore {
    private val baseUrl = stringPreferencesKey("base_url")
    private val theme = stringPreferencesKey("theme_mode")
    private val dynamic = booleanPreferencesKey("dynamic_color")
    private val locale = stringPreferencesKey("locale_tag")
    private val trip = booleanPreferencesKey("notify_trip")
    private val price = booleanPreferencesKey("notify_price")
    private val nearby = booleanPreferencesKey("notify_nearby")

    override val settings: Flow<AppSettings> =
        store.safeData().map { p ->
            AppSettings(
                baseUrlOverride = p[baseUrl]?.takeIf(String::isNotBlank),
                themeMode = p[theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
                dynamicColor = p[dynamic] ?: false,
                localeTag = p[locale]?.takeIf(String::isNotBlank),
                tripNotifications = p[trip] ?: true,
                priceAlertNotifications = p[price] ?: true,
                nearbyNotifications = p[nearby] ?: false,
            )
        }.distinctUntilChanged()

    override suspend fun current(): AppSettings = settings.first()

    override suspend fun setBaseUrlOverride(url: String?) = store.editKey(baseUrl, url?.trim()?.trimEnd('/'))

    override suspend fun setThemeMode(mode: ThemeMode) = store.editKey(theme, mode.name)

    override suspend fun setDynamicColor(enabled: Boolean) {
        store.edit { it[dynamic] = enabled }
    }

    override suspend fun setLocaleTag(tag: String?) = store.editKey(locale, tag)

    override suspend fun setNotificationToggles(trip: Boolean?, priceAlerts: Boolean?, nearby: Boolean?) {
        store.edit { p ->
            trip?.let { p[this.trip] = it }
            priceAlerts?.let { p[price] = it }
            nearby?.let { p[this.nearby] = it }
        }
    }
}

class PreferencesTravellerProfileStore(private val store: DataStore<Preferences>) : TravellerProfileStore {
    private val completed = booleanPreferencesKey("onboarding_completed")
    private val primary = stringPreferencesKey("primary_type")
    private val secondary = stringPreferencesKey("secondary_types")
    private val answers = stringPreferencesKey("answers_json")
    private val completedAt = longPreferencesKey("completed_at")
    private val answersSerializer = MapSerializer(String.serializer(), ListSerializer(String.serializer()))

    override val profile: Flow<TravellerProfile> =
        store.safeData().map { p ->
            TravellerProfile(
                onboardingCompleted = p[completed] ?: false,
                primaryType = p[primary],
                secondaryTypes = p[secondary]?.split(',')?.filter(String::isNotBlank).orEmpty(),
                answers = p[answers]?.let { runCatching { TravelJson.decodeFromString(answersSerializer, it) }.getOrNull() }.orEmpty(),
                completedAtEpochMillis = p[completedAt],
            )
        }.distinctUntilChanged()

    override suspend fun save(profile: TravellerProfile) {
        store.edit { p ->
            p[completed] = profile.onboardingCompleted
            p.setOrRemove(primary, profile.primaryType)
            p[secondary] = profile.secondaryTypes.joinToString(",")
            p[answers] = TravelJson.encodeToString(answersSerializer, profile.answers)
            val at = profile.completedAtEpochMillis
            if (at != null) p[completedAt] = at else p.remove(completedAt)
        }
    }

    override suspend fun clear() {
        store.edit { it.clear() }
    }
}

class PreferencesFeatureOverridesStore(private val store: DataStore<Preferences>) : FeatureOverridesStore {
    private val enabled = stringSetPreferencesKey("enabled")
    private val disabled = stringSetPreferencesKey("disabled")

    override val overrides: Flow<FeatureOverrides> =
        store.safeData()
            .map { FeatureOverrides(it[enabled].orEmpty(), it[disabled].orEmpty()) }
            .distinctUntilChanged()

    override suspend fun set(featureId: String, enabled: Boolean?) {
        store.edit { p ->
            val on = p[this.enabled].orEmpty() - featureId
            val off = p[disabled].orEmpty() - featureId
            p[this.enabled] = if (enabled == true) on + featureId else on
            p[disabled] = if (enabled == false) off + featureId else off
        }
    }

    override suspend fun clear() {
        store.edit { it.clear() }
    }
}

private suspend fun DataStore<Preferences>.editKey(key: Preferences.Key<String>, value: String?) {
    edit { it.setOrRemove(key, value) }
}

private fun MutablePreferences.setOrRemove(key: Preferences.Key<String>, value: String?) {
    if (value.isNullOrBlank()) remove(key) else this[key] = value
}
