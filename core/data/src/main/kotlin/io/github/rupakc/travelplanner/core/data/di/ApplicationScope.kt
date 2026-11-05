package io.github.rupakc.travelplanner.core.data.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.rupakc.travelplanner.core.common.AppDispatchers
import io.github.rupakc.travelplanner.core.common.AppLogger
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

/** Process-wide scope for work that must outlive a screen (the running search, draft persistence). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object ApplicationScopeModule {
    @Provides @Singleton @ApplicationScope
    fun applicationScope(dispatchers: AppDispatchers): CoroutineScope = CoroutineScope(SupervisorJob() + dispatchers.default + LoggingExceptionHandler)

    /** An uncaught failure in background work is logged, never a process crash (review I7). */
    val LoggingExceptionHandler: CoroutineExceptionHandler =
        CoroutineExceptionHandler { _, throwable -> AppLogger.e("ApplicationScope", "Uncaught exception in background work", throwable) }
}
