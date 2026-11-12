package io.github.rupakc.travelplanner.feature.account.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.rupakc.travelplanner.feature.account.CacheExportTarget
import io.github.rupakc.travelplanner.feature.account.ExportTarget

@Module
@InstallIn(SingletonComponent::class)
internal abstract class AccountModule {
    @Binds abstract fun exportTarget(impl: CacheExportTarget): ExportTarget
}
