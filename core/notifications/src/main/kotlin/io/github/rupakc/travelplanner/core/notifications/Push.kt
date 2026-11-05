package io.github.rupakc.travelplanner.core.notifications

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.rupakc.travelplanner.core.common.AppLogger
import io.github.rupakc.travelplanner.core.data.di.ApplicationScope
import io.github.rupakc.travelplanner.core.data.repository.DevicesRepository
import io.github.rupakc.travelplanner.core.data.repository.NotificationsRepository
import io.github.rupakc.travelplanner.core.data.repository.PushTokenSource
import io.github.rupakc.travelplanner.core.model.alerts.AppNotification
import io.github.rupakc.travelplanner.core.model.devices.PushDataKeys
import io.github.rupakc.travelplanner.core.model.navigation.DeepLinks
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** True only when the app was built with google-services.json (FirebaseApp auto-initialised). */
fun isFirebaseConfigured(context: Context): Boolean = runCatching { FirebaseApp.getApps(context).isNotEmpty() }.getOrDefault(false)

/** FCM token, or null when Firebase is not configured (the default build). */
@Singleton
class FirebasePushTokenSource @Inject constructor(@param:ApplicationContext private val context: Context) : PushTokenSource {
    @Suppress("TooGenericExceptionCaught")
    override suspend fun token(): String? {
        if (!isFirebaseConfigured(context)) return null
        return try {
            FirebaseMessaging.getInstance().token.await()
        } catch (e: Exception) {
            AppLogger.w(TAG, "FCM token unavailable", e)
            null
        }
    }

    private companion object {
        const val TAG = "Push"
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class PushModule {
    @Binds abstract fun pushTokenSource(impl: FirebasePushTokenSource): PushTokenSource
}

/**
 * FCM entry point. Disabled in the manifest unless Firebase is configured; even
 * when enabled it only registers tokens and posts alerts (deduped by id with the poller).
 */
@AndroidEntryPoint
class TravelMessagingService : FirebaseMessagingService() {
    @Inject lateinit var devices: DevicesRepository

    @Inject lateinit var notifications: NotificationsRepository

    @Inject lateinit var notifier: Notifier

    @Inject @ApplicationScope
    lateinit var scope: CoroutineScope

    override fun onNewToken(token: String) {
        scope.launch { devices.register(token, Locale.getDefault().toLanguageTag()) }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val id = data[PushDataKeys.NOTIFICATION_ID]?.toLongOrNull() ?: return
        val alert = AppNotification(
            id = id,
            kind = data[PushDataKeys.KIND],
            title = message.notification?.title,
            body = message.notification?.body,
        )
        scope.launch {
            notifications.markSeen(listOf(id))
            val watchLink = data[PushDataKeys.WATCH_ID]?.let { watch ->
                "${DeepLinks.SCHEME}://alerts?" + listOfNotNull(data[PushDataKeys.WATCH_KIND]?.let { "kind=$it" }, "watch=$watch").joinToString("&")
            }
            if (watchLink != null) {
                notifier.post(TravelChannel.PRICE_ALERTS, (id % Int.MAX_VALUE).toInt(), alert.title.orEmpty(), alert.body.orEmpty(), watchLink)
            } else {
                notifier.postPriceAlert(alert)
            }
        }
    }
}
