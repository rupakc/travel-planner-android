package io.github.rupakc.travelplanner.core.model.notifications

import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.model.alerts.QuietHours
import io.github.rupakc.travelplanner.core.model.json.LenientInt
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Notifications API (docs/Notifications-API.md of the web repo, frozen). Category names are kept as strings on the wire
// (the server may add some); [NotificationCategory] is the set this app knows.

/** The notification categories of `GET /notification-settings`. [NEARBY] is honoured on the device; the server never sends it. */
enum class NotificationCategory(val wire: String) {
    PRICE_DROPS("price_drops"),
    TRIP_REMINDERS("trip_reminders"),
    MORNING_BRIEF("morning_brief"),
    WEATHER("weather"),
    NEARBY("nearby"),
    SOCIAL("social"),
    QUIZ("quiz"),
    ;

    companion object {
        /** The category for [wire], or null when this app does not know it. */
        fun from(wire: String?): NotificationCategory? = entries.firstOrNull { it.wire == wire }
    }
}

/** One category's switch. */
@Serializable
data class CategorySetting(val enabled: Boolean = true)

/** `push` of the settings: whether the server can deliver to that platform. */
@Serializable
data class PushStatus(val fcm: Boolean = false, val apns: Boolean = false)

/**
 * `GET|PUT /notification-settings`. [timezone], [quietHoursActive], [push] and [devices] are read-only. A category the
 * server omits counts as on (the default).
 */
@Serializable
data class NotificationSettings(
    val categories: Map<String, CategorySetting> = emptyMap(),
    /** The same value as `AlertPrefs.quiet_hours`: a change through either route shows in both. */
    @SerialName("quiet_hours") val quietHours: QuietHours? = null,
    /** 1..10 non-price pushes per local day. */
    @SerialName("daily_cap") val dailyCap: LenientInt = null,
    val timezone: String? = null,
    @SerialName("quiet_hours_active") val quietHoursActive: Boolean = false,
    val push: PushStatus = PushStatus(),
    val devices: LenientInt = null,
) {
    /** Whether [category] is on (default on). */
    fun isEnabled(category: NotificationCategory): Boolean = categories[category.wire]?.enabled ?: true
}

/** `PUT /notification-settings` body: any subset; null fields are omitted. */
@Serializable
data class NotificationSettingsUpdate(
    val categories: Map<String, CategorySetting>? = null,
    @SerialName("quiet_hours") val quietHours: QuietHours? = null,
    @SerialName("daily_cap") val dailyCap: Int? = null,
) {
    companion object {
        /** An update that switches one category. */
        fun category(category: NotificationCategory, enabled: Boolean) =
            NotificationSettingsUpdate(categories = mapOf(category.wire to CategorySetting(enabled)))
    }
}

/** `GET /notifications/catalog`: the localized text templates with `{name}` placeholders. [locale] is the one actually served. */
@Serializable
data class NotificationCatalog(
    val locale: String = "en",
    val templates: Map<String, String> = emptyMap(),
)

/** Machine codes of the notification routes. */
enum class NotificationsErrorCode(val wire: String?) {
    NOTIFICATION_SETTINGS_INVALID("notification_settings_invalid"),
    UNKNOWN(null),
    ;

    companion object {
        /** Maps an `error_code`; null or anything new is [UNKNOWN] (show the backend `detail`). */
        fun from(code: String?): NotificationsErrorCode = entries.firstOrNull { it.wire != null && it.wire == code } ?: UNKNOWN
    }
}

/** The notification-route machine code of this failure ([NotificationsErrorCode.UNKNOWN] when it is none of ours). */
fun AppError.notificationsErrorCode(): NotificationsErrorCode =
    NotificationsErrorCode.from(
        when (this) {
            is AppError.NotFound -> code
            is AppError.Client -> code
            is AppError.Server -> code
            is AppError.Forbidden -> code
            is AppError.RateLimited -> code
            else -> null
        },
    )
