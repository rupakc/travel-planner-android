package io.github.rupakc.travelplanner.core.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.rupakc.travelplanner.core.data.impl.OauthRepositoryImpl
import io.github.rupakc.travelplanner.core.data.repository.OauthRepository

/** Social sign-in hand-off. */
@Module
@InstallIn(SingletonComponent::class)
abstract class OauthDataModule {
    @Binds abstract fun oauth(impl: OauthRepositoryImpl): OauthRepository
}
