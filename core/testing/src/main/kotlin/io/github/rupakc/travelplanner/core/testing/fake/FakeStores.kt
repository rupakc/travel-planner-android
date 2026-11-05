package io.github.rupakc.travelplanner.core.testing.fake

import io.github.rupakc.travelplanner.core.datastore.FeatureOverridesStore
import io.github.rupakc.travelplanner.core.datastore.SessionStore
import io.github.rupakc.travelplanner.core.datastore.SettingsStore
import io.github.rupakc.travelplanner.core.datastore.TravellerProfileStore
import io.github.rupakc.travelplanner.core.model.auth.User
import io.github.rupakc.travelplanner.core.model.settings.AppSettings
import io.github.rupakc.travelplanner.core.model.settings.FeatureOverrides
import io.github.rupakc.travelplanner.core.model.settings.Session
import io.github.rupakc.travelplanner.core.model.settings.ThemeMode
import io.github.rupakc.travelplanner.core.model.settings.TravellerProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** In-memory stores for core-layer tests (features use the repository fakes instead). */
class FakeSessionStore(initial: Session? = null) : SessionStore {
    val state = MutableStateFlow(initial)
    override val session: Flow<Session?> = state

    /** Like the real store: survives [clear]. */
    var lastUser: String? = initial?.user?.username

    override suspend fun current(): Session? = state.value

    /** Like the real store: survives [clear]. */
    var lastServerUrl: String? = initial?.baseUrl

    override suspend fun save(session: Session) {
        state.value = session
        lastUser = session.user.username
        session.baseUrl?.let { lastServerUrl = it }
    }

    override suspend fun lastUsername(): String? = lastUser

    override suspend fun lastServer(): String? = lastServerUrl

    override suspend fun updateToken(token: String) = state.update { it?.copy(token = token) }

    override suspend fun updateUser(user: User) = state.update { it?.copy(user = user) }

    override suspend fun clear() {
        state.value = null
    }
}

class FakeSettingsStore(initial: AppSettings = AppSettings()) : SettingsStore {
    val state = MutableStateFlow(initial)
    override val settings: Flow<AppSettings> = state

    override suspend fun current(): AppSettings = state.value

    override suspend fun setBaseUrlOverride(url: String?) = state.update { it.copy(baseUrlOverride = url) }

    override suspend fun setThemeMode(mode: ThemeMode) = state.update { it.copy(themeMode = mode) }

    override suspend fun setDynamicColor(enabled: Boolean) = state.update { it.copy(dynamicColor = enabled) }

    override suspend fun setLocaleTag(tag: String?) = state.update { it.copy(localeTag = tag) }

    override suspend fun setNotificationToggles(trip: Boolean?, priceAlerts: Boolean?, nearby: Boolean?) =
        state.update {
            it.copy(
                tripNotifications = trip ?: it.tripNotifications,
                priceAlertNotifications = priceAlerts ?: it.priceAlertNotifications,
                nearbyNotifications = nearby ?: it.nearbyNotifications,
            )
        }
}

class FakeTravellerProfileStore : TravellerProfileStore {
    val state = MutableStateFlow(TravellerProfile())
    override val profile: Flow<TravellerProfile> = state

    override suspend fun save(profile: TravellerProfile) {
        state.value = profile
    }

    override suspend fun clear() {
        state.value = TravellerProfile()
    }
}

class FakeFeatureOverridesStore : FeatureOverridesStore {
    val state = MutableStateFlow(FeatureOverrides())
    override val overrides: Flow<FeatureOverrides> = state

    override suspend fun set(featureId: String, enabled: Boolean?) =
        state.update { o ->
            val on = o.enabled - featureId
            val off = o.disabled - featureId
            FeatureOverrides(if (enabled == true) on + featureId else on, if (enabled == false) off + featureId else off)
        }

    override suspend fun clear() {
        state.value = FeatureOverrides()
    }

    override suspend fun replace(overrides: FeatureOverrides) {
        state.value = FeatureOverrides(overrides.enabled, overrides.disabled - overrides.enabled)
    }
}
