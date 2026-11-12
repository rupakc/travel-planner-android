package io.github.rupakc.travelplanner.core.network.api

import io.github.rupakc.travelplanner.core.model.notifications.NotificationCatalog
import io.github.rupakc.travelplanner.core.model.notifications.NotificationSettings
import io.github.rupakc.travelplanner.core.model.notifications.NotificationSettingsUpdate
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Query

/** Notification categories, quiet hours and daily cap. Only called when `features.notifications_api >= 1` (older backends answer 404). */
interface NotificationSettingsApi {
    @GET("api/notification-settings")
    suspend fun settings(): NotificationSettings

    @PUT("api/notification-settings")
    suspend fun update(
        @Body body: NotificationSettingsUpdate,
    ): NotificationSettings
}

/** The shared text catalog; an unknown locale is answered in English (the response's `locale` says which). */
interface NotificationCatalogApi {
    @GET("api/notifications/catalog")
    suspend fun catalog(
        @Query("locale") locale: String,
    ): NotificationCatalog
}
