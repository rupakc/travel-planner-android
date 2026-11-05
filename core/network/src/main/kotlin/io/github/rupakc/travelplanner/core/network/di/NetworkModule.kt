package io.github.rupakc.travelplanner.core.network.di

import io.github.rupakc.travelplanner.core.common.AppBuildInfo
import io.github.rupakc.travelplanner.core.common.AppClock
import io.github.rupakc.travelplanner.core.common.AppDispatchers
import io.github.rupakc.travelplanner.core.common.DefaultAppDispatchers
import io.github.rupakc.travelplanner.core.common.SystemAppClock
import io.github.rupakc.travelplanner.core.model.json.TravelJson
import io.github.rupakc.travelplanner.core.network.AuthInterceptor
import io.github.rupakc.travelplanner.core.network.BaseUrlInterceptor
import io.github.rupakc.travelplanner.core.network.PLACEHOLDER_BASE_URL
import io.github.rupakc.travelplanner.core.network.api.AdminApi
import io.github.rupakc.travelplanner.core.network.api.AuthApi
import io.github.rupakc.travelplanner.core.network.api.DevicesApi
import io.github.rupakc.travelplanner.core.network.api.FareWatchApi
import io.github.rupakc.travelplanner.core.network.api.FeedbackApi
import io.github.rupakc.travelplanner.core.network.api.HealthApi
import io.github.rupakc.travelplanner.core.network.api.ImportApi
import io.github.rupakc.travelplanner.core.network.api.NearbyApi
import io.github.rupakc.travelplanner.core.network.api.NotificationsApi
import io.github.rupakc.travelplanner.core.network.api.PlansApi
import io.github.rupakc.travelplanner.core.network.api.PreferencesApi
import io.github.rupakc.travelplanner.core.network.api.PriceWatchApi
import io.github.rupakc.travelplanner.core.network.api.ReferenceApi
import io.github.rupakc.travelplanner.core.network.api.SearchApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.time.Duration
import javax.inject.Qualifier
import javax.inject.Singleton

/** Regular JSON calls: 30 s read timeout. */
@Qualifier @Retention(AnnotationRetention.BINARY)
annotation class DefaultHttp

/** LLM-backed calls (single agents, discover, compare, import, nearby, watch checks): 300 s read timeout (agents may take 240 s). */
@Qualifier @Retention(AnnotationRetention.BINARY)
annotation class AiHttp

/** SSE: no read or call timeout. */
@Qualifier @Retention(AnnotationRetention.BINARY)
annotation class StreamingHttp

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    private const val CONNECT_SECONDS = 15L
    private const val READ_SECONDS = 30L
    private const val AI_READ_SECONDS = 300L

    @Provides @Singleton
    fun json(): Json = TravelJson

    @Provides @Singleton
    fun dispatchers(): AppDispatchers = DefaultAppDispatchers

    @Provides @Singleton
    fun clock(): AppClock = SystemAppClock()

    @Provides @Singleton @DefaultHttp
    fun okHttp(baseUrl: BaseUrlInterceptor, auth: AuthInterceptor, buildInfo: AppBuildInfo): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(Duration.ofSeconds(CONNECT_SECONDS))
            .readTimeout(Duration.ofSeconds(READ_SECONDS))
            .addInterceptor(baseUrl)
            .addInterceptor(auth)
            .apply {
                if (buildInfo.debug) {
                    addInterceptor(
                        HttpLoggingInterceptor().apply {
                            level = HttpLoggingInterceptor.Level.BASIC
                            redactHeader(AuthInterceptor.HEADER)
                        },
                    )
                }
            }
            .build()

    @Provides @Singleton @AiHttp
    fun aiOkHttp(@DefaultHttp base: OkHttpClient): OkHttpClient =
        base.newBuilder().readTimeout(Duration.ofSeconds(AI_READ_SECONDS)).build()

    @Provides @Singleton @StreamingHttp
    fun streamingOkHttp(@DefaultHttp base: OkHttpClient): OkHttpClient =
        base.newBuilder().readTimeout(Duration.ZERO).callTimeout(Duration.ZERO).build()

    private fun retrofit(client: OkHttpClient, json: Json): Retrofit = Retrofit.Builder()
        .baseUrl(PLACEHOLDER_BASE_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides @Singleton @DefaultHttp
    fun defaultRetrofit(@DefaultHttp client: OkHttpClient, json: Json): Retrofit = retrofit(client, json)

    @Provides @Singleton @AiHttp
    fun aiRetrofit(@AiHttp client: OkHttpClient, json: Json): Retrofit = retrofit(client, json)

    @Provides @Singleton fun authApi(@DefaultHttp r: Retrofit): AuthApi = r.create(AuthApi::class.java)

    @Provides @Singleton fun searchApi(@AiHttp r: Retrofit): SearchApi = r.create(SearchApi::class.java)

    @Provides @Singleton fun referenceApi(@DefaultHttp r: Retrofit): ReferenceApi = r.create(ReferenceApi::class.java)

    @Provides @Singleton fun plansApi(@DefaultHttp r: Retrofit): PlansApi = r.create(PlansApi::class.java)

    @Provides @Singleton fun preferencesApi(@DefaultHttp r: Retrofit): PreferencesApi = r.create(PreferencesApi::class.java)

    @Provides @Singleton fun fareWatchApi(@AiHttp r: Retrofit): FareWatchApi = r.create(FareWatchApi::class.java)

    @Provides @Singleton fun priceWatchApi(@AiHttp r: Retrofit): PriceWatchApi = r.create(PriceWatchApi::class.java)

    @Provides @Singleton fun notificationsApi(@DefaultHttp r: Retrofit): NotificationsApi = r.create(NotificationsApi::class.java)

    @Provides @Singleton fun importApi(@AiHttp r: Retrofit): ImportApi = r.create(ImportApi::class.java)

    @Provides @Singleton fun devicesApi(@DefaultHttp r: Retrofit): DevicesApi = r.create(DevicesApi::class.java)

    @Provides @Singleton fun nearbyApi(@AiHttp r: Retrofit): NearbyApi = r.create(NearbyApi::class.java)

    @Provides @Singleton fun feedbackApi(@DefaultHttp r: Retrofit): FeedbackApi = r.create(FeedbackApi::class.java)

    @Provides @Singleton fun adminApi(@DefaultHttp r: Retrofit): AdminApi = r.create(AdminApi::class.java)

    @Provides @Singleton fun healthApi(@DefaultHttp r: Retrofit): HealthApi = r.create(HealthApi::class.java)
}
