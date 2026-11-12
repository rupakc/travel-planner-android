package io.github.rupakc.travelplanner.core.model.auth

import io.github.rupakc.travelplanner.core.model.game.GameDelta
import io.github.rupakc.travelplanner.core.model.json.LenientInt
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `Session` of the Accounts contract: login, verify-email, password-reset confirm and OAuth results.
 * [refreshToken] is present only for `client` ios/android. [expiresIn] is 3600 for mobile.
 */
@Serializable
data class SessionDto(
    @SerialName("access_token") val accessToken: String,
    @SerialName("token_type") val tokenType: String = "bearer",
    @SerialName("expires_in") val expiresIn: Long? = null,
    @SerialName("refresh_token") val refreshToken: String? = null,
    val user: User,
)

/** `POST /auth/refresh` request. */
@Serializable
data class RefreshRequest(
    @SerialName("refresh_token") val refreshToken: String,
)

/** `POST /auth/refresh` 200: the rotated pair (persist [refreshToken] immediately). */
@Serializable
data class RefreshResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("token_type") val tokenType: String = "bearer",
    @SerialName("expires_in") val expiresIn: Long? = null,
    @SerialName("refresh_token") val refreshToken: String? = null,
)

/** `POST /auth/logout` body (best effort, the call answers 204). */
@Serializable
data class LogoutRequest(
    @SerialName("refresh_token") val refreshToken: String? = null,
)

/** One linked sign-in method of `GET /account`. */
@Serializable
data class IdentityDto(
    val provider: String? = null,
    val email: String? = null,
    @SerialName("linked_at") val linkedAt: String? = null,
)

/**
 * `Settings` of the Accounts contract (always complete in responses). PATCH takes any subset as a JSON object
 * (so `null` can clear `language`, `timezone` and `bucket_list_goal`); unknown keys are a 422.
 */
@Serializable
data class AccountSettings(
    val language: String? = null,
    val theme: String = "system",
    val currency: String = "USD",
    val units: String = "metric",
    val timezone: String? = null,
    @SerialName("hide_game") val hideGame: Boolean = false,
    @SerialName("hide_community") val hideCommunity: Boolean = false,
    @SerialName("profile_visibility") val profileVisibility: String = "public",
    @SerialName("telemetry_opt_out") val telemetryOptOut: Boolean = false,
    @SerialName("bucket_list_goal") val bucketListGoal: LenientInt = null,
)

/** `Onboarding` of the Accounts contract. */
@Serializable
data class OnboardingDto(
    val step: Int = 0,
    val done: Boolean = false,
    val skipped: Boolean = false,
    @SerialName("traveller_type") val travellerType: String? = null,
    @SerialName("completed_at") val completedAt: String? = null,
)

/** `GET /account`. */
@Serializable
data class AccountDto(
    val user: User,
    val identities: List<IdentityDto> = emptyList(),
    val settings: AccountSettings = AccountSettings(),
    val onboarding: OnboardingDto = OnboardingDto(),
)

/** `PATCH /account` body and `{"user"}` response. */
@Serializable
data class DisplayNameRequest(
    @SerialName("display_name") val displayName: String,
)

@Serializable
data class UserEnvelope(val user: User)

/** `PUT /account/onboarding` body; [skipped] and [travellerType] (`^[a-z_]{1,32}$`) are optional. */
@Serializable
data class OnboardingRequest(
    val step: Int,
    val done: Boolean,
    val skipped: Boolean? = null,
    @SerialName("traveller_type") val travellerType: String? = null,
)

/** `PUT /account/onboarding` 200. */
@Serializable
data class OnboardingResponse(
    val onboarding: OnboardingDto = OnboardingDto(),
    val game: GameDelta? = null,
)

/** `PUT`/`DELETE /account/avatar` 200 (`null` after a delete). */
@Serializable
data class AvatarResponse(
    @SerialName("avatar_url") val avatarUrl: String? = null,
)

/** `DELETE /account` body: [password] when `has_password`, else [confirmUsername]. */
@Serializable
data class DeleteAccountRequest(
    val password: String? = null,
    @SerialName("confirm_username") val confirmUsername: String? = null,
)

/** `POST /auth/oauth/link-ticket`. */
@Serializable
data class LinkTicketRequest(val provider: String, val client: String? = null)

/** `POST /auth/oauth/link-ticket` 200: a relative `start_url` (untrusted until checked). */
@Serializable
data class LinkTicketResponse(
    @SerialName("start_url") val startUrl: String? = null,
)

/** `GET /account/quota` 200. */
@Serializable
data class QuotaDto(
    val day: String? = null,
    @SerialName("resets_at") val resetsAt: String? = null,
    val buckets: Map<String, QuotaBucket> = emptyMap(),
)

/** [limit] null = unlimited (admin or quotas off). */
@Serializable
data class QuotaBucket(val used: Int = 0, val limit: LenientInt = null)

/**
 * `GET /auth/sessions` (Task B1, feature-detected: 404 on older backends) row. Not in the frozen contract yet.
 */
@Serializable
data class SessionsList(val sessions: List<SessionInfo> = emptyList())

@Serializable
data class SessionInfo(
    val id: String,
    val client: String? = null,
    @SerialName("device_name") val deviceName: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("last_used_at") val lastUsedAt: String? = null,
    val current: Boolean = false,
)
