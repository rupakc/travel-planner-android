package io.github.rupakc.travelplanner.core.network.api

import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Url

/** Replays a queued write: [path] is relative to the server root (`api/trips/1/expenses`); HTTP errors come back as a [Response], not an exception. */
interface OutboxApi {
    @POST
    suspend fun post(
        @Url path: String,
        @Body body: RequestBody,
    ): Response<ResponseBody>

    @PUT
    suspend fun put(
        @Url path: String,
        @Body body: RequestBody,
    ): Response<ResponseBody>

    @PATCH
    suspend fun patch(
        @Url path: String,
        @Body body: RequestBody,
    ): Response<ResponseBody>

    @DELETE
    suspend fun delete(
        @Url path: String,
    ): Response<ResponseBody>
}
