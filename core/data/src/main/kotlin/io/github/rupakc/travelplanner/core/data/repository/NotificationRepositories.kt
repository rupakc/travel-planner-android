package io.github.rupakc.travelplanner.core.data.repository

import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.model.notifications.NotificationCatalog
import io.github.rupakc.travelplanner.core.model.notifications.NotificationSettings
import io.github.rupakc.travelplanner.core.model.notifications.NotificationSettingsUpdate

/**
 * Server-side notification categories, quiet hours and daily cap. Callers must have checked
 * `NotificationsFlags.apiAvailable`. Failures carry the server's `error_code`; read it with `AppError.notificationsErrorCode()`.
 */
interface NotificationSettingsRepository {
    /** `GET /notification-settings`. */
    suspend fun settings(): AppResult<NotificationSettings>

    /** `PUT /notification-settings` with any subset; answers the full settings. `422 notification_settings_invalid`. */
    suspend fun update(update: NotificationSettingsUpdate): AppResult<NotificationSettings>
}

/** The shared notification text catalog. */
interface NotificationCatalogRepository {
    /**
     * The templates for [localeTag] (any BCP-47 tag; an unsupported one is English). Never fails: it answers from the
     * per-locale cache, else the server, else the bundled English table. Server keys are laid over the bundled English
     * ones, so every known key is always present. Only a server answer is cached; a failure is retried on the next call.
     */
    suspend fun catalog(localeTag: String?): NotificationCatalog
}
