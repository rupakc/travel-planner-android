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
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/** Local reminders for saved plans with dates (spec §7). Rescheduled on plan save/delete and boot. */
interface TripReminderScheduler {
    /** Replaces every scheduled reminder with those of [plans] (an empty list cancels all). */
    fun reschedule(plans: List<Plan>)
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
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val clock: AppClock,
    ) : TripReminderScheduler {
        override fun reschedule(plans: List<Plan>) {
            val workManager = WorkManager.getInstance(context)
            // Serial on WorkManager's executor: the cancel runs before the new requests are enqueued.
            workManager.cancelAllWorkByTag(TAG)
            val now = ZonedDateTime.ofInstant(clock.now(), clock.zone())
            remindersFor(plans, now).forEach { (plan, reminder) ->
                val delay = Duration.between(now, reminder.fireAt).toMillis()
                val request =
                    OneTimeWorkRequestBuilder<TripReminderWorker>()
                        .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                        .addTag(TAG)
                        .addTag("$TAG-plan-${plan.id}")
                        .setInputData(
                            workDataOf(
                                TripReminderWorker.KEY_PLAN_ID to plan.id,
                                TripReminderWorker.KEY_TYPE to reminder.type.name,
                                TripReminderWorker.KEY_DAY to reminder.dayNumber,
                                TripReminderWorker.KEY_DESTINATION to destinationOf(plan),
                                TripReminderWorker.KEY_UNIQUE to reminder.uniqueName,
                            ),
                        ).build()
                workManager.enqueueUniqueWork(reminder.uniqueName, ExistingWorkPolicy.REPLACE, request)
            }
        }

        companion object {
            const val TAG = "trip-reminder"
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
    ) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            if (!settings.settings.first().tripNotifications) return Result.success()
            val planId = inputData.getLong(KEY_PLAN_ID, -1).takeIf { it >= 0 } ?: return Result.success()
            val type = runCatching { ReminderType.valueOf(inputData.getString(KEY_TYPE).orEmpty()) }.getOrNull() ?: return Result.success()
            val place = inputData.getString(KEY_DESTINATION).orEmpty()
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
            val unique = inputData.getString(KEY_UNIQUE) ?: "$planId-$type-$day"
            notifier.post(TravelChannel.TRIP_REMINDERS, NotificationIds.tripReminder(unique), title, body, planLink, actions)
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
}
