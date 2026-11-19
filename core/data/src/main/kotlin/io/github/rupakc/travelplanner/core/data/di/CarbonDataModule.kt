package io.github.rupakc.travelplanner.core.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.rupakc.travelplanner.core.data.impl.CarbonRepositoryImpl
import io.github.rupakc.travelplanner.core.data.repository.CarbonRepository

/** Trip carbon footprint bindings (kept apart from [DataModule], a shared hotspot). */
@Module
@InstallIn(SingletonComponent::class)
abstract class CarbonDataModule {
    @Binds abstract fun carbon(impl: CarbonRepositoryImpl): CarbonRepository
}
