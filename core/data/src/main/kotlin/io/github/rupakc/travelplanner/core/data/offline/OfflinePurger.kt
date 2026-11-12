package io.github.rupakc.travelplanner.core.data.offline

import io.github.rupakc.travelplanner.core.data.repository.UserStateCleaner
import io.github.rupakc.travelplanner.core.database.OfflinePackDao
import io.github.rupakc.travelplanner.core.database.OutboxDao
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Deletes every offline pack and queued write on this device. Contributed to the session wipe as a
 * [UserStateCleaner], so sign-out, `session_revoked`, account deletion, a different user signing in and a
 * server change all purge them. Packs are per user, and the next account must never see or send them.
 */
@Singleton
internal class OfflinePurger
    @Inject
    constructor(
        private val packs: OfflinePackDao,
        private val outbox: OutboxDao,
        private val scheduler: OutboxFlushScheduler,
    ) : UserStateCleaner {
        override suspend fun clear() {
            packs.clear()
            outbox.clear()
            scheduler.cancelPeriodic()
        }
    }
