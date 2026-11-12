package io.github.rupakc.travelplanner.core.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import io.github.rupakc.travelplanner.core.data.offline.NetworkOutboxSender
import io.github.rupakc.travelplanner.core.data.offline.OfflinePackRepositoryImpl
import io.github.rupakc.travelplanner.core.data.offline.OfflinePurger
import io.github.rupakc.travelplanner.core.data.offline.OutboxFlushScheduler
import io.github.rupakc.travelplanner.core.data.offline.OutboxImpl
import io.github.rupakc.travelplanner.core.data.offline.OutboxSender
import io.github.rupakc.travelplanner.core.data.offline.WorkManagerOutboxScheduler
import io.github.rupakc.travelplanner.core.data.repository.OfflinePackRepository
import io.github.rupakc.travelplanner.core.data.repository.Outbox
import io.github.rupakc.travelplanner.core.data.repository.UserStateCleaner

/** Offline pack, outbox and their background flush. The purger joins the session wipe through [UserStateCleaner]. */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class OfflineModule {
    @Binds abstract fun offlinePacks(impl: OfflinePackRepositoryImpl): OfflinePackRepository

    @Binds abstract fun outbox(impl: OutboxImpl): Outbox

    @Binds abstract fun sender(impl: NetworkOutboxSender): OutboxSender

    @Binds abstract fun scheduler(impl: WorkManagerOutboxScheduler): OutboxFlushScheduler

    @Binds @IntoSet
    abstract fun purger(impl: OfflinePurger): UserStateCleaner
}
