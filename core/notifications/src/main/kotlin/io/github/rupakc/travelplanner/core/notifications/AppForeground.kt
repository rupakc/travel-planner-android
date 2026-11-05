package io.github.rupakc.travelplanner.core.notifications

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * Suspends until this process shows UI. A process started only for a worker, an FCM message or a
 * broadcast never gets there, so network work gated on it (device registration, `/health` refresh)
 * is skipped in those starts (performance review I1).
 */
fun interface AppForeground {
    suspend fun awaitForeground()
}

@Singleton
class ProcessAppForeground
    @Inject
    constructor() : AppForeground {
        override suspend fun awaitForeground() {
            ProcessLifecycleOwner.get().lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.STARTED) }
        }
    }

@Module
@InstallIn(SingletonComponent::class)
abstract class AppForegroundModule {
    @Binds abstract fun foreground(impl: ProcessAppForeground): AppForeground
}
