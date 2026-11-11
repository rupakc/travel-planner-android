package io.github.rupakc.travelplanner.core.network

import io.github.rupakc.travelplanner.core.common.AppLanguage
import javax.inject.Inject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.Buffer

/**
 * Adds `"locale": <UI language>` to the JSON body of AI POST requests so the backend answers in the user's
 * language. Only the wire body changes (drafts and cache keys are untouched). Must run after auth and before
 * the base-URL interceptor, while the host is still the placeholder. Anything unexpected passes through as is.
 */
class AiLocaleInterceptor
    @Inject
    constructor(private val language: AppLanguage) : Interceptor {
        private val placeholderHost = PLACEHOLDER_BASE_URL.toHttpUrlOrNull()!!.host

        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            val body = request.body
            val type = body?.contentType()
            if (body == null || type == null || !isRewritableAiPost(request, body, type)) {
                return chain.proceed(request)
            }
            val rewritten =
                runCatching {
                    val raw = Buffer().also { body.writeTo(it) }.readUtf8()
                    val obj = Json.parseToJsonElement(raw) as? JsonObject
                    if (obj == null || "locale" in obj) {
                        null
                    } else {
                        JsonObject(obj + ("locale" to JsonPrimitive(language.current()))).toString()
                    }
                }.getOrNull() ?: return chain.proceed(request)
            return chain.proceed(request.newBuilder().method("POST", rewritten.toRequestBody(type)).build())
        }

        private fun isRewritableAiPost(request: Request, body: RequestBody, type: MediaType): Boolean =
            !body.isOneShot() && !body.isDuplex() && isAiJsonPost(request, type)

        private fun isAiJsonPost(request: Request, type: MediaType): Boolean =
            request.method == "POST" && request.url.host == placeholderHost &&
                request.url.encodedPath in AI_PATHS && type.type == "application" && type.subtype == "json"

        companion object {
            val AI_PATHS: Set<String> =
                setOf(
                    "/api/search",
                    "/api/search/sync",
                    "/api/chat",
                    "/api/discover",
                    "/api/compare/destination",
                    "/api/stress-test",
                    "/api/flights",
                    "/api/flights/filtered",
                    "/api/hotels",
                    "/api/hotels/filtered",
                    "/api/activities",
                    "/api/activities/filtered",
                    "/api/visa",
                    "/api/sim",
                    "/api/tips",
                    "/api/getting-around",
                    "/api/forex",
                    "/api/itinerary",
                    "/api/events",
                    "/api/walking-tours",
                )
        }
    }
