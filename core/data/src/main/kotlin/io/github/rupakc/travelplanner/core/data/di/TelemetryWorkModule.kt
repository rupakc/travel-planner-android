package io.github.rupakc.travelplanner.core.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.rupakc.travelplanner.core.data.telemetry.TelemetryFlushScheduler
import io.github.rupakc.travelplanner.core.data.telemetry.TelemetryQueueFile
import io.github.rupakc.travelplanner.core.data.telemetry.TelemetryQueueStore
import io.github.rupakc.travelplanner.core.data.telemetry.TelemetrySender
import io.github.rupakc.travelplanner.core.data.telemetry.TelemetryUploader
import io.github.rupakc.travelplanner.core.data.telemetry.WorkManagerFlushScheduler

/** Binds the telemetry delivery pieces (uploader, queue file, WorkManager scheduler). */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class TelemetryWorkModule {
    @Binds abstract fun sender(impl: TelemetryUploader): TelemetrySender

    @Binds abstract fun queueStore(impl: TelemetryQueueFile): TelemetryQueueStore

    @Binds abstract fun scheduler(impl: WorkManagerFlushScheduler): TelemetryFlushScheduler
}
