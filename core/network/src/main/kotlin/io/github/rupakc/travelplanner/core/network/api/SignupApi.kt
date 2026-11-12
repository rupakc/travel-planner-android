package io.github.rupakc.travelplanner.core.network.api

import io.github.rupakc.travelplanner.core.model.auth.EmailRequest
import io.github.rupakc.travelplanner.core.model.auth.PasswordResetConfirmRequest
import io.github.rupakc.travelplanner.core.model.auth.PasswordResetResponse
import io.github.rupakc.travelplanner.core.model.auth.SignupRequest
import io.github.rupakc.travelplanner.core.model.auth.StatusResponse
import io.github.rupakc.travelplanner.core.model.auth.VerificationSentDto
import io.github.rupakc.travelplanner.core.model.auth.VerifyEmailRequest
import io.github.rupakc.travelplanner.core.model.auth.VerifyEmailResponse
import retrofit2.http.Body
import retrofit2.http.POST

/** Native sign-up, email verification and password reset (called only when `auth.signup_available` / `password_reset_available`). */
interface SignupApi {
    @POST("api/auth/signup")
    suspend fun signup(
        @Body body: SignupRequest,
    ): VerificationSentDto

    @POST("api/auth/verify-email/resend")
    suspend fun resendVerification(
        @Body body: EmailRequest,
    ): VerificationSentDto

    @POST("api/auth/verify-email")
    suspend fun verifyEmail(
        @Body body: VerifyEmailRequest,
    ): VerifyEmailResponse

    @POST("api/auth/password-reset/request")
    suspend fun requestPasswordReset(
        @Body body: EmailRequest,
    ): StatusResponse

    @POST("api/auth/password-reset/confirm")
    suspend fun confirmPasswordReset(
        @Body body: PasswordResetConfirmRequest,
    ): PasswordResetResponse
}
