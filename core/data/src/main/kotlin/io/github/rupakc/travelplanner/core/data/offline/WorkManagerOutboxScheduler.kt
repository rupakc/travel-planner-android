package io.github.rupakc.travelplanner.core.data.offline

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** A connectivity-triggered flush plus a 15 minute periodic one, both unique (already queued runs are kept). */
@Singleton
internal class WorkManagerOutboxScheduler
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : OutboxFlushScheduler {
        private val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        override fun schedule() {
            val manager = WorkManager.getInstance(context)
            val once =
                OneTimeWorkRequestBuilder<OutboxFlushWorker>()
                    .setConstraints(constraints)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_S, TimeUnit.SECONDS)
                    .build()
            manager.enqueueUniqueWork(ONCE, ExistingWorkPolicy.KEEP, once)
            val periodic = PeriodicWorkRequestBuilder<OutboxFlushWorker>(PERIOD_MIN, TimeUnit.MINUTES).setConstraints(constraints).build()
            manager.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, periodic)
        }

        override fun cancelPeriodic() {
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC)
        }

        private companion object {
            const val ONCE = "outbox_flush"
            const val PERIODIC = "outbox_flush_periodic"
            const val BACKOFF_S = 30L
            const val PERIOD_MIN = 15L
        }
    }
