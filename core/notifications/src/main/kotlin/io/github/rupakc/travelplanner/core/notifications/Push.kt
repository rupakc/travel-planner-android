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
import io.github.rupakc.travelplanner.core.common.SystemAppClock
import io.github.rupakc.travelplanner.core.data.di.ApplicationScope
import io.github.rupakc.travelplanner.core.data.repository.AuthRepository
import io.github.rupakc.travelplanner.core.data.repository.DevicesRepository
import io.github.rupakc.travelplanner.core.data.repository.NotificationsRepository
import io.github.rupakc.travelplanner.core.data.repository.PushTokenSource
import io.github.rupakc.travelplanner.core.data.repository.SettingsRepository
import io.github.rupakc.travelplanner.core.data.repository.UserStateCleaner
import io.github.rupakc.travelplanner.core.domain.alerts.AlertLink
import io.github.rupakc.travelplanner.core.domain.alerts.NotificationTarget
import io.github.rupakc.travelplanner.core.domain.alerts.notificationCategory
import io.github.rupakc.travelplanner.core.domain.alerts.parseAlertsPath
import io.github.rupakc.travelplanner.core.domain.alerts.resolveNotificationTarget
import io.github.rupakc.travelplanner.core.model.devices.PushDataKeys
import io.github.rupakc.travelplanner.core.model.notifications.NotificationCategory
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

    @Binds abstract fun recentPushKeys(impl: SharedPrefsRecentPushKeys): RecentPushKeys

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

/** Where a push `data` payload leads (R8): a `trip_*` phase push its trip's Today tab, an empty `plan_id` the list, else Alerts. */
fun pushTarget(data: Map<String, String>): NotificationTarget =
    resolveNotificationTarget(
        kind = data[PushDataKeys.KIND],
        link = data[PushDataKeys.LINK],
        planId = data[PushDataKeys.PLAN_ID],
        watchId = data[PushDataKeys.WATCH_ID]?.toLongOrNull(),
        watchKind = data[PushDataKeys.WATCH_KIND],
        category = data[PushDataKeys.CATEGORY],
    )

/** Price-alert notification kinds the server pushes high-priority; `watch_expired` / `watch_error` are notices. */
private val NOTICE_KINDS = setOf("watch_expired", "watch_error")

/**
 * Handles one data-only FCM message (backend `push.py` and `notify_center.py`: keys `kind`, `notification_id`, `link`,
 * `watch_id`, `watch_kind`, `category`, `plan_id` (`""` when none), `group_count` (a grouped price push), `title`,
 * `body`, all strings). Marks the id seen first (so the poller never posts it again), then posts it on the channel of
 * its category (R8: an absent category is a price alert from an older server, an unknown one the default channel), if the
 * price-alert toggle allows it for price drops (the server already applied every other category switch, quiet hours and
 * the daily cap), opening the destination of [pushTarget]. A grouped push replaces the previous group of its category
 * instead of stacking. Text is the server's, already in the user's language and without game wording under `hide_game`.
 * Unknown kinds are tolerated. Posts nothing without a session. Returns true when something was posted.
 */
@Singleton
class PushAlertHandler
    @Inject
    constructor(
        private val auth: AuthRepository,
        private val notifications: NotificationsRepository,
        private val settings: SettingsRepository,
        private val notifier: Notifier,
        private val recent: RecentPushKeys = RecentPushKeys.InMemory(),
        private val clock: AppClock = SystemAppClock(),
    ) {
        suspend fun handle(data: Map<String, String>): Boolean {
            val id = data[PushDataKeys.NOTIFICATION_ID]?.toLongOrNull() ?: return false
            notifications.markSeen(listOf(id))
            // Signed out (e.g. an unregister that never reached the server): never show a user's alerts (API review I1).
            if (auth.session.first() == null || isResend(data)) return false
            val category = notificationCategory(data[PushDataKeys.CATEGORY], data[PushDataKeys.KIND])
            if (!localSwitchAllows(category, settings.settings.first())) return false
            val title = data[KEY_TITLE].orEmpty()
            if (title.isBlank()) return false
            val grouped = (data[PushDataKeys.GROUP_COUNT]?.toIntOrNull() ?: 1) > 1
            notifier.post(
                TravelChannel.forCategory(category),
                if (grouped) NotificationIds.group(category) else NotificationIds.priceAlert(id),
                title,
                data[KEY_BODY].orEmpty(),
                NotificationLinks.of(pushTarget(data), category),
                quiet = data[PushDataKeys.KIND] in NOTICE_KINDS,
            )
            return true
        }

        /** The server's outbox may resend after a lost ack: one notification per dedupe_key (24 h, last 100 keys). */
        private fun isResend(data: Map<String, String>): Boolean =
            data[KEY_DEDUPE]?.takeIf { it.isNotBlank() }?.let { !recent.firstSeen(it, clock.now().toEpochMilli()) } ?: false

        /** The on-device switches that mirror the server categories: price drops, and trip reminders with the morning brief. */
        private fun localSwitchAllows(category: NotificationCategory?, s: io.github.rupakc.travelplanner.core.model.settings.AppSettings): Boolean =
            when (category) {
                NotificationCategory.PRICE_DROPS -> s.priceAlertNotifications
                NotificationCategory.TRIP_REMINDERS, NotificationCategory.MORNING_BRIEF -> s.tripNotifications
                else -> true
            }

        companion object {
            const val KEY_TITLE = "title"
            const val KEY_BODY = "body"
            const val KEY_DEDUPE = "dedupe_key"
        }
    }

private const val RECENT_MAX = 100
private val RECENT_TTL_MILLIS: Long = Duration.ofHours(24).toMillis()

/** Remembers the last push `dedupe_key`s so a resent push shows once. */
interface RecentPushKeys {
    /** True the first time [key] is seen inside the window (and records it); false for a repeat. */
    fun firstSeen(key: String, nowMillis: Long): Boolean

    class InMemory : RecentPushKeys {
        private val seen = RecentKeyWindow()

        @Synchronized
        override fun firstSeen(key: String, nowMillis: Long) = seen.firstSeen(key, nowMillis)
    }
}

/** Insertion-ordered key to time map: entries older than [ttlMillis] or beyond the newest [max] are dropped. */
class RecentKeyWindow(private val max: Int = RECENT_MAX, private val ttlMillis: Long = RECENT_TTL_MILLIS) {
    private val keys = LinkedHashMap<String, Long>()

    constructor(max: Int, ttlMillis: Long, initial: Map<String, Long>) : this(max, ttlMillis) {
        keys.putAll(initial)
    }

    fun firstSeen(key: String, nowMillis: Long): Boolean {
        keys.entries.removeAll { nowMillis - it.value >= ttlMillis }
        if (key in keys) return false
        keys[key] = nowMillis
        while (keys.size > max) keys.remove(keys.keys.first())
        return true
    }

    fun snapshot(): Map<String, Long> = keys.toMap()
}

/** [RecentPushKeys] persisted in app-private prefs (the FCM service may run in a fresh process). */
@Singleton
class SharedPrefsRecentPushKeys
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : RecentPushKeys {
        private val prefs by lazy { context.getSharedPreferences("push_recent_keys", Context.MODE_PRIVATE) }

        @Synchronized
        override fun firstSeen(key: String, nowMillis: Long): Boolean {
            val window = RecentKeyWindow(RECENT_MAX, RECENT_TTL_MILLIS, prefs.all.mapNotNull { (k, v) -> (v as? Long)?.let { k to it } }.toMap())
            val first = window.firstSeen(key, nowMillis)
            if (first) prefs.edit().clear().apply { window.snapshot().forEach { (k, v) -> putLong(k, v) } }.apply()
            return first
        }
    }

/**
 * A notification posted by FCM itself (not via [PushAlertHandler]) opens MainActivity with the data
 * keys as extras. If [intent] has no data URI but a web `link` (or a `plan_id`, or a watch id) extra, give it the app
 * deep link (`travelplanner://alerts?…`, `travelplanner://trip/{id}?tab=today`, `travelplanner://notifications`) in
 * place, so the NavHost opens the right screen. Safe to call on any intent.
 */
fun withPushDeepLink(intent: Intent): Intent {
    if (intent.data != null) return intent
    val extras = intent.extras ?: return intent
    val data = buildMap { extras.keySet().forEach { k -> extras.getString(k)?.let { put(k, it) } } }
    if (PushDataKeys.LINK !in data && PushDataKeys.WATCH_ID !in data && data[PushDataKeys.PLAN_ID].isNullOrBlank()) return intent
    val category = notificationCategory(data[PushDataKeys.CATEGORY], data[PushDataKeys.KIND])
    intent.action = Intent.ACTION_VIEW
    intent.data = NotificationLinks.of(pushTarget(data), category).toUri()
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
        // Post before returning: FCM may stop the process right after this call (it allows ~10–20 s).
        handleBlocking(handler, pushPayload(message))
    }
}

/**
 * The data map of one FCM [message]: its data keys, with the text of a legacy `notification` block (older servers)
 * filling in a missing `title` / `body`. Unknown keys pass through untouched.
 */
fun pushPayload(message: RemoteMessage): Map<String, String> {
    val data = message.data
    return data +
        listOfNotNull(
            message.notification?.title?.let { PushAlertHandler.KEY_TITLE to it }.takeIf { data[PushAlertHandler.KEY_TITLE] == null },
            message.notification?.body?.let { PushAlertHandler.KEY_BODY to it }.takeIf { data[PushAlertHandler.KEY_BODY] == null },
        )
}
