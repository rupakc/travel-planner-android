package io.github.rupakc.travelplanner.core.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.rupakc.travelplanner.core.data.impl.JournalRepositoryImpl
import io.github.rupakc.travelplanner.core.data.repository.JournalRepository

/** Journal and place binding (kept apart from [DataModule], a shared hotspot). */
@Module
@InstallIn(SingletonComponent::class)
abstract class JournalDataModule {
    @Binds abstract fun journal(impl: JournalRepositoryImpl): JournalRepository
}
