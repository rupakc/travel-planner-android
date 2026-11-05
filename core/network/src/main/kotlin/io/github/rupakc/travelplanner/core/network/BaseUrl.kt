package io.github.rupakc.travelplanner.core.network

import io.github.rupakc.travelplanner.core.common.AppBuildInfo
import io.github.rupakc.travelplanner.core.datastore.SettingsStore
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/** Placeholder host every Retrofit/SSE request is built against; [BaseUrlInterceptor] swaps in the real base URL. */
const val PLACEHOLDER_BASE_URL = "http://travelplanner.invalid/"

/** The effective backend URL: Settings → Server override, else the flavour default. */
@Singleton
class BaseUrlProvider @Inject constructor(
    private val settings: SettingsStore,
    private val buildInfo: AppBuildInfo,
) {
    suspend fun current(): HttpUrl? = resolve(settings.current().baseUrlOverride)

    fun resolve(override: String?): HttpUrl? =
        (override?.takeIf(String::isNotBlank) ?: buildInfo.defaultBaseUrl).takeIf(String::isNotBlank)?.toHttpUrlOrNull()

    /** Validates a URL typed in Settings → Server for this flavour (prod: HTTPS only). */
    fun validate(input: String): BaseUrlValidation {
        val url = input.trim().toHttpUrlOrNull() ?: return BaseUrlValidation.Invalid
        if (!url.isHttps && !buildInfo.cleartextAllowed) return BaseUrlValidation.HttpsRequired
        return BaseUrlValidation.Valid(url.toString().trimEnd('/'))
    }
}

sealed interface BaseUrlValidation {
    data class Valid(val normalized: String) : BaseUrlValidation

    data object Invalid : BaseUrlValidation

    data object HttpsRequired : BaseUrlValidation
}

/** Rewrites requests aimed at [PLACEHOLDER_BASE_URL] to the configured base URL (keeps any base path prefix). */
class BaseUrlInterceptor @Inject constructor(private val provider: BaseUrlProvider) : Interceptor {
    private val placeholderHost = PLACEHOLDER_BASE_URL.toHttpUrlOrNull()!!.host

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.url.host != placeholderHost) return chain.proceed(request)
        // DataStore keeps its value in memory after the first read: this is a cheap, non-IO lookup.
        val base = runBlocking { provider.current() } ?: throw NoServerConfiguredException()
        val url = base.newBuilder()
            .addEncodedPathSegments(request.url.encodedPath.trimStart('/'))
            .encodedQuery(request.url.encodedQuery)
            .build()
        return chain.proceed(request.newBuilder().url(url).build())
    }
}
