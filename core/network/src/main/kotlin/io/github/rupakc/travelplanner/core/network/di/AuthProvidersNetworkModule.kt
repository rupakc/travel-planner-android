package io.github.rupakc.travelplanner.core.network.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.rupakc.travelplanner.core.network.api.AuthProvidersApi
import javax.inject.Singleton
import retrofit2.Retrofit

/** `GET /api/auth/providers` on the regular JSON client (kept apart from [NetworkModule], which another change owns). */
@Module
@InstallIn(SingletonComponent::class)
object AuthProvidersNetworkModule {
    @Provides @Singleton
    fun authProvidersApi(
        @DefaultHttp r: Retrofit,
    ): AuthProvidersApi = r.create(AuthProvidersApi::class.java)
}
