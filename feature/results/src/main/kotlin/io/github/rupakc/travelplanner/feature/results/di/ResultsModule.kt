package io.github.rupakc.travelplanner.feature.results.di

import dagger.BindsOptionalOf
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.rupakc.travelplanner.core.domain.feature.FeatureCatalogue

/**
 * The feature catalogue is implemented by the onboarding/settings team; until it
 * is bound, results keep the web order with every section expanded.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ResultsModule {
    @BindsOptionalOf
    abstract fun featureCatalogue(): FeatureCatalogue
}
