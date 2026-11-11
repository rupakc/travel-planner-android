package io.github.rupakc.travelplanner.core.data.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.rupakc.travelplanner.core.data.telemetry.Telemetry
import io.github.rupakc.travelplanner.core.data.telemetry.TelemetryEngine
import io.github.rupakc.travelplanner.core.data.telemetry.TelemetryLifecycle
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/** The single-threaded dispatcher that confines all telemetry state (`Dispatchers.IO.limitedParallelism(1)`). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class TelemetryConfinement

@Module
@InstallIn(SingletonComponent::class)
internal abstract class TelemetryModule {
    @Binds
    abstract fun telemetry(engine: TelemetryEngine): Telemetry

    @Binds
    abstract fun telemetryLifecycle(engine: TelemetryEngine): TelemetryLifecycle

    companion object {
        @Provides @Singleton @TelemetryConfinement
        fun confinement(): CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1)
    }
}
