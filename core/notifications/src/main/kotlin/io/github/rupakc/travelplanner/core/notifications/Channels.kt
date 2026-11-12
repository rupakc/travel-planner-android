package io.github.rupakc.travelplanner.core.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.content.getSystemService
import io.github.rupakc.travelplanner.core.model.notifications.NotificationCategory

/** Notification channels (spec §7). Ids are stable: never rename them. */
enum class TravelChannel(val id: String, internal val nameRes: Int, internal val descRes: Int, internal val importance: Int) {
    TRIP_REMINDERS(
        "trip_reminders",
        R.string.channel_trip_reminders,
        R.string.channel_trip_reminders_desc,
        NotificationManager.IMPORTANCE_DEFAULT,
    ),
    PRICE_ALERTS("price_alerts", R.string.channel_price_alerts, R.string.channel_price_alerts_desc, NotificationManager.IMPORTANCE_HIGH),
    NEARBY("nearby_suggestions", R.string.channel_nearby, R.string.channel_nearby_desc, NotificationManager.IMPORTANCE_LOW),
    ACCOUNT("account", R.string.channel_account, R.string.channel_account_desc, NotificationManager.IMPORTANCE_DEFAULT),
    MORNING_BRIEF("morning_brief", R.string.channel_morning_brief, R.string.channel_morning_brief_desc, NotificationManager.IMPORTANCE_DEFAULT),
    WEATHER("weather", R.string.channel_weather, R.string.channel_weather_desc, NotificationManager.IMPORTANCE_DEFAULT),
    SOCIAL("social", R.string.channel_social, R.string.channel_social_desc, NotificationManager.IMPORTANCE_LOW),
    QUIZ("quiz", R.string.channel_quiz, R.string.channel_quiz_desc, NotificationManager.IMPORTANCE_LOW),
    ;

    companion object {
        /**
         * The channel for a notification [category] (R8): one channel per known category, the price-alert one for the
         * categories that are price drops, and the default (account) channel for a category this app does not know.
         * `nearby` is on-device only and keeps its own channel.
         */
        fun forCategory(category: NotificationCategory?): TravelChannel =
            when (category) {
                NotificationCategory.PRICE_DROPS -> PRICE_ALERTS
                NotificationCategory.TRIP_REMINDERS -> TRIP_REMINDERS
                NotificationCategory.MORNING_BRIEF -> MORNING_BRIEF
                NotificationCategory.WEATHER -> WEATHER
                NotificationCategory.NEARBY -> NEARBY
                NotificationCategory.SOCIAL -> SOCIAL
                NotificationCategory.QUIZ -> QUIZ
                null -> ACCOUNT
            }
    }
}

object NotificationChannels {
    /** Idempotent; call from Application.onCreate (cheap, no IO). */
    fun createAll(context: Context) {
        val manager = context.getSystemService<NotificationManager>() ?: return
        manager.createNotificationChannels(
            TravelChannel.entries.map { channel ->
                NotificationChannel(channel.id, context.getString(channel.nameRes), channel.importance).apply {
                    description = context.getString(channel.descRes)
                }
            },
        )
    }
}
