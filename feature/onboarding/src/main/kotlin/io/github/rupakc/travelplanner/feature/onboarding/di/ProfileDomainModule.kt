package io.github.rupakc.travelplanner.feature.onboarding.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.rupakc.travelplanner.core.domain.feature.FeatureCatalogue
import io.github.rupakc.travelplanner.core.domain.profile.DefaultFeatureCatalogue
import io.github.rupakc.travelplanner.core.domain.profile.DefaultTravellerClassifier
import io.github.rupakc.travelplanner.core.domain.traveller.TravellerClassifier

/**
 * App-wide bindings for the pure profile domain (core:domain has no DI). Installed in the
 * singleton component, so Home and Settings inject [FeatureCatalogue] / [TravellerClassifier] too.
 * Bind them nowhere else (Hilt rejects duplicate bindings).
 */
@Module
@InstallIn(SingletonComponent::class)
object ProfileDomainModule {
    @Provides
    fun featureCatalogue(): FeatureCatalogue = DefaultFeatureCatalogue

    @Provides
    fun travellerClassifier(): TravellerClassifier = DefaultTravellerClassifier
}
