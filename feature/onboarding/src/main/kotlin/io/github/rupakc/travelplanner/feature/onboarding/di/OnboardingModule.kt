package io.github.rupakc.travelplanner.feature.onboarding.di

import dagger.BindsOptionalOf
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.rupakc.travelplanner.core.data.repository.AccountRepository

/** Declares [AccountRepository] optional: onboarding stays local-only until the account area binds it. */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class OnboardingModule {
    @BindsOptionalOf
    abstract fun accountRepository(): AccountRepository
}
