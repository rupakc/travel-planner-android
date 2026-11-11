package io.github.rupakc.travelplanner.core.network.api

import io.github.rupakc.travelplanner.core.model.auth.AuthProvidersDto
import retrofit2.Response
import retrofit2.http.GET

/** Social sign-in providers (Phase 1, feature-detected: older servers answer 404). */
interface AuthProvidersApi {
    @GET("api/auth/providers")
    suspend fun providers(): Response<AuthProvidersDto>
}
