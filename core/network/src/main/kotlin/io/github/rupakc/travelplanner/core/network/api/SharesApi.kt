package io.github.rupakc.travelplanner.core.network.api

import io.github.rupakc.travelplanner.core.model.share.CreateShareRequest
import io.github.rupakc.travelplanner.core.model.share.CreatedShare
import io.github.rupakc.travelplanner.core.model.share.SharesList
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * Share links 2.0 (management routes, signed in). Only called when `features.sharing >= 1`; an older backend answers 404.
 * The public routes (`GET /share/{token}`, `/page`, `/card.png`) are not here: the viewer uses [PlansApi.shared] and the
 * card is downloaded by `ShareCardStore`.
 */
interface SharesApi {
    /** Idempotent per (user, kind, plan): the same token comes back and its options are replaced. */
    @POST("api/shares")
    suspend fun create(
        @Body body: CreateShareRequest,
    ): CreatedShare

    @GET("api/shares")
    suspend fun list(): SharesList

    /** `204`; `404 share_not_found` when the token is not the caller's. */
    @DELETE("api/shares/{token}")
    suspend fun revoke(
        @Path("token") token: String,
    ): Response<Unit>
}
