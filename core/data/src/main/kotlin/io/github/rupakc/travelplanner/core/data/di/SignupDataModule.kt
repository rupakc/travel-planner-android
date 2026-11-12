package io.github.rupakc.travelplanner.core.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.rupakc.travelplanner.core.data.impl.AuthConfigRepositoryImpl
import io.github.rupakc.travelplanner.core.data.impl.SignupRepositoryImpl
import io.github.rupakc.travelplanner.core.data.repository.AuthConfigRepository
import io.github.rupakc.travelplanner.core.data.repository.SignupRepository

/** Native sign-up / verify / reset and the auth switches of `GET /api/config`. */
@Module
@InstallIn(SingletonComponent::class)
abstract class SignupDataModule {
    @Binds abstract fun signup(impl: SignupRepositoryImpl): SignupRepository

    @Binds abstract fun authConfig(impl: AuthConfigRepositoryImpl): AuthConfigRepository
}
