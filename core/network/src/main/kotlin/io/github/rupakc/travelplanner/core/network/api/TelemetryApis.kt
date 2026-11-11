package io.github.rupakc.travelplanner.core.network.api

import io.github.rupakc.travelplanner.core.model.config.ClientConfigDto
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

/** `GET /api/config`: a 404 (older backend) is a [Response], never an exception, so callers can feature-detect. */
interface ClientConfigApi {
    @GET("api/config")
    suspend fun config(): Response<ClientConfigDto>
}

/** `POST /api/telemetry`: the body is already-gzipped JSON bytes; [encoding] is `gzip` (or null for plain). */
interface TelemetryApi {
    @POST("api/telemetry")
    suspend fun send(
        @Header("Content-Encoding") encoding: String?,
        @Body body: RequestBody,
    ): Response<Unit>
}
