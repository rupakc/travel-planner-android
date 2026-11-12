package io.github.rupakc.travelplanner.core.model.auth

import io.github.rupakc.travelplanner.core.model.game.GameDelta
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** `POST /auth/signup`. [client] is `"android"`; [locale] one of the seven UI languages. */
@Serializable
data class SignupRequest(
    val username: String,
    val email: String,
    val password: String,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("invite_code") val inviteCode: String? = null,
    val locale: String? = null,
    val client: String? = null,
)

/** 202 of signup, resend and `POST /account/email/verify` (`status` is `verification_sent`). */
@Serializable
data class VerificationSentDto(
    val status: String? = null,
    val email: String? = null,
    @SerialName("expires_in") val expiresIn: Long? = null,
)

/** `POST /auth/verify-email/resend` and `POST /auth/password-reset/request`. */
@Serializable
data class EmailRequest(val email: String, val locale: String? = null)

/** `POST /account/email/verify` body. */
@Serializable
data class LocaleRequest(val locale: String? = null)

/** `POST /auth/verify-email`. */
@Serializable
data class VerifyEmailRequest(
    val token: String,
    val client: String? = null,
    @SerialName("device_name") val deviceName: String? = null,
)

/** 200 of verify-email. [session] is null when an email was confirmed on an already active account. */
@Serializable
data class VerifyEmailResponse(
    val status: String? = null,
    val session: SessionDto? = null,
    val game: GameDelta? = null,
)

/** `POST /auth/password-reset/request` 202 (`status` is `reset_sent`, always). */
@Serializable
data class StatusResponse(val status: String? = null)

/** `POST /auth/password-reset/confirm`. */
@Serializable
data class PasswordResetConfirmRequest(
    val token: String,
    @SerialName("new_password") val newPassword: String,
    val client: String? = null,
    @SerialName("device_name") val deviceName: String? = null,
)

/** 200 of password-reset confirm. */
@Serializable
data class PasswordResetResponse(
    val status: String? = null,
    val session: SessionDto? = null,
)

/** The `403 account_pending` extras (`email_hint` like `j•••@example.com`) arrive in `AppError.Client.extras`. */
object AuthErrorCodes {
    const val ACCOUNT_PENDING = "account_pending"
    const val SESSION_REVOKED = "session_revoked"
    const val REFRESH_STALE = "refresh_stale"
    const val REFRESH_INVALID = "refresh_invalid"
    const val EMAIL_UNVERIFIED = "email_unverified"
    const val AI_DAILY_QUOTA = "ai_daily_quota"
}
