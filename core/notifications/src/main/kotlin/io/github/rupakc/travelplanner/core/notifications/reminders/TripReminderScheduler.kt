package io.github.rupakc.travelplanner.core.notifications.reminders

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.Binds
import dagger.Module
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.rupakc.travelplanner.core.common.AppClock
import io.github.rupakc.travelplanner.core.data.place.PlaceNameLookup
import io.github.rupakc.travelplanner.core.data.repository.SettingsRepository
import io.github.rupakc.travelplanner.core.domain.alerts.parseIsoDate
import io.github.rupakc.travelplanner.core.model.plan.Plan
import io.github.rupakc.travelplanner.core.notifications.NotificationAction
import io.github.rupakc.travelplanner.core.notifications.NotificationIds
import io.github.rupakc.travelplanner.core.notifications.NotificationLinks
import io.github.rupakc.travelplanner.core.notifications.Notifier
import io.github.rupakc.travelplanner.core.notifications.R
import io.github.rupakc.travelplanner.core.notifications.TravelChannel
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/** Local reminders for saved plans with dates (spec §7). Rescheduled on plan save/delete and boot. */
interface TripReminderScheduler {
    /**
     * Makes the scheduled reminders those of [plans] (an empty list cancels all): reminders no longer
     * wanted are cancelled, unchanged ones are kept as they are, and new ones are added. [clockChanged]
     * (time or time zone changed) re-times every reminder for the new wall clock.
     */
    fun reschedule(plans: List<Plan>, clockChanged: Boolean = false)
}

/** The reminders of [plans] still ahead of [now] (plans without a valid departure are skipped). */
fun remindersFor(plans: List<Plan>, now: ZonedDateTime): List<Pair<Plan, TripReminder>> =
    plans.flatMap { plan ->
        val request = plan.searchRequest ?: return@flatMap emptyList()
        val departure = parseIsoDate(request.departureDate) ?: return@flatMap emptyList()
        TripReminderPlanner.plan(plan.id, departure, parseIsoDate(request.returnDate), now).map { plan to it }
    }

@Singleton
class WorkManagerTripReminderScheduler
    internal constructor(
        private val context: Context,
        private val clock: AppClock,
        private val zone: () -> ZoneId,
    ) : TripReminderScheduler {
        /** The zone is read at each call: after ACTION_TIMEZONE_CHANGED the process default is the new one. */
        @Inject
        constructor(
            @ApplicationContext context: Context,
            clock: AppClock,
        ) : this(context, clock, { ZoneId.systemDefault() })

        @Synchronized
        override fun reschedule(plans: List<Plan>, clockChanged: Boolean) {
            val workManager = WorkManager.getInstance(context)
            val now = ZonedDateTime.ofInstant(clock.now(), zone())
            val wanted = remindersFor(plans, now).associateBy { (plan, reminder) -> workName(plan, reminder) }
            // Cancel only what is no longer wanted (a pending, overdue reminder must survive app starts).
            workManager.getWorkInfosByTag(TAG).get()
                .filterNot { it.state.isFinished }
                .flatMap { info -> info.tags.filter { it.startsWith(NAME_TAG) }.map { it.removePrefix(NAME_TAG) } }
                .filterNot { it in wanted }
                .forEach(workManager::cancelUniqueWork)
            val policy = if (clockChanged) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP
            wanted.forEach { (name, pair) ->
                val (plan, reminder) = pair
                val delay = Duration.between(now, reminder.fireAt).toMillis().coerceAtLeast(0)
                val request =
                    OneTimeWorkRequestBuilder<TripReminderWorker>()
                        .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                        .addTag(TAG)
                        .addTag("$TAG-plan-${plan.id}")
                        .addTag(NAME_TAG + name)
                        .setInputData(
                            workDataOf(
                                TripReminderWorker.KEY_PLAN_ID to plan.id,
                                TripReminderWorker.KEY_TYPE to reminder.type.name,
                                TripReminderWorker.KEY_DAY to reminder.dayNumber,
                                TripReminderWorker.KEY_DESTINATION to destinationOf(plan),
                                TripReminderWorker.KEY_UNIQUE to name,
                            ),
                        ).build()
                workManager.enqueueUniqueWork(name, policy, request)
            }
        }

        companion object {
            const val TAG = "trip-reminder"
            private const val NAME_TAG = "trip-reminder-name:"

            /** Unique per reminder and destination: renaming the trip's destination re-creates the text. */
            internal fun workName(plan: Plan, reminder: TripReminder): String =
                reminder.uniqueName + "-" + Integer.toHexString(destinationOf(plan).hashCode())
        }
    }

/** Unique names of reminders already posted (a re-enqueued grace reminder never posts twice). */
interface ReminderPostLog {
    fun wasPosted(name: String): Boolean

    fun markPosted(name: String)
}

@Singleton
class SharedPrefsReminderPostLog
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : ReminderPostLog {
        private val prefs by lazy { context.getSharedPreferences(FILE, Context.MODE_PRIVATE) }

        override fun wasPosted(name: String): Boolean = prefs.getStringSet(KEY, emptySet()).orEmpty().contains(name)

        override fun markPosted(name: String) {
            // Keep the newest names only; reminder names carry their date, so old ones never come back.
            val names = (prefs.getStringSet(KEY, emptySet()).orEmpty() + name).sorted().takeLast(MAX_NAMES).toSet()
            prefs.edit().putStringSet(KEY, names).apply()
        }

        private companion object {
            const val FILE = "trip_reminders"
            const val KEY = "posted"
            const val MAX_NAMES = 500
        }
    }

/** "Lisbon" from "Lisbon, Portugal"; multi-city trips name their first stop; falls back to the plan name. */
internal fun destinationOf(plan: Plan): String {
    val request = plan.searchRequest
    val stop = request?.destinations?.firstOrNull() ?: request?.destination
    return stop?.substringBefore(',')?.trim()?.takeIf(String::isNotEmpty) ?: plan.name
}

/** Posts one trip reminder, if trip reminders are still on in Settings. */
@HiltWorker
class TripReminderWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted params: WorkerParameters,
        private val settings: SettingsRepository,
        private val notifier: Notifier,
        private val posted: ReminderPostLog,
        private val placeNames: PlaceNameLookup,
    ) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            val name = inputData.getString(KEY_UNIQUE)
            val planId = inputData.getLong(KEY_PLAN_ID, -1).takeIf { it >= 0 }
            val type = runCatching { ReminderType.valueOf(inputData.getString(KEY_TYPE).orEmpty()) }.getOrNull()
            val skip = (name != null && posted.wasPosted(name)) || !settings.settings.first().tripNotifications
            if (planId == null || type == null || skip) return Result.success()
            val place = placeNames.displayName(inputData.getString(KEY_DESTINATION).orEmpty())
            val day = inputData.getInt(KEY_DAY, 0)
            val res = applicationContext.resources
            val (title, body) =
                when (type) {
                    ReminderType.WEEK_BEFORE -> res.getString(R.string.reminder_week_title, place) to res.getString(R.string.reminder_week_body)
                    ReminderType.DAY_BEFORE -> res.getString(R.string.reminder_day_before_title, place) to res.getString(R.string.reminder_day_before_body)
                    ReminderType.TRIP_START -> res.getString(R.string.reminder_start_title, place) to res.getString(R.string.reminder_start_body)
                    ReminderType.TRIP_DAY -> res.getString(R.string.reminder_day_title, day, place) to res.getString(R.string.reminder_day_body)
                    ReminderType.LAST_DAY -> res.getString(R.string.reminder_last_title, place) to res.getString(R.string.reminder_last_body)
                }
            val planLink = NotificationLinks.plan(planId)
            val actions =
                if (type == ReminderType.TRIP_START) {
                    listOf(
                        NotificationAction(res.getString(R.string.reminder_action_itinerary), planLink),
                        NotificationAction(res.getString(R.string.reminder_action_emergency), planLink),
                        NotificationAction(res.getString(R.string.reminder_action_nearby), NotificationLinks.NEARBY),
                    )
                } else {
                    emptyList()
                }
            val unique = name ?: "$planId-$type-$day"
            notifier.post(TravelChannel.TRIP_REMINDERS, NotificationIds.tripReminder(unique), title, body, planLink, actions)
            posted.markPosted(unique)
            return Result.success()
        }

        companion object {
            const val KEY_PLAN_ID = "plan_id"
            const val KEY_TYPE = "type"
            const val KEY_DAY = "day"
            const val KEY_DESTINATION = "destination"
            const val KEY_UNIQUE = "unique"
        }
    }

@Module
@InstallIn(SingletonComponent::class)
abstract class TripReminderModule {
    @Binds abstract fun scheduler(impl: WorkManagerTripReminderScheduler): TripReminderScheduler

    @Binds abstract fun postLog(impl: SharedPrefsReminderPostLog): ReminderPostLog
}
