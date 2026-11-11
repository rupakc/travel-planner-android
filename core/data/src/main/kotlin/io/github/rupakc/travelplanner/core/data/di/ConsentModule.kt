package io.github.rupakc.travelplanner.core.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.rupakc.travelplanner.core.data.telemetry.ClientConfigRepository
import io.github.rupakc.travelplanner.core.data.telemetry.ClientConfigRepositoryImpl
import io.github.rupakc.travelplanner.core.data.telemetry.ConsentRepository
import io.github.rupakc.travelplanner.core.data.telemetry.ConsentRepositoryImpl

@Module
@InstallIn(SingletonComponent::class)
abstract class ConsentModule {
    @Binds abstract fun consent(impl: ConsentRepositoryImpl): ConsentRepository

    @Binds abstract fun clientConfig(impl: ClientConfigRepositoryImpl): ClientConfigRepository
}
