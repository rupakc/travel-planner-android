package io.github.rupakc.travelplanner.core.model.auth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** [client] (`"android"`) makes the server issue a refresh token; [deviceName] is at most 64 characters. */
@Serializable
data class LoginRequest(
    val username: String,
    val password: String,
    val client: String? = null,
    @SerialName("device_name") val deviceName: String? = null,
)

/** `POST /api/auth/login` 200. */
@Serializable
data class LoginResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("token_type") val tokenType: String = "bearer",
    val user: User,
    @SerialName("expires_in") val expiresIn: Long? = null,
    @SerialName("refresh_token") val refreshToken: String? = null,
)

/** `/api/auth/me` and login `user` (api-contract §0.2; Accounts-API "User"). Every Phase 1 field is defaulted. */
@Serializable
data class User(
    val username: String,
    @SerialName("is_admin") val isAdmin: Boolean = false,
    @SerialName("requires_password_change") val requiresPasswordChange: Boolean = false,
    val email: String? = null,
    val id: String? = null,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("email_verified") val emailVerified: Boolean = false,
    /** False until the email is confirmed (AI is gated on it); admin-created users count as verified. */
    @SerialName("account_verified") val accountVerified: Boolean = true,
    @SerialName("has_password") val hasPassword: Boolean = true,
    /** `admin` | `password` | `oauth`. */
    @SerialName("signup_method") val signupMethod: String? = null,
    /** Null = no avatar (draw initials). */
    @SerialName("avatar_url") val avatarUrl: String? = null,
)

@Serializable
data class ChangePasswordRequest(
    @SerialName("current_password") val currentPassword: String,
    @SerialName("new_password") val newPassword: String,
    val client: String? = null,
)

/** `POST /api/auth/change-password` → a fresh token (replace the stored one). */
@Serializable
data class TokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("token_type") val tokenType: String = "bearer",
    @SerialName("expires_in") val expiresIn: Long? = null,
    @SerialName("refresh_token") val refreshToken: String? = null,
)

/** `PUT /api/auth/email` (`""` clears). */
@Serializable
data class EmailUpdateRequest(val email: String)

@Serializable
data class EmailResponse(val email: String? = null)

/** Minimum new-password length enforced by the backend. */
const val MIN_PASSWORD_LENGTH = 8
