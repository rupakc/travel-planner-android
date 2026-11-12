package io.github.rupakc.travelplanner.core.network.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.rupakc.travelplanner.core.network.api.PlayApi
import io.github.rupakc.travelplanner.core.network.api.SocialApi
import javax.inject.Singleton
import retrofit2.Retrofit

/** Phase 5 APIs (social and play, no AI behind either) on the regular JSON client; kept apart from [NetworkModule]. */
@Module
@InstallIn(SingletonComponent::class)
object SocialNetworkModule {
    @Provides @Singleton
    fun socialApi(
        @DefaultHttp r: Retrofit,
    ): SocialApi = r.create(SocialApi::class.java)

    @Provides @Singleton
    fun playApi(
        @DefaultHttp r: Retrofit,
    ): PlayApi = r.create(PlayApi::class.java)
}
