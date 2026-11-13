package io.github.rupakc.travelplanner.core.notifications.nearby

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.Binds
import dagger.Module
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.rupakc.travelplanner.core.common.AppClock
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.data.plan.CurrentPlanStore
import io.github.rupakc.travelplanner.core.data.repository.AuthRepository
import io.github.rupakc.travelplanner.core.data.repository.JourneyFlagsRepository
import io.github.rupakc.travelplanner.core.data.repository.NearbyRepository
import io.github.rupakc.travelplanner.core.data.repository.NotificationCatalogRepository
import io.github.rupakc.travelplanner.core.data.repository.NotificationSettingsRepository
import io.github.rupakc.travelplanner.core.data.repository.PlansRepository
import io.github.rupakc.travelplanner.core.data.repository.SettingsRepository
import io.github.rupakc.travelplanner.core.data.telemetry.Telemetry
import io.github.rupakc.travelplanner.core.domain.alerts.parseIsoDate
import io.github.rupakc.travelplanner.core.domain.telemetry.TelemetryEvents
import io.github.rupakc.travelplanner.core.model.alerts.QuietHours
import io.github.rupakc.travelplanner.core.model.nearby.LocationCell
import io.github.rupakc.travelplanner.core.model.notifications.NotificationCategory
import io.github.rupakc.travelplanner.core.model.search.TravelSearchRequest
import io.github.rupakc.travelplanner.core.notifications.NotificationIds
import io.github.rupakc.travelplanner.core.notifications.NotificationLinks
import io.github.rupakc.travelplanner.core.notifications.Notifier
import io.github.rupakc.travelplanner.core.notifications.R
import io.github.rupakc.travelplanner.core.notifications.TravelChannel
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * When the "See what's near you" invitation may be posted: opt-in, during a trip unless "always", at most once per
 * local calendar day, and never in quiet hours ([QUIET_START]–[QUIET_END] local time).
 */
object NearbyNotificationPolicy {
    /** How often the worker wakes to see whether an invitation is allowed (not how often one is posted). */
    val MIN_GAP: Duration = Duration.ofHours(4)

    /** Quiet hours start (inclusive), local time. */
    val QUIET_START: LocalTime = LocalTime.of(22, 0)

    /** Quiet hours end (exclusive), local time. */
    val QUIET_END: LocalTime = LocalTime.of(8, 0)

    @Suppress("LongParameterList")
    fun shouldNotify(
        enabled: Boolean,
        always: Boolean,
        onTrip: Boolean,
        lastPostedAt: Instant?,
        now: Instant,
        zone: ZoneId,
        userQuietHours: QuietHours? = null,
    ): Boolean {
        if (!enabled) return false
        if (!always && !onTrip) return false
        val local = now.atZone(zone)
        if (isQuiet(local.toLocalTime()) || NearbyArrivalText.inUserQuietHours(userQuietHours, local.toLocalTime())) return false
        return lastPostedAt == null || lastPostedAt.atZone(zone).toLocalDate() != local.toLocalDate()
    }

    /** 22:00–08:00 local. */
    fun isQuiet(time: LocalTime): Boolean = !time.isBefore(QUIET_START) || time.isBefore(QUIET_END)

    /** [today] falls within a trip's dates (departure..return, one-way = the departure day). */
    fun isOnTrip(trips: List<TravelSearchRequest>, today: LocalDate): Boolean =
        trips.any { r ->
            val dep = parseIsoDate(r.departureDate) ?: return@any false
            val ret = parseIsoDate(r.returnDate) ?: dep
            !today.isBefore(dep) && !today.isAfter(ret)
        }
}

/**
 * The last foreground fix from Near me (a ~5 km cell), only while it is under [MAX_AGE] old: the
 * invitation is posted only to someone who used Near me recently. Background location is never requested.
 */
fun usableLocation(cell: LocationCell?, now: Instant): LocationCell? =
    cell?.takeIf { Duration.between(Instant.ofEpochMilli(it.atEpochMillis), now) < MAX_AGE }

/** How old the remembered Near me fix may be for a background invitation. */
val MAX_AGE: Duration = Duration.ofHours(24)

/** Remembers when the last nearby suggestion was posted (device-local, survives restarts). */
interface NearbyPostLog {
    fun lastPostedAt(): Instant?

    fun markPosted(at: Instant)
}

@Singleton
class SharedPrefsNearbyPostLog
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : NearbyPostLog {
        private val prefs by lazy { context.getSharedPreferences(FILE, Context.MODE_PRIVATE) }

        override fun lastPostedAt(): Instant? = prefs.getLong(KEY, -1).takeIf { it >= 0 }?.let(Instant::ofEpochMilli)

        override fun markPosted(at: Instant) = prefs.edit().putLong(KEY, at.toEpochMilli()).apply()

        private companion object {
            const val FILE = "nearby_notifications"
            const val KEY = "last_posted_at"
        }
    }

/**
 * Posts one "See what's near you" invitation when [NearbyNotificationPolicy] allows; it opens Near me,
 * where the user's own request runs. The only network call is [NearbyArrivalSource]'s single non-grounded v1
 * `/api/nearby` request (Google Maps grounding may only answer a user's request, never a background job); on any
 * failure the generic text is posted. The remembered ~5 km cell is sent only in that request.
 */
@Suppress("LongParameterList")
@HiltWorker
class NearbySuggestionWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted params: WorkerParameters,
        private val settings: SettingsRepository,
        private val currentPlan: CurrentPlanStore,
        private val plans: PlansRepository,
        private val nearby: NearbyRepository,
        private val log: NearbyPostLog,
        private val notifier: Notifier,
        private val clock: AppClock,
        private val catalog: NotificationCatalogRepository,
        private val serverSettings: NotificationSettingsRepository,
        private val journeyFlags: JourneyFlagsRepository,
        private val auth: AuthRepository,
        private val arrivals: NearbyArrivalSource,
        private val telemetry: Telemetry,
    ) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            // Device rules first: nothing goes to the server when they already say no.
            if (!allowed(null) || !notifier.canPost()) return Result.success()
            // Best effort: the server's `nearby` switch and quiet hours, only with `notifications_api` and a session.
            // Offline or without the API the device rules stand alone.
            val server =
                if (journeyFlags.flags().notifications.apiAvailable && auth.session.first() != null) {
                    (serverSettings.settings() as? AppResult.Success)?.value
                } else {
                    null
                }
            if (server?.isEnabled(NotificationCategory.NEARBY) == false) return Result.success()
            if (server != null && !allowed(server.quietHours)) return Result.success()
            val cell = usableLocation(nearby.lastLocation(), clock.now()) ?: return Result.success()
            val res = applicationContext.resources
            val arrival = arrivals.arrival(cell)
            val body =
                when {
                    arrival == null -> {
                        res.getString(R.string.nearby_notification_body)
                    }

                    arrival.minutes != null -> {
                        NearbyArrivalText.body(catalog.catalog(settings.settings.first().localeTag).templates, arrival)
                    }

                    arrival.reason != null -> {
                        res.getString(R.string.nearby_notification_personalized, arrival.item, arrival.reason)
                    }

                    else -> {
                        res.getString(R.string.nearby_notification_personalized_plain, arrival.item)
                    }
                }
            telemetry.emit(TelemetryEvents.NEARBY_NOTIF_POST, mapOf("kind" to if (arrival == null) "generic" else "personalized"))
            notifier.post(
                TravelChannel.NEARBY,
                NotificationIds.NEARBY,
                res.getString(R.string.nearby_notification_title),
                body,
                NotificationLinks.NEARBY,
            )
            log.markPosted(clock.now())
            return Result.success()
        }

        private suspend fun allowed(userQuietHours: QuietHours?): Boolean {
            val s = settings.settings.first()
            val trips = listOfNotNull(currentPlan.draftRequest()) + plans.plans.first().mapNotNull { it.searchRequest }
            val onTrip = NearbyNotificationPolicy.isOnTrip(trips, clock.today())
            return NearbyNotificationPolicy.shouldNotify(
                s.nearbyNotifications,
                s.nearbyNotificationsAlways,
                onTrip,
                log.lastPostedAt(),
                clock.now(),
                clock.zone(),
                userQuietHours,
            )
        }
    }

/** Runs the nearby worker every 4 h while the toggle is on. */
interface NearbySuggestionScheduler {
    fun setEnabled(enabled: Boolean)
}

@Singleton
class WorkManagerNearbySuggestionScheduler
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : NearbySuggestionScheduler {
        override fun setEnabled(enabled: Boolean) {
            val workManager = WorkManager.getInstance(context)
            if (!enabled) {
                workManager.cancelUniqueWork(UNIQUE)
                return
            }
            val request =
                PeriodicWorkRequestBuilder<NearbySuggestionWorker>(NearbyNotificationPolicy.MIN_GAP.toHours(), TimeUnit.HOURS)
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).setRequiresBatteryNotLow(true).build())
                    .build()
            workManager.enqueueUniquePeriodicWork(UNIQUE, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        companion object {
            const val UNIQUE = "nearby-suggestions"
        }
    }

/** The Nearby suggestions toggle: runs or stops the worker, and forgets the remembered location when off. */
@Singleton
class NearbySuggestionsToggle
    @Inject
    constructor(
        private val scheduler: NearbySuggestionScheduler,
        private val nearby: NearbyRepository,
    ) {
        suspend fun apply(on: Boolean) {
            scheduler.setEnabled(on)
            // The remembered location exists only for these suggestions: off = forget it.
            if (!on) nearby.forgetLocation()
        }
    }

@Module
@InstallIn(SingletonComponent::class)
abstract class NearbyNotificationsModule {
    @Binds abstract fun log(impl: SharedPrefsNearbyPostLog): NearbyPostLog

    @Binds abstract fun scheduler(impl: WorkManagerNearbySuggestionScheduler): NearbySuggestionScheduler
}
