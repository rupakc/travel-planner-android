package io.github.rupakc.travelplanner.core.data.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.rupakc.travelplanner.core.data.profile.FeatureVisibility
import io.github.rupakc.travelplanner.core.data.profile.ProfileFeatureVisibility
import io.github.rupakc.travelplanner.core.domain.feature.FeatureCatalogue
import io.github.rupakc.travelplanner.core.domain.profile.DefaultFeatureCatalogue
import io.github.rupakc.travelplanner.core.domain.profile.DefaultTravellerClassifier
import io.github.rupakc.travelplanner.core.domain.traveller.TravellerClassifier

/**
 * App-wide bindings for the pure profile domain (core:domain has no DI), bound once here so every feature
 * injects the same [FeatureCatalogue] / [TravellerClassifier] / [FeatureVisibility] (architecture review I3:
 * no optional bindings, no per-screen fallbacks). Bind them nowhere else.
 */
@Module
@InstallIn(SingletonComponent::class)
object ProfileDomainModule {
    @Provides
    fun featureCatalogue(): FeatureCatalogue = DefaultFeatureCatalogue

    @Provides
    fun travellerClassifier(): TravellerClassifier = DefaultTravellerClassifier

    @Provides
    fun featureVisibility(impl: ProfileFeatureVisibility): FeatureVisibility = impl
}
