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
import io.github.rupakc.travelplanner.core.data.repository.NearbyRepository
import io.github.rupakc.travelplanner.core.data.repository.PlansRepository
import io.github.rupakc.travelplanner.core.data.repository.SettingsRepository
import io.github.rupakc.travelplanner.core.domain.alerts.parseIsoDate
import io.github.rupakc.travelplanner.core.model.nearby.LocationCell
import io.github.rupakc.travelplanner.core.model.nearby.NearbyResponse
import io.github.rupakc.travelplanner.core.model.search.TravelSearchRequest
import io.github.rupakc.travelplanner.core.notifications.NotificationIds
import io.github.rupakc.travelplanner.core.notifications.NotificationLinks
import io.github.rupakc.travelplanner.core.notifications.Notifier
import io.github.rupakc.travelplanner.core.notifications.R
import io.github.rupakc.travelplanner.core.notifications.TravelChannel
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/** When a nearby suggestion may be posted (spec §7/§8): opt-in, during a trip unless "always", at most 1 per 4 h. */
object NearbyNotificationPolicy {
    val MIN_GAP: Duration = Duration.ofHours(4)

    /** WorkManager's flex can start a 4 h periodic run a little early; don't skip it for that. */
    val TOLERANCE: Duration = Duration.ofMinutes(15)

    fun shouldNotify(enabled: Boolean, always: Boolean, onTrip: Boolean, lastPostedAt: Instant?, now: Instant): Boolean {
        if (!enabled) return false
        if (!always && !onTrip) return false
        return lastPostedAt == null || Duration.between(lastPostedAt, now) >= MIN_GAP.minus(TOLERANCE)
    }

    /** [today] falls within a trip's dates (departure..return, one-way = the departure day). */
    fun isOnTrip(trips: List<TravelSearchRequest>, today: LocalDate): Boolean =
        trips.any { r ->
            val dep = parseIsoDate(r.departureDate) ?: return@any false
            val ret = parseIsoDate(r.returnDate) ?: dep
            !today.isBefore(dep) && !today.isAfter(ret)
        }
}

/**
 * Where background suggestions look: the last foreground fix from Near me (a ~5 km cell), only
 * while it is under [MAX_AGE] old. Background location is never requested (spec §8: foreground only).
 */
fun usableLocation(cell: LocationCell?, now: Instant): LocationCell? =
    cell?.takeIf { Duration.between(Instant.ofEpochMilli(it.atEpochMillis), now) < MAX_AGE }

/** How old the remembered Near me fix may be for a background suggestion. */
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

/** Posts one "near you" suggestion from `/api/nearby` when [NearbyNotificationPolicy] allows; opens Near me. */
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
    ) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            if (!allowed() || !notifier.canPost()) return Result.success()
            val response = suggestions() ?: return Result.success()
            val top = response.items.firstOrNull { !it.name.isNullOrBlank() } ?: return Result.success()
            val res = applicationContext.resources
            val place = response.place?.name
            val title = if (place != null) res.getString(R.string.nearby_notification_title_place, place) else res.getString(R.string.nearby_notification_title)
            val body = listOfNotNull(top.name, top.description?.takeIf(String::isNotBlank)).joinToString(" — ")
            notifier.post(TravelChannel.NEARBY, NotificationIds.NEARBY, title, body, NotificationLinks.NEARBY)
            log.markPosted(clock.now())
            return Result.success()
        }

        private suspend fun allowed(): Boolean {
            val s = settings.settings.first()
            val trips = listOfNotNull(currentPlan.draft.value.searchRequest) + plans.plans.first().mapNotNull { it.searchRequest }
            val onTrip = NearbyNotificationPolicy.isOnTrip(trips, clock.today())
            return NearbyNotificationPolicy.shouldNotify(s.nearbyNotifications, s.nearbyNotificationsAlways, onTrip, log.lastPostedAt(), clock.now())
        }

        private suspend fun suggestions(): NearbyResponse? {
            val cell = usableLocation(nearby.lastLocation(), clock.now()) ?: return null
            return (nearby.nearby(cell.lat, cell.lng, limit = SUGGESTIONS) as? AppResult.Success)?.value
        }

        private companion object {
            const val SUGGESTIONS = 3
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
