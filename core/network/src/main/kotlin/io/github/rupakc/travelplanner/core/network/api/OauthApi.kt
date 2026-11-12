package io.github.rupakc.travelplanner.core.network.api

import io.github.rupakc.travelplanner.core.model.auth.LinkTicketRequest
import io.github.rupakc.travelplanner.core.model.auth.LinkTicketResponse
import io.github.rupakc.travelplanner.core.model.auth.OauthCompleteRequest
import io.github.rupakc.travelplanner.core.model.auth.OauthExchangeRequest
import io.github.rupakc.travelplanner.core.model.auth.OauthResult
import retrofit2.http.Body
import retrofit2.http.POST

/** OAuth hand-off after the system-browser leg (the browser leg itself is a Custom Tab, not an API call). */
interface OauthApi {
    @POST("api/auth/oauth/exchange")
    suspend fun exchange(
        @Body body: OauthExchangeRequest,
    ): OauthResult

    @POST("api/auth/oauth/complete")
    suspend fun complete(
        @Body body: OauthCompleteRequest,
    ): OauthResult

    @POST("api/auth/oauth/link-ticket")
    suspend fun linkTicket(
        @Body body: LinkTicketRequest,
    ): LinkTicketResponse
}
