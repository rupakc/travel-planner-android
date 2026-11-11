package io.github.rupakc.travelplanner.core.data.telemetry

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** One unique, network-constrained flush at a time (an already-queued run is kept, not duplicated). */
@Singleton
internal class WorkManagerFlushScheduler
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : TelemetryFlushScheduler {
        override fun schedule() {
            val request =
                OneTimeWorkRequestBuilder<TelemetryFlushWorker>()
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_S, TimeUnit.SECONDS)
                    .build()
            WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.KEEP, request)
        }

        override fun cancel() {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NAME)
        }

        private companion object {
            const val UNIQUE_NAME = "telemetry_flush"
            const val BACKOFF_S = 30L
        }
    }
