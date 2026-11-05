package io.github.rupakc.travelplanner.feature.search.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.rupakc.travelplanner.feature.search.importlink.ContentResolverScreenshotReader
import io.github.rupakc.travelplanner.feature.search.importlink.ScreenshotReader

@Module
@InstallIn(SingletonComponent::class)
internal interface SearchModule {
    @Binds
    fun screenshotReader(impl: ContentResolverScreenshotReader): ScreenshotReader
}
