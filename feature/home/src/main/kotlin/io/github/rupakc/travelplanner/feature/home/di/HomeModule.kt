package io.github.rupakc.travelplanner.feature.home.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.rupakc.travelplanner.feature.home.search.ResourceSearchLabels
import io.github.rupakc.travelplanner.feature.home.search.SearchLabels

@Module
@InstallIn(SingletonComponent::class)
abstract class HomeModule {
    @Binds abstract fun searchLabels(impl: ResourceSearchLabels): SearchLabels
}
