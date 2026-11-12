package io.github.rupakc.travelplanner.core.testing.fake

import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.data.repository.NotificationCatalogRepository
import io.github.rupakc.travelplanner.core.data.repository.NotificationSettingsRepository
import io.github.rupakc.travelplanner.core.domain.notifications.NotificationText
import io.github.rupakc.travelplanner.core.model.notifications.CategorySetting
import io.github.rupakc.travelplanner.core.model.notifications.NotificationCatalog
import io.github.rupakc.travelplanner.core.model.notifications.NotificationSettings
import io.github.rupakc.travelplanner.core.model.notifications.NotificationSettingsUpdate

/**
 * Scriptable [NotificationSettingsRepository]. By default it behaves like the server: an update is merged into [current]
 * and answered in full. Set [updateResult] to force one failure or answer; [settingsResult] overrides the GET.
 */
class FakeNotificationSettingsRepository(
    var current: NotificationSettings = NotificationSettings(),
) : NotificationSettingsRepository {
    var settingsResult: AppResult<NotificationSettings>? = null
    var updateResult: AppResult<NotificationSettings>? = null
    var settingsCalls = 0
    val updates = mutableListOf<NotificationSettingsUpdate>()

    override suspend fun settings(): AppResult<NotificationSettings> {
        settingsCalls++
        return settingsResult ?: AppResult.Success(current)
    }

    override suspend fun update(update: NotificationSettingsUpdate): AppResult<NotificationSettings> {
        updates += update
        updateResult?.let { return it }
        current =
            current.copy(
                categories = current.categories + (update.categories ?: emptyMap<String, CategorySetting>()),
                quietHours = update.quietHours ?: current.quietHours,
                dailyCap = update.dailyCap ?: current.dailyCap,
            )
        return AppResult.Success(current)
    }
}

/** [NotificationCatalogRepository] answering the bundled English table (or [templates]); records the requested tags. */
class FakeNotificationCatalogRepository(
    var templates: Map<String, String> = NotificationText.BUNDLED_EN,
) : NotificationCatalogRepository {
    val requests = mutableListOf<String?>()

    override suspend fun catalog(localeTag: String?): NotificationCatalog {
        requests += localeTag
        return NotificationCatalog(NotificationText.catalogLocale(localeTag), templates)
    }
}
