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
import io.github.rupakc.travelplanner.core.model.alerts.AppNotification
import io.github.rupakc.travelplanner.core.model.navigation.DeepLinks
import javax.inject.Inject
import javax.inject.Singleton

/** Posts local notifications. Every post opens [deepLink] (a `travelplanner://` URI) in MainActivity. */
interface Notifier {
    /** False when POST_NOTIFICATIONS is not granted (Android 13+) or notifications are blocked. */
    fun canPost(): Boolean

    fun post(channel: TravelChannel, id: Int, title: String, body: String, deepLink: String)

    /** A backend price alert (from AlertPollWorker or FCM); deep-links to Alerts like the web `/alerts?kind&watch`. */
    fun postPriceAlert(notification: AppNotification)

    fun cancel(id: Int)
}

@Singleton
class SystemNotifier @Inject constructor(@param:ApplicationContext private val context: Context) : Notifier {
    private val manager = NotificationManagerCompat.from(context)

    override fun canPost(): Boolean {
        val permitted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return permitted && manager.areNotificationsEnabled()
    }

    override fun post(channel: TravelChannel, id: Int, title: String, body: String, deepLink: String) {
        if (!canPost()) return
        val intent = Intent(Intent.ACTION_VIEW, deepLink.toUri()).setPackage(context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(context, id, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(context, channel.id)
            .setSmallIcon(R.drawable.ic_stat_voyager)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        @Suppress("MissingPermission") // checked by canPost()
        manager.notify(id, notification)
    }

    override fun postPriceAlert(notification: AppNotification) {
        val link = buildString {
            append("${DeepLinks.SCHEME}://alerts")
            val params = listOfNotNull(notification.watchKind?.let { "kind=$it" }, notification.watchId?.let { "watch=$it" })
            if (params.isNotEmpty()) append("?").append(params.joinToString("&"))
        }
        post(
            TravelChannel.PRICE_ALERTS,
            id = (PRICE_ALERT_ID_BASE + notification.id % PRICE_ALERT_ID_RANGE).toInt(),
            title = notification.title.orEmpty(),
            body = notification.body.orEmpty(),
            deepLink = link,
        )
    }

    override fun cancel(id: Int) = manager.cancel(id)

    private companion object {
        const val PRICE_ALERT_ID_BASE = 100_000L
        const val PRICE_ALERT_ID_RANGE = 1_000_000L
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class NotifierModule {
    @Binds abstract fun notifier(impl: SystemNotifier): Notifier
}
