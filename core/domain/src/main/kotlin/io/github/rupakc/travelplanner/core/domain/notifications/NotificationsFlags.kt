package io.github.rupakc.travelplanner.core.domain.notifications

import io.github.rupakc.travelplanner.core.model.config.ClientConfigDto

/**
 * Notifications 2.0 gates from `GET /config` `features`. Everything is off for an older backend or a missing config.
 *
 * - [apiAvailable]: `/notification-settings` and the catalog exist (`notifications_api >= 1`); the settings UI hides without it.
 * - [v2Active]: the server applies categories, trip pushes and platform routing (`notifications_v2`).
 * - [apns]: an Apple key is configured (informational; the Android app delivers through FCM).
 */
data class NotificationsFlags(
    val notificationsApi: Int = 0,
    val v2Active: Boolean = false,
    val apns: Boolean = false,
) {
    val apiAvailable: Boolean get() = notificationsApi >= API_V1

    companion object {
        private const val API_V1 = 1

        /** Everything off. */
        val OFF = NotificationsFlags()

        /** Derives the gates from [config]; null means everything off. */
        operator fun invoke(config: ClientConfigDto?): NotificationsFlags {
            val f = config?.features ?: return OFF
            return NotificationsFlags(notificationsApi = f.notificationsApi ?: 0, v2Active = f.notificationsV2, apns = f.apns)
        }
    }
}
