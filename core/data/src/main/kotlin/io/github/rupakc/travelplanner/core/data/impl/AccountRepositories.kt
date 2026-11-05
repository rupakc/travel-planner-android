package io.github.rupakc.travelplanner.core.data.impl

import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.common.map
import io.github.rupakc.travelplanner.core.common.onSuccess
import io.github.rupakc.travelplanner.core.data.repository.FeedbackRepository
import io.github.rupakc.travelplanner.core.data.repository.PreferencesRepository
import io.github.rupakc.travelplanner.core.data.repository.ProfileRepository
import io.github.rupakc.travelplanner.core.data.repository.RecentSearchesRepository
import io.github.rupakc.travelplanner.core.data.repository.SettingsRepository
import io.github.rupakc.travelplanner.core.database.RecentSearchDao
import io.github.rupakc.travelplanner.core.database.RecentSearchEntity
import io.github.rupakc.travelplanner.core.datastore.FeatureOverridesStore
import io.github.rupakc.travelplanner.core.datastore.SessionStore
import io.github.rupakc.travelplanner.core.datastore.SettingsStore
import io.github.rupakc.travelplanner.core.datastore.TravellerProfileStore
import io.github.rupakc.travelplanner.core.model.feedback.AnalyticsBatch
import io.github.rupakc.travelplanner.core.model.feedback.AnalyticsEvent
import io.github.rupakc.travelplanner.core.model.feedback.FeedbackRequest
import io.github.rupakc.travelplanner.core.model.json.TravelJson
import io.github.rupakc.travelplanner.core.model.preferences.Preferences
import io.github.rupakc.travelplanner.core.model.preferences.RecentSearch
import io.github.rupakc.travelplanner.core.model.preferences.RecentSearchIn
import io.github.rupakc.travelplanner.core.model.preferences.TasteProfile
import io.github.rupakc.travelplanner.core.model.settings.AppSettings
import io.github.rupakc.travelplanner.core.model.settings.FeatureOverrides
import io.github.rupakc.travelplanner.core.model.settings.ThemeMode
import io.github.rupakc.travelplanner.core.model.settings.TravellerProfile
import io.github.rupakc.travelplanner.core.network.BaseUrlProvider
import io.github.rupakc.travelplanner.core.network.BaseUrlValidation
import io.github.rupakc.travelplanner.core.network.api.FeedbackApi
import io.github.rupakc.travelplanner.core.network.api.PreferencesApi
import io.github.rupakc.travelplanner.core.network.apiCall
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

@Singleton
class PreferencesRepositoryImpl
    @Inject
    constructor(private val api: PreferencesApi) : PreferencesRepository {
        override suspend fun get(): AppResult<Preferences> = apiCall { api.get() }

        override suspend fun save(preferences: Preferences): AppResult<Preferences> = apiCall { api.put(preferences) }

        override suspend fun update(transform: (Preferences) -> Preferences): AppResult<Preferences> =
            when (val current = get()) {
                is AppResult.Success -> save(transform(current.value))
                is AppResult.Failure -> current
            }

        override suspend fun tasteProfile(): AppResult<TasteProfile> = apiCall { api.tasteProfile() }

        override suspend fun resetTasteProfile(): AppResult<Int> = apiCall { api.resetTasteProfile() }.map { it.deletedSignals ?: 0 }
    }

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class RecentSearchesRepositoryImpl
    @Inject
    constructor(
        private val api: PreferencesApi,
        private val dao: RecentSearchDao,
        private val sessionStore: SessionStore,
    ) : RecentSearchesRepository {
        override val recent: Flow<List<RecentSearch>> =
            sessionStore.session.flatMapLatest { session ->
                if (session == null) {
                    flowOf(emptyList())
                } else {
                    dao.observe(session.user.username).map { rows ->
                        rows.mapNotNull { runCatching { TravelJson.decodeFromString(RecentSearch.serializer(), it.json) }.getOrNull() }
                    }
                }
            }

        override suspend fun refresh(): AppResult<Unit> =
            apiCall { api.recentSearches() }
                .onSuccess { list ->
                    val username = sessionStore.current()?.user?.username ?: return@onSuccess
                    dao.replaceAll(username, list.map { it.toEntity(username) })
                }.map { }

        override suspend fun add(search: RecentSearchIn): AppResult<RecentSearch> =
            apiCall { api.addRecentSearch(search) }
                .onSuccess { item -> sessionStore.current()?.user?.username?.let { dao.upsert(item.toEntity(it)) } }

        override suspend fun delete(id: String): AppResult<Unit> =
            apiCall {
                val response = api.deleteRecentSearch(id)
                if (!response.isSuccessful && response.code() != NOT_FOUND) throw retrofit2.HttpException(response)
            }.onSuccess { dao.delete(id) }

        private fun RecentSearch.toEntity(username: String) =
            RecentSearchEntity(id, username, TravelJson.encodeToString(RecentSearch.serializer(), this), lastUsedAt.orEmpty())

        private companion object {
            const val NOT_FOUND = 404
        }
    }

@Singleton
class SettingsRepositoryImpl
    @Inject
    constructor(
        private val store: SettingsStore,
        private val baseUrlProvider: BaseUrlProvider,
    ) : SettingsRepository {
        override val settings: Flow<AppSettings> = store.settings

        override val effectiveBaseUrl: Flow<String> =
            store.settings.map { baseUrlProvider.resolve(it.baseUrlOverride)?.toString()?.trimEnd('/').orEmpty() }

        override suspend fun setBaseUrl(url: String?): AppResult<Unit> {
            if (url.isNullOrBlank()) {
                store.setBaseUrlOverride(null)
                return AppResult.Success(Unit)
            }
            return when (val v = baseUrlProvider.validate(url)) {
                is BaseUrlValidation.Valid -> {
                    store.setBaseUrlOverride(v.normalized)
                    AppResult.Success(Unit)
                }

                BaseUrlValidation.Invalid -> {
                    AppResult.Failure(AppError.Client(status = 0, message = null, code = CODE_INVALID_URL))
                }

                BaseUrlValidation.HttpsRequired -> {
                    AppResult.Failure(AppError.Client(status = 0, message = null, code = CODE_HTTPS_REQUIRED))
                }
            }
        }

        override suspend fun setThemeMode(mode: ThemeMode) = store.setThemeMode(mode)

        override suspend fun setDynamicColor(enabled: Boolean) = store.setDynamicColor(enabled)

        override suspend fun setLocaleTag(tag: String?) = store.setLocaleTag(tag)

        override suspend fun setNotificationToggles(trip: Boolean?, priceAlerts: Boolean?, nearby: Boolean?) =
            store.setNotificationToggles(trip, priceAlerts, nearby)

        companion object {
            /** AppError.Client.code values from [setBaseUrl]. */
            const val CODE_INVALID_URL = "invalid_url"
            const val CODE_HTTPS_REQUIRED = "https_required"
        }
    }

@Singleton
class ProfileRepositoryImpl
    @Inject
    constructor(
        private val profileStore: TravellerProfileStore,
        private val overridesStore: FeatureOverridesStore,
    ) : ProfileRepository {
        override val profile: Flow<TravellerProfile> = profileStore.profile
        override val featureOverrides: Flow<FeatureOverrides> = overridesStore.overrides

        override suspend fun saveProfile(profile: TravellerProfile) = profileStore.save(profile)

        override suspend fun setFeature(featureId: String, enabled: Boolean?) = overridesStore.set(featureId, enabled)

        override suspend fun resetFeatures() = overridesStore.clear()

        override suspend fun setFeatureOverrides(overrides: FeatureOverrides) = overridesStore.replace(overrides)
    }

@Singleton
class FeedbackRepositoryImpl
    @Inject
    constructor(private val api: FeedbackApi) : FeedbackRepository {
        override suspend fun sendFeedback(request: FeedbackRequest): AppResult<Unit> = apiCall { api.feedback(request) }.map { }

        override suspend fun sendAnalytics(events: List<AnalyticsEvent>): AppResult<Unit> =
            apiCall { api.analytics(AnalyticsBatch(events.take(MAX_EVENTS))) }.map { }

        private companion object {
            const val MAX_EVENTS = 50
        }
    }
