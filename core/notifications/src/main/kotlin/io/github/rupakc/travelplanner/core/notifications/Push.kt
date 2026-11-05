package io.github.rupakc.travelplanner.core.notifications

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
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
import dagger.multibindings.IntoSet
import io.github.rupakc.travelplanner.core.common.AppClock
import io.github.rupakc.travelplanner.core.common.AppLogger
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.data.di.ApplicationScope
import io.github.rupakc.travelplanner.core.data.repository.AuthRepository
import io.github.rupakc.travelplanner.core.data.repository.DevicesRepository
import io.github.rupakc.travelplanner.core.data.repository.NotificationsRepository
import io.github.rupakc.travelplanner.core.data.repository.PushTokenSource
import io.github.rupakc.travelplanner.core.data.repository.SettingsRepository
import io.github.rupakc.travelplanner.core.data.repository.UserStateCleaner
import io.github.rupakc.travelplanner.core.domain.alerts.AlertLink
import io.github.rupakc.travelplanner.core.domain.alerts.parseAlertsPath
import io.github.rupakc.travelplanner.core.model.devices.PushDataKeys
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

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

        @Suppress("TooGenericExceptionCaught")
        override suspend fun delete() {
            if (!isFirebaseConfigured(context)) return
            try {
                FirebaseMessaging.getInstance().deleteToken().await()
            } catch (e: Exception) {
                AppLogger.w(TAG, "FCM token not deleted", e)
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

    @Binds abstract fun registrationLog(impl: SharedPrefsPushRegistrationLog): PushRegistrationLog

    @Binds @IntoSet
    abstract fun registrationCleaner(impl: PushRegistrationCleaner): UserStateCleaner
}

/** The last successful `POST /api/devices`: a [fingerprint] of what was registered and when. */
data class PushRegistration(val fingerprint: String, val atMillis: Long)

/** Remembers [PushRegistration] across process starts (app-private prefs; cleared with the user's data). */
interface PushRegistrationLog {
    fun last(): PushRegistration?

    fun record(registration: PushRegistration)

    fun clear()
}

@Singleton
class SharedPrefsPushRegistrationLog
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : PushRegistrationLog {
        private val prefs by lazy { context.getSharedPreferences(FILE, Context.MODE_PRIVATE) }

        override fun last(): PushRegistration? {
            val fingerprint = prefs.getString(KEY_FINGERPRINT, null) ?: return null
            return PushRegistration(fingerprint, prefs.getLong(KEY_AT, 0))
        }

        override fun record(registration: PushRegistration) =
            prefs.edit().putString(KEY_FINGERPRINT, registration.fingerprint).putLong(KEY_AT, registration.atMillis).apply()

        override fun clear() = prefs.edit().clear().apply()

        private companion object {
            const val FILE = "push_registration"
            const val KEY_FINGERPRINT = "fingerprint"
            const val KEY_AT = "at"
        }
    }

/** Sign-out, a different user or a server change: the next sign-in registers again (the server forgot this token). */
class PushRegistrationCleaner
    @Inject
    constructor(
        private val log: PushRegistrationLog,
    ) : UserStateCleaner {
        override suspend fun clear() = log.clear()
    }

/** SHA-256 of what a registration binds together: who, which token, which locale, which server. */
fun pushRegistrationFingerprint(username: String, token: String, locale: String, server: String): String =
    MessageDigest
        .getInstance("SHA-256")
        .digest(listOf(username, token, locale, server).joinToString("\u0000").toByteArray())
        .joinToString("") { "%02x".format(it) }

/**
 * Registers this device's FCM token (`POST /api/devices`, an idempotent upsert) only when the
 * (user, token, locale, server) [pushRegistrationFingerprint] changed, or the last registration is older
 * than [REREGISTER_AFTER] (heals a server that lost it). App starts while signed in check it once the UI is
 * shown; worker and FCM process starts never call the server for it (performance review I1). Sign-out
 * unregisters in `AuthRepository.logout`. Inert without Firebase: [PushTokenSource.token] returns null.
 */
@Singleton
class PushRegistrar
    @Inject
    constructor(
        private val auth: AuthRepository,
        private val tokens: PushTokenSource,
        private val devices: DevicesRepository,
        private val log: PushRegistrationLog,
        private val clock: AppClock,
        private val foreground: AppForeground,
    ) {
        /** Suspends for the lifetime of [auth]'s session flow. */
        suspend fun run() {
            auth.session.map { it?.user?.username }.distinctUntilChanged().collectLatest { username ->
                if (username != null) {
                    foreground.awaitForeground()
                    register()
                }
            }
        }

        /**
         * Registers [token] (from `onNewToken`) or the current token when anything changed since the last
         * registration. Signed out: nothing (the token is tied to a user server-side). True when it called the server.
         */
        suspend fun register(token: String? = null): Boolean {
            val session = auth.session.first() ?: return false
            val current = token ?: tokens.token() ?: return false
            val locale = Locale.getDefault().toLanguageTag()
            val fingerprint = pushRegistrationFingerprint(session.user.username, current, locale, session.baseUrl.orEmpty())
            val now = clock.now()
            val last = log.last()
            if (last?.fingerprint == fingerprint && Duration.between(Instant.ofEpochMilli(last.atMillis), now) < REREGISTER_AFTER) return false
            if (devices.register(current, locale) is AppResult.Success) log.record(PushRegistration(fingerprint, now.toEpochMilli()))
            return true
        }

        companion object {
            val REREGISTER_AFTER: Duration = Duration.ofDays(7)
        }
    }

/** The Alerts link of a push `data` payload: the server's web `link`, else watch id/kind. */
fun pushAlertLink(data: Map<String, String>): AlertLink =
    parseAlertsPath(data[PushDataKeys.LINK])
        ?: AlertLink(data[PushDataKeys.WATCH_KIND], data[PushDataKeys.WATCH_ID]?.toLongOrNull())

/** Price-alert notification kinds the server pushes high-priority; `watch_expired` / `watch_error` are notices. */
private val NOTICE_KINDS = setOf("watch_expired", "watch_error")

/**
 * Handles one data-only FCM message (backend `push.py`: keys `kind`, `notification_id`, `link`,
 * `watch_id`, `watch_kind`, `title`, `body`, all strings). Marks the id seen first (so the poller never
 * posts it again), then posts it on the Price alerts channel if that toggle is on, opening the app
 * route for the server's web `link`. Posts nothing without a session. Returns true when something was posted.
 */
@Singleton
class PushAlertHandler
    @Inject
    constructor(
        private val auth: AuthRepository,
        private val notifications: NotificationsRepository,
        private val settings: SettingsRepository,
        private val notifier: Notifier,
    ) {
        suspend fun handle(data: Map<String, String>): Boolean {
            val id = data[PushDataKeys.NOTIFICATION_ID]?.toLongOrNull() ?: return false
            notifications.markSeen(listOf(id))
            // Signed out (e.g. an unregister that never reached the server): never show a user's alerts (API review I1).
            if (auth.session.first() == null) return false
            if (!settings.settings.first().priceAlertNotifications) return false
            val title = data[KEY_TITLE].orEmpty()
            if (title.isBlank()) return false
            notifier.post(
                TravelChannel.PRICE_ALERTS,
                NotificationIds.priceAlert(id),
                title,
                data[KEY_BODY].orEmpty(),
                pushAlertLink(data).toDeepLink(),
                quiet = data[PushDataKeys.KIND] in NOTICE_KINDS,
            )
            return true
        }

        companion object {
            const val KEY_TITLE = "title"
            const val KEY_BODY = "body"
        }
    }

/**
 * A notification posted by FCM itself (not via [PushAlertHandler]) opens MainActivity with the data
 * keys as extras. If [intent] has no data URI but a web `link` extra, give it the app deep link
 * (`travelplanner://alerts?…`) in place, so the NavHost opens the watch. Safe to call on any intent.
 */
fun withPushDeepLink(intent: Intent): Intent {
    if (intent.data != null) return intent
    val extras = intent.extras ?: return intent
    val link = extras.getString(PushDataKeys.LINK) ?: extras.getString(PushDataKeys.WATCH_ID)?.let { "/alerts?watch=$it" } ?: return intent
    val data = buildMap { extras.keySet().forEach { k -> extras.getString(k)?.let { put(k, it) } } } + (PushDataKeys.LINK to link)
    intent.action = Intent.ACTION_VIEW
    intent.data = pushAlertLink(data).toDeepLink().toUri()
    return intent
}

/** How long a push may take to post on the FCM thread (well inside FCM's ~10–20 s window). */
internal const val PUSH_TIMEOUT_MILLIS = 8_000L

/** Runs [PushAlertHandler.handle] synchronously with [PUSH_TIMEOUT_MILLIS]; false when it timed out. */
internal fun handleBlocking(handler: PushAlertHandler, data: Map<String, String>): Boolean =
    runBlocking { withTimeoutOrNull(PUSH_TIMEOUT_MILLIS) { handler.handle(data) } } ?: false

/**
 * FCM entry point. Disabled in the manifest unless Firebase is configured. Messages are
 * data-only, so this always runs, in the foreground and the background ([PushAlertHandler]).
 */
@AndroidEntryPoint
class TravelMessagingService : FirebaseMessagingService() {
    @Inject lateinit var registrar: PushRegistrar

    @Inject lateinit var handler: PushAlertHandler

    @Inject @ApplicationScope
    lateinit var scope: CoroutineScope

    override fun onNewToken(token: String) {
        // The token is tied to a user server-side: the registrar registers it only while signed in.
        scope.launch { registrar.register(token) }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        // Older servers also sent a notification block; fall back to it for the text.
        val withText =
            data +
                listOfNotNull(
                    message.notification?.title?.let { PushAlertHandler.KEY_TITLE to it }.takeIf { data[PushAlertHandler.KEY_TITLE] == null },
                    message.notification?.body?.let { PushAlertHandler.KEY_BODY to it }.takeIf { data[PushAlertHandler.KEY_BODY] == null },
                )
        // Post before returning: FCM may stop the process right after this call (it allows ~10–20 s).
        handleBlocking(handler, withText)
    }
}
