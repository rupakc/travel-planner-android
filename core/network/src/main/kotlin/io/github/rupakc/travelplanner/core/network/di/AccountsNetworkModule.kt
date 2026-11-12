package io.github.rupakc.travelplanner.core.network.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.rupakc.travelplanner.core.network.api.AccountApi
import io.github.rupakc.travelplanner.core.network.api.GameApi
import io.github.rupakc.travelplanner.core.network.api.OauthApi
import io.github.rupakc.travelplanner.core.network.api.SignupApi
import io.github.rupakc.travelplanner.core.network.api.TripsApi
import javax.inject.Singleton
import retrofit2.Retrofit

/** Phase 1/2 APIs on the regular JSON client (kept apart from [NetworkModule]). */
@Module
@InstallIn(SingletonComponent::class)
object AccountsNetworkModule {
    @Provides @Singleton
    fun accountApi(
        @DefaultHttp r: Retrofit,
    ): AccountApi = r.create(AccountApi::class.java)

    @Provides @Singleton
    fun signupApi(
        @DefaultHttp r: Retrofit,
    ): SignupApi = r.create(SignupApi::class.java)

    @Provides @Singleton
    fun oauthApi(
        @DefaultHttp r: Retrofit,
    ): OauthApi = r.create(OauthApi::class.java)

    @Provides @Singleton
    fun gameApi(
        @DefaultHttp r: Retrofit,
    ): GameApi = r.create(GameApi::class.java)

    @Provides @Singleton
    fun tripsApi(
        @DefaultHttp r: Retrofit,
    ): TripsApi = r.create(TripsApi::class.java)
}
