package io.github.rupakc.travelplanner.feature.settings.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.rupakc.travelplanner.feature.settings.root.AppLocales
import io.github.rupakc.travelplanner.feature.settings.root.SystemAppLocales

@Module
@InstallIn(SingletonComponent::class)
abstract class SettingsModule {
    @Binds abstract fun appLocales(impl: SystemAppLocales): AppLocales
}
