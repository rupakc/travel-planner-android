package io.github.rupakc.travelplanner.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.rupakc.travelplanner.core.common.AppLanguage
import io.github.rupakc.travelplanner.i18n.AppCompatAppLanguage

@Module
@InstallIn(SingletonComponent::class)
abstract class LanguageModule {
    @Binds
    abstract fun appLanguage(impl: AppCompatAppLanguage): AppLanguage
}
