package io.github.rupakc.travelplanner.core.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.rupakc.travelplanner.core.data.impl.JourneyFlagsRepositoryImpl
import io.github.rupakc.travelplanner.core.data.repository.JourneyFlagsRepository

/** The Phase 1/2 feature gates (`GET /api/config`) used by results, My Plan and chat. */
@Module
@InstallIn(SingletonComponent::class)
abstract class PlanJourneyModule {
    @Binds abstract fun journeyFlags(impl: JourneyFlagsRepositoryImpl): JourneyFlagsRepository
}
