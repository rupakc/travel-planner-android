package io.github.rupakc.travelplanner.core.network.di

import dagger.Lazy
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.rupakc.travelplanner.core.common.AppBuildInfo
import io.github.rupakc.travelplanner.core.common.AppClock
import io.github.rupakc.travelplanner.core.common.AppDispatchers
import io.github.rupakc.travelplanner.core.common.DefaultAppDispatchers
import io.github.rupakc.travelplanner.core.common.SystemAppClock
import io.github.rupakc.travelplanner.core.model.json.TravelJson
import io.github.rupakc.travelplanner.core.network.AiLocaleInterceptor
import io.github.rupakc.travelplanner.core.network.ApiRefreshCall
import io.github.rupakc.travelplanner.core.network.AuthInterceptor
import io.github.rupakc.travelplanner.core.network.BaseUrlInterceptor
import io.github.rupakc.travelplanner.core.network.PLACEHOLDER_BASE_URL
import io.github.rupakc.travelplanner.core.network.RedactingHttpLogger
import io.github.rupakc.travelplanner.core.network.RefreshCall
import io.github.rupakc.travelplanner.core.network.api.AccountApi
import io.github.rupakc.travelplanner.core.network.api.AdminApi
import io.github.rupakc.travelplanner.core.network.api.AlertsV2Api
import io.github.rupakc.travelplanner.core.network.api.AuthApi
import io.github.rupakc.travelplanner.core.network.api.DevicesApi
import io.github.rupakc.travelplanner.core.network.api.FareWatchApi
import io.github.rupakc.travelplanner.core.network.api.FeedbackApi
import io.github.rupakc.travelplanner.core.network.api.HealthApi
import io.github.rupakc.travelplanner.core.network.api.ImportApi
import io.github.rupakc.travelplanner.core.network.api.LiveCheckApi
import io.github.rupakc.travelplanner.core.network.api.NearbyApi
import io.github.rupakc.travelplanner.core.network.api.NotificationCatalogApi
import io.github.rupakc.travelplanner.core.network.api.NotificationSettingsApi
import io.github.rupakc.travelplanner.core.network.api.NotificationsApi
import io.github.rupakc.travelplanner.core.network.api.PlansApi
import io.github.rupakc.travelplanner.core.network.api.PreferencesApi
import io.github.rupakc.travelplanner.core.network.api.PriceWatchApi
import io.github.rupakc.travelplanner.core.network.api.ReferenceApi
import io.github.rupakc.travelplanner.core.network.api.SearchApi
import io.github.rupakc.travelplanner.core.network.api.SharesApi
import java.time.Duration
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/** Regular JSON calls: 30 s read timeout. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultHttp

/** LLM-backed calls (single agents, discover, compare, import, nearby, watch checks): 300 s read timeout (agents may take 240 s). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AiHttp

/** SSE: 300 s idle read timeout ([NetworkModule.STREAM_IDLE_SECONDS]), no call timeout. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class StreamingHttp

/** Token refresh only: base-URL rewriting, no bearer, no auth recovery (so a refresh can never recurse). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class RefreshHttp

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    private const val CONNECT_SECONDS = 15L
    private const val READ_SECONDS = 30L
    private const val AI_READ_SECONDS = 300L

    /**
     * SSE idle read timeout: above the backend's longest silent phase (240 s per agent, no keepalives in
     * Phase 1), so a half-open connection ends as Interrupted (retried once) instead of hanging (review I2).
     */
    const val STREAM_IDLE_SECONDS = 300L

    @Provides @Singleton
    fun json(): Json = TravelJson

    @Provides @Singleton
    fun dispatchers(): AppDispatchers = DefaultAppDispatchers

    @Provides @Singleton
    fun clock(): AppClock = SystemAppClock()

    @Provides @Singleton @DefaultHttp
    fun okHttp(
        baseUrl: BaseUrlInterceptor,
        auth: AuthInterceptor,
        aiLocale: AiLocaleInterceptor,
        buildInfo: AppBuildInfo,
    ): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(Duration.ofSeconds(CONNECT_SECONDS))
            .readTimeout(Duration.ofSeconds(READ_SECONDS))
            // Auth first: it must see the placeholder host to scope the token to our backend (review I3).
            .addInterceptor(auth)
            // Adds `locale` to AI bodies; before baseUrl so the host is still the placeholder. @AiHttp and
            // @StreamingHttp derive from this client, so all three carry it.
            .addInterceptor(aiLocale)
            .addInterceptor(baseUrl)
            .apply {
                if (buildInfo.debug) {
                    addInterceptor(
                        // Share tokens and token-like path segments are masked (security review S-M3).
                        HttpLoggingInterceptor(RedactingHttpLogger()).apply {
                            level = HttpLoggingInterceptor.Level.BASIC
                            redactHeader(AuthInterceptor.HEADER)
                        },
                    )
                }
            }
            .build()

    @Provides @Singleton @RefreshHttp
    fun refreshOkHttp(baseUrl: BaseUrlInterceptor): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(Duration.ofSeconds(CONNECT_SECONDS))
            .readTimeout(Duration.ofSeconds(READ_SECONDS))
            .addInterceptor(baseUrl)
            .build()

    @Provides @Singleton
    fun refreshCall(
        @RefreshHttp client: Lazy<OkHttpClient>,
        json: Json,
    ): RefreshCall = ApiRefreshCall(retrofit(client, json).create(AccountApi::class.java))

    @Provides @Singleton @AiHttp
    fun aiOkHttp(
        @DefaultHttp base: OkHttpClient,
    ): OkHttpClient =
        base.newBuilder().readTimeout(Duration.ofSeconds(AI_READ_SECONDS)).build()

    @Provides @Singleton @StreamingHttp
    fun streamingOkHttp(
        @DefaultHttp base: OkHttpClient,
    ): OkHttpClient =
        base.newBuilder().readTimeout(Duration.ofSeconds(STREAM_IDLE_SECONDS)).callTimeout(Duration.ZERO).build()

    /**
     * The client is [Lazy] (performance M1): OkHttp (platform + TLS init) is built on the first call,
     * which runs off the main thread, not when the API graph is injected.
     */
    private fun retrofit(client: Lazy<OkHttpClient>, json: Json): Retrofit =
        Retrofit.Builder()
            .baseUrl(PLACEHOLDER_BASE_URL)
            .callFactory { request -> client.get().newCall(request) }
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides @Singleton @DefaultHttp
    fun defaultRetrofit(
        @DefaultHttp client: Lazy<OkHttpClient>,
        json: Json,
    ): Retrofit = retrofit(client, json)

    @Provides @Singleton @AiHttp
    fun aiRetrofit(
        @AiHttp client: Lazy<OkHttpClient>,
        json: Json,
    ): Retrofit = retrofit(client, json)

    @Provides @Singleton
    fun authApi(
        @DefaultHttp r: Retrofit,
    ): AuthApi = r.create(AuthApi::class.java)

    @Provides @Singleton
    fun searchApi(
        @AiHttp r: Retrofit,
    ): SearchApi = r.create(SearchApi::class.java)

    @Provides @Singleton
    fun referenceApi(
        @DefaultHttp r: Retrofit,
    ): ReferenceApi = r.create(ReferenceApi::class.java)

    @Provides @Singleton
    fun plansApi(
        @DefaultHttp r: Retrofit,
    ): PlansApi = r.create(PlansApi::class.java)

    @Provides @Singleton
    fun preferencesApi(
        @DefaultHttp r: Retrofit,
    ): PreferencesApi = r.create(PreferencesApi::class.java)

    @Provides @Singleton
    fun fareWatchApi(
        @AiHttp r: Retrofit,
    ): FareWatchApi = r.create(FareWatchApi::class.java)

    @Provides @Singleton
    fun sharesApi(
        @DefaultHttp r: Retrofit,
    ): SharesApi = r.create(SharesApi::class.java)

    @Provides @Singleton
    fun alertsV2Api(
        @AiHttp r: Retrofit,
    ): AlertsV2Api = r.create(AlertsV2Api::class.java)

    @Provides @Singleton
    fun notificationSettingsApi(
        @DefaultHttp r: Retrofit,
    ): NotificationSettingsApi = r.create(NotificationSettingsApi::class.java)

    @Provides @Singleton
    fun notificationCatalogApi(
        @DefaultHttp r: Retrofit,
    ): NotificationCatalogApi = r.create(NotificationCatalogApi::class.java)

    @Provides @Singleton
    fun priceWatchApi(
        @AiHttp r: Retrofit,
    ): PriceWatchApi = r.create(PriceWatchApi::class.java)

    @Provides @Singleton
    fun notificationsApi(
        @DefaultHttp r: Retrofit,
    ): NotificationsApi = r.create(NotificationsApi::class.java)

    @Provides @Singleton
    fun importApi(
        @AiHttp r: Retrofit,
    ): ImportApi = r.create(ImportApi::class.java)

    @Provides @Singleton
    fun devicesApi(
        @DefaultHttp r: Retrofit,
    ): DevicesApi = r.create(DevicesApi::class.java)

    @Provides @Singleton
    fun nearbyApi(
        @AiHttp r: Retrofit,
    ): NearbyApi = r.create(NearbyApi::class.java)

    @Provides @Singleton
    fun liveCheckApi(
        @AiHttp r: Retrofit,
    ): LiveCheckApi = r.create(LiveCheckApi::class.java)

    @Provides @Singleton
    fun feedbackApi(
        @DefaultHttp r: Retrofit,
    ): FeedbackApi = r.create(FeedbackApi::class.java)

    @Provides @Singleton
    fun adminApi(
        @DefaultHttp r: Retrofit,
    ): AdminApi = r.create(AdminApi::class.java)

    @Provides @Singleton
    fun healthApi(
        @DefaultHttp r: Retrofit,
    ): HealthApi = r.create(HealthApi::class.java)
}
