package io.github.rupakc.travelplanner.core.notifications.alerts

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
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
import dagger.multibindings.IntoSet
import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.data.repository.AuthRepository
import io.github.rupakc.travelplanner.core.data.repository.NotificationsRepository
import io.github.rupakc.travelplanner.core.data.repository.SettingsRepository
import io.github.rupakc.travelplanner.core.data.repository.UserStateCleaner
import io.github.rupakc.travelplanner.core.notifications.Notifier
import java.time.Duration
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random
import kotlinx.coroutines.flow.first

/**
 * Which signed-in user the poller has a baseline for. The first poll for a user (fresh install,
 * or after sign-out, which clears the seen table) marks every current unread alert seen without
 * posting it, so signing in never floods old alerts; later polls post only newer ones.
 */
interface PollBaseline {
    fun user(): String?

    fun set(user: String?)
}

@Singleton
class SharedPrefsPollBaseline
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : PollBaseline {
        private val prefs by lazy { context.getSharedPreferences(FILE, Context.MODE_PRIVATE) }

        override fun user(): String? = prefs.getString(KEY, null)

        override fun set(user: String?) = prefs.edit().putString(KEY, user).apply()

        private companion object {
            const val FILE = "alert_poll"
            const val KEY = "baseline_user"
        }
    }

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
        private val baseline: PollBaseline,
    ) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            val user = auth.session.first()?.user?.username ?: return Result.success()
            val unseen =
                when (val result = notifications.unseen()) {
                    is AppResult.Success -> result.value
                    is AppResult.Failure -> return if (result.error.isRetryable()) Result.retry() else Result.success()
                }
            val first = baseline.user() != user
            // Seen first: a crash after posting must not post (and alert) again.
            if (unseen.isNotEmpty()) notifications.markSeen(unseen.map { it.id })
            if (first) {
                baseline.set(user)
                return Result.success()
            }
            if (settings.settings.first().priceAlertNotifications) {
                // Oldest first, so the newest ends up on top of the shade.
                unseen.sortedBy { it.id }.forEach(notifier::postPriceAlert)
            }
            return Result.success()
        }
    }

/**
 * Network and 5xx retry with the request's backoff (from [AlertPollIntervals.RETRY_BACKOFF]). A 429 does not:
 * the next period is the retry, so a throttled server never sees a retry burst (performance review M2).
 */
private fun AppError.isRetryable(): Boolean = this is AppError.Network || this is AppError.Server

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

    /** First retry delay after a network or server failure (exponential after that), instead of WorkManager's 30 s. */
    val RETRY_BACKOFF: Duration = Duration.ofMinutes(10)

    fun of(pushActive: Boolean): Duration = if (pushActive) WITH_PUSH else WITHOUT_PUSH

    /**
     * How to enqueue [wanted] when [applied] was last enqueued (persisted across processes): the same interval
     * KEEPs the running schedule (and its next run time); a different one UPDATEs it (performance review I2).
     */
    fun policy(applied: Duration?, wanted: Duration): ExistingPeriodicWorkPolicy =
        if (applied == wanted) ExistingPeriodicWorkPolicy.KEEP else ExistingPeriodicWorkPolicy.UPDATE
}

/** The poll interval last enqueued, persisted so a new process doesn't re-enqueue (or flip) it. */
interface AlertPollIntervalStore {
    fun applied(): Duration?

    fun set(interval: Duration?)
}

@Singleton
class SharedPrefsAlertPollIntervalStore
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : AlertPollIntervalStore {
        private val prefs by lazy { context.getSharedPreferences(FILE, Context.MODE_PRIVATE) }

        override fun applied(): Duration? = prefs.getLong(KEY, -1).takeIf { it > 0 }?.let(Duration::ofMinutes)

        override fun set(interval: Duration?) = prefs.edit().putLong(KEY, interval?.toMinutes() ?: -1).apply()

        private companion object {
            const val FILE = "alert_poll"
            const val KEY = "interval_minutes"
        }
    }

@Singleton
class WorkManagerAlertPollScheduler
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val store: AlertPollIntervalStore,
    ) : AlertPollScheduler {
        /** What this process already made sure of; skips even the KEEP enqueue on repeats. */
        private var ensured: Duration? = null

        @Synchronized
        override fun schedule(pushActive: Boolean) {
            val interval = AlertPollIntervals.of(pushActive)
            if (interval == ensured) return
            val policy = AlertPollIntervals.policy(store.applied(), interval)
            val request =
                PeriodicWorkRequestBuilder<AlertPollWorker>(interval.toMinutes(), TimeUnit.MINUTES)
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, AlertPollIntervals.RETRY_BACKOFF.toMinutes(), TimeUnit.MINUTES)
                    // Jitter: spread devices so polls never line up across users (spec §4 Scale).
                    .setInitialDelay(Random.nextLong(interval.toMinutes() / 2 + 1), TimeUnit.MINUTES)
                    .build()
            // KEEP when unchanged: still re-creates the work if it was lost, never moves its next run.
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(UNIQUE, policy, request)
            store.set(interval)
            ensured = interval
        }

        @Synchronized
        override fun cancel() {
            ensured = null
            store.set(null)
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

    @Binds abstract fun baseline(impl: SharedPrefsPollBaseline): PollBaseline

    @Binds abstract fun intervalStore(impl: SharedPrefsAlertPollIntervalStore): AlertPollIntervalStore

    @Binds @IntoSet
    abstract fun baselineCleaner(impl: PollBaselineCleaner): UserStateCleaner
}

/**
 * Forgets the poll baseline in the session wipe, so the next sign-in (same user included: the seen
 * table was cleared too) marks current unread alerts seen instead of posting them all (security S-M5).
 */
class PollBaselineCleaner
    @Inject
    constructor(
        private val baseline: PollBaseline,
    ) : UserStateCleaner {
        override suspend fun clear() = baseline.set(null)
    }
