package io.github.rupakc.travelplanner.core.data.impl

import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.data.repository.NotificationCatalogRepository
import io.github.rupakc.travelplanner.core.data.repository.NotificationSettingsRepository
import io.github.rupakc.travelplanner.core.domain.notifications.NotificationText
import io.github.rupakc.travelplanner.core.model.notifications.NotificationCatalog
import io.github.rupakc.travelplanner.core.model.notifications.NotificationSettings
import io.github.rupakc.travelplanner.core.model.notifications.NotificationSettingsUpdate
import io.github.rupakc.travelplanner.core.network.api.NotificationCatalogApi
import io.github.rupakc.travelplanner.core.network.api.NotificationSettingsApi
import io.github.rupakc.travelplanner.core.network.apiCall
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationSettingsRepositoryImpl
    @Inject
    constructor(private val api: NotificationSettingsApi) : NotificationSettingsRepository {
        override suspend fun settings(): AppResult<NotificationSettings> = apiCall { api.settings() }

        override suspend fun update(update: NotificationSettingsUpdate): AppResult<NotificationSettings> = apiCall { api.update(update) }
    }

/** In-memory per-locale cache over `GET /notifications/catalog`, falling back to the bundled English table. */
@Singleton
class NotificationCatalogRepositoryImpl
    @Inject
    constructor(private val api: NotificationCatalogApi) : NotificationCatalogRepository {
        private val cache = ConcurrentHashMap<String, NotificationCatalog>()

        override suspend fun catalog(localeTag: String?): NotificationCatalog {
            val locale = NotificationText.catalogLocale(localeTag)
            cache[locale]?.let { return it }
            return when (val result = apiCall { api.catalog(locale) }) {
                is AppResult.Success -> {
                    NotificationCatalog(
                        locale = result.value.locale,
                        templates = NotificationText.BUNDLED_EN + result.value.templates,
                    ).also { cache[locale] = it }
                }

                is AppResult.Failure -> {
                    BUNDLED
                }
            }
        }

        private companion object {
            val BUNDLED = NotificationCatalog(NotificationText.DEFAULT_LOCALE, NotificationText.BUNDLED_EN)
        }
    }
