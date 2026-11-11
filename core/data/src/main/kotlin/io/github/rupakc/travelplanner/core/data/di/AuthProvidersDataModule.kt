package io.github.rupakc.travelplanner.core.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.rupakc.travelplanner.core.data.impl.AuthProvidersRepositoryImpl
import io.github.rupakc.travelplanner.core.data.repository.AuthProvidersRepository

/** Social sign-in provider detection (kept apart from [DataModule]). */
@Module
@InstallIn(SingletonComponent::class)
abstract class AuthProvidersDataModule {
    @Binds abstract fun authProviders(impl: AuthProvidersRepositoryImpl): AuthProvidersRepository
}
