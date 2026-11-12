package io.github.rupakc.travelplanner.core.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.rupakc.travelplanner.core.data.impl.RecapRepositoryImpl
import io.github.rupakc.travelplanner.core.data.repository.RecapRepository

/** Recap binding (kept apart from [DataModule], a shared hotspot). */
@Module
@InstallIn(SingletonComponent::class)
abstract class RecapDataModule {
    @Binds abstract fun recap(impl: RecapRepositoryImpl): RecapRepository
}
