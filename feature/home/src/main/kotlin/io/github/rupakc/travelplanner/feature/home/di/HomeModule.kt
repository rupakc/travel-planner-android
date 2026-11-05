package io.github.rupakc.travelplanner.feature.home.di

import dagger.Binds
import dagger.BindsOptionalOf
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.rupakc.travelplanner.core.domain.feature.FeatureCatalogue
import io.github.rupakc.travelplanner.feature.home.search.ResourceSearchLabels
import io.github.rupakc.travelplanner.feature.home.search.SearchLabels

@Module
@InstallIn(SingletonComponent::class)
abstract class HomeModule {
    @Binds abstract fun searchLabels(impl: ResourceSearchLabels): SearchLabels

    /** Implemented and bound by the onboarding team; Home works (explicit toggles only) until it is. */
    @BindsOptionalOf abstract fun featureCatalogue(): FeatureCatalogue
}
