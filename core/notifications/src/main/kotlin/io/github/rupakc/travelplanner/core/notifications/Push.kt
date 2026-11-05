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
import io.github.rupakc.travelplanner.core.data.repository.AuthRepository
import io.github.rupakc.travelplanner.core.data.repository.DevicesRepository
import io.github.rupakc.travelplanner.core.data.repository.NotificationsRepository
import io.github.rupakc.travelplanner.core.data.repository.PushTokenSource
import io.github.rupakc.travelplanner.core.data.repository.SettingsRepository
import io.github.rupakc.travelplanner.core.domain.alerts.AlertLink
import io.github.rupakc.travelplanner.core.domain.alerts.parseAlertsPath
import io.github.rupakc.travelplanner.core.model.devices.PushDataKeys
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/** True only when the app was built with google-services.json (FirebaseApp auto-initialised). */
fun isFirebaseConfigured(context: Context): Boolean = runCatching { FirebaseApp.getApps(context).isNotEmpty() }.getOrDefault(false)

/** FCM token, or null when Firebase is not configured (the default build). */
@Singleton
class FirebasePushTokenSource
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : PushTokenSource {
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
 * Registers this device's FCM token after every sign-in (and app start while signed in);
 * `POST /api/devices` is an idempotent upsert. Sign-out unregisters in `AuthRepository.logout`.
 * Inert without Firebase: [PushTokenSource.token] returns null.
 */
@Singleton
class PushRegistrar
    @Inject
    constructor(
        private val auth: AuthRepository,
        private val tokens: PushTokenSource,
        private val devices: DevicesRepository,
    ) {
        /** Suspends for the lifetime of [auth]'s session flow. */
        suspend fun run() {
            auth.session.map { it?.user?.username }.distinctUntilChanged().collect { username ->
                if (username != null) register()
            }
        }

        suspend fun register() {
            val token = tokens.token() ?: return
            devices.register(token, Locale.getDefault().toLanguageTag())
        }
    }

/** The Alerts link of a push `data` payload: the server's web `link`, else watch id/kind. */
fun pushAlertLink(data: Map<String, String>): AlertLink =
    parseAlertsPath(data[PushDataKeys.LINK])
        ?: AlertLink(data[PushDataKeys.WATCH_KIND], data[PushDataKeys.WATCH_ID]?.toLongOrNull())

/**
 * FCM entry point. Disabled in the manifest unless Firebase is configured. In the background
 * Android shows the message itself; in the foreground we post it (if Price alerts are on) and
 * mark the id seen so [io.github.rupakc.travelplanner.core.notifications.alerts.AlertPollWorker] won't post it again.
 */
@AndroidEntryPoint
class TravelMessagingService : FirebaseMessagingService() {
    @Inject lateinit var devices: DevicesRepository

    @Inject lateinit var auth: AuthRepository

    @Inject lateinit var notifications: NotificationsRepository

    @Inject lateinit var settings: SettingsRepository

    @Inject lateinit var notifier: Notifier

    @Inject @ApplicationScope
    lateinit var scope: CoroutineScope

    override fun onNewToken(token: String) {
        scope.launch {
            // The token is tied to a user server-side: register only while signed in.
            if (auth.session.first() != null) devices.register(token, Locale.getDefault().toLanguageTag())
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val id = data[PushDataKeys.NOTIFICATION_ID]?.toLongOrNull() ?: return
        scope.launch {
            notifications.markSeen(listOf(id))
            if (!settings.settings.first().priceAlertNotifications) return@launch
            notifier.post(
                TravelChannel.PRICE_ALERTS,
                NotificationIds.priceAlert(id),
                message.notification?.title.orEmpty(),
                message.notification?.body.orEmpty(),
                pushAlertLink(data).toDeepLink(),
            )
        }
    }
}
