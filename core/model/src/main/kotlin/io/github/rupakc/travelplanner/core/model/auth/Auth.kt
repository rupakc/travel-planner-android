package io.github.rupakc.travelplanner.core.model.auth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(val username: String, val password: String)

/** `POST /api/auth/login` 200. */
@Serializable
data class LoginResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("token_type") val tokenType: String = "bearer",
    val user: User,
)

/** `/api/auth/me` and login `user` (api-contract §0.2). */
@Serializable
data class User(
    val username: String,
    @SerialName("is_admin") val isAdmin: Boolean = false,
    @SerialName("requires_password_change") val requiresPasswordChange: Boolean = false,
    val email: String? = null,
)

@Serializable
data class ChangePasswordRequest(
    @SerialName("current_password") val currentPassword: String,
    @SerialName("new_password") val newPassword: String,
)

/** `POST /api/auth/change-password` → a fresh token (replace the stored one). */
@Serializable
data class TokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("token_type") val tokenType: String = "bearer",
)

/** `PUT /api/auth/email` (`""` clears). */
@Serializable
data class EmailUpdateRequest(val email: String)

@Serializable
data class EmailResponse(val email: String? = null)

/** Minimum new-password length enforced by the backend. */
const val MIN_PASSWORD_LENGTH = 8
