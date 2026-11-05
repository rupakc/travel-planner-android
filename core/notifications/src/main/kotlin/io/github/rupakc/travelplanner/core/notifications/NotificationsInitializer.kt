package io.github.rupakc.travelplanner.core.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.rupakc.travelplanner.core.data.di.ApplicationScope
import io.github.rupakc.travelplanner.core.data.repository.AuthRepository
import io.github.rupakc.travelplanner.core.data.repository.HealthRepository
import io.github.rupakc.travelplanner.core.data.repository.PlansRepository
import io.github.rupakc.travelplanner.core.data.repository.SettingsRepository
import io.github.rupakc.travelplanner.core.notifications.alerts.AlertPollScheduler
import io.github.rupakc.travelplanner.core.notifications.alerts.PollBaseline
import io.github.rupakc.travelplanner.core.notifications.nearby.NearbySuggestionsToggle
import io.github.rupakc.travelplanner.core.notifications.reminders.TripReminderScheduler
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Wires notifications at app start (call [start] from `Application.onCreate`; cheap, no IO on
 * the caller's thread): the alert poll follows the session and `/health.push_configured`, trip
 * reminders follow the saved plans (so every save/delete reschedules), the nearby worker follows
 * its toggle, and the FCM token is registered after each sign-in.
 *
 * Every process start runs this, including worker, FCM and broadcast starts (performance review
 * I1/I2): the poll is scheduled only from known capabilities (persisted, or fetched), never from
 * "unknown"; the `/health` refresh and device registration wait until the UI is shown, and both are
 * skipped when nothing changed (capabilities TTL, registration fingerprint).
 */
@Singleton
class NotificationsInitializer
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        @param:ApplicationScope private val scope: CoroutineScope,
        private val auth: AuthRepository,
        private val health: HealthRepository,
        private val plans: PlansRepository,
        private val settings: SettingsRepository,
        private val alertPoll: AlertPollScheduler,
        private val tripReminders: TripReminderScheduler,
        private val nearby: NearbySuggestionsToggle,
        private val pushRegistrar: PushRegistrar,
        private val pollBaseline: PollBaseline,
        private val foreground: AppForeground,
    ) {
        private var started = false

        /** Overridable in tests (Robolectric has no FirebaseApp). */
        internal var firebaseConfigured: () -> Boolean = { isFirebaseConfigured(context) }

        @Synchronized
        fun start() {
            if (started) return
            started = true
            scope.launch {
                val firebase = firebaseConfigured()
                auth.session.map { it != null }.distinctUntilChanged().collectLatest { inSession ->
                    if (!inSession) {
                        alertPoll.cancel()
                        // Sign-out clears the seen table: the next sign-in starts a fresh baseline.
                        pollBaseline.set(null)
                        return@collectLatest
                    }
                    coroutineScope {
                        health.restore()
                        launch {
                            foreground.awaitForeground()
                            health.refreshIfStale()
                        }
                        // Unknown capabilities keep whatever is scheduled; the first known value decides.
                        health.capabilities
                            .filterNotNull()
                            .map { it.pushConfigured && firebase }
                            .distinctUntilChanged()
                            .collect { push -> alertPoll.schedule(pushActive = push) }
                    }
                }
            }
            scope.launch { plans.plans.distinctUntilChanged().collect { tripReminders.reschedule(it) } }
            scope.launch {
                settings.settings.map { it.nearbyNotifications }.distinctUntilChanged().collect(nearby::apply)
            }
            scope.launch { pushRegistrar.run() }
        }

        /** After a reboot or app update (re-derive from the cached plans) or a time / time-zone change (re-time all). */
        suspend fun rescheduleNow(clockChanged: Boolean = false) {
            tripReminders.reschedule(plans.plans.first(), clockChanged)
        }
    }

/**
 * BOOT_COMPLETED / MY_PACKAGE_REPLACED: reschedule trip reminders from the cached plans.
 * TIMEZONE_CHANGED / TIME_SET: re-time them for the new wall clock (08:00 where the traveller is now).
 */
@AndroidEntryPoint
class RescheduleReceiver : BroadcastReceiver() {
    @Inject lateinit var initializer: NotificationsInitializer

    @Inject @ApplicationScope
    lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in ACTIONS) return
        val pending = goAsync()
        scope.launch {
            try {
                initializer.rescheduleNow(clockChanged = intent.action in CLOCK_ACTIONS)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        val CLOCK_ACTIONS = setOf(Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_TIME_CHANGED)
        val ACTIONS = setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED) + CLOCK_ACTIONS
    }
}
