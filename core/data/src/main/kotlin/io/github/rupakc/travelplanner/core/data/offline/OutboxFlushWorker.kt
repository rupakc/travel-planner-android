package io.github.rupakc.travelplanner.core.data.offline

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.github.rupakc.travelplanner.core.data.repository.Outbox

/** Flushes the signed-in user's outbox; retries (with backoff, a few times) while requests are still waiting. */
@HiltWorker
internal class OutboxFlushWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted params: WorkerParameters,
        private val outbox: Outbox,
    ) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            val result = outbox.flush()
            return if (result.failed > 0 && runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.success()
        }

        private companion object {
            const val MAX_ATTEMPTS = 3
        }
    }
