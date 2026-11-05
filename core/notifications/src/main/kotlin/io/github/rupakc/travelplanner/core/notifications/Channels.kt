package io.github.rupakc.travelplanner.core.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.content.getSystemService

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
