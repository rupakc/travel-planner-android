package io.github.rupakc.travelplanner.core.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.rupakc.travelplanner.core.data.impl.PrepRepositoryImpl
import io.github.rupakc.travelplanner.core.data.repository.PrepRepository

/** Prep checklist binding (kept apart from [DataModule], a shared hotspot). */
@Module
@InstallIn(SingletonComponent::class)
abstract class PrepDataModule {
    @Binds abstract fun prep(impl: PrepRepositoryImpl): PrepRepository
}
