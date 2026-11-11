package io.github.rupakc.travelplanner.core.network.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.rupakc.travelplanner.core.network.api.ClientConfigApi
import io.github.rupakc.travelplanner.core.network.api.TelemetryApi
import javax.inject.Singleton
import retrofit2.Retrofit

/** Config and telemetry APIs on the default client: auth, the base URL swap and log redaction apply. */
@Module
@InstallIn(SingletonComponent::class)
object TelemetryNetworkModule {
    @Provides @Singleton
    fun clientConfigApi(
        @DefaultHttp r: Retrofit,
    ): ClientConfigApi = r.create(ClientConfigApi::class.java)

    @Provides @Singleton
    fun telemetryApi(
        @DefaultHttp r: Retrofit,
    ): TelemetryApi = r.create(TelemetryApi::class.java)
}
