package io.github.rupakc.travelplanner.core.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.rupakc.travelplanner.core.data.impl.AccountRepositoryImpl
import io.github.rupakc.travelplanner.core.data.repository.AccountRepository

/** Binds the account area; modules that declared it optional (onboarding) now see it present. */
@Module
@InstallIn(SingletonComponent::class)
abstract class AccountDataModule {
    @Binds abstract fun account(impl: AccountRepositoryImpl): AccountRepository
}
