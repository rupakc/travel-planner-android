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
import io.github.rupakc.travelplanner.core.model.auth.User
import io.github.rupakc.travelplanner.core.model.json.TravelJson
import io.github.rupakc.travelplanner.core.model.nearby.LocationCell
import io.github.rupakc.travelplanner.core.model.settings.AppSettings
import io.github.rupakc.travelplanner.core.model.settings.FeatureOverrides
import io.github.rupakc.travelplanner.core.model.settings.Session
import io.github.rupakc.travelplanner.core.model.settings.ThemeMode
import io.github.rupakc.travelplanner.core.model.settings.TravellerProfile
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

internal fun DataStore<Preferences>.safeData(): Flow<Preferences> =
    data.catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }

class PreferencesSettingsStore(private val store: DataStore<Preferences>) : SettingsStore {
    private val baseUrl = stringPreferencesKey("base_url")
    private val theme = stringPreferencesKey("theme_mode")
    private val dynamic = booleanPreferencesKey("dynamic_color")
    private val locale = stringPreferencesKey("locale_tag")
    private val trip = booleanPreferencesKey("notify_trip")
    private val price = booleanPreferencesKey("notify_price")
    private val nearby = booleanPreferencesKey("notify_nearby")
    private val nearbyAlways = booleanPreferencesKey("notify_nearby_always")

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
                nearbyNotificationsAlways = p[nearbyAlways] ?: false,
            )
        }.distinctUntilChanged()

    override suspend fun current(): AppSettings = settings.first()

    override suspend fun setBaseUrlOverride(url: String?) = store.editKey(baseUrl, url?.trim()?.trimEnd('/'))

    override suspend fun setThemeMode(mode: ThemeMode) = store.editKey(theme, mode.name)

    override suspend fun setDynamicColor(enabled: Boolean) {
        store.edit { it[dynamic] = enabled }
    }

    override suspend fun setLocaleTag(tag: String?) = store.editKey(locale, tag)

    override suspend fun setNotificationToggles(trip: Boolean?, priceAlerts: Boolean?, nearby: Boolean?, nearbyAlways: Boolean?) {
        store.edit { p ->
            trip?.let { p[this.trip] = it }
            priceAlerts?.let { p[price] = it }
            nearby?.let { p[this.nearby] = it }
            nearbyAlways?.let { p[this.nearbyAlways] = it }
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

    override suspend fun replace(overrides: FeatureOverrides) {
        store.edit { p ->
            p[enabled] = overrides.enabled
            p[disabled] = overrides.disabled - overrides.enabled
        }
    }
}

private suspend fun DataStore<Preferences>.editKey(key: Preferences.Key<String>, value: String?) {
    edit { it.setOrRemove(key, value) }
}

private fun MutablePreferences.setOrRemove(key: Preferences.Key<String>, value: String?) {
    if (value.isNullOrBlank()) remove(key) else this[key] = value
}

class PreferencesLastLocationStore(private val store: DataStore<Preferences>) : LastLocationStore {
    private val lat = stringPreferencesKey("lat")
    private val lng = stringPreferencesKey("lng")
    private val at = longPreferencesKey("at")

    override suspend fun get(): LocationCell? {
        val p = store.safeData().first()
        val la = p[lat]?.toDoubleOrNull() ?: return null
        val ln = p[lng]?.toDoubleOrNull() ?: return null
        return LocationCell(la, ln, p[at] ?: return null)
    }

    override suspend fun set(cell: LocationCell) {
        store.edit {
            it[lat] = cell.lat.toString()
            it[lng] = cell.lng.toString()
            it[at] = cell.atEpochMillis
        }
    }

    override suspend fun clear() {
        store.edit { it.clear() }
    }
}
