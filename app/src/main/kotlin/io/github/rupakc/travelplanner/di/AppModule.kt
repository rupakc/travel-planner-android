package io.github.rupakc.travelplanner.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.rupakc.travelplanner.BuildConfig
import io.github.rupakc.travelplanner.core.common.AppBuildInfo
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton
    fun buildInfo(): AppBuildInfo = AppBuildInfo(
        defaultBaseUrl = BuildConfig.DEFAULT_BASE_URL,
        flavor = BuildConfig.FLAVOR,
        versionName = BuildConfig.VERSION_NAME,
        debug = BuildConfig.DEBUG,
        cleartextAllowed = BuildConfig.CLEARTEXT_ALLOWED,
        webBaseUrl = BuildConfig.WEB_BASE_URL,
    )
}
