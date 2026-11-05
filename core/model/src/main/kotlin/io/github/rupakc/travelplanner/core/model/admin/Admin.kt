package io.github.rupakc.travelplanner.core.model.admin

import io.github.rupakc.travelplanner.core.model.json.LenientBoolean
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Admin wire models (api-contract §2.8). Admin UI is web-only (spec §3 non-goal);
 * modelled so a future `feature:admin` needs no contract work.
 */
@Serializable
data class CreateUserRequest(
    val username: String,
    val password: String,
    val email: String? = null,
    @SerialName("is_admin") val isAdmin: Boolean = false,
)

@Serializable
data class AdminUser(
    val id: Long? = null,
    val username: String,
    val email: String? = null,
    @SerialName("is_admin") val isAdmin: LenientBoolean = null,
    @SerialName("is_first_login") val isFirstLogin: LenientBoolean = null,
    @SerialName("is_active") val isActive: LenientBoolean = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class ResetPasswordRequest(@SerialName("new_password") val newPassword: String)

@Serializable
data class AdminStatusResponse(val status: String? = null, val username: String? = null)
