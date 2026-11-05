package io.github.rupakc.travelplanner.core.datastore

import io.github.rupakc.travelplanner.core.model.auth.User
import io.github.rupakc.travelplanner.core.model.nearby.LocationCell
import io.github.rupakc.travelplanner.core.model.settings.AppSettings
import io.github.rupakc.travelplanner.core.model.settings.FeatureOverrides
import io.github.rupakc.travelplanner.core.model.settings.Session
import io.github.rupakc.travelplanner.core.model.settings.ThemeMode
import io.github.rupakc.travelplanner.core.model.settings.TravellerProfile
import kotlinx.coroutines.flow.Flow

/** Signed-in session (bearer token + user). Cleared on logout and on any 401. */
interface SessionStore {
    val session: Flow<Session?>

    suspend fun current(): Session?

    suspend fun save(session: Session)

    suspend fun updateToken(token: String)

    suspend fun updateUser(user: User)

    suspend fun clear()

    /**
     * Username of the last signed-in user on this device; survives [clear] so the app can tell a
     * different user is signing in and wipe the previous user's local data (review I6).
     */
    suspend fun lastUsername(): String? = null

    /** Base URL of the server the last session belonged to (survives [clear]); see review R2-I2. */
    suspend fun lastServer(): String? = null
}

/** Device-only settings: server URL, theme, dynamic colour, locale, notification toggles. */
interface SettingsStore {
    val settings: Flow<AppSettings>

    suspend fun current(): AppSettings

    suspend fun setBaseUrlOverride(url: String?)

    suspend fun setThemeMode(mode: ThemeMode)

    suspend fun setDynamicColor(enabled: Boolean)

    suspend fun setLocaleTag(tag: String?)

    suspend fun setNotificationToggles(trip: Boolean? = null, priceAlerts: Boolean? = null, nearby: Boolean? = null, nearbyAlways: Boolean? = null)
}

/** Onboarding result (device-local, spec §6.3). */
interface TravellerProfileStore {
    val profile: Flow<TravellerProfile>

    suspend fun save(profile: TravellerProfile)

    suspend fun clear()
}

/** Feature visibility overrides (device-local, spec §6.3). */
interface FeatureOverridesStore {
    val overrides: Flow<FeatureOverrides>

    /** true = force on, false = force off, null = back to the traveller-type default. */
    suspend fun set(featureId: String, enabled: Boolean?)

    suspend fun clear()

    /** Replaces both sets atomically (one write). */
    suspend fun replace(overrides: FeatureOverrides) {
        clear()
        overrides.enabled.forEach { set(it, true) }
        overrides.disabled.forEach { set(it, false) }
    }
}

/** Last coarse foreground fix (Near me), for background nearby suggestions. Cleared on sign-out. */
interface LastLocationStore {
    suspend fun get(): LocationCell?

    suspend fun set(cell: LocationCell)

    suspend fun clear()
}
