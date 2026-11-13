package io.github.rupakc.travelplanner.core.notifications

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.rupakc.travelplanner.core.domain.alerts.AlertLink
import io.github.rupakc.travelplanner.core.domain.alerts.NotificationTarget
import io.github.rupakc.travelplanner.core.domain.alerts.notificationLink
import io.github.rupakc.travelplanner.core.model.alerts.AppNotification
import io.github.rupakc.travelplanner.core.model.navigation.DeepLinks
import io.github.rupakc.travelplanner.core.model.notifications.NotificationCategory
import javax.inject.Inject
import javax.inject.Singleton

/** A button on a notification: [label] (resolved text) opens [deepLink]. */
data class NotificationAction(val label: String, val deepLink: String)

/** Posts local notifications. Every post opens [deepLink] (a `travelplanner://` URI) in MainActivity. */
interface Notifier {
    /** False when POST_NOTIFICATIONS is not granted (Android 13+) or notifications are blocked. */
    fun canPost(): Boolean

    fun post(
        channel: TravelChannel,
        id: Int,
        title: String,
        body: String,
        deepLink: String,
        actions: List<NotificationAction> = emptyList(),
        /** Ended/paused notices: no sound or heads-up (web parity: lower priority than drops). */
        quiet: Boolean = false,
    )

    /** A backend price alert (from AlertPollWorker or FCM); deep-links to Alerts like the web `/alerts?kind&watch`. */
    fun postPriceAlert(notification: AppNotification)

    fun cancel(id: Int)
}

/** Notification ids per source, so a trip reminder never replaces a price alert. */
object NotificationIds {
    private const val PRICE_ALERT_BASE = 100_000L
    private const val PRICE_ALERT_RANGE = 1_000_000L
    private const val TRIP_REMINDER_BASE = 2_000_000
    private const val TRIP_REMINDER_RANGE = 1_000_000
    const val NEARBY = 3_000_001
    private const val GROUP_BASE = 4_000_000

    /** One slot per category for a grouped push, so the next group replaces the last instead of stacking. */
    fun group(category: NotificationCategory?): Int = GROUP_BASE + (category?.ordinal ?: NotificationCategory.entries.size)

    fun priceAlert(notificationId: Long): Int = (PRICE_ALERT_BASE + Math.floorMod(notificationId, PRICE_ALERT_RANGE)).toInt()

    fun tripReminder(uniqueName: String): Int = TRIP_REMINDER_BASE + Math.floorMod(uniqueName.hashCode(), TRIP_REMINDER_RANGE)
}

/** `travelplanner://alerts?kind=…&watch=…` for an alert link (web `/alerts?kind=&watch=`). */
fun AlertLink.toDeepLink(): String =
    buildString {
        append("${DeepLinks.SCHEME}://alerts")
        val params = listOfNotNull(kind?.let { "kind=$it" }, watchId?.let { "watch=$it" })
        if (params.isNotEmpty()) append("?").append(params.joinToString("&"))
    }

/** Deep links built by notifications (patterns in [DeepLinks]). */
object NotificationLinks {
    fun plan(planId: Long): String = "${DeepLinks.SCHEME}://plan/$planId"

    /** The trip's Today tab (`travelplanner://trip/{id}?tab=today`). */
    fun tripToday(planId: Long): String = "${DeepLinks.SCHEME}://trip/$planId?tab=today"

    /** The notification list. */
    val INBOX: String = "${DeepLinks.SCHEME}://notifications"

    /** The ideas feed (`feature:social`). */
    val FEED: String = DeepLinks.FEED_PATTERN

    /** Query parameter a push deep link carries so the tap can be counted (`notif_open`); see [PushTaps]. */
    const val OPEN_CATEGORY_PARAM = "notif_cat"

    /** The link for a resolved [target], tagged with the [category] the tap reports (null = untagged). */
    fun of(target: NotificationTarget, category: NotificationCategory? = null): String {
        val base =
            when (target) {
                is NotificationTarget.Alert -> target.link.toDeepLink()

                is NotificationTarget.TripToday -> tripToday(target.planId)

                NotificationTarget.Feed -> FEED

                // The match screens land with T5.5; until then the list is the safe destination (no dead end).
                is NotificationTarget.H2h, NotificationTarget.Inbox -> INBOX
            }
        if (category == null) return base
        return base + (if ('?' in base) "&" else "?") + "$OPEN_CATEGORY_PARAM=${category.wire}"
    }

    val NEARBY: String = "${DeepLinks.SCHEME}://nearby"
}

@Singleton
class SystemNotifier
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : Notifier {
        private val manager = NotificationManagerCompat.from(context)

        override fun canPost(): Boolean {
            val permitted =
                Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            return permitted && manager.areNotificationsEnabled()
        }

        private fun open(requestCode: Int, deepLink: String): PendingIntent {
            val intent =
                Intent(Intent.ACTION_VIEW, deepLink.toUri()).setPackage(context.packageName)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            return PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }

        override fun post(
            channel: TravelChannel,
            id: Int,
            title: String,
            body: String,
            deepLink: String,
            actions: List<NotificationAction>,
            quiet: Boolean,
        ) {
            if (!canPost()) return
            val builder =
                NotificationCompat.Builder(context, channel.id)
                    .setSmallIcon(R.drawable.ic_stat_voyager)
                    .setContentTitle(title)
                    .setContentText(body)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                    .setAutoCancel(true)
                    // Re-posting the same id (poll + push race, retried work) never alerts twice.
                    .setOnlyAlertOnce(true)
                    .setSilent(quiet)
                    .setContentIntent(open(id, deepLink))
            actions.forEachIndexed { i, action ->
                // Distinct request codes keep each action's PendingIntent apart.
                builder.addAction(0, action.label, open(id * ACTION_SLOTS + i + 1, action.deepLink))
            }
            @Suppress("MissingPermission") // checked by canPost()
            manager.notify(id, builder.build())
        }

        override fun postPriceAlert(notification: AppNotification) {
            post(
                TravelChannel.PRICE_ALERTS,
                id = NotificationIds.priceAlert(notification.id),
                title = notification.title.orEmpty(),
                body = notification.body.orEmpty(),
                deepLink = notificationLink(notification).toDeepLink(),
            )
        }

        override fun cancel(id: Int) = manager.cancel(id)

        private companion object {
            const val ACTION_SLOTS = 8
        }
    }

@Module
@InstallIn(SingletonComponent::class)
abstract class NotifierModule {
    @Binds abstract fun notifier(impl: SystemNotifier): Notifier
}
