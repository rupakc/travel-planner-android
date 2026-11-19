package io.github.rupakc.travelplanner.core.network.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.rupakc.travelplanner.core.network.api.CarbonApi
import javax.inject.Singleton
import retrofit2.Retrofit

/** Trip carbon footprint (pure computation, no AI) on the regular JSON client; kept apart from [NetworkModule]. */
@Module
@InstallIn(SingletonComponent::class)
object CarbonNetworkModule {
    @Provides @Singleton
    fun carbonApi(
        @DefaultHttp r: Retrofit,
    ): CarbonApi = r.create(CarbonApi::class.java)
}
