package io.github.rupakc.travelplanner.core.network.api

import io.github.rupakc.travelplanner.core.model.auth.AccountDto
import io.github.rupakc.travelplanner.core.model.auth.AccountSettings
import io.github.rupakc.travelplanner.core.model.auth.AvatarResponse
import io.github.rupakc.travelplanner.core.model.auth.DeleteAccountRequest
import io.github.rupakc.travelplanner.core.model.auth.DisplayNameRequest
import io.github.rupakc.travelplanner.core.model.auth.LocaleRequest
import io.github.rupakc.travelplanner.core.model.auth.LogoutRequest
import io.github.rupakc.travelplanner.core.model.auth.OnboardingRequest
import io.github.rupakc.travelplanner.core.model.auth.OnboardingResponse
import io.github.rupakc.travelplanner.core.model.auth.QuotaDto
import io.github.rupakc.travelplanner.core.model.auth.RefreshRequest
import io.github.rupakc.travelplanner.core.model.auth.RefreshResponse
import io.github.rupakc.travelplanner.core.model.auth.SessionsList
import io.github.rupakc.travelplanner.core.model.auth.UserEnvelope
import io.github.rupakc.travelplanner.core.model.auth.VerificationSentDto
import kotlinx.serialization.json.JsonObject
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Streaming

/**
 * Accounts-API: refresh and sign-out, account, settings, onboarding, avatar, export, delete, quota, sessions.
 * Calls are made only when `features.accounts` is set (the previous backend answers 404).
 * Failures throw (`HttpException`/`IOException`) and are mapped to `AppError` by the repositories.
 */
interface AccountApi {
    @POST("api/auth/refresh")
    suspend fun refresh(
        @Body body: RefreshRequest,
    ): RefreshResponse

    @POST("api/auth/logout")
    suspend fun logout(
        @Body body: LogoutRequest,
    )

    @POST("api/auth/logout-all")
    suspend fun logoutAll()

    /** Task B1 endpoint: 404 on backends without it (the app then shows only "Sign out everywhere"). */
    @GET("api/auth/sessions")
    suspend fun sessions(
        @Header("X-Refresh-Token") refreshToken: String?,
    ): SessionsList

    @DELETE("api/auth/sessions/{id}")
    suspend fun revokeSession(
        @Path("id") id: String,
    )

    @GET("api/account")
    suspend fun account(): AccountDto

    @PATCH("api/account")
    suspend fun updateDisplayName(
        @Body body: DisplayNameRequest,
    ): UserEnvelope

    @GET("api/account/settings")
    suspend fun settings(): AccountSettings

    /** [patch] holds only the changed keys, so `null` can clear `language`, `timezone` and `bucket_list_goal`. */
    @PATCH("api/account/settings")
    suspend fun patchSettings(
        @Body patch: JsonObject,
    ): AccountSettings

    @PUT("api/account/onboarding")
    suspend fun putOnboarding(
        @Body body: OnboardingRequest,
    ): OnboardingResponse

    /** Multipart field `file` (jpeg/png/webp/gif, at most 2 MB). */
    @Multipart
    @PUT("api/account/avatar")
    suspend fun putAvatar(
        @Part file: MultipartBody.Part,
    ): AvatarResponse

    @DELETE("api/account/avatar")
    suspend fun deleteAvatar(): AvatarResponse

    @POST("api/account/email/verify")
    suspend fun requestEmailVerification(
        @Body body: LocaleRequest,
    ): VerificationSentDto

    @DELETE("api/account/identities/{provider}")
    suspend fun unlinkIdentity(
        @Path("provider") provider: String,
    )

    @GET("api/account/quota")
    suspend fun quota(): QuotaDto

    /** The whole export as a stream (could be large): write it to a cache file, never into memory. */
    @Streaming
    @GET("api/account/export")
    suspend fun export(): ResponseBody

    /** `DELETE` with a body (`HTTP` because Retrofit's `@DELETE` takes none). */
    @HTTP(method = "DELETE", path = "api/account", hasBody = true)
    suspend fun deleteAccount(
        @Body body: DeleteAccountRequest,
    )
}
