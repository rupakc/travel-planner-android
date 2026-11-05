package io.github.rupakc.travelplanner.core.notifications.alerts

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
import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.data.repository.AuthRepository
import io.github.rupakc.travelplanner.core.data.repository.NotificationsRepository
import io.github.rupakc.travelplanner.core.data.repository.SettingsRepository
import io.github.rupakc.travelplanner.core.notifications.Notifier
import java.time.Duration
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random
import kotlinx.coroutines.flow.first

/**
 * Reads `/api/notifications` and posts new unread ones locally, deduped by id in Room
 * (`NotificationsRepository.unseen` / `markSeen`, shared with FCM). With the Price alerts toggle
 * off it marks them seen without posting, so turning it back on never floods old alerts.
 */
@HiltWorker
class AlertPollWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted params: WorkerParameters,
        private val auth: AuthRepository,
        private val notifications: NotificationsRepository,
        private val settings: SettingsRepository,
        private val notifier: Notifier,
    ) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            if (auth.session.first() == null) return Result.success()
            val unseen =
                when (val result = notifications.unseen()) {
                    is AppResult.Success -> result.value
                    is AppResult.Failure -> return if (result.error.isTransient()) Result.retry() else Result.success()
                }
            if (unseen.isEmpty()) return Result.success()
            if (settings.settings.first().priceAlertNotifications) {
                // Oldest first, so the newest ends up on top of the shade.
                unseen.sortedBy { it.id }.forEach(notifier::postPriceAlert)
            }
            notifications.markSeen(unseen.map { it.id })
            return Result.success()
        }
    }

private fun AppError.isTransient(): Boolean = this is AppError.Network || this is AppError.Server || this is AppError.RateLimited

/** Keeps the periodic alert poll scheduled at the right interval. */
interface AlertPollScheduler {
    /** [pushActive]: FCM delivers alerts, so the poll is only a safety net. */
    fun schedule(pushActive: Boolean)

    fun cancel()
}

/** Poll intervals (spec §7): 30 min without push, 6 h as a safety net with push. */
object AlertPollIntervals {
    val WITHOUT_PUSH: Duration = Duration.ofMinutes(30)
    val WITH_PUSH: Duration = Duration.ofHours(6)

    fun of(pushActive: Boolean): Duration = if (pushActive) WITH_PUSH else WITHOUT_PUSH
}

@Singleton
class WorkManagerAlertPollScheduler
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : AlertPollScheduler {
        private var current: Duration? = null

        @Synchronized
        override fun schedule(pushActive: Boolean) {
            val interval = AlertPollIntervals.of(pushActive)
            if (interval == current) return
            current = interval
            val request =
                PeriodicWorkRequestBuilder<AlertPollWorker>(interval.toMinutes(), TimeUnit.MINUTES)
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    // Jitter: spread devices so polls never line up across users (spec §4 Scale).
                    .setInitialDelay(Random.nextLong(interval.toMinutes() / 2 + 1), TimeUnit.MINUTES)
                    .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(UNIQUE, ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        @Synchronized
        override fun cancel() {
            current = null
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE)
        }

        companion object {
            const val UNIQUE = "alert-poll"
        }
    }

@Module
@InstallIn(SingletonComponent::class)
abstract class AlertPollModule {
    @Binds abstract fun scheduler(impl: WorkManagerAlertPollScheduler): AlertPollScheduler
}
