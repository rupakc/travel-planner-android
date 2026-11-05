package io.github.rupakc.travelplanner.core.testing

import io.github.rupakc.travelplanner.core.common.AppBuildInfo
import io.github.rupakc.travelplanner.core.common.AppDispatchers
import io.github.rupakc.travelplanner.core.datastore.SessionStore
import io.github.rupakc.travelplanner.core.model.json.TravelJson
import io.github.rupakc.travelplanner.core.model.settings.AppSettings
import io.github.rupakc.travelplanner.core.network.AuthEvents
import io.github.rupakc.travelplanner.core.network.AuthInterceptor
import io.github.rupakc.travelplanner.core.network.BaseUrlInterceptor
import io.github.rupakc.travelplanner.core.network.BaseUrlProvider
import io.github.rupakc.travelplanner.core.network.PLACEHOLDER_BASE_URL
import io.github.rupakc.travelplanner.core.testing.fake.FakeSessionStore
import io.github.rupakc.travelplanner.core.testing.fake.FakeSettingsStore
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * The real network stack (base-URL + auth interceptors, TravelJson Retrofit) pointed at
 * [baseUrl] — use with OkHttp MockWebServer in repository tests.
 */
class TestNetwork(
    baseUrl: String,
    val sessionStore: SessionStore = FakeSessionStore(),
    val dispatchers: AppDispatchers,
) {
    val authEvents = AuthEvents()
    private val buildInfo =
        AppBuildInfo(defaultBaseUrl = baseUrl, flavor = "local", versionName = "test", debug = false, cleartextAllowed = true)
    val baseUrlProvider = BaseUrlProvider(FakeSettingsStore(AppSettings()), buildInfo)
    val client: OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(sessionStore, authEvents, baseUrlProvider))
            .addInterceptor(BaseUrlInterceptor(baseUrlProvider))
            .build()
    val retrofit: Retrofit =
        Retrofit.Builder()
            .baseUrl(PLACEHOLDER_BASE_URL)
            .client(client)
            .addConverterFactory(TravelJson.asConverterFactory("application/json".toMediaType()))
            .build()

    inline fun <reified T> api(): T = retrofit.create(T::class.java)
}
