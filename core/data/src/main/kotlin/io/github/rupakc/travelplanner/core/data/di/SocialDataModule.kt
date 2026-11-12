package io.github.rupakc.travelplanner.core.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.rupakc.travelplanner.core.data.impl.PlayRepositoryImpl
import io.github.rupakc.travelplanner.core.data.impl.SocialRepositoryImpl
import io.github.rupakc.travelplanner.core.data.repository.PlayRepository
import io.github.rupakc.travelplanner.core.data.repository.SocialRepository

/** Social and Play bindings (kept apart from [DataModule], a shared hotspot). Nothing is persisted, so no cleaner is needed. */
@Module
@InstallIn(SingletonComponent::class)
abstract class SocialDataModule {
    @Binds abstract fun social(impl: SocialRepositoryImpl): SocialRepository

    @Binds abstract fun play(impl: PlayRepositoryImpl): PlayRepository
}
